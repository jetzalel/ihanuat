package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PestManager {
    private static final Pattern PESTS_ALIVE_PATTERN = Pattern.compile("(?i)(?:Pests|Alive):?\\s*\\(?(\\d+)\\)?");
    private static final Pattern COOLDOWN_PATTERN = Pattern
            .compile("(?i)Cooldown:\\s*\\(?(READY|MAX\\s*PESTS?|(?:(\\d+)m)?\\s*(?:(\\d+)s)?)\\)?");

    public static volatile boolean isCleaningInProgress = false;
    public static volatile String currentInfestedPlot = null;
    public static volatile boolean prepSwappedForCurrentPestCycle = false;
    public static volatile int currentPestSessionId = 0;
    public static volatile boolean isReturningFromPestVisitor = false;
    public static volatile boolean isStoppingFlight = false;
    public static int flightStopStage = 0;
    public static int flightStopTicks = 0;

    public static void reset() {
        isCleaningInProgress = false;
        prepSwappedForCurrentPestCycle = false;
        currentInfestedPlot = null;
        isReturningFromPestVisitor = false;
        isStoppingFlight = false;
        flightStopStage = 0;
        flightStopTicks = 0;
        currentPestSessionId++;
    }

    public static void checkTabListForPests(Minecraft client, MacroState.State currentState) {
        if (client.getConnection() == null || !com.ihanuat.mod.MacroStateManager.isMacroRunning())
            return;

        if (isCleaningInProgress) {
            // Only allow re-entry if we've been in CLEANING state for a long time without
            // finishing
            // This is a safety reset for 'stuck' conditions
            if (currentState == MacroState.State.FARMING) {
                isCleaningInProgress = false;
            } else {
                return;
            }
        }

        int aliveCount = -1;
        Set<String> infestedPlots = new HashSet<>();
        Collection<PlayerInfo> players = client.getConnection().getListedOnlinePlayers();

        for (PlayerInfo info : players) {
            String name = "";
            if (info.getTabListDisplayName() != null) {
                name = info.getTabListDisplayName().getString();
            } else if (info.getProfile() != null) {
                name = String.valueOf(info.getProfile());
            }

            String clean = name.replaceAll("(?i)\u00A7[0-9a-fk-or]", "").trim();
            // Replace non-breaking spaces with normal spaces for easier matching
            String normalized = clean.replace('\u00A0', ' ');

            Matcher aliveMatcher = PESTS_ALIVE_PATTERN.matcher(normalized);
            if (aliveMatcher.find()) {
                int found = Integer.parseInt(aliveMatcher.group(1));
                if (found > aliveCount)
                    aliveCount = found;
            }

            if (normalized.toUpperCase().contains("MAX PESTS")) {
                aliveCount = 99; // Explicitly high count to ensure threshold is met
            }

            Matcher cooldownMatcher = COOLDOWN_PATTERN.matcher(normalized);
            if (cooldownMatcher.find()) {
                String cdVal = cooldownMatcher.group(1).toUpperCase();
                int cooldownSeconds = -1;

                if (cdVal.contains("MAX PEST")) {
                    aliveCount = 99; // Treat as max threshold met
                    cooldownSeconds = 999; // High cooldown value to avoid prep-swap during max state
                } else if (cdVal.equalsIgnoreCase("READY")) {
                    cooldownSeconds = 0;
                } else {
                    int m = 0;
                    int s = 0;
                    if (cooldownMatcher.group(2) != null)
                        m = Integer.parseInt(cooldownMatcher.group(2));
                    if (cooldownMatcher.group(3) != null)
                        s = Integer.parseInt(cooldownMatcher.group(3));

                    if (m > 0 || s > 0) {
                        cooldownSeconds = (m * 60) + s;
                    }
                }

                if (MacroConfig.autoEquipment) {
                    if (cooldownSeconds > MacroConfig.autoEquipmentFarmingTime && prepSwappedForCurrentPestCycle
                            && !isCleaningInProgress) {
                        prepSwappedForCurrentPestCycle = false;
                    }
                } else {
                    if (cooldownSeconds > 3 && prepSwappedForCurrentPestCycle && !isCleaningInProgress) {
                        prepSwappedForCurrentPestCycle = false;
                    }
                }

                // Prep swap logic
                if (currentState == MacroState.State.FARMING && cooldownSeconds != -1 && cooldownSeconds >= 0
                        && !prepSwappedForCurrentPestCycle && !isCleaningInProgress) {

                    boolean thresholdMet = (aliveCount >= MacroConfig.pestThreshold || aliveCount >= 8);
                    if (!thresholdMet) {
                        if (MacroConfig.autoEquipment) {
                            if (cooldownSeconds <= MacroConfig.autoEquipmentFarmingTime)
                                triggerPrepSwap(client);
                        } else if (cooldownSeconds <= 3) {
                            triggerPrepSwap(client);
                        }
                    } else {
                        // Threshold met, prep will be skipped and startCleaningSequence will be called
                        // after loop
                    }
                }
            }

            if (normalized.contains("Plot")) {
                Matcher m = Pattern.compile("(\\d+)").matcher(normalized);
                while (m.find()) {
                    infestedPlots.add(m.group(1).trim());
                }
            }
        }

        if (aliveCount >= MacroConfig.pestThreshold || aliveCount >= 8) {
            if (aliveCount >= 8 && aliveCount < 99) {
                client.player.displayClientMessage(Component.literal("§eMax Pests (8) reached. Starting cleaning..."),
                        true);
            }
            String targetPlot = infestedPlots.isEmpty() ? "0" : infestedPlots.iterator().next();
            startCleaningSequence(client, targetPlot);
        }
    }

    public static void handlePestCleaningFinished(Minecraft client) {
        client.player.displayClientMessage(Component.literal("§aPest cleaning finished detected."), true);
        new Thread(() -> {
            try {
                if (MacroConfig.unflyMode == MacroConfig.UnflyMode.DOUBLE_TAP_SPACE) {
                    performUnfly(client);
                    Thread.sleep(150);
                }

                int visitors = VisitorManager.getVisitorCount(client);
                if (visitors >= MacroConfig.visitorThreshold) {
                    client.player.displayClientMessage(
                            Component.literal("\u00A7dVisitor Threshold Met (" + visitors + "). Warping to Garden..."),
                            true);
                    ClientUtils.sendCommand(client, "/warp garden");
                    Thread.sleep(1000);

                    client.execute(() -> {
                        GearManager.swapToFarmingTool(client);
                    });
                    Thread.sleep(250);

                    if (MacroConfig.armorSwapVisitor && MacroConfig.wardrobeSlotVisitor > 0
                            && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                        client.player.displayClientMessage(Component.literal(
                                "\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor
                                        + ")..."),
                                true);
                        client.execute(() -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor));
                        Thread.sleep(400);
                    }

                    ClientUtils.waitForGearAndGui(client);
                    try {
                        ClientUtils.sendCommand(client, ".ez-stopscript");
                        Thread.sleep(250);
                        ClientUtils.sendCommand(client, ".ez-startscript misc:visitor");
                    } catch (InterruptedException ignored) {
                    }
                    isCleaningInProgress = false;
                    return;
                }

                Thread.sleep(150);
                ClientUtils.sendCommand(client, "/warp garden");
                Thread.sleep(1000); // 1s wait for garden load
                isReturningFromPestVisitor = true;
                finalizeReturnToFarm(client);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

    }

    static void performUnfly(Minecraft client) throws InterruptedException {
        if (client.player == null)
            return;

        if (MacroConfig.unflyMode == MacroConfig.UnflyMode.DOUBLE_TAP_SPACE) {
            isStoppingFlight = true;
            flightStopStage = 0;
            flightStopTicks = 0;

            long deadline = System.currentTimeMillis() + 3000;
            while (isStoppingFlight && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
        } else {
            // SNEAK mode
            client.execute(() -> {
                if (client.options != null)
                    client.options.keyShift.setDown(true);
            });
            Thread.sleep(150);
            client.execute(() -> {
                if (client.options != null)
                    client.options.keyShift.setDown(false);
            });
        }
    }

    private static void finalizeReturnToFarm(Minecraft client) {
        if (!com.ihanuat.mod.MacroStateManager.isMacroRunning())
            return;

        try {
            if (MacroConfig.unflyMode == MacroConfig.UnflyMode.SNEAK) {
                performUnfly(client);
                Thread.sleep(150);
            }

            int visitors = VisitorManager.getVisitorCount(client);
            if (visitors >= MacroConfig.visitorThreshold) {
                client.execute(() -> {
                    GearManager.swapToFarmingTool(client);
                });
                Thread.sleep(250);

                if (MacroConfig.armorSwapVisitor && MacroConfig.wardrobeSlotVisitor > 0
                        && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                    client.player.displayClientMessage(Component.literal(
                            "\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor + ")..."),
                            true);
                    client.execute(() -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor));
                    Thread.sleep(400);
                }

                ClientUtils.waitForGearAndGui(client);
                try {
                    ClientUtils.sendCommand(client, ".ez-stopscript");
                    Thread.sleep(250);
                    ClientUtils.sendCommand(client, ".ez-startscript misc:visitor");
                } catch (InterruptedException ignored) {
                }
                isCleaningInProgress = false;
                return;
            }

            client.execute(() -> {
                GearManager.swapToFarmingTool(client);
            });
            Thread.sleep(250);

            if (MacroConfig.armorSwapVisitor && MacroConfig.wardrobeSlotFarming > 0
                    && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotFarming) {
                client.player.displayClientMessage(Component.literal(
                        "§eRestoring Farming Wardrobe (Slot " + MacroConfig.wardrobeSlotFarming + ")..."), true);
                client.execute(() -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming));
                Thread.sleep(400);
            }

            ClientUtils.waitForGearAndGui(client);
            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
            prepSwappedForCurrentPestCycle = false; // Ensure flag is reset when returning
            try {
                ClientUtils.sendCommand(client, ".ez-stopscript");
                Thread.sleep(250);
                ClientUtils.sendCommand(client, MacroConfig.restartScript);
            } catch (InterruptedException ignored) {
            }
            isCleaningInProgress = false;
        } catch (InterruptedException ignored) {
        }
    }

    public static void update(Minecraft client) {
        checkTabListForPests(client, com.ihanuat.mod.MacroStateManager.getCurrentState());
    }

    private static void triggerPrepSwap(Minecraft client) {
        prepSwappedForCurrentPestCycle = true;
        client.player.displayClientMessage(Component.literal("\u00A7ePest cooldown detected. Triggering prep-swap..."),
                true);
        new Thread(() -> {
            try {
                ClientUtils.sendCommand(client, ".ez-stopscript");
                Thread.sleep(375);
                if (isCleaningInProgress) {
                    prepSwappedForCurrentPestCycle = false;
                    return;
                }

                if (MacroConfig.autoEquipment) {
                    GearManager.ensureEquipment(client, false);
                    Thread.sleep(375);
                    while (GearManager.isSwappingEquipment && !isCleaningInProgress)
                        Thread.sleep(50);
                    Thread.sleep(250);
                }

                if (isCleaningInProgress) {
                    prepSwappedForCurrentPestCycle = false;
                    return;
                }

                if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.ROD) {
                    GearManager.executeRodSequence(client);
                } else if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE) {
                    GearManager.triggerWardrobeSwap(client, MacroConfig.wardrobeSlotPest);
                } else {
                    resumeAfterPrepSwap(client);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void resumeAfterPrepSwap(Minecraft client) {
        client.execute(() -> {
            GearManager.swapToFarmingTool(client);
            if (isCleaningInProgress)
                return;
            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
            ClientUtils.sendCommand(client, MacroConfig.restartScript);
        });
    }

    public static void startCleaningSequence(Minecraft client, String plot) {
        if (isCleaningInProgress || GearManager.isSwappingWardrobe || GearManager.isSwappingEquipment)
            return;

        ClientUtils.sendCommand(client, ".ez-stopscript");
        isCleaningInProgress = true;
        GearManager.shouldRestartFarmingAfterSwap = false;
        com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.CLEANING);
        currentInfestedPlot = plot;
        final int sessionId = ++currentPestSessionId;

        new Thread(() -> {
            try {
                Thread.sleep(850); // Slightly longer delay for script stop

                if (sessionId != currentPestSessionId)
                    return;

                if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE) {
                    int targetSlot = MacroConfig.wardrobeSlotFarming;
                    boolean needsSwap = prepSwappedForCurrentPestCycle || GearManager.trackedWardrobeSlot != targetSlot;

                    if (needsSwap && targetSlot > 0) {
                        client.player.displayClientMessage(Component.literal(
                                "§eRestoring Farming Wardrobe (Slot " + targetSlot + ") for Vacuuming..."), true);
                        client.execute(() -> GearManager.ensureWardrobeSlot(client, targetSlot));
                        Thread.sleep(400);
                        while (GearManager.isSwappingWardrobe)
                            Thread.sleep(50);
                        while (GearManager.wardrobeCleanupTicks > 0)
                            Thread.sleep(50);
                        Thread.sleep(250);
                    } else {
                        client.player.displayClientMessage(
                                Component.literal("§aGear verified: Already in Farming Wardrobe."), true);
                    }
                }

                if (MacroConfig.autoEquipment) {
                    // Always try to ensures farming gear for vacuuming
                    GearManager.ensureEquipment(client, true);
                    Thread.sleep(400);
                    while (GearManager.isSwappingEquipment)
                        Thread.sleep(50);
                    Thread.sleep(250);
                }

                prepSwappedForCurrentPestCycle = false;

                client.player.displayClientMessage(
                        Component.literal("§6Starting Pest Cleaner script (" + currentInfestedPlot + ")..."), true);
                ClientUtils.sendCommand(client, "/setspawn");
                Thread.sleep(400); // Wait on thread, not main thread

                try {
                    ClientUtils.sendCommand(client, ".ez-stopscript");
                    Thread.sleep(250);
                    client.execute(() -> GearManager.swapToFarmingTool(client));
                    Thread.sleep(250);
                    ClientUtils.sendCommand(client, ".ez-startscript misc:pestCleaner");
                } catch (InterruptedException ignored) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void triggerCleaningNow(Minecraft client, Set<String> infestedPlots) {
        String targetPlot = infestedPlots.isEmpty() ? "0" : infestedPlots.iterator().next();
        startCleaningSequence(client, targetPlot);
    }

    public static void resumeAfterPrepSwapLogic(Minecraft client) {
        new Thread(() -> {
            try {
                client.execute(() -> GearManager.swapToFarmingTool(client));
                Thread.sleep(250);
                ClientUtils.sendCommand(client, ".ez-stopscript");
                Thread.sleep(250);
                ClientUtils.sendCommand(client, MacroConfig.restartScript);
            } catch (InterruptedException ignored) {
            }
        }).start();
    }
}
