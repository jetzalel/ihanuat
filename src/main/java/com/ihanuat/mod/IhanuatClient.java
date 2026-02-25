package com.ihanuat.mod;

import me.shedaniel.clothconfig2.api.ConfigBuilder;

import me.shedaniel.clothconfig2.api.ConfigCategory;

import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.KeyMapping;

import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.screens.Screen;

import net.minecraft.core.BlockPos;

import net.minecraft.network.chat.Component;

import net.minecraft.resources.Identifier;

import net.minecraft.world.phys.Vec3;

import org.lwjgl.glfw.GLFW;

import java.util.List;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import net.minecraft.world.inventory.Slot;

import net.minecraft.world.inventory.ClickType;

public class IhanuatClient implements ClientModInitializer {

    private static KeyMapping configKey;

    // Pest Control State

    private enum MacroState {

        OFF,

        FARMING,

        CLEANING,

        RECOVERING

    }

    private static volatile MacroState currentState = MacroState.OFF;

    // Return Sequence State

    // Return Sequence State

    private enum ReturnState {

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

    private static volatile ReturnState returnState = ReturnState.OFF;

    private static int returnTickCounter = 0;

    private static long lastAspectUsageTime = 0;

    private static int aspectUsageCount = 0;

    private static volatile int flightToggleStage = 0;

    private static volatile int flightToggleTicks = 0;

    private static long losEstablishedTime = 0; // Track how long LOS has been clear

    private static volatile long stateStartTime = 0; // Generic timer for states

    // Return Sequence Look Target

    private static Vec3 returnLookTarget = null;

    // Movement Simulation

    private static boolean isSimulatingMove = false;

    private static Vec3 startMovePos;

    private static final double MOVE_TARGET_DISTANCE = 5.0;

    // Flight Stop Logic

    private static volatile boolean isStoppingFlight = false;

    private static volatile int flightStopStage = 0;

    private static volatile int flightStopTicks = 0;

    // Rotation Logic

    private static boolean isRotating = false;

    private static RotationUtils.Rotation startRot;

    private static RotationUtils.Rotation targetRot;

    private static long rotationStartTime;

    private static long rotationDuration;

    private static long lastRotationUpdateTime = 0;

    // Regex for Pest Control

    private static int tickCounter = 0;

    private static final java.util.regex.Pattern PESTS_ALIVE_PATTERN = java.util.regex.Pattern

            .compile("Alive:\\s*(\\d+)");

    private static final java.util.regex.Pattern PLOTS_PATTERN = java.util.regex.Pattern.compile("Plots:\\s*(\\d+)");

    private static final java.util.regex.Pattern VISITORS_PATTERN = java.util.regex.Pattern

            .compile("Visitors:\\s*\\(?(\\d+)\\)?");

    private static final java.util.regex.Pattern COOLDOWN_PATTERN = java.util.regex.Pattern

            .compile("Cooldown:\\s*(READY|(\\d+)m\\s*(\\d+)s|(\\d+)s)");

    // Stability & Gating

    private static long lastCommandTime = 0;

    private static final long COMMAND_COOLDOWN_MS = 250;

    private static volatile String currentInfestedPlot = null;

    private static volatile boolean isCleaningInProgress = false;

    private static volatile boolean isHandlingMessage = false;

    private static volatile int targetWardrobeSlot = -1;

    private static volatile boolean prepSwappedForCurrentPestCycle = false;

    private static volatile boolean isReturningFromPestVisitor = false;

    private static volatile boolean isProactiveReturnPending = false;

    private static volatile int currentPestSessionId = 0;

    private static volatile long swapSecurityTimer = 0; // Stash Management State
    private static boolean isStashPickupActive = false;
    private static long lastStashPickupTime = 0;
    private static final long STASH_PICKUP_DELAY_MS = 3300;

    private static volatile long swapNullScreenStartTime = 0;

    private static volatile boolean isRestingForDynamicRest = false;

    private static volatile boolean isReconnectingForDynamicRest = false;

    private static volatile boolean intentionalDisconnect = false;

    private static boolean hasCheckedPersistenceOnJoin = false;

    public static boolean isMacroRunning() {

        return currentState != MacroState.OFF;

    }

    public static boolean isIntentionalDisconnect() {

        return intentionalDisconnect;

    }

    // Recovery State

    private static int recoveryFailedAttempts = 0;

    private static long lastRecoveryActionTime = 0;

    private static Location lastRecoveryLocation = Location.UNKNOWN;

    private enum Location {

        GARDEN, HUB, LOBBY, LIMBO, UNKNOWN

    }

    // Plot helper (Deprecated since rollback, but keeping for references)

    private String getCurrentPlot(Minecraft client) {

        return null;

    }

    private static Location getCurrentLocation(Minecraft client) {

        if (client.level == null || client.player == null)

            return Location.UNKNOWN;

        net.minecraft.world.scores.Scoreboard scoreboard = client.level.getScoreboard();

        net.minecraft.world.scores.Objective sidebar = scoreboard != null

                ? scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR)

                : null;

        // Limbo has no sidebar

        if (sidebar == null)

            return Location.LIMBO;

        // Check for Game Menu and My Profile in hotbar for Lobby

        boolean hasLobbyItems = false;

        for (int i = 0; i < 9; i++) {

            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(i);

            if (stack != null && !stack.isEmpty()) {

                String itemName = stack.getHoverName().getString().replaceAll("\u00A7[0-9a-fk-or]", "").trim();

                // "Game Menu" = Main Lobby. "SkyBlock Menu" = SkyBlock. We also check "My

                // Profile".

                if (itemName.contains("Game Menu") || itemName.contains("My Profile")) {

                    hasLobbyItems = true;

                    break;

                }

            }

        }

        if (hasLobbyItems) {

            return Location.LOBBY;

        }

        // Parse Tab List for Area:

        if (client.getConnection() != null) {

            java.util.Collection<net.minecraft.client.multiplayer.PlayerInfo> players = client.getConnection()

                    .getListedOnlinePlayers();

            for (net.minecraft.client.multiplayer.PlayerInfo info : players) {

                String name = "";

                if (info.getTabListDisplayName() != null) {

                    name = info.getTabListDisplayName().getString();

                } else if (info.getProfile() != null) {

                    name = String.valueOf(info.getProfile());

                }

                String clean = name.replaceAll("\u00A7[0-9a-fk-or]", "").trim();

                if (clean.contains("Area: Garden"))

                    return Location.GARDEN;

                if (clean.contains("Area:")) {

                    return Location.HUB; // If we see "Area:" and it's not Garden, we treat it as HUB so it sends /warp

                    // garden

                }

            }

        }

        // If sidebar exists but we didn't match the others, just fallback to HUB to try

        // /warp garden

        return Location.HUB;

    }

    // Delayed Restart State

    private static boolean isRestartPending = false;

    private static long restartExecutionTime = 0;

    private static int restartSequenceStage = 0;

    private static long nextRestartActionTime = 0;

    private static long getContestRemainingMs(Minecraft client) {

        if (client.level == null || client.player == null)

            return 0;

        net.minecraft.world.scores.Scoreboard scoreboard = client.level.getScoreboard();

        if (scoreboard == null)

            return 0;

        net.minecraft.world.scores.Objective sidebar = scoreboard

                .getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);

        if (sidebar == null)

            return 0;

        java.util.Collection<net.minecraft.world.scores.PlayerScoreEntry> scores = scoreboard.listPlayerScores(sidebar);

        boolean foundContestHeader = false;

        // Iterate through scores (these are rendered bottom-up or top-down depending on

        // iteration, but we just look for the header then the timer)

        // Since we want the line physically *below* the header, and scoreboard entries

        // are sorted by score, we'll just extract all lines to an ordered list first.

        java.util.List<String> lines = new java.util.ArrayList<>();

        for (net.minecraft.world.scores.PlayerScoreEntry entry : scores) {

            String entryName = entry.owner();

            net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayersTeam(entryName);

            String fullText = entryName;

            if (team != null) {

                fullText = team.getPlayerPrefix().getString() + entryName + team.getPlayerSuffix().getString();

            }

            lines.add(fullText.replaceAll("\u00A7[0-9a-fk-or]", "").trim());

        }

        // Reverse to match visual order (top-down)

        java.util.Collections.reverse(lines);

        for (int i = 0; i < lines.size(); i++) {

            String line = lines.get(i);

            if (line.contains("Jacob's Contest")) {

                if (i + 1 < lines.size()) {

                    String timeLine = lines.get(i + 1); // e.g. "Mushroom 10m50s"

                    // Regex to extract optional minutes and seconds: (?:(\d+)m\s*)?(?:(\d+)s)?

                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:(\\d+)m\\s*)?(?:(\\d+)s)?$")

                            .matcher(timeLine);

                    if (m.find()) {

                        long ms = 0;

                        if (m.group(1) != null)

                            ms += Long.parseLong(m.group(1)) * 60000L;

                        if (m.group(2) != null)

                            ms += Long.parseLong(m.group(2)) * 1000L;

                        return ms;

                    }

                }

                break; // Found header but couldn't parse time

            }

        }

        return 0;

    }

    // Dynamic Rest Tracking

    private static long nextRestTriggerMs = 0;

    private static void forceReleaseKeys(Minecraft client) {

        if (client.options != null) {

            client.options.keyUp.setDown(false);

            client.options.keyDown.setDown(false);

            client.options.keyLeft.setDown(false);

            client.options.keyRight.setDown(false);

            client.options.keyJump.setDown(false);

            client.options.keyShift.setDown(false);

            client.options.keyAttack.setDown(false);

            client.options.keyUse.setDown(false);

        }

        if (client.mouseHandler != null) {

            client.mouseHandler.releaseMouse();

        }

    }

    private static volatile boolean isSwappingWardrobe = false;

    private static volatile long wardrobeInteractionTime = 0;

    private static volatile int wardrobeInteractionStage = 0;

    private static volatile boolean shouldRestartFarmingAfterSwap = false;

    private static volatile long wardrobeOpenPendingTime = 0;

    private static volatile int wardrobeCleanupTicks = 0;
    private static volatile int trackedWardrobeSlot = -1; // -1 = Unknown, 1 = Farming, 2 = Pest

    private static volatile boolean isStartingFlight = false;

    // Equipment Swap Logic

    private static volatile boolean isSwappingEquipment = false;

    private static volatile int equipmentInteractionStage = 0;

    private static volatile long equipmentInteractionTime = 0;

    private static volatile boolean swappingToFarmingGear = false;

    private static volatile int equipmentTargetIndex = 0; // 0-3 for the 4 pieces
    private static volatile Boolean trackedIsPestGear = null; // null = Unknown, true = Pest, false = Farming

    @Override

    public void onInitializeClient() {

        // Load Config

        MacroConfig.load();

        // Check for persisted rest state

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (client.player == null) {

                hasCheckedPersistenceOnJoin = false;

                if (client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {

                    long reconnectAt = RestStateManager.loadReconnectTime();

                    if (reconnectAt > 0) {

                        long remaining = reconnectAt - java.time.Instant.now().getEpochSecond();

                        if (remaining <= 0) {

                            // Time already passed, reconnect now with small jitter

                            ReconnectScheduler.scheduleReconnect(5, RestStateManager.shouldResume());

                        } else if (!ReconnectScheduler.isPending()) {

                            // Resume the countdown

                            ReconnectScheduler.scheduleReconnect(remaining, RestStateManager.shouldResume());

                        }

                    }

                }

            } else if (!hasCheckedPersistenceOnJoin) {

                // If we have a player and haven't checked join state yet

                long reconnectAt = RestStateManager.loadReconnectTime();

                if (reconnectAt != 0) {

                    if (RestStateManager.shouldResume()) {

                        client.player.displayClientMessage(

                                Component
                                        .literal(
                                                "\u00A76[Ihanuat] Session persistence detected! Initializing recovery..."),

                                false);

                        currentState = MacroState.RECOVERING;

                        lastRecoveryActionTime = System.currentTimeMillis() + 2000; // Give it 2s to load before first

                        // pulse

                    }

                    RestStateManager.clearState();

                }

                hasCheckedPersistenceOnJoin = true;

            }

        });

        KeyMapping.Category category = new KeyMapping.Category(Identifier.fromNamespaceAndPath("ihanuat", "main"));

        configKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(

                "key.ihanuat.config",

                GLFW.GLFW_KEY_O,

                category));

        KeyMapping startScriptKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(

                "key.ihanuat.start_script",

                GLFW.GLFW_KEY_K,

                category));

        KeyMapping returnKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ihanuat.return_to_start",
                GLFW.GLFW_KEY_R,
                category));

        // Centralized Chat Listener

        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {

            if (isHandlingMessage)

                return;

            try {

                isHandlingMessage = true;

                String text = message.getString();

                String lowerText = text.toLowerCase();

                // 0. Server Restart Checks

                if ((lowerText.contains("server") && lowerText.contains("restart")) ||

                        text.contains("Evacuating to Hub...") ||

                        text.contains("SERVER REBOOT!") ||

                        lowerText.contains("proxy restart")) {

                    if (currentState != MacroState.OFF && currentState != MacroState.RECOVERING

                            && !isRestartPending) {

                        long contestMs = getContestRemainingMs(Minecraft.getInstance());

                        if (contestMs > 0) {

                            Minecraft.getInstance().player.displayClientMessage(

                                    Component.literal(

                                            "\u00A7c[Ihanuat] Server restart detected! Delaying abort until Jacob's contest ends..."),

                                    false);

                            restartExecutionTime = System.currentTimeMillis() + contestMs + 10000; // 10s grace period

                        } else {

                            Minecraft.getInstance().player.displayClientMessage(

                                    Component.literal(

                                            "\u00A7c[Ihanuat] Server restart/evacuation detected! Initiating abort sequence..."),

                                    false);

                            restartExecutionTime = System.currentTimeMillis();

                        }

                        isRestartPending = true;

                        restartSequenceStage = 0;

                    }

                    return; // Prevent further processing

                }

                // 0.5 Limbo/Lobby Disconnect Checks

                if (text.contains("You were spawned in Limbo.") ||

                        text.contains(

                                "A disconnect occurred in your connection, so you were put in the SkyBlock Lobby!")) {

                    if (currentState != MacroState.OFF && currentState != MacroState.RECOVERING) {

                        Minecraft.getInstance().player.displayClientMessage(

                                Component.literal(

                                        "\u00A7c[Ihanuat] Disconnect detected! Starting recovery sequence..."),

                                false);

                        sendCommand(Minecraft.getInstance(), ".ez-stopscript");

                        forceReleaseKeys(Minecraft.getInstance());

                        isRestartPending = false; // Override any pending restarts

                        currentState = MacroState.RECOVERING;

                        recoveryFailedAttempts = 0;

                        lastRecoveryActionTime = System.currentTimeMillis(); // Start with a 5s delay

                        lastRecoveryLocation = Location.UNKNOWN;

                    }

                    return; // Prevent further processing

                }

                // 1. Bonus/Barn Sync

                // 3. Pest Cleaning Finished

                if (text.contains("Pest Cleaner") && text.contains("Finished")) {

                    if (currentState == MacroState.CLEANING) {

                        handlePestCleaningFinished(Minecraft.getInstance());

                    }

                }

                // 4. Visitor Script Finished

                if (currentState == MacroState.CLEANING && lowerText.contains("visitor")

                        && lowerText.contains("finished")

                        && !text.contains("sequence complete")) {

                    handleVisitorScriptFinished(Minecraft.getInstance());

                }

                if (returnState != ReturnState.OFF && lowerText.contains("fell into the void")) {
                    returnState = ReturnState.TP_START;
                    returnTickCounter = 0;
                    flightToggleStage = 0;
                    flightToggleTicks = 0;
                    isStartingFlight = false;
                    isStoppingFlight = false;
                    forceReleaseKeys(Minecraft.getInstance());
                }

                // 5. Stash Manager Triggers
                if (MacroConfig.autoStashManager) {
                    if (text.contains("You picked up all items from your material stash") ||
                            text.contains("Your stash isn't holding any items or materials!")) {
                        isStashPickupActive = false;
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("\u00A7c[Ihanuat] Stash empty. Manager deactivated."), true);
                    } else if (text.contains("materials stashed away!") || text.contains("materials in stash,")) {
                        isStashPickupActive = true;
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("\u00A7d[Ihanuat] Items detected in stash! Activating Manager..."),
                                true);
                    }
                }
            } finally {

                isHandlingMessage = false;

            }

        });

        // Config & Script Toggle Keys (Start Tick)

        ClientTickEvents.START_CLIENT_TICK.register(client ->

        {

            if (client.player == null)

                return;

            while (configKey.consumeClick()) {

                client.setScreen(createConfigScreen(client.screen));

            }

            while (returnKey.consumeClick()) {
                if (client.player != null) {
                    returnState = ReturnState.TP_START;
                    returnTickCounter = 0;
                    currentState = MacroState.OFF;
                }
            }

            while (startScriptKey.consumeClick()) {

                if (currentState == MacroState.OFF) {

                    currentState = MacroState.FARMING;

                    // Generate Next Rest Trigger Time

                    if (nextRestTriggerMs == 0) {

                        int base = MacroConfig.restScriptingTime;

                        int offset = MacroConfig.restScriptingTimeOffset;

                        int randomOffset = (offset > 0) ? (new java.util.Random().nextInt(offset * 2 + 1) - offset) : 0;

                        int finalSeconds = base + randomOffset;

                        nextRestTriggerMs = System.currentTimeMillis() + (finalSeconds * 60L * 1000L);

                        long minutes = finalSeconds;

                        long seconds = 0;

                        client.player.displayClientMessage(

                                Component.literal(
                                        String.format("\u00A7b[Ihanuat] Next Dynamic Rest scheduled in %02d:%02d",

                                                minutes, seconds)),

                                false);

                    }

                    new Thread(() -> {

                        try {

                            if (client.getConnection() != null) {

                                // Only swap to Slot 1 if we're not already sure we're in it

                                // or if we know we previously swapped away.

                                if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE
                                        && prepSwappedForCurrentPestCycle
                                        && trackedWardrobeSlot != MacroConfig.wardrobeSlotFarming) {

                                    client.execute(() -> ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming));

                                    Thread.sleep(800); // Wait for swap

                                }

                                client.execute(() -> {

                                    swapToFarmingTool(client);
                                    sendCommand(client, MacroConfig.restartScript);

                                });

                            }

                        } catch (Exception e) {

                            e.printStackTrace();

                        }

                    }).start();

                    client.player.displayClientMessage(Component.literal("\u00A7aMacro Started: Farming Mode"), true);

                } else {

                    stopMacro(client);

                }

            }

        });

        // Main Tick Loop (Pest Control, Rotation Logic, Movement)

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            // Check if Pause Menu or Chat is open to stop macro

            if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen ||

                    client.screen instanceof net.minecraft.client.gui.screens.ChatScreen) {

                if (currentState != MacroState.OFF || returnState != ReturnState.OFF) {

                    stopMacro(client);

                }

            }

            if (client.screen instanceof AbstractContainerScreen) {

                handleWardrobeMenu(client, (AbstractContainerScreen<?>) client.screen);

            }

            if (client.screen instanceof AbstractContainerScreen) {

                handleEquipmentMenu(client, (AbstractContainerScreen<?>) client.screen);

            }

            // --- Swap Safety Reset ---

            if (isSwappingWardrobe || isSwappingEquipment) {

                if (swapSecurityTimer == 0) {

                    swapSecurityTimer = System.currentTimeMillis();

                } else if (System.currentTimeMillis() - swapSecurityTimer > 7000) {

                    // Force reset after 7 seconds of being stuck

                    isSwappingWardrobe = false;

                    isSwappingEquipment = false;

                    swapSecurityTimer = 0;

                }

                // Proactive Screen-Missing Check (e.g. menu closed but macro didn't see it)

                if (client.screen == null) {

                    if (swapNullScreenStartTime == 0) {

                        swapNullScreenStartTime = System.currentTimeMillis();

                    } else if (System.currentTimeMillis() - swapNullScreenStartTime > 1200) {

                        isSwappingWardrobe = false;

                        isSwappingEquipment = false;

                    }

                } else {

                    swapNullScreenStartTime = 0;

                }

            } else {

                swapSecurityTimer = 0;

                swapNullScreenStartTime = 0;

            }

            // --- Dynamic Rest Screen Watchdog ---

            if (isRestingForDynamicRest) {

                if (client.player != null) {

                    // Player is back in game, stop resting

                    isRestingForDynamicRest = false;

                } else if (client.screen == null || !(client.screen instanceof DynamicRestScreen)) {

                    // Re-apply the rest screen if it was wiped by the default disconnect screen

                    long reconnectAt = RestStateManager.loadReconnectTime();

                    if (reconnectAt > 0) {

                        client.setScreen(new DynamicRestScreen(reconnectAt * 1000L));

                    }

                }

            } else {

                isReconnectingForDynamicRest = false;

            }

            // --- Stash Manager Tick Logic ---
            if (MacroConfig.autoStashManager && isStashPickupActive && client.player != null) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastStashPickupTime >= STASH_PICKUP_DELAY_MS) {
                    lastStashPickupTime = currentTime;
                    client.player.connection.sendCommand("pickupstash");
                }
            } else if (!MacroConfig.autoStashManager) {
                isStashPickupActive = false;
            }

            // Delayed Restart Pending

            if (isRestartPending && currentState == MacroState.FARMING) {

                if (restartSequenceStage == 0 && System.currentTimeMillis() >= restartExecutionTime) {

                    client.player.displayClientMessage(

                            Component.literal("\u00A7c[Ihanuat] Executing delayed restart abort sequence..."), false);

                    sendCommand(client, ".ez-stopscript");

                    forceReleaseKeys(client);

                    client.player.connection.sendChat("/setspawn");

                    restartSequenceStage = 1;

                    nextRestartActionTime = System.currentTimeMillis() + 5000; // Wait 5s before /hub

                } else if (restartSequenceStage == 1 && System.currentTimeMillis() >= nextRestartActionTime) {

                    client.player.connection.sendChat("/hub");

                    restartSequenceStage = 2;

                    nextRestartActionTime = System.currentTimeMillis() + 10000; // Wait 10s before Recovery

                } else if (restartSequenceStage == 2 && System.currentTimeMillis() >= nextRestartActionTime) {

                    sendCommand(client, ".ez-stopscript"); // Just in case

                    currentState = MacroState.RECOVERING;

                    recoveryFailedAttempts = 0;

                    lastRecoveryActionTime = System.currentTimeMillis(); // Start recovery with 5s delay

                    lastRecoveryLocation = Location.UNKNOWN;

                    restartSequenceStage = 0;

                    isRestartPending = false;

                }

            }

            // Auto-Recovery Sequence Check

            if (currentState == MacroState.RECOVERING) {

                if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {

                    return; // Don't do anything if paused

                }

                if (System.currentTimeMillis() - lastRecoveryActionTime < 5000) {

                    return; // Wait 5 seconds between actions

                }

                Location currentLoc = getCurrentLocation(client);

                // Reset failed attempts if location successfully changed

                if (currentLoc != lastRecoveryLocation) {

                    recoveryFailedAttempts = 0;

                    lastRecoveryLocation = currentLoc;

                }

                if (recoveryFailedAttempts >= 15) {

                    client.player.displayClientMessage(

                            Component.literal(
                                    "\u00A7c[Ihanuat] Auto-Recovery failed after 15 attempts. Stopping macro."),

                            false);

                    stopMacro(client);

                    return;

                }

                lastRecoveryActionTime = System.currentTimeMillis();

                recoveryFailedAttempts++;

                switch (currentLoc) {

                    case LIMBO:

                        client.player.displayClientMessage(

                                Component.literal("\u00A7e[Ihanuat] Recovery (Attempt " + recoveryFailedAttempts

                                        + "): Warping to Lobby from Limbo..."),

                                false);

                        client.player.connection.sendChat("/lobby");

                        break;

                    case LOBBY:

                        client.player.displayClientMessage(

                                Component.literal("\u00A7e[Ihanuat] Recovery (Attempt " + recoveryFailedAttempts

                                        + "): Warping to SkyBlock from Lobby..."),

                                false);

                        client.player.connection.sendChat("/skyblock");

                        break;

                    case HUB:

                    case UNKNOWN:

                        client.player.displayClientMessage(

                                Component.literal("\u00A7e[Ihanuat] Recovery (Attempt " + recoveryFailedAttempts

                                        + "): Warping to Garden..."),

                                false);

                        client.player.connection.sendChat("/warp garden");

                        break;

                    case GARDEN:

                        client.player.displayClientMessage(

                                Component.literal("\u00A7a[Ihanuat] Recovery Successful! Resuming Farming..."), false);

                        recoveryFailedAttempts = 0;

                        currentState = MacroState.FARMING;

                        swapToFarmingTool(client);
                        client.player.displayClientMessage(
                                Component.literal("\u00A7d[Ihanuat] Resuming via RecoveryHandler"), false);
                        sendCommand(client, MacroConfig.restartScript);

                        break;

                }

                return; // Block other tick logic while recovering

            }

            // Automated Return to Start (Distance-based)

            if (currentState == MacroState.FARMING && returnState == ReturnState.OFF && MacroConfig.endPos != null) {

                Vec3 endVec = new Vec3(MacroConfig.endPos.getX() + 0.5, MacroConfig.endPos.getY(),

                        MacroConfig.endPos.getZ() + 0.5);

                if (client.player.position().distanceTo(endVec) <= 1.0) {

                    client.player.displayClientMessage(

                            Component.literal("\u00A76End Position reached. Stopping script..."),

                            true);

                    // Immediately stop script

                    sendCommand(client, ".ez-stopscript");

                    // 250ms pre-sequence wait

                    returnState = ReturnState.TP_PRE_WAIT;

                    stateStartTime = System.currentTimeMillis();

                }

            }

            // Delayed Wardrobe Open after StopScript

            if (wardrobeOpenPendingTime != 0 && System.currentTimeMillis() - wardrobeOpenPendingTime >= 200) {

                if (client.player != null) {

                    client.player.connection.sendChat("/wardrobe");

                }

                wardrobeOpenPendingTime = 0;

            }

            // Delayed StartScript after Wardrobe Swap

            if (shouldRestartFarmingAfterSwap && !isSwappingWardrobe && client.screen == null
                    && returnState == ReturnState.OFF) {

                if (wardrobeInteractionTime != 0 && System.currentTimeMillis() - wardrobeInteractionTime >= 500) {

                    swapToFarmingTool(client);

                    if (isInEndRow(client)) {

                        client.player.displayClientMessage(

                                Component.literal(

                                        "\u00A7cDetected same row as End Position after swap! Triggering early return..."),

                                true);

                        isCleaningInProgress = false;

                        returnState = ReturnState.TP_START;

                        returnTickCounter = 0;

                        currentState = MacroState.OFF; // Ensure farming doesn't restart

                    } else {

                        if (isRestartPending && System.currentTimeMillis() >= restartExecutionTime) {

                            client.player.displayClientMessage(

                                    Component.literal(

                                            "\u00A7c[Ihanuat] Aborting farm resume due to pending Server Restart!"),

                                    true);

                            currentState = MacroState.FARMING;

                            isCleaningInProgress = false;

                        } else {

                            swapToFarmingTool(client);

                            sendCommand(client, MacroConfig.restartScript);

                            currentState = MacroState.FARMING;

                            isCleaningInProgress = false; // Reset flag after swap logic finish

                        }

                    }

                    shouldRestartFarmingAfterSwap = false;

                    wardrobeInteractionTime = 0;

                    wardrobeCleanupTicks = 10; // Run cleanup for 10 ticks (0.5s)

                }

            }

            // Post-interaction Cleanup (Fixes inventory locks/phantom items)

            if (wardrobeCleanupTicks > 0) {

                wardrobeCleanupTicks--;

                if (client.player != null) {

                    try {

                        if (client.player.containerMenu != null) {

                            client.player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);

                            client.player.containerMenu.broadcastChanges();

                        }

                        if (client.player.inventoryMenu != null) {

                            client.player.inventoryMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);

                            client.player.inventoryMenu.broadcastChanges();

                        }

                        // Explicitly send a Close Container packet to ensure the server is in sync

                        client.player.connection

                                .send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(0));

                    } catch (Exception ignored) {

                    }

                }

                if (client.mouseHandler != null) {

                    client.mouseHandler.releaseMouse();

                }

            }

            // Return Sequence Logic

            if (returnState != ReturnState.OFF) {

                handleReturnSequence(client);

            }

            // Pest Control Logic

            if (currentState == MacroState.FARMING) {

                tickCounter++;

                if (tickCounter >= 20) { // Check every second

                    tickCounter = 0;

                    checkTabListForPests(client);

                }

            }

            // Flight Stop Logic (Tick-based)

            if (isStoppingFlight) {
                if (client.player != null && !client.player.getAbilities().flying) {
                    isStoppingFlight = false;
                    flightStopStage = 0;
                    flightStopTicks = 0;
                    if (client.options.keyJump != null)
                        KeyMapping.set(client.options.keyJump.getDefaultKey(), false);
                    return;
                }
                flightStopTicks++;
                Minecraft mc = client;
                if (mc.options.keyJump != null) {

                    // Stage 0: Press Space

                    if (flightStopStage == 0) {

                        KeyMapping.set(mc.options.keyJump.getDefaultKey(), true);

                        if (flightStopTicks >= 2) {

                            flightStopStage = 1;

                            flightStopTicks = 0;

                        }

                    }

                    // Stage 1: Release Space

                    else if (flightStopStage == 1) {

                        KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);

                        if (flightStopTicks >= 3) {

                            flightStopStage = 2;

                            flightStopTicks = 0;

                        }

                    }

                    // Stage 2: Press Space

                    else if (flightStopStage == 2) {

                        KeyMapping.set(mc.options.keyJump.getDefaultKey(), true);

                        if (flightStopTicks >= 2) {

                            flightStopStage = 3;

                            flightStopTicks = 0;

                        }

                    }

                    // Stage 3: Release Space and Finish

                    else if (flightStopStage == 3) {

                        KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);

                        isStoppingFlight = false;

                        flightStopStage = 0;

                        flightStopTicks = 0;

                        client.player.displayClientMessage(Component.literal("\u00A7bFlight stop sequence finished."),

                                true);

                    }

                }

            }

            // Flight Start Logic (Tick-based double jump)

            if (currentState != MacroState.OFF && isStartingFlight) {
                if ((client.player != null && client.player.getAbilities().flying)
                        || performFlightToggle(client, true)) {
                    isStartingFlight = false;
                    flightToggleStage = 0;
                    flightToggleTicks = 0;
                    if (client.options.keyJump != null)
                        KeyMapping.set(client.options.keyJump.getDefaultKey(), false);
                }
            }

        });

        // Command Registration

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            dispatcher.register(

                    ClientCommandManager.literal("setpos")

                            .then(ClientCommandManager.literal("start").executes(context -> {

                                Minecraft client = Minecraft.getInstance();

                                if (client.player != null) {

                                    MacroConfig.startPos = client.player.blockPosition();

                                    String plot = PlotUtils.getPlotName(client);

                                    plot = plot.toLowerCase().replaceAll("plot", "").trim();

                                    MacroConfig.startPlot = plot;

                                    context.getSource()

                                            .sendFeedback(Component.literal(

                                                    "\u00A7aStart position set to: \u00A7f"

                                                            + MacroConfig.startPos.toShortString()

                                                            + " \u00A77(Automatic Warp Plot: \u00A7e"
                                                            + MacroConfig.startPlot

                                                            + "\u00A77)"));

                                }

                                return 1;

                            })).then(ClientCommandManager.literal("end").executes(context -> {

                                Minecraft client = Minecraft.getInstance();

                                if (client.player != null) {

                                    MacroConfig.endPos = client.player.blockPosition();

                                    String plot = PlotUtils.getPlotName(client);

                                    plot = plot.toLowerCase().replaceAll("plot", "").trim();

                                    MacroConfig.endPlot = plot;

                                    context.getSource()

                                            .sendFeedback(Component.literal(

                                                    "\u00A7cEnd position set to: \u00A7f"
                                                            + MacroConfig.endPos.toShortString()

                                                            + " \u00A77(Plot: " + MacroConfig.endPlot + ")"));

                                }

                                return 1;

                            })));

        });

    }

    // Return Sequence State

    public static void updateRotation() {

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)

            return;

        // Priority 1: Time-Based Linear Rotation

        if (isRotating && startRot != null && targetRot != null) {

            long currentTime = System.currentTimeMillis();

            long elapsed = currentTime - rotationStartTime;

            float t = (float) elapsed / (float) rotationDuration;

            if (t >= 1.0f) {

                t = 1.0f;

                isRotating = false;

                // mc.player.displayClientMessage(Component.literal("\u00A7aRotation
                // complete."),

                // true);

                // If we finished a rotation, ensure we snap to the final target look

                // This prevents a 1-frame jitter if the next state hasn't set returnLookTarget

                // yet

                if (returnState == ReturnState.ALIGN_WAIT || returnState == ReturnState.FLY_APPROACH) {

                    // Keep looking at the final target point for this frame

                }

            }

            float currentYaw = startRot.yaw + (targetRot.yaw - startRot.yaw) * t;

            float currentPitch = startRot.pitch + (targetRot.pitch - startRot.pitch) * t;

            mc.player.setYRot(currentYaw);

            mc.player.setXRot(currentPitch);

            // Snap for interpolation too just in case

            mc.player.yRotO = currentYaw;

            mc.player.xRotO = currentPitch;

            return;

        }

        // Priority 2: Return Sequence Tracking (Time-Based Smooth)

        // Smoothly rotates toward returnLookTarget at rotationSpeed deg/s.

        // Non-blocking: does NOT gate movement.

        if (returnState != ReturnState.OFF && returnLookTarget != null) {

            RotationUtils.Rotation target = RotationUtils.calculateLookAt(mc.player.getEyePosition(), returnLookTarget);

            // Time-based: compute how many degrees we can move this frame

            long now = System.currentTimeMillis();

            float deltaSeconds = (lastRotationUpdateTime == 0) ? (1.0f / 60.0f)

                    : (now - lastRotationUpdateTime) / 1000.0f;

            deltaSeconds = Math.min(deltaSeconds, 0.1f); // Cap to prevent teleport on lag spike

            lastRotationUpdateTime = now;

            float maxStep = (float) MacroConfig.rotationSpeed * deltaSeconds;

            float curYaw = mc.player.getYRot();

            float curPitch = mc.player.getXRot();

            float yawDiff = net.minecraft.util.Mth.wrapDegrees(target.yaw - curYaw);

            float pitchDiff = target.pitch - curPitch;

            float totalDiff = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

            float newYaw, newPitch;

            if (totalDiff <= maxStep || totalDiff < 0.1f) {

                newYaw = curYaw + yawDiff;

                newPitch = target.pitch;

            } else {

                float ratio = maxStep / totalDiff;

                newYaw = curYaw + yawDiff * ratio;

                newPitch = curPitch + pitchDiff * ratio;

            }

            mc.player.setYRot(newYaw);

            mc.player.setXRot(newPitch);

            mc.player.yRotO = newYaw;

            mc.player.xRotO = newPitch;

            return;

        }

    }

    // Called by MixinMouseHandler to suppress mouse rotation during return sequence

    public static boolean shouldSuppressMouseRotation() {

        return returnState != ReturnState.OFF && (isRotating || returnLookTarget != null);

    }

    private static void useAspectItem() {

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)

            return;

        // Search hotbar (0-8)

        for (int i = 0; i < 9; i++) {

            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(i);

            String name = stack.getHoverName().getString().toLowerCase();

            if (name.contains("aspect of the")) {

                // Found it!

                // Found it!

                ((com.ihanuat.mod.mixin.AccessorInventory) mc.player.getInventory()).setSelected(i);

                mc.player.displayClientMessage(
                        Component.literal("\u00A7dAspect Teleport! Switching to slot " + (i + 1)),

                        true);

                // Simulate Right Click

                if (mc.gameMode != null) {

                    mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);

                }

                return;

            }

        }

        mc.player.displayClientMessage(Component.literal("\u00A7c'Aspect of the' item not found in hotbar!"), true);

    }

    private int getVisitorCount(Minecraft client) {

        if (!MacroConfig.autoVisitor)

            return 0;

        if (client.level == null)

            return 0;

        try {

            // Check Tab List (Hypixel often puts it here)

            if (client.getConnection() != null) {

                java.util.Collection<net.minecraft.client.multiplayer.PlayerInfo> players = client.getConnection()

                        .getListedOnlinePlayers();

                for (net.minecraft.client.multiplayer.PlayerInfo info : players) {

                    String name = "";

                    if (info.getTabListDisplayName() != null) {

                        name = info.getTabListDisplayName().getString();

                    } else if (info.getProfile() != null) {

                        // Fallback to toString() which contains name, to avoid symbol errors

                        name = String.valueOf(info.getProfile());

                    }

                    String clean = name.replaceAll("\u00A7[0-9a-fk-or]", "").trim();

                    java.util.regex.Matcher m = VISITORS_PATTERN.matcher(clean);

                    if (m.find()) {

                        return Integer.parseInt(m.group(1));

                    }

                }

            }

        } catch (Exception e) {

            e.printStackTrace();

        }

        return 0;

    }

    private void swapToFarmingTool(Minecraft client) {

        if (client.player == null)

            return;

        String[] keywords = { "hoe", "dicer", "knife", "chopper", "cutter" };

        // Scan 0-8

        for (int i = 0; i < 9; i++) {

            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(i);

            String name = stack.getHoverName().getString().toLowerCase();

            for (String kw : keywords) {

                if (name.contains(kw)) {

                    ((com.ihanuat.mod.mixin.AccessorInventory) client.player.getInventory()).setSelected(i);

                    client.player.displayClientMessage(Component.literal("\u00A7aEquipped Farming Tool: " + name),
                            true);

                    return;

                }

            }

        }

        client.player.displayClientMessage(Component.literal("\u00A7cNo farming tool found in hotbar!"), true);

    }

    private void checkTabListForPests(Minecraft client) {

        if (client.getConnection() == null || isCleaningInProgress)

            return;

        int aliveCount = -1;

        java.util.Set<String> infestedPlots = new java.util.HashSet<>();

        for (net.minecraft.client.multiplayer.PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {

            String name = "";

            if (info.getTabListDisplayName() != null) {

                name = info.getTabListDisplayName().getString();

            } else if (info.getProfile() != null) {

                name = String.valueOf(info.getProfile());

            }

            String clean = name.replaceAll("\u00A7[0-9a-fk-or]", "").trim();

            java.util.regex.Matcher aliveMatcher = PESTS_ALIVE_PATTERN.matcher(clean);

            if (aliveMatcher.find()) {

                try {

                    aliveCount = Integer.parseInt(aliveMatcher.group(1));

                } catch (NumberFormatException ignored) {

                }

            }

            java.util.regex.Matcher cooldownMatcher = COOLDOWN_PATTERN.matcher(clean);

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

                // Mirroring Option 2 reset logic separately for both pathways
                if (MacroConfig.autoEquipment) {
                    if (cooldownSeconds > 170 && prepSwappedForCurrentPestCycle && !isCleaningInProgress) {
                        prepSwappedForCurrentPestCycle = false;
                    }
                } else {
                    // For the 8s version, reset above 8s to allow next cycle
                    if (cooldownSeconds > 8 && prepSwappedForCurrentPestCycle && !isCleaningInProgress) {
                        prepSwappedForCurrentPestCycle = false;
                    }
                }

                if (currentState == MacroState.FARMING && cooldownSeconds != -1
                        && cooldownSeconds > 0 && !isCleaningInProgress && !prepSwappedForCurrentPestCycle
                        && returnState == ReturnState.OFF) {

                    boolean shouldEquipSoon = MacroConfig.autoEquipment && cooldownSeconds <= 180;
                    boolean shouldWardrobeSoon = (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE
                            && cooldownSeconds <= 15);

                    if ((shouldEquipSoon || shouldWardrobeSoon) && isInEndRow(client)) {
                        client.player.displayClientMessage(
                                Component.literal("\u00A7eProactive Return: Low Cooldown & End Row."), true);
                        new Thread(() -> {
                            try {
                                currentState = MacroState.OFF; // End farming immediately
                                sendCommand(client, ".ez-stopscript");
                                Thread.sleep(500);
                                synchronized (IhanuatClient.class) {
                                    isCleaningInProgress = false;
                                    isProactiveReturnPending = true; // Mark for landing swap
                                    shouldRestartFarmingAfterSwap = false;
                                    returnState = ReturnState.TP_START;
                                    returnTickCounter = 0;
                                }
                            } catch (Exception ignored) {
                            }
                        }).start();
                        return;
                    }
                }
                if (currentState == MacroState.FARMING && cooldownSeconds != -1
                        && cooldownSeconds > 0 && !prepSwappedForCurrentPestCycle
                        && !isCleaningInProgress) {

                    if (MacroConfig.autoEquipment) {
                        // 2m 50s version (Option 2)
                        if (cooldownSeconds <= 170) {
                            synchronized (IhanuatClient.class) {
                                if (prepSwappedForCurrentPestCycle || isCleaningInProgress)
                                    return;
                                prepSwappedForCurrentPestCycle = true;
                            }
                            client.player.displayClientMessage(Component
                                    .literal("\u00A7ePest cooldown detected (<= 170s). Triggering prep-swap..."), true);
                            new Thread(() -> {
                                try {
                                    if (isCleaningInProgress)
                                        return;
                                    sendCommand(client, ".ez-stopscript");
                                    Thread.sleep(375);
                                    if (isCleaningInProgress)
                                        return;
                                    ensureEquipment(client, false); // Option 2
                                    Thread.sleep(375);
                                    while (isSwappingEquipment && !isCleaningInProgress)
                                        Thread.sleep(50);
                                    Thread.sleep(250);
                                    if (isCleaningInProgress)
                                        return;
                                    if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.ROD) {
                                        executeRodSequence(client);
                                    } else if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE) {
                                        triggerWardrobeSwap(client);
                                    } else {
                                        resumeAfterPrepSwap(client);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                    } else {
                        // 8s version (Independent refined version)
                        if (cooldownSeconds <= 8) {
                            synchronized (IhanuatClient.class) {
                                if (prepSwappedForCurrentPestCycle || isCleaningInProgress)
                                    return;
                                prepSwappedForCurrentPestCycle = true;
                            }
                            client.player.displayClientMessage(
                                    Component.literal("\u00A7ePest cooldown detected (<= 8s). Triggering prep-swap..."),
                                    true);
                            new Thread(() -> {
                                try {
                                    if (isCleaningInProgress)
                                        return;
                                    sendCommand(client, ".ez-stopscript");
                                    Thread.sleep(375);
                                    if (isCleaningInProgress)
                                        return;
                                    if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.ROD) {
                                        executeRodSequence(client);
                                    } else if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE) {
                                        triggerWardrobeSwap(client);
                                    } else {
                                        resumeAfterPrepSwap(client);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                    }
                }

            }

            if (clean.contains("Plots:")) {

                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(clean);

                while (m.find()) {

                    infestedPlots.add(m.group(1).trim());

                }

            }

        }

        if (aliveCount >= MacroConfig.pestThreshold && !infestedPlots.isEmpty())

        {

            // Guard: Block new cleaning if one is already running OR a gear swap is

            // mid-flight

            if (isCleaningInProgress || currentState == MacroState.CLEANING

                    || isSwappingWardrobe || isSwappingEquipment) {

                return;

            }

            isCleaningInProgress = true;

            currentState = MacroState.CLEANING;

            currentInfestedPlot = infestedPlots.iterator().next();

            final int sessionId = ++currentPestSessionId;

            new Thread(() -> {

                try {

                    // Stop script BEFORE opening any menus

                    if (client.getConnection() != null) {

                        client.getConnection().sendChat(".ez-stopscript");

                        lastCommandTime = System.currentTimeMillis();

                    }

                    Thread.sleep(750); // 750ms wait for script to fully halt

                    if (sessionId != currentPestSessionId || currentState != MacroState.CLEANING)

                        return;

                    if (sessionId != currentPestSessionId || currentState != MacroState.CLEANING)

                        return;

                    if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE) {
                        client.player.displayClientMessage(
                                Component.literal("\u00A7eStarting cleaning sequence. Ensuring Wardrobe Slot "
                                        + MacroConfig.wardrobeSlotFarming + "..."),
                                true);
                        prepSwappedForCurrentPestCycle = true; // Mark for return sync
                        ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming);
                        Thread.sleep(375); // Base wait for menu opening
                        long wardrobeStart = System.currentTimeMillis();
                        while (isSwappingWardrobe && currentState == MacroState.CLEANING
                                && sessionId == currentPestSessionId) {
                            Thread.sleep(50);
                            if (System.currentTimeMillis() - wardrobeStart > 5000) {
                                isSwappingWardrobe = false;
                                break;
                            }
                        }
                        while (wardrobeCleanupTicks > 0 && currentState == MacroState.CLEANING
                                && sessionId == currentPestSessionId)
                            Thread.sleep(50);
                        Thread.sleep(250); // Added safety gap
                    }

                    if (currentState != MacroState.CLEANING)

                        return;

                    // Swap to Pest Gear AFTER Wardrobe but BEFORE SetSpawn/Warp
                    if (MacroConfig.autoEquipment) {
                        client.player.displayClientMessage(Component.literal("\u00A7eSwapping to Pest Equipment..."),
                                true);

                        ensureEquipment(client, true);

                        Thread.sleep(375); // Base wait for menu opening

                        long eqStart = System.currentTimeMillis();

                        while (isSwappingEquipment && currentState == MacroState.CLEANING

                                && sessionId == currentPestSessionId) {

                            Thread.sleep(50);

                            if (System.currentTimeMillis() - eqStart > 5000) {

                                isSwappingEquipment = false;

                                break;

                            }

                        }

                        Thread.sleep(250); // Added safety gap

                    }

                    if (currentState != MacroState.CLEANING)

                        return;

                    sendCommand(client, "/setspawn");

                    Thread.sleep(500);

                    // Default Logic: Trigger Flight then Warp

                    flightToggleStage = 0;

                    flightToggleTicks = 0;

                    isStartingFlight = true;

                    long startWait = System.currentTimeMillis();

                    while (isStartingFlight && currentState == MacroState.CLEANING

                            && sessionId == currentPestSessionId) {

                        Thread.sleep(50);

                        if (System.currentTimeMillis() - startWait > 3000) {

                            isStartingFlight = false;

                            break;

                        }

                    }

                    if (sessionId != currentPestSessionId || currentState != MacroState.CLEANING)

                        return;

                    sendCommand(client, "/tptoplot " + currentInfestedPlot);

                    Thread.sleep(1250); // Ensure warp is complete

                    if (sessionId != currentPestSessionId || currentState != MacroState.CLEANING)

                        return;

                    sendCommand(client, ".ez-startscript misc:pestCleaner");

                } catch (Exception e) {

                    e.printStackTrace();

                }

            }).start();

        }

    }

    private void sendCommand(Minecraft client, String cmd) {

        if (client.player == null || client.getConnection() == null)

            return;

        long now = System.currentTimeMillis();

        long diff = now - lastCommandTime;

        if (diff < COMMAND_COOLDOWN_MS) {

            try {

                Thread.sleep(COMMAND_COOLDOWN_MS - diff);

            } catch (InterruptedException ignored) {

            }

        }

        if (cmd.startsWith("/")) {
            client.getConnection().sendCommand(cmd.substring(1));
        } else {
            client.getConnection().sendChat(cmd);
        }

        lastCommandTime = System.currentTimeMillis();

    }

    private void sleepRandom(int min, int max) throws InterruptedException {

        // Random sleep between min and max (inclusive)

        long sleepTime = min + (long) (Math.random() * (max - min + 1));

        Thread.sleep(sleepTime);

    }

    private void initiateReturnRotation(Minecraft mc, Vec3 targetPos, long minDuration) {

        if (mc.player == null)

            return;

        // 1. Clear the instant-look target so updateRotation uses isRotating logic

        returnLookTarget = null;

        // 2. Setup Linear Rotation

        startRot = new RotationUtils.Rotation(mc.player.getYRot(), mc.player.getXRot());

        targetRot = RotationUtils.calculateLookAt(mc.player.getEyePosition(), targetPos);

        // 3. Shortest Path Unwrapping: Ensure interpolation doesn't spin the

        // "long way"

        targetRot = RotationUtils.getAdjustedEnd(startRot, targetRot);

        // 3. Dynamic Duration Calculation (based on angle distance)

        float yawDiff = Math.abs(net.minecraft.util.Mth.wrapDegrees(targetRot.yaw - startRot.yaw));

        float pitchDiff = Math.abs(targetRot.pitch - startRot.pitch);

        float totalDistance = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

        // duration (ms) = (distance / (speed * 1.5)) * 1000

        long calculatedDuration = (long) ((totalDistance / ((float) MacroConfig.rotationSpeed * 1.5f)) * 1000f);

        // Use the larger of calculated, requested minimum, or a global 150ms floor

        rotationDuration = Math.max(150, Math.max(calculatedDuration, minDuration));

        rotationStartTime = System.currentTimeMillis();

        isRotating = true;

    }

    private void handleReturnSequence(Minecraft mc) {

        if (mc.player == null || mc.gameMode == null) {

            returnState = ReturnState.OFF;

            return;

        }

        returnTickCounter++;

        switch (returnState) {

            case TP_PRE_WAIT:

                // Wait 250ms after .ez-stopscript before teleporting

                if (System.currentTimeMillis() - stateStartTime >= 250) {

                    returnState = ReturnState.TP_START;

                    returnTickCounter = 0;

                }

                break;

            case TP_START:

                String startPlot = MacroConfig.startPlot != null ? MacroConfig.startPlot : "8";

                mc.player.connection.sendChat("/tptoplot " + startPlot);

                returnState = ReturnState.TP_WAIT;

                returnTickCounter = 0;

                break;

            case TP_WAIT:

                if (returnTickCounter >= 20) { // 1 second

                    returnState = ReturnState.FLIGHT_START;

                    flightToggleStage = 0;
                    flightToggleTicks = 0;

                    returnTickCounter = 0;

                }

                break;

            case FLIGHT_START:
                if (mc.player.getAbilities().flying || performFlightToggle(mc, true)) {
                    if (mc.options.keyJump != null)
                        KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);
                    returnState = ReturnState.ALIGN_WAIT;

                    // Trigger Smooth Rotation to High Target (230ms)

                    double tx = MacroConfig.startPos.getX() + 0.5;

                    double ty = MacroConfig.startPos.getY() + 3.5;

                    double tz = MacroConfig.startPos.getZ() + 0.5;

                    initiateReturnRotation(mc, new Vec3(tx, ty, tz), 230);

                    stateStartTime = System.currentTimeMillis();

                    // Do NOT set returnLookTarget yet, let smooth rotation finish

                }

                break;

            case ALIGN_WAIT:

                // Wait for rotation to finish AND 500ms delay

                // isRotating becomes false when done (see updateRotation)

                if (!isRotating && (System.currentTimeMillis() - stateStartTime > 500)) {

                    returnState = ReturnState.FLY_HIGH;

                    aspectUsageCount = 0;

                    losEstablishedTime = 0;

                }

                break;

            case FLY_HIGH:

                // Target: StartPos + 0.5, + 3.5, + 0.5

                double tx2 = MacroConfig.startPos.getX() + 0.5;

                double ty2 = MacroConfig.startPos.getY() + 3.5;

                double tz2 = MacroConfig.startPos.getZ() + 0.5;

                Vec3 targetHigh = new Vec3(tx2, ty2, tz2);

                // Track target (non-blocking) - camera follows while player moves

                returnLookTarget = targetHigh;

                // Priority 1: Gated Rotation Wait

                if (isRotating) {

                    releaseMovementKeys(mc); // Stop all movement while rotating

                    return;

                }

                // Priority 2: Persistent Forward Movement (Only if NOT rotating)

                KeyMapping.set(mc.options.keyUp.getDefaultKey(), true);

                // Priority 3: Distance Check & Transition

                double distHigh = mc.player.position().distanceTo(targetHigh);

                // Check Line of Sight to START POS (not current target 3.5y up)

                Vec3 startPosVec = new Vec3(MacroConfig.startPos.getX() + 0.5, MacroConfig.startPos.getY(),

                        MacroConfig.startPos.getZ() + 0.5);

                // We check LOS to the *actual start pos* because that's our next target

                boolean los = hasLineOfSight(mc.player, startPosVec);

                // Transition Condition: Distance Gate + LOS OR Forced Proximity Fallback

                if ((distHigh <= 15.0 && los) || distHigh <= 3.0) {

                    returnState = ReturnState.FLY_APPROACH;

                    aspectUsageCount = 0;

                    initiateReturnRotation(mc, startPosVec, 100);

                    if (los) {

                        mc.player.displayClientMessage(Component.literal("\u00A7aLOS Established. Approaching..."),
                                true);

                    } else {

                        mc.player.displayClientMessage(
                                Component.literal("\u00A7eProximity Fallback. Forcing Approach..."),

                                true);

                    }

                } else {

                    // Phase 1 Action: > 15 blocks and NO LOS -> Aspect every 500ms to get closer

                    long now = System.currentTimeMillis();

                    if (now - lastAspectUsageTime >= 500) {

                        useAspectItem();

                        lastAspectUsageTime = now;

                    }

                }

                break;

            case FLY_APPROACH:

                Vec3 startPosVec2 = new Vec3(MacroConfig.startPos.getX() + 0.5, MacroConfig.startPos.getY(),

                        MacroConfig.startPos.getZ() + 0.5);

                double dxF = mc.player.getX() - startPosVec2.x;

                double dzF = mc.player.getZ() - startPosVec2.z;

                double horizontalDistFinal = Math.sqrt(dxF * dxF + dzF * dzF);

                // 1. Track target (non-blocking, with overshoot deadzone)

                if (horizontalDistFinal >= 1.0) {

                    returnLookTarget = startPosVec2;

                }

                // 2. Gated Action: Wait for Rotation

                if (isRotating) {

                    KeyMapping.set(mc.options.keyUp.getDefaultKey(), false);

                    return;

                }

                double distFinal = mc.player.position().distanceTo(startPosVec2);

                // 3. Proximity Pathfinding logic (WASD/Jump/Shift)

                if (distFinal < 3.0) {

                    Vec3 detourTarget = getDetourTarget(mc, startPosVec2);

                    // Transition to momentum glide when very close and path is clear

                    boolean pathClear = detourTarget.distanceTo(startPosVec2) < 0.1;

                    if (horizontalDistFinal < 0.8 && pathClear) {

                        releaseMovementKeys(mc);

                    } else {

                        // Active steering to overcome blockages or align

                        moveTowards(mc, detourTarget);

                    }

                } else {

                    // Far approach: Use Aspect and simple forward movement logic

                    // (Actually we usually come from FLY_HIGH which has forward enabled)

                    // Let's ensure W is on if we are still far

                    KeyMapping.set(mc.options.keyUp.getDefaultKey(), true);

                    long now = System.currentTimeMillis();

                    if (now - lastAspectUsageTime >= 500) {

                        useAspectItem();

                        lastAspectUsageTime = now;

                    }

                }

                if (horizontalDistFinal < 0.3) {

                    releaseMovementKeys(mc);

                    returnState = ReturnState.LANDING_SHIFT;

                    stateStartTime = System.currentTimeMillis();

                    returnLookTarget = null; // Stop looking

                    isRotating = false; // Stop any active rotation

                    // Snap to precise X/Z

                    double targetX = MacroConfig.startPos.getX() + 0.5;

                    double targetZ = MacroConfig.startPos.getZ() + 0.5;

                    mc.player.setPos(targetX, mc.player.getY(), targetZ);

                    mc.player.xo = targetX;

                    mc.player.yo = mc.player.getY();

                    mc.player.zo = targetZ;

                    mc.player.xOld = targetX;

                    mc.player.yOld = mc.player.getY();

                    mc.player.zOld = targetZ;

                    mc.player.setDeltaMovement(0, 0, 0); // Reset velocity to prevent sliding

                }

                break;

            case LANDING_SHIFT:

                // Hold Shift

                KeyMapping.set(mc.options.keyShift.getDefaultKey(), true);

                if (System.currentTimeMillis() - stateStartTime > 500) {

                    KeyMapping.set(mc.options.keyShift.getDefaultKey(), false); // Release Shift

                    returnState = ReturnState.LANDING_WAIT;

                    stateStartTime = System.currentTimeMillis();

                }

                break;

            case LANDING_WAIT:

                // Wait for player to be on the ground AND at least 290ms elapsed

                boolean onGround = mc.player.onGround();

                boolean timeElapsed = System.currentTimeMillis() - stateStartTime >= 290;

                if (onGround && timeElapsed) {

                    // Final position verification: ensure we are actually at the start pos

                    double landDx = mc.player.getX() - (MacroConfig.startPos.getX() + 0.5);

                    double landDz = mc.player.getZ() - (MacroConfig.startPos.getZ() + 0.5);

                    double landHDist = Math.sqrt(landDx * landDx + landDz * landDz);

                    if (landHDist > 0.5) {

                        // Not close enough, snap position

                        double snapX = MacroConfig.startPos.getX() + 0.5;

                        double snapZ = MacroConfig.startPos.getZ() + 0.5;

                        mc.player.setPos(snapX, mc.player.getY(), snapZ);

                        mc.player.xo = snapX;

                        mc.player.yo = mc.player.getY();

                        mc.player.zo = snapZ;

                        mc.player.xOld = snapX;

                        mc.player.yOld = mc.player.getY();

                        mc.player.zOld = snapZ;

                        mc.player.setDeltaMovement(0, 0, 0);

                        mc.player.displayClientMessage(Component.literal("\u00A7eSnapping to start position..."), true);

                        stateStartTime = System.currentTimeMillis();

                        return;

                    }

                    returnState = ReturnState.OFF;

                    if (isProactiveReturnPending) {
                        triggerPestGearSwap(mc);
                        mc.player.displayClientMessage(
                                Component.literal("\u00A7eReturn complete. Triggering pending gear swap..."), true);
                    } else if (prepSwappedForCurrentPestCycle
                            || (!shouldRestartFarmingAfterSwap && !isSwappingWardrobe)) {
                        // Priority: If we have a pending gear restoration (prepSwapped), we MUST handle
                        // it here
                        startFarmingWithGearCheck(mc);
                        mc.player.displayClientMessage(Component.literal("\u00A7aAuto-Restarting Macro: Farming Mode"),
                                true);
                    } else {
                        mc.player.displayClientMessage(
                                Component.literal("\u00A7eReturn complete. Waiting for gear swap..."), true);
                    }

                } else if (!onGround) {

                    // Still in the air, keep holding shift to descend

                    KeyMapping.set(mc.options.keyShift.getDefaultKey(), true);

                }

                break;

        }

    }

    // Helper: Row Safeguard

    private boolean isInEndRow(Minecraft client) {
        if (client.player == null || MacroConfig.startPos == null || MacroConfig.endPos == null)
            return false;

        double currentX = client.player.getX();
        double currentZ = client.player.getZ();

        double startX = MacroConfig.startPos.getX() + 0.5;
        double startZ = MacroConfig.startPos.getZ() + 0.5;
        double endX = MacroConfig.endPos.getX() + 0.5;
        double endZ = MacroConfig.endPos.getZ() + 0.5;

        double deltaX = Math.abs(startX - endX);
        double deltaZ = Math.abs(startZ - endZ);

        if (deltaX < deltaZ) {
            // Z is the primary axis of displacement. Match Z against end row.
            return Math.abs(currentZ - endZ) < 2.0;
        } else {
            // X is the primary axis of displacement. Match X against end row.
            return Math.abs(currentX - endX) < 2.0;
        }
    }

    // Helper: Perform Double Jump (Toggle Flight)

    // Returns TRUE when sequence finishes

    private boolean performFlightToggle(Minecraft mc, boolean enable) {

        flightToggleTicks++;

        if (mc.options.keyJump != null) {

            // Stage 0: Press Space

            if (flightToggleStage == 0) {

                if (flightToggleTicks == 1)

                    KeyMapping.set(mc.options.keyJump.getDefaultKey(), true);

                if (flightToggleTicks >= 2) { // 100ms

                    flightToggleStage = 1;

                    flightToggleTicks = 0;

                }

            }

            // Stage 1: Release Space

            else if (flightToggleStage == 1) {

                if (flightToggleTicks == 1)

                    KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);

                if (flightToggleTicks >= 3) { // 150ms gap

                    flightToggleStage = 2;

                    flightToggleTicks = 0;

                }

            }

            // Stage 2: Press Space

            else if (flightToggleStage == 2) {

                if (flightToggleTicks == 1)

                    KeyMapping.set(mc.options.keyJump.getDefaultKey(), true);

                if (flightToggleTicks >= 2) { // 100ms

                    flightToggleStage = 3;

                    flightToggleTicks = 0;

                }

            }

            // Stage 3: Release Space and Finish
            else if (flightToggleStage == 3) {
                if (flightToggleTicks == 1)
                    KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);
                if (flightToggleTicks >= 2)
                    return true;
            }

        }

        return false;

    }

    // Helper: Look At

    private static void lookAt(net.minecraft.world.entity.player.Player player, Vec3 target) {

        double dx = target.x - player.getX();

        double dy = target.y - (player.getY() + player.getEyeHeight());

        double dz = target.z - player.getZ();

        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;

        float pitch = (float) -(Math.atan2(dy, distXZ) * 180.0D / Math.PI);

        player.setYRot(yaw);

        player.setXRot(pitch);

    }

    // Helper: LOS Check

    private boolean hasLineOfSight(net.minecraft.world.entity.player.Player player, Vec3 target) {

        net.minecraft.world.level.Level level = player.level();

        Vec3 start = player.getEyePosition();

        // Raytrace block only, ignore fluids

        net.minecraft.world.phys.BlockHitResult result = level.clip(new net.minecraft.world.level.ClipContext(

                start, target,

                net.minecraft.world.level.ClipContext.Block.VISUAL,

                net.minecraft.world.level.ClipContext.Fluid.NONE,

                player));

        // If we hit something, and it's not the target (block pos), then LOS is

        // blocked.

        // Actually clip returns MISS if nothing hit.

        // If it returns BLOCK, we hit something.

        if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {

            // Robust check: If the ray hits a block within 1.0 block of the target center,

            // it's considered LOS established (we hit the target block or something right

            // next to it).

            double distToTarget = result.getLocation().distanceTo(target);

            if (distToTarget < 1.0) {

                return true;

            }

            return false;

        }

        return true;

    }

    private void handlePestCleaningFinished(Minecraft client) {

        client.player.displayClientMessage(Component.literal("\u00A7aPest cleaning finished detected."), true);

        new Thread(() -> {

            try {

                isStoppingFlight = true;

                Thread.sleep(300);

                while (isStoppingFlight)

                    Thread.sleep(50);

                // Wait for sync before checking visitors
                Thread.sleep(100);

                int visitors = getVisitorCount(client);

                if (visitors >= MacroConfig.visitorThreshold) {

                    client.player.displayClientMessage(

                            Component
                                    .literal("\u00A7dVisitor Threshold Met (" + visitors
                                            + "). Direct Transition in 1s..."),

                            true);

                    Thread.sleep(1000); // Wait 1s as requested

                    swapToFarmingTool(client);

                    sendCommand(client, ".ez-startscript misc:visitor");

                    isCleaningInProgress = false;

                    return;

                }

                Thread.sleep(150); // Small delay before warp

                sendCommand(client, "/warp garden");

                Thread.sleep(150);

                isReturningFromPestVisitor = true;
                finalizeReturnToFarm(client);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

    }

    private void handleVisitorScriptFinished(Minecraft client) {

        client.player.displayClientMessage(Component.literal("\u00A7aVisitor sequence complete. Returning to farm..."),

                true);

        new Thread(() -> {

            try {

                // Swap back to farming wardrobe after visitor if armor swap was done
                if (MacroConfig.armorSwapVisitor && MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE
                        && trackedWardrobeSlot != MacroConfig.wardrobeSlotFarming) {
                    client.player.displayClientMessage(
                            Component.literal("\u00A7eRestoring Farming Wardrobe (Slot "
                                    + MacroConfig.wardrobeSlotFarming + ")..."),
                            true);
                    client.execute(() -> ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming));
                    Thread.sleep(375);
                    while (isSwappingWardrobe)
                        Thread.sleep(50);
                    while (wardrobeCleanupTicks > 0)
                        Thread.sleep(50);
                    Thread.sleep(250);
                }

                Thread.sleep(150); // Delay before return
                sendCommand(client, "/warp garden");

                Thread.sleep(150);

                isReturningFromPestVisitor = true;
                finalizeReturnToFarm(client);

            } catch (Exception e) {

                e.printStackTrace();

            }

        }).start();

    }

    private void finalizeReturnToFarm(Minecraft client) {

        if (currentState == MacroState.OFF)

            return;

        // Visitor Sync Gap
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
        }

        int visitors = getVisitorCount(client);

        if (visitors >= MacroConfig.visitorThreshold) {

            client.player.displayClientMessage(

                    Component.literal("\u00A7dVisitor Threshold Met (" + visitors + "). Redirecting to Visitors..."),
                    true);

            swapToFarmingTool(client);

            // Swap to visitor wardrobe if configured
            if (MacroConfig.armorSwapVisitor && MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE
                    && trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                client.player.displayClientMessage(
                        Component.literal("\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor
                                + ")..."),
                        true);
                client.execute(() -> ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor));
                try {
                    Thread.sleep(375);
                    while (isSwappingWardrobe)
                        Thread.sleep(50);
                    while (wardrobeCleanupTicks > 0)
                        Thread.sleep(50);
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                }
            }

            sendCommand(client, ".ez-startscript misc:visitor");

            isCleaningInProgress = false;

            return;

        }

        if (isRestartPending && System.currentTimeMillis() >= restartExecutionTime) {

            client.player.displayClientMessage(

                    Component.literal("\u00A7c[Ihanuat] Aborting farm resume due to pending Server Restart!"), true);

            currentState = MacroState.FARMING;

            isCleaningInProgress = false;

        } else if (nextRestTriggerMs != 0 && System.currentTimeMillis() >= nextRestTriggerMs) {

            client.player.displayClientMessage(

                    Component.literal("\u00A7b[Ihanuat] Dynamic Rest triggered! Taking a break..."), true);

            // Calculate random break duration

            int base = MacroConfig.restBreakTime;

            int offset = MacroConfig.restBreakTimeOffset;

            int randomOffset = (offset > 0) ? (new java.util.Random().nextInt(offset * 2 + 1) - offset) : 0;

            int finalSeconds = base + randomOffset;

            long restEndTimeMs = System.currentTimeMillis() + (finalSeconds * 60L * 1000L);

            cachedRestEndTimeMs = restEndTimeMs;

            isRestingForDynamicRest = true;

            // Disconnect and schedule reconnection

            client.player.connection.sendChat("/setspawn");

            new Thread(() -> {

                try {

                    Thread.sleep(500); // Give setspawn time to process

                    client.execute(() -> {

                        if (client.getConnection() != null) {

                            intentionalDisconnect = true;

                            ReconnectScheduler.scheduleReconnect(finalSeconds * 60L, true);

                            client.getConnection().getConnection()

                                    .disconnect(Component.literal("Dynamic Rest Initiated"));

                            intentionalDisconnect = false;

                        }

                    });

                } catch (Exception e) {

                    e.printStackTrace();

                }

            }).start();

            currentState = MacroState.OFF;

            isCleaningInProgress = false;

        } else {
            startFarmingWithGearCheck(client);
        }
    }

    private void startFarmingWithGearCheck(Minecraft client) {
        if (isProactiveReturnPending)
            return; // Prevent normal restart if proactive swap is booked

        new Thread(() -> {

            try {
                shouldRestartFarmingAfterSwap = false; // Prevent tick handler from sending duplicate startscript

                if (isReturningFromPestVisitor) {
                    // Simple return from pest/visitor finish
                    isReturningFromPestVisitor = false;
                    isCleaningInProgress = false;

                    // Delay before restart
                    Thread.sleep(450); // Reduced from 1.2s cushion

                    client.execute(() -> {
                        swapToFarmingTool(client);
                        sendCommand(client, MacroConfig.restartScript);
                        currentState = MacroState.FARMING;
                    });
                    return;
                }

                if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE && prepSwappedForCurrentPestCycle
                        && trackedWardrobeSlot != MacroConfig.wardrobeSlotFarming) {
                    client.player.displayClientMessage(
                            Component.literal("\u00A7eRestoring Farming Wardrobe (Slot "
                                    + MacroConfig.wardrobeSlotFarming + ")..."),
                            true);
                    ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming);
                    Thread.sleep(375);
                    while (isSwappingWardrobe && currentState != MacroState.OFF)
                        Thread.sleep(50);
                    while (wardrobeCleanupTicks > 0 && currentState != MacroState.OFF)
                        Thread.sleep(50);
                    Thread.sleep(250);
                }

                if (MacroConfig.autoEquipment && prepSwappedForCurrentPestCycle
                        && !Boolean.TRUE.equals(trackedIsPestGear)) {
                    client.player.displayClientMessage(Component.literal("\u00A7eRestoring Farming Accessories..."),
                            true);

                    ensureEquipment(client, true); // Lotus/Blossom (farming restoration gear)

                    Thread.sleep(375);

                    while (isSwappingEquipment && currentState != MacroState.OFF)

                        Thread.sleep(50);

                    Thread.sleep(250);

                }

                // When no swap was queued, apply normal startup delay
                if (!prepSwappedForCurrentPestCycle || (MacroConfig.gearSwapMode != MacroConfig.GearSwapMode.WARDROBE
                        && !MacroConfig.autoEquipment)) {
                    Thread.sleep(500);
                }

                // Wait for any open menus to fully close before starting
                long menuWaitStart = System.currentTimeMillis();
                while (client.screen != null && System.currentTimeMillis() - menuWaitStart < 5000)
                    Thread.sleep(50);
                Thread.sleep(100);

                prepSwappedForCurrentPestCycle = false;
                isCleaningInProgress = false;
                swapToFarmingTool(client);
                currentState = MacroState.FARMING;
                sendCommand(client, MacroConfig.restartScript);

            } catch (Exception e) {

                e.printStackTrace();

            }

        }).start();

    }

    private void triggerPestGearSwap(Minecraft client) {
        net.minecraft.client.multiplayer.ClientPacketListener connection = client.getConnection();
        if (connection == null)
            return;

        int cooldownSeconds = -1;
        for (net.minecraft.client.multiplayer.PlayerInfo info : connection.getListedOnlinePlayers()) {
            String name = (info.getTabListDisplayName() != null) ? info.getTabListDisplayName().getString() : "";
            String clean = name.replaceAll("\u00A7[0-9a-fk-or]", "").trim();
            java.util.regex.Matcher cooldownMatcher = COOLDOWN_PATTERN.matcher(clean);
            if (cooldownMatcher.find()) {
                if (cooldownMatcher.group(1).equalsIgnoreCase("READY"))
                    cooldownSeconds = 0;
                else if (cooldownMatcher.group(2) != null)
                    cooldownSeconds = Integer.parseInt(cooldownMatcher.group(2)) * 60
                            + Integer.parseInt(cooldownMatcher.group(3));
                else if (cooldownMatcher.group(4) != null)
                    cooldownSeconds = Integer.parseInt(cooldownMatcher.group(4));
                break;
            }
        }

        if (cooldownSeconds == -1) {
            startFarmingWithGearCheck(client);
            return;
        }

        if (MacroConfig.autoEquipment) {
            // Option 2 path mirror
            if (cooldownSeconds <= 170) {
                synchronized (IhanuatClient.class) {
                    if (prepSwappedForCurrentPestCycle || isCleaningInProgress)
                        return;
                    prepSwappedForCurrentPestCycle = true;
                }
                client.player.displayClientMessage(Component.literal("\u00A7ePest exchange triggered (<= 170s)..."),
                        true);
                new Thread(() -> {
                    try {
                        isProactiveReturnPending = false;
                        if (isCleaningInProgress)
                            return;
                        Thread.sleep(100);
                        ensureEquipment(client, false); // Option 2
                        Thread.sleep(375);
                        while (isSwappingEquipment && !isCleaningInProgress)
                            Thread.sleep(50);
                        Thread.sleep(250);
                        if (isCleaningInProgress)
                            return;
                        if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.ROD)
                            executeRodSequence(client);
                        else if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE)
                            triggerWardrobeSwap(client);
                        else
                            resumeAfterPrepSwap(client);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            } else {
                startFarmingWithGearCheck(client);
            }
        } else {
            // 8s path mirror
            if (cooldownSeconds <= 8) {
                synchronized (IhanuatClient.class) {
                    if (prepSwappedForCurrentPestCycle || isCleaningInProgress)
                        return;
                    prepSwappedForCurrentPestCycle = true;
                }
                client.player.displayClientMessage(Component.literal("\u00A7ePest exchange triggered (<= 8s)..."),
                        true);
                new Thread(() -> {
                    try {
                        isProactiveReturnPending = false;
                        if (isCleaningInProgress)
                            return;
                        Thread.sleep(100);
                        if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.ROD)
                            executeRodSequence(client);
                        else if (MacroConfig.gearSwapMode == MacroConfig.GearSwapMode.WARDROBE)
                            triggerWardrobeSwap(client);
                        else
                            resumeAfterPrepSwap(client);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            } else {
                startFarmingWithGearCheck(client);
            }
        }
    }

    private static void stopMacro(Minecraft client) {

        if (currentState == MacroState.OFF && returnState == ReturnState.OFF)

            return;

        currentState = MacroState.OFF;

        returnState = ReturnState.OFF;

        currentPestSessionId++; // Kill background cleaning thread

        isCleaningInProgress = false;

        isStartingFlight = false;

        isStoppingFlight = false;

        flightToggleStage = 0;

        flightStopStage = 0;

        isSwappingWardrobe = false;

        wardrobeOpenPendingTime = 0;

        wardrobeCleanupTicks = 0;

        isSwappingEquipment = false;

        prepSwappedForCurrentPestCycle = false;

        shouldRestartFarmingAfterSwap = false;

        nextRestTriggerMs = 0; // Reset Dynamic Rest cycle on manual stop

        returnLookTarget = null;

        forceReleaseKeys(client);

        if (client.getConnection() != null) {

            client.getConnection().sendChat(".ez-stopscript");

        }

        if (client.player != null) {

            client.player.displayClientMessage(Component.literal("\u00A7cMacro Stopped Forcefully"), true);

        }

    }

    private Screen createConfigScreen(Screen parent) {

        ConfigBuilder builder = ConfigBuilder.create()

                .setParentScreen(parent)

                .setTitle(Component.literal("Ihanuat Config"))

                .setSavingRunnable(MacroConfig::save);

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        // Wardrobe/Rod Swap Mode (Mutually Exclusive)
        general.addEntry(builder.getEntryBuilder()
                .startEnumSelector(Component.literal("Wardrobe/Rod Swap Mode"), MacroConfig.GearSwapMode.class,
                        MacroConfig.gearSwapMode)
                .setDefaultValue(MacroConfig.GearSwapMode.NONE)
                .setSaveConsumer(newValue -> MacroConfig.gearSwapMode = newValue)
                .build());

        // Pest Threshold Slider

        general.addEntry(builder.getEntryBuilder()

                .startIntSlider(Component.literal("Pest Threshold"), MacroConfig.pestThreshold, 1, 8)

                .setDefaultValue(1)

                .setSaveConsumer(newValue -> MacroConfig.pestThreshold = newValue)

                .build());

        // Visitor Threshold Slider

        general.addEntry(builder.getEntryBuilder()

                .startIntSlider(Component.literal("Visitor Threshold"), MacroConfig.visitorThreshold, 1, 5)

                .setDefaultValue(5)

                .setSaveConsumer(newValue -> MacroConfig.visitorThreshold = newValue)

                .build());

        // Rotation Speed Slider

        general.addEntry(builder.getEntryBuilder()

                .startIntSlider(Component.literal("Rotation Speed (deg/s)"), MacroConfig.rotationSpeed, 10, 2000)

                .setDefaultValue(200)

                .setSaveConsumer(newValue -> MacroConfig.rotationSpeed = newValue)

                .build());

        // GUI Click Delay

        general.addEntry(builder.getEntryBuilder()

                .startIntSlider(Component.literal("GUI Click Delay (ms)"), MacroConfig.guiClickDelay, 100, 2000)

                .setDefaultValue(500)

                .setSaveConsumer(newValue -> MacroConfig.guiClickDelay = newValue)

                .build());

        // Restart Time

        general.addEntry(builder.getEntryBuilder()

                .startIntField(Component.literal("Restart Time (Minutes)"), MacroConfig.restartTime)

                .setDefaultValue(5)

                .setSaveConsumer(newValue -> MacroConfig.restartTime = newValue)

                .build());

        // Restart Script

        general.addEntry(builder.getEntryBuilder()

                .startStrField(Component.literal("Restart Script Command"), MacroConfig.restartScript)

                .setDefaultValue(".ez-startscript netherwart:1")

                .setSaveConsumer(newValue -> MacroConfig.restartScript = newValue)

                .build());

        // Auto-Visitor Toggle

        general.addEntry(builder.getEntryBuilder()

                .startBooleanToggle(Component.literal("Auto-Visitor"), MacroConfig.autoVisitor)

                .setDefaultValue(true)

                .setSaveConsumer(newValue -> MacroConfig.autoVisitor = newValue)

                .build());

        // Auto-Equipment Toggle

        general.addEntry(builder.getEntryBuilder()

                .startBooleanToggle(Component.literal("Auto-Equipment"), MacroConfig.autoEquipment)

                .setDefaultValue(true)

                .setSaveConsumer(newValue -> MacroConfig.autoEquipment = newValue)

                .build());

        // Armor Swap for Visitor

        general.addEntry(builder.getEntryBuilder()

                .startBooleanToggle(Component.literal("Armor Swap for Visitor"), MacroConfig.armorSwapVisitor)

                .setDefaultValue(false)

                .setSaveConsumer(newValue -> MacroConfig.armorSwapVisitor = newValue)

                .build());

        // Wardrobe Slot - Farming

        general.addEntry(builder.getEntryBuilder()

                .startIntSlider(Component.literal("Wardrobe Slot: Farming"), MacroConfig.wardrobeSlotFarming, 1, 9)

                .setDefaultValue(1)

                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotFarming = newValue)

                .build());

        // Wardrobe Slot - Pest

        general.addEntry(builder.getEntryBuilder()

                .startIntSlider(Component.literal("Wardrobe Slot: Pest"), MacroConfig.wardrobeSlotPest, 1, 9)

                .setDefaultValue(2)

                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotPest = newValue)

                .build());

        // Wardrobe Slot - Visitor

        general.addEntry(builder.getEntryBuilder()

                .startIntSlider(Component.literal("Wardrobe Slot: Visitor"), MacroConfig.wardrobeSlotVisitor, 1, 9)

                .setDefaultValue(3)

                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotVisitor = newValue)

                .build());

        ConfigCategory dynamicRest = builder.getOrCreateCategory(Component.literal("Dynamic Rest"));

        dynamicRest.addEntry(builder.getEntryBuilder()

                .startIntField(Component.literal("Scripting Time (Minutes)"), MacroConfig.restScriptingTime)

                .setDefaultValue(30)

                .setSaveConsumer(newValue -> MacroConfig.restScriptingTime = newValue)

                .build());

        dynamicRest.addEntry(builder.getEntryBuilder()

                .startIntField(Component.literal("Scripting Time Offset (Minutes)"),

                        MacroConfig.restScriptingTimeOffset)

                .setDefaultValue(3)

                .setSaveConsumer(newValue -> MacroConfig.restScriptingTimeOffset = newValue)

                .build());

        dynamicRest.addEntry(builder.getEntryBuilder()

                .startIntField(Component.literal("Break Time (Minutes)"), MacroConfig.restBreakTime)

                .setDefaultValue(20)

                .setSaveConsumer(newValue -> MacroConfig.restBreakTime = newValue)

                .build());

        dynamicRest.addEntry(builder.getEntryBuilder()

                .startIntField(Component.literal("Break Time Offset (Minutes)"), MacroConfig.restBreakTimeOffset)

                .setDefaultValue(3)

                .setSaveConsumer(newValue -> MacroConfig.restBreakTimeOffset = newValue)

                .build());

        ConfigCategory qol = builder.getOrCreateCategory(Component.literal("QOL"));

        qol.addEntry(builder.getEntryBuilder()
                .startBooleanToggle(Component.literal("Stash Manager"), MacroConfig.autoStashManager)
                .setDefaultValue(false)
                .setSaveConsumer(newValue -> {
                    MacroConfig.autoStashManager = newValue;
                    if (!newValue) {
                        isStashPickupActive = false;
                    }
                })
                .build());

        // Start Position Capture

        general.addEntry(new ButtonEntry(
                Component.literal("\u00A7eCapture Start Position"),
                Component.literal(
                        "\u00A77Current: \u00A7f" + MacroConfig.startPos.toShortString() + " \u00A77Plot: \u00A7f"
                                + MacroConfig.startPlot),

                btn -> {

                    Minecraft client = Minecraft.getInstance();

                    if (client.player != null) {

                        MacroConfig.startPos = client.player.blockPosition();

                        MacroConfig.startPlot = PlotUtils.getPlotName(client);

                        MacroConfig.save();

                        // Auto-refresh screen

                        client.setScreen(createConfigScreen(parent));

                    }

                }));

        // End Position Capture

        general.addEntry(new ButtonEntry(
                Component.literal("\u00A7bCapture End Position"),
                Component.literal(
                        "\u00A77Current: \u00A7f" + MacroConfig.endPos.toShortString() + " \u00A77Plot: \u00A7f"
                                + MacroConfig.endPlot),

                btn -> {

                    Minecraft client = Minecraft.getInstance();

                    if (client.player != null) {

                        MacroConfig.endPos = client.player.blockPosition();

                        MacroConfig.endPlot = PlotUtils.getPlotName(client);

                        MacroConfig.save();

                        // Auto-refresh screen

                        client.setScreen(createConfigScreen(parent));

                    }

                }));

        return builder.build();

    }

    /**
     * 
     * Custom Cloth Config entry for a simple action button.
     * 
     */

    private static class ButtonEntry extends me.shedaniel.clothconfig2.gui.entries.TooltipListEntry<Object> {

        private final net.minecraft.client.gui.components.Button button;

        private final Component fieldName;

        public ButtonEntry(Component fieldName, Component tooltip,

                net.minecraft.client.gui.components.Button.OnPress onPress) {

            super(fieldName, () -> java.util.Optional.of(new Component[] { tooltip }));

            this.fieldName = fieldName;

            this.button = net.minecraft.client.gui.components.Button.builder(fieldName, onPress)

                    .bounds(0, 0, 150, 20)

                    .build();

        }

        @Override

        public void render(net.minecraft.client.gui.GuiGraphics graphics, int index, int y, int x, int entryWidth,

                int entryHeight, int mouseX, int mouseY, boolean isHovered, float tickDelta) {

            super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, tickDelta);

            this.button.setX(x + entryWidth - 160);

            this.button.setY(y);

            this.button.setWidth(150);

            this.button.render(graphics, mouseX, mouseY, tickDelta);

            // Render Label

            graphics.drawString(Minecraft.getInstance().font, fieldName, x, y + 6, 0xFFFFFF);

        }

        @Override

        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {

            return java.util.Collections.singletonList(button);

        }

        @Override

        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {

            return java.util.Collections.singletonList(button);

        }

        @Override

        public Object getValue() {

            return null;

        }

        @Override

        public java.util.Optional<Object> getDefaultValue() {

            return java.util.Optional.empty();

        }

        @Override

        public void save() {

        }

    }

    private void ensureWardrobeSlot(Minecraft client, int slot) {
        if (client.player == null)
            return;

        if (trackedWardrobeSlot == slot) {
            // Already there
            return;
        }

        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        shouldRestartFarmingAfterSwap = false;
        sendCommand(client, "/wardrobe");
    }

    private void handleWardrobeMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingWardrobe || targetWardrobeSlot == -1)
            return;

        String title = screen.getTitle().getString();
        if (!title.contains("Wardrobe"))
            return;

        if (wardrobeInteractionTime == 0) {
            wardrobeInteractionTime = System.currentTimeMillis();
            return;
        }

        long currentDelay = MacroConfig.guiClickDelay;
        if (wardrobeInteractionStage == 1)
            currentDelay = MacroConfig.guiClickDelay;
        else if (wardrobeInteractionStage == 2)
            currentDelay = MacroConfig.guiClickDelay;

        if (System.currentTimeMillis() - wardrobeInteractionTime < currentDelay) {
            return;
        }

        Slot targetSlotObj = null;
        Slot closeSlotObj = null;

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem())
                continue;
            String itemName = slot.getItem().getHoverName().getString();

            if (itemName.contains("Slot " + targetWardrobeSlot + ":")) {
                targetSlotObj = slot;
                // Check if already equipped (green 'Equipped' in name or lore-like string)
                if (itemName.contains("Equipped")) {
                    trackedWardrobeSlot = targetWardrobeSlot;
                    isSwappingWardrobe = false;
                    targetWardrobeSlot = -1;
                    wardrobeInteractionStage = 0;
                    wardrobeCleanupTicks = 20;
                    if (client.screen != null)
                        client.setScreen(null);
                    return;
                }
            }
            if (itemName.contains("Close") || itemName.contains("Go Back")) {
                closeSlotObj = slot;
            }
        }

        if (targetSlotObj != null) {
            if (wardrobeInteractionStage == 0) {
                client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, targetSlotObj.index, 0,
                        ClickType.PICKUP, client.player);
                wardrobeInteractionTime = System.currentTimeMillis();
                wardrobeInteractionStage = 1;
            } else if (wardrobeInteractionStage == 1) {
                if (closeSlotObj != null) {
                    client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, closeSlotObj.index, 0,
                            ClickType.PICKUP, client.player);
                }
                wardrobeInteractionTime = System.currentTimeMillis();
                wardrobeInteractionStage = 2;
            } else if (wardrobeInteractionStage == 2) {
                // Protocol-level reset and CLEANUP FINISH
                int containerId = screen.getMenu().containerId;
                client.gameMode.handleInventoryMouseClick(containerId, -999, 0, ClickType.PICKUP, client.player);
                client.gameMode.handleInventoryMouseClick(0, -999, 0, ClickType.PICKUP, client.player);
                if (client.player != null) {
                    if (client.player.containerMenu != null)
                        client.player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                    if (client.player.inventoryMenu != null)
                        client.player.inventoryMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                }

                trackedWardrobeSlot = targetWardrobeSlot;
                targetWardrobeSlot = -1;
                isSwappingWardrobe = false; // FINALLY clear the flag here
                wardrobeInteractionStage = 3; // Done
                wardrobeCleanupTicks = 20;
                if (client.screen != null)
                    client.setScreen(null);

                if (client.mouseHandler != null) {
                    client.mouseHandler.releaseMouse();
                }
            }
        }
    }

    private void ensureEquipment(Minecraft client, boolean toFarming) {
        if (!MacroConfig.autoEquipment)
            return;

        if (trackedIsPestGear != null && trackedIsPestGear == !toFarming) {
            return;
        }

        isSwappingEquipment = true;
        swappingToFarmingGear = toFarming;
        equipmentInteractionStage = 0;
        equipmentInteractionTime = 0;
        equipmentTargetIndex = 0;
        sendCommand(client, "/eq");
    }

    private void handleEquipmentMenu(Minecraft client, AbstractContainerScreen<?> screen) {

        if (!isSwappingEquipment)

            return;

        // If something went wrong and the screen isn't what we expect, or it's just

        // closing

        String title = screen.getTitle().getString();

        if (!title.contains("Equipment")) {

            return;

        }

        // Initialize time on first frame

        if (equipmentInteractionTime == 0) {

            equipmentInteractionTime = System.currentTimeMillis();

            return;

        }

        // Configurable delay between clicks

        if (System.currentTimeMillis() - equipmentInteractionTime < MacroConfig.guiClickDelay) {

            return;

        }

        // Precise target groups per click index

        String[][] pestTargets = {

                { "Lotus Bracelet", "Blossom Bracelet" },

                { "Lotus Belt", "Blossom Belt" },

                { "Lotus Necklace", "Blossom Necklace" },

                { "Lotus Cloak", "Blossom Cloak" }

        };

        String[][] farmTargets = {

                { "Pesthunter's Necklace" },

                { "Pesthunter's Belt" },

                { "Pesthunter's Gloves" },

                { "Pest Vest" }

        };

        if (equipmentTargetIndex < 4) {
            // Fast Scan: If matching pieces are already equipped, skip to close
            if (equipmentTargetIndex == 0) {
                int equippedCount = 0;
                for (int i = 0; i < 4; i++) {
                    String[] currentTargetGroup = swappingToFarmingGear ? pestTargets[i] : farmTargets[i];
                    boolean foundEquipped = false;
                    for (Slot slot : screen.getMenu().slots) {
                        if (!slot.hasItem() || slot.index < 54)
                            continue;
                        String itemName = slot.getItem().getHoverName().getString();
                        for (String pattern : currentTargetGroup) {
                            if (itemName.contains(pattern) && itemName.contains("Equipped")) {
                                foundEquipped = true;
                                break;
                            }
                        }
                        if (foundEquipped)
                            break;
                    }
                    if (foundEquipped)
                        equippedCount++;
                }

                if (equippedCount == 4) {
                    equipmentTargetIndex = 4; // Jump to close
                }
            }
        }

        if (equipmentTargetIndex < 4) {

            // Search for the current target piece group

            Slot targetSlotObj = null;

            String[] currentTargetGroup = swappingToFarmingGear ? pestTargets[equipmentTargetIndex]

                    : farmTargets[equipmentTargetIndex];

            for (Slot slot : screen.getMenu().slots) {

                if (!slot.hasItem() || slot.index < 54)

                    continue;

                String itemName = slot.getItem().getHoverName().getString();

                for (String pattern : currentTargetGroup) {

                    if (itemName.contains(pattern) && !itemName.contains("Equipped")) {

                        targetSlotObj = slot;

                        break;

                    }

                }

                if (targetSlotObj != null)

                    break; // Found one

            }

            if (targetSlotObj != null) {

                // Click it

                client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, targetSlotObj.index, 0,

                        ClickType.PICKUP, client.player);

                equipmentInteractionTime = System.currentTimeMillis();

            } else {

                // Not found (maybe already equipped or missing item), skip to next phase anyway

                // to not get stuck

                equipmentInteractionTime = System.currentTimeMillis();

            }

            equipmentTargetIndex++; // Move to next piece

        } else {

            // All 4 pieces cycled, close menu

            if (equipmentInteractionStage == 0) {

                // Find Close button

                Slot closeSlotObj = null;

                for (Slot slot : screen.getMenu().slots) {

                    if (!slot.hasItem())

                        continue;

                    String itemName = slot.getItem().getHoverName().getString();

                    if (itemName.contains("Close") || itemName.contains("Go Back")) {

                        closeSlotObj = slot;

                        break;

                    }

                }

                if (closeSlotObj != null) {

                    client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, closeSlotObj.index, 0,

                            ClickType.PICKUP, client.player);

                }

                equipmentInteractionTime = System.currentTimeMillis();

                equipmentInteractionStage = 1; // Buffer delay

            } else if (equipmentInteractionStage == 1) {

                // Phase 1: Wait for buffer then clear flag and close

                isSwappingEquipment = false;

                client.setScreen(null);

                equipmentInteractionTime = System.currentTimeMillis();

                equipmentInteractionStage = 2; // Protocol reset phase

            } else if (equipmentInteractionStage == 2) {

                // Phase 2: Protocol-level reset

                int containerId = screen.getMenu().containerId;

                client.gameMode.handleInventoryMouseClick(containerId, -999, 0, ClickType.PICKUP, client.player);

                client.gameMode.handleInventoryMouseClick(0, -999, 0, ClickType.PICKUP, client.player);

                if (client.player != null) {

                    if (client.player.containerMenu != null)

                        client.player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);

                    if (client.player.inventoryMenu != null)

                        client.player.inventoryMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);

                }

                if (client.mouseHandler != null) {

                    client.mouseHandler.releaseMouse();

                }

                trackedIsPestGear = !swappingToFarmingGear; // Update tracked state
                isSwappingEquipment = false; // FINALLY clear flag
                equipmentInteractionStage = 3; // Done

            }

        }

    }

    // --- Dynamic Rest Custom Screen ---

    private static volatile long cachedRestEndTimeMs = 0;

    private static class DynamicRestScreen extends Screen {

        private final long restEndTimeMs;

        private boolean reconnecting = false;

        protected DynamicRestScreen(long restEndTimeMs) {

            super(Component.literal("Dynamic Rest"));

            this.restEndTimeMs = restEndTimeMs;

        }

        @Override

        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

            // Render basic dirty background

            this.renderTransparentBackground(graphics);

            super.render(graphics, mouseX, mouseY, partialTick);

            long remainingMs = Math.max(0, restEndTimeMs - System.currentTimeMillis());

            long totalSeconds = remainingMs / 1000;

            long minutes = totalSeconds / 60;

            long seconds = totalSeconds % 60;

            String title = "\u00A7bDynamic Rest";

            String subTitle = String.format("\u00A7eRemaining on rest: %02d:%02d", minutes, seconds);

            int width = this.width;

            int height = this.height;

            graphics.drawCenteredString(this.font, title, width / 2, height / 2 - 20, 0xFFFFFF);

            graphics.drawCenteredString(this.font, subTitle, width / 2, height / 2, 0xFFFFFF);

        }

        @Override

        public void tick() {

            super.tick();

        }

        @Override

        public boolean shouldCloseOnEsc() {

            // Keep the default logic, or return false to forbid escaping (forcing people to

            // click cancel/multiplayer to abort)

            return true;

        }

    }

    private void releaseMovementKeys(Minecraft mc) {

        if (mc.options == null)

            return;

        KeyMapping.set(mc.options.keyUp.getDefaultKey(), false);

        KeyMapping.set(mc.options.keyDown.getDefaultKey(), false);

        KeyMapping.set(mc.options.keyLeft.getDefaultKey(), false);

        KeyMapping.set(mc.options.keyRight.getDefaultKey(), false);

        KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);

        KeyMapping.set(mc.options.keyShift.getDefaultKey(), false);

    }

    private void moveTowards(Minecraft mc, Vec3 target) {

        if (mc.player == null)

            return;

        double deltaX = target.x - mc.player.getX();

        double deltaZ = target.z - mc.player.getZ();

        // Calculate angle to target in degrees (0 to 360)

        double angleToTarget = Math.toDegrees(Math.atan2(-deltaX, deltaZ));

        float playerYaw = mc.player.getYRot();

        // Calculate relative angle (-180 to 180)

        double relativeAngle = net.minecraft.util.Mth.wrapDegrees(angleToTarget - playerYaw);

        // Movement Key Mapping logic:

        // Forward (W): -45 to 45

        // Backward (S): 135 to -135

        // Left (A): -135 to -45

        // Right (D): 45 to 135

        boolean w = relativeAngle > -67.5 && relativeAngle < 67.5;

        boolean s = relativeAngle > 112.5 || relativeAngle < -112.5;

        boolean a = relativeAngle > -157.5 && relativeAngle < -22.5;

        boolean d = relativeAngle > 22.5 && relativeAngle < 157.5;

        KeyMapping.set(mc.options.keyUp.getDefaultKey(), w);

        KeyMapping.set(mc.options.keyDown.getDefaultKey(), s);

        KeyMapping.set(mc.options.keyLeft.getDefaultKey(), a);

        KeyMapping.set(mc.options.keyRight.getDefaultKey(), d);

        // Vertical Movement

        double deltaY = target.y - mc.player.getY();

        boolean jump = deltaY > 0.3;

        boolean sneak = deltaY < -0.3;

        KeyMapping.set(mc.options.keyJump.getDefaultKey(), jump);

        KeyMapping.set(mc.options.keyShift.getDefaultKey(), sneak);

    }

    private Vec3 getDetourTarget(Minecraft mc, Vec3 target) {

        if (mc.level == null || mc.player == null)

            return target;

        Vec3 start = mc.player.getEyePosition();

        // Raytrace to check for blocks between eye and target

        net.minecraft.world.phys.BlockHitResult result = mc.level.clip(new net.minecraft.world.level.ClipContext(

                start, target,

                net.minecraft.world.level.ClipContext.Block.COLLIDER,

                net.minecraft.world.level.ClipContext.Fluid.NONE,

                mc.player));

        if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {

            return target;

        }

        // If blocked, search for a detour point

        BlockPos playerPos = mc.player.blockPosition();

        // Search offsets: favored directions (higher, or closer to target)

        int[][] searchOffsets = {

                { 0, 1, 0 }, // Up

                { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, // Horizontal

                { 1, 1, 0 }, { -1, 1, 0 }, { 0, 1, 1 }, { 0, 1, -1 }, // Up-Diagonal

                { 0, 2, 0 }, // High Up

                { 1, -1, 0 }, { -1, -1, 0 }, { 0, -1, 1 }, { 0, -1, -1 } // Down-Diagonal

        };

        for (int[] off : searchOffsets) {

            BlockPos check = playerPos.offset(off[0], off[1], off[2]);

            // Ensure the detour point is air and has a clear path from us

            if (mc.level.getBlockState(check).isAir()) {

                Vec3 detourVec = new Vec3(check.getX() + 0.5, check.getY() + 0.5, check.getZ() + 0.5);

                // Final check: is this detour point clear from our eyes?

                net.minecraft.world.phys.BlockHitResult detourRes = mc.level

                        .clip(new net.minecraft.world.level.ClipContext(

                                start, detourVec,

                                net.minecraft.world.level.ClipContext.Block.COLLIDER,

                                net.minecraft.world.level.ClipContext.Fluid.NONE,

                                mc.player));

                if (detourRes.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {

                    mc.player.displayClientMessage(Component.literal("\u00A77Approach Detour: Avoiding blockage..."),

                            true);

                    return detourVec;

                }

            }

        }

        return target; // Fallback

    }

    private void executeRodSequence(Minecraft client) {
        client.player.displayClientMessage(Component.literal("\u00A7eExecuting Rod Swap sequence..."), true);
        client.execute(() -> {
            for (int i = 0; i < 9; i++) {
                String rodItemName = client.player.getInventory().getItem(i).getHoverName().getString().toLowerCase();
                if (rodItemName.contains("rod")) {
                    ((com.ihanuat.mod.mixin.AccessorInventory) client.player.getInventory()).setSelected(i);
                    break;
                }
            }
        });
        try {
            Thread.sleep(500);
            client.execute(() -> client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND));
            Thread.sleep(400);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        resumeAfterPrepSwap(client);
    }

    private void triggerWardrobeSwap(Minecraft client) {
        targetWardrobeSlot = MacroConfig.wardrobeSlotPest;
        isSwappingWardrobe = true;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        shouldRestartFarmingAfterSwap = true;
        sendCommand(client, "/wardrobe");
    }

    private void resumeAfterPrepSwap(Minecraft client) {
        swapToFarmingTool(client);
        currentState = MacroState.FARMING;
        sendCommand(client, MacroConfig.restartScript);
    }

}