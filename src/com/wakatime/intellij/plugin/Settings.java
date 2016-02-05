/* ==========================================================
File:        Settings.java
Description: Prompts user for api key if it does not exist.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;

public class Settings extends DialogWrapper {
    private final JPanel panel;
    private final JLabel apiKeyLabel;
    private final JTextField apiKey;
    private final JLabel proxyLabel;
    private final JTextField proxy;
    private final JLabel debugLabel;
    private final JCheckBox debug;

    public Settings(@Nullable Project project) {
        super(project, true);
        setTitle("WakaTime Settings");
        setOKButtonText("Save");
        panel = new JPanel();
        panel.setLayout(new GridLayout(0,2));

        apiKeyLabel = new JLabel("API Key:", JLabel.CENTER);
        panel.add(apiKeyLabel);
        apiKey = new JTextField(36);
        apiKey.setText(ApiKey.getApiKey());
        panel.add(apiKey);

        proxyLabel = new JLabel("Proxy:", JLabel.CENTER);
        panel.add(proxyLabel);
        proxy = new JTextField();
        String p = ConfigFile.get("settings", "proxy");
        if (p == null) p = "";
        proxy.setText(p);
        panel.add(proxy);

        debugLabel = new JLabel("Debug:", JLabel.CENTER);
        panel.add(debugLabel);
        String val = ConfigFile.get("settings", "debug");
        debug = new JCheckBox();
        debug.setSelected(val != null && val.trim().toLowerCase().equals("true"));
        panel.add(debug);

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return panel;
    }

    @Override
    protected ValidationInfo doValidate() {
        try {
            UUID.fromString(apiKey.getText());
        } catch (Exception e) {
            return new ValidationInfo("Invalid api key.");
        }
        return null;
    }

    @Override
    public void doOKAction() {
        ApiKey.setApiKey(apiKey.getText());
        ConfigFile.set("settings", "proxy", proxy.getText());
        String val = "false";
        if (debug.isSelected()) val = "true";
        ConfigFile.set("settings", "debug", val);
        WakaTime.setupDebugging();
        WakaTime.setLoggingLevel();
        super.doOKAction();
    }

}
