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
import com.intellij.openapi.actionSystem.*;
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

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.log4j.Level;

public class WakaTime implements ApplicationComponent {

    public static final String VERSION = "4.0.13";
    public static final String CONFIG = ".wakatime.cfg";
    public static final long FREQUENCY = 2; // minutes between pings
    public static final Logger log = Logger.getInstance("WakaTime");

    public static String IDE_NAME;
    public static String IDE_VERSION;
    public static MessageBusConnection connection;
    public static Boolean DEBUG = false;

    public static Boolean READY = false;
    public static String lastFile = null;
    public static long lastTime = 0;

    public WakaTime() {
    }

    public void initComponent() {
        log.info("Initializing WakaTime plugin v" + VERSION + " (https://wakatime.com/)");
        //System.out.println("Initializing WakaTime plugin v" + VERSION + " (https://wakatime.com/)");

        // Set runtime constants
        IDE_NAME = PlatformUtils.getPlatformPrefix();
        IDE_VERSION = ApplicationInfo.getInstance().getFullVersion();

        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                    public void run() {
                        if (!Dependencies.isCLIInstalled()) {
                            log.info("Downloading and installing wakatime-cli ...");
                            Dependencies.installCLI();
                            WakaTime.READY = true;
                            log.info("Finished downloading and installing wakatime-cli.");
                        } else if (Dependencies.isCLIOld()) {
                            log.info("Upgrading wakatime-cli ...");
                            Dependencies.upgradeCLI();
                            WakaTime.READY = true;
                            log.info("Finished upgrading wakatime-cli.");
                        } else {
                            WakaTime.READY = true;
                            log.info("wakatime-cli is up to date.");
                        }
                    }
                });
            }
        });

        if (Dependencies.isPythonInstalled()) {

            WakaTime.DEBUG = WakaTime.isDebugEnabled();
            if (WakaTime.DEBUG) {
                log.setLevel(Level.DEBUG);
                log.debug("Logging level set to DEBUG");
            }

            log.debug("Python location: " + Dependencies.getPythonLocation());
            log.debug("CLI location: " + Dependencies.getCLILocation());

            // prompt for apiKey if it does not already exist
            Project project = ProjectManager.getInstance().getDefaultProject();
            ApiKey apiKey = new ApiKey(project);
            if (apiKey.getApiKey().equals("")) {
                apiKey.promptForApiKey();
            }
            log.debug("Api Key: "+ApiKey.getApiKey());

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

            log.info("Finished initializing WakaTime plugin");

        } else {

            ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
                public void run() {
                    ApplicationManager.getApplication().runReadAction(new Runnable() {
                        public void run() {

                            log.info("Python not found, downloading python...");

                            // download and install python
                            Dependencies.installPython();

                            if (Dependencies.isPythonInstalled()) {
                                log.info("Finished installing python...");
                            } else {
                                ApplicationManager.getApplication().invokeLater(new Runnable(){
                                    public void run(){
                                        Messages.showErrorDialog("WakaTime requires Python to be installed.\nYou can install it from https://www.python.org/downloads/\nAfter installing Python, restart your IDE.", "Error");
                                    }
                                });
                            }
                        }
                    });
                }
            });
        }
    }

    public void disposeComponent() {
        try {
            connection.disconnect();
        } catch(Exception e) { }
    }

    public static void logFile(final String file, final boolean isWrite) {
        if (WakaTime.READY) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    WakaTime.executeCLI(file, isWrite, 0);
                }
            });
        }
    }

    public static void executeCLI(String file, boolean isWrite, int tries) {
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
            Process proc = Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
            if (WakaTime.DEBUG) {
                BufferedReader stdInput = new BufferedReader(new
                        InputStreamReader(proc.getInputStream()));
                BufferedReader stdError = new BufferedReader(new
                        InputStreamReader(proc.getErrorStream()));
                proc.waitFor();
                String s;
                while ((s = stdInput.readLine()) != null) {
                    log.debug(s);
                }
                while ((s = stdError.readLine()) != null) {
                    log.debug(s);
                }
                log.debug("Command finished with return value: "+proc.exitValue());
            }
        } catch (Exception e) {
            if (tries < 3) {
                log.debug(e);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e1) {
                    log.error(e1);
                }
                WakaTime.executeCLI(file, isWrite, tries+1);
            } else {
                log.error(e);
            }
        }
    }

    public static String getProjectName() {
        DataContext dataContext = DataManager.getInstance().getDataContext();
        if (dataContext != null) {
            Project project = null;

            try {
                project = PlatformDataKeys.PROJECT.getData(dataContext);
            } catch (NoClassDefFoundError e) {
                try {
                    project = DataKeys.PROJECT.getData(dataContext);
                } catch (NoClassDefFoundError ex) { }
            }
            if (project != null) {
                return project.getName();
            }
        }
        return null;
    }

    public static boolean enoughTimePassed(long currentTime) {
        return WakaTime.lastTime + FREQUENCY * 60 < currentTime;
    }

    public static boolean shouldLogFile(String file) {
        if (file.equals("atlassian-ide-plugin.xml") || file.contains("/.idea/workspace.xml")) {
            return false;
        }
        return true;
    }

    public static Boolean isDebugEnabled() {
        Boolean debug = false;
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
                    if (parts.length == 2 && parts[0].trim().equals("debug") && parts[1].trim().toLowerCase().equals("true")) {
                        debug = true;
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
        return debug;
    }

    @NotNull
    public String getComponentName() {
        return "WakaTime";
    }
}
