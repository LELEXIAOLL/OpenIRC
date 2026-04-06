package com.openircclient.utils;

import java.io.*;
import java.nio.file.*;
import java.util.List;

public class ConfigUtils {
    private static final String FILE_NAME = "user.txt";

    public static void saveConfig(int mode, String user, String pass) {
        try {
            List<String> lines = List.of(String.valueOf(mode), user, pass);
            Files.write(Paths.get(FILE_NAME), lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String[] loadConfig() {
        try {
            Path path = Paths.get(FILE_NAME);
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                if (lines.size() >= 3) {
                    return new String[]{lines.get(0), lines.get(1), lines.get(2)};
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}