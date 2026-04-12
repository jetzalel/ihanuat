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
    private static final long DEFAULT_NO_PEST_RETURN_DELAY_MS = 10_000;
    private static long lastZeroPestTime = 0;
    private static volatile boolean manualReturnArmed = false;
    private static volatile int predictedAliveCount = 0;
    private static volatile long lastChatSpawnUpdateMs = 0;
    private static volatile long pestReentryCooldownUntilMs = 0;
    private static final long TAB_SYNC_GRACE_MS = 5000;
    private static volatile int lastLoggedManualAliveCount = Integer.MIN_VALUE;
    private static volatile boolean lastLoggedManualArmed = false;
    private static volatile boolean lastLoggedManualTimerActive = false;
    private static volatile String lastLoggedManualSource = "";

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

    private static long getNoPestReturnDelayMs() {
        return MacroConfig.manualPestClean ? MacroConfig.manualPestReturnDelay : DEFAULT_NO_PEST_RETURN_DELAY_MS;
    }

    private static int getReturnReadyPestCount() {
        return MacroConfig.manualPestClean ? MacroConfig.manualPestRewarpAt : 0;
    }

    public static void reset() {
        isCleaningInProgress = false;
        currentInfestedPlot = null;
        lastZeroPestTime = 0;
        manualReturnArmed = false;
        predictedAliveCount = 0;
        lastChatSpawnUpdateMs = 0;
        pestReentryCooldownUntilMs = 0;
        lastLoggedManualAliveCount = Integer.MIN_VALUE;
        lastLoggedManualArmed = false;
        lastLoggedManualTimerActive = false;
        lastLoggedManualSource = "";
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
            if (MacroConfig.manualPestClean) {
                if (MacroConfig.showDebug) {
                    ClientUtils.sendDebugMessage(client,
                            "Manual pest state drifted to FARMING during active cleaning; restoring CLEANING polling.");
                }
                MacroStateManager.setCurrentState(MacroState.State.CLEANING);
                currentState = MacroState.State.CLEANING;
            } else {
                isCleaningInProgress = false;
            }
        }

        PestTabListParser.TabListData data = PestTabListParser.parseTabList(client);
        syncPredictedAliveFromTab(data.aliveCount);
        int effectiveAlive = getEffectiveAliveCount(data.aliveCount);
        String manualAliveSource = "effective";
        if (currentState == MacroState.State.CLEANING && MacroConfig.manualPestClean) {
            int sidebarAliveCount = ClientUtils.getGardenPestCountFromSidebar(client);
            if (sidebarAliveCount >= 0) {
                predictedAliveCount = sidebarAliveCount;
                effectiveAlive = sidebarAliveCount;
                manualAliveSource = "sidebar";
            } else if (data.aliveCount >= 0) {
                predictedAliveCount = data.aliveCount;
                effectiveAlive = data.aliveCount;
                manualAliveSource = "tab";
            } else {
                manualAliveSource = "predicted";
            }
        }
        
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

        if (currentState == MacroState.State.CLEANING) {
            if (MacroConfig.manualPestClean && effectiveAlive > getReturnReadyPestCount()) {
                manualReturnArmed = true;
            }
            if (MacroConfig.manualPestClean) {
                logManualReturnState(client, effectiveAlive, manualAliveSource);
            }

            if (effectiveAlive <= getReturnReadyPestCount() && (!MacroConfig.manualPestClean || manualReturnArmed)) {
                if (lastZeroPestTime == 0) {
                    lastZeroPestTime = System.currentTimeMillis();
                    if (MacroConfig.manualPestClean && MacroConfig.showDebug) {
                        ClientUtils.sendDebugMessage(client,
                                "Manual pest countdown started: alive=" + effectiveAlive
                                        + ", source=" + manualAliveSource
                                        + ", target<=" + getReturnReadyPestCount()
                                        + ", delayMs=" + getNoPestReturnDelayMs());
                    }
                } else if (System.currentTimeMillis() - lastZeroPestTime > getNoPestReturnDelayMs()) {
                    if (client.player != null) {
                        client.player.displayClientMessage(
                                Component.literal("§cFail-safe: No pests detected for 10s. Returning to farm."), true);
                    }
                    if (MacroConfig.manualPestClean && MacroConfig.showDebug) {
                        ClientUtils.sendDebugMessage(client,
                                "Manual pest return triggered: alive=" + effectiveAlive
                                        + ", source=" + manualAliveSource
                                        + ", armed=" + manualReturnArmed);
                    }
                    lastZeroPestTime = 0;
                    handlePestCleaningFinished(client);
                    return;
                }
            } else {
                if (MacroConfig.manualPestClean && lastZeroPestTime != 0 && MacroConfig.showDebug) {
                    ClientUtils.sendDebugMessage(client,
                            "Manual pest countdown reset: alive=" + effectiveAlive
                                    + ", source=" + manualAliveSource
                                    + ", target<=" + getReturnReadyPestCount()
                                    + ", armed=" + manualReturnArmed);
                }
                lastZeroPestTime = 0;
            }
        } else {
            lastZeroPestTime = 0;
            manualReturnArmed = false;
            lastLoggedManualAliveCount = Integer.MIN_VALUE;
            lastLoggedManualArmed = false;
            lastLoggedManualTimerActive = false;
            lastLoggedManualSource = "";
        }

        if (!MacroConfig.autoPestEnabled) {
            return;
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

        if (!MacroConfig.autoPestEnabled) {
            if (MacroConfig.showDebug) {
                ClientUtils.sendDebugMessage(client, "Chat pest trigger ignored: Auto Pest is paused.");
            }
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

    private static void logManualReturnState(Minecraft client, int effectiveAlive, String source) {
        if (!MacroConfig.showDebug) {
            return;
        }

        boolean timerActive = lastZeroPestTime != 0;
        if (effectiveAlive != lastLoggedManualAliveCount
                || manualReturnArmed != lastLoggedManualArmed
                || timerActive != lastLoggedManualTimerActive
                || !source.equals(lastLoggedManualSource)) {
            ClientUtils.sendDebugMessage(client,
                    "Manual pest state: alive=" + effectiveAlive
                            + ", source=" + source
                            + ", target<=" + getReturnReadyPestCount()
                            + ", armed=" + manualReturnArmed
                            + ", timerActive=" + timerActive);
            lastLoggedManualAliveCount = effectiveAlive;
            lastLoggedManualArmed = manualReturnArmed;
            lastLoggedManualTimerActive = timerActive;
            lastLoggedManualSource = source;
        }
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
        if (isCleaningInProgress) return;
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

}
