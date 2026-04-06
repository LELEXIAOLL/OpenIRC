package com.openircclient;

import com.openircclient.network.IrcWebSocketClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class Openircclient implements ModInitializer, ClientModInitializer {
    public static volatile IrcWebSocketClient networkClient;
    public static String currentUsername = "";
    public static String currentPasswordHash = "";
    public static String currentTag = "";
    public static String currentGroup = "";
    public static boolean isAuthorized = false;

    private static final Map<String, String> ircToMcNameMap = new HashMap<>();

    @Override
    public void onInitialize() {}

    @Override
    public void onInitializeClient() {
        initConnection();
        com.openircclient.chat.ChatHandler.init();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            trySyncMcName();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (networkClient != null) networkClient.close();
        }));
    }

    public static void trySyncMcName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        if (isAuthorized && networkClient != null && networkClient.isOpen() && client.player != null) {
            try {
                String mcName = client.getSession().getUsername();
                com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                json.addProperty("r_type", "SYNC_MC_NAME");
                json.addProperty("mc_name", mcName);

                networkClient.sendEncrypted(json);
                System.out.println("[OpenIRC] Sent MC Name Sync: " + mcName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static synchronized void updateMcNameMap(Map<String, String> newMap) {
        ircToMcNameMap.clear();
        ircToMcNameMap.putAll(newMap);
    }

    public static Map<String, String> getOnlineMapping() {
        return new HashMap<>(ircToMcNameMap);
    }

    public static synchronized void initConnection() {
        if (networkClient != null && networkClient.isOpen()) return;
        try {
            networkClient = new IrcWebSocketClient(new URI(InfoGetter.getWsAddress()));
            new Thread(() -> {
                try { networkClient.connectBlocking(); } catch (Exception e) { e.printStackTrace(); }
            }).start();
        } catch (Exception e) { e.printStackTrace(); }
    }
}