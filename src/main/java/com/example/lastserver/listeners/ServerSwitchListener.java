package com.example.lastserver.listeners;

import com.example.lastserver.LastServer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;

public class ServerSwitchListener {
    private final LastServer plugin;

    public ServerSwitchListener(LastServer plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        String serverName = player.getCurrentServer().map(server -> server.getServerInfo().getName()).orElse(null);
        
        if (serverName == null) {
            return;
        }

        if (plugin.getConfiguration().getBlacklistedServers().contains(serverName)) {
            if (plugin.getConfiguration().isDebug()) {
                plugin.getLogger().info("Not saving blacklisted server '{}' for player {}", serverName, player.getUsername());
            }
            return;
        }

        String uuid = player.getUniqueId().toString();
        String username = player.getUsername();

        plugin.getServerManager().saveLastServer(uuid, username, serverName).thenRun(() -> {
            if (plugin.getConfiguration().isDebug()) {
                plugin.getLogger().info("Saved last server '{}' for player {}", serverName, username);
            }
        });

        plugin.getServerManager().clearPlayerCache(uuid);
    }
}