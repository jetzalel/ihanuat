package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.util.ClientUtils;
import com.ihanuat.mod.util.MovementUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PestManager {
    private static final Pattern PESTS_ALIVE_PATTERN = Pattern.compile("Alive:\\s*(\\d+)");
    private static final Pattern COOLDOWN_PATTERN = Pattern.compile("Cooldown:\\s*(READY|(\\d+)m\\s*(\\d+)s|(\\d+)s)");

    public static volatile boolean isCleaningInProgress = false;
    public static volatile String currentInfestedPlot = null;
    public static volatile boolean prepSwappedForCurrentPestCycle = false;
    public static volatile int currentPestSessionId = 0;
    public static volatile boolean isReturningFromPestVisitor = false;
    private static volatile boolean isStoppingFlight = false;
    private static volatile int flightStopStage = 0;
    private static volatile int flightStopTicks = 0;

    public static void checkTabListForPests(Minecraft client, MacroState.State currentState,
            MacroState.ReturnState returnState) {
        if (client.getConnection() == null || isCleaningInProgress)
            return;

        int aliveCount = -1;
        Set<String> infestedPlots = new HashSet<>();
        Collection<PlayerInfo> players = client.getConnection().getListedOnlinePlayers();

        for (PlayerInfo info : players) {
            String name = "";
            if (info.getTabListDisplayName() != null) {
                name = info.getTabListDisplayName().getString();
            } else if (info.getProfile() != null) {
                name = String.valueOf(info.getProfile());
            }

            String clean = name.replaceAll("\u00A7[0-9a-fk-or]", "").trim();
            Matcher aliveMatcher = PESTS_ALIVE_PATTERN.matcher(clean);
            if (aliveMatcher.find()) {
                aliveCount = Integer.parseInt(aliveMatcher.group(1));
            }

            Matcher cooldownMatcher = COOLDOWN_PATTERN.matcher(clean);
            if (cooldownMatcher.find()) {
                int cooldownSeconds = -1;
                if (cooldownMatcher.group(1).equalsIgnoreCase("READY")) {
                    cooldownSeconds = 0;
                } else if (cooldownMatcher.group(2) != null) {
                    cooldownSeconds = Integer.parseInt(cooldownMatcher.group(2)) * 60
                            + Integer.parseInt(cooldownMatcher.group(3));
                } else if (cooldownMatcher.group(4) != null) {
                    cooldownSeconds = Integer.parseInt(cooldownMatcher.group(4));
                }

                if (MacroConfig.autoEquipment) {
                    if (cooldownSeconds > 170 && prepSwappedForCurrentPestCycle && !isCleaningInProgress) {
                        prepSwappedForCurrentPestCycle = false;
                    }
                } else {
                    if (cooldownSeconds > 8 && prepSwappedForCurrentPestCycle && !isCleaningInProgress) {
                        prepSwappedForCurrentPestCycle = false;
                    }
                }

                // Proactive return logic
                if (currentState == MacroState.State.FARMING && cooldownSeconds != -1 && cooldownSeconds > 0
                        && !isCleaningInProgress && !prepSwappedForCurrentPestCycle
                        && returnState == MacroState.ReturnState.OFF) {

                    boolean shouldEquipSoon = MacroConfig.autoEquipment && cooldownSeconds <= 180;
                    boolean shouldWardrobeSoon = (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE
                            && cooldownSeconds <= 15);

                    if ((shouldEquipSoon || shouldWardrobeSoon) && MovementUtils.isInEndRow(client)) {
                        triggerProactiveReturn(client);
                        return;
                    }
                }

                // Prep swap logic
                if (currentState == MacroState.State.FARMING && cooldownSeconds != -1 && cooldownSeconds > 0
                        && !prepSwappedForCurrentPestCycle && !isCleaningInProgress) {

                    if (MacroConfig.autoEquipment) {
                        if (cooldownSeconds <= 170)
                            triggerPrepSwap(client);
                    } else if (cooldownSeconds <= 8) {
                        triggerPrepSwap(client);
                    }
                }
            }

            if (clean.contains("Plots:")) {
                Matcher m = Pattern.compile("(\\d+)").matcher(clean);
                while (m.find()) {
                    infestedPlots.add(m.group(1).trim());
                }
            }
        }

        if (aliveCount >= MacroConfig.pestThreshold && !infestedPlots.isEmpty()) {
            startCleaningSequence(client, infestedPlots.iterator().next());
        }
    }

    public static void handlePestCleaningFinished(Minecraft client) {
        client.player.displayClientMessage(Component.literal("§aPest cleaning finished detected."), true);
        new Thread(() -> {
            try {
                isStoppingFlight = true;
                Thread.sleep(300);
                while (isStoppingFlight)
                    Thread.sleep(50);
                Thread.sleep(100);

                int visitors = VisitorManager.getVisitorCount(client);
                if (visitors >= MacroConfig.visitorThreshold) {
                    client.player.displayClientMessage(
                            Component.literal("§dVisitor Threshold Met (" + visitors + "). Direct Transition in 1s..."),
                            true);
                    Thread.sleep(1000);
                    GearManager.swapToFarmingTool(client);
                    ClientUtils.sendCommand(client, ".ez-startscript misc:visitor");
                    isCleaningInProgress = false;
                    return;
                }

                Thread.sleep(150);
                ClientUtils.sendCommand(client, "/warp garden");
                Thread.sleep(150);
                isReturningFromPestVisitor = true;
                finalizeReturnToFarm(client);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void finalizeReturnToFarm(Minecraft client) {
        if (!com.ihanuat.mod.MacroStateManager.isMacroRunning())
            return;
        try {
            Thread.sleep(150);
            int visitors = VisitorManager.getVisitorCount(client);
            if (visitors >= MacroConfig.visitorThreshold) {
                GearManager.swapToFarmingTool(client);
                ClientUtils.sendCommand(client, ".ez-startscript misc:visitor");
                isCleaningInProgress = false;
                return;
            }
            GearManager.swapToFarmingTool(client);
            ClientUtils.sendCommand(client, MacroConfig.restartScript);
            isCleaningInProgress = false;
        } catch (InterruptedException ignored) {
        }
    }

    public static void update(Minecraft client) {
        if (isStoppingFlight) {
            if (client.player != null && !client.player.getAbilities().flying) {
                isStoppingFlight = false;
                flightStopStage = 0;
                flightStopTicks = 0;
                return;
            }
            performFlightStopTick(client);
        }
    }

    private static void performFlightStopTick(Minecraft client) {
        flightStopTicks++;
        if (flightStopStage == 0) {
            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), true);
            if (flightStopTicks >= 2) {
                flightStopStage = 1;
                flightStopTicks = 0;
            }
        } else if (flightStopStage == 1) {
            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), false);
            if (flightStopTicks >= 3) {
                flightStopStage = 2;
                flightStopTicks = 0;
            }
        } else if (flightStopStage == 2) {
            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), true);
            if (flightStopTicks >= 2) {
                flightStopStage = 3;
                flightStopTicks = 0;
            }
        } else if (flightStopStage == 3) {
            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), false);
            isStoppingFlight = false;
            flightStopStage = 0;
            flightStopTicks = 0;
        }
    }

    private static void triggerProactiveReturn(Minecraft client) {
        client.player.displayClientMessage(Component.literal("§eProactive Return: Low Cooldown & End Row."), true);
        com.ihanuat.mod.MacroStateManager.setReturnState(MacroState.ReturnState.TP_START);
        com.ihanuat.mod.MacroStateManager.setCurrentState(MacroState.State.OFF);
    }

    private static void triggerPrepSwap(Minecraft client) {
        prepSwappedForCurrentPestCycle = true;
        client.player.displayClientMessage(Component.literal("\u00A7ePest cooldown detected. Triggering prep-swap..."),
                true);
        new Thread(() -> {
            try {
                ClientUtils.sendCommand(client, ".ez-stopscript");
                Thread.sleep(375);
                if (isCleaningInProgress)
                    return;

                if (MacroConfig.autoEquipment) {
                    GearManager.ensureEquipment(client, false);
                    Thread.sleep(375);
                    while (GearManager.isSwappingEquipment && !isCleaningInProgress)
                        Thread.sleep(50);
                    Thread.sleep(250);
                }

                if (isCleaningInProgress)
                    return;

                if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.ROD) {
                    GearManager.executeRodSequence(client);
                } else if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE) {
                    GearManager.triggerWardrobeSwap(client, MacroConfig.wardrobeSlotPest);
                } else {
                    resumeAfterPrepSwap(client);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void resumeAfterPrepSwap(Minecraft client) {
        GearManager.swapToFarmingTool(client);
        com.ihanuat.mod.MacroStateManager.setCurrentState(MacroState.State.FARMING);
        ClientUtils.sendCommand(client, MacroConfig.restartScript);
    }

    private static void startCleaningSequence(Minecraft client, String plot) {
        if (isCleaningInProgress || GearManager.isSwappingWardrobe || GearManager.isSwappingEquipment)
            return;

        isCleaningInProgress = true;
        currentInfestedPlot = plot;
        final int sessionId = ++currentPestSessionId;

        new Thread(() -> {
            try {
                ClientUtils.sendCommand(client, ".ez-stopscript");
                Thread.sleep(750);

                if (sessionId != currentPestSessionId)
                    return;

                if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE) {
                    prepSwappedForCurrentPestCycle = true;
                    GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotPest);
                }

                client.execute(() -> {
                    GearManager.swapToFarmingTool(client); // Just in case, to have vacuum ready if needed
                    ClientUtils.sendCommand(client, ".ez-startscript misc:pest");
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void resumeAfterPrepSwapLogic(Minecraft client) {
        GearManager.swapToFarmingTool(client);
        ClientUtils.sendCommand(client, MacroConfig.restartScript);
    }
}
