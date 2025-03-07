package com.sparkedhost.accuratereadings.listeners;

import com.mattmalec.pterodactyl4j.client.managers.WebSocketManager;
import com.mattmalec.pterodactyl4j.client.ws.events.AuthSuccessEvent;
import com.mattmalec.pterodactyl4j.client.ws.events.StatsUpdateEvent;
import com.mattmalec.pterodactyl4j.client.ws.events.connection.FailureEvent;
import com.mattmalec.pterodactyl4j.client.ws.hooks.ClientSocketListenerAdapter;
import com.sparkedhost.accuratereadings.Main;
import com.sparkedhost.accuratereadings.managers.PterodactylManager;
import com.sparkedhost.accuratereadings.managers.ResourceUsageManager;
import com.sparkedhost.accuratereadings.tasks.ResourceType;
import org.bukkit.Bukkit;

import java.net.ProtocolException;
import java.util.logging.Level;

public class WebSocketListener extends ClientSocketListenerAdapter {
    PterodactylManager pteroManager = Main.getInstance().pteroAPI;
    ResourceUsageManager resourceUsageManager = pteroManager.getResourceUsageManager();

    @Override
    public void onAuthSuccess(AuthSuccessEvent e) {
        Main.getInstance().log(Level.INFO, "Successfully established a websocket connection.");
        e.getWebSocketManager().request(WebSocketManager.RequestAction.STATS);
        resourceUsageManager.setWebSocketManager(e.getWebSocketManager());
    }

    @Override
    public void onStatsUpdate(StatsUpdateEvent e) {
        pteroManager.setUsage(ResourceType.CPU, (long) e.getCPU());
        pteroManager.setUsage(ResourceType.MEMORY, e.getMemory());
        pteroManager.setUsage(ResourceType.DISK, e.getDisk());
        pteroManager.setUptime(e.getUptimeFormatted());
    }

    @Override
    public void onFailure(FailureEvent e) {
        if (e.getThrowable() instanceof ProtocolException) {
            Main.getInstance().log(Level.WARNING, "Unable to utilize websockets, falling back to API polling...");
            resourceUsageManager.stopListener();
            Main.getInstance().getSettings().pterodactyl_useWebsocket = false;
            resourceUsageManager.startListener();
            return;
        }

        Main.getInstance().log(Level.WARNING, "An error occurred with the websocket connection, reconnecting...");

        if (resourceUsageManager.getWebSocketManager() != null) {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> resourceUsageManager.getWebSocketManager().reconnect(), 60L);
            return;
        }

        // Fallback just in case getWebSocketManager() returns null
        resourceUsageManager.stopListener();
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> resourceUsageManager.startListener(), 60L);
    }
}
