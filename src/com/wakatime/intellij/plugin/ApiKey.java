/* ==========================================================
File:        ApiKey.java
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
import java.io.*;
import java.util.UUID;

public class ApiKey extends DialogWrapper {
    private final JPanel panel;
    private final JTextField input;
    private static String _api_key = "";

    public ApiKey(@Nullable Project project) {
        super(project, true);
        setTitle("WakaTime API Key");
        setOKButtonText("Save");
        panel = new JPanel();
        input = new JTextField(36);
        panel.add(input);

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return panel;
    }

    @Override
    protected ValidationInfo doValidate() {
        String apiKey = input.getText();
        try {
            UUID.fromString(apiKey);
        } catch (Exception e) {
            return new ValidationInfo("Invalid api key.");
        }
        return null;
    }

    @Override
    public void doOKAction() {
        ApiKey.setApiKey(input.getText());
        super.doOKAction();
    }

    public String promptForApiKey() {
        input.setText(ApiKey.getApiKey());
        this.show();
        return input.getText();
    }

    public static String getApiKey() {
        if (!ApiKey._api_key.equals("")) {
            return ApiKey._api_key;
        }
        String apiKey = "";
        File userHome = new File(System.getProperty("user.home"));
        File configFile = new File(userHome, WakaTime.CONFIG);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(configFile.getAbsolutePath()));
        } catch (FileNotFoundException e1) {}
        if (br != null) {
            try {
                String line = br.readLine();
                while (line != null) {
                    String[] parts = line.split("=");
                    if (parts.length == 2 && parts[0].trim().equals("api_key")) {
                        apiKey = parts[1].trim();
                    }
                    line = br.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        ApiKey._api_key = apiKey;
        return apiKey;
    }

    private static void setApiKey(String apiKey) {
        File userHome = new File(System.getProperty("user.home"));
        File configFile = new File(userHome, WakaTime.CONFIG);
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        try {
            br = new BufferedReader(new FileReader(configFile.getAbsolutePath()));
        } catch (FileNotFoundException e1) {
        }
        if (br != null) {
            try {
                String line = br.readLine();
                while (line != null) {
                    String[] parts = line.split("=");
                    if (parts.length == 2 && parts[0].trim().equals("api_key")) {
                        found = true;
                        sb.append("api_key = " + apiKey + "\n");
                    } else {
                        sb.append(line + "\n");
                    }
                    line = br.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (!found) {
            sb = new StringBuilder();
            sb.append("[settings]\n");
            sb.append("api_key = " + apiKey + "\n");
            sb.append("debug = false\n");
        }
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(configFile.getAbsolutePath(), "UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (writer != null) {
            writer.print(sb.toString());
            writer.close();
        }
        ApiKey._api_key = apiKey;
    }

}
