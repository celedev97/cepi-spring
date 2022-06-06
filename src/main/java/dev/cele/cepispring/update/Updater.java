package dev.cele.cepispring.update;

import dev.cele.cepispring.Main;
import org.json.JSONObject;

import java.awt.*;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.stream.Collectors;

public class Updater {

    public static void checkUpdates() {
        //loading version from properties file
        String currentVersionString = "0.0.0";
        final Properties properties = new Properties();
        try {
            properties.load(Main.class.getClassLoader().getResourceAsStream("info.properties"));
            currentVersionString = properties.getProperty("version");
            System.out.println("cepi-spring version: " + currentVersionString);
        } catch (IOException e) {
            System.out.println("cepi-spring version: unknown");
            System.out.println("Cannot update cepi-spring");
            return;
        }

        System.out.println("Checking for updates...");

        //loading latest version from github.
        String latestVersionString = "0.0.0";
        JSONObject jsonObject = downloadJSON("https://api.github.com/repos/celedev97/cepi-spring/releases/latest");
        if (jsonObject == null) {
            return;
        }
        latestVersionString = jsonObject.getString("tag_name");


        //comparing versions
        Version currentVersion = new Version(currentVersionString);
        Version latestVersion = new Version(latestVersionString);

        if(!latestVersion.isNewer(currentVersion)) {
            System.out.println("cepi-spring is up to date");
            return;
        }

        System.out.println("Update available: " + latestVersion.toString());
        String updateUrl = jsonObject.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");

        String oldJarPath = getJarPath();
        String newJarPath = oldJarPath.replace(".jar", "-update.jar");

        System.out.println("Downloading update...");
        downloadFile(updateUrl, newJarPath);

        //TODO: start a process to replace the current jar with the new one
        StringWriter command = new StringWriter();
        PrintWriter print = new PrintWriter(command);


        System.out.println("Replacing version with the new one...");
        //System.out.println("OLD PATH: " + oldJarPath);
        //System.out.println("NEW PATH: " + newJarPath);
        try{
            if(System.getProperty("os.name").toLowerCase().contains("win")) {
                System.out.println("Please restart cepi-spring");

                print.println("ping -n 2 127.0.0.1 > nul");
                print.println("DEL /F \""+oldJarPath+"\"");
                print.println("MOVE \""+newJarPath+"\" \""+oldJarPath+"\"");
                print.println("DEL /F update.bat");


                Path batPath = Paths.get("update.bat").toAbsolutePath();

                Files.write(batPath, command.toString().getBytes());

                Desktop.getDesktop().open(batPath.toFile());

                System.exit(0);
            } else {
                print.println("sleep 1");
                print.println("rm \""+oldJarPath+"\"");
                print.println("mv \""+newJarPath+"\" \""+oldJarPath+"\"");
                print.println("rm update.sh");

                Files.write(Paths.get("update.sh"), command.toString().getBytes());

                ProcessBuilder chmod = new ProcessBuilder("chmod", "777", "update.sh");
                chmod.start().waitFor();

                ProcessBuilder pb = new ProcessBuilder("./update.sh");
                pb.start();

                System.exit(0);
            }
        }catch (IOException | InterruptedException e){
            System.out.println("Error while replacing jar");
        }

    }

    private static String getJarPath() {
        try {
            return new File(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static JSONObject downloadJSON(String url) {
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(
                new URL(url).openStream()
        ))) {
            return new JSONObject(reader.lines().collect(Collectors.joining()));
        } catch (IOException e) {
            System.out.println("Cannot update cepi-spring, error connecting to github");
            return null;
        }
    }

    private static boolean downloadFile(String url, String outputFileName){
        try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(outputFileName)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private static class Version{
        private final int major;
        private final int minor;
        private final int patch;

        public Version(String version) {
            version = version.replace("v", "");
            String[] versionParts = version.split("\\.");
            major = versionParts.length>0 ? Integer.parseInt(versionParts[0]) : 0;
            minor = versionParts.length>1 ? Integer.parseInt(versionParts[1]) : 0;
            patch = versionParts.length>2 ? Integer.parseInt(versionParts[2]) : 0;
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }

        public boolean isNewer(Version version){

            if(major>version.major){
                return true;
            }else if (major<version.major){
                return false;
            }

            if(minor>version.minor){
                return true;
            } else if (minor<version.minor){
                return false;
            }

            if(patch>version.patch){
                return true;
            } else if (patch<version.patch){
                return false;
            }

            return false;
        }
    }
}
