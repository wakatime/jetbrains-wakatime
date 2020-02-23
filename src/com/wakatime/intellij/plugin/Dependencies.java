/* ==========================================================
File:        Dependencies.java
Description: Manages plugin dependencies.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.PasswordAuthentication;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.nio.file.attribute.PosixFilePermission.*;

public class Dependencies {

    private static String resourcesLocation = null;
    private static String s3Prefix = "https://wakatime-cli.s3-us-west-2.amazonaws.com/";

    public static String getResourcesLocation() {
        if (Dependencies.resourcesLocation == null) {
            if (System.getenv("WAKATIME_HOME") != null && !System.getenv("WAKATIME_HOME").trim().isEmpty()) {
                File resourcesFolder = new File(System.getenv("WAKATIME_HOME"));
                if (resourcesFolder.exists()) {
                    Dependencies.resourcesLocation = resourcesFolder.getAbsolutePath();
                    WakaTime.log.debug("Using $WAKATIME_HOME for resources folder: " + Dependencies.resourcesLocation);
                    return Dependencies.resourcesLocation;
                }
            }

            if (isWindows()) {
                File appDataFolder = new File(System.getenv("APPDATA"));
                File resourcesFolder = new File(appDataFolder, "WakaTime");
                Dependencies.resourcesLocation = resourcesFolder.getAbsolutePath();
            } else {
                File userHomeDir = new File(System.getProperty("user.home"));
                File resourcesFolder = new File(userHomeDir, ".wakatime");
                Dependencies.resourcesLocation = resourcesFolder.getAbsolutePath();
            }
        }
        return Dependencies.resourcesLocation;
    }

    public static boolean isCLIInstalled() {
        File cli = new File(Dependencies.getCLILocation());
        return cli.exists();
    }

    public static boolean isCLIOld() {
        if (!Dependencies.isCLIInstalled()) {
            return false;
        }
        ArrayList<String> cmds = new ArrayList<String>();
        cmds.add(Dependencies.getCLILocation());
        cmds.add("--version");
        try {
            Process p = Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));
            p.waitFor();
            String output = "";
            String s;
            while ((s = stdInput.readLine()) != null) {
                output += s;
            }
            while ((s = stdError.readLine()) != null) {
                output += s;
            }
            WakaTime.log.debug("wakatime-cli local version output: \"" + output + "\"");
            WakaTime.log.debug("wakatime-cli local version exit code: " + p.exitValue());

            if (p.exitValue() == 0) {
                String cliVersion = latestCliVersion();
                WakaTime.log.debug("Current wakatime-cli version: " + cliVersion);
                if (output.contains(cliVersion))
                    return false;
            }
        } catch (Exception e) {
            WakaTime.log.warn(e);
        }
        return true;
    }

    public static String latestCliVersion() {
        String url = Dependencies.s3BucketUrl() + "current_version.txt";
        try {
            String aboutText = getUrlAsString(url);
            Pattern p = Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+)");
            Matcher m = p.matcher(aboutText);
            if (m.find()) return m.group(1);
        } catch (Exception e) {
            WakaTime.log.warn(e);
        }
        return "Unknown";
    }

    public static String getCLILocation() {
        String ext = isWindows() ? ".exe" : "";
        return combinePaths(Dependencies.getResourcesLocation(), "wakatime-cli", "wakatime-cli" + ext);
    }

    public static void installCLI() {
        File resourceDir = new File(Dependencies.getResourcesLocation());
        if (!resourceDir.exists()) resourceDir.mkdirs();

        String url = Dependencies.s3BucketUrl() + "wakatime-cli.zip";
        String zipFile = combinePaths(Dependencies.getResourcesLocation(), "wakatime-cli.zip");

        if (downloadFile(url, zipFile)) {

            // Delete old wakatime-master directory if it exists
            File dir = new File(combinePaths(Dependencies.getResourcesLocation(), "wakatime-cli"));
            if (dir.exists()) {
                deleteDirectory(dir);
            }

            File outputDir = new File(Dependencies.getResourcesLocation());
            try {
                Dependencies.unzip(zipFile, outputDir);
                File oldZipFile = new File(zipFile);
                oldZipFile.delete();
                if (!isWindows()) makeExecutable(getCLILocation());
            } catch (IOException e) {
                WakaTime.log.warn(e);
            }
        }
    }

    public static boolean downloadFile(String url, String saveAs) {
        File outFile = new File(saveAs);

        // create output directory if does not exist
        File outDir = outFile.getParentFile();
        if (!outDir.exists())
            outDir.mkdirs();

        URL downloadUrl = null;
        try {
            downloadUrl = new URL(url);
        } catch (MalformedURLException e) { }

        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        try {
            rbc = Channels.newChannel(downloadUrl.openStream());
            fos = new FileOutputStream(saveAs);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();
            return true;
        } catch (RuntimeException e) {
            WakaTime.log.warn(e);
            try {
                // try downloading without verifying SSL cert (https://github.com/wakatime/jetbrains-wakatime/issues/46)
                SSLContext SSL_CONTEXT = SSLContext.getInstance("SSL");
                SSL_CONTEXT.init(null, new TrustManager[] { new LocalSSLTrustManager() }, null);
                HttpsURLConnection.setDefaultSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
                HttpsURLConnection conn = (HttpsURLConnection)downloadUrl.openConnection();
                InputStream inputStream = conn.getInputStream();
                fos = new FileOutputStream(saveAs);
                int bytesRead = -1;
                byte[] buffer = new byte[4096];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                fos.close();
                return true;
            } catch (NoSuchAlgorithmException e1) {
                WakaTime.log.warn(e1);
            } catch (KeyManagementException e1) {
                WakaTime.log.warn(e1);
            } catch (IOException e1) {
                WakaTime.log.warn(e1);
            }
        } catch (IOException e) {
            WakaTime.log.warn(e);
        }

        return false;
    }

    public static String getUrlAsString(String url) {
        StringBuilder text = new StringBuilder();

        URL downloadUrl = null;
        try {
            downloadUrl = new URL(url);
        } catch (MalformedURLException e) { }

        try {
            InputStream inputStream = downloadUrl.openStream();
            byte[] buffer = new byte[4096];
            while (inputStream.read(buffer) != -1) {
                text.append(new String(buffer, "UTF-8"));
            }
            inputStream.close();
        } catch (RuntimeException e) {
            WakaTime.log.warn(e);
            try {
                // try downloading without verifying SSL cert (https://github.com/wakatime/jetbrains-wakatime/issues/46)
                SSLContext SSL_CONTEXT = SSLContext.getInstance("SSL");
                SSL_CONTEXT.init(null, new TrustManager[]{new LocalSSLTrustManager()}, null);
                HttpsURLConnection.setDefaultSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
                HttpsURLConnection conn = (HttpsURLConnection) downloadUrl.openConnection();
                InputStream inputStream = conn.getInputStream();
                byte[] buffer = new byte[4096];
                while (inputStream.read(buffer) != -1) {
                    text.append(new String(buffer, "UTF-8"));
                }
                inputStream.close();
            } catch (NoSuchAlgorithmException e1) {
                WakaTime.log.warn(e1);
            } catch (KeyManagementException e1) {
                WakaTime.log.warn(e1);
            } catch (UnknownHostException e1) {
                WakaTime.log.warn(e1);
            } catch (IOException e1) {
                WakaTime.log.warn(e1);
            }
        } catch (UnknownHostException e) {
            WakaTime.log.warn(e);
        } catch (Exception e) {
            WakaTime.log.warn(e);
        }

        return text.toString();
    }

    /**
     * Configures a proxy if one is set in ~/.wakatime.cfg.
     */
    public static void configureProxy() {
        String proxyConfig = ConfigFile.get("settings", "proxy");
        if (proxyConfig != null && !proxyConfig.trim().equals("")) {
            try {
                URL proxyUrl = new URL(proxyConfig);
                String userInfo = proxyUrl.getUserInfo();
                if (userInfo != null) {
                    final String user = userInfo.split(":")[0];
                    final String pass = userInfo.split(":")[1];
                    Authenticator authenticator = new Authenticator() {
                        public PasswordAuthentication getPasswordAuthentication() {
                            return (new PasswordAuthentication(user, pass.toCharArray()));
                        }
                    };
                    Authenticator.setDefault(authenticator);
                }

                System.setProperty("https.proxyHost", proxyUrl.getHost());
                System.setProperty("https.proxyPort", Integer.toString(proxyUrl.getPort()));

            } catch (MalformedURLException e) {
                WakaTime.log.error("Proxy string must follow https://user:pass@host:port format: " + proxyConfig);
            }
        }
    }

    private static void unzip(String zipFile, File outputDir) throws IOException {
        if(!outputDir.exists())
            outputDir.mkdirs();

        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry ze = zis.getNextEntry();

        while (ze != null) {
            String fileName = ze.getName();
            File newFile = new File(outputDir, fileName);

            if (ze.isDirectory()) {
                newFile.mkdirs();
            } else {
                FileOutputStream fos = new FileOutputStream(newFile.getAbsolutePath());
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }

            ze = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
    }

    private static void deleteDirectory(File path) {
        if( path.exists() ) {
            File[] files = path.listFiles();
            for(int i=0; i<files.length; i++) {
                if(files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                }
                else {
                    files[i].delete();
                }
            }
        }
        path.delete();
    }

    public static boolean is64bit() {
        return System.getProperty("os.arch").indexOf("64") != -1;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    public static String combinePaths(String... args) {
        File path = null;
        for (String arg : args) {
            if (arg != null) {
                if (path == null)
                    path = new File(arg);
                else
                    path = new File(path, arg);
            }
        }
        if (path == null)
            return null;
        return path.toString();
    }

    private static String s3BucketUrl() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.indexOf("win") >= 0) {
            String arch = Dependencies.is64bit() ? "64" : "32";
            return Dependencies.s3Prefix + "windows-x86-" + arch + "/";
        } else if (os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0) {
            return Dependencies.s3Prefix + "mac-x86-64/";
        } else {
            return Dependencies.s3Prefix + "linux-x86-64/";
        }
    }

    private static void makeExecutable(String filePath) throws IOException {
        File file = new File(filePath);
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(OWNER_READ);
        perms.add(OWNER_WRITE);
        perms.add(OWNER_EXECUTE);
        perms.add(GROUP_READ);
        perms.add(GROUP_EXECUTE);
        perms.add(OTHERS_READ);
        perms.add(OTHERS_EXECUTE);
        Files.setPosixFilePermissions(file.toPath(), perms);
    }
}
