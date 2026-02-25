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
    private static final Pattern PESTS_ALIVE_PATTERN = Pattern.compile("(?:Pests|Alive):?\\s*\\(?(\\d+)\\)?");
    private static final Pattern COOLDOWN_PATTERN = Pattern
            .compile("Cooldown:\\s*\\(?(READY|(?:(\\d+)m)?\\s*(?:(\\d+)s)?)\\)?");

    public static volatile boolean isCleaningInProgress = false;
    public static volatile String currentInfestedPlot = null;
    public static volatile boolean prepSwappedForCurrentPestCycle = false;
    public static volatile int currentPestSessionId = 0;
    public static volatile boolean isReturningFromPestVisitor = false;

    public static void reset() {
        isCleaningInProgress = false;
        prepSwappedForCurrentPestCycle = false;
        currentInfestedPlot = null;
        isReturningFromPestVisitor = false;
        currentPestSessionId++;
    }

    public static void checkTabListForPests(Minecraft client, MacroState.State currentState) {
        if (client.getConnection() == null || isCleaningInProgress)
            return;

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
            Matcher aliveMatcher = PESTS_ALIVE_PATTERN.matcher(clean);
            if (aliveMatcher.find()) {
                aliveCount = Integer.parseInt(aliveMatcher.group(1));
            } else if (clean.contains("Alive: MAX PESTS") || clean.contains("Pests: MAX PESTS")) {
                aliveCount = 99; // Explicitly high count to ensure threshold is met
            }

            Matcher cooldownMatcher = COOLDOWN_PATTERN.matcher(clean);
            if (cooldownMatcher.find()) {
                int cooldownSeconds = -1;
                if (cooldownMatcher.group(1).equalsIgnoreCase("READY")) {
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
                    if (cooldownSeconds > 170 && prepSwappedForCurrentPestCycle && !isCleaningInProgress) {
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

                    if (MacroConfig.autoEquipment) {
                        if (cooldownSeconds <= 170)
                            triggerPrepSwap(client);
                    } else if (cooldownSeconds <= 3) {
                        triggerPrepSwap(client);
                    }
                }
            }

            if (clean.contains("Plot")) {
                Matcher m = Pattern.compile("(\\d+)").matcher(clean);
                while (m.find()) {
                    infestedPlots.add(m.group(1).trim());
                }
            }
        }

        if (aliveCount >= MacroConfig.pestThreshold && !infestedPlots.isEmpty()) {
            startCleaningSequence(client, infestedPlots.iterator().next());
        }
    }

    public static void handlePestCleaningFinished(Minecraft client) {
        client.player.displayClientMessage(Component.literal("§aPest cleaning finished detected."), true);
        new Thread(() -> {
            try {
                int visitors = VisitorManager.getVisitorCount(client);
                if (visitors >= MacroConfig.visitorThreshold) {
                    client.player.displayClientMessage(
                            Component.literal("§dVisitor Threshold Met (" + visitors + "). Direct Transition in 1s..."),
                            true);
                    Thread.sleep(1000);
                    GearManager.swapToFarmingTool(client);

                    if (MacroConfig.armorSwapVisitor && MacroConfig.wardrobeSlotVisitor > 0
                            && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                        client.player.displayClientMessage(Component.literal(
                                "§eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor + ")..."),
                                true);
                        client.execute(() -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor));
                        Thread.sleep(400);
                        while (GearManager.isSwappingWardrobe)
                            Thread.sleep(50);
                        while (GearManager.wardrobeCleanupTicks > 0)
                            Thread.sleep(50);
                        Thread.sleep(250);
                    }

                    ClientUtils.sendCommand(client, ".ez-startscript misc:visitor");
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

    private static void finalizeReturnToFarm(Minecraft client) {
        if (!com.ihanuat.mod.MacroStateManager.isMacroRunning())
            return;

        if (client.options != null) {
            client.options.keyShift.setDown(true);
        }
        try {
            Thread.sleep(150);
            int visitors = VisitorManager.getVisitorCount(client);
            if (visitors >= MacroConfig.visitorThreshold) {
                GearManager.swapToFarmingTool(client);

                if (MacroConfig.armorSwapVisitor && MacroConfig.wardrobeSlotVisitor > 0
                        && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                    client.player.displayClientMessage(Component.literal(
                            "§eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor + ")..."), true);
                    client.execute(() -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor));
                    Thread.sleep(400);
                    while (GearManager.isSwappingWardrobe)
                        Thread.sleep(50);
                    while (GearManager.wardrobeCleanupTicks > 0)
                        Thread.sleep(50);
                    Thread.sleep(250);
                }

                ClientUtils.sendCommand(client, ".ez-startscript misc:visitor");
                isCleaningInProgress = false;
                return;
            }

            GearManager.swapToFarmingTool(client);

            if (MacroConfig.armorSwapVisitor && MacroConfig.wardrobeSlotFarming > 0
                    && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotFarming) {
                client.player.displayClientMessage(Component.literal(
                        "§eRestoring Farming Wardrobe (Slot " + MacroConfig.wardrobeSlotFarming + ")..."), true);
                client.execute(() -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming));
                Thread.sleep(400);
                while (GearManager.isSwappingWardrobe)
                    Thread.sleep(50);
                while (GearManager.wardrobeCleanupTicks > 0)
                    Thread.sleep(50);
                Thread.sleep(250);
            }

            if (MacroConfig.autoEquipment) {
                GearManager.ensureEquipment(client, true); // Restore farming gear
                Thread.sleep(400);
                while (GearManager.isSwappingEquipment)
                    Thread.sleep(50);
                Thread.sleep(250);
            }

            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
            prepSwappedForCurrentPestCycle = false; // Ensure flag is reset when returning
            ClientUtils.sendCommand(client, MacroConfig.restartScript);
            isCleaningInProgress = false;
        } catch (InterruptedException ignored) {
        }
    }

    public static void update(Minecraft client) {
    }

    private static void triggerPrepSwap(Minecraft client) {
        prepSwappedForCurrentPestCycle = true;
        client.player.displayClientMessage(Component.literal("\u00A7ePest cooldown detected. Triggering prep-swap..."),
                true);
        new Thread(() -> {
            try {
                ClientUtils.sendCommand(client, ".ez-stopscript");
                Thread.sleep(375);
                if (isCleaningInProgress)
                    return;

                if (MacroConfig.autoEquipment) {
                    GearManager.ensureEquipment(client, false);
                    Thread.sleep(375);
                    while (GearManager.isSwappingEquipment && !isCleaningInProgress)
                        Thread.sleep(50);
                    Thread.sleep(250);
                }

                if (isCleaningInProgress)
                    return;

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
        GearManager.swapToFarmingTool(client);
        com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
        ClientUtils.sendCommand(client, MacroConfig.restartScript);
    }

    public static void startCleaningSequence(Minecraft client, String plot) {
        if (isCleaningInProgress || GearManager.isSwappingWardrobe || GearManager.isSwappingEquipment)
            return;

        final boolean wasPrepSwapped = prepSwappedForCurrentPestCycle;
        ClientUtils.sendCommand(client, ".ez-stopscript");
        isCleaningInProgress = true;
        com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.CLEANING);
        currentInfestedPlot = plot;
        prepSwappedForCurrentPestCycle = false;
        final int sessionId = ++currentPestSessionId;

        new Thread(() -> {
            try {
                Thread.sleep(850); // Slightly longer delay for script stop

                if (sessionId != currentPestSessionId)
                    return;

                if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE) {
                    int targetSlot = MacroConfig.wardrobeSlotFarming;
                    boolean needsSwap = wasPrepSwapped || GearManager.trackedWardrobeSlot != targetSlot;

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

                client.execute(() -> {
                    GearManager.swapToFarmingTool(client); // Just in case, to have vacuum ready if needed
                    ClientUtils.sendCommand(client, "/setspawn");
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ignored) {
                    }
                    ClientUtils.sendCommand(client, ".ez-startscript misc:pestCleaner");
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void resumeAfterPrepSwapLogic(Minecraft client) {
        GearManager.swapToFarmingTool(client);
        ClientUtils.sendCommand(client, MacroConfig.restartScript);
    }
}
