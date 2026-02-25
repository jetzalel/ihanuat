package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.util.ClientUtils;
import com.ihanuat.mod.util.MovementUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.Vec3;

public class ReturnManager {
    private static int returnTickCounter = 0;
    private static long lastAspectUsageTime = 0;
    private static long stateStartTime = 0;

    public static volatile boolean isProactiveReturnPending = false;
    public static volatile boolean isStartingFlight = false;
    public static volatile int flightToggleStage = 0;
    public static volatile int flightToggleTicks = 0;

    public static void handleReturnSequence(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            MacroStateManager.setReturnState(MacroState.ReturnState.OFF);
            return;
        }

        returnTickCounter++;
        MacroState.ReturnState state = MacroStateManager.getReturnState();

        switch (state) {
            case TP_PRE_WAIT:
                if (System.currentTimeMillis() - stateStartTime >= 250) {
                    MacroStateManager.setReturnState(MacroState.ReturnState.TP_START);
                    returnTickCounter = 0;
                }
                break;

            case TP_START:
                String startPlot = MacroConfig.startPlot != null ? MacroConfig.startPlot : "8";
                ClientUtils.sendCommand(client, "/tptoplot " + startPlot);
                MacroStateManager.setReturnState(MacroState.ReturnState.TP_WAIT);
                returnTickCounter = 0;
                break;

            case TP_WAIT:
                if (returnTickCounter >= 20) {
                    MacroStateManager.setReturnState(MacroState.ReturnState.FLIGHT_START);
                    flightToggleStage = 0;
                    flightToggleTicks = 0;
                    returnTickCounter = 0;
                }
                break;

            case FLIGHT_START:
                if (client.player.getAbilities().flying || performFlightToggle(client, true)) {
                    if (client.options.keyJump != null)
                        KeyMapping.set(client.options.keyJump.getDefaultKey(), false);
                    MacroStateManager.setReturnState(MacroState.ReturnState.ALIGN_WAIT);

                    Vec3 targetHigh = new Vec3(MacroConfig.startPos.getX() + 0.5, MacroConfig.startPos.getY() + 3.5,
                            MacroConfig.startPos.getZ() + 0.5);
                    RotationManager.initiateRotation(client, targetHigh, 230);
                    stateStartTime = System.currentTimeMillis();
                }
                break;

            case ALIGN_WAIT:
                if (!RotationManager.isRotating() && (System.currentTimeMillis() - stateStartTime > 500)) {
                    MacroStateManager.setReturnState(MacroState.ReturnState.FLY_HIGH);
                }
                break;

            case FLY_HIGH:
                Vec3 targetHigh = new Vec3(MacroConfig.startPos.getX() + 0.5, MacroConfig.startPos.getY() + 3.5,
                        MacroConfig.startPos.getZ() + 0.5);
                RotationManager.setReturnLookTarget(targetHigh);

                if (RotationManager.isRotating()) {
                    MovementUtils.releaseMovementKeys(client);
                    return;
                }

                KeyMapping.set(client.options.keyUp.getDefaultKey(), true);

                Vec3 startPosVec = new Vec3(MacroConfig.startPos.getX() + 0.5, MacroConfig.startPos.getY(),
                        MacroConfig.startPos.getZ() + 0.5);
                double distHigh = client.player.position().distanceTo(targetHigh);
                boolean los = ClientUtils.hasLineOfSight(client.player, startPosVec);

                if ((distHigh <= 15.0 && los) || distHigh <= 3.0) {
                    MacroStateManager.setReturnState(MacroState.ReturnState.FLY_APPROACH);
                    RotationManager.initiateRotation(client, startPosVec, 100);
                } else {
                    long now = System.currentTimeMillis();
                    if (now - lastAspectUsageTime >= 500) {
                        useAspectItem(client);
                        lastAspectUsageTime = now;
                    }
                }
                break;

            case FLY_APPROACH:
                Vec3 startPosVec2 = new Vec3(MacroConfig.startPos.getX() + 0.5, MacroConfig.startPos.getY(),
                        MacroConfig.startPos.getZ() + 0.5);
                double horizontalDistFinal = Math.sqrt(Math.pow(client.player.getX() - startPosVec2.x, 2)
                        + Math.pow(client.player.getZ() - startPosVec2.z, 2));

                if (horizontalDistFinal >= 1.0) {
                    RotationManager.setReturnLookTarget(startPosVec2);
                }

                if (RotationManager.isRotating()) {
                    KeyMapping.set(client.options.keyUp.getDefaultKey(), false);
                    return;
                }

                double distFinal = client.player.position().distanceTo(startPosVec2);
                if (distFinal < 3.0) {
                    Vec3 detourTarget = MovementUtils.getDetourTarget(client, startPosVec2);
                    boolean pathClear = detourTarget.distanceTo(startPosVec2) < 0.1;
                    if (horizontalDistFinal < 0.8 && pathClear) {
                        MovementUtils.releaseMovementKeys(client);
                    } else {
                        MovementUtils.moveTowards(client, detourTarget);
                    }
                } else {
                    KeyMapping.set(client.options.keyUp.getDefaultKey(), true);
                    long now = System.currentTimeMillis();
                    if (now - lastAspectUsageTime >= 500) {
                        useAspectItem(client);
                        lastAspectUsageTime = now;
                    }
                }

                if (horizontalDistFinal < 0.3) {
                    MacroStateManager.setReturnState(MacroState.ReturnState.LANDING_SHIFT);
                    returnTickCounter = 0;
                }
                break;

            case LANDING_SHIFT:
                KeyMapping.set(client.options.keyShift.getDefaultKey(), true);
                if (client.player.onGround()) {
                    MacroStateManager.setReturnState(MacroState.ReturnState.LANDING_WAIT);
                    stateStartTime = System.currentTimeMillis();
                }
                break;

            case LANDING_WAIT:
                if (System.currentTimeMillis() - stateStartTime >= 500) {
                    MacroStateManager.stopMacro(client);
                    client.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§aReturn to start complete."), true);
                }
                break;

            case OFF:
                break;
        }
    }

    public static boolean performFlightToggle(Minecraft mc, boolean enable) {
        if (mc.player == null)
            return false;

        flightToggleTicks++;
        if (flightToggleStage == 0) {
            KeyMapping.set(mc.options.keyJump.getDefaultKey(), true);
            if (flightToggleTicks >= 2) {
                flightToggleStage = 1;
                flightToggleTicks = 0;
            }
        } else if (flightToggleStage == 1) {
            KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);
            if (flightToggleTicks >= 3) {
                flightToggleStage = 2;
                flightToggleTicks = 0;
            }
        } else if (flightToggleStage == 2) {
            KeyMapping.set(mc.options.keyJump.getDefaultKey(), true);
            if (flightToggleTicks >= 2) {
                flightToggleStage = 3;
                flightToggleTicks = 0;
            }
        } else if (flightToggleStage == 3) {
            KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);
            return true;
        }
        return false;
    }

    private static void useAspectItem(Minecraft client) {
        if (client.player == null)
            return;
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                String name = stack.getHoverName().getString().toLowerCase();
                if (name.contains("aspect of the void") || name.contains("aspect of the end")) {
                    ((com.ihanuat.mod.mixin.AccessorInventory) client.player.getInventory()).setSelected(i);
                    client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                    break;
                }
            }
        }
    }
}
