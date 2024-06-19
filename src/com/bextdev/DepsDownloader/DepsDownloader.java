package com.bextdev.DepsDownloader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.zip.*;
import org.json.*;

public class DepsDownloader {

    private static final String MAVEN_SEARCH_URL = "https://search.maven.org/solrsearch/select?q=";
    private static final int DOWNLOAD_TIMEOUT_MS = 5000; 

    public static void main(String[] args) {
        // Display ASCII banner
        displayBanner();

        if (args.length != 2 || !args[0].equalsIgnoreCase("downloadSearch")) {
            System.out.println("Usage: depsdownloader downloadSearch --library_name");
            return;
        }

        String libraryName = args[1].replaceFirst("--", "");

        try {
            // Start timing the download process
            long startTime = System.currentTimeMillis();

            // Search for the library in Maven Central
            JSONObject mavenInfo = searchMavenCentral(libraryName);

            if (mavenInfo == null) {
                System.out.println("Library not found in Maven Central.");
                return;
            }

            String groupId = mavenInfo.getString("g");
            String artifactId = mavenInfo.getString("a");
            String version = mavenInfo.getString("latestVersion");

            // Construct the Maven URL
            String mavenUrl = String.format("https://repo1.maven.org/maven2/%s/%s/%s/%s-%s.aar",
                    groupId.replace('.', '/'),
                    artifactId,
                    version,
                    artifactId,
                    version);

            // Download the .aar file
            downloadFile(mavenUrl, libraryName + ".aar");

            // Check if the download exceeded the time limit
            long endTime = System.currentTimeMillis();
            if ((endTime - startTime) > DOWNLOAD_TIMEOUT_MS) {
                System.out.println("Error: Download took too long.");
                return;
            }

            // Rename .aar to .zip
            Path aarPath = Paths.get(libraryName + ".aar");
            Path zipPath = Paths.get(libraryName + ".zip");
            Files.move(aarPath, zipPath, StandardCopyOption.REPLACE_EXISTING);

            // Extract classes.jar from the .zip file
            extractJarFromZip(zipPath.toString(), "classes.jar", libraryName + ".jar");

            // Move the JAR file to the deps folder
            Path depsFolder = Paths.get("deps");
            if (!Files.exists(depsFolder)) {
                Files.createDirectory(depsFolder);
            }
            Path jarPath = Paths.get(libraryName + ".jar");
            Files.move(jarPath, depsFolder.resolve(jarPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);

            // Delete the temporary .zip file
            Files.delete(zipPath);

            System.out.println("Dependency downloaded and processed successfully!");

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private static void displayBanner() {
        System.out.println("  ____                  ____                      _                   ");
        System.out.println(" |  _ \\  ___  ___ ___  |  _ \\  ___  ___ ___ _ __ (_)_ __   __ _ _ __  ");
        System.out.println(" | | | |/ _ \\/ __/ __| | | | |/ _ \\/ __/ _ \\ '_ \\| | '_ \\ / _` | '_ \\ ");
        System.out.println(" | |_| |  __/\\__ \\__ \\ | |_| |  __/ (_|  __/ | | | | | | | (_| | | | |");
        System.out.println(" |____/ \\___||___/___/ |____/ \\___|\\___\\___|_| |_|_|_| |_|\\__,_|_| |_|");
        System.out.println("                                                                      ");
        System.out.println(" Developed by Bextdev");
        System.out.println();
    }

    private static JSONObject searchMavenCentral(String libraryName) throws IOException, JSONException {
        URL url = new URL(MAVEN_SEARCH_URL + libraryName + "&rows=1&wt=json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }

            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONArray docs = jsonResponse.getJSONObject("response").getJSONArray("docs");

            if (docs.length() > 0) {
                return docs.getJSONObject(0);
            }
        }

        return null;
    }

    private static void downloadFile(String url, String outputFileName) throws IOException {
        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, Paths.get(outputFileName), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void extractJarFromZip(String zipFilePath, String jarFileName, String outputJarFileName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(jarFileName)) {
                    try (FileOutputStream fos = new FileOutputStream(outputJarFileName)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                    break;
                }
            }
        }
    }
}
