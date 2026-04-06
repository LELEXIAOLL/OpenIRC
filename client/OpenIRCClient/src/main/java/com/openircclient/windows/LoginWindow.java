package com.openircclient.windows;

import com.google.gson.JsonObject;
import com.openircclient.Openircclient;
import com.openircclient.utils.*;
import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

public class LoginWindow {
    private static final Object LOCK = new Object();
    private static boolean isAuthDone = false;
    private static JFrame frame;
    private static JLabel serverStatusLabel;
    private static JTextField userField;
    private static JPasswordField passField;
    private static JCheckBox rememberCheck;
    private static JCheckBox autoCheck;
    private static JButton loginBtn;

    public static void showBlocking() {
        com.openircclient.Openircclient.initConnection();
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(LoginWindow::createAndShowGUI);
        synchronized (LOCK) {
            while (!isAuthDone) {
                try { LOCK.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
    }

    private static void createAndShowGUI() {
        frame = new JFrame("OpenIRCClient - 认证中心");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(450, 380);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("登录", createLoginTab());
        tabs.addTab("注册", createRegisterTab());
        tabs.addTab("续费", createRenewTab());
        mainPanel.add(tabs, BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        JButton getHwidBtn = new JButton("获取机器码");
        getHwidBtn.setFocusPainted(false);
        getHwidBtn.addActionListener(e -> {
            String hwid = HWIDUtils.getHWID();
            ClipboardUtils.copyToClipboard(hwid);
            JTextField hf = new JTextField(hwid);
            hf.setEditable(false); hf.setBackground(null); hf.setBorder(null);
            JPanel mp = new JPanel(new BorderLayout(5, 5));
            mp.add(new JLabel("机器码已自动复制到剪切板："), BorderLayout.NORTH);
            mp.add(hf, BorderLayout.CENTER);
            JOptionPane.showMessageDialog(frame, mp, "机器码 (SHA256)", JOptionPane.INFORMATION_MESSAGE);
        });
        statusBar.add(getHwidBtn, BorderLayout.WEST);
        serverStatusLabel = new JLabel("IRCServer: 离线");
        serverStatusLabel.setForeground(Color.RED);
        statusBar.add(serverStatusLabel, BorderLayout.EAST);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        String[] cfg = ConfigUtils.loadConfig();
        if (cfg != null) {
            userField.setText(cfg[1]);
            passField.setText(cfg[2]);
            if (cfg[0].equals("1")) rememberCheck.setSelected(true);
            if (cfg[0].equals("2")) {
                autoCheck.setSelected(true);
                Timer t = new Timer(500, null);
                t.addActionListener(e -> {
                    if (Openircclient.networkClient != null && Openircclient.networkClient.isOpen()) {
                        loginBtn.doClick();
                        t.stop();
                    }
                });
                t.start();
            }
        }

        new Timer(1000, e -> updateStatus()).start();
        frame.add(mainPanel);
        frame.setVisible(true);
    }

    private static void updateStatus() {
        if (Openircclient.networkClient != null && Openircclient.networkClient.isOpen()) {
            serverStatusLabel.setText("IRCServer: 在线");
            serverStatusLabel.setForeground(new Color(0, 150, 0));
        } else {
            serverStatusLabel.setText("IRCServer: 离线");
            serverStatusLabel.setForeground(Color.RED);
        }
    }

    private static JPanel createLoginTab() {
        JPanel p = UiFactory.createPaddedPanel();
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL; g.insets = new Insets(5, 5, 5, 5);
        userField = new JTextField(); UiFactory.applyAlphanumericFilter(userField);
        passField = new JPasswordField();
        addComponent(p, new JLabel("用户名:"), userField, g, 0);
        addComponent(p, new JLabel("密  码:"), passField, g, 1);

        JLabel st = new JLabel(" ", SwingConstants.CENTER); st.setForeground(Color.RED);
        g.gridx = 0; g.gridy = 2; g.gridwidth = 2; p.add(st, g);

        loginBtn = new JButton("登 陆");
        g.gridy = 3; g.fill = GridBagConstraints.NONE; p.add(loginBtn, g);

        JPanel checks = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        rememberCheck = new JCheckBox("记住密码");
        autoCheck = new JCheckBox("自动登录");
        rememberCheck.addActionListener(e -> { if(rememberCheck.isSelected()) autoCheck.setSelected(false); });
        autoCheck.addActionListener(e -> { if(autoCheck.isSelected()) rememberCheck.setSelected(false); });
        checks.add(rememberCheck); checks.add(autoCheck);
        g.gridy = 4; g.fill = GridBagConstraints.HORIZONTAL; p.add(checks, g);

        loginBtn.addActionListener(e -> handleAction(st, loginBtn, "登 陆", () -> {
            String u = userField.getText().trim();
            String p1 = new String(passField.getPassword());
            if (u.isEmpty() || p1.isEmpty()) return error("请完整填写信息");
            JsonObject req = new JsonObject();
            req.addProperty("r_type", "LOGIN");
            req.addProperty("username", u);
            req.addProperty("password", HashUtils.sha256(p1));
            req.addProperty("hwid", HWIDUtils.getHWID());
            return req;
        }));
        return p;
    }

    private static JPanel createRegisterTab() {
        JPanel p = UiFactory.createPaddedPanel();
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL; g.insets = new Insets(4, 5, 4, 5);
        JTextField uF = new JTextField(); UiFactory.applyAlphanumericFilter(uF);
        JPasswordField p1 = new JPasswordField(); JPasswordField p2 = new JPasswordField();
        JTextField cF = new JTextField();
        addComponent(p, new JLabel("用户名:"), uF, g, 0);
        addComponent(p, new JLabel("密码:"), p1, g, 1);
        addComponent(p, new JLabel("确认密码:"), p2, g, 2);
        addComponent(p, new JLabel("卡密:"), cF, g, 3);
        addAuthAction(p, "注 册", g, 4, () -> {
            String u = uF.getText().trim(); String pw1 = new String(p1.getPassword());
            if (u.isEmpty() || pw1.isEmpty() || !pw1.equals(new String(p2.getPassword())) || cF.getText().trim().isEmpty()) return error("校验失败");
            JsonObject req = new JsonObject();
            req.addProperty("r_type", "REGISTER");
            req.addProperty("username", u);
            req.addProperty("password", HashUtils.sha256(pw1));
            req.addProperty("key", cF.getText().trim());
            req.addProperty("hwid", HWIDUtils.getHWID());
            return req;
        });
        return p;
    }

    private static JPanel createRenewTab() {
        JPanel p = UiFactory.createPaddedPanel();
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL; g.insets = new Insets(8, 5, 8, 5);
        JTextField uF = new JTextField(); JTextField cF = new JTextField();
        addComponent(p, new JLabel("用户名:"), uF, g, 0);
        addComponent(p, new JLabel("卡密:"), cF, g, 1);
        addAuthAction(p, "充 值", g, 2, () -> {
            if (uF.getText().trim().isEmpty() || cF.getText().trim().isEmpty()) return error("信息不全");
            JsonObject req = new JsonObject();
            req.addProperty("r_type", "RENEW");
            req.addProperty("username", uF.getText().trim());
            req.addProperty("key", cF.getText().trim());
            return req;
        });
        return p;
    }

    private static void handleAction(JLabel st, JButton b, String txt, Supplier<JsonObject> s) {
        JsonObject req = s.get();
        if (req == null || req.has("_internal_error")) { st.setText(req != null ? req.get("_internal_error").getAsString() : "错误"); return; }
        if (Openircclient.networkClient == null || !Openircclient.networkClient.isOpen()) { st.setText("未连接"); return; }
        b.setEnabled(false); st.setText("处理中...");
        Openircclient.networkClient.setMessageHandler(resp -> SwingUtilities.invokeLater(() -> {
            b.setEnabled(true);
            if (resp.get("status").getAsString().equals("success")) {
                if (txt.equals("登 陆") || txt.equals("注 册")) {
                    Openircclient.currentUsername = resp.get("username").getAsString();
                    Openircclient.currentPasswordHash = req.get("password").getAsString();
                    int m = autoCheck != null && autoCheck.isSelected() ? 2 : (rememberCheck != null && rememberCheck.isSelected() ? 1 : 0);
                    if (m > 0) ConfigUtils.saveConfig(m, userField.getText(), new String(passField.getPassword()));
                }
                if (txt.equals("充 值")) { st.setText("充值成功"); } else {
                    frame.dispose();
                    InfoDisplayWindow.show(resp.get("username").getAsString(), resp.get("expiredtime").getAsString(),
                            resp.get("group").getAsString(), resp.get("tag").getAsString(), LoginWindow::notifyAuthDone);
                }
            } else { st.setText(resp.get("message").getAsString()); }
        }));
        Openircclient.networkClient.sendEncrypted(req);
    }

    private static void addAuthAction(JPanel p, String txt, GridBagConstraints g, int r, Supplier<JsonObject> s) {
        JLabel st = new JLabel(" ", SwingConstants.CENTER); st.setForeground(Color.RED);
        g.gridx = 0; g.gridy = r; g.gridwidth = 2; p.add(st, g);
        JButton b = new JButton(txt); g.gridy = r + 1; g.fill = GridBagConstraints.NONE; p.add(b, g);
        b.addActionListener(e -> handleAction(st, b, txt, s));
    }

    private static void addComponent(JPanel p, JLabel l, JComponent c, GridBagConstraints g, int r) {
        g.gridwidth = 1; g.weightx = 0; g.gridx = 0; g.gridy = r; p.add(l, g);
        g.gridx = 1; g.weightx = 1.0; p.add(c, g);
    }

    private static JsonObject error(String m) { JsonObject o = new JsonObject(); o.addProperty("_internal_error", m); return o; }
    public static void notifyAuthDone() { synchronized (LOCK) { isAuthDone = true; LOCK.notifyAll(); } }
}