package com.openircclient.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class HWIDUtils {
    public static String getHWID() {
        try {
            String sn = getWmiProperty("baseboard", "serialnumber");
            String cpu = getWmiProperty("cpu", "processorid");
            return HashUtils.sha256(sn + cpu);
        } catch (Exception e) {
            return "UNKNOWN_HWID";
        }
    }

    private static String getWmiProperty(String type, String property) throws Exception {
        Process process = Runtime.getRuntime().exec("wmic " + type + " get " + property);
        process.getOutputStream().close();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return reader.lines().skip(1).findFirst().orElse("").trim();
        }
    }
}