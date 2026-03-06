package com.ihanuat.mod.util;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
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

public class ClientUtils {
    private static long lastCommandTime = 0;
    private static final long COMMAND_COOLDOWN_MS = 250;

    public static void sendDebugMessage(Minecraft client, String message) {
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
        if (client.player == null || client.getConnection() == null)
            return;

        long now = System.currentTimeMillis();
        long diff = now - lastCommandTime;

        if (diff < COMMAND_COOLDOWN_MS) {
            try {
                Thread.sleep(COMMAND_COOLDOWN_MS - diff);
            } catch (InterruptedException ignored) {
            }
        }

        if (cmd.startsWith("/")) {
            client.execute(() -> {
                if (client.getConnection() != null)
                    client.getConnection().sendCommand(cmd.substring(1));
            });
        } else {
            if (cmd.equalsIgnoreCase(".ez-stopscript")) {
                client.execute(() -> forceReleaseKeys(client));
            }
            client.execute(() -> {
                if (client.getConnection() != null)
                    client.getConnection().sendChat(cmd);
            });
        }

        lastCommandTime = System.currentTimeMillis();
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

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard != null
                ? scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
                : null;

        if (sidebar == null)
            return MacroState.Location.LIMBO;

        boolean hasLobbyItems = false;
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                String itemName = stack.getHoverName().getString().replaceAll("\u00A7[0-9a-fk-or]", "").trim();
                if (itemName.contains("Game Menu") || itemName.contains("My Profile")) {
                    hasLobbyItems = true;
                    break;
                }
            }
        }

        if (hasLobbyItems) {
            return MacroState.Location.LOBBY;
        }

        if (client.getConnection() != null) {
            Collection<net.minecraft.client.multiplayer.PlayerInfo> players = client.getConnection()
                    .getListedOnlinePlayers();

            for (net.minecraft.client.multiplayer.PlayerInfo info : players) {
                String name = "";
                if (info.getTabListDisplayName() != null) {
                    name = info.getTabListDisplayName().getString();
                } else if (info.getProfile() != null) {
                    name = String.valueOf(info.getProfile());
                }

                String clean = name.replaceAll("\u00A7[0-9a-fk-or]", "").trim();
                if (clean.contains("Area: Garden"))
                    return MacroState.Location.GARDEN;
                if (clean.contains("Area:")) {
                    return MacroState.Location.HUB;
                }
            }
        }

        return MacroState.Location.HUB;
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
            lines.add(fullText.replaceAll("\u00A7[0-9a-fk-or]", "").trim());
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
            String line = fullText.replaceAll("(?i)§[0-9A-FK-ORZ]", "").replaceAll(",", "").trim();

            if (line.contains("Purse:")) {
                try {
                    String valuePart = line.split("Purse:")[1].trim();
                    // Handle "26,000,000 (+300)" by taking only the first part before any space
                    String mainBalance = valuePart.split(" ")[0].replaceAll("[^0-9]", "");
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
            String line = fullText.replaceAll("(?i)\u00A7.", "").trim();

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
            // Wait for Wardrobe swap
            while (com.ihanuat.mod.modules.GearManager.isSwappingWardrobe)
                Thread.sleep(50);

            // Wait for Equipment swap
            while (com.ihanuat.mod.modules.GearManager.isSwappingEquipment)
                Thread.sleep(50);

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
            while (!com.ihanuat.mod.modules.GearManager.wardrobeGuiDetected
                    && System.currentTimeMillis() - start < 5000) {
                Thread.sleep(50);
            }
        } catch (InterruptedException ignored) {
        }
    }

    public static void waitForEquipmentGui(Minecraft client) {
        try {
            long start = System.currentTimeMillis();
            while (!com.ihanuat.mod.modules.GearManager.equipmentGuiDetected
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
                String itemName = stack.getHoverName().getString().replaceAll("\u00A7[0-9a-fk-or]", "").trim();
                String lowercaseName = itemName.toLowerCase();
                if (lowercaseName.contains("aspect of the void") || lowercaseName.contains("aspect of the end")) {
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
