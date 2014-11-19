package com.wakatime.intellij.plugin;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by alanhamlett on 3/21/14.
 */
public class Dependencies {

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
                "usr/bin/python",
                "\\python37\\pythonw",
                "\\python36\\pythonw",
                "\\python35\\pythonw",
                "\\python34\\pythonw",
                "\\python33\\pythonw",
                "\\python32\\pythonw",
                "\\python31\\pythonw",
                "\\python30\\pythonw",
                "\\python27\\pythonw",
                "\\python26\\pythonw",
                "\\python37\\python",
                "\\python36\\python",
                "\\python35\\python",
                "\\python34\\python",
                "\\python33\\python",
                "\\python32\\python",
                "\\python31\\python",
                "\\python30\\python",
                "\\python27\\python",
                "\\python26\\python",
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
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String currentVersion = "2.1.6";
            if (currentVersion.equals(stdError.readLine())) {
                return false;
            }
        } catch (Exception e) { }
        return true;
    }

    public static String getCLILocation() {
        return Dependencies.getResourcesLocation()+File.separator+"wakatime-master"+File.separator+"wakatime-cli.py";
    }

    public static void installCLI() {
        File cli = new File(Dependencies.getCLILocation());
        if (!cli.getParentFile().getParentFile().exists())
            cli.getParentFile().getParentFile().mkdirs();

        URL url = null;
        try {
            url = new URL("https://codeload.github.com/wakatime/wakatime/zip/master");
        } catch (MalformedURLException e) { }
        String zipFile = cli.getParentFile().getParentFile().getAbsolutePath()+File.separator+"wakatime-cli.zip";
        File outputDir = cli.getParentFile().getParentFile();

        // download wakatime-master.zip file
        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        try {
            rbc = Channels.newChannel(url.openStream());
            fos = new FileOutputStream(zipFile);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            Dependencies.unzip(zipFile, outputDir);
            File oldZipFile = new File(zipFile);
            oldZipFile.delete();
        } catch (IOException e) {
            WakaTime.log.error(e);
        }
    }

    public static void upgradeCLI() {
        File cliDir = new File(new File(Dependencies.getCLILocation()).getParent());
        cliDir.delete();
        Dependencies.installCLI();
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
}
