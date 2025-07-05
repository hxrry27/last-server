package com.example.lastserver;

import com.example.lastserver.commands.LastServerCommand;
import com.example.lastserver.config.Configuration;
import com.example.lastserver.database.MySQL;
import com.example.lastserver.database.ServerManager;
import com.example.lastserver.listeners.ConnectionListener;
import com.example.lastserver.listeners.ServerSwitchListener;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Plugin(
    id = "lastserver",
    name = "LastServer",
    version = "1.0.0",
    description = "Remember and return players to their last server",
    authors = {"YourName"}
)
public class LastServer {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private Configuration configuration;
    private MySQL mysql;
    private ServerManager serverManager;

    @Inject
    public LastServer(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Starting LastServer plugin...");
        
        // Load configuration
        configuration = new Configuration(this, dataDirectory);
        if (!configuration.load()) {
            logger.error("Failed to load configuration! Plugin will not function.");
            return;
        }
        
        // Initialize database
        mysql = new MySQL(this);
        if (!mysql.connect()) {
            logger.error("Failed to connect to MySQL! Plugin will not function.");
            return;
        }
        
        // Initialize server manager
        serverManager = new ServerManager(this);
        
        // Register listeners
        server.getEventManager().register(this, new ConnectionListener(this));
        server.getEventManager().register(this, new ServerSwitchListener(this));
        
        // Register commands
        CommandMeta commandMeta = server.getCommandManager()
            .metaBuilder("lastserver")
            .aliases("ls")
            .plugin(this)
            .build();
        
        server.getCommandManager().register(commandMeta, new LastServerCommand(this));
        
        // Schedule cleanup task (every 6 hours)
        server.getScheduler()
            .buildTask(this, () -> serverManager.cleanupOldEntries())
            .delay(1, TimeUnit.HOURS)
            .repeat(6, TimeUnit.HOURS)
            .schedule();
        
        logger.info("LastServer plugin loaded successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Shutting down LastServer plugin...");
        
        if (mysql != null) {
            mysql.close();
        }
        
        logger.info("LastServer plugin shutdown complete.");
    }

    public void reload() {
        logger.info("Reloading LastServer configuration...");
        
        if (configuration.load()) {
            // Reconnect to database if settings changed
            if (mysql != null) {
                mysql.close();
                mysql.connect();
            }
            logger.info("Configuration reloaded successfully!");
        } else {
            logger.error("Failed to reload configuration!");
        }
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public MySQL getMySQL() {
        return mysql;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }
}