package com.example.lastserver.listeners;

import com.example.lastserver.LastServer;
import com.example.lastserver.utils.MessageUtil;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
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
            targetServer = plugin.getServer().getServer("lobby").orElse(null);
            
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
        // Maintenance mode
        if (plugin.getConfiguration().isMaintenanceEnabled()) {
            return plugin.getServer().getServer(plugin.getConfiguration().getMaintenanceServer()).orElse(null);
        }

        // Bypass permission
        if (player.hasPermission(plugin.getConfiguration().getBypassPermission())) {
            return plugin.getServer().getServer(plugin.getConfiguration().getFallbackServer()).orElse(null);
        }

        // Try to get last server synchronously (this will use cache or return null)
        try {
            String lastServer = plugin.getServerManager().getLastServer(uuid).get(100, TimeUnit.MILLISECONDS);
            
            if (lastServer != null && 
                !plugin.getConfiguration().getBlacklistedServers().contains(lastServer) &&
                isValidServerName(lastServer) &&
                plugin.getServerManager().serverExists(lastServer) &&
                hasServerPermission(player, lastServer)) {
                
                RegisteredServer server = plugin.getServerManager().getServer(lastServer);
                if (server != null) {
                    player.sendMessage(MessageUtil.formatWithServer(
                        plugin.getConfiguration().getMessage("sending-last-server"),
                        lastServer
                    ));
                    return server;
                }
            }
        } catch (Exception e) {
            // Database timeout or error, fall back to first join server
            if (plugin.getConfiguration().isDebug()) {
                plugin.getLogger().info("Could not retrieve last server for {}, using first join server", username);
            }
        }

        // Default to first join server
        player.sendMessage(MessageUtil.format(plugin.getConfiguration().getMessage("first-join")));
        return plugin.getServer().getServer(plugin.getConfiguration().getFirstJoinServer()).orElse(
            plugin.getServer().getServer(plugin.getConfiguration().getFallbackServer()).orElse(null)
        );
    }


    private boolean hasServerPermission(Player player, String serverName) {
        return player.hasPermission("server." + serverName) || player.hasPermission("server.*");
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

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (event.getResult().isAllowed()) {
            RegisteredServer server = event.getResult().getServer().orElse(null);
            if (server != null) {
                String serverName = server.getServerInfo().getName();
                if (!hasServerPermission(event.getPlayer(), serverName)) {
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    event.getPlayer().sendMessage(MessageUtil.format(plugin.getConfiguration().getMessage("no-permission")));
                }
            }
        }
    }
}