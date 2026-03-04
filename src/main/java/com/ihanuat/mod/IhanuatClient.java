package com.ihanuat.mod;

import com.ihanuat.mod.gui.ConfigScreenFactory;
import com.ihanuat.mod.gui.MacroHudRenderer;
import com.ihanuat.mod.modules.GearManager;
import com.ihanuat.mod.modules.PestManager;
import com.ihanuat.mod.modules.GeorgeManager;
import com.ihanuat.mod.modules.RecoveryManager;
import com.ihanuat.mod.modules.DynamicRestManager;
import com.ihanuat.mod.modules.RestartManager;
import com.ihanuat.mod.modules.BoosterCookieManager;
import com.ihanuat.mod.modules.BookCombineManager;
import com.ihanuat.mod.modules.RotationManager;
import com.ihanuat.mod.modules.VisitorManager;
import com.ihanuat.mod.modules.JunkManager;
import com.ihanuat.mod.modules.ProfitManager;
import com.ihanuat.mod.util.ClientUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

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

    private static boolean isPickingUpStash = false;
    private static String lastScannedVisitorTitle = null;

    @Override
    public void onInitializeClient() {
        MacroConfig.load();
        ProfitManager.loadLifetime();
        MacroStateManager.syncFromConfig();
        RestStateManager.clearState();
        MacroHudRenderer.register();
        com.ihanuat.mod.gui.ProfitHudRenderer.register();

        // ── Visitor ROI: detect "offer accepted" chat messages (case-insensitive) ──
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay)
                return;
            String text = message.getString();
            String cleanText = text.replaceAll("(?i)\u00A7[0-9a-fk-or]", "").trim();
            String lowerClean = cleanText.toLowerCase();

            if (lowerClean.contains("offer accepted")) {
                // Extract visitor name after "with" if present
                String visitorName = cleanText;
                int withIdx = cleanText.toLowerCase().indexOf("with");
                if (withIdx >= 0) {
                    visitorName = cleanText.substring(withIdx + 4).trim();
                }
                if (visitorName.contains("]")) {
                    visitorName = visitorName.substring(visitorName.lastIndexOf("]") + 1).trim();
                }
                VisitorManager.onOfferAccepted(visitorName);
            }
        });

        // ── HUD edit-mode: render panel and handle drag/resize in inventory screens ──
        ScreenEvents.AFTER_INIT.register((mcClient, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen))
                return;

            ScreenEvents.afterRender(screen).register((scr, graphics, mouseX, mouseY, tickDelta) -> {
                MacroHudRenderer.renderInEditMode(graphics, Minecraft.getInstance());
                com.ihanuat.mod.gui.ProfitHudRenderer.renderInEditMode(graphics, Minecraft.getInstance());

                // ── Visitor ROI: scan visitor GUI for costs/rewards ──
                if (scr instanceof AbstractContainerScreen) {
                    AbstractContainerScreen<?> containerScr = (AbstractContainerScreen<?>) scr;
                    String title = containerScr.getTitle().getString().trim();
                    // Only scan once per unique GUI title, and only if macro is running
                    if (!title.equals(lastScannedVisitorTitle)
                            && com.ihanuat.mod.MacroStateManager.isMacroRunning()) {
                        // Check if slot 29 has loaded (has items)
                        if (containerScr.getMenu().slots.size() > 29) {
                            net.minecraft.world.inventory.Slot slot = containerScr.getMenu().getSlot(29);
                            if (slot != null && slot.hasItem()) {
                                String itemName = slot.getItem().getHoverName().getString();
                                if (itemName.contains("Accept Offer")) {
                                    lastScannedVisitorTitle = title;
                                    new Thread(() -> VisitorManager.scanVisitorGui(
                                            Minecraft.getInstance(), containerScr)).start();
                                }
                            }
                        }
                    }
                }
            });

            // Fabric 0.141+: mouse event object carries x, y, button and modifiers
            ScreenMouseEvents.allowMouseClick(screen).register((scr, event) -> {
                if (event.button() == 0) {
                    if (MacroHudRenderer.isHovered(event.x(), event.y())) {
                        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
                        MacroHudRenderer.startDrag(event.x(), event.y(), ctrl);
                        return false;
                    }
                    if (com.ihanuat.mod.gui.ProfitHudRenderer.isHovered(event.x(), event.y())) {
                        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
                        com.ihanuat.mod.gui.ProfitHudRenderer.startDrag(event.x(), event.y(), ctrl);
                        return false;
                    }
                }
                return true;
            });

            ScreenMouseEvents.allowMouseDrag(screen).register((scr, event, deltaX, deltaY) -> {
                if (MacroHudRenderer.isInteracting()) {
                    MacroHudRenderer.drag(event.x(), event.y());
                    return false;
                }
                if (com.ihanuat.mod.gui.ProfitHudRenderer.isInteracting()) {
                    com.ihanuat.mod.gui.ProfitHudRenderer.drag(event.x(), event.y());
                    return false;
                }
                return true;
            });

            ScreenMouseEvents.allowMouseRelease(screen).register((scr, event) -> {
                MacroHudRenderer.endDrag();
                com.ihanuat.mod.gui.ProfitHudRenderer.endDrag();
                return true;
            });
        });

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
                MacroStateManager.setIntentionalDisconnect(false);
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
                    RestartManager.handleRestartMessage(net.minecraft.client.Minecraft.getInstance());
                    return;
                }

                if (lowerText.contains("autosell") && lowerText.contains("script activated")) {
                    MacroStateManager.setCurrentState(MacroState.State.AUTOSELLING);
                    return;
                }

                if (lowerText.contains("autosell") && lowerText.contains("script stopped")) {
                    if (MacroStateManager.getCurrentState() == MacroState.State.AUTOSELLING) {
                        MacroStateManager.setCurrentState(MacroState.State.FARMING); // Fallback to farming or whatever
                                                                                     // was before? User didn't specify,
                                                                                     // but usually it's farming.
                    }
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
                        ProfitManager.stopSprayPhase();
                        PestManager.handlePestCleaningFinished(Minecraft.getInstance());
                    }
                }

                String plainText = text.replaceAll("(?i)[\u00A7&][0-9a-fk-or]", "");

                // Track bazaar buys during pest cleaner spray phase
                if (ProfitManager.isSprayPhaseActive && plainText.contains("[Bazaar]")
                        && plainText.contains("Bought")) {
                    java.util.regex.Matcher bazaarMatcher = java.util.regex.Pattern.compile(
                            "\\[Bazaar\\]\\s*Bought\\s+(\\d+)x\\s+.+?for\\s+([\\d,]+)\\s+coins",
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(plainText);
                    if (bazaarMatcher.find()) {
                        try {
                            int qty = Integer.parseInt(bazaarMatcher.group(1));
                            long coins = Long.parseLong(bazaarMatcher.group(2).replace(",", ""));
                            ProfitManager.addSprayCost(qty, coins);
                            if (MacroConfig.showDebug) {
                                ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                                        "Spray buy detected: " + qty + " items for " + coins + " coins");
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                // Debug for ANY message containing "YUCK" or "Plot" for diagnostic purposes
                if (lowerText.contains("yuck") && MacroConfig.showDebug) {
                    ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                            "Diagnose: Seen YUCK in chat. Text: " + plainText);
                }

                if (MacroConfig.triggerPestOnChat && lowerText.contains("yuck") && lowerText.contains("plot")) {
                    if (MacroConfig.showDebug) {
                        ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                                "YUCK detected. State: " + MacroStateManager.getCurrentState());
                    }
                    if (lowerText.contains("spawned") || lowerText.contains("phillip")) {
                        if (MacroStateManager.getCurrentState() == MacroState.State.FARMING) {
                            // Match format: "Plot - 14" or "Plot #14" or "Plot: 6"
                            Pattern p = Pattern.compile("Plot\\s*[\\-#:]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
                            Matcher m = p.matcher(plainText);
                            if (m.find()) {
                                String plot = m.group(1);
                                if (MacroConfig.pestChatTriggerDelay > 0) {
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(MacroConfig.pestChatTriggerDelay);
                                            PestManager.startCleaningSequence(Minecraft.getInstance(), plot);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }).start();
                                } else {
                                    PestManager.startCleaningSequence(Minecraft.getInstance(), plot);
                                }
                            } else if (MacroConfig.showDebug) {
                                ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                                        "Chat pest trigger failed regex on: " + plainText);
                            }
                        }
                    }
                }

                // Debug: log all visitor-related messages when in VISITING/CLEANING state
                if (MacroConfig.showDebug && plainText.toLowerCase().contains("visitor")) {
                    ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                            "[visitorCheck] state=" + MacroStateManager.getCurrentState()
                                    + " hasScript=" + plainText.toLowerCase().contains("script")
                                    + " hasStopped=" + plainText.toLowerCase().contains("stopped")
                                    + " hasFinished=" + plainText.toLowerCase().contains("finished")
                                    + " msg=[" + plainText + "]");
                }

                if ((MacroStateManager.getCurrentState() == MacroState.State.VISITING
                        || MacroStateManager.getCurrentState() == MacroState.State.CLEANING)
                        && plainText.toLowerCase().contains("visitor") && plainText.toLowerCase().contains("script")
                        && (plainText.toLowerCase().contains("finished") || plainText.toLowerCase().contains("stopped"))
                        && !plainText.contains("sequence complete")) {
                    ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                            "Visitor macro completion detected.");
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

                ProfitManager.handleChatMessage(message);
                PestManager.handlePhillipMessage(Minecraft.getInstance(), text);

                // Notify CommandUtils about the chat message for command synchronization
                com.ihanuat.mod.util.CommandUtils.onChatMessage(text);

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
                    GeorgeManager.reset();
                    BookCombineManager.reset();
                    JunkManager.reset();
                    RecoveryManager.reset();
                    MacroStateManager.setCurrentState(MacroState.State.FARMING);
                    ProfitManager.startStartupPriceFetch();
                    ProfitManager.printPetXpPriceDebug(client);
                    DynamicRestManager.scheduleNextRest();
                    new Thread(() -> {
                        try {
                            if (PestManager.prepSwappedForCurrentPestCycle
                                    && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotFarming) {
                                client.execute(
                                        () -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming));
                                Thread.sleep(800);
                            }

                            ClientUtils.waitForGearAndGui(client);
                            if (PestManager.isCleaningInProgress || PestManager.isPrepSwapping)
                                return;

                            GearManager.swapToFarmingToolSync(client);
                            if (PestManager.isCleaningInProgress || PestManager.isPrepSwapping)
                                return;

                            com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
                            if (PestManager.isCleaningInProgress || PestManager.isPrepSwapping)
                                return;

                            com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(),
                                    0);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                } else {
                    if (!MacroConfig.persistSessionTimer) {
                        DynamicRestManager.reset();
                    }
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
                if (client.screen == currentScreen)
                    BookCombineManager.handleAnvilMenu(client, currentScreen);
                if (client.screen == currentScreen)
                    JunkManager.handleInventoryMenu(client, currentScreen);
            }

            GeorgeManager.update(client);
            BookCombineManager.update(client);
            JunkManager.update(client);

            DynamicRestManager.update(client);
            RestartManager.update(client);
            PestManager.update(client);
            GearManager.cleanupTick(client);
            RotationManager.update(client);
            MacroStateManager.periodicUpdate();
            ProfitManager.update(client);
            com.ihanuat.mod.modules.DiscordStatusManager.update(client);

            if (PestManager.isSneakingForAotv) {
                if (client.options != null) {
                    client.options.keyShift.setDown(true);
                }
            }

            if (GearManager.isHoldingRodUse) {
                client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND);
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
                                client.execute(() -> com.ihanuat.mod.util.CommandUtils.stopScript(client, 0));
                                Thread.sleep(300);
                                client.execute(() -> MacroConfig.executePlotTpRewarp(client));
                                Thread.sleep(1200); // Wait for warp
                                if (MacroStateManager.getCurrentState() == MacroState.State.FARMING) {
                                    client.execute(
                                            () -> com.ihanuat.mod.util.CommandUtils.startScript(client,
                                                    MacroConfig.getFullRestartCommand(), 0));
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