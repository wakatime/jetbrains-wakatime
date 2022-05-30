/* ==========================================================
File:        WakaTime.java
Description: Automatic time tracking for JetBrains IDEs.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.google.api.Http;
import com.intellij.AppTopics;
import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.PlatformUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.HttpConfigurable;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.KeyboardFocusManager;
import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.*;

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
    public static Boolean isBuilding = false;
    public static LineStats lineStats = new LineStats();

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
                    if (System.getenv("WAKATIME_CLI_LOCATION") != null && !System.getenv("WAKATIME_CLI_LOCATION").trim().isEmpty()) {
                        File wakatimeCLI = new File(System.getenv("WAKATIME_CLI_LOCATION"));
                        if (wakatimeCLI.exists()) {
                          log.warn("$WAKATIME_CLI_LOCATION is out of date, please update it.");
                        }
                    } else {
                        log.info("Upgrading wakatime-cli ...");
                        Dependencies.installCLI();
                        WakaTime.READY = true;
                        log.info("Finished upgrading wakatime-cli.");
                    }
                } else {
                    WakaTime.READY = true;
                    log.info("wakatime-cli is up to date.");
                }
                Dependencies.createSymlink(Dependencies.combinePaths(Dependencies.getResourcesLocation(), "wakatime-cli"), Dependencies.getCLILocation());
                log.debug("wakatime-cli location: " + Dependencies.getCLILocation());
            }
        });
    }

    private static void checkApiKey() {
        ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {
                // prompt for apiKey if it does not already exist
                Project project = getCurrentProject();
                if (project == null) return;
                if (ConfigFile.getApiKey().equals("")) {
                    try {
                        ApiKey apiKey = new ApiKey(project);
                        apiKey.promptForApiKey();
                    } catch(Exception e) {
                        log.warn(e);
                    } catch (Throwable throwable) {
                        log.warn("Unable to prompt for api key because UI not ready.");
                    }
                }
            }
        });
    }

    private void setupEventListeners() {
        ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {
                Disposable disposable = Disposer.newDisposable("WakaTimeListener");
                MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();

                // save file
                connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new CustomSaveListener());

                // edit document
                EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new CustomDocumentListener(), disposable);

                // mouse press
                EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(new CustomEditorMouseListener(), disposable);

                // scroll document
                EditorFactory.getInstance().getEventMulticaster().addVisibleAreaListener(new CustomVisibleAreaListener(), disposable);

                // compiling
                connection.subscribe(BuildManagerListener.TOPIC, new CustomBuildManagerListener());
                connection.subscribe(CompilerTopics.COMPILATION_STATUS, new CustomBuildManagerListener());
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
        ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {
                Messages.showWarningDialog("Your IDE may respond slower. Disable debug mode from Tools -> WakaTime Settings.", "WakaTime Debug Mode Enabled");
            }
        });
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

    public static void appendHeartbeat(final VirtualFile file, final Project project, final boolean isWrite, @Nullable final LineStats lineStats) {
        checkDebug();

        if (WakaTime.READY) {
            updateStatusBarText();
            if (project != null) {
                StatusBar statusbar = WindowManager.getInstance().getStatusBar(project);
                if (statusbar != null) statusbar.updateWidget("WakaTime");
            }
        }

        if (!shouldLogFile(file)) return;

        final BigDecimal time = WakaTime.getCurrentTimestamp();

        if (!isWrite && file.getPath().equals(WakaTime.lastFile) && !enoughTimePassed(time)) {
            return;
        }

        WakaTime.lastFile = file.getPath();
        WakaTime.lastTime = time;

        final String projectName = project != null ? project.getName() : null;
        final String language = WakaTime.getLanguage(file);

        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
                Heartbeat h = new Heartbeat();
                h.entity = file.getPath();
                h.timestamp = time;
                h.isWrite = isWrite;
                h.isUnsavedFile = !file.exists();
                h.project = projectName;
                h.language = language;
                h.isBuilding = WakaTime.isBuilding;
                if (lineStats != null) {
                    h.lineCount = lineStats.lineCount;
                    h.lineNumber = lineStats.lineNumber;
                    h.cursorPosition = lineStats.cursorPosition;
                }

                heartbeatsQueue.add(h);

                if (WakaTime.isBuilding) setBuildTimeout();
            }
        });
    }

    private static void setBuildTimeout() {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(new Runnable() {
            @Override
            public void run() {
                if (!WakaTime.isBuilding) return;
                Project project = getCurrentProject();
                if (project == null) return;
                if (!WakaTime.isProjectInitialized(project)) return;
                VirtualFile file = WakaTime.getCurrentFile(project);
                if (file == null) return;
                Document document = WakaTime.getCurrentDocument(project);
                WakaTime.appendHeartbeat(file, project, false, null);
            }
        }, 10, TimeUnit.SECONDS);
    }

    private static void processHeartbeatQueue() {
        if (!WakaTime.READY) return;

        checkApiKey();

        // get single heartbeat from queue
        Heartbeat heartbeat = heartbeatsQueue.poll();
        if (heartbeat == null)
            return;

        // get all extra heartbeats from queue
        ArrayList<Heartbeat> extraHeartbeats = new ArrayList<>();
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
            if (Dependencies.isWindows() && e.toString().contains("Access is denied")) {
                try {
                    Messages.showWarningDialog("Microsoft Defender is blocking WakaTime. Please allow " + Dependencies.getCLILocation() + " to run so WakaTime can upload code stats to your dashboard.", "Error");
                } catch (Exception ex) { }
            }
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
            if (heartbeat.lineCount != null) {
                h.append(",\"lines\":");
                h.append(heartbeat.lineCount);
            }
            if (heartbeat.lineNumber != null) {
                h.append(",\"lineno\":");
                h.append(heartbeat.lineNumber);
            }
            if (heartbeat.cursorPosition != null) {
                h.append(",\"cursorpos\":");
                h.append(heartbeat.cursorPosition);
            }
            if (heartbeat.isUnsavedFile) {
                h.append(",\"is_unsaved_entity\":true");
            }
            if (heartbeat.isBuilding) {
                h.append(",\"category\":\"building\"");
            }
            if (heartbeat.project != null) {
                h.append(",\"alternate_project\":\"");
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
            json.append(h);
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
        String apiKey = ConfigFile.getApiKey();
        if (!apiKey.equals("")) {
            cmds.add("--key");
            cmds.add(apiKey);
        }
        if (heartbeat.lineCount != null) {
            cmds.add("--lines-in-file");
            cmds.add(heartbeat.lineCount.toString());
        }
        if (heartbeat.lineNumber != null) {
            cmds.add("--lineno");
            cmds.add(heartbeat.lineNumber.toString());
        }
        if (heartbeat.cursorPosition != null) {
            cmds.add("--cursorpos");
            cmds.add(heartbeat.cursorPosition.toString());
        }
        if (heartbeat.project != null) {
            cmds.add("--alternate-project");
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
        if (heartbeat.isUnsavedFile)
            cmds.add("--is-unsaved-entity");
        if (heartbeat.isBuilding) {
            cmds.add("--category");
            cmds.add("building");
        }

        String proxy = getBuiltinProxy();
        if (proxy != null) {
            WakaTime.log.info("built-in proxy will be used: " + proxy);
            cmds.add("--proxy");
            cmds.add(proxy);
        }

        if (extraHeartbeats.size() > 0)
            cmds.add("--extra-heartbeats");
        return cmds.toArray(new String[cmds.size()]);
    }

    private static String getBuiltinProxy() {
        HttpConfigurable config = HttpConfigurable.getInstance();

        String host = config.PROXY_HOST;
        if (host != null) {
            String auth = "";
            String protocol = config.PROXY_TYPE_IS_SOCKS ? "socks5://" : "https://";

            String user = null;
            try {
                user = config.getProxyLogin();
                if (user != null) {
                    auth = String.format("%s:%s@", user, config.getPlainProxyPassword());
                }
            } catch (NoSuchMethodError e) { }

            String url = protocol + auth + host;
            if (config.PROXY_PORT > 0) {
                url += String.format(":%d", config.PROXY_PORT);
            }

            return url;
        }

        return null;
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
        String debug = ConfigFile.get("settings", "debug", false);
        WakaTime.DEBUG = debug != null && debug.trim().equals("true");
    }

    public static void setupStatusBar() {
        String statusBarVal = ConfigFile.get("settings", "status_bar_enabled", false);
        WakaTime.STATUS_BAR = statusBarVal == null || !statusBarVal.trim().equals("false");
        if (WakaTime.READY) {
            try {
                updateStatusBarText();
                Project project = getCurrentProject();
                if (project == null) return;
                StatusBar statusbar = WindowManager.getInstance().getStatusBar(project);
                if (statusbar == null) return;
                statusbar.updateWidget("WakaTime");
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

    private static String getLanguage(final VirtualFile file) {
        FileType type = file.getFileType();
        if (type != null)
            return type.getName();
        return null;
    }

    @Nullable
    public static VirtualFile getFile(Document document) {
        if (document == null) return null;
        FileDocumentManager instance = FileDocumentManager.getInstance();
        if (instance == null) return null;
        VirtualFile file = instance.getFile(document);
        return file;
    }

    @Nullable
    public static VirtualFile getCurrentFile(Project project) {
        if (project == null) return null;
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) return null;
        Document document = editor.getDocument();
        return WakaTime.getFile(document);
    }

    public static Project getProject(Document document) {
        Editor[] editors = EditorFactory.getInstance().getEditors(document);
        if (editors.length > 0) {
            return editors[0].getProject();
        }
        return null;
    }

    @Nullable
    public static Document getCurrentDocument(Project project) {
        if (project == null) return null;
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) return null;
        return editor.getDocument();
    }

    @Nullable
    public static Project getCurrentProject() {
        Project project = null;
        try {
            project = ProjectManager.getInstance().getDefaultProject();
        } catch (Exception e) { }
        return project;
    }

    public static LineStats getLineStats(Document document, int offset) {
        LineStats lineStats = new LineStats();
        lineStats.lineCount = document.getLineCount();
        try {
            lineStats.lineNumber = document.getLineNumber(offset);
        } catch (NoSuchMethodError e) { }
        try {
            lineStats.cursorPosition = offset - document.getLineStartOffset(lineStats.lineCount - 1);
        } catch (NoSuchMethodError e) { }
        return lineStats;
    }

    public static void openDashboardWebsite() {
        BrowserUtil.browse("https://wakatime.com/dashboard");
    }

    private static String todayText = "initialized";
    private static BigDecimal todayTextTime = new BigDecimal(0);

    public static String getStatusBarText() {
        if (!WakaTime.READY) return "";
        if (!WakaTime.STATUS_BAR) return "";
        return todayText;
    }

    public static void updateStatusBarText() {

        // rate limit, to prevent from fetching Today's stats too frequently
        BigDecimal now = getCurrentTimestamp();
        if (todayTextTime.add(new BigDecimal(60)).compareTo(now) > 0) return;
        todayTextTime = getCurrentTimestamp();

        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
                final String[] cmds = new String[]{Dependencies.getCLILocation(), "--today", "--key", ConfigFile.getApiKey()};
                log.debug("Executing CLI: " + Arrays.toString(obfuscateKey(cmds)));

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
                    } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                } catch (Exception e) {
                    log.warn(e);
                    if (Dependencies.isWindows() && e.toString().contains("Access is denied")) {
                        try {
                            Messages.showWarningDialog("Microsoft Defender is blocking WakaTime. Please allow " + Dependencies.getCLILocation() + " to run so WakaTime can upload code stats to your dashboard.", "Error");
                        } catch (Exception ex) { }
                    }
                }
            }
        });
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
