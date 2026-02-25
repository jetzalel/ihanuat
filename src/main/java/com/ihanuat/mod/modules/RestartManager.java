package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class RestartManager {
    private static boolean isRestartPending = false;
    private static long restartExecutionTime = 0;
    private static int restartSequenceStage = 0;
    private static long nextRestartActionTime = 0;

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
        if (!isRestartPending || MacroStateManager.getCurrentState() != MacroState.State.FARMING)
            return;

        if (restartSequenceStage == 0 && System.currentTimeMillis() >= restartExecutionTime) {
            client.player.displayClientMessage(
                    Component.literal("§c[Ihanuat] Executing delayed restart abort sequence..."), false);
            ClientUtils.sendCommand(client, ".ez-stopscript");
            ClientUtils.forceReleaseKeys(client);
            client.player.connection.sendChat("/setspawn");
            restartSequenceStage = 1;
            nextRestartActionTime = System.currentTimeMillis() + 5000;
        } else if (restartSequenceStage == 1 && System.currentTimeMillis() >= nextRestartActionTime) {
            client.player.connection.sendChat("/hub");
            restartSequenceStage = 2;
            nextRestartActionTime = System.currentTimeMillis() + 10000;
        } else if (restartSequenceStage == 2 && System.currentTimeMillis() >= nextRestartActionTime) {
            ClientUtils.sendCommand(client, ".ez-stopscript");
            MacroStateManager.setCurrentState(MacroState.State.RECOVERING);
            restartSequenceStage = 0;
            isRestartPending = true; // Still pending until recovery starts? Actually, let's reset it here.
            isRestartPending = false;
        }
    }

    public static boolean isRestartPending() {
        return isRestartPending;
    }
}
