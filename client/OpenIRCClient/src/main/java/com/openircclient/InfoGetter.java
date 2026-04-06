package com.openircclient;

public class InfoGetter {
    public static String CLIENT_VERSION = "b1";
    public static final String SERVER_IP = "127.0.0.1";
    public static final int SERVER_PORT = 13370;

    public static String getWsAddress() {
        return "ws://" + SERVER_IP + ":" + SERVER_PORT;
    }
}
