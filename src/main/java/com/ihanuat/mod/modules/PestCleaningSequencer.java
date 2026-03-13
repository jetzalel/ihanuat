package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class PestCleaningSequencer {

    public static void startCleaningSequence(Minecraft client, String plot, String currentInfestedPlot,
            int currentPestSessionId) {
        if (PestManager.isCleaningInProgress || WardrobeManager.isSwappingWardrobe
                || EquipmentManager.isSwappingEquipment)
            return;

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

                if (!restoreGearForCleaning(client))
                    return;

                PestPrepSwapManager.prepSwappedForCurrentPestCycle = false;
                client.player.displayClientMessage(
                        Component.literal("§6Starting Pest Cleaner script (" + currentInfestedPlot + ")..."), true);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;

                if (PestBonusManager.isBonusInactive) {
                    client.player.displayClientMessage(
                            Component.literal("§dBonus is INACTIVE! Triggering Phillip reactivation..."), true);
                    PestBonusManager.isReactivatingBonus = true;

                    if (MacroConfig.autoRodPestSpawn) {
                        ClientUtils.sendDebugMessage(client, "Auto Rod: Triggering rod cast on pest spawn (Bonus inactive).");
                        RodManager.executeRodSequence(client);
                    }

                    com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 0);
                    return;
                }

                boolean isSamePlot = currentInfestedPlot != null && currentInfestedPlot.equals(currentPlot);
                boolean shouldDoAotv = PestAotvManager.shouldDoAotvOnCurrentPlot(client, currentInfestedPlot,
                        isSamePlot);

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

    private static boolean restoreGearForCleaning(Minecraft client) throws InterruptedException {
        if (MacroConfig.autoWardrobePest) {
            int targetSlot = MacroConfig.wardrobeSlotFarming;
            if ((PestPrepSwapManager.prepSwappedForCurrentPestCycle
                    || WardrobeManager.trackedWardrobeSlot != targetSlot)
                    && targetSlot > 0) {
                client.player.displayClientMessage(
                        Component.literal("§eRestoring Farming Wardrobe (Slot " + targetSlot + ") for Vacuuming..."),
                        true);
                client.execute(() -> GearManager.ensureWardrobeSlot(client, targetSlot));

                // client.execute is async; wait for swap state to actually start so later waits
                // are not skipped.
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
                MacroWorkerThread.sleep(250);
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
        com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 0);
    }
}
