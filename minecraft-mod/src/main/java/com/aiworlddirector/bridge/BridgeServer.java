package com.aiworlddirector.bridge;

import com.aiworlddirector.AIWorldDirector;
import com.aiworlddirector.events.WorldEventLog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.loading.FMLPaths;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class BridgeServer {
    private static final Gson GSON = new Gson();
    private static final int PORT = 38765;
    private static volatile HttpServer httpServer;
    private static volatile MinecraftServer minecraftServer;
    private static volatile String token;

    private BridgeServer() {}

    public static synchronized void start(MinecraftServer server) {
        if (httpServer != null) return;
        minecraftServer = server;
        try {
            token = loadOrCreateToken();
            HttpServer instance = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            instance.createContext("/", BridgeServer::handle);
            instance.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "AIWorldDirector-HTTP");
                thread.setDaemon(true);
                return thread;
            }));
            instance.start();
            httpServer = instance;
            AIWorldDirector.LOGGER.info("AI World Director bridge listening on 127.0.0.1:{}", PORT);
            AIWorldDirector.LOGGER.info("Bridge token saved in config/aiworlddirector.token (keep it private).");
            WorldEventLog.add("bridge_started", "Local AI bridge started on port " + PORT);
        } catch (Exception exception) {
            AIWorldDirector.LOGGER.error("Could not start the local AI bridge", exception);
        }
    }

    public static synchronized void stop() {
        HttpServer current = httpServer;
        httpServer = null;
        minecraftServer = null;
        if (current != null) current.stop(0);
    }

    private static String loadOrCreateToken() throws IOException {
        Path path = FMLPaths.CONFIGDIR.get().resolve("aiworlddirector.token");
        if (Files.exists(path)) {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.createDirectories(path.getParent());
        Files.writeString(path, generated, StandardCharsets.UTF_8);
        return generated;
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && "/health".equals(path)) {
                respond(exchange, 200, ok("ready"));
                return;
            }
            if (!authorized(exchange)) {
                respond(exchange, 401, error("unauthorized"));
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && "/v1/player".equals(path)) {
                respond(exchange, 200, playerStatus());
            } else if ("GET".equals(exchange.getRequestMethod()) && "/v1/world".equals(path)) {
                respond(exchange, 200, worldStatus());
            } else if ("GET".equals(exchange.getRequestMethod()) && "/v1/events".equals(path)) {
                respond(exchange, 200, eventsStatus());
            } else if ("POST".equals(exchange.getRequestMethod()) && "/v1/actions".equals(path)) {
                if (exchange.getRequestBody().readNBytes(16385).length > 16384) {
                    respond(exchange, 413, error("request_too_large"));
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject request = JsonParser.parseString(body).getAsJsonObject();
                JsonObject result = executeAction(request);
                respond(exchange, result.get("ok").getAsBoolean() ? 200 : 400, result);
            } else {
                respond(exchange, 404, error("not_found"));
            }
        } catch (Exception exception) {
            AIWorldDirector.LOGGER.warn("Bridge request failed: {}", exception.toString());
            respond(exchange, 500, error("internal_error"));
        } finally {
            exchange.close();
        }
    }

    private static boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.equals("Bearer " + token) && token != null;
    }

    private static JsonObject playerStatus() {
        MinecraftServer server = minecraftServer;
        if (server == null) return error("server_not_ready");
        ServerPlayer player = firstPlayer(server);
        if (player == null) return error("no_player_online");
        JsonObject result = ok("player_status");
        result.addProperty("name", player.getGameProfile().getName());
        result.addProperty("health", player.getHealth());
        result.addProperty("max_health", player.getMaxHealth());
        result.addProperty("food", player.getFoodData().getFoodLevel());
        result.addProperty("x", player.getX());
        result.addProperty("y", player.getY());
        result.addProperty("z", player.getZ());
        result.addProperty("dimension", player.level().dimension().location().toString());
        JsonArray inventory = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                JsonObject item = new JsonObject();
                item.addProperty("id", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
                item.addProperty("count", stack.getCount());
                inventory.add(item);
            }
        }
        result.add("inventory", inventory);
        return result;
    }

    private static JsonObject worldStatus() {
        MinecraftServer server = minecraftServer;
        if (server == null) return error("server_not_ready");
        ServerLevel level = server.overworld();
        JsonObject result = ok("world_status");
        result.addProperty("day_time", level.getDayTime());
        result.addProperty("game_time", level.getGameTime());
        result.addProperty("raining", level.isRaining());
        result.addProperty("thundering", level.isThundering());
        result.addProperty("online_players", server.getPlayerList().getPlayers().size());
        result.addProperty("dimension", level.dimension().location().toString());
        return result;
    }

    private static JsonObject eventsStatus() {
        JsonObject result = ok("recent_events");
        JsonArray array = new JsonArray();
        WorldEventLog.recent(50).forEach(array::add);
        result.add("events", array);
        return result;
    }

    private static JsonObject executeAction(JsonObject request) throws Exception {
        MinecraftServer server = minecraftServer;
        if (server == null) return error("server_not_ready");
        if (!request.has("type") || !request.get("type").isJsonPrimitive()) return error("missing_action_type");
        String type = request.get("type").getAsString();
        JsonObject payload = request.has("payload") && request.get("payload").isJsonObject()
                ? request.getAsJsonObject("payload") : new JsonObject();

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                ServerPlayer player = firstPlayer(server);
                if (player == null) {
                    future.complete(error("no_player_online"));
                    return;
                }
                JsonObject result;
                switch (type) {
                    case "spawn_entity" -> result = spawnEntities(player, payload);
                    case "give_item" -> result = giveItem(player, payload);
                    case "set_time" -> result = setTime(server, payload);
                    case "set_weather" -> result = setWeather(server, payload);
                    default -> result = error("unsupported_action");
                }
                future.complete(result);
            } catch (Exception exception) {
                future.complete(error("action_failed"));
            }
        });
        try {
            return future.get(6, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return error("action_timeout");
        }
    }

    private static JsonObject spawnEntities(ServerPlayer player, JsonObject payload) {
        String id = stringValue(payload, "entity", "minecraft:zombie");
        if (!List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper").contains(id)) {
            return error("entity_not_allowed");
        }
        int count = intValue(payload, "count", 1);
        int radius = intValue(payload, "radius", 5);
        if (count < 1 || count > 5 || radius < 2 || radius > 16) return error("invalid_spawn_limits");
        ResourceLocation key = ResourceLocation.tryParse(id);
        EntityType<?> entityType = key == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(key);
        if (entityType == null) return error("unknown_entity");
        ServerLevel level = player.serverLevel();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i) / count;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            BlockPos pos = BlockPos.containing(x, player.getY(), z);
            Entity entity = entityType.create(level);
            if (entity != null) {
                entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
                if (level.noCollision(entity) && level.addFreshEntity(entity)) spawned++;
            }
        }
        WorldEventLog.add("director_action", "Spawned " + spawned + " " + id);
        JsonObject result = ok("spawn_entity");
        result.addProperty("requested", count);
        result.addProperty("spawned", spawned);
        result.addProperty("entity", id);
        return result;
    }

    private static JsonObject giveItem(ServerPlayer player, JsonObject payload) {
        String id = stringValue(payload, "item", "minecraft:diamond");
        int count = intValue(payload, "count", 1);
        if (count < 1 || count > 64) return error("invalid_item_count");
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return error("unknown_item");
        boolean added = player.getInventory().add(new ItemStack(item, count));
        if (!added) player.drop(new ItemStack(item, count), false);
        WorldEventLog.add("director_reward", "Gave " + count + " " + id);
        JsonObject result = ok("give_item");
        result.addProperty("item", id);
        result.addProperty("count", count);
        return result;
    }

    private static JsonObject setTime(MinecraftServer server, JsonObject payload) {
        String value = stringValue(payload, "value", "day").toLowerCase(Locale.ROOT);
        long time = switch (value) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "sunset" -> 12000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> -1L;
        };
        if (time < 0) return error("invalid_time_value");
        server.overworld().setDayTime(time);
        WorldEventLog.add("director_action", "Changed time to " + value);
        return ok("set_time");
    }

    private static JsonObject setWeather(MinecraftServer server, JsonObject payload) {
        String value = stringValue(payload, "value", "clear").toLowerCase(Locale.ROOT);
        int duration = intValue(payload, "duration", 6000);
        if (duration < 20 || duration > 24000) return error("invalid_weather_duration");
        boolean rain = value.equals("rain") || value.equals("thunder");
        boolean thunder = value.equals("thunder");
        if (!value.equals("clear") && !rain && !thunder) return error("invalid_weather_value");
        server.overworld().setWeatherParameters(0, duration, rain, thunder);
        WorldEventLog.add("director_action", "Changed weather to " + value);
        return ok("set_weather");
    }

    private static ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static JsonObject ok(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("message", message);
        return result;
    }

    private static JsonObject error(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", false);
        result.addProperty("error", message);
        return result;
    }

    private static void respond(HttpExchange exchange, int status, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://127.0.0.1");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
