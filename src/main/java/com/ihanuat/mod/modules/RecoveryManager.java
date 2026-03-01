package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class RecoveryManager {
    private static int recoveryFailedAttempts = 0;
    private static long lastRecoveryActionTime = 0;
    private static MacroState.Location lastRecoveryLocation = MacroState.Location.UNKNOWN;

    public static void reset() {
        recoveryFailedAttempts = 0;
        lastRecoveryActionTime = 0;
        lastRecoveryLocation = MacroState.Location.UNKNOWN;
    }

    public static void update(Minecraft client) {
        if (MacroStateManager.getCurrentState() != MacroState.State.RECOVERING)
            return;

        if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen)
            return;

        if (System.currentTimeMillis() - lastRecoveryActionTime < 5000)
            return;

        MacroState.Location currentLoc = ClientUtils.getCurrentLocation(client);

        if (currentLoc != lastRecoveryLocation) {
            recoveryFailedAttempts = 0;
            lastRecoveryLocation = currentLoc;
        }

        if (recoveryFailedAttempts >= 15) {
            client.player.displayClientMessage(
                    Component.literal("\u00A7c[Ihanuat] Auto-Recovery failed after 15 attempts. Stopping macro."),
                    false);
            MacroStateManager.stopMacro(client);
            return;
        }

        lastRecoveryActionTime = System.currentTimeMillis();
        recoveryFailedAttempts++;

        switch (currentLoc) {
            case LIMBO:
                client.player.displayClientMessage(Component.literal("\u00A7e[Ihanuat] Recovery (Attempt "
                        + recoveryFailedAttempts + "): Warping to Lobby from Limbo..."), false);
                client.player.connection.sendChat("/lobby");
                break;
            case LOBBY:
                client.player.displayClientMessage(Component.literal("\u00A7e[Ihanuat] Recovery (Attempt "
                        + recoveryFailedAttempts + "): Warping to SkyBlock from Lobby..."), false);
                client.player.connection.sendChat("/skyblock");
                break;
            case HUB:
            case UNKNOWN:
                client.player.displayClientMessage(Component.literal(
                        "\u00A7e[Ihanuat] Recovery (Attempt " + recoveryFailedAttempts + "): Warping to Garden..."),
                        false);
                client.player.connection.sendChat("/warp garden");
                break;
            case GARDEN:
                client.player.displayClientMessage(
                        Component.literal("\u00A7a[Ihanuat] Recovery Successful! Resuming Farming..."), false);
                recoveryFailedAttempts = 0;
                MacroStateManager.setCurrentState(MacroState.State.FARMING);
                GearManager.swapToFarmingTool(client);
                ClientUtils.sendCommand(client, MacroConfig.restartScript);
                break;
        }
    }
}
