package com.example.lastserver.commands;

import com.example.lastserver.LastServer;
import com.example.lastserver.utils.MessageUtil;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LastServerCommand implements SimpleCommand {
    private final LastServer plugin;
    private final ConcurrentHashMap<String, Long> commandCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_TIME = TimeUnit.SECONDS.toMillis(1);

    public LastServerCommand(LastServer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof @SuppressWarnings("unused") Player player)) {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                source.sendMessage(MessageUtil.format("<yellow>LastServer Commands:</yellow>"));
                source.sendMessage(MessageUtil.format("<gray>/lastserver reload - Reload configuration</gray>"));
                source.sendMessage(MessageUtil.format("<gray>/lastserver maintenance <on|off> - Toggle maintenance mode</gray>"));
                source.sendMessage(MessageUtil.format("<gray>/lastserver info <player> - Check a player's last server</gray>"));
                return;
            }
        }

        String identifier = source instanceof Player ? ((Player) source).getUniqueId().toString() : "console";
        
        if (isOnCooldown(identifier)) {
            source.sendMessage(MessageUtil.format("<red>Please wait before using this command again.</red>"));
            return;
        }

        setCooldown(identifier);

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            source.sendMessage(MessageUtil.format("<yellow>LastServer Commands:</yellow>"));
            source.sendMessage(MessageUtil.format("<gray>/lastserver reload - Reload configuration</gray>"));
            source.sendMessage(MessageUtil.format("<gray>/lastserver maintenance <on|off> - Toggle maintenance mode</gray>"));
            source.sendMessage(MessageUtil.format("<gray>/lastserver info <player> - Check a player's last server</gray>"));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(source);
            case "maintenance" -> handleMaintenance(source, args);
            case "info" -> handleInfo(source, args);
            default -> source.sendMessage(MessageUtil.format("<red>Unknown command. Use /lastserver help for usage.</red>"));
        }
    }

    private void handleReload(CommandSource source) {
        if (!source.hasPermission("lastserver.admin.reload")) {
            source.sendMessage(MessageUtil.format("<red>You don't have permission to use this command.</red>"));
            return;
        }

        CompletableFuture.runAsync(() -> {
            plugin.reload();
            source.sendMessage(MessageUtil.format("<green>LastServer configuration reloaded successfully!</green>"));
        }).exceptionally(throwable -> {
            source.sendMessage(MessageUtil.format("<red>Failed to reload configuration: " + throwable.getMessage() + "</red>"));
            return null;
        });
    }

    private void handleMaintenance(CommandSource source, String[] args) {
        if (!source.hasPermission("lastserver.admin.maintenance")) {
            source.sendMessage(MessageUtil.format("<red>You don't have permission to use this command.</red>"));
            return;
        }

        if (args.length < 2) {
            source.sendMessage(MessageUtil.format("<red>Usage: /lastserver maintenance <on|off></red>"));
            return;
        }

        boolean enable = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
        boolean disable = args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("false");

        if (!enable && !disable) {
            source.sendMessage(MessageUtil.format("<red>Usage: /lastserver maintenance <on|off></red>"));
            return;
        }

        plugin.getConfiguration().setMaintenanceEnabled(enable);

        if (enable) {
            source.sendMessage(MessageUtil.format("<yellow>Maintenance mode enabled. All new players will be sent to the maintenance server.</yellow>"));
            executeCommands(plugin.getConfiguration().getOnEnableCommands(), source);
        } else {
            source.sendMessage(MessageUtil.format("<green>Maintenance mode disabled.</green>"));
            executeCommands(plugin.getConfiguration().getOnDisableCommands(), source);
        }
    }

    private void handleInfo(CommandSource source, String[] args) {
        if (!source.hasPermission("lastserver.admin.info")) {
            source.sendMessage(MessageUtil.format("<red>You don't have permission to use this command.</red>"));
            return;
        }

        if (args.length < 2) {
            source.sendMessage(MessageUtil.format("<red>Usage: /lastserver info <player></red>"));
            return;
        }

        String playerName = args[1];
        
        plugin.getMySQL().getPlayerLastServer(playerName).thenAccept(serverName -> {
            if (serverName != null) {
                source.sendMessage(MessageUtil.format(
                    "<green>Player <yellow>" + playerName + "</yellow> was last seen on server: <yellow>" + serverName + "</yellow></green>"
                ));
            } else {
                source.sendMessage(MessageUtil.format(
                    "<red>No last server found for player: <yellow>" + playerName + "</yellow></red>"
                ));
            }
        }).exceptionally(throwable -> {
            source.sendMessage(MessageUtil.format("<red>Failed to lookup player information: " + throwable.getMessage() + "</red>"));
            return null;
        });
    }

    private void executeCommands(List<String> commands, CommandSource source) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        for (String command : commands) {
            if (command.startsWith("velocity ")) {
                String velocityCommand = command.substring(9);
                plugin.getServer().getCommandManager().executeAsync(source, velocityCommand);
            } else {
                plugin.getServer().getCommandManager().executeAsync(source, command);
            }
        }
    }

    private boolean isOnCooldown(String identifier) {
        Long lastUsed = commandCooldowns.get(identifier);
        if (lastUsed == null) {
            return false;
        }
        return System.currentTimeMillis() - lastUsed < COOLDOWN_TIME;
    }

    private void setCooldown(String identifier) {
        commandCooldowns.put(identifier, System.currentTimeMillis());
        
        plugin.getServer().getScheduler()
            .buildTask(plugin, () -> commandCooldowns.remove(identifier))
            .delay(COOLDOWN_TIME + 100, TimeUnit.MILLISECONDS)
            .schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length == 0) {
            return List.of("reload", "maintenance", "info", "help");
        }
        
        if (args.length == 1) {
            return List.of("reload", "maintenance", "info", "help").stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) {
            return List.of("on", "off").stream()
                .filter(option -> option.startsWith(args[1].toLowerCase()))
                .toList();
        }
        
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        CommandSource source = invocation.source();
        return source.hasPermission("lastserver.admin.reload") || 
               source.hasPermission("lastserver.admin.maintenance") || 
               source.hasPermission("lastserver.admin.info");
    }
}