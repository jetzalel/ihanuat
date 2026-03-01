package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class DiscordStatusManager {
    private static long lastUpdateTime = System.currentTimeMillis();
    private static boolean isTakingScreenshot = false;
    private static long screenshotRequestTime = 0;

    public static void update(Minecraft client) {
        if (!MacroConfig.sendDiscordStatus || MacroConfig.discordWebhookUrl == null
                || MacroConfig.discordWebhookUrl.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        // convert minutes to milliseconds for interval
        if (!isTakingScreenshot && now - lastUpdateTime >= MacroConfig.discordStatusUpdateTime * 60 * 1000L) {
            lastUpdateTime = now;
            takeAndSendScreenshot(client);
        }

        if (isTakingScreenshot && now - screenshotRequestTime > 2000) {
            // It's been 2s since we requested the screenshot, it should be saved by now.
            isTakingScreenshot = false;
            sendScreenshotAsync(client);
        }
    }

    private static void takeAndSendScreenshot(Minecraft client) {
        isTakingScreenshot = true;
        screenshotRequestTime = System.currentTimeMillis();
        client.execute(() -> {
            Screenshot.grab(client.gameDirectory, client.getMainRenderTarget(), (msg) -> {
                // Ignore the message
            });
        });
    }

    private static void sendScreenshotAsync(Minecraft client) {
        new Thread(() -> {
            try {
                File screenshotsDir = new File(client.gameDirectory, "screenshots");
                if (!screenshotsDir.exists())
                    return;

                File latestScreenshot = Files.list(screenshotsDir.toPath())
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".png"))
                        .filter(p -> System.currentTimeMillis() - p.toFile().lastModified() < 15000) // created in last
                                                                                                     // 15s
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                        .map(Path::toFile).orElse(null);

                if (latestScreenshot == null)
                    return;

                sendWebhook(MacroConfig.discordWebhookUrl, latestScreenshot);
                try {
                    Files.deleteIfExists(latestScreenshot.toPath());
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void sendWebhook(String webhookUrl, File imageFile) throws Exception {
        String boundary = "===" + System.currentTimeMillis() + "===";
        URL url = new java.net.URI(webhookUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("User-Agent", "Java-DiscordWebhook-Client");

        try (OutputStream outputStream = connection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true)) {

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"payload_json\"\r\n");
            writer.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");

            String state = String.valueOf(MacroStateManager.getCurrentState());
            long sessionTotalSecs = MacroStateManager.getSessionStartTime() == 0 ? 0
                    : (System.currentTimeMillis() - MacroStateManager.getSessionStartTime()) / 1000;
            long sHours = sessionTotalSecs / 3600;
            long sMins = (sessionTotalSecs % 3600) / 60;
            long sSecs = sessionTotalSecs % 60;
            String sessionStr = sHours > 0
                    ? String.format("%02d:%02d:%02d", sHours, sMins, sSecs)
                    : String.format("%02d:%02d", sMins, sSecs);

            long nextRestTriggerMs = DynamicRestManager.getNextRestTriggerMs();
            String nextRestStr;
            if (nextRestTriggerMs > 0 && !DynamicRestManager.isRestPending()) {
                long remaining = nextRestTriggerMs - System.currentTimeMillis();
                if (remaining > 0) {
                    long mins = (remaining / 1000) / 60;
                    long secs = (remaining / 1000) % 60;
                    nextRestStr = String.format("%02d:%02d", mins, secs);
                } else {
                    nextRestStr = "Starting soon...";
                }
            } else if (DynamicRestManager.isRestPending()) {
                nextRestStr = "Resting now...";
            } else {
                nextRestStr = "Not scheduled";
            }

            // Using Gson to safely escape the strings is highly recommended,
            // but we can construct the embed JSON directly for simplicity here.
            String jsonTemplate = "{\n" +
                    "  \"embeds\": [{\n" +
                    "    \"title\": \"Status Update\",\n" +
                    "    \"description\": \"Here is your latest Ihanuat status update! :rocket:\",\n" +
                    "    \"color\": 5814783,\n" +
                    "    \"fields\": [\n" +
                    "      {\n" +
                    "        \"name\": \"Current State\",\n" +
                    "        \"value\": \"`%s`\",\n" +
                    "        \"inline\": true\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"name\": \"Session Time\",\n" +
                    "        \"value\": \"`%s`\",\n" +
                    "        \"inline\": true\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"name\": \"Time Until Next Rest\",\n" +
                    "        \"value\": \"`%s`\",\n" +
                    "        \"inline\": true\n" +
                    "      }\n" +
                    "    ],\n" +
                    "    \"image\": {\n" +
                    "      \"url\": \"attachment://%s\"\n" +
                    "    }\n" +
                    "  }]\n" +
                    "}";

            String jsonFileName = imageFile.getName();
            String json = String.format(jsonTemplate, state, sessionStr, nextRestStr, jsonFileName);

            writer.append(json).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + imageFile.getName() + "\"\r\n");
            writer.append("Content-Type: image/png\r\n\r\n");
            writer.flush();

            Files.copy(imageFile.toPath(), outputStream);
            outputStream.flush();

            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
            writer.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            System.out.println("Discord webhook sent successfully.");
        } else {
            System.err.println("Discord webhook failed with response code: " + responseCode);
        }
    }
}
