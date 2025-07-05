package com.example.lastserver.config;

import com.example.lastserver.LastServer;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public class Configuration {
    private final LastServer plugin;
    private final Path configPath;
    private Map<String, Object> config;
    
    // MySQL settings
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int poolSize;
    
    // Server settings
    private String fallbackServer;
    private List<String> blacklistedServers;
    private int inactiveDays;
    private String bypassPermission;
    private String firstJoinServer;
    
    // Maintenance settings
    private volatile boolean maintenanceEnabled;
    private String maintenanceServer;
    private List<String> onEnableCommands;
    private List<String> onDisableCommands;
    
    // Messages
    private Map<String, String> messages;
    
    // Debug
    private boolean debug;

    public Configuration(LastServer plugin, Path dataDirectory) {
        this.plugin = plugin;
        this.configPath = dataDirectory.resolve("config.yml");
        this.messages = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public boolean load() {
        try {
            // Create config directory if it doesn't exist
            if (!Files.exists(configPath.getParent())) {
                Files.createDirectories(configPath.getParent());
            }
            
            // Copy default config if it doesn't exist
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    }
                }
            }
            
            // Load config
            Yaml yaml = new Yaml();
            try (InputStream in = Files.newInputStream(configPath)) {
                config = yaml.load(in);
            }
            
            // Parse MySQL settings
            Map<String, Object> mysql = (Map<String, Object>) config.get("mysql");
            mysqlHost = (String) mysql.get("host");
            mysqlPort = (Integer) mysql.get("port");
            mysqlDatabase = (String) mysql.get("database");
            mysqlUsername = (String) mysql.get("username");
            mysqlPassword = (String) mysql.get("password");
            poolSize = (Integer) mysql.get("pool-size");
            
            // Parse server settings
            fallbackServer = (String) config.get("fallback-server");
            blacklistedServers = (List<String>) config.getOrDefault("blacklisted-servers", Collections.emptyList());
            inactiveDays = (Integer) config.getOrDefault("inactive-days", 5);
            bypassPermission = (String) config.get("bypass-permission");
            firstJoinServer = (String) config.get("first-join-server");
            
            // Parse maintenance settings
            Map<String, Object> maintenance = (Map<String, Object>) config.get("maintenance");
            maintenanceEnabled = (Boolean) maintenance.getOrDefault("enabled", false);
            maintenanceServer = (String) maintenance.get("maintenance-server");
            onEnableCommands = (List<String>) maintenance.getOrDefault("on-enable-commands", Collections.emptyList());
            onDisableCommands = (List<String>) maintenance.getOrDefault("on-disable-commands", Collections.emptyList());
            
            // Parse messages
            Map<String, String> msgConfig = (Map<String, String>) config.get("messages");
            messages.clear();
            messages.putAll(msgConfig);
            
            // Parse debug
            debug = (Boolean) config.get("debug");
            
            return true;
        } catch (IOException e) {
            plugin.getLogger().error("Failed to load configuration", e);
            return false;
        } catch (Exception e) {
            plugin.getLogger().error("Failed to parse configuration", e);
            return false;
        }
    }

    // Getters
    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public String getFallbackServer() {
        return fallbackServer;
    }

    public List<String> getBlacklistedServers() {
        return blacklistedServers;
    }

    public int getInactiveDays() {
        return inactiveDays;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }

    public String getFirstJoinServer() {
        return firstJoinServer;
    }

    public boolean isMaintenanceEnabled() {
        return maintenanceEnabled;
    }

    public void setMaintenanceEnabled(boolean enabled) {
        this.maintenanceEnabled = enabled;
    }

    public String getMaintenanceServer() {
        return maintenanceServer;
    }

    public List<String> getOnEnableCommands() {
        return onEnableCommands;
    }

    public List<String> getOnDisableCommands() {
        return onDisableCommands;
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "<red>Missing message: " + key + "</red>");
    }

    public boolean isDebug() {
        return debug;
    }
}