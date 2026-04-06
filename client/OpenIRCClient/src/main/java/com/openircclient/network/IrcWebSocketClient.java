package com.openircclient.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openircclient.Openircclient;
import com.openircclient.utils.ChatUtils;
import com.openircclient.utils.CryptoUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class IrcWebSocketClient extends WebSocketClient {
    private final HandshakeManager handshakeManager;
    private java.util.Timer heartbeatTimer;
    private Consumer<JsonObject> messageHandler;

    public IrcWebSocketClient(URI serverUri) throws Exception {
        super(serverUri);
        this.setProxy(Proxy.NO_PROXY);
        // 关键点：禁用库内置的60秒超时检测，依靠我们自己的20秒HEARTBEAT维持连接
        this.setConnectionLostTimeout(0);
        this.handshakeManager = new HandshakeManager();
    }

    public void setMessageHandler(Consumer<JsonObject> handler) {
        this.messageHandler = handler;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        JsonObject init = new JsonObject();
        init.addProperty("type", "HANDSHAKE_INIT");
        init.addProperty("key", handshakeManager.getPublicKeyBase64());
        send(init.toString());
        startHeartbeat();
    }

    @Override
    public void onMessage(String message) {
        try {
            if (!handshakeManager.isCompleted()) {
                JsonObject resp = JsonParser.parseString(message).getAsJsonObject();
                if (resp.has("type") && resp.get("type").getAsString().equals("HANDSHAKE_RESP")) {
                    handshakeManager.completeHandshake(resp.get("key").getAsString());
                }
            } else {
                String decrypted = CryptoUtils.decrypt(message, handshakeManager.getSessionKey());
                JsonObject json = JsonParser.parseString(decrypted).getAsJsonObject();

                if (json.has("r_type")) {
                    String rType = json.get("r_type").getAsString();
                    switch (rType) {
                        case "IRC_SAY" -> handleIrcSay(json);
                        case "MC_NAME_LIST" -> handleMcNameList(json);
                        case "CRITICAL_ACTION" -> handleCriticalAction(json);
                    }
                }

                if (json.has("status") && json.get("status").getAsString().equals("success")) {
                    updateSessionData(json);
                }

                if (messageHandler != null) messageHandler.accept(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startHeartbeat() {
        if (heartbeatTimer != null) heartbeatTimer.cancel();
        heartbeatTimer = new java.util.Timer("OpenIRC-Heartbeat");
        heartbeatTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                if (isOpen() && handshakeManager.isCompleted()) {
                    JsonObject ping = new JsonObject();
                    ping.addProperty("r_type", "HEARTBEAT");
                    sendEncrypted(ping);
                }
            }
        }, 20000, 20000);
    }

    private void handleIrcSay(JsonObject json) {
        String sender = json.get("sender").getAsString();
        String content = json.get("content").getAsString();
        if (sender.equals("§rConsole")) {
            ChatUtils.sendMessageWithPrefix("§rConsole >> " + content);
        } else {
            String group = json.has("group") ? json.get("group").getAsString() : "user";
            String tag = json.has("tag") ? json.get("tag").getAsString() : "";
            ChatUtils.sendMessageWithPrefix("§b[" + group + "]§r[" + tag + "§r] §b" + sender + " §r>> " + content);
        }
    }

    private void handleMcNameList(JsonObject json) {
        JsonObject mapping = json.getAsJsonObject("mapping");
        Map<String, String> tempMap = new HashMap<>();
        for (String key : mapping.keySet()) {
            tempMap.put(key, mapping.get(key).getAsString());
        }
        Openircclient.updateMcNameMap(tempMap);
    }

    private void handleCriticalAction(JsonObject json) {
        if (json.get("action").getAsString().equals("FORCE_EXIT")) System.exit(1);
    }

    private void updateSessionData(JsonObject json) {
        if (json.has("username")) Openircclient.currentUsername = json.get("username").getAsString();
        if (json.has("group")) Openircclient.currentGroup = json.get("group").getAsString();
        if (json.has("tag")) Openircclient.currentTag = json.get("tag").getAsString();
        if (json.has("message") && json.get("message").getAsString().contains("成功")) {
            Openircclient.isAuthorized = true;
            Openircclient.trySyncMcName();
        }
    }

    public void sendEncrypted(JsonObject json) {
        try {
            if (handshakeManager.isCompleted()) {
                send(CryptoUtils.encrypt(json.toString(), handshakeManager.getSessionKey()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
        if (com.openircclient.Openircclient.isAuthorized) {
            com.openircclient.utils.ReconnectionManager.start();
        }
    }
    @Override public void onError(Exception ex) { ex.printStackTrace(); }
}