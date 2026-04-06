package com.openircclient.utils;

import com.google.gson.JsonObject;
import com.openircclient.Openircclient;
import java.util.Timer;
import java.util.TimerTask;

public class ReconnectionManager {
    private static Timer timer;
    private static boolean isReconnecting = false;

    public static void start() {
        if (isReconnecting || !Openircclient.isAuthorized) return;
        isReconnecting = true;

        ChatUtils.sendMessageWithPrefix("§c与服务器断开连接，正在重连...");

        timer = new Timer("OpenIRC-Reconnect-Timer");
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (Openircclient.networkClient != null && Openircclient.networkClient.isOpen()) {
                        stop();
                        return;
                    }

                    Openircclient.initConnection();
                    Thread.sleep(1500);

                    if (Openircclient.networkClient != null && Openircclient.networkClient.isOpen()) {
                        Thread.sleep(1000);
                        autoLogin();
                    }
                } catch (Exception e) {
                }
            }
        }, 3000, 3000);
    }

    private static void autoLogin() {
        if (Openircclient.currentUsername.isEmpty()) return;

        JsonObject req = new JsonObject();
        req.addProperty("r_type", "LOGIN");
        req.addProperty("username", Openircclient.currentUsername);
        req.addProperty("password", Openircclient.currentPasswordHash);
        req.addProperty("hwid", HWIDUtils.getHWID());

        Openircclient.networkClient.sendEncrypted(req);
        ChatUtils.sendMessageWithPrefix("§a重连成功!");
        stop();
    }

    public static void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        isReconnecting = false;
    }
}