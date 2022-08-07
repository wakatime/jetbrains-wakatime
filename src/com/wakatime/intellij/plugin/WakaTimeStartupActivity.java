package com.wakatime.intellij.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class WakaTimeStartupActivity implements StartupActivity.Background {

    @Override
    public void runActivity(@NotNull Project project) {
        WakaTime.checkApiKey();
    }
}