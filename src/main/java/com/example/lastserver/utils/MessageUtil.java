package com.example.lastserver.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class MessageUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static Component format(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    public static Component format(String message, String placeholder, String replacement) {
        return MINI_MESSAGE.deserialize(message.replace(placeholder, replacement));
    }

    public static Component formatWithServer(String message, String serverName) {
        return format(message, "{server}", serverName);
    }

    public static Component formatWithPlayer(String message, String playerName) {
        return format(message, "{player}", playerName);
    }
}