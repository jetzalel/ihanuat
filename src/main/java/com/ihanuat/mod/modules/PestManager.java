package com.ihanuat.mod.modules;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.mixin.AccessorInventory;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class PestManager {
    private static final Pattern PESTS_ALIVE_PATTERN = Pattern.compile("(?i)(?:Pests|Alive):?\\s*\\(?(\\d+)\\)?");
    private static final Pattern COOLDOWN_PATTERN = Pattern
            .compile("(?i)Cooldown:\\s*\\(?(READY|MAX\\s*PESTS?|(?:(\\d+)m)?\\s*(?:(\\d+)s)?)\\)?");

    public static volatile boolean isCleaningInProgress = false;
    public static volatile String currentInfestedPlot = null;
    public static volatile boolean prepSwappedForCurrentPestCycle = false;
    public static volatile int currentPestSessionId = 0;
    public static volatile boolean isReturningFromPestVisitor = false;
    public static volatile boolean isReturnToLocationActive = false;
    public static volatile boolean isStoppingFlight = false;
    public static volatile boolean isSneakingForAotv = false;
    public static int flightStopStage = 0;
    public static int flightStopTicks = 0;
    public static volatile boolean isPrepSwapping = false;
    public static volatile boolean isBonusInactive = false;
    public static volatile boolean isReactivatingBonus = false;
    private static long lastZeroPestTime = 0;

    public static void reset() {
        isCleaningInProgress = false;
        prepSwappedForCurrentPestCycle = false;
        currentInfestedPlot = null;
        isReturningFromPestVisitor = false;
        isReturnToLocationActive = false;
        isStoppingFlight = false;
        isSneakingForAotv = false;
        flightStopStage = 0;
        flightStopTicks = 0;
        isPrepSwapping = false;
        isReactivatingBonus = false;
        lastZeroPestTime = 0;
        currentPestSessionId++;
    }

    public static void checkTabListForPests(Minecraft client, MacroState.State currentState) {
        if (client.getConnection() == null || client.player == null || !MacroStateManager.isMacroRunning())
            return;

        if (isCleaningInProgress && currentState == MacroState.State.FARMING) {
            isCleaningInProgress = false;
        }

        int aliveCount = -1;
        boolean bonusFound = false;
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
                    if (cooldownSeconds > 170 && prepSwappedForCurrentPestCycle
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
                        && !prepSwappedForCurrentPestCycle && !isCleaningInProgress && !isReturnToLocationActive) {

                    boolean thresholdMet = (aliveCount >= MacroConfig.pestThreshold || aliveCount >= 8);
                    if (!thresholdMet) {
                        if (MacroConfig.autoEquipment) {
                            if (cooldownSeconds <= 170)
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

            if (normalized.toUpperCase().contains("BONUS: INACTIVE")) {
                bonusFound = true;
            }
        }

        isBonusInactive = bonusFound;

        // Failsafe: if cleaning and 0 pests for 10s, return to farming
        if (currentState == MacroState.State.CLEANING) {
            if (aliveCount <= 0) {
                if (lastZeroPestTime == 0) {
                    lastZeroPestTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - lastZeroPestTime > 10000) {
                    client.player.displayClientMessage(
                            Component.literal("§cFail-safe: No pests detected for 10s. Returning to farm."), true);
                    lastZeroPestTime = 0;
                    handlePestCleaningFinished(client);
                    return;
                }
            } else {
                lastZeroPestTime = 0;
            }
        } else {
            lastZeroPestTime = 0;
        }

        if (isCleaningInProgress)
            return;

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
                    MacroState.Location loc = ClientUtils.getCurrentLocation(client);
                    if (loc != MacroState.Location.GARDEN) {
                        client.player.displayClientMessage(
                                Component.literal(
                                        "\u00A7dVisitor Threshold Met (" + visitors + "). Warping to Garden..."),
                                true);
                        com.ihanuat.mod.util.CommandUtils.warpGarden(client);
                        Thread.sleep(250);
                    } else {
                        ClientUtils.sendDebugMessage(client, "Already in Garden, skipping /warp garden for visitors");
                    }

                    GearManager.swapToFarmingToolSync(client);

                    if (MacroConfig.armorSwapVisitor && MacroConfig.wardrobeSlotVisitor > 0
                            && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                        client.player.displayClientMessage(Component.literal(
                                "\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor
                                        + ")..."),
                                true);
                        GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor);
                        if (GearManager.isSwappingWardrobe) {
                            ClientUtils.waitForWardrobeGui(client);
                            while (GearManager.isSwappingWardrobe)
                                Thread.sleep(50);
                            while (GearManager.wardrobeCleanupTicks > 0)
                                Thread.sleep(50);
                            Thread.sleep(250);
                        }
                    }

                    ClientUtils.waitForGearAndGui(client);
                    ClientUtils.sendDebugMessage(client, "Wardrobe swap done, now triggering visitor macro");
                    MacroStateManager.setCurrentState(MacroState.State.VISITING);
                    ClientUtils.sendDebugMessage(client, "Stopping script: Visitor threshold reached");
                    com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
                    ClientUtils.sendDebugMessage(client, "Starting visitor macro script");
                    com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:visitor", 0);
                    isCleaningInProgress = false;
                    client.player.displayClientMessage(
                            Component.literal("§ePest cleaner finished (visitors)."), false);
                    return;
                }

                Thread.sleep(150);
                ClientUtils.sendDebugMessage(client, "Warping to garden (PestManager)...");
                com.ihanuat.mod.util.CommandUtils.warpGarden(client);
                Thread.sleep(250);
                isReturningFromPestVisitor = true;
                ClientUtils.sendDebugMessage(client, "Finalizing return to farm (PestManager)...");
                finalizeReturnToFarm(client);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

    }

    public static void performUnfly(Minecraft client) throws InterruptedException {
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
            // Already handled in handlePestCleaningFinished but just in case it's called
            // from elsewhere
            if (MacroConfig.unflyMode == MacroConfig.UnflyMode.SNEAK) {
                performUnfly(client);
                Thread.sleep(150);
            }

            int visitors = VisitorManager.getVisitorCount(client);
            if (visitors >= MacroConfig.visitorThreshold) {
                client.execute(() -> {
                    GearManager.swapToFarmingTool(client);
                });

                if (MacroConfig.armorSwapVisitor && MacroConfig.wardrobeSlotVisitor > 0
                        && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                    client.player.displayClientMessage(Component.literal(
                            "\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor + ")..."),
                            true);
                    GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor);
                    if (GearManager.isSwappingWardrobe) {
                        ClientUtils.waitForWardrobeGui(client);
                        while (GearManager.isSwappingWardrobe)
                            Thread.sleep(50);
                        while (GearManager.wardrobeCleanupTicks > 0)
                            Thread.sleep(50);
                        Thread.sleep(250);
                    }
                }

                // Wait for any remaining GUIs and wardrobe swap (equipment swap not done for
                // visitors)
                try {
                    while (GearManager.isSwappingWardrobe)
                        Thread.sleep(50);
                    long guiStart = System.currentTimeMillis();
                    while (client.screen != null && System.currentTimeMillis() - guiStart < 5000) {
                        Thread.sleep(100);
                    }
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                }
                ClientUtils.sendDebugMessage(client, "Wardrobe swap done, now triggering visitor macro");
                ClientUtils.sendDebugMessage(client, "Stopping script: Returning to visitor macro");
                com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
                ClientUtils.sendDebugMessage(client, "Starting visitor macro script");
                com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:visitor", 0);
                isCleaningInProgress = false;
                return;
            }

            GearManager.swapToFarmingToolSync(client);
            if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.ROD_2X) {
                ClientUtils.sendDebugMessage(client, "ROD 2X: Triggering second rod cast (PestManager)...");
                GearManager.executeRodSequence(client);
            }

            // Only wait for gear swap if equipment swap is enabled (since it's only done
            // during cleaning if enabled)
            if (MacroConfig.autoEquipment) {
                ClientUtils.waitForGearAndGui(client);
            }

            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
            prepSwappedForCurrentPestCycle = false; // Ensure flag is reset when returning
            ClientUtils.sendDebugMessage(client, "Stopping script: Pest cleaning finished, returning to farming");
            com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
            isCleaningInProgress = false;
            if (client.player != null) {
                ClientUtils.sendDebugMessage(client, "Pest cleaner finished.");
            }
            com.ihanuat.mod.util.ClientUtils.sendDebugMessage(client,
                    "Pest cleaning sequence finished. Restarting farming...");
            ClientUtils.sendDebugMessage(client, "Starting farming script: " + MacroConfig.getFullRestartCommand());
            com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
        } catch (InterruptedException ignored) {
        }
    }

    public static void update(Minecraft client) {
        checkTabListForPests(client, com.ihanuat.mod.MacroStateManager.getCurrentState());
    }

    private static void triggerPrepSwap(Minecraft client) {
        prepSwappedForCurrentPestCycle = true;
        isPrepSwapping = true;
        ClientUtils.sendDebugMessage(client, "Pest cooldown detected. Triggering prep-swap...");
        new Thread(() -> {
            try {
                ClientUtils.sendDebugMessage(client, "Stopping script: Triggering prep-swap");
                com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
                // Wait for script to actually stop before attempting wardrobe swap
                Thread.sleep(400);

                if (isCleaningInProgress) {
                    prepSwappedForCurrentPestCycle = false;
                    return;
                }

                if (isCleaningInProgress) {
                    prepSwappedForCurrentPestCycle = false;
                    return;
                }

                // 1. Wardrobe (Synchronous wait)
                if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE && MacroConfig.wardrobeSlotPest > 0) {
                    ClientUtils.sendDebugMessage(client,
                            "Prep-swap: Initiating wardrobe swap to slot " + MacroConfig.wardrobeSlotPest);
                    GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotPest);
                    if (GearManager.isSwappingWardrobe) {
                        ClientUtils.sendDebugMessage(client, "Prep-swap: Waiting for wardrobe GUI...");
                        ClientUtils.waitForWardrobeGui(client);

                        // Check if wardrobe GUI was actually detected
                        if (!GearManager.wardrobeGuiDetected) {
                            ClientUtils.sendDebugMessage(client,
                                    "§cPrep-swap: Wardrobe GUI not detected! Retrying in 1 second...");
                            Thread.sleep(1000);

                            // Retry wardrobe swap
                            ClientUtils.sendDebugMessage(client, "Prep-swap: Retry - Initiating wardrobe swap to slot "
                                    + MacroConfig.wardrobeSlotPest);
                            GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotPest);
                            if (GearManager.isSwappingWardrobe) {
                                ClientUtils.sendDebugMessage(client, "Prep-swap: Retry - Waiting for wardrobe GUI...");
                                ClientUtils.waitForWardrobeGui(client);

                                if (!GearManager.wardrobeGuiDetected) {
                                    ClientUtils.sendDebugMessage(client,
                                            "§cPrep-swap: Wardrobe GUI still not detected after retry! Aborting prep-swap.");
                                    prepSwappedForCurrentPestCycle = false;
                                    return;
                                } else {
                                    ClientUtils.sendDebugMessage(client,
                                            "§aPrep-swap: Retry successful! Wardrobe GUI detected.");
                                }
                            }
                            ClientUtils.sendDebugMessage(client, "§aPrep-swap: Wardrobe GUI detected successfully.");
                        }

                        while (GearManager.isSwappingWardrobe && !isCleaningInProgress)
                            Thread.sleep(50);
                        Thread.sleep(250);
                        ClientUtils.sendDebugMessage(client, "Prep-swap: Wardrobe swap completed.");
                    } else {
                        ClientUtils.sendDebugMessage(client,
                                "Prep-swap: Wardrobe swap not needed (already on correct slot).");
                    }
                }

                if (isCleaningInProgress) {
                    prepSwappedForCurrentPestCycle = false;
                    return;
                }

                // 2. Equipment (Synchronous wait)
                if (MacroConfig.autoEquipment) {
                    ClientUtils.sendDebugMessage(client, "Prep-swap: Initiating equipment swap to pest gear");
                    GearManager.ensureEquipment(client, false);
                    // Give server time to open GUI before we even check
                    Thread.sleep(200);
                    ClientUtils.sendDebugMessage(client, "Prep-swap: Waiting for equipment GUI...");
                    ClientUtils.waitForEquipmentGui(client);

                    // Check if equipment GUI was actually detected
                    if (!GearManager.equipmentGuiDetected) {
                        ClientUtils.sendDebugMessage(client,
                                "§cPrep-swap: Equipment GUI not detected! Retrying in 1 second...");
                        Thread.sleep(1000);

                        // Retry equipment swap
                        ClientUtils.sendDebugMessage(client,
                                "Prep-swap: Retry - Initiating equipment swap to pest gear");
                        GearManager.ensureEquipment(client, false);
                        Thread.sleep(200);
                        ClientUtils.sendDebugMessage(client, "Prep-swap: Retry - Waiting for equipment GUI...");
                        ClientUtils.waitForEquipmentGui(client);

                        if (!GearManager.equipmentGuiDetected) {
                            ClientUtils.sendDebugMessage(client,
                                    "§cPrep-swap: Equipment GUI still not detected after retry! Aborting prep-swap.");
                            prepSwappedForCurrentPestCycle = false;
                            return;
                        } else {
                            ClientUtils.sendDebugMessage(client,
                                    "§aPrep-swap: Retry successful! Equipment GUI detected.");
                        }
                    } else {
                        ClientUtils.sendDebugMessage(client, "§aPrep-swap: Equipment GUI detected successfully.");
                    }

                    while (GearManager.isSwappingEquipment && !isCleaningInProgress)
                        Thread.sleep(50);

                    // Ensure the screen is actually gone
                    while (client.screen != null && !isCleaningInProgress) {
                        Thread.sleep(50);
                    }
                    Thread.sleep(250);
                    ClientUtils.sendDebugMessage(client, "Prep-swap: Equipment swap completed.");
                }

                if (isCleaningInProgress) {
                    prepSwappedForCurrentPestCycle = false;
                    return;
                }

                // 3. Rod Sequence (Wait for previous steps confirmed by GearManager checks)
                if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.ROD
                        || MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.ROD_2X) {
                    GearManager.executeRodSequence(client);
                }

                // 3. Final Resume
                if (!isCleaningInProgress) {
                    GearManager.finalResume(client);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isPrepSwapping = false;
            }
        }).start();
    }

    public static void startCleaningSequence(Minecraft client, String plot) {
        if (isCleaningInProgress || GearManager.isSwappingWardrobe || GearManager.isSwappingEquipment)
            return;

        ClientUtils.sendDebugMessage(client,
                "Stopping script: Pest threshold reached, starting cleaning sequence for plot " + plot);
        com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
        isCleaningInProgress = true;
        GearManager.shouldRestartFarmingAfterSwap = false;
        com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.CLEANING);
        currentInfestedPlot = plot;
        final int sessionId = ++currentPestSessionId;
        final String currentPlot = ClientUtils.getCurrentPlot(client);

        new Thread(() -> {
            try {
                Thread.sleep(850);
                if (sessionId != currentPestSessionId)
                    return;

                // 1. Gear Restoration
                if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE) {
                    int targetSlot = MacroConfig.wardrobeSlotFarming;
                    if ((prepSwappedForCurrentPestCycle || GearManager.trackedWardrobeSlot != targetSlot)
                            && targetSlot > 0) {
                        client.player.displayClientMessage(
                                Component.literal(
                                        "§eRestoring Farming Wardrobe (Slot " + targetSlot + ") for Vacuuming..."),
                                true);
                        client.execute(() -> GearManager.ensureWardrobeSlot(client, targetSlot));
                        ClientUtils.waitForWardrobeGui(client);
                        while (GearManager.isSwappingWardrobe)
                            Thread.sleep(50);
                        while (GearManager.wardrobeCleanupTicks > 0)
                            Thread.sleep(50);
                        Thread.sleep(250);
                    }
                }

                if (MacroConfig.autoEquipment) {
                    GearManager.ensureEquipment(client, true);
                    ClientUtils.waitForEquipmentGui(client);
                    while (GearManager.isSwappingEquipment)
                        Thread.sleep(50);
                    Thread.sleep(250);
                }

                prepSwappedForCurrentPestCycle = false;
                client.player.displayClientMessage(
                        Component.literal("§6Starting Pest Cleaner script (" + currentInfestedPlot + ")..."), true);
                com.ihanuat.mod.util.CommandUtils.setSpawn(client);

                if (isBonusInactive) {
                    client.player.displayClientMessage(
                            Component.literal("§dBonus is INACTIVE! Triggering Phillip reactivation..."), true);
                    isReactivatingBonus = true;
                    com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 0);
                    return;
                }

                // 2. AOTV and Teleport Logic
                boolean shouldDoAotv = false;
                boolean isSamePlot = currentInfestedPlot != null && currentInfestedPlot.equals(currentPlot);

                if (MacroConfig.aotvToRoof) {
                    if (MacroConfig.aotvRoofPlots.isEmpty()) {
                        shouldDoAotv = isSamePlot;
                    } else {
                        if (MacroConfig.aotvRoofPlots.contains(currentInfestedPlot)) {
                            ClientUtils.sendDebugMessage(client, "plot in list, performing aotv");
                            shouldDoAotv = true;
                        } else {
                            ClientUtils.sendDebugMessage(client, "plot not in list, skipping aotv");
                        }
                    }
                }

                // Warp to infested plot if not already there
                if (!isSamePlot && currentInfestedPlot != null && !currentInfestedPlot.equals("0")) {
                    if (com.ihanuat.mod.util.CommandUtils.plotTp(client, currentInfestedPlot)) {
                        Thread.sleep(250);
                    } else {
                        client.player.displayClientMessage(
                                Component.literal("§cFailed to warp to plot " + currentInfestedPlot + "!"), true);
                        shouldDoAotv = false; // Cancel AOTV if warp failed
                    }
                }

                if (shouldDoAotv) {
                    isSneakingForAotv = true;
                    Vec3 eyePos = client.player.getEyePosition();
                    float yawRad = (float) Math.toRadians(client.player.getYRot());
                    double offsetX = -Math.sin(yawRad) * (0.0008 + Math.random() * 0.0006);
                    double offsetZ = Math.cos(yawRad) * (0.0008 + Math.random() * 0.0006);
                    Vec3 targetPos = new Vec3(eyePos.x + offsetX, eyePos.y + (85.0 + Math.random() * 6.0),
                            eyePos.z + offsetZ);
                    int rotTime = (int) (MacroConfig.rotationTime * (0.92 + Math.random() * 0.16));

                    RotationManager.initiateRotation(client, targetPos, rotTime);
                    ClientUtils.waitForRotationToComplete(client, -90.0f, rotTime);

                    int aotvSlot = ClientUtils.findAspectOfTheVoidSlot(client);
                    if (aotvSlot != -1 && aotvSlot < 9) {
                        client.execute(() -> ((AccessorInventory) client.player.getInventory()).setSelected(aotvSlot));
                        Thread.sleep(150);
                        double startY = client.player.getY();
                        Thread.sleep(50 + (long) (Math.random() * 80));
                        client.execute(() -> client.gameMode.useItem(client.player,
                                net.minecraft.world.InteractionHand.MAIN_HAND));

                        ClientUtils.waitForYChange(client, startY, 1500);
                        Thread.sleep(40 + (long) (Math.random() * 60));
                        isSneakingForAotv = false;
                        client.execute(() -> client.options.keyShift.setDown(false));
                        Thread.sleep(30 + (long) (Math.random() * 50));

                        // Look forward again
                        Vec3 postEyePos = client.player.getEyePosition();
                        float yawPost = (float) Math.toRadians(client.player.getYRot());
                        double targetPitch = -5.0 + Math.random() * 10.0;
                        Vec3 forward = new Vec3(postEyePos.x - Math.sin(yawPost) * 100,
                                postEyePos.y + Math.tan(Math.toRadians(-targetPitch)) * 5.0,
                                postEyePos.z + Math.cos(yawPost) * 100);
                        RotationManager.initiateRotation(client, forward, 250 + (int) (Math.random() * 150));
                    } else {
                        // Fallback: AOTV failed or not found
                        isSneakingForAotv = false;
                        client.execute(() -> client.options.keyShift.setDown(false));
                        // No redundant plotTp needed here as we already warped above
                    }
                }

                // 3. Start Cleaning Script
                ClientUtils.sendDebugMessage(client, "Ready to start pest cleaner");
                com.ihanuat.mod.util.CommandUtils.stopScript(client, 50);
                GearManager.swapToFarmingToolSync(client);
                ClientUtils.sendDebugMessage(client, "Starting pest cleaner script for plot " + currentInfestedPlot);
                com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 0);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void triggerCleaningNow(Minecraft client, Set<String> infestedPlots) {
        String targetPlot = infestedPlots.isEmpty() ? "0" : infestedPlots.iterator().next();
        startCleaningSequence(client, targetPlot);
    }

    public static void handlePhillipMessage(Minecraft client, String text) {
        if (!isReactivatingBonus || client.player == null)
            return;

        String plain = text.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
        if (plain.toLowerCase().contains("pesthunter phillip") && plain.toLowerCase().contains("thanks for the")) {
            client.player.displayClientMessage(Component.literal(
                    "§aPhillip message detected! Returning to plot §e" + currentInfestedPlot + "..."), true);
            new Thread(() -> {
                try {
                    ClientUtils.sendDebugMessage(client,
                            "Stopping script: Phillip message detected, reactivating bonus");
                    com.ihanuat.mod.util.CommandUtils.stopScript(client, MacroConfig.getRandomizedDelay(250));
                    ClientUtils.sendDebugMessage(client, "Teleporting back to plot " + currentInfestedPlot);
                    com.ihanuat.mod.util.CommandUtils.plotTp(client, currentInfestedPlot);
                    ClientUtils.sendDebugMessage(client, "Starting pest cleaner script after Phillip message");
                    com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 250);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    isReactivatingBonus = false;
                }
            }).start();
        }
    }
}
