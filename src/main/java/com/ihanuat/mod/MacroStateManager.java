package com.ihanuat.mod;

import net.minecraft.client.Minecraft;
import com.ihanuat.mod.util.ClientUtils;

public class MacroStateManager {
    private static volatile MacroState.State currentState = MacroState.State.OFF;
    private static volatile boolean intentionalDisconnect = false;
    private static volatile long sessionAccumulated = 0;
    private static volatile long lifetimeAccumulated = 0;
    private static volatile long lastSessionStartTime = 0;
    private static long lastPeriodicSaveTime = 0;

    public static void syncFromConfig() {
        lifetimeAccumulated = MacroConfig.lifetimeAccumulated;
    }

    public static void periodicUpdate() {
        if (!isMacroRunning())
            return;

        long now = System.currentTimeMillis();
        if (now - lastPeriodicSaveTime > 60000) { // 1 minute
            lastPeriodicSaveTime = now;
            long diff = now - lastSessionStartTime;

            if (MacroConfig.persistSessionTimer) {
                // Keep session timer as is for pause/unpause if enabled
            } else {
                // Not actually hit here since we're periodic other than if someone pauses?
                // Wait, sessionAccumulated is only saved to disk if we want it to survive
                // RESTART
            }
            MacroConfig.lifetimeAccumulated = lifetimeAccumulated + diff;
            MacroConfig.save();
        }
    }

    public static long getSessionRunningTime() {
        if (currentState != MacroState.State.OFF && currentState != MacroState.State.RECOVERING
                && lastSessionStartTime != 0) {
            return sessionAccumulated + (System.currentTimeMillis() - lastSessionStartTime);
        }
        return sessionAccumulated;
    }

    public static long getLifetimeRunningTime() {
        if (currentState != MacroState.State.OFF && currentState != MacroState.State.RECOVERING
                && lastSessionStartTime != 0) {
            return lifetimeAccumulated + (System.currentTimeMillis() - lastSessionStartTime);
        }
        return lifetimeAccumulated;
    }

    public static boolean isMacroRunning() {
        return currentState != MacroState.State.OFF;
    }

    public static boolean isIntentionalDisconnect() {
        return intentionalDisconnect;
    }

    public static void setIntentionalDisconnect(boolean intentional) {
        intentionalDisconnect = intentional;
    }

    public static MacroState.State getCurrentState() {
        return currentState;
    }

    public static void setCurrentState(MacroState.State state) {
        if (currentState == MacroState.State.OFF && state != MacroState.State.OFF
                && state != MacroState.State.RECOVERING) {
            lastSessionStartTime = System.currentTimeMillis();
            if (!com.ihanuat.mod.MacroConfig.persistSessionTimer) {
                sessionAccumulated = 0;
                com.ihanuat.mod.modules.ProfitManager.reset();
            }
            lastPeriodicSaveTime = System.currentTimeMillis();
        } else if (currentState != MacroState.State.OFF && currentState != MacroState.State.RECOVERING
                && (state == MacroState.State.OFF || state == MacroState.State.RECOVERING)) {
            if (lastSessionStartTime != 0) {
                long diff = System.currentTimeMillis() - lastSessionStartTime;
                sessionAccumulated += diff;
                lifetimeAccumulated += diff;
                lastSessionStartTime = 0;

                // Persist stats
                MacroConfig.lifetimeAccumulated = lifetimeAccumulated;
                MacroConfig.save();
            }
        }
        currentState = state;
    }

    public static void stopMacro(Minecraft client) {
        setCurrentState(MacroState.State.OFF);
        ClientUtils.forceReleaseKeys(client);
        ClientUtils.sendCommand(client, ".ez-stopscript");
        com.ihanuat.mod.modules.PestManager.reset();
        com.ihanuat.mod.modules.GearManager.reset();
        com.ihanuat.mod.modules.GeorgeManager.reset();
        com.ihanuat.mod.modules.BookCombineManager.reset();
        com.ihanuat.mod.modules.RecoveryManager.reset();
        if (!MacroConfig.persistSessionTimer) {
            com.ihanuat.mod.modules.DynamicRestManager.reset();
            com.ihanuat.mod.modules.ProfitManager.reset();
        }
    }
}
