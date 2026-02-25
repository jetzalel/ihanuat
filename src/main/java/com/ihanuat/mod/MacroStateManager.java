package com.ihanuat.mod;

import net.minecraft.client.Minecraft;
import com.ihanuat.mod.util.ClientUtils;

public class MacroStateManager {
    private static volatile MacroState.State currentState = MacroState.State.OFF;
    private static volatile MacroState.ReturnState returnState = MacroState.ReturnState.OFF;
    private static volatile boolean intentionalDisconnect = false;

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
        currentState = state;
    }

    public static MacroState.ReturnState getReturnState() {
        return returnState;
    }

    public static void setReturnState(MacroState.ReturnState state) {
        returnState = state;
    }

    public static void stopMacro(Minecraft client) {
        currentState = MacroState.State.OFF;
        returnState = MacroState.ReturnState.OFF;
        ClientUtils.forceReleaseKeys(client);
        ClientUtils.sendCommand(client, ".ez-stopscript");
    }
}
