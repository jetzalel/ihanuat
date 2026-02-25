package com.ihanuat.mod;

public class MacroState {
    public enum State {
        OFF,
        FARMING,
        CLEANING,
        RECOVERING
    }

    public enum ReturnState {
        OFF,
        TP_PRE_WAIT,
        TP_START,
        TP_WAIT,
        FLIGHT_START,
        ALIGN_WAIT,
        FLY_HIGH,
        FLY_APPROACH,
        LANDING_SHIFT,
        LANDING_WAIT
    }

    public enum Location {
        GARDEN, HUB, LOBBY, LIMBO, UNKNOWN
    }
}
