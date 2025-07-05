package com.example.lastserver.listeners;

import com.example.lastserver.LastServer;
import com.example.lastserver.utils.MessageUtil;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.concurrent.TimeUnit;

public class ConnectionListener {
    private final LastServer plugin;

    public ConnectionListener(LastServer plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String username = player.getUsername();
        
        plugin.getServer().getScheduler()
            .buildTask(plugin, () -> handlePlayerLogin(player, uuid, username))
            .delay(100, TimeUnit.MILLISECONDS)
            .schedule();
    }

    private void handlePlayerLogin(Player player, String uuid, String username) {
        if (plugin.getConfiguration().isDebug()) {
            plugin.getLogger().info("Processing login for player: {}", username);
        }

        if (plugin.getConfiguration().isMaintenanceEnabled()) {
            sendToMaintenanceServer(player);
            return;
        }

        if (player.hasPermission(plugin.getConfiguration().getBypassPermission())) {
            sendToFallbackServer(player, "bypass");
            return;
        }

        plugin.getServerManager().getLastServer(uuid).thenAccept(lastServer -> {
            if (lastServer == null) {
                sendToFirstJoinServer(player);
                return;
            }

            if (plugin.getConfiguration().getBlacklistedServers().contains(lastServer)) {
                sendToFallbackServer(player, "blacklisted");
                return;
            }

            if (!isValidServerName(lastServer) || !plugin.getServerManager().serverExists(lastServer)) {
                sendToFallbackServer(player, "not_exists");
                return;
            }

            plugin.getServerManager().isServerAvailable(lastServer).thenAccept(available -> {
                if (!available) {
                    sendToFallbackServer(player, "offline");
                    return;
                }

                RegisteredServer server = plugin.getServerManager().getServer(lastServer);
                if (server == null) {
                    sendToFallbackServer(player, "not_found");
                    return;
                }

                if (hasServerPermission(player, lastServer)) {
                    sendToLastServer(player, server);
                } else {
                    sendToFallbackServer(player, "no_permission");
                }
            });
        });
    }

    private void sendToLastServer(Player player, RegisteredServer server) {
        if (plugin.getConfiguration().isDebug()) {
            plugin.getLogger().info("Sending {} to last server: {}", player.getUsername(), server.getServerInfo().getName());
        }

        player.sendMessage(MessageUtil.formatWithServer(
            plugin.getConfiguration().getMessage("sending-last-server"),
            server.getServerInfo().getName()
        ));

        player.createConnectionRequest(server).fireAndForget();
    }

    private void sendToFallbackServer(Player player, String reason) {
        RegisteredServer fallback = plugin.getServer().getServer(plugin.getConfiguration().getFallbackServer()).orElse(null);
        if (fallback == null) {
            plugin.getLogger().error("Fallback server '{}' not found!", plugin.getConfiguration().getFallbackServer());
            player.disconnect(MessageUtil.format("<red>Server configuration error. Please contact an administrator.</red>"));
            return;
        }

        if (plugin.getConfiguration().isDebug()) {
            plugin.getLogger().info("Sending {} to fallback server due to: {}", player.getUsername(), reason);
        }

        switch (reason) {
            case "offline" -> player.sendMessage(MessageUtil.format(plugin.getConfiguration().getMessage("server-offline")));
            case "no_permission" -> player.sendMessage(MessageUtil.format(plugin.getConfiguration().getMessage("no-permission")));
            case "full" -> player.sendMessage(MessageUtil.format(plugin.getConfiguration().getMessage("server-full")));
        }

        player.createConnectionRequest(fallback).fireAndForget();
    }

    private void sendToFirstJoinServer(Player player) {
        RegisteredServer firstJoin = plugin.getServer().getServer(plugin.getConfiguration().getFirstJoinServer()).orElse(null);
        if (firstJoin == null) {
            plugin.getLogger().error("First join server '{}' not found!", plugin.getConfiguration().getFirstJoinServer());
            sendToFallbackServer(player, "fallback");
            return;
        }

        if (plugin.getConfiguration().isDebug()) {
            plugin.getLogger().info("Sending {} to first join server", player.getUsername());
        }

        player.sendMessage(MessageUtil.format(plugin.getConfiguration().getMessage("first-join")));
        player.createConnectionRequest(firstJoin).fireAndForget();
    }

    private void sendToMaintenanceServer(Player player) {
        RegisteredServer maintenance = plugin.getServer().getServer(plugin.getConfiguration().getMaintenanceServer()).orElse(null);
        if (maintenance == null) {
            plugin.getLogger().error("Maintenance server '{}' not found!", plugin.getConfiguration().getMaintenanceServer());
            player.disconnect(MessageUtil.format(plugin.getConfiguration().getMessage("maintenance-kick")));
            return;
        }

        if (plugin.getConfiguration().isDebug()) {
            plugin.getLogger().info("Sending {} to maintenance server", player.getUsername());
        }

        player.createConnectionRequest(maintenance).fireAndForget();
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