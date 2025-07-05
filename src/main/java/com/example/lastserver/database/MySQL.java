package com.example.lastserver.database;

import com.example.lastserver.LastServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MySQL {
    private final LastServer plugin;
    private HikariDataSource dataSource;
    
    private static final String CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS last_server (
            uuid VARCHAR(36) PRIMARY KEY,
            username VARCHAR(16) NOT NULL,
            server_name VARCHAR(50) NOT NULL,
            last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_last_seen (last_seen)
        )
        """;
    
    private static final String SELECT_LAST_SERVER = """
        SELECT server_name, last_seen FROM last_server WHERE uuid = ? AND last_seen > DATE_SUB(NOW(), INTERVAL ? DAY)
        """;
    
    private static final String INSERT_OR_UPDATE = """
        INSERT INTO last_server (uuid, username, server_name) VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE username = VALUES(username), server_name = VALUES(server_name), last_seen = CURRENT_TIMESTAMP
        """;
    
    private static final String DELETE_OLD_ENTRIES = """
        DELETE FROM last_server WHERE last_seen < DATE_SUB(NOW(), INTERVAL 30 DAY)
        """;

    public MySQL(LastServer plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + plugin.getConfiguration().getMysqlHost() + ":" + 
                plugin.getConfiguration().getMysqlPort() + "/" + plugin.getConfiguration().getMysqlDatabase() + 
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            config.setUsername(plugin.getConfiguration().getMysqlUsername());
            config.setPassword(plugin.getConfiguration().getMysqlPassword());
            config.setMaximumPoolSize(plugin.getConfiguration().getPoolSize());
            config.setMinimumIdle(2);
            config.setMaxLifetime(TimeUnit.MINUTES.toMillis(30));
            config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(10));
            config.setIdleTimeout(TimeUnit.MINUTES.toMillis(10));
            config.setLeakDetectionThreshold(TimeUnit.MINUTES.toMillis(2));
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            
            this.dataSource = new HikariDataSource(config);
            
            // Create table if it doesn't exist
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE)) {
                stmt.executeUpdate();
            }
            
            plugin.getLogger().info("Successfully connected to MySQL database");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to connect to MySQL database", e);
            return false;
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection closed");
        }
    }

    public CompletableFuture<String> getLastServer(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_LAST_SERVER)) {
                
                stmt.setString(1, uuid.trim());
                stmt.setInt(2, plugin.getConfiguration().getInactiveDays());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String serverName = rs.getString("server_name");
                        return isValidServerName(serverName) ? serverName : null;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to get last server for UUID: " + uuid, e);
            }
            return null;
        });
    }

    public CompletableFuture<Void> saveLastServer(String uuid, String username, String serverName) {
        if (uuid == null || uuid.trim().isEmpty() || 
            username == null || username.trim().isEmpty() || 
            serverName == null || serverName.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (!isValidServerName(serverName) || !isValidUsername(username)) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(INSERT_OR_UPDATE)) {
                
                stmt.setString(1, uuid.trim());
                stmt.setString(2, username.trim());
                stmt.setString(3, serverName.trim());
                
                stmt.executeUpdate();
                
                if (plugin.getConfiguration().isDebug()) {
                    plugin.getLogger().info("Saved last server for {}: {}", username, serverName);
                }
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to save last server for UUID: " + uuid, e);
            }
        });
    }

    public CompletableFuture<Integer> cleanupOldEntries() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(DELETE_OLD_ENTRIES)) {
                
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    plugin.getLogger().info("Cleaned up {} old entries from database", deleted);
                }
                return deleted;
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to cleanup old entries", e);
                return 0;
            }
        });
    }

    public CompletableFuture<String> getPlayerLastServer(String playerName) {
        if (!isValidUsername(playerName)) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT server_name, last_seen FROM last_server WHERE username = ? ORDER BY last_seen DESC LIMIT 1")) {
                
                stmt.setString(1, playerName.trim());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String serverName = rs.getString("server_name");
                        return isValidServerName(serverName) ? serverName : null;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to get last server for player: " + playerName, e);
            }
            return null;
        });
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
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
    
    private boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = username.trim();
        if (trimmed.length() > 16 || trimmed.length() < 3) {
            return false;
        }
        
        return trimmed.matches("^[a-zA-Z0-9_]+$");
    }
}