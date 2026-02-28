package com.ihanuat.mod;

import com.ihanuat.mod.gui.ConfigScreenFactory;
import com.ihanuat.mod.modules.*;
import com.ihanuat.mod.util.ClientUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IhanuatClient implements ClientModInitializer {
    private static KeyMapping configKey;
    private static KeyMapping startScriptKey;

    private static boolean isHandlingMessage = false;
    private static boolean hasCheckedPersistenceOnJoin = false;
    private static long lastStashPickupTime = 0;
    private static final long STASH_PICKUP_DELAY_MS = 3300;
    private static long lastRewarpTime = 0;
    private static final long REWARP_COOLDOWN_MS = 5000; // 5 seconds cooldown

    private static long nextRestTriggerMs = 0;
    private static boolean isPickingUpStash = false;

    @Override
    public void onInitializeClient() {
        MacroConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                hasCheckedPersistenceOnJoin = false;
                if (client.screen instanceof TitleScreen) {
                    long reconnectAt = RestStateManager.loadReconnectTime();
                    if (reconnectAt > 0) {
                        long remaining = reconnectAt - java.time.Instant.now().getEpochSecond();
                        if (remaining <= 0) {
                            ReconnectScheduler.scheduleReconnect(5, RestStateManager.shouldResume());
                        } else if (!ReconnectScheduler.isPending()) {
                            ReconnectScheduler.scheduleReconnect(remaining, RestStateManager.shouldResume());
                        }
                    }
                }
            } else if (!hasCheckedPersistenceOnJoin) {
                long reconnectAt = RestStateManager.loadReconnectTime();
                if (reconnectAt != 0) {
                    if (RestStateManager.shouldResume()) {
                        client.player.displayClientMessage(
                                Component.literal(
                                        "\u00A76[Ihanuat] Session persistence detected! Initializing recovery..."),
                                false);
                        MacroStateManager.setCurrentState(MacroState.State.RECOVERING);
                    }
                    RestStateManager.clearState();
                }
                hasCheckedPersistenceOnJoin = true;
            }
        });

        Identifier categoryId = Identifier.fromNamespaceAndPath("ihanuat", "main");
        KeyMapping.Category category = new KeyMapping.Category(categoryId);

        configKey = KeyBindingHelper
                .registerKeyBinding(new KeyMapping("key.ihanuat.config", GLFW.GLFW_KEY_O, category));
        startScriptKey = KeyBindingHelper
                .registerKeyBinding(new KeyMapping("key.ihanuat.start_script", GLFW.GLFW_KEY_K, category));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (isHandlingMessage)
                return;
            try {
                isHandlingMessage = true;
                String text = message.getString();
                String lowerText = text.toLowerCase();

                if ((lowerText.contains("server") && lowerText.contains("restart"))
                        || text.contains("Evacuating to Hub...") || text.contains("SERVER REBOOT!")
                        || lowerText.contains("proxy restart")) {
                    com.ihanuat.mod.modules.RestartManager
                            .handleRestartMessage(net.minecraft.client.Minecraft.getInstance());
                    return;
                }

                if (text.contains("You were spawned in Limbo.") || text
                        .contains("A disconnect occurred in your connection, so you were put in the SkyBlock Lobby!")) {
                    if (MacroStateManager.getCurrentState() != MacroState.State.OFF
                            && MacroStateManager.getCurrentState() != MacroState.State.RECOVERING) {
                        Minecraft.getInstance().player.displayClientMessage(Component
                                .literal("\u00A7c[Ihanuat] Disconnect detected! Starting recovery sequence..."), false);
                        MacroStateManager.stopMacro(Minecraft.getInstance());
                        MacroStateManager.setCurrentState(MacroState.State.RECOVERING);
                    }
                    return;
                }

                if (text.contains("Pest Cleaner") && text.contains("Finished")) {
                    if (MacroStateManager.getCurrentState() == MacroState.State.CLEANING) {
                        PestManager.handlePestCleaningFinished(Minecraft.getInstance());
                    }
                }

                if (lowerText.contains("yuck!") && lowerText.contains("spawned in plot")) {
                    if (MacroStateManager.getCurrentState() == MacroState.State.FARMING) {
                        Pattern p = Pattern.compile("Plot\\s*(?:-|#)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
                        Matcher m = p.matcher(text);
                        if (m.find()) {
                            String plot = m.group(1);
                            PestManager.startCleaningSequence(Minecraft.getInstance(), plot);
                        }
                    }
                }

                if (MacroStateManager.getCurrentState() == MacroState.State.CLEANING && lowerText.contains("visitor")
                        && lowerText.contains("finished") && !text.contains("sequence complete")) {
                    VisitorManager.handleVisitorScriptFinished(Minecraft.getInstance());
                }

                if (text.contains("Return To Location")) {
                    if (lowerText.contains("activated")) {
                        PestManager.isReturnToLocationActive = true;
                    } else if (lowerText.contains("stopped")) {
                        PestManager.isReturnToLocationActive = false;
                    }
                }

                if (lowerText.contains("stashed away!")) {
                    isPickingUpStash = true;
                }

                if (lowerText.contains("your stash isn't holding any items or materials!")) {
                    isPickingUpStash = false;
                }
            } finally {
                isHandlingMessage = false;
            }
        });

        ClientSendMessageEvents.COMMAND.register((command) -> {
            if (command.equalsIgnoreCase("call george")) {
                GeorgeManager.onCallGeorgeSent();
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;
            while (configKey.consumeClick())
                client.setScreen(ConfigScreenFactory.createConfigScreen(client.screen));
            while (startScriptKey.consumeClick()) {
                if (MacroStateManager.getCurrentState() == MacroState.State.OFF) {
                    PestManager.reset();
                    GearManager.reset();
                    MacroStateManager.setCurrentState(MacroState.State.FARMING);
                    if (nextRestTriggerMs == 0) {
                        int base = MacroConfig.restScriptingTime;
                        int offset = MacroConfig.restScriptingTimeOffset;
                        int randomOffset = (offset > 0) ? (new Random().nextInt(offset * 2 + 1) - offset) : 0;
                        nextRestTriggerMs = System.currentTimeMillis() + ((base + randomOffset) * 60L * 1000L);
                    }
                    new Thread(() -> {
                        try {
                            if (PestManager.prepSwappedForCurrentPestCycle
                                    && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotFarming) {
                                client.execute(
                                        () -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming));
                                Thread.sleep(800);
                            }

                            ClientUtils.waitForGearAndGui(client);

                            client.execute(() -> GearManager.swapToFarmingTool(client));
                            Thread.sleep(250);

                            ClientUtils.sendCommand(client, ".ez-stopscript");
                            Thread.sleep(250);

                            ClientUtils.sendCommand(client, MacroConfig.restartScript);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                } else {
                    MacroStateManager.stopMacro(client);
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;
            if (client.screen instanceof PauseScreen || client.screen instanceof ChatScreen) {
                if (MacroStateManager.isMacroRunning()) {
                    MacroStateManager.stopMacro(client);
                }
            }

            if (client.screen instanceof AbstractContainerScreen) {
                AbstractContainerScreen<?> currentScreen = (AbstractContainerScreen<?>) client.screen;
                GearManager.handleWardrobeMenu(client, currentScreen);
                if (client.screen == currentScreen)
                    GearManager.handleEquipmentMenu(client, currentScreen);
                if (client.screen == currentScreen)
                    GeorgeManager.handleGeorgeMenu(client, currentScreen);
                if (client.screen == currentScreen)
                    BoosterCookieManager.handleBoosterCookieMenu(client, currentScreen);
            }

            GeorgeManager.update(client);

            com.ihanuat.mod.modules.RestartManager.update(client);
            com.ihanuat.mod.modules.PestManager.update(client);
            com.ihanuat.mod.modules.GearManager.cleanupTick(client);
            RotationManager.update(client);

            if (PestManager.isSneakingForAotv) {
                if (client.options != null) {
                    client.options.keyShift.setDown(true);
                }
            }

            // Double-tap Space Flight Toggle
            if (PestManager.isStoppingFlight) {
                PestManager.flightStopTicks++;
                switch (PestManager.flightStopStage) {
                    case 0: // Press
                        if (client.options.keyJump != null)
                            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), true);
                        if (PestManager.flightStopTicks >= 2) {
                            PestManager.flightStopStage = 1;
                            PestManager.flightStopTicks = 0;
                        }
                        break;
                    case 1: // Release
                        if (client.options.keyJump != null)
                            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), false);
                        if (PestManager.flightStopTicks >= 3) {
                            PestManager.flightStopStage = 2;
                            PestManager.flightStopTicks = 0;
                        }
                        break;
                    case 2: // Press
                        if (client.options.keyJump != null)
                            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), true);
                        if (PestManager.flightStopTicks >= 2) {
                            PestManager.flightStopStage = 3;
                            PestManager.flightStopTicks = 0;
                        }
                        break;
                    case 3: // Done
                        if (client.options.keyJump != null)
                            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), false);
                        PestManager.isStoppingFlight = false;
                        break;
                }
            }

            if (MacroStateManager.getCurrentState() == MacroState.State.RECOVERING) {
                RecoveryManager.update(client);
                return;
            }

            // Stash Pickup Logic
            if (MacroConfig.autoStashManager && isPickingUpStash && client.player != null) {
                // Only perform stash pickup when NOT in a GUI
                if (client.screen == null) {
                    long now = System.currentTimeMillis();
                    if (now - lastStashPickupTime >= STASH_PICKUP_DELAY_MS) {
                        lastStashPickupTime = now;
                        client.player.connection.sendCommand("pickupstash");
                    }
                }
            }

            // PlotTP Rewarp Logic (Coordinate-based)
            if (MacroConfig.enablePlotTpRewarp && MacroConfig.rewarpEndPosSet
                    && MacroStateManager.getCurrentState() == MacroState.State.FARMING) {
                long now = System.currentTimeMillis();
                if (now - lastRewarpTime >= REWARP_COOLDOWN_MS) {
                    double dx = client.player.getX() - MacroConfig.rewarpEndX;
                    double dy = client.player.getY() - MacroConfig.rewarpEndY;
                    double dz = client.player.getZ() - MacroConfig.rewarpEndZ;
                    double distanceSq = dx * dx + dy * dy + dz * dz;

                    if (distanceSq <= 1.5 * 1.5) { // Within 1.5 blocks
                        lastRewarpTime = now;
                        client.player.displayClientMessage(Component.literal("§6Rewarp End Position reached!"), true);
                        new Thread(() -> {
                            try {
                                client.execute(() -> ClientUtils.sendCommand(client, ".ez-stopscript"));
                                Thread.sleep(300);
                                client.execute(() -> MacroConfig.executePlotTpRewarp(client));
                                Thread.sleep(1200); // Wait for warp
                                if (MacroStateManager.getCurrentState() == MacroState.State.FARMING) {
                                    client.execute(() -> ClientUtils.sendCommand(client, MacroConfig.restartScript));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                }
            }
        });
    }

    public static void updateRotation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            RotationManager.update(mc);
        }
    }

    public static boolean shouldSuppressMouseRotation() {
        return RotationManager.isRotating();
    }
}