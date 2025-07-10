package com.example.lastserver.database;

import com.example.lastserver.LastServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ServerManager {
    private final LastServer plugin;
    private final ConcurrentHashMap<String, ServerStatus> serverStatusCache;
    private final ConcurrentHashMap<String, Long> playerCache;
    private static final long CACHE_DURATION = TimeUnit.SECONDS.toMillis(30);
    private static final long SERVER_CACHE_DURATION = TimeUnit.SECONDS.toMillis(5);

    public ServerManager(LastServer plugin) {
        this.plugin = plugin;
        this.serverStatusCache = new ConcurrentHashMap<>();
        this.playerCache = new ConcurrentHashMap<>();
    }

    public CompletableFuture<String> getLastServer(String uuid) {
        Long cachedTime = playerCache.get(uuid);
        if (cachedTime != null && System.currentTimeMillis() - cachedTime < CACHE_DURATION) {
            return CompletableFuture.completedFuture(getCachedServer(uuid));
        }
        
        return plugin.getMySQL().getLastServer(uuid).thenApply(server -> {
            if (server != null) {
                playerCache.put(uuid, System.currentTimeMillis());
            }
            return server;
        });
    }

    public CompletableFuture<Void> saveLastServer(String uuid, String username, String serverName) {
        if (plugin.getConfiguration().getBlacklistedServers().contains(serverName)) {
            return CompletableFuture.completedFuture(null);
        }
        
        playerCache.remove(uuid);
        return plugin.getMySQL().saveLastServer(uuid, username, serverName);
    }

    public CompletableFuture<Boolean> isServerAvailable(String serverName) {
        ServerStatus cached = serverStatusCache.get(serverName);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < SERVER_CACHE_DURATION) {
            return CompletableFuture.completedFuture(cached.available);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            RegisteredServer server = plugin.getServer().getServer(serverName).orElse(null);
            if (server == null) {
                serverStatusCache.put(serverName, new ServerStatus(false, System.currentTimeMillis()));
                return false;
            }
            
            try {
                server.ping().get(3, TimeUnit.SECONDS);
                serverStatusCache.put(serverName, new ServerStatus(true, System.currentTimeMillis()));
                return true;
            } catch (Exception e) {
                serverStatusCache.put(serverName, new ServerStatus(false, System.currentTimeMillis()));
                return false;
            }
        });
    }

    public boolean serverExists(String serverName) {
        return plugin.getServer().getServer(serverName).isPresent();
    }

    public RegisteredServer getServer(String serverName) {
        return plugin.getServer().getServer(serverName).orElse(null);
    }

    public CompletableFuture<Void> cleanupOldEntries() {
        return plugin.getMySQL().cleanupOldEntries().thenAccept(deleted -> {
            if (deleted > 0 && plugin.getConfiguration().isDebug()) {
                plugin.getLogger().info("Cleaned up {} old player entries", deleted);
            }
        });
    }

    public void clearPlayerCache(String uuid) {
        playerCache.remove(uuid);
    }

    public void clearServerCache(String serverName) {
        serverStatusCache.remove(serverName);
    }

    public void clearAllCaches() {
        playerCache.clear();
        serverStatusCache.clear();
    }

    private String getCachedServer(String uuid) {
        return null;
    }

    private static class ServerStatus {
        final boolean available;
        final long timestamp;

        ServerStatus(boolean available, long timestamp) {
            this.available = available;
            this.timestamp = timestamp;
        }
    }
}