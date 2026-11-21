package com.aiworlddirector.events;

import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public final class WorldEventLog {
    private static final int MAX_EVENTS = 200;
    private static final ConcurrentLinkedDeque<JsonObject> EVENTS = new ConcurrentLinkedDeque<>();
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private WorldEventLog() {}

    public static void add(String type, String description) {
        JsonObject event = new JsonObject();
        event.addProperty("id", NEXT_ID.getAndIncrement());
        event.addProperty("type", type);
        event.addProperty("description", description);
        event.addProperty("timestamp", Instant.now().toString());
        EVENTS.addLast(event);
        while (EVENTS.size() > MAX_EVENTS) {
            EVENTS.pollFirst();
        }
    }

    public static List<JsonObject> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_EVENTS));
        List<JsonObject> all = new ArrayList<>(EVENTS);
        int start = Math.max(0, all.size() - safeLimit);
        return new ArrayList<>(all.subList(start, all.size()));
    }
}
