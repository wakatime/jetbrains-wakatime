/* ==========================================================
File:        WakaTime.java
Description: Automatic time tracking for JetBrains IDEs.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.AppTopics;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.PlatformUtils;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.awt.KeyboardFocusManager;
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

    public static final BigDecimal FREQUENCY = new BigDecimal(2 * 60); // max secs between heartbeats for continuous coding
    public static final Logger log = Logger.getInstance("WakaTime");

    public static String VERSION;
    public static String IDE_NAME;
    public static String IDE_VERSION;
    public static MessageBusConnection connection;
    public static Boolean DEBUG = false;
    public static Boolean DEBUG_CHECKED = false;
    public static Boolean STATUS_BAR = false;
    public static Boolean READY = false;
    public static String lastFile = null;
    public static BigDecimal lastTime = new BigDecimal(0);

    private final int queueTimeoutSeconds = 30;
    private static ConcurrentLinkedQueue<Heartbeat> heartbeatsQueue = new ConcurrentLinkedQueue<Heartbeat>();
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> scheduledFixture;

    public WakaTime() {
    }

    public void initComponent() {
        try {
            // support older IDE versions with deprecated PluginManager
            VERSION = PluginManager.getPlugin(PluginId.getId("com.wakatime.intellij.plugin")).getVersion();
        } catch (Exception e) {
            // use PluginManagerCore if PluginManager deprecated
            VERSION = PluginManagerCore.getPlugin(PluginId.getId("com.wakatime.intellij.plugin")).getVersion();
        }
        log.info("Initializing WakaTime plugin v" + VERSION + " (https://wakatime.com/)");
        //System.out.println("Initializing WakaTime plugin v" + VERSION + " (https://wakatime.com/)");

        // Set runtime constants
        IDE_NAME = PlatformUtils.getPlatformPrefix();
        IDE_VERSION = ApplicationInfo.getInstance().getFullVersion();

        setupDebugging();
        setupStatusBar();
        setLoggingLevel();
        Dependencies.configureProxy();
        checkApiKey();
        checkCli();
        setupEventListeners();
        setupQueueProcessor();
    }

    private void checkCli() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
                if (!Dependencies.isCLIInstalled()) {
                    log.info("Downloading and installing wakatime-cli...");
                    Dependencies.installCLI();
                    WakaTime.READY = true;
                    log.info("Finished downloading and installing wakatime-cli.");
                } else if (Dependencies.isCLIOld()) {
                    log.info("Upgrading wakatime-cli ...");
                    Dependencies.installCLI();
                    WakaTime.READY = true;
                    log.info("Finished upgrading wakatime-cli.");
                } else {
                    WakaTime.READY = true;
                    log.info("wakatime-cli is up to date.");
                }
                log.debug("wakatime-cli location: " + Dependencies.getCLILocation());
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
                } catch (Exception e) { }
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

    private static void checkDebug() {
        if (DEBUG_CHECKED) return;
        DEBUG_CHECKED = true;
        if (!DEBUG) return;
        try {
            Messages.showWarningDialog("Your IDE may respond slower. Disable debug mode from Tools -> WakaTime Settings.", "WakaTime Debug Mode Enabled");
        } catch (Exception e) { }
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

    public static void appendHeartbeat(final VirtualFile file, Project project, final boolean isWrite) {
        checkDebug();
        if (WakaTime.READY && project != null) {
            StatusBar statusbar = WindowManager.getInstance().getStatusBar(project);
            if (statusbar != null) statusbar.updateWidget("WakaTime");
        }
        if (!shouldLogFile(file))
            return;
        final String projectName = project != null ? project.getName() : null;
        final BigDecimal time = WakaTime.getCurrentTimestamp();
        if (!isWrite && file.getPath().equals(WakaTime.lastFile) && !enoughTimePassed(time)) {
            return;
        }
        WakaTime.lastFile = file.getPath();
        WakaTime.lastTime = time;
        final String language = WakaTime.getLanguage(file);
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
                Heartbeat h = new Heartbeat();
                h.entity = file.getPath();
                h.timestamp = time;
                h.isWrite = isWrite;
                h.project = projectName;
                h.language = language;
                heartbeatsQueue.add(h);
            }
        });
    }

    private static void processHeartbeatQueue() {
        if (!WakaTime.READY) return;

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
            if (heartbeat.language != null) {
                h.append(",\"language\":\"");
                h.append(jsonEscape(heartbeat.language));
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
        if (heartbeat.language != null) {
            cmds.add("--alternate-language");
            cmds.add(heartbeat.language);
        }
        cmds.add("--plugin");
        cmds.add(IDE_NAME+"/"+IDE_VERSION+" "+IDE_NAME+"-wakatime/"+VERSION);
        if (heartbeat.isWrite)
            cmds.add("--write");
        if (extraHeartbeats.size() > 0)
            cmds.add("--extra-heartbeats");
        return cmds.toArray(new String[cmds.size()]);
    }

    private static String getLanguage(final VirtualFile file) {
        FileType type = file.getFileType();
        if (type != null)
            return type.getName();
        return null;
    }

    public static VirtualFile getFile(Document document) {
        if (document == null) return null;
        FileDocumentManager instance = FileDocumentManager.getInstance();
        if (instance == null) return null;
        VirtualFile file = instance.getFile(document);
        return file;
    }

    public static boolean enoughTimePassed(BigDecimal currentTime) {
        return WakaTime.lastTime.add(FREQUENCY).compareTo(currentTime) < 0;
    }

    public static boolean shouldLogFile(VirtualFile file) {
        if (file == null || file.getUrl().startsWith("mock://")) {
            return false;
        }
        String filePath = file.getPath();
        if (filePath.equals("atlassian-ide-plugin.xml") || filePath.contains("/.idea/workspace.xml")) {
            return false;
        }
        return true;
    }

    public static boolean isAppActive() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null;
    }

    public static boolean isProjectInitialized(Project project) {
        if (project == null) return true;
        return project.isInitialized();
    }

    public static void setupDebugging() {
        String debug = ConfigFile.get("settings", "debug");
        WakaTime.DEBUG = debug != null && debug.trim().equals("true");
    }

    public static void setupStatusBar() {
        String statusBarVal = ConfigFile.get("settings", "status_bar_enabled");
        WakaTime.STATUS_BAR = statusBarVal == null || !statusBarVal.trim().equals("false");
        if (WakaTime.READY) {
            try {
                Project project = ProjectManager.getInstance().getDefaultProject();
                StatusBar statusbar = WindowManager.getInstance().getStatusBar(project);
                if (statusbar != null) statusbar.updateWidget("WakaTime");
            } catch (Exception e) {
                log.warn(e);
            }
        }
    }

    public static void setLoggingLevel() {
        if (WakaTime.DEBUG) {
            log.setLevel(Level.DEBUG);
            log.debug("Logging level set to DEBUG");
        } else {
            log.setLevel(Level.INFO);
        }
    }

    public static Project getProject(Document document) {
        Editor[] editors = EditorFactory.getInstance().getEditors(document);
        if (editors.length > 0) {
            return editors[0].getProject();
        }
        return null;
    }

    public static void openDashboardWebsite() {
        BrowserUtil.browse("https://wakatime.com/dashboard");
    }

    private static String todayText = "";
    private static BigDecimal todayTextTime = new BigDecimal(0);

    public static String getTodayText() {
        if (!WakaTime.READY) return todayText;
        if (!WakaTime.STATUS_BAR) return "";
        BigDecimal now = getCurrentTimestamp();
        if (todayTextTime.add(new BigDecimal(60)).compareTo(now) > 0) return todayText;
        todayTextTime = getCurrentTimestamp();

        Project project;
        try {
            project = ProjectManager.getInstance().getDefaultProject();
        } catch (Exception e) {
            return todayText;
        }

        final String[] cmds = new String[]{Dependencies.getCLILocation(), "--today", "--key", ApiKey.getApiKey()};
        log.debug("Executing CLI: " + Arrays.toString(obfuscateKey(cmds)));

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating wakatime status bar") {
            public void run(@NotNull ProgressIndicator progressIndicator) {
                progressIndicator.setIndeterminate(false);
                try {
                    Process proc = Runtime.getRuntime().exec(cmds);
                    BufferedReader stdout = new BufferedReader(new
                            InputStreamReader(proc.getInputStream()));
                    BufferedReader stderr = new BufferedReader(new
                            InputStreamReader(proc.getErrorStream()));
                    proc.waitFor();
                    ArrayList<String> output = new ArrayList<String>();
                    String s;
                    while ((s = stdout.readLine()) != null) {
                        output.add(s);
                    }
                    while ((s = stderr.readLine()) != null) {
                        output.add(s);
                    }
                    log.debug("Command finished with return value: " + proc.exitValue());
                    todayText = " " + String.join("", output);
                    todayTextTime = getCurrentTimestamp();
                } catch (Exception e) {
                    log.warn(e);
                }
                progressIndicator.setFraction(1.0);
                progressIndicator.stop();
            }
        });
        return todayText;
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
