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
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Dependencies {

    private static final String cliVersion = "4.1.0";

    private static String pythonLocation = null;
    private static String resourcesLocation = null;
    private static String cliLocation = null;

    public static boolean isPythonInstalled() {
        return Dependencies.getPythonLocation() != null;
    }

    public static String getResourcesLocation() {
        if (Dependencies.resourcesLocation == null) {
            String separator = "[\\\\/]";
            Dependencies.resourcesLocation = WakaTime.class.getResource("WakaTime.class").getPath()
                    .replaceFirst("file:", "")
                    .replaceAll("%20", " ")
                    .replaceFirst("com" + separator + "wakatime" + separator + "intellij" + separator + "plugin" + separator + "WakaTime.class", "")
                    .replaceFirst("WakaTime.jar!" + separator, "") + "WakaTime-resources";
            if (System.getProperty("os.name").startsWith("Windows") && Dependencies.resourcesLocation.startsWith("/")) {
                Dependencies.resourcesLocation = Dependencies.resourcesLocation.substring(1);
            }
        }
        return Dependencies.resourcesLocation;
    }

    public static String getPythonLocation() {
        if (Dependencies.pythonLocation != null)
            return Dependencies.pythonLocation;
        String []paths = new String[] {
                "pythonw",
                "python",
                "/usr/local/bin/python",
                "/usr/bin/python",
                "\\python37\\pythonw",
                "\\Python37\\pythonw",
                "\\python36\\pythonw",
                "\\Python36\\pythonw",
                "\\python35\\pythonw",
                "\\Python35\\pythonw",
                "\\python34\\pythonw",
                "\\Python34\\pythonw",
                "\\python33\\pythonw",
                "\\Python33\\pythonw",
                "\\python32\\pythonw",
                "\\Python32\\pythonw",
                "\\python31\\pythonw",
                "\\Python31\\pythonw",
                "\\python30\\pythonw",
                "\\Python30\\pythonw",
                "\\python27\\pythonw",
                "\\Python27\\pythonw",
                "\\python26\\pythonw",
                "\\Python26\\pythonw",
                "\\python37\\python",
                "\\Python37\\python",
                "\\python36\\python",
                "\\Python36\\python",
                "\\python35\\python",
                "\\Python35\\python",
                "\\python34\\python",
                "\\Python34\\python",
                "\\python33\\python",
                "\\Python33\\python",
                "\\python32\\python",
                "\\Python32\\python",
                "\\python31\\python",
                "\\Python31\\python",
                "\\python30\\python",
                "\\Python30\\python",
                "\\python27\\python",
                "\\Python27\\python",
                "\\python26\\python",
                "\\Python26\\python",
        };
        for (int i=0; i<paths.length; i++) {
            try {
                Runtime.getRuntime().exec(paths[i]);
                Dependencies.pythonLocation = paths[i];
                break;
            } catch (Exception e) { }
        }
        return Dependencies.pythonLocation;
    }

    public static boolean isCLIInstalled() {
        File cli = new File(Dependencies.getCLILocation());
        return (cli.exists() && !cli.isDirectory());
    }

    public static boolean isCLIOld() {
        if (!Dependencies.isCLIInstalled()) {
            return false;
        }
        ArrayList<String> cmds = new ArrayList<String>();
        cmds.add(Dependencies.getPythonLocation());
        cmds.add(Dependencies.getCLILocation());
        cmds.add("--version");
        try {
            Process p = Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
            BufferedReader stdOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String usingVersion = stdErr.readLine();
            WakaTime.log.debug("*** STDOUT ***");
            WakaTime.log.debug("\"" + stdOut.readLine() + "\"");
            WakaTime.log.debug("*** STDERR ***");
            WakaTime.log.debug("\"" + usingVersion + "\"");
            if (usingVersion.contains(cliVersion)) {
                return false;
            }
        } catch (Exception e) { }
        return true;
    }

    public static String getCLILocation() {
        return combinePaths(Dependencies.getResourcesLocation(), "wakatime-master", "wakatime", "cli.py");
    }

    public static void installCLI() {
        File cli = new File(Dependencies.getCLILocation());
        if (!cli.getParentFile().getParentFile().getParentFile().exists())
            cli.getParentFile().getParentFile().getParentFile().mkdirs();

        String url = "https://codeload.github.com/wakatime/wakatime/zip/master";
        String zipFile = cli.getParentFile().getParentFile().getParentFile().getAbsolutePath() + File.separator + "wakatime-cli.zip";
        File outputDir = cli.getParentFile().getParentFile().getParentFile();

        // Delete old wakatime-master directory if it exists
        File dir = cli.getParentFile().getParentFile();
        if (dir.exists()) {
            deleteDirectory(dir);
        }

        // download wakatime-master.zip file
        if (downloadFile(url, zipFile)) {
            try {
                Dependencies.unzip(zipFile, outputDir);
                File oldZipFile = new File(zipFile);
                oldZipFile.delete();
            } catch (IOException e) {
                WakaTime.log.error(e);
            }
        }
    }

    public static void upgradeCLI() {
        File cliDir = new File(new File(Dependencies.getCLILocation()).getParent());
        cliDir.delete();
        Dependencies.installCLI();
    }

    public static void installPython() {
        if (System.getProperty("os.name").contains("Windows")) {
            String url = "https://www.python.org/ftp/python/3.4.2/python-3.4.2.msi";
            if (System.getenv("ProgramFiles(x86)") != null) {
                url = "https://www.python.org/ftp/python/3.4.2/python-3.4.2.amd64.msi";
            }

            File cli = new File(Dependencies.getCLILocation());
            String outFile = cli.getParentFile().getParentFile().getAbsolutePath()+File.separator+"python.msi";
            if (downloadFile(url, outFile)) {

                // execute python msi installer
                ArrayList<String> cmds = new ArrayList<String>();
                cmds.add("msiexec");
                cmds.add("/i");
                cmds.add(outFile);
                cmds.add("/norestart");
                cmds.add("/qb!");
                try {
                    Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
            return true;
        } catch (RuntimeException e) {
            WakaTime.log.error(e);
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
                return true;
            } catch (NoSuchAlgorithmException e1) {
                WakaTime.log.error(e1);
            } catch (KeyManagementException e1) {
                WakaTime.log.error(e1);
            } catch (IOException e1) {
                WakaTime.log.error(e1);
            }
        } catch (IOException e) {
            WakaTime.log.error(e);
        }

        return false;
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

    private static String combinePaths(String... args) {
        File path = null;
        for (String arg : args) {
            if (path == null)
                path = new File(arg);
            else
                path = new File(path, arg);
        }
        if (path == null)
            return null;
        return path.toString();
    }
}
