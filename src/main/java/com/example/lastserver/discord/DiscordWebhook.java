package com.example.lastserver.discord;

import com.example.lastserver.LastServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.awt.Color;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class DiscordWebhook {
    
    // Colors matching DiscordSRV style
    private static final int COLOR_JOIN = new Color(0, 255, 0).getRGB() & 0xFFFFFF;     // Green
    private static final int COLOR_LEAVE = new Color(255, 0, 0).getRGB() & 0xFFFFFF;    // Red
    private static final int COLOR_SWITCH = new Color(255, 165, 0).getRGB() & 0xFFFFFF;  // Orange
    private static final int COLOR_FIRST_TIME = new Color(255, 215, 0).getRGB() & 0xFFFFFF; // Gold
    
    public static void sendJoinEmbed(LastServer plugin, Player player, RegisteredServer server, boolean isFirstTime) {
        if (!shouldSendMessage(plugin)) return;
        
        String serverName = getServerDisplayName(plugin, server);
        String title;
        int color;
        
        if (isFirstTime && server.getServerInfo().getName().equals(plugin.getConfiguration().getFirstTimeAnnounceServer())) {
            title = player.getUsername() + " joined the server for the first time!";
            color = COLOR_FIRST_TIME;
        } else {
            title = player.getUsername() + " joined the server";
            color = COLOR_JOIN;
        }
        
        String json = createEmbed(title, null, color, player.getUniqueId().toString(), player.getUsername());
        sendWebhook(plugin, json);
    }
    
    public static void sendLeaveEmbed(LastServer plugin, Player player, RegisteredServer server) {
        if (!shouldSendMessage(plugin)) return;
        
        String json = createEmbed(
            player.getUsername() + " left the server",
            null,
            COLOR_LEAVE,
            player.getUniqueId().toString(),
            player.getUsername()
        );
        
        sendWebhook(plugin, json);
    }
    
    public static void sendSwitchEmbed(LastServer plugin, Player player, RegisteredServer from, RegisteredServer to) {
        if (!shouldSendMessage(plugin)) return;
        
        String fromName = getServerDisplayName(plugin, from);
        String toName = getServerDisplayName(plugin, to);
        
        String json = createEmbed(
            player.getUsername() + " switched servers",
            "**" + fromName + "** → **" + toName + "**",
            COLOR_SWITCH,
            player.getUniqueId().toString(),
            player.getUsername()
        );
        
        sendWebhook(plugin, json);
    }
    
    private static String createEmbed(String title, String description, int color, String uuid, String username) {
        // Remove dashes from UUID for avatar services
        String cleanUuid = uuid.replace("-", "");
        String avatarUrl = String.format("https://mc-heads.net/avatar/%s/100", cleanUuid);
        
        // Build embed JSON
        if (description != null && !description.trim().isEmpty()) {
            return String.format("""
                {
                    "embeds": [{
                        "author": {
                            "name": "%s",
                            "icon_url": "%s"
                        },
                        "description": "%s",
                        "color": %d
                    }]
                }
                """,
                title,
                avatarUrl,
                description,
                color
            );
        } else {
            // No description - matches DiscordSRV's style
            return String.format("""
                {
                    "embeds": [{
                        "author": {
                            "name": "%s",
                            "icon_url": "%s"
                        },
                        "color": %d
                    }]
                }
                """,
                title,
                avatarUrl,
                color
            );
        }
    }
    
    private static void sendWebhook(LastServer plugin, String json) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(plugin.getConfiguration().getDiscordWebhookUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "LastServer-Plugin/1.0");
                conn.setDoOutput(true);
                
                if (plugin.getConfiguration().isDebug()) {
                    plugin.getLogger().info("Sending webhook: " + json);
                }
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode != 204 && responseCode != 200) {
                    plugin.getLogger().warn("Discord webhook returned unexpected code: " + responseCode);
                }
                
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().error("Failed to send Discord webhook: " + e.getMessage());
                if (plugin.getConfiguration().isDebug()) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    private static boolean shouldSendMessage(LastServer plugin) {
        return plugin.getConfiguration().isDiscordEnabled() && 
               plugin.getConfiguration().getDiscordWebhookUrl() != null &&
               !plugin.getConfiguration().getDiscordWebhookUrl().isEmpty();
    }
    
    private static String getServerDisplayName(LastServer plugin, RegisteredServer server) {
        String serverName = server.getServerInfo().getName();
        return plugin.getConfiguration().getServerDisplayNames()
            .getOrDefault(serverName, serverName);
    }
}