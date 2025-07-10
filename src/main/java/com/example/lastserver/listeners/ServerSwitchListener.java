package com.example.lastserver.listeners;

import com.example.lastserver.LastServer;
import com.example.lastserver.discord.DiscordWebhook;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ServerSwitchListener {
    private final LastServer plugin;
    private final ConcurrentHashMap<String, Long> recentSwitches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastKnownServer = new ConcurrentHashMap<>();
    private static final long SWITCH_COOLDOWN = TimeUnit.SECONDS.toMillis(3);

    public ServerSwitchListener(LastServer plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer currentServer = player.getCurrentServer()
            .map(connection -> connection.getServer())
            .orElse(null);
        
        if (currentServer == null) {
            return;
        }

        String serverName = currentServer.getServerInfo().getName();
        RegisteredServer previousServer = event.getPreviousServer();
        String uuid = player.getUniqueId().toString();
        String username = player.getUsername();
        
        // Save to database (existing code)
        if (!plugin.getConfiguration().getBlacklistedServers().contains(serverName)) {
            plugin.getServerManager().saveLastServer(uuid, username, serverName).thenRun(() -> {
                if (plugin.getConfiguration().isDebug()) {
                    plugin.getLogger().info("Saved last server '{}' for player {}", serverName, username);
                }
            });
            plugin.getServerManager().clearPlayerCache(uuid);
        }

        // Handle Discord notifications if enabled
        if (!plugin.getConfiguration().isDiscordEnabled()) {
            return;
        }

        // Check permissions
        if (player.hasPermission("lastserver.silent")) {
            return;
        }

        // SMART DETECTION LOGIC
        if (previousServer == null) {
            // No previous server - this is a network join
            String expectedServer = lastKnownServer.get(uuid);
            
            if (expectedServer != null && !expectedServer.equals(serverName)) {
                // They joined a different server than they left from = SERVER SWITCH
                RegisteredServer leftFrom = plugin.getServer().getServer(expectedServer).orElse(null);
                if (leftFrom != null && !plugin.getConfiguration().getBlacklistedServers().contains(expectedServer) &&
                    !plugin.getConfiguration().getBlacklistedServers().contains(serverName)) {
                    handleServerSwitch(player, leftFrom, currentServer);
                }
            } else {
                // They joined the same server they left from (or first join) = REAL JOIN
                if (!plugin.getConfiguration().getBlacklistedServers().contains(serverName)) {
                    // Check if first time
                    plugin.getMySQL().isFirstTimePlayer(uuid).thenAccept(isFirstTime -> {
                        DiscordWebhook.sendJoinEmbed(plugin, player, currentServer, isFirstTime);
                    });
                }
            }
            
            // Clear the last known server as they've now joined
            lastKnownServer.remove(uuid);
        } else {
            // Has previous server - this is an internal switch (they used /server command)
            handleServerSwitch(player, previousServer, currentServer);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        
        // Store their current server for comparison when they rejoin
        player.getCurrentServer().ifPresent(connection -> {
            String serverName = connection.getServer().getServerInfo().getName();
            lastKnownServer.put(uuid, serverName);
            
            // Schedule cleanup after 5 minutes (in case they don't rejoin)
            plugin.getServer().getScheduler()
                .buildTask(plugin, () -> {
                    String stored = lastKnownServer.get(uuid);
                    if (serverName.equals(stored)) {
                        lastKnownServer.remove(uuid);
                        
                        // They never rejoined - send leave message
                        if (plugin.getConfiguration().isDiscordEnabled() && 
                            !player.hasPermission("lastserver.silent") &&
                            !plugin.getConfiguration().getBlacklistedServers().contains(serverName)) {
                            DiscordWebhook.sendLeaveEmbed(plugin, player, connection.getServer());
                        }
                    }
                })
                .delay(5, TimeUnit.MINUTES)
                .schedule();
        });
    }

    private void handleServerSwitch(Player player, RegisteredServer from, RegisteredServer to) {
        // Skip if either server is blacklisted
        if (plugin.getConfiguration().getBlacklistedServers().contains(from.getServerInfo().getName()) ||
            plugin.getConfiguration().getBlacklistedServers().contains(to.getServerInfo().getName())) {
            return;
        }

        // Cooldown check to prevent spam
        String uuid = player.getUniqueId().toString();
        Long lastSwitch = recentSwitches.get(uuid);
        if (lastSwitch != null && System.currentTimeMillis() - lastSwitch < SWITCH_COOLDOWN) {
            return;
        }
        recentSwitches.put(uuid, System.currentTimeMillis());

        // Clean up old cooldown entries periodically
        if (recentSwitches.size() > 100) {
            long cutoff = System.currentTimeMillis() - SWITCH_COOLDOWN;
            recentSwitches.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }

        // Send the switch embed to Discord
        DiscordWebhook.sendSwitchEmbed(plugin, player, from, to);
    }
}