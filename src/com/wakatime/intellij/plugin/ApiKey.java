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
import org.apache.commons.lang.StringEscapeUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.UUID;

public class ApiKey extends DialogWrapper {
    private final JPanel panel;
    private final JLabel label;
    private final JTextField input;
    private final LinkPane link;

    public ApiKey(@Nullable Project project) {
        super(project, true);
        setTitle("WakaTime API Key");
        setOKButtonText("Save");
        panel = new JPanel();
        panel.setLayout(new GridLayout(0,1));
        label  = new JLabel("Enter your WakaTime API key:", JLabel.CENTER);
        panel.add(label);
        input = new JTextField(36);
        panel.add(input);
        link = new LinkPane("https://wakatime.com/api-key");
        panel.add(link);

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
            UUID.fromString(apiKey.replaceFirst("^waka_", ""));
        } catch (Exception e) {
            return new ValidationInfo("Invalid api key.");
        }
        return null;
    }

    @Override
    public void doOKAction() {
        ConfigFile.setApiKey(input.getText());
        super.doOKAction();
    }

    public String promptForApiKey() {
        input.setText(ConfigFile.getApiKey());
        this.show();
        return input.getText();
    }

}

class LinkPane extends JTextPane {
    private final String url;

    public LinkPane(String url) {
        this.url = url;
        this.setEditable(false);
        this.addHyperlinkListener(new UrlHyperlinkListener());
        this.setContentType("text/html");
        this.setBackground(new Color(0,0,0,0));
        this.setText(url);
    }

    private class UrlHyperlinkListener implements HyperlinkListener {
        @Override
        public void hyperlinkUpdate(final HyperlinkEvent event) {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(event.getURL().toURI());
                } catch (final IOException e) {
                    throw new RuntimeException("Can't open URL", e);
                } catch (final URISyntaxException e) {
                    throw new RuntimeException("Can't open URL", e);
                }
            }
        }
    }

    @Override
    public void setText(final String text) {
        super.setText("<html><body style=\"text-align:center;\"><a href=\"" + url + "\">" + text + "</a></body></html>");
    }
}