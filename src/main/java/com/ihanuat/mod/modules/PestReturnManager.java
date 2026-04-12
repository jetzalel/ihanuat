package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class PestReturnManager {
    public static volatile boolean isReturningFromPestVisitor = false;
    public static volatile boolean isReturnToLocationActive = false;
    public static volatile boolean isStoppingFlight = false;
    public static volatile boolean isFinishingInProgress = false;
    public static int flightStopStage = 0;
    public static int flightStopTicks = 0;


    public static void resetState() {
        isReturningFromPestVisitor = false;
        isReturnToLocationActive = false;
        isStoppingFlight = false;
        isFinishingInProgress = false;
        flightStopStage = 0;
        flightStopTicks = 0;
    }

    public static void handlePestCleaningFinished(Minecraft client) {
        if (isFinishingInProgress) {
            ClientUtils.sendDebugMessage(client, "Pest cleaning finish already in progress, ignoring duplicate trigger.");
            return;
        }
        isFinishingInProgress = true;
        ClientUtils.sendDebugMessage(client, "Pest cleaning finished sequence started.");
        client.player.displayClientMessage(Component.literal("§aPest cleaning finished detected."), true);
        MacroWorkerThread.getInstance().submit("PestCleaning-Finished", () -> {
            try {
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                ClientUtils.sendDebugMessage(client, "Finisher: Performing unfly ...");
                performUnfly(client);
                MacroWorkerThread.sleep(150);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;

                int visitors = VisitorManager.getVisitorCount(client);
                ClientUtils.sendDebugMessage(client, "Finisher: Visitor count check: " + visitors + " (Threshold: "
                        + MacroConfig.visitorThreshold + ")");
                if (visitors >= MacroConfig.visitorThreshold
                    && !VisitorManager.isVisitorReentryCooldownActive(client, true)) {
                    MacroState.Location loc = ClientUtils.getCurrentLocation(client);
                    if (loc != MacroState.Location.GARDEN) {
                        client.player.displayClientMessage(
                                Component.literal(
                                        "\u00A7dVisitor Threshold Met (" + visitors + "). Warping to Garden..."),
                                true);
                        com.ihanuat.mod.util.CommandUtils.warpGarden(client);
                        performSneakUnfly(client);
                        MacroWorkerThread.sleep(250);
                    } else {
                        ClientUtils.sendDebugMessage(client, "Already in Garden, skipping /warp garden for visitors");
                    }

                    GearManager.swapToFarmingToolSync(client);

                    if (MacroConfig.autoWardrobeVisitor && MacroConfig.wardrobeSlotVisitor > 0
                            && WardrobeManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                        client.player.displayClientMessage(Component.literal(
                                "\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor
                                        + ")..."),
                                true);
                        GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor);
                        if (WardrobeManager.isSwappingWardrobe) {
                            ClientUtils.sendDebugMessage(client, "Finisher (Visitor): Waiting for wardrobe GUI...");
                            ClientUtils.waitForWardrobeGui(client);
                            ClientUtils.sendDebugMessage(client,
                                    "Finisher (Visitor): Wardrobe GUI cleared, waiting for swap completion...");
                            while (WardrobeManager.isSwappingWardrobe)
                                MacroWorkerThread.sleep(50);
                            while (WardrobeManager.wardrobeCleanupTicks > 0)
                                MacroWorkerThread.sleep(50);
                            MacroWorkerThread.sleep(250);
                        }
                    }

                    ClientUtils.sendDebugMessage(client,
                            "Finisher (Visitor): Gear restoration done, waiting for stability...");
                    ClientUtils.waitForGearAndGui(client);
                    ClientUtils.sendDebugMessage(client,
                            "Finisher (Visitor): stability reached, transitioning to VISITING.");
                    ClientUtils.sendDebugMessage(client,
                            "Wardrobe swap done, now triggering visitor macro. Next state: VISITING");
                    PestManager.isCleaningInProgress = false;
                    MacroStateManager.setCurrentState(MacroState.State.VISITING);
                    ClientUtils.sendDebugMessage(client, "Stopping script: Visitor threshold reached");
                    com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
                    ClientUtils.sendDebugMessage(client, "Starting visitor macro script");
                    com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:visitor", 0);
                    client.player.displayClientMessage(
                            Component.literal("§ePest cleaner finished (visitors)."), false);
                    return;
                }

                if (visitors >= MacroConfig.visitorThreshold) {
                    ClientUtils.sendDebugMessage(client,
                            "Finisher: Visitor threshold met, but cooldown is active. Returning to farm.");
                }

                MacroWorkerThread.sleep(150);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;

                if (MacroConfig.autoRodReturnToFarm) {
                    ClientUtils.sendDebugMessage(client,
                            "Finisher: Auto Rod - Triggering rod cast before /warp garden.");
                    RodManager.executeRodSequence(client);
                    if (MacroWorkerThread.shouldAbortTask(client))
                        return;
                }

                ClientUtils.sendDebugMessage(client, "Finisher: Warping to garden (Return to Farm)...");
                com.ihanuat.mod.util.CommandUtils.warpGarden(client);
                performSneakUnfly(client);
                MacroWorkerThread.sleep(250);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                isReturningFromPestVisitor = true;
                ClientUtils.sendDebugMessage(client, "Finisher: Calling finalizeReturnToFarm...");
                finalizeReturnToFarm(client);
            } catch (Exception e) {
                e.printStackTrace();
                ClientUtils.sendDebugMessage(client,
                        "§cCRITICAL ERROR in handlePestCleaningFinished: " + e.getMessage());
                ClientUtils.sendDebugMessage(client, "§6Triggering failsafe: Returning to farming...");
                PestManager.isCleaningInProgress = false;
                PestPrepSwapManager.isPrepSwapping = false;
                MacroStateManager.setCurrentState(MacroState.State.FARMING);
                ClientUtils.sendDebugMessage(client, "§6Failsafe: Warping to garden...");
                com.ihanuat.mod.util.CommandUtils.warpGarden(client);
                MacroWorkerThread.sleep(250);
                try { performSneakUnfly(client); } catch (InterruptedException ignored) {}
                client.execute(() -> {
                    GearManager.swapToFarmingTool(client);
                    com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
                });
            } finally {
                isFinishingInProgress = false;
            }
        });
    }

    public static void performUnfly(Minecraft client) throws InterruptedException {
        if (client.player == null)
            return;

        if (!isPlayerFlying(client)) {
            ClientUtils.sendDebugMessage(client, "Unfly skipped: player is not currently flying.");
            return;
        }

        if (MacroConfig.unflyMode == MacroConfig.UnflyMode.DOUBLE_TAP_SPACE) {
            isStoppingFlight = true;
            flightStopStage = 0;
            flightStopTicks = 0;

            long deadline = System.currentTimeMillis() + 3000;
            while (isStoppingFlight && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
        }
    }

    private static void performSneakUnfly(Minecraft client) throws InterruptedException {
        if (client.player == null)
            return;

        if (!isPlayerFlying(client)) {
            ClientUtils.sendDebugMessage(client, "Sneak unfly skipped: player is not currently flying.");
            return;
        }

        if (MacroConfig.unflyMode == MacroConfig.UnflyMode.SNEAK) {
            Thread.sleep(50);
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

    private static boolean isPlayerFlying(Minecraft client) {
        return client.player != null && client.player.getAbilities().flying;
    }

    private static void finalizeReturnToFarm(Minecraft client) {
        if (!com.ihanuat.mod.MacroStateManager.isMacroRunning())
            return;

        try {
            ClientUtils.sendDebugMessage(client, "Finalize: Starting return sequence.");

            int visitors = VisitorManager.getVisitorCount(client);
            ClientUtils.sendDebugMessage(client, "Finalize: Visitor count check: " + visitors);
            if (visitors >= MacroConfig.visitorThreshold
                    && !VisitorManager.isVisitorReentryCooldownActive(client, true)) {
                finalizeSwitchToVisitor(client);
                return;
            }

            if (visitors >= MacroConfig.visitorThreshold) {
                ClientUtils.sendDebugMessage(client,
                        "Finalize: Visitor threshold met, but cooldown is active. Continuing farming.");
            }

            // Swap to farming tool before restarting (this is the intended check point)
            ClientUtils.sendDebugMessage(client, "Finalize: Swapping to farming tool...");
            GearManager.swapToFarmingToolSync(client);
            ClientUtils.sendDebugMessage(client, "Finalize: Tool swap done.");

            // Original: also wait for gear/GUI stability if equipment swap is enabled.
            // Experimental: skip this — we already have the right gear from the clean sequence.
            // gear/gui wait skipped (experimental pest/gear merged native)

            ClientUtils.sendDebugMessage(client, "Pest cleaning sequence completed. Next state: FARMING");
            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
            PestPrepSwapManager.prepSwappedForCurrentPestCycle = false;
            PestManager.isCleaningInProgress = false;
            ClientUtils.sendDebugMessage(client, "Pest cleaning sequence finished. Restarting farming...");
            client.execute(() -> {
                ClientUtils.sendDebugMessage(client, "Starting farming script: " + MacroConfig.getFullRestartCommand());
                com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
            });
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Extracted visitor-switch logic for use in finalizeReturnToFarm.
     * Swaps to the visitor wardrobe (if configured), waits for stability,
     * then starts the visitor macro script.
     */
    private static void finalizeSwitchToVisitor(Minecraft client) throws InterruptedException {
        client.execute(() -> GearManager.swapToFarmingTool(client));

        if (MacroConfig.autoWardrobeVisitor && MacroConfig.wardrobeSlotVisitor > 0
                && WardrobeManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
            client.player.displayClientMessage(Component.literal(
                    "\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor + ")..."),
                    true);
            GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor);
            if (WardrobeManager.isSwappingWardrobe) {
                ClientUtils.sendDebugMessage(client, "Finalize (Visitor): Waiting for wardrobe GUI...");
                ClientUtils.waitForWardrobeGui(client);
                ClientUtils.sendDebugMessage(client,
                        "Finalize (Visitor): Wardrobe GUI cleared, waiting for swap completion...");
                while (WardrobeManager.isSwappingWardrobe)
                    Thread.sleep(50);
                while (WardrobeManager.wardrobeCleanupTicks > 0)
                    Thread.sleep(50);
                Thread.sleep(250);
            }
        }

        try {
            ClientUtils.sendDebugMessage(client, "Finalize (Visitor): Waiting for final stability...");
            while (WardrobeManager.isSwappingWardrobe)
                Thread.sleep(50);
            long guiStart = System.currentTimeMillis();
            while (client.screen != null && System.currentTimeMillis() - guiStart < 5000) {
                Thread.sleep(100);
            }
            Thread.sleep(250);
        } catch (InterruptedException ignored) {}

        ClientUtils.sendDebugMessage(client,
                "Wardrobe swap done, now triggering visitor macro. Next state: VISITING");
        ClientUtils.sendDebugMessage(client, "Stopping script: Returning to visitor macro");
        com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
        ClientUtils.sendDebugMessage(client, "Starting visitor macro script");
        com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:visitor", 0);
        PestManager.isCleaningInProgress = false;
    }
}
