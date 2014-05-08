/* ==========================================================
File:        WakaTime.java
Description: Automatic time tracking for JetBrains IDEs.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.AppTopics;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.PlatformUtils;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

public class WakaTime implements ApplicationComponent {

    public static final String VERSION = "2.0.2";
    public static final String CONFIG = ".wakatime.cfg";
    public static final long FREQUENCY = 2; // minutes between pings
    public static final Logger log = Logger.getInstance("WakaTime");

    public static String IDE_NAME;
    public static String IDE_VERSION;
    public static MessageBusConnection connection;

    public static String lastFile = null;
    public static long lastTime = 0;

    public WakaTime() {
    }

    public void initComponent() {
        log.info("Initializing WakaTime plugin v" + VERSION + " (https://wakatime.com/)");

        // Set runtime constants
        IDE_NAME = PlatformUtils.getPlatformPrefix();
        IDE_VERSION = ApplicationInfo.getInstance().getFullVersion();

        if (!Dependencies.isCLIInstalled()) {
            log.info("Downloading and installing wakatime-cli ...");
            Dependencies.installCLI();
        } else if (Dependencies.isCLIOld()) {
            log.info("Upgrading wakatime-cli ...");
            Dependencies.upgradeCLI();
        }

        if (Dependencies.isPythonInstalled()) {
            log.debug("Python location: " + Dependencies.getPythonLocation());
            log.debug("CLI location: " + Dependencies.getCLILocation());

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
            Messages.showErrorDialog("WakaTime requires Python to be installed.\nYou can install it from https://www.python.org/downloads/\nAfter installing Python, restart your IDE.", "Error");
        }
    }

    public void disposeComponent() {
        connection.disconnect();
    }

    public static void logFile(String file, boolean isWrite) {
        ArrayList<String> cmds = new ArrayList<String>();
        cmds.add(Dependencies.getPythonLocation());
        cmds.add(Dependencies.getCLILocation());
        cmds.add("--file");
        cmds.add(file);
        String project = WakaTime.getProjectName();
        if (project != null) {
            cmds.add("--project");
            cmds.add(project);
        }
        cmds.add("--plugin");
        cmds.add(IDE_NAME+"/"+IDE_VERSION+" "+IDE_NAME+"-wakatime/"+VERSION);
        if (isWrite)
            cmds.add("--write");
        try {
            log.debug("Executing CLI: " + Arrays.toString(cmds.toArray()));
            Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
        } catch (Exception e) {
            log.error(e);
        }
    }

    public static String getProjectName() {
        DataContext dataContext = DataManager.getInstance().getDataContext();
        if (dataContext != null) {
            Project project = CommonDataKeys.PROJECT.getData(dataContext);
            if (project != null) {
                return project.getName();
            }
        }
        return null;
    }

    public static boolean enoughTimePassed(long currentTime) {
        return WakaTime.lastTime + FREQUENCY * 60 < currentTime;
    }

    @NotNull
    public String getComponentName() {
        return "WakaTime";
    }
}
