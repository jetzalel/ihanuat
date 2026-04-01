package com.ihanuat.mod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import com.ihanuat.mod.gui.ClickGui;
import com.ihanuat.mod.gui.DynamicRestScreen;
import com.ihanuat.mod.gui.MacroHudRenderer;
import com.ihanuat.mod.modules.BookCombineManager;
import com.ihanuat.mod.modules.BoosterCookieManager;
import com.ihanuat.mod.modules.ChatRuleManager;
import com.ihanuat.mod.modules.DynamicRestManager;
import com.ihanuat.mod.modules.GearManager;
import com.ihanuat.mod.modules.GeorgeManager;
import com.ihanuat.mod.modules.JunkManager;
import com.ihanuat.mod.modules.PestAotvManager;
import com.ihanuat.mod.modules.PestManager;
import com.ihanuat.mod.modules.PestPrepSwapManager;
import com.ihanuat.mod.modules.PestReturnManager;
import com.ihanuat.mod.modules.profitTracker.ProfitManager;
import com.ihanuat.mod.modules.QuitThresholdManager;
import com.ihanuat.mod.modules.RecoveryManager;
import com.ihanuat.mod.modules.RestartManager;
import com.ihanuat.mod.modules.RodManager;
import com.ihanuat.mod.modules.RotationManager;
import com.ihanuat.mod.modules.SuperCrafter;
import com.ihanuat.mod.modules.VisitorManager;
import com.ihanuat.mod.modules.WardrobeManager;
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
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class IhanuatClient implements ClientModInitializer {
    private static KeyMapping configKey;
    private static KeyMapping startScriptKey;

    private static boolean isHandlingMessage = false;
    private static boolean hasCheckedPersistenceOnJoin = false;
    private static boolean hasCheckedUpdate = false;
    private static long lastStashPickupTime = 0;
    private static final long STASH_PICKUP_DELAY_MS = 3300;
    private static long lastRewarpTime = 0;
    private static final long REWARP_COOLDOWN_MS = 5000;

    private static boolean isPickingUpStash = false;
    private static boolean lastScreenWasBoosterCookie = false;
    private static String lastScannedVisitorTitle = null;
    private static long lastUnexpectedRecoveryTriggerMs = 0;
    private static final long UNEXPECTED_RECOVERY_COOLDOWN_MS = 7000;

    @Override
    public void onInitializeClient() {
        MacroConfig.load();
        ProfitManager.loadLifetime();
        ProfitManager.loadDaily();
        MacroStateManager.syncFromConfig();


        RestStateManager.clearState();
        MacroHudRenderer.register();
        com.ihanuat.mod.gui.ProfitHudRenderer.register();
        MacroWorkerThread.getInstance().start();

        // ── Visitor ROI: detect "offer accepted" chat messages (case-insensitive) ──
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay)
                return;
            String text = message.getString();
            String cleanText = com.ihanuat.mod.util.ClientUtils.stripColor(text).trim();
            String lowerClean = cleanText.toLowerCase();

            if (lowerClean.contains("offer accepted")) {
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

                if (scr instanceof AbstractContainerScreen) {
                    AbstractContainerScreen<?> containerScr = (AbstractContainerScreen<?>) scr;
                    String title = containerScr.getTitle().getString().trim();
                    if (!title.equals(lastScannedVisitorTitle)
                            && com.ihanuat.mod.MacroStateManager.isMacroRunning()) {
                        if (containerScr.getMenu().slots.size() > 29) {
                            net.minecraft.world.inventory.Slot slot = containerScr.getMenu().getSlot(29);
                            if (slot != null && slot.hasItem()) {
                                String itemName = slot.getItem().getHoverName().getString();
                                if (itemName.contains("Accept Offer")) {
                                    lastScannedVisitorTitle = title;
                                    MacroWorkerThread.getInstance().submit("VisitorGui-Scan",
                                            () -> VisitorManager.scanVisitorGui(
                                                    Minecraft.getInstance(), containerScr));
                                }
                            }
                        }
                    }
                }
            });

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

                // Failsafe: if macro is still marked active during an unexpected disconnect,
                // force RECOVERING and ensure reconnect is scheduled.
                if (MacroStateManager.isMacroRunning()
                        && MacroStateManager.getCurrentState() != MacroState.State.RECOVERING
                        && !MacroStateManager.isIntentionalDisconnect()) {
                    if (MacroConfig.autoRecoverUnexpectedDisconnect) {
                        if (!ReconnectScheduler.isPending()) {
                            ReconnectScheduler.scheduleReconnect(10, true);
                        }
                        MacroStateManager.setCurrentState(MacroState.State.RECOVERING);
                    } else {
                        MacroStateManager.stopMacro(client);
                    }
                }

                if (client.screen instanceof TitleScreen || client.screen instanceof DisconnectedScreen
                        || client.screen instanceof DynamicRestScreen) {
                    RestStateManager.RestState restState = RestStateManager.getState();
                    long reconnectAt = restState.reconnectAt();
                    if (reconnectAt > 0) {
                        long now = java.time.Instant.now().getEpochSecond();
                        long remaining = reconnectAt - now;
                        if (remaining <= 0) {
                            if (!ReconnectScheduler.isPending()) {
                                ReconnectScheduler.scheduleReconnect(10, restState.shouldResume());
                                if (client.screen instanceof DisconnectedScreen) {
                                    client.execute(() -> client.setScreen(new DynamicRestScreen(
                                            java.time.Instant.now().getEpochSecond() * 1000 + 10000, 10000)));
                                }
                            }
                        } else if (!ReconnectScheduler.isPending()) {
                            ReconnectScheduler.scheduleReconnect(remaining, restState.shouldResume());
                            if (client.screen instanceof DisconnectedScreen) {
                                client.execute(() -> client
                                        .setScreen(new DynamicRestScreen(reconnectAt * 1000, remaining * 1000)));
                            }
                        }
                    }
                }
            } else if (!hasCheckedPersistenceOnJoin) {
                RestStateManager.RestState restState = RestStateManager.getState();
                long reconnectAt = restState.reconnectAt();
                if (reconnectAt != 0) {
                    // Rejoin can complete a few seconds after the scheduled reconnect time,
                    // so the saved resume flag is the reliable signal here.
                    if (restState.shouldResume() && MacroConfig.autoResumeAfterDynamicRest) {
                        client.player.displayClientMessage(
                                Component.literal(
                                        "\u00A76[Ihanuat] Session persistence detected! Initializing recovery..."),
                                false);
                        MacroStateManager.setCurrentState(MacroState.State.RECOVERING);
                    }
                    RestStateManager.clearState();
                }
                // ── Timer persistence on join ───────────────────────────────────────────
                // Only reset the Dynamic Rest timer if the macro is fully OFF.
                // If the macro was running (e.g. player left garden and rejoined),
                // we want the current session timer and next-rest timer to keep
                // counting from where they left off — never reset on a plain join.
                // Timers are only zeroed by: (a) pressing K to stop, or (b) a
                // scheduled Dynamic Rest re-arm after reconnect.
                if (!MacroStateManager.isMacroRunning()) {
                    DynamicRestManager.reset();
                }
                hasCheckedPersistenceOnJoin = true;
                MacroStateManager.setIntentionalDisconnect(false);
            }
            if (!hasCheckedUpdate && client.player != null) {
                hasCheckedUpdate = true;
                UpdateChecker.checkAndNotify(client);
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
                        MacroStateManager.setCurrentState(MacroState.State.FARMING);
                    }
                    return;
                }

                if (text.contains("You were spawned in Limbo.") || text
                        .contains("A disconnect occurred in your connection, so you were put in the SkyBlock Lobby!")) {
                    if (MacroStateManager.getCurrentState() != MacroState.State.OFF
                            && MacroStateManager.getCurrentState() != MacroState.State.RECOVERING
                            && MacroConfig.autoRecoverUnexpectedDisconnect) {
                        Minecraft.getInstance().player.displayClientMessage(Component
                                .literal("\u00A7c[Ihanuat] Disconnect detected! Starting recovery sequence..."), false);
                        MacroStateManager.stopMacro(Minecraft.getInstance());
                        MacroStateManager.setCurrentState(MacroState.State.RECOVERING);
                    }
                    return;
                }

                if (text.contains("Taunahi >>") && text.contains("Let's use sprayonator.")) {
                    MacroStateManager.setCurrentState(MacroState.State.SPRAYING);
                    PestManager.isCleaningInProgress = true;
                    ProfitManager.startSprayPhase();
                    return;
                }

                String plainText = com.ihanuat.mod.util.ClientUtils.stripColor(text);
                String lowerPlainText = plainText.toLowerCase();

                if (lowerPlainText.contains("script stopped") && lowerPlainText.contains("remote control")) {
                    MacroStateManager.stopMacro(Minecraft.getInstance());
                    return;
                }

                boolean isPestCleanerFinishSignal = lowerPlainText.contains("pest cleaner")
                        && lowerPlainText.contains("finished")
                        && !lowerPlainText.contains("sprayed plot")
                        && !lowerPlainText.contains("plot -")
                        && !lowerPlainText.matches(".*plot\\s*[#:\\-]\\s*\\d+.*")
                        && !lowerPlainText.contains("[debug]")
                        && !lowerPlainText.contains("detected from chat");

                if (isPestCleanerFinishSignal) {
                    if (MacroStateManager.getCurrentState() == MacroState.State.CLEANING
                            || MacroStateManager.getCurrentState() == MacroState.State.SPRAYING) {
                        if (MacroConfig.showDebug) {
                            ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                                    "Pest cleaner completion detected from chat: [" + plainText + "]");
                        }
                        ProfitManager.stopSprayPhase();
                        PestManager.handlePestCleaningFinished(Minecraft.getInstance());
                    }

                }

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

                if (MacroConfig.triggerPestOnChat && MacroStateManager.isMacroRunning() && lowerText.contains("yuck") && lowerText.contains("plot")) {
                    if (MacroConfig.showDebug) {
                        ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                                "YUCK detected. State: " + MacroStateManager.getCurrentState());
                    }
                    if (lowerText.contains("spawned") || lowerText.contains("phillip")) {
                        if (MacroStateManager.getCurrentState() == MacroState.State.FARMING) {
                            Pattern p = Pattern.compile("Plot\\s*[\\-#:]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
                            Matcher m = p.matcher(plainText);
                            if (m.find()) {
                                String plot = m.group(1);
                                int spawnedCount = 0;
                                Matcher spawnMatcher = Pattern
                                        .compile("(?i)yuck!\\s*(\\d+)\\D+pest\\s+(?:have|has)\\s+spawned")
                                        .matcher(plainText);
                                if (spawnMatcher.find()) {
                                    try {
                                        spawnedCount = Integer.parseInt(spawnMatcher.group(1));
                                    } catch (NumberFormatException ignored) {
                                        spawnedCount = 0;
                                    }
                                }
                                final int parsedSpawnedCount = spawnedCount;
                                if (MacroConfig.pestChatTriggerDelay > 0) {
                                    MacroWorkerThread.getInstance().submit("PestClean-ChatTrigger-" + plot, () -> {
                                        if (MacroWorkerThread.shouldAbortTask(Minecraft.getInstance(),
                                                MacroState.State.FARMING)) {
                                            return;
                                        }
                                        MacroWorkerThread.sleep(MacroConfig.pestChatTriggerDelay);
                                        if (MacroWorkerThread.shouldAbortTask(Minecraft.getInstance(),
                                                MacroState.State.FARMING)) {
                                            return;
                                        }
                                        PestManager.tryStartCleaningSequenceFromChat(Minecraft.getInstance(), plot,
                                                parsedSpawnedCount);
                                    });
                                } else {
                                    MacroWorkerThread.getInstance().submit("PestClean-ChatTrigger-" + plot,
                                            () -> {
                                                if (MacroWorkerThread.shouldAbortTask(Minecraft.getInstance(),
                                                        MacroState.State.FARMING)) {
                                                    return;
                                                }
                                                PestManager.tryStartCleaningSequenceFromChat(
                                                        Minecraft.getInstance(), plot, parsedSpawnedCount);
                                            });
                                }
                            } else if (MacroConfig.showDebug) {
                                ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                                        "Chat pest trigger failed regex on: " + plainText);
                            }
                        }
                    }
                }

                if (MacroConfig.showDebug && plainText.toLowerCase().contains("visitor")
                        && !plainText.startsWith("[Debug]")) {
                    ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                            "[visitorCheck] state=" + MacroStateManager.getCurrentState()
                                    + " hasScript=" + plainText.toLowerCase().contains("script")
                                    + " hasStopped=" + plainText.toLowerCase().contains("stopped")
                                    + " hasFinished=" + plainText.toLowerCase().contains("finished")
                                    + " msg=[" + plainText + "]");
                }

                if ((MacroStateManager.getCurrentState() == MacroState.State.VISITING
                        || MacroStateManager.getCurrentState() == MacroState.State.CLEANING
                        || MacroStateManager.getCurrentState() == MacroState.State.SPRAYING)
                        && !plainText.startsWith("[Debug]")
                        && plainText.toLowerCase().contains("visitor") && plainText.toLowerCase().contains("script")
                        && (plainText.toLowerCase().contains("finished") || plainText.toLowerCase().contains("stopped"))
                        && !plainText.contains("sequence complete")) {
                    ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                            "Visitor macro completion detected.");
                    VisitorManager.handleVisitorScriptFinished(Minecraft.getInstance());
                }

                if (text.contains("Return To Location")) {
                    if (lowerText.contains("activated")) {
                        PestReturnManager.isReturnToLocationActive = true;
                    } else if (lowerText.contains("stopped")) {
                        PestReturnManager.isReturnToLocationActive = false;
                    }
                }

                if (lowerText.contains("stashed away!")) {
                    isPickingUpStash = true;
                }

                if (lowerText.contains("your stash isn't holding any items or materials!")) {
                    isPickingUpStash = false;
                }

                ProfitManager.handleChatMessage(message);
                com.ihanuat.mod.modules.CropFeverManager.handleChatMessage(Minecraft.getInstance(), plainText);

                com.ihanuat.mod.util.CommandUtils.onChatMessage(plainText);

            } finally {
                isHandlingMessage = false;
            }
        });

        // ── ChatRules: capture both GAME (server/system/Hypixel) and CHAT (signed player) ──
        //
        // On Hypixel, virtually all messages are GAME events (server-sent).
        // Signed player CHAT events cover direct player-to-player chat.
        // Both are registered so nothing is missed.
        //
        // Double-fire protection: ChatRuleManager.handleChatMessage() uses a
        // ConcurrentHashMap dedup guard keyed on (ruleName + message text).
        // putIfAbsent() ensures that even if the same message text somehow
        // triggers both events, the webhook fires exactly once per rule match.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return; // ignore action bar / boss bar overlays
            String plain = com.ihanuat.mod.util.ClientUtils.stripColor(message.getString()).trim();
            if (!plain.isEmpty()) {
                ChatRuleManager.handleChatMessage(net.minecraft.client.Minecraft.getInstance(), plain);
            }
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTime) -> {
            String plain = com.ihanuat.mod.util.ClientUtils.stripColor(message.getString()).trim();
            if (!plain.isEmpty()) {
                ChatRuleManager.handleChatMessage(net.minecraft.client.Minecraft.getInstance(), plain);
            }
        });


        ClientSendMessageEvents.COMMAND.register((command) -> {
            if (command.equalsIgnoreCase("call george")) {
                GeorgeManager.onCallGeorgeSent();
            }
            if (command.equalsIgnoreCase("supercraft")) {
                SuperCrafter.startSuperCraft(Minecraft.getInstance());
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;
            while (configKey.consumeClick())
                client.setScreen(new ClickGui());
            while (startScriptKey.consumeClick()) {
                if (MacroStateManager.getCurrentState() == MacroState.State.OFF) {
                    PestManager.reset();
                    GearManager.reset();
                    GeorgeManager.reset();
                    BookCombineManager.reset();
                    JunkManager.reset();
                    RecoveryManager.reset();
                    QuitThresholdManager.reset(); // re-arm quit threshold for new session
                    MacroStateManager.setCurrentState(MacroState.State.FARMING);
                    ProfitManager.startStartupPriceFetch();
                    ProfitManager.printPetXpPriceDebug(client);
                    DynamicRestManager.scheduleNextRest();
                    MacroWorkerThread.getInstance().submit("StartScript-KeyPress", () -> {
                        try {
                            if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING)) {
                                return;
                            }
                            if (PestPrepSwapManager.prepSwappedForCurrentPestCycle
                                    && WardrobeManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotFarming) {
                                client.execute(
                                        () -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming));
                                MacroWorkerThread.sleep(800);
                                if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING)) {
                                    return;
                                }
                            }

                            ClientUtils.waitForGearAndGui(client);
                            if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING)) {
                                return;
                            }
                            if (PestManager.isCleaningInProgress || PestPrepSwapManager.isPrepSwapping)
                                return;

                            if (MacroConfig.autoRodReturnToFarm) {
                                ClientUtils.sendDebugMessage(client, "Auto Rod: Executing rod cast during startup.");
                                RodManager.executeRodSequence(client);
                            }
                            GearManager.swapToFarmingToolSync(client);
                            
                            if (PestManager.isCleaningInProgress || PestPrepSwapManager.isPrepSwapping)
                                return;

                            com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
                            if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING)) {
                                return;
                            }
                            if (PestManager.isCleaningInProgress || PestPrepSwapManager.isPrepSwapping)
                                return;

                            com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(),
                                    0);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
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

            MacroState.State macroState = MacroStateManager.getCurrentState();
            if (macroState != MacroState.State.OFF
                    && macroState != MacroState.State.RECOVERING
                    && macroState != MacroState.State.FARMING) {
                MacroState.Location currentLocation = ClientUtils.getCurrentLocation(client);
                if (currentLocation != MacroState.Location.GARDEN) {
                    long now = System.currentTimeMillis();
                    if (now - lastUnexpectedRecoveryTriggerMs >= UNEXPECTED_RECOVERY_COOLDOWN_MS) {
                        lastUnexpectedRecoveryTriggerMs = now;
                        client.player.displayClientMessage(
                                Component.literal(
                                        "\u00A7c[Ihanuat] Unexpected location while " + macroState
                                                + " (" + currentLocation + "). Starting recovery..."),
                                false);
                        MacroStateManager.stopMacro(client);
                        MacroStateManager.setCurrentState(MacroState.State.RECOVERING);
                        return;
                    }
                }
            }

            if (client.screen instanceof PauseScreen || client.screen instanceof ChatScreen) {
                if (MacroStateManager.isMacroRunning()) {
                    MacroStateManager.stopMacro(client);
                }
            }

            if (client.screen instanceof AbstractContainerScreen) {
                AbstractContainerScreen<?> currentScreen = (AbstractContainerScreen<?>) client.screen;
                String currentTitle = currentScreen.getTitle().getString().toLowerCase();
                lastScreenWasBoosterCookie = currentTitle.equals("booster cookie");
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
                if (client.screen == currentScreen)
                    SuperCrafter.handleRecipeGui(client, currentScreen);
            } else {
                if (lastScreenWasBoosterCookie) {
                    BoosterCookieManager.onMenuClosed();
                }
                lastScreenWasBoosterCookie = false;
            }

            GeorgeManager.update(client);
            BookCombineManager.update(client);
            JunkManager.update(client);

            DynamicRestManager.update(client);
            QuitThresholdManager.update(client); // check quit threshold each tick
            RestartManager.update(client);
            PestManager.update(client);
            GearManager.cleanupTick(client);
            RotationManager.update(client);
            MacroStateManager.periodicUpdate();
            ProfitManager.update(client);
            com.ihanuat.mod.modules.DiscordStatusManager.update(client);
            com.ihanuat.mod.modules.CropFeverManager.update(client);

            if (PestAotvManager.isSneakingForAotv) {
                if (client.options != null) {
                    client.options.keyShift.setDown(true);
                }
            }

            // Double-tap Space Flight Toggle
            if (PestReturnManager.isStoppingFlight) {
                PestReturnManager.flightStopTicks++;
                switch (PestReturnManager.flightStopStage) {
                    case 0:
                        if (client.options.keyJump != null)
                            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), true);
                        if (PestReturnManager.flightStopTicks >= 2) {
                            PestReturnManager.flightStopStage = 1;
                            PestReturnManager.flightStopTicks = 0;
                        }
                        break;
                    case 1:
                        if (client.options.keyJump != null)
                            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), false);
                        if (PestReturnManager.flightStopTicks >= 3) {
                            PestReturnManager.flightStopStage = 2;
                            PestReturnManager.flightStopTicks = 0;
                        }
                        break;
                    case 2:
                        if (client.options.keyJump != null)
                            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), true);
                        if (PestReturnManager.flightStopTicks >= 2) {
                            PestReturnManager.flightStopStage = 3;
                            PestReturnManager.flightStopTicks = 0;
                        }
                        break;
                    case 3:
                        if (client.options.keyJump != null)
                            net.minecraft.client.KeyMapping.set(client.options.keyJump.getDefaultKey(), false);
                        PestReturnManager.isStoppingFlight = false;
                        break;
                }
            }

            if (MacroStateManager.getCurrentState() == MacroState.State.RECOVERING) {
                RecoveryManager.update(client);
                return;
            }

            // Stash Pickup Logic
            if (MacroConfig.autoStashManager && isPickingUpStash && client.player != null) {
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

                    if (distanceSq <= 1.5 * 1.5) {
                        lastRewarpTime = now;
                        client.player.displayClientMessage(Component.literal("§6Rewarp End Position reached!"), true);
                        MacroWorkerThread.getInstance().submit("PlotTpRewarp", () -> {
                            if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING)) {
                                return;
                            }
                            client.execute(() -> com.ihanuat.mod.util.CommandUtils.stopScript(client, 0));
                            MacroWorkerThread.sleep(300);
                            if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING)) {
                                return;
                            }
                            client.execute(() -> MacroConfig.executePlotTpRewarp(client));
                            MacroWorkerThread.sleep(1200);
                            if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING)) {
                                return;
                            }

                            if (MacroConfig.holdWUntilWall) {
                                client.execute(() -> client.options.keyUp.setDown(true));
                                MacroWorkerThread.sleep(200);
                                long wallTimeout = System.currentTimeMillis() + 5000;
                                double lastX = client.player.getX();
                                double lastZ = client.player.getZ();
                                while (System.currentTimeMillis() < wallTimeout) {
                                    if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING)) {
                                        client.execute(() -> client.options.keyUp.setDown(false));
                                        return;
                                    }
                                    MacroWorkerThread.sleep(100);
                                    double currX = client.player.getX();
                                    double currZ = client.player.getZ();
                                    double moved = Math.sqrt(
                                            (currX - lastX) * (currX - lastX) + (currZ - lastZ) * (currZ - lastZ));
                                    if (moved < 0.03) {
                                        break;
                                    }
                                    lastX = currX;
                                    lastZ = currZ;
                                }
                                client.execute(() -> client.options.keyUp.setDown(false));
                                MacroWorkerThread.sleep(100);
                                if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING)) {
                                    return;
                                }
                            }

                            if (MacroStateManager.getCurrentState() == MacroState.State.FARMING) {
                                client.execute(
                                        () -> com.ihanuat.mod.util.CommandUtils.startScript(client,
                                                MacroConfig.getFullRestartCommand(), 0));
                            }
                        });
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
