package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class PestManager {
    // Shared state
    public static volatile boolean isCleaningInProgress = false;
    public static volatile String currentInfestedPlot = null;
    public static volatile int currentPestSessionId = 0;
    private static final long PEST_REENTRY_COOLDOWN_MS = 30_000;
    private static long lastZeroPestTime = 0;
    private static volatile int predictedAliveCount = 0;
    private static volatile long lastChatSpawnUpdateMs = 0;
    private static volatile long pestReentryCooldownUntilMs = 0;
    private static final long TAB_SYNC_GRACE_MS = 5000;

    private static boolean isThresholdMet(int aliveCount) {
        return aliveCount >= MacroConfig.pestThreshold || aliveCount >= 8;
    }

    private static boolean isPestReentryCooldownActive() {
        return getPestReentryCooldownRemainingMs() > 0;
    }

    private static long getPestReentryCooldownRemainingMs() {
        return Math.max(0L, pestReentryCooldownUntilMs - System.currentTimeMillis());
    }

    private static void startPestReentryCooldown() {
        pestReentryCooldownUntilMs = System.currentTimeMillis() + PEST_REENTRY_COOLDOWN_MS;
    }

    public static void reset() {
        isCleaningInProgress = false;
        currentInfestedPlot = null;
        lastZeroPestTime = 0;
        predictedAliveCount = 0;
        lastChatSpawnUpdateMs = 0;
        pestReentryCooldownUntilMs = 0;
        currentPestSessionId++;
        
        PestPrepSwapManager.resetState();
        PestReturnManager.resetState();
        PestAotvManager.resetState();
        PestBonusManager.resetState();
    }

    public static void checkTabListForPests(Minecraft client, MacroState.State currentState) {
        if (client.getConnection() == null || client.player == null || !MacroStateManager.isMacroRunning())
            return;

        if (isCleaningInProgress && currentState == MacroState.State.FARMING) {
            isCleaningInProgress = false;
        }

        PestTabListParser.TabListData data = PestTabListParser.parseTabList(client);
        syncPredictedAliveFromTab(data.aliveCount);
        int effectiveAlive = getEffectiveAliveCount(data.aliveCount);
        
        // Update bonus status
        PestBonusManager.isBonusInactive = data.bonusFound;

        // Handle prep swap flag updates based on cooldown
        if (data.cooldownSeconds != -1) {
            PestPrepSwapManager.updatePrepSwapFlag(data.cooldownSeconds, isCleaningInProgress);

            // Check if prep swap should be triggered
            boolean thresholdMet = isThresholdMet(effectiveAlive);
            if (!thresholdMet && PestPrepSwapManager.shouldTriggerPrepSwap(
                    currentState, data.cooldownSeconds, isCleaningInProgress, PestReturnManager.isReturnToLocationActive)) {
                PestPrepSwapManager.triggerPrepSwap(client);
            }
        }

        // Failsafe: if CLEANING and 0 pests for 10s, return to farming.
        // Do not apply this during SPRAYING because spray routes can legitimately
        // travel multiple plots with 0 alive pests between spray actions.
        if (currentState == MacroState.State.CLEANING) {
            if (effectiveAlive <= 0) {
                if (lastZeroPestTime == 0) {
                    lastZeroPestTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - lastZeroPestTime > 10000) {
                    if (client.player != null) {
                        client.player.displayClientMessage(
                                Component.literal("§cFail-safe: No pests detected for 10s. Returning to farm."), true);
                    }
                    lastZeroPestTime = 0;
                    handlePestCleaningFinished(client);
                    return;
                }
            } else {
                lastZeroPestTime = 0;
            }
        } else {
            lastZeroPestTime = 0;
        }

        if (isCleaningInProgress)
            return;

        // Check if cleaning should be triggered
        if (isThresholdMet(effectiveAlive)) {
            if (isPestReentryCooldownActive()) {
                return;
            }
            if (effectiveAlive >= 8 && effectiveAlive < 99) {
                client.player.displayClientMessage(Component.literal("§eMax Pests (8) reached. Starting cleaning..."),
                        true);
            }
            String targetPlot = data.infestedPlots.isEmpty() ? "0" : data.infestedPlots.iterator().next();
            startCleaningSequence(client, targetPlot);
        }
    }

    public static boolean tryStartCleaningSequenceFromChat(Minecraft client, String requestedPlot, int spawnedCount) {
        if (client == null || client.getConnection() == null || client.player == null || isCleaningInProgress) {
            return false;
        }

        if (spawnedCount > 0) {
            predictedAliveCount = Math.min(99, predictedAliveCount + spawnedCount);
            lastChatSpawnUpdateMs = System.currentTimeMillis();
        }

        PestTabListParser.TabListData data = PestTabListParser.parseTabList(client);
        syncPredictedAliveFromTab(data.aliveCount);
        int effectiveAlive = getEffectiveAliveCount(data.aliveCount);

        if (!isThresholdMet(effectiveAlive)) {
            if (MacroConfig.showDebug) {
                ClientUtils.sendDebugMessage(client,
                        "Chat pest trigger ignored: effective=" + effectiveAlive
                                + " (chat=" + predictedAliveCount + ", tab=" + data.aliveCount
                                + ") < threshold=" + MacroConfig.pestThreshold);
            }
            return false;
        }

        if (isPestReentryCooldownActive()) {
            if (MacroConfig.showDebug) {
                ClientUtils.sendDebugMessage(client,
                        "Chat pest trigger ignored: pest re-entry cooldown active for "
                                + getPestReentryCooldownRemainingMs() + "ms.");
            }
            return false;
        }

        String targetPlot = requestedPlot;
        if ((targetPlot == null || targetPlot.isBlank() || "0".equals(targetPlot)) && !data.infestedPlots.isEmpty()) {
            targetPlot = data.infestedPlots.iterator().next();
        }

        startCleaningSequence(client, targetPlot);
        return true;
    }

    private static void syncPredictedAliveFromTab(int tabAliveCount) {
        if (tabAliveCount < 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (tabAliveCount >= predictedAliveCount) {
            predictedAliveCount = tabAliveCount;
            return;
        }

        if (now - lastChatSpawnUpdateMs > TAB_SYNC_GRACE_MS) {
            predictedAliveCount = tabAliveCount;
        }
    }

    private static int getEffectiveAliveCount(int tabAliveCount) {
        if (tabAliveCount < 0) {
            return predictedAliveCount;
        }
        return Math.max(tabAliveCount, predictedAliveCount);
    }

    public static void handlePestCleaningFinished(Minecraft client) {
        if (!PestReturnManager.isFinishingInProgress) {
            startPestReentryCooldown();
        }
        PestReturnManager.handlePestCleaningFinished(client);
    }

    public static void update(Minecraft client) {
        checkTabListForPests(client, com.ihanuat.mod.MacroStateManager.getCurrentState());
    }

    public static void startCleaningSequence(Minecraft client, String plot) {
        if (isPestReentryCooldownActive()) {
            return;
        }

        if (MacroConfig.delayPestForCropFever && CropFeverManager.isCropFeverActive) {
            client.player.displayClientMessage(
                    Component.literal("§dDelaying pest cleaning due to CROP FEVER buff!"), true);
            return;
        }

        currentInfestedPlot = plot;
        currentPestSessionId++;
        PestCleaningSequencer.startCleaningSequence(client, plot, currentInfestedPlot, currentPestSessionId);
    }

    public static void handlePhillipMessage(Minecraft client, String text) {
        PestBonusManager.handlePhillipMessage(client, text, currentInfestedPlot);
    }
}
