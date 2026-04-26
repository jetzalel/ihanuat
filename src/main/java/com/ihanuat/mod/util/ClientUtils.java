package com.ihanuat.mod.util;

import com.ihanuat.mod.DebugLogger;
import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroWorkerThread;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.DisplaySlot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientUtils {
    private static long lastCommandTime = 0;
    private static final long COMMAND_COOLDOWN_MS = 250;
    private static final long LOCATION_CACHE_VALIDITY_MS = 100;
    public static final java.util.regex.Pattern COLOR_PATTERN = java.util.regex.Pattern.compile("(?i)§[0-9A-FK-ORZ]");
    public static final java.util.regex.Pattern COMMA_PATTERN = java.util.regex.Pattern.compile(",");
    public static final java.util.regex.Pattern NON_DIGIT_PATTERN = java.util.regex.Pattern.compile("[^0-9]");
    private static final Pattern SIDEBAR_PEST_COUNT_PATTERN = Pattern.compile("(?i)\\bx\\s*(\\d+)\\b");
    private static MacroState.Location cachedLocation = MacroState.Location.UNKNOWN;
    private static long cachedLocationAtMs = 0;

    public static String stripColor(String text) {
        if (text == null) return null;
        return COLOR_PATTERN.matcher(text).replaceAll("");
    }

    public static void sendDebugMessage(Minecraft client, String message) {
        // Always feed the file logger (it checks MacroConfig.logDebugToFile itself)
        DebugLogger.getInstance().log(message);

        if (MacroConfig.showDebug) {
            client.execute(() -> {
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("§9[Debug] " + message),
                            false);
                }
            });
        }
    }

    public static void sendCommand(Minecraft client, String cmd) {
        sendCommand(client, cmd, false);
    }

    public static void sendCommand(Minecraft client, String cmd, boolean forceClientSide) {
        if (client.player == null || client.getConnection() == null)
            return;

        long now = System.currentTimeMillis();
        long diff = now - lastCommandTime;

        if (diff < COMMAND_COOLDOWN_MS) {
            if (client.isSameThread()) {
                MacroWorkerThread.getInstance().submit("DeferredCommand:" + abbreviateCommandLabel(cmd),
                        () -> sendCommand(client, cmd, forceClientSide));
                return;
            }
            try {
                Thread.sleep(COMMAND_COOLDOWN_MS - diff);
            } catch (InterruptedException ignored) {
            }
        }

        if (cmd.startsWith("/")) {
            client.execute(() -> {
                if (client.player != null) {
                    if (forceClientSide) {
                        // Use chat to allow client-side mods (Skytils, NEU) to intercept
                        client.player.connection.sendChat(cmd);
                    } else {
                        // Direct server command
                        client.player.connection.sendCommand(cmd.substring(1));
                    }
                }
            });
        } else {
            if (cmd.equalsIgnoreCase(".ez-stopscript")) {
                client.execute(() -> forceReleaseKeys(client));
            }
            client.execute(() -> {
                if (client.player != null)
                    client.player.connection.sendChat(cmd);
            });
        }

        lastCommandTime = System.currentTimeMillis();
    }

    private static String abbreviateCommandLabel(String cmd) {
        if (cmd == null || cmd.isBlank()) {
            return "unknown";
        }
        String trimmed = cmd.trim();
        return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 24);
    }

    public static void forceReleaseKeys(Minecraft client) {
        if (client.options != null) {
            client.options.keyUp.setDown(false);
            client.options.keyDown.setDown(false);
            client.options.keyLeft.setDown(false);
            client.options.keyRight.setDown(false);
            client.options.keyJump.setDown(false);
            client.options.keyShift.setDown(false);
            client.options.keyAttack.setDown(false);
            client.options.keyUse.setDown(false);
        }

        if (client.mouseHandler != null) {
            client.mouseHandler.releaseMouse();
        }
    }

    public static void pressHotbarSlot(Minecraft client, int slotIndex) {
        // slotIndex is 0-based (0..8), so first slot is 0
        if (client == null || client.player == null || slotIndex < 0 || slotIndex > 8) {
            if (client != null) sendDebugMessage(client, "pressHotbarSlot skipped: invalid args (" + slotIndex + ")");
            return;
        }

        client.execute(() -> {
            if (client.player == null) return;

            int currentSlot = ((com.ihanuat.mod.mixin.AccessorInventory) client.player.getInventory()).getSelected();
            if (currentSlot == slotIndex) {
                sendDebugMessage(client, "pressHotbarSlot: already on slot " + slotIndex);
                return;
            }

            ((com.ihanuat.mod.mixin.AccessorInventory) client.player.getInventory()).setSelected(slotIndex);

            if (MacroConfig.showDebug) {
                client.player.displayClientMessage(
                        Component.literal("§epressHotbarSlot: selected slot " + (slotIndex + 1)),
                        true);
            }
            sendDebugMessage(client, "pressHotbarSlot direct setSelected slot " + slotIndex);
        });

        MacroWorkerThread.sleep(60);
    }

    public static MacroState.Location getCurrentLocation(Minecraft client) {
        if (client.level == null || client.player == null)
            return MacroState.Location.UNKNOWN;

        if (!client.isSameThread()) {
            java.util.concurrent.CompletableFuture<MacroState.Location> future = new java.util.concurrent.CompletableFuture<>();
            client.execute(() -> {
                future.complete(getCurrentLocation(client));
            });
            try {
                return future.get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                return MacroState.Location.UNKNOWN;
            }
        }

        long now = System.currentTimeMillis();
        if (now - cachedLocationAtMs <= LOCATION_CACHE_VALIDITY_MS) {
            return cachedLocation;
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard != null
                ? scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
                : null;

        if (sidebar == null) {
            cachedLocation = MacroState.Location.LIMBO;
            cachedLocationAtMs = now;
            return cachedLocation;
        }

        // flag for if we are in skyblock or not
        String sidebarTitle = stripColor(sidebar.getDisplayName().getString()).trim();
        boolean inSkyblock = sidebarTitle.toUpperCase().contains("SKYBLOCK");

        boolean hasLobbyItems = false;
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                String itemName = stripColor(stack.getHoverName().getString()).trim();
                if (itemName.contains("Game Menu") || itemName.contains("My Profile")) {
                    hasLobbyItems = true;
                    break;
                }
            }
        }

        if (hasLobbyItems || !inSkyblock) {
            cachedLocation = MacroState.Location.LOBBY;
            cachedLocationAtMs = now;
            return cachedLocation;
        }

        for (String line : TabListCache.getTabLines(client)) {
            if (line.contains("Area: Garden")) {
                cachedLocation = MacroState.Location.GARDEN;
                cachedLocationAtMs = now;
                return cachedLocation;
            }
            if (line.contains("Area:")) {
                cachedLocation = MacroState.Location.HUB;
                cachedLocationAtMs = now;
                return cachedLocation;
            }
        }

        cachedLocation = MacroState.Location.HUB;
        cachedLocationAtMs = now;
        return cachedLocation;
    }

    public static long getContestRemainingMs(Minecraft client) {
        if (client.level == null || client.player == null)
            return 0;

        Scoreboard scoreboard = client.level.getScoreboard();
        if (scoreboard == null)
            return 0;

        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null)
            return 0;

        Collection<PlayerScoreEntry> scores = scoreboard.listPlayerScores(sidebar);
        List<String> lines = new ArrayList<>();

        for (PlayerScoreEntry entry : scores) {
            String entryName = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(entryName);
            String fullText = entryName;
            if (team != null) {
                fullText = team.getPlayerPrefix().getString() + entryName + team.getPlayerSuffix().getString();
            }
            lines.add(stripColor(fullText).trim());
        }

        Collections.reverse(lines);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("Jacob's Contest")) {
                if (i + 1 < lines.size()) {
                    String timeLine = lines.get(i + 1);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:(\\d+)m\\s*)?(?:(\\d+)s)?$")
                            .matcher(timeLine);
                    if (m.find()) {
                        long ms = 0;
                        if (m.group(1) != null)
                            ms += Long.parseLong(m.group(1)) * 60000L;
                        if (m.group(2) != null)
                            ms += Long.parseLong(m.group(2)) * 1000L;
                        return ms;
                    }
                }
                break;
            }
        }
        return 0;
    }

    public static long getPurse(Minecraft client) {
        if (client.level == null || client.player == null)
            return 0;

        Scoreboard scoreboard = client.level.getScoreboard();
        if (scoreboard == null)
            return 0;

        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null)
            return 0;

        Collection<PlayerScoreEntry> scores = scoreboard.listPlayerScores(sidebar);
        for (PlayerScoreEntry entry : scores) {
            String entryName = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(entryName);
            String fullText = entryName;
            if (team != null) {
                fullText = team.getPlayerPrefix().getString() + entryName + team.getPlayerSuffix().getString();
            }
            String line = COMMA_PATTERN.matcher(stripColor(fullText)).replaceAll("").trim();

            if (line.contains("Purse:")) {
                try {
                    String valuePart = line.split("Purse:")[1].trim();
                    String firstPart = valuePart.split(" ")[0];
                    String mainBalance = NON_DIGIT_PATTERN.matcher(firstPart).replaceAll("");
                    return Long.parseLong(mainBalance);
                } catch (Exception ignored) {
                }
            }
        }
        return -1;
    }

    public static String getCurrentPlot(Minecraft client) {
        if (client.level == null || client.player == null)
            return "Unknown";

        Scoreboard scoreboard = client.level.getScoreboard();
        if (scoreboard == null)
            return "Unknown";

        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null)
            return "Unknown";

        Collection<PlayerScoreEntry> scores = scoreboard.listPlayerScores(sidebar);
        for (PlayerScoreEntry entry : scores) {
            String entryName = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(entryName);
            String fullText = entryName;
            if (team != null) {
                fullText = team.getPlayerPrefix().getString() + entryName + team.getPlayerSuffix().getString();
            }
            String line = stripColor(fullText).trim();

            // Match formats: "Plot: 14", "Plot - 6", "Plot #14", "Plot: Barn"
            // Handles non-standard color codes like §y and multiple spaces.
            if (line.toLowerCase().contains("plot")) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("plot\\s*[:\\-#]\\s*([a-z0-9]+)",
                        java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(line);
                if (m.find()) {
                    return m.group(1).trim();
                }
            }
        }

        // If we reached here, we couldn't find the "Plot:" line.
        // Let's print all lines to debug if showDebug is on.
        if (MacroConfig.showDebug) {
            sendDebugMessage(client, "Failed to find Plot in Scoreboard. Lines found:");
            for (PlayerScoreEntry entry : scores) {
                String entryName = entry.owner();
                PlayerTeam team = scoreboard.getPlayersTeam(entryName);
                String fullText = entryName;
                if (team != null) {
                    fullText = team.getPlayerPrefix().getString() + entryName + team.getPlayerSuffix().getString();
                }
                sendDebugMessage(client, " - " + fullText.replaceAll("(?i)\u00A7[0-9A-FK-ORZ]", ""));
            }
        }

        return "Unknown";
    }

    public static int getGardenPestCountFromSidebar(Minecraft client) {
        if (client.level == null || client.player == null)
            return -1;

        Scoreboard scoreboard = client.level.getScoreboard();
        if (scoreboard == null)
            return -1;

        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null)
            return -1;

        Collection<PlayerScoreEntry> scores = scoreboard.listPlayerScores(sidebar);
        String currentPlot = getCurrentPlot(client);

        for (PlayerScoreEntry entry : scores) {
            String entryName = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(entryName);
            String fullText = entryName;
            if (team != null) {
                fullText = team.getPlayerPrefix().getString() + entryName + team.getPlayerSuffix().getString();
            }

            String line = stripColor(fullText).trim();
            String lowerLine = line.toLowerCase();
            boolean isPlotLine = lowerLine.contains("plot -")
                    || lowerLine.contains("plot:")
                    || lowerLine.contains("plot #");
            if (!isPlotLine) {
                continue;
            }

            if (!"Unknown".equalsIgnoreCase(currentPlot)
                    && !lowerLine.matches(".*plot\\s*[:\\-#]\\s*" + java.util.regex.Pattern.quote(currentPlot.toLowerCase()) + ".*")) {
                continue;
            }

            Matcher matcher = SIDEBAR_PEST_COUNT_PATTERN.matcher(line);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }

            return 0;
        }

        return -1;
    }

    public static boolean hasLineOfSight(Player player, Vec3 target) {
        if (player.level() == null)
            return false;
        Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                eyePos, target,
                net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player);
        net.minecraft.world.phys.BlockHitResult result = player.level().clip(context);
        return result.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    public static void lookAt(Player player, Vec3 target) {
        com.ihanuat.mod.RotationUtils.Rotation rot = com.ihanuat.mod.RotationUtils
                .calculateLookAt(player.getEyePosition(), target);
        player.setYRot(rot.yaw);
        player.setXRot(rot.pitch);
    }

    public static void sleepRandom(int min, int max) throws InterruptedException {
        long sleepTime = min + (long) (Math.random() * (max - min + 1));
        Thread.sleep(sleepTime);
    }

    public static void waitForGearAndGui(Minecraft client) {
        try {
            // Wait for Wardrobe swap with timeout failsafe
            long wardrobeStart = System.currentTimeMillis();
            while (com.ihanuat.mod.modules.WardrobeManager.isSwappingWardrobe
                    && System.currentTimeMillis() - wardrobeStart < 6000) {
                Thread.sleep(50);
            }
            // Force-failsafe: if wardrobe swap is still pending after timeout, trigger completion and continue
            if (com.ihanuat.mod.modules.WardrobeManager.isSwappingWardrobe) {
                sendDebugMessage(client,
                        "§eWARNING: Wardrobe swap detection timeout. Force-completing and resuming sequence...");
                com.ihanuat.mod.modules.WardrobeManager.forceWardrobeCompletionFailsafe(client);
            }

            // Wait for Equipment swap with timeout failsafe
            long equipStart = System.currentTimeMillis();
            while (com.ihanuat.mod.modules.EquipmentManager.isSwappingEquipment
                    && System.currentTimeMillis() - equipStart < 6000) {
                Thread.sleep(50);
            }
            // Force-failsafe: if equipment swap is still pending after timeout, reset and continue
            if (com.ihanuat.mod.modules.EquipmentManager.isSwappingEquipment) {
                sendDebugMessage(client,
                        "§eWARNING: Equipment swap detection timeout. Force-resetting and continuing sequence...");
                com.ihanuat.mod.modules.EquipmentManager.resetState();
            }

            // Check for any open GUI (wardrobe, equipment, or any other menu)
            long guiStart = System.currentTimeMillis();
            while (client.screen != null && System.currentTimeMillis() - guiStart < 5000) {
                Thread.sleep(100);
            }

            // Small safety delay after GUI is gone
            Thread.sleep(250);
        } catch (InterruptedException ignored) {
        }
    }

    public static void waitForWardrobeGui(Minecraft client) {
        try {
            long start = System.currentTimeMillis();
            long lastRetry = start;
            int retryCount = 0;
            while (!com.ihanuat.mod.modules.WardrobeManager.wardrobeGuiDetected
                    && System.currentTimeMillis() - start < 5000) {
                if (!com.ihanuat.mod.modules.WardrobeManager.isSwappingWardrobe)
                    return;

                long now = System.currentTimeMillis();
                if (now - lastRetry >= 500) {
                    retryCount++;
                    sendDebugMessage(client,
                            "[Debug] Wardrobe GUI not detected after " + (now - start)
                                    + "ms. Retrying /wardrobe (" + retryCount + ")");
                    client.execute(() -> sendCommand(client, "/wardrobe"));
                    lastRetry = now;
                }
                Thread.sleep(50);
            }
        } catch (InterruptedException ignored) {
        }
    }

    public static void waitForEquipmentGui(Minecraft client) {
        try {
            long start = System.currentTimeMillis();
            while (!com.ihanuat.mod.modules.EquipmentManager.equipmentGuiDetected
                    && System.currentTimeMillis() - start < 5000) {
                Thread.sleep(50);
            }
        } catch (InterruptedException ignored) {
        }
    }

    public static void waitForYChange(Minecraft client, double startY, long timeoutMs) {
        if (client.player == null)
            return;

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            double currentY = client.player.getY();
            if (Math.abs(currentY - startY) > 1) {
                sendDebugMessage(client, "AOTV teleport successful");
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
                break;
            }
        }
    }

    public static int findAspectOfTheVoidSlot(Minecraft client) {
        if (client.player == null)
            return -1;

        for (int i = 0; i < 36; i++) {
            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                String itemName = stripColor(stack.getHoverName().getString()).trim();
                String lowercaseName = itemName.toLowerCase();
                if (lowercaseName.contains("aspect of the void") || lowercaseName.contains("aspect of the end")) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Searches the hotbar (slots 0-8) for any item whose display name contains
     * "vacuum" (case-insensitive). Returns the slot index, or -1 if not found.
     */
    public static int findVacuumSlot(Minecraft client) {
        if (client.player == null)
            return -1;
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                String name = stack.getHoverName().getString().replaceAll("\u00A7[0-9a-fk-or]", "").trim();
                if (name.toLowerCase().contains("vacuum")) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static void performShiftRightClick(Minecraft client) {
        if (client.player == null || client.options == null)
            return;

        client.execute(() -> {
            client.options.keyShift.setDown(true);
            client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        });

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }

        client.execute(() -> client.options.keyShift.setDown(false));
    }

    public static void waitForRotationToComplete(Minecraft client, float targetPitch, int rotationTime) {
        if (client.player == null)
            return;

        long startTime = System.currentTimeMillis();
        long timeout = 5000; // 5 second timeout

        while (System.currentTimeMillis() - startTime < timeout) {
            float currentPitch = client.player.getXRot();
            float pitchDiff = Math.abs(currentPitch - targetPitch);

            if (pitchDiff < 1.0f) {
                break; // Rotation complete
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
