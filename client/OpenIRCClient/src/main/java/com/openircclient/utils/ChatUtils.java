package com.openircclient.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ChatUtils {
    private static final String PREFIX = "§b[OpenIRC] §r";

    public static void sendMessage(String message) {
        MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
    }

    public static void sendMessageWithPrefix(String message) {
        MinecraftClient.getInstance().player.sendMessage(Text.literal(PREFIX + message), false);
    }
}
