package com.ihanuat.mod;

import com.ihanuat.mod.gui.ConfigScreenFactory;
import com.ihanuat.mod.modules.*;
import com.ihanuat.mod.util.ClientUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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

public class IhanuatClient implements ClientModInitializer {
    private static KeyMapping configKey;
    private static KeyMapping startScriptKey;
    private static KeyMapping returnKey;

    private static boolean isHandlingMessage = false;
    private static boolean hasCheckedPersistenceOnJoin = false;
    private static long lastStashPickupTime = 0;
    private static final long STASH_PICKUP_DELAY_MS = 3300;

    private static long nextRestTriggerMs = 0;

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
        returnKey = KeyBindingHelper
                .registerKeyBinding(new KeyMapping("key.ihanuat.return_to_start", GLFW.GLFW_KEY_R, category));

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

                if (MacroStateManager.getCurrentState() == MacroState.State.CLEANING && lowerText.contains("visitor")
                        && lowerText.contains("finished") && !text.contains("sequence complete")) {
                    VisitorManager.handleVisitorScriptFinished(Minecraft.getInstance());
                }

                if (MacroStateManager.getReturnState() != MacroState.ReturnState.OFF
                        && lowerText.contains("fell into the void")) {
                    MacroStateManager.setReturnState(MacroState.ReturnState.TP_START);
                    ClientUtils.forceReleaseKeys(Minecraft.getInstance());
                }
            } finally {
                isHandlingMessage = false;
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;
            while (configKey.consumeClick())
                client.setScreen(ConfigScreenFactory.createConfigScreen(client.screen));
            while (returnKey.consumeClick()) {
                MacroStateManager.setReturnState(MacroState.ReturnState.TP_START);
                MacroStateManager.setCurrentState(MacroState.State.OFF);
            }
            while (startScriptKey.consumeClick()) {
                if (MacroStateManager.getCurrentState() == MacroState.State.OFF) {
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
                            client.execute(() -> {
                                GearManager.swapToFarmingTool(client);
                                ClientUtils.sendCommand(client, MacroConfig.restartScript);
                            });
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
                if (MacroStateManager.isMacroRunning()
                        || MacroStateManager.getReturnState() != MacroState.ReturnState.OFF) {
                    MacroStateManager.stopMacro(client);
                }
            }

            if (client.screen instanceof AbstractContainerScreen) {
                GearManager.handleWardrobeMenu(client, (AbstractContainerScreen<?>) client.screen);
                GearManager.handleEquipmentMenu(client, (AbstractContainerScreen<?>) client.screen);
            }

            com.ihanuat.mod.modules.RestartManager.update(client);
            com.ihanuat.mod.modules.PestManager.update(client);
            com.ihanuat.mod.modules.GearManager.cleanupTick(client);
            RotationManager.update(client, MacroStateManager.getReturnState());

            if (MacroStateManager.getCurrentState() == MacroState.State.RECOVERING) {
                RecoveryManager.update(client);
                return;
            }

            if (MacroStateManager.getCurrentState() == MacroState.State.FARMING
                    && MacroStateManager.getReturnState() == MacroState.ReturnState.OFF) {
                PestManager.checkTabListForPests(client, MacroStateManager.getCurrentState(),
                        MacroStateManager.getReturnState());
            }

            if (MacroStateManager.getReturnState() != MacroState.ReturnState.OFF) {
                ReturnManager.handleReturnSequence(client);
            }

            // Stash Pickup Logic
            if (MacroConfig.autoStashManager && client.player != null) {
                long now = System.currentTimeMillis();
                if (now - lastStashPickupTime >= STASH_PICKUP_DELAY_MS) {
                    lastStashPickupTime = now;
                    client.player.connection.sendCommand("pickupstash");
                }
            }
        });
    }

    public static void updateRotation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            RotationManager.update(mc, MacroStateManager.getReturnState());
        }
    }

    public static boolean shouldSuppressMouseRotation() {
        return RotationManager.isRotating() || MacroStateManager.getReturnState() != MacroState.ReturnState.OFF;
    }
}