package com.openircclient.windows;

import javax.swing.*;
import java.awt.*;

public class InfoDisplayWindow {
    public static void show(String user, String exp, String group, String tag, Runnable onFinish) {
        JFrame frame = new JFrame("验证信息");
        frame.setSize(350, 250);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new GridLayout(6, 1));
        frame.setAlwaysOnTop(true);

        frame.add(new JLabel(" 欢迎回来: " + user, SwingConstants.CENTER));
        frame.add(new JLabel(" 到期时间: " + exp, SwingConstants.CENTER));
        frame.add(new JLabel(" 用户组: " + group, SwingConstants.CENTER));
        frame.add(new JLabel(" 称号: " + tag, SwingConstants.CENTER));

        JLabel timerLabel = new JLabel(" 2 秒后进入游戏...", SwingConstants.CENTER);
        timerLabel.setForeground(Color.BLUE);
        frame.add(timerLabel);

        frame.setVisible(true);

        Timer timer = new Timer(1000, null);
        final int[] seconds = {2};
        timer.addActionListener(e -> {
            seconds[0]--;
            if (seconds[0] <= 0) {
                timer.stop();
                frame.dispose();
                onFinish.run();
            } else {
                timerLabel.setText(" " + seconds[0] + " 秒后进入游戏...");
            }
        });
        timer.start();
    }
}