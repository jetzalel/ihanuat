package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.modules.GearManager;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class PestCleaningSequencer {
    private static final long SETSPAWN_TO_WARDROBE_COOLDOWN_MS = 1000L;
    private static final long CLEANING_START_BUSY_WAIT_MS = 10000L;
    private static final Object DEFERRED_START_LOCK = new Object();

    private static volatile boolean cleaningStartDeferred = false;
    private static volatile String deferredPlot = null;
    private static volatile String deferredInfestedPlot = null;
    private static volatile int deferredSessionId = 0;

    public static void startCleaningSequence(Minecraft client, String plot, String currentInfestedPlot,
            int currentPestSessionId) {

        if (PestManager.isCleaningInProgress)
            return;

        if (isCleaningStartBlockedByGearSwap()) {
            deferCleaningSequenceStart(client, plot, currentInfestedPlot, currentPestSessionId);
            return;
        }

        submitCleaningSequence(client, plot, currentInfestedPlot, currentPestSessionId);
    }

    private static boolean isCleaningStartBlockedByGearSwap() {
        return WardrobeManager.isSwappingWardrobe
                || EquipmentManager.isSwappingEquipment
                || PestPrepSwapManager.isPrepSwapping;
    }

    private static void deferCleaningSequenceStart(Minecraft client, String plot, String currentInfestedPlot,
            int currentPestSessionId) {
        boolean shouldQueueWaitTask = false;
        synchronized (DEFERRED_START_LOCK) {
            deferredPlot = plot;
            deferredInfestedPlot = currentInfestedPlot;
            deferredSessionId = currentPestSessionId;
            if (!cleaningStartDeferred) {
                cleaningStartDeferred = true;
                shouldQueueWaitTask = true;
            }
        }

        if (!shouldQueueWaitTask) {
            ClientUtils.sendDebugMessage(client,
                    "Cleaning start still blocked by gear swap; updated deferred request for plot " + currentInfestedPlot);
            return;
        }

        ClientUtils.sendDebugMessage(client,
                "Cleaning start blocked by gear swap; deferring pest cleaning for plot " + currentInfestedPlot);
        MacroWorkerThread.getInstance().submit("CleaningSequence-WaitForGear-" + plot, () -> {
            long waitStart = System.currentTimeMillis();
            try {
                while (isCleaningStartBlockedByGearSwap()) {
                    if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING))
                        return;
                    if (System.currentTimeMillis() - waitStart > CLEANING_START_BUSY_WAIT_MS) {
                        ClientUtils.sendDebugMessage(client,
                                "Cleaning start timed out waiting for gear swap to finish.");
                        return;
                    }
                    MacroWorkerThread.sleep(50);
                }
            } finally {
                synchronized (DEFERRED_START_LOCK) {
                    cleaningStartDeferred = false;
                }
            }

            if (PestManager.isCleaningInProgress || MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING))
                return;

            String latestPlot;
            String latestInfestedPlot;
            int latestSessionId;
            synchronized (DEFERRED_START_LOCK) {
                latestPlot = deferredPlot;
                latestInfestedPlot = deferredInfestedPlot;
                latestSessionId = deferredSessionId;
                deferredPlot = null;
                deferredInfestedPlot = null;
            }

            if (latestSessionId != PestManager.currentPestSessionId)
                return;

            startCleaningSequence(client, latestPlot, latestInfestedPlot, latestSessionId);
        });
    }

    private static void submitCleaningSequence(Minecraft client, String plot, String currentInfestedPlot,
            int currentPestSessionId) {
        ClientUtils.sendDebugMessage(client,
                "Stopping script: Pest threshold reached, starting cleaning sequence for plot " + plot);
        com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
        PestManager.isCleaningInProgress = true;
        WardrobeManager.shouldRestartFarmingAfterSwap = false;
        com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.CLEANING);
        final int sessionId = currentPestSessionId;
        final String currentPlot = ClientUtils.getCurrentPlot(client);

        MacroWorkerThread.getInstance().submit("CleaningSequence-" + plot, () -> {
            try {
                // Set spawn with 10s timeout (increased in CommandUtils)
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                if (!com.ihanuat.mod.util.CommandUtils.setSpawn(client)) {
                    client.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "§c[Ihanuat] /setspawn timed out — aborting pest cleaning to prevent roof spawn."),
                            false);
                    PestManager.isCleaningInProgress = false;
                    com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
                    return;
                }
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                if (sessionId != PestManager.currentPestSessionId)
                    return;
                if (MacroConfig.autoWardrobePest) {
                    ClientUtils.sendDebugMessage(client,
                            "Cooling down 1s after /setspawn before any wardrobe interaction.");
                    MacroWorkerThread.sleep(SETSPAWN_TO_WARDROBE_COOLDOWN_MS);
                }
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;

                boolean isSamePlot = currentInfestedPlot != null && currentInfestedPlot.equals(currentPlot);
                boolean shouldDoAotv = PestAotvManager.shouldDoAotvOnCurrentPlot(client, currentInfestedPlot,
                        isSamePlot);

                // restoreGearForCleaning restores farming wardrobe/equipment BEFORE movement.
                if (!restoreGearForCleaning(client, shouldDoAotv))
                    return;

                PestPrepSwapManager.prepSwappedForCurrentPestCycle = false;
                client.player.displayClientMessage(
                        Component.literal("§6Starting Pest Cleaner script (" + currentInfestedPlot + ")..."), true);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;

                ClientUtils.sendDebugMessage(client, "Bonus inactive flag: " + PestBonusManager.isBonusInactive);

                if (PestBonusManager.isBonusInactive) {
                    client.player.displayClientMessage(
                            Component.literal("§dBonus is INACTIVE! Triggering Phillip reactivation..."), true);
                    PestBonusManager.runBonusReactivationSequence(client);
                    if (MacroWorkerThread.shouldAbortTask(client))
                        return;
                    if (PestBonusManager.isBonusInactive) {
                        ClientUtils.sendDebugMessage(client,
                                "Bonus still INACTIVE after Phillip wait — continuing sequence anyway.");
                    }

                    if (MacroConfig.autoRodPestSpawn) {
                        ClientUtils.sendDebugMessage(client, "Auto Rod: Triggering rod cast on pest spawn (Bonus inactive).");
                        RodManager.executeRodSequence(client);
                        // Swap to farming tool after rod usage.
                        GearManager.swapToFarmingTool(client);
                    }
                    com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 0);
                    return;
                }

                if (shouldDoAotv) {
                    // AOTV to roof handles movement — skip /tptoplot
                    PestAotvManager.performAotvToRoof(client);
                } else {
                    // AOTV off: always /tptoplot, even on same plot
                    warpToInfestedPlotIfNeeded(client, currentInfestedPlot, false);
                }

                startPestCleanerScript(client, currentInfestedPlot);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Restores farming wardrobe and equipment before starting the pest cleaner.
     *
     * @param aotvPath  true when the sequence will AOTV to the roof.  In that
     *                  case wardrobeAotvDelay is used instead of wardrobePostSwapDelay,
     *                  allowing independent tuning of the AOTV launch cadence.
     */
    private static boolean restoreGearForCleaning(Minecraft client, boolean aotvPath) throws InterruptedException {
        if (MacroConfig.autoWardrobePest) {
            int targetSlot = MacroConfig.wardrobeSlotFarming;
            if ((PestPrepSwapManager.prepSwappedForCurrentPestCycle
                    || WardrobeManager.trackedWardrobeSlot != targetSlot)
                    && targetSlot > 0) {
                client.player.displayClientMessage(
                        Component.literal("§eRestoring Farming Wardrobe (Slot " + targetSlot + ") for Vacuuming..."),
                        true);
                client.execute(() -> GearManager.ensureWardrobeSlot(client, targetSlot));

                long wardrobeStartWait = System.currentTimeMillis();
                while (!WardrobeManager.isSwappingWardrobe && System.currentTimeMillis() - wardrobeStartWait < 2000) {
                    if (MacroWorkerThread.shouldAbortTask(client))
                        return false;
                    MacroWorkerThread.sleep(25);
                }

                ClientUtils.waitForWardrobeGui(client);
                long wardrobeFinishWait = System.currentTimeMillis();
                while (WardrobeManager.isSwappingWardrobe && System.currentTimeMillis() - wardrobeFinishWait < 7000)
                    MacroWorkerThread.sleep(50);

                if (WardrobeManager.isSwappingWardrobe) {
                    ClientUtils.sendDebugMessage(client,
                            "§eWardrobe swap wait timeout in cleaning sequence. Triggering failsafe completion.");
                    WardrobeManager.forceWardrobeCompletionFailsafe(client);
                }

                while (WardrobeManager.wardrobeCleanupTicks > 0)
                    MacroWorkerThread.sleep(50);

                // AOTV path: use the user-configured wardrobeAotvDelay.
                // Non-AOTV path: use the user-configured wardrobePostSwapDelay.
                int postSwapWait = aotvPath ? MacroConfig.getRandomizedDelay(MacroConfig.wardrobeAotvDelay) 
                                            : MacroConfig.getRandomizedDelay(MacroConfig.wardrobePostSwapDelay);
                MacroWorkerThread.sleep(postSwapWait);

                if (MacroWorkerThread.shouldAbortTask(client))
                    return false;
            }
        }

        if (MacroConfig.autoEquipment) {
            GearManager.ensureEquipment(client, true);

            long equipmentStartWait = System.currentTimeMillis();
            while (!EquipmentManager.isSwappingEquipment && System.currentTimeMillis() - equipmentStartWait < 2000) {
                if (MacroWorkerThread.shouldAbortTask(client))
                    return false;
                MacroWorkerThread.sleep(25);
            }

            ClientUtils.waitForEquipmentGui(client);
            long equipmentFinishWait = System.currentTimeMillis();
            while (EquipmentManager.isSwappingEquipment && System.currentTimeMillis() - equipmentFinishWait < 7000)
                MacroWorkerThread.sleep(50);

            if (EquipmentManager.isSwappingEquipment) {
                ClientUtils.sendDebugMessage(client,
                        "§eEquipment swap wait timeout in cleaning sequence. Resetting equipment state.");
                EquipmentManager.resetState();
            }

            MacroWorkerThread.sleep(250);
            if (MacroWorkerThread.shouldAbortTask(client))
                return false;
        }
        return true;
    }

    private static boolean warpToInfestedPlotIfNeeded(Minecraft client, String currentInfestedPlot, boolean isSamePlot)
            throws InterruptedException {
        if (isSamePlot || currentInfestedPlot == null || currentInfestedPlot.equals("0"))
            return true;

        if (com.ihanuat.mod.util.CommandUtils.plotTp(client, currentInfestedPlot)) {
            Thread.sleep(250);
            return !MacroWorkerThread.shouldAbortTask(client);
        }

        client.player.displayClientMessage(Component.literal("§cFailed to warp to plot " + currentInfestedPlot + "!"),
                true);
        return false;
    }

    private static void startPestCleanerScript(Minecraft client, String currentInfestedPlot) {
        ClientUtils.sendDebugMessage(client, "Ready to start pest cleaner");
        com.ihanuat.mod.util.CommandUtils.stopScript(client, 50);

        ClientUtils.sendDebugMessage(client, "Starting pest cleaner script for plot " + currentInfestedPlot);
        if (MacroConfig.autoRodPestSpawn) {
            ClientUtils.sendDebugMessage(client, "Auto Rod: Triggering rod cast on pest spawn.");
            RodManager.executeRodSequence(client);
        }

        // Swap to farming tool before starting pest cleaner script
        GearManager.swapToFarmingTool(client);

        com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 0);
    }
}
