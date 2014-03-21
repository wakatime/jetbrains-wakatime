package com.wakatime.intellij.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

/**
 * Created by alanhamlett on 3/20/14.
 */
public class PluginMenu extends AnAction {
    public PluginMenu() {
        super("WakaTime API Key");
        // super("WakaTime API Key", "", IconLoader.getIcon("/Mypackage/icon.png"));
    }
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        ApiKey apiKey = new ApiKey(project);
        apiKey.promptForApiKey();
    }
}
