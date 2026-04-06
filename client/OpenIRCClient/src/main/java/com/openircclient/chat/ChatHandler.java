package com.openircclient.chat;

import com.google.gson.JsonObject;
import com.openircclient.Openircclient;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

public class ChatHandler {
    public static void init() {
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (message.startsWith("#")) {
                if (Openircclient.isAuthorized && Openircclient.networkClient != null && Openircclient.networkClient.isOpen()) {
                    String content = message.substring(1).trim();
                    if (!content.isEmpty()) {
                        sendIrcMessage(content);
                    }
                } else {
                    com.openircclient.utils.ChatUtils.sendMessageWithPrefix("§c你尚未登录IRC服务器，无法发送消息");
                }
                return false;
            }
            return true;
        });
    }

    private static void sendIrcMessage(String content) {
        JsonObject json = new JsonObject();
        json.addProperty("r_type", "CHAT");
        json.addProperty("content", content);
        Openircclient.networkClient.sendEncrypted(json);
    }
}