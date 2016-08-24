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
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.apache.log4j.Level;

public class WakaTime implements ApplicationComponent {

    public static final String VERSION = "7.0.9";
    public static final String CONFIG = ".wakatime.cfg";
    public static final BigDecimal FREQUENCY = new BigDecimal(2 * 60); // max secs between heartbeats for continuous coding
    public static final Logger log = Logger.getInstance("WakaTime");

    public static String IDE_NAME;
    public static String IDE_VERSION;
    public static MessageBusConnection connection;
    public static Boolean DEBUG = false;
    public static Boolean READY = false;
    public static String lastFile = null;
    public static BigDecimal lastTime = new BigDecimal(0);
    private static String lastProject = null;

    private final int queueTimeoutSeconds = 10;
    private static ConcurrentLinkedQueue<Heartbeat> heartbeatsQueue = new ConcurrentLinkedQueue<Heartbeat>();
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> scheduledFixture;

    public WakaTime() {
    }

    public void initComponent() {
        log.info("Initializing WakaTime plugin v" + VERSION + " (https://wakatime.com/)");
        //System.out.println("Initializing WakaTime plugin v" + VERSION + " (https://wakatime.com/)");

        // Set runtime constants
        IDE_NAME = PlatformUtils.getPlatformPrefix();
        IDE_VERSION = ApplicationInfo.getInstance().getFullVersion();

        setupDebugging();
        setLoggingLevel();

        Dependencies.configureProxy();

        checkApiKey();

        setupMenuItem();

        if (Dependencies.isPythonInstalled()) {

            checkCore();
            setupEventListeners();
            setupQueueProcessor();
            checkDebug();
            log.info("Finished initializing WakaTime plugin");

        } else {

            ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
                public void run() {
                    log.info("Python not found, downloading python...");

                    // download and install python
                    Dependencies.installPython();

                    if (Dependencies.isPythonInstalled()) {
                        log.info("Finished installing python...");

                        checkCore();
                        setupEventListeners();
                        setupQueueProcessor();
                        checkDebug();
                        log.info("Finished initializing WakaTime plugin");

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
    }

    private void checkCore() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
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
                log.debug("CLI location: " + Dependencies.getCLILocation());
            }
        });
    }

    private void checkApiKey() {
        ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {
                // prompt for apiKey if it does not already exist
                Project project = null;
                try {
                    project = ProjectManager.getInstance().getDefaultProject();
                } catch (NullPointerException e) { }
                ApiKey apiKey = new ApiKey(project);
                if (apiKey.getApiKey().equals("")) {
                    apiKey.promptForApiKey();
                }
                log.debug("Api Key: " + obfuscateKey(ApiKey.getApiKey()));
            }
        });
    }

    private void setupEventListeners() {
        ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {

                // save file
                MessageBus bus = ApplicationManager.getApplication().getMessageBus();
                connection = bus.connect();
                connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new CustomSaveListener());

                // edit document
                EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new CustomDocumentListener());

                // mouse press
                EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(new CustomEditorMouseListener());

                // scroll document
                EditorFactory.getInstance().getEventMulticaster().addVisibleAreaListener(new CustomVisibleAreaListener());
            }
        });
    }

    private void setupQueueProcessor() {
        final Runnable handler = new Runnable() {
            public void run() {
                processHeartbeatQueue();
            }
        };
        long delay = queueTimeoutSeconds;
        scheduledFixture = scheduler.scheduleAtFixedRate(handler, delay, delay, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void setupMenuItem() {
        ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {
                ActionManager am = ActionManager.getInstance();
                PluginMenu action = new PluginMenu();
                am.registerAction("WakaTimeApiKey", action);
                DefaultActionGroup menu = (DefaultActionGroup) am.getAction("ToolsMenu");
                menu.addSeparator();
                menu.add(action);
            }
        });
    }

    private void checkDebug() {
        if (WakaTime.DEBUG) {
            try {
                Messages.showWarningDialog("Running WakaTime in DEBUG mode. Your IDE may be slow when saving or editing files.", "Debug");
            } catch (NullPointerException e) { }
        }
    }

    public void disposeComponent() {
        try {
            connection.disconnect();
        } catch(Exception e) { }
        try {
            scheduledFixture.cancel(true);
        } catch (Exception e) { }

        // make sure to send all heartbeats before exiting
        processHeartbeatQueue();
    }

    public static BigDecimal getCurrentTimestamp() {
        return new BigDecimal(String.valueOf(System.currentTimeMillis() / 1000.0)).setScale(4, BigDecimal.ROUND_HALF_UP);
    }

    public static void appendHeartbeat(final BigDecimal time, final String file, final boolean isWrite) {
        WakaTime.lastFile = file;
        WakaTime.lastTime = time;
        final String project = WakaTime.getProjectName();
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
                Heartbeat h = new Heartbeat();
                h.entity = file;
                h.timestamp = time;
                h.isWrite = isWrite;
                h.project = project;
                heartbeatsQueue.add(h);
            }
        });
    }

    private static void processHeartbeatQueue() {
        if (WakaTime.READY) {

            // get single heartbeat from queue
            Heartbeat heartbeat = heartbeatsQueue.poll();
            if (heartbeat == null)
                return;

            // get all extra heartbeats from queue
            ArrayList<Heartbeat> extraHeartbeats = new ArrayList<Heartbeat>();
            while (true) {
                Heartbeat h = heartbeatsQueue.poll();
                if (h == null)
                    break;
                extraHeartbeats.add(h);
            }

            sendHeartbeat(heartbeat, extraHeartbeats);
        }
    }

    private static void sendHeartbeat(final Heartbeat heartbeat, final ArrayList<Heartbeat> extraHeartbeats) {
        final String[] cmds = buildCliCommand(heartbeat, extraHeartbeats);
        log.debug("Executing CLI: " + Arrays.toString(obfuscateKey(cmds)));
        try {
            Process proc = Runtime.getRuntime().exec(cmds);
            if (extraHeartbeats.size() > 0) {
                String json = toJSON(extraHeartbeats);
                log.debug(json);
                try {
                    BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
                    stdin.write(json);
                    stdin.write("\n");
                    try {
                        stdin.flush();
                        stdin.close();
                    } catch (IOException e) { /* ignored because wakatime-cli closes pipe after receiving \n */ }
                } catch (IOException e) {
                    log.warn(e);
                }
            }
            if (WakaTime.DEBUG) {
                BufferedReader stdout = new BufferedReader(new
                        InputStreamReader(proc.getInputStream()));
                BufferedReader stderr = new BufferedReader(new
                        InputStreamReader(proc.getErrorStream()));
                proc.waitFor();
                String s;
                while ((s = stdout.readLine()) != null) {
                    log.debug(s);
                }
                while ((s = stderr.readLine()) != null) {
                    log.debug(s);
                }
                log.debug("Command finished with return value: " + proc.exitValue());
            }
        } catch (Exception e) {
            log.warn(e);
        }
    }

    private static String toJSON(ArrayList<Heartbeat> extraHeartbeats) {
        StringBuffer json = new StringBuffer();
        json.append("[");
        boolean first = true;
        for (Heartbeat heartbeat : extraHeartbeats) {
            StringBuffer h = new StringBuffer();
            h.append("{\"entity\":\"");
            h.append(jsonEscape(heartbeat.entity));
            h.append("\",\"timestamp\":");
            h.append(heartbeat.timestamp.toPlainString());
            h.append(",\"is_write\":");
            h.append(heartbeat.isWrite.toString());
            if (heartbeat.project != null) {
                h.append(",\"project\":\"");
                h.append(jsonEscape(heartbeat.project));
                h.append("\"");
            }
            h.append("}");
            if (!first)
                json.append(",");
            json.append(h.toString());
            first = false;
        }
        json.append("]");
        return json.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null)
            return null;
        StringBuffer escaped = new StringBuffer();
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch(c) {
                case '\\': escaped.append("\\\\"); break;
                case '"': escaped.append("\\\""); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    boolean isUnicode = (c >= '\u0000' && c <= '\u001F') || (c >= '\u007F' && c <= '\u009F') || (c >= '\u2000' && c <= '\u20FF');
                    if (isUnicode){
                        escaped.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int k = 0; k < 4 - hex.length(); k++) {
                            escaped.append('0');
                        }
                        escaped.append(hex.toUpperCase());
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    private static String[] buildCliCommand(Heartbeat heartbeat, ArrayList<Heartbeat> extraHeartbeats) {
        ArrayList<String> cmds = new ArrayList<String>();
        cmds.add(Dependencies.getPythonLocation());
        cmds.add(Dependencies.getCLILocation());
        cmds.add("--entity");
        cmds.add(heartbeat.entity);
        cmds.add("--time");
        cmds.add(heartbeat.timestamp.toPlainString());
        cmds.add("--key");
        cmds.add(ApiKey.getApiKey());
        if (heartbeat.project != null) {
            cmds.add("--project");
            cmds.add(heartbeat.project);
        }
        cmds.add("--plugin");
        cmds.add(IDE_NAME+"/"+IDE_VERSION+" "+IDE_NAME+"-wakatime/"+VERSION);
        if (heartbeat.isWrite)
            cmds.add("--write");
        if (extraHeartbeats.size() > 0)
            cmds.add("--extra-heartbeats");
        return cmds.toArray(new String[cmds.size()]);
    }

    private static String getProjectName() {
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
                lastProject = project.getName();
                return lastProject;
            }
        }

        if (lastProject != null) {
            return lastProject;
        } else {
            return null;
        }
    }

    public static boolean enoughTimePassed(BigDecimal currentTime) {
        return WakaTime.lastTime.add(FREQUENCY).compareTo(currentTime) < 0;
    }

    public static boolean shouldLogFile(String file) {
        if (file.equals("atlassian-ide-plugin.xml") || file.contains("/.idea/workspace.xml")) {
            return false;
        }
        return true;
    }

    public static void setupDebugging() {
        String debug = ConfigFile.get("settings", "debug");
        WakaTime.DEBUG = debug != null && debug.trim().equals("true");
    }

    public static void setLoggingLevel() {
        if (WakaTime.DEBUG) {
            log.setLevel(Level.DEBUG);
            log.debug("Logging level set to DEBUG");
        } else {
            log.setLevel(Level.INFO);
        }
    }

    private static String obfuscateKey(String key) {
        String newKey = null;
        if (key != null) {
            newKey = key;
            if (key.length() > 4)
                newKey = "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXX" + key.substring(key.length() - 4);
        }
        return newKey;
    }

    private static String[] obfuscateKey(String[] cmds) {
        ArrayList<String> newCmds = new ArrayList<String>();
        String lastCmd = "";
        for (String cmd : cmds) {
            if (lastCmd == "--key")
                newCmds.add(obfuscateKey(cmd));
            else
                newCmds.add(cmd);
            lastCmd = cmd;
        }
        return newCmds.toArray(new String[newCmds.size()]);
    }

    @NotNull
    public String getComponentName() {
        return "WakaTime";
    }
}
