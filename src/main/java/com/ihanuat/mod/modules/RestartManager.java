package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class RestartManager {
    private static boolean isRestartPending = false;
    private static long restartExecutionTime = 0;
    private static int restartSequenceStage = 0;
    private static long nextRestartActionTime = 0;

    private static boolean isSafeToRunRestartAbort(MacroState.State state) {
        if (state == MacroState.State.OFF || state == MacroState.State.RECOVERING) {
            return false;
        }

        if (PestManager.isCleaningInProgress
                || PestPrepSwapManager.isPrepSwapping
                || PestReturnManager.isFinishingInProgress
                || PestReturnManager.isReturnToLocationActive
                || PestReturnManager.isReturningFromPestVisitor
                || state == MacroState.State.CLEANING
                || state == MacroState.State.SPRAYING
                || state == MacroState.State.VISITING) {
            return false;
        }

        return state == MacroState.State.FARMING;
    }

    public static void handleRestartMessage(Minecraft client) {
        if (MacroStateManager.getCurrentState() != MacroState.State.OFF
                && MacroStateManager.getCurrentState() != MacroState.State.RECOVERING && !isRestartPending) {
            long contestMs = ClientUtils.getContestRemainingMs(client);
            if (contestMs > 0) {
                client.player.displayClientMessage(
                        Component.literal(
                                "§c[Ihanuat] Server restart detected! Delaying abort until Jacob's contest ends..."),
                        false);
                restartExecutionTime = System.currentTimeMillis() + contestMs + 10000;
            } else {
                client.player.displayClientMessage(Component.literal(
                        "§c[Ihanuat] Server restart/evacuation detected! Initiating abort sequence..."), false);
                restartExecutionTime = System.currentTimeMillis();
            }
            isRestartPending = true;
            restartSequenceStage = 0;
        }
    }

    public static void update(Minecraft client) {
        if (!isRestartPending)
            return;

        if (client.player == null)
            return;

        MacroState.State state = MacroStateManager.getCurrentState();

        // Stage 0: Wait until safe, then stop script and set spawn
        if (restartSequenceStage == 0 && System.currentTimeMillis() >= restartExecutionTime) {
            if (!isSafeToRunRestartAbort(state)) {
                return;
            }

            client.player.displayClientMessage(
                    Component.literal("§c[Ihanuat] Evacuation: stopping script and setting spawn..."), false);
            ClientUtils.sendDebugMessage(client, "Stopping script: Server restart/evacuation detected");
            MacroWorkerThread.getInstance().cancelCurrent();
            com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
            ClientUtils.forceReleaseKeys(client);
            com.ihanuat.mod.util.CommandUtils.initiateSetSpawn(client);
            restartSequenceStage = 1;
            nextRestartActionTime = System.currentTimeMillis() + 3000;
        }

        // Stage 1: Wait for spawn to be set, then warp to island
        else if (restartSequenceStage == 1) {
            if (com.ihanuat.mod.util.CommandUtils.hasSpawnBeenSet()
                    || System.currentTimeMillis() >= nextRestartActionTime) {
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Evacuation: warping to island..."), false);
                client.execute(() -> ClientUtils.sendCommand(client, "is"));
                restartSequenceStage = 2;
                nextRestartActionTime = System.currentTimeMillis() + 3000;
            }
            else {
                com.ihanuat.mod.util.CommandUtils.initiateSetSpawn(client);
                nextRestartActionTime = System.currentTimeMillis() + 3000;
            }
        }

        // Stage 2: Wait to arrive on island, then warp back to garden
        else if (restartSequenceStage == 2) {
            if (client.player == null) return;
            if (System.currentTimeMillis() >= nextRestartActionTime) {
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Evacuation: returning to garden..."), false);
                client.execute(() -> ClientUtils.sendCommand(client, "warp garden"));
                restartSequenceStage = 3;
                nextRestartActionTime = System.currentTimeMillis() + 3000;
            }
        }

        // Stage 3: Wait to arrive in garden, then resume
        else if (restartSequenceStage == 3) {
            if (client.player == null) return;
            if (System.currentTimeMillis() >= nextRestartActionTime) {
                client.player.displayClientMessage(
                        Component.literal("§a[Ihanuat] Evacuation complete! Back in garden, resuming..."), false);
                isRestartPending = false;
                restartSequenceStage = 0;
                com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
            }
        }
    }
}