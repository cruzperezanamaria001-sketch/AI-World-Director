package com.aiworlddirector;

import com.aiworlddirector.bridge.BridgeServer;
import com.aiworlddirector.events.WorldEventLog;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AIWorldDirector.MOD_ID)
public class AIWorldDirector {
    public static final String MOD_ID = "aiworlddirector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public AIWorldDirector() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("AI World Director loaded. The director is watching.");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        BridgeServer.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BridgeServer.stop();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        WorldEventLog.add("player_login", event.getEntity().getGameProfile().getName() + " joined the world");
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        String entityName = event.getEntity().getType().builtInRegistryHolder().key().location().toString();
        String detail = entityName + " died";
        if (event.getSource().getEntity() != null) {
            detail += " (killer: " + event.getSource().getEntity().getName().getString() + ")";
        }
        WorldEventLog.add("entity_death", detail);
    }
}
