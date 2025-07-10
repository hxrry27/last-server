package com.example.lastserver.listeners;

import com.example.lastserver.LastServer;
import com.example.lastserver.utils.MessageUtil;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.Component;

import java.util.concurrent.TimeUnit;

public class ConnectionListener {
    private final LastServer plugin;

    public ConnectionListener(LastServer plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        plugin.getLogger().info("!!! EVENT FIRED FOR: " + event.getPlayer().getUsername());
        
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String username = player.getUsername();
        
        RegisteredServer targetServer = determineTargetServer(player, uuid, username);
        
        // CRITICAL SAFETY CHECK
        if (targetServer == null) {
            plugin.getLogger().error("determineTargetServer returned null for " + username);
            
            // Try each fallback option
            targetServer = plugin.getServer().getServer("resource").orElse(null);
            
            if (targetServer == null) {
                // Get literally ANY server
                targetServer = plugin.getServer().getAllServers().stream().findFirst().orElse(null);
            }
            
            if (targetServer == null) {
                plugin.getLogger().error("CRITICAL: No servers available at all!");
                event.getPlayer().disconnect(Component.text("No servers available"));
                return;
            }
        }
        
        plugin.getLogger().info("Setting initial server to: " + targetServer.getServerInfo().getName());
        event.setInitialServer(targetServer);
    }

    private RegisteredServer determineTargetServer(Player player, String uuid, String username) {
        plugin.getLogger().info("=== determineTargetServer called for {} ===", username);
        
        // Maintenance mode
        if (plugin.getConfiguration().isMaintenanceEnabled()) {
            plugin.getLogger().info("Maintenance mode is enabled");
            return plugin.getServer().getServer(plugin.getConfiguration().getMaintenanceServer()).orElse(null);
        }

        plugin.getLogger().info("Checking bypass permission...");
        // Bypass permission
        if (player.hasPermission(plugin.getConfiguration().getBypassPermission())) {
            plugin.getLogger().info("Player has bypass permission!");
            return plugin.getServer().getServer(plugin.getConfiguration().getFallbackServer()).orElse(null);
        }
        plugin.getLogger().info("Player does NOT have bypass permission");

        plugin.getLogger().info("Attempting to retrieve last server from database...");
        // Try to get last server synchronously (this will use cache or return null)
        try {
            plugin.getLogger().info("Calling getLastServer for UUID: {}", uuid);
            String lastServer = plugin.getServerManager().getLastServer(uuid).get(1000, TimeUnit.MILLISECONDS);
            plugin.getLogger().info("Database returned: '{}'", lastServer);
            
            if (lastServer == null) {
                plugin.getLogger().info("Database returned null - player has no saved server");
            } else {
                // Check each validation step
                plugin.getLogger().info("Validating last server: {}", lastServer);
                
                boolean isBlacklisted = plugin.getConfiguration().getBlacklistedServers().contains(lastServer);
                plugin.getLogger().info("- Is blacklisted? {}", isBlacklisted);
                
                boolean isValidName = isValidServerName(lastServer);
                plugin.getLogger().info("- Valid server name? {}", isValidName);
                
                boolean serverExists = plugin.getServerManager().serverExists(lastServer);
                plugin.getLogger().info("- Server exists? {}", serverExists);
                
                // Removed permission check - if they were on the server, they can rejoin it
                
                if (!isBlacklisted && isValidName && serverExists) {
                    RegisteredServer server = plugin.getServerManager().getServer(lastServer);
                    if (server != null) {
                        plugin.getLogger().info("All checks passed! Returning server: {}", lastServer);
                        player.sendMessage(MessageUtil.formatWithServer(
                            plugin.getConfiguration().getMessage("sending-last-server"),
                            lastServer
                        ));
                        return server;
                    } else {
                        plugin.getLogger().warn("getServer() returned null for: {}", lastServer);
                    }
                } else {
                    plugin.getLogger().info("Validation failed for server: {} (blacklisted={}, validName={}, exists={})", 
                    lastServer, isBlacklisted, isValidName, serverExists);
                }
            }
        } catch (Exception e) {
            // Database timeout or error, fall back to first join server
            plugin.getLogger().error("Exception while retrieving last server: ", e);
            if (plugin.getConfiguration().isDebug()) {
                plugin.getLogger().info("Could not retrieve last server for {}, using first join server", username);
            }
        }

        // Default to first join server
        plugin.getLogger().info("Falling back to first join server");
        player.sendMessage(MessageUtil.format(plugin.getConfiguration().getMessage("first-join")));
        String firstJoinServer = plugin.getConfiguration().getFirstJoinServer();
        String fallbackServer = plugin.getConfiguration().getFallbackServer();
        plugin.getLogger().info("First join server: '{}', Fallback server: '{}'", firstJoinServer, fallbackServer);
        
        return plugin.getServer().getServer(firstJoinServer).orElse(
            plugin.getServer().getServer(fallbackServer).orElse(null)
        );
    }
    
    private boolean isValidServerName(String serverName) {
        if (serverName == null || serverName.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = serverName.trim();
        if (trimmed.length() > 50) {
            return false;
        }
        
        return trimmed.matches("^[a-zA-Z0-9_-]+$");
    }
}