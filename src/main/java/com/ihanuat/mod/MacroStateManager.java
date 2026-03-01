package com.ihanuat.mod;

import net.minecraft.client.Minecraft;
import com.ihanuat.mod.util.ClientUtils;

public class MacroStateManager {
    private static volatile MacroState.State currentState = MacroState.State.OFF;
    private static volatile boolean intentionalDisconnect = false;
    private static volatile long sessionStartTime = 0;

    public static long getSessionStartTime() {
        return sessionStartTime;
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
            sessionStartTime = System.currentTimeMillis();
        }
        currentState = state;
    }

    public static void stopMacro(Minecraft client) {
        currentState = MacroState.State.OFF;
        ClientUtils.forceReleaseKeys(client);
        ClientUtils.sendCommand(client, ".ez-stopscript");
        com.ihanuat.mod.modules.PestManager.reset();
        com.ihanuat.mod.modules.GearManager.reset();
        com.ihanuat.mod.modules.GeorgeManager.reset();
        com.ihanuat.mod.modules.BookCombineManager.reset();
        com.ihanuat.mod.modules.RecoveryManager.reset();
    }
}
