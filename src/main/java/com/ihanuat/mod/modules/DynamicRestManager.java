package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.ReconnectScheduler;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Random;

/**
 * Implements the Dynamic Rest feature.
 *
 * Flow:
 * 1. While in FARMING state, the timer is shown in the action bar (countdown).
 * 2. When the timer expires, a staged shutdown begins (still gated on FARMING):
 * Stage 0 — send /setspawn, stop the macro script, force-release keys.
 * Stage 1 — disconnect from the server (intentional) and schedule reconnect
 * via ReconnectScheduler for the configured break duration.
 * 3. After the break the existing reconnect → recovery path runs normally,
 * which warps back to the Garden and restarts the script.
 */
public class DynamicRestManager {

    // ── State ────────────────────────────────────────────────────────────────

    /** Epoch-ms when the next rest should be triggered. 0 = not scheduled yet. */
    private static long nextRestTriggerMs = 0;

    private static boolean restSequencePending = false;
    private static int restSequenceStage = 0;
    private static long nextStageActionTime = 0;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Called when the macro starts (or after a recovery reconnect).
     * Schedules the next rest timer using the configured scripting time ± offset.
     */
    public static void scheduleNextRest() {
        int base = MacroConfig.restScriptingTime;
        int offset = MacroConfig.restScriptingTimeOffset;
        int randomOffset = (offset > 0) ? (new Random().nextInt(offset * 2 + 1) - offset) : 0;
        nextRestTriggerMs = System.currentTimeMillis() + ((base + randomOffset) * 60L * 1000L);
        restSequencePending = false;
        restSequenceStage = 0;
        nextStageActionTime = 0;
    }

    /**
     * Clears the rest timer entirely (called when the macro is stopped manually).
     */
    public static void reset() {
        nextRestTriggerMs = 0;
        restSequencePending = false;
        restSequenceStage = 0;
        nextStageActionTime = 0;
    }

    /** Returns true while a rest sequence is actively in progress. */
    public static boolean isRestPending() {
        return restSequencePending;
    }

    /** Returns the scheduled rest trigger time (epoch ms), or 0 if not set. */
    public static long getNextRestTriggerMs() {
        return nextRestTriggerMs;
    }

    // ── Tick update ──────────────────────────────────────────────────────────

    /**
     * Must be called every client END_CLIENT_TICK while player != null.
     * Handles both the countdown HUD and the shutdown sequence.
     */
    public static void update(Minecraft client) {
        if (client.player == null)
            return;

        // === Countdown / timer display (only while actively running) ===
        MacroState.State currentState = MacroStateManager.getCurrentState();
        if (currentState != MacroState.State.OFF && currentState != MacroState.State.RECOVERING
                && nextRestTriggerMs > 0 && !restSequencePending) {
            long remaining = nextRestTriggerMs - System.currentTimeMillis();

            if (remaining > 0) {
                // Show countdown in the action-bar
                long totalSecs = remaining / 1000;
                long mins = totalSecs / 60;
                long secs = totalSecs % 60;

                long sessionTotalSecs = MacroStateManager.getSessionStartTime() == 0 ? 0
                        : (System.currentTimeMillis() - MacroStateManager.getSessionStartTime()) / 1000;
                long sHours = sessionTotalSecs / 3600;
                long sMins = (sessionTotalSecs % 3600) / 60;
                long sSecs = sessionTotalSecs % 60;

                String sessionStr = sHours > 0
                        ? String.format("%02d:%02d:%02d", sHours, sMins, sSecs)
                        : String.format("%02d:%02d", sMins, sSecs);

                String stateDesc = currentState == MacroState.State.CLEANING ? "Cleaning..." : "Farming...";
                String hudText = String.format(
                        "§l§b[Ihanuat] §7%s §8| §dSession: %s §8| §eNext rest: §a%02d:%02d", stateDesc, sessionStr,
                        mins, secs);
                client.player.displayClientMessage(Component.literal(hudText), true);
            } else {
                // Timer expired — kick off the rest sequence
                restSequencePending = true;
                restSequenceStage = 0;
                nextStageActionTime = System.currentTimeMillis();
                client.player.displayClientMessage(
                        Component.literal("§6[Ihanuat] Dynamic Rest triggered! Starting shutdown sequence..."),
                        false);
            }
        }

        // === Shutdown sequence — runs to completion regardless of current state ===
        if (!restSequencePending)
            return;

        if (System.currentTimeMillis() < nextStageActionTime)
            return;

        switch (restSequenceStage) {
            case 0: {
                // Stop the farming script, release all keys, /setspawn
                ClientUtils.sendCommand(client, ".ez-stopscript");
                ClientUtils.forceReleaseKeys(client);
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Dynamic Rest: running /setspawn..."), false);
                client.player.connection.sendChat("/setspawn");
                MacroStateManager.setCurrentState(MacroState.State.OFF);

                restSequenceStage = 1;
                nextStageActionTime = System.currentTimeMillis() + 3000; // 3 s for /setspawn to register
                break;
            }
            case 1: {
                // Disconnect and schedule the reconnect after the break duration
                int base = MacroConfig.restBreakTime;
                int offset = MacroConfig.restBreakTimeOffset;
                int randomOffset = (offset > 0) ? (new Random().nextInt(offset * 2 + 1) - offset) : 0;
                long breakSeconds = (base + randomOffset) * 60L;

                client.player.displayClientMessage(
                        Component.literal(String.format(
                                "§c[Ihanuat] Dynamic Rest: disconnecting. Reconnecting in %d minutes...",
                                breakSeconds / 60)),
                        false);

                // Mark as intentional so the disconnect mixin does not trigger an
                // unexpected-kick reconnect on top of ours.
                MacroStateManager.setIntentionalDisconnect(true);

                // Schedule reconnect (shouldResume = true → recovery will fire on rejoin)
                ReconnectScheduler.scheduleReconnect(breakSeconds, true);

                // Actually disconnect — use Minecraft#disconnect which cleanly
                // tears down the connection and returns to the title screen.
                client.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);

                restSequenceStage = 2;
                nextStageActionTime = Long.MAX_VALUE; // no further stages needed
                break;
            }
            default:
                break;
        }
    }
}
