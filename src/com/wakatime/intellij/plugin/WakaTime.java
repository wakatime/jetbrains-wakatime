/* ==========================================================
File:        WakaTime.java
Description: Automatic time tracking for JetBrains IDEs.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.AppTopics;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.PlatformUtils;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;

public class WakaTime implements ApplicationComponent {

    public static final String VERSION = "1.0.0";
    public static final String CONFIG = ".wakatime.cfg";
    public static final long FREQUENCY = 2; // minutes between pings

    public static String IDE_NAME;
    public static String IDE_VERSION;
    public static MessageBusConnection connection;

    public static String lastFile = null;
    public static long lastTime = 0;

    public WakaTime() {
    }

    public void initComponent() {
        System.out.printf("Initializing WakaTime plugin v%s (https://wakatime.com/)%n", VERSION);

        // Set runtime constants
        IDE_NAME = PlatformUtils.getPlatformPrefix();
        IDE_VERSION = ApplicationInfo.getInstance().getFullVersion();

        if (WakaTime.isWakaTimeCLIInstalled()) {

            // prompt for apiKey if it does not already exist
            if (ApiKey.getApiKey().equals("")) {
                Project project = ProjectManager.getInstance().getDefaultProject();
                ApiKey apiKey = new ApiKey(project);
                apiKey.promptForApiKey();
            }

            // add WakaTime item to File menu
            ActionManager am = ActionManager.getInstance();
            PluginMenu action = new PluginMenu();
            am.registerAction("WakaTimeApiKey", action);
            DefaultActionGroup fileMenu = (DefaultActionGroup) am.getAction("FileMenu");
            fileMenu.addSeparator();
            fileMenu.add(action);

            // Setup message listeners
            MessageBus bus = ApplicationManager.getApplication().getMessageBus();
            connection = bus.connect();
            connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new CustomSaveListener());
            EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new CustomDocumentListener());

        } else {
            Messages.showErrorDialog("WakaTime requires the wakatime python package to be installed.\nYou can install it from https://pypi.python.org/pypi/wakatime\nAfter installing, restart your IDE.", "Error");
        }
    }

    public void disposeComponent() {
        connection.disconnect();
    }

    public static void logFile(String file, boolean isWrite) {
        ArrayList<String> cmds = new ArrayList<String>();
        cmds.add(WakaTime.findWakaTimeCLI());
        cmds.add("--file");
        cmds.add(file);
        cmds.add("--plugin");
        cmds.add(IDE_NAME+"/"+IDE_VERSION+" "+IDE_NAME+"-wakatime/"+VERSION);
        if (isWrite)
            cmds.add("--write");
        try {
            Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean enoughTimePassed(long currentTime) {
        return WakaTime.lastTime + FREQUENCY * 60 < currentTime;
    }

    public static boolean isWakaTimeCLIInstalled() {
        return WakaTime.findWakaTimeCLI() != null;
    }

    public static String findWakaTimeCLI() {
        String cli = null;
        String []paths = new String[]{
            "wakatime",
            "/usr/bin/wakatime",
            "/usr/local/bin/wakatime",
        };
        for (int i=0; i<paths.length; i++) {
            try {
                Runtime.getRuntime().exec(paths[i]);
                cli = paths[i];
                break;
            } catch (IOException e) { }
        }
        return cli;
    }

    @NotNull
    public String getComponentName() {
        return "WakaTime";
    }
}
