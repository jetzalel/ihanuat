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
    private static KeyMapping moveForwardKey;

    // Pest Control State
    private enum MacroState {
        OFF,
        FARMING,
        CLEANING
    }

    private static MacroState currentState = MacroState.OFF;

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

    private static ReturnState returnState = ReturnState.OFF;
    private static int returnTickCounter = 0;
    private static long lastAspectUsageTime = 0;
    private static int aspectUsageCount = 0;
    private static int flightToggleStage = 0;
    private static int flightToggleTicks = 0;
    private static long losEstablishedTime = 0; // Track how long LOS has been clear
    private static long stateStartTime = 0; // Generic timer for states

    // Return Sequence Look Target
    private static Vec3 returnLookTarget = null;

    // Movement Simulation
    private static boolean isSimulatingMove = false;
    private static Vec3 startMovePos;
    private static final double MOVE_TARGET_DISTANCE = 5.0;

    // Flight Stop Logic
    private static boolean isStoppingFlight = false;
    private static int flightStopStage = 0;
    private static int flightStopTicks = 0;

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
    private static String currentInfestedPlot = null;
    private static boolean isCleaningInProgress = false;
    private static boolean isHandlingMessage = false;
    private static int targetWardrobeSlot = -1;
    private static boolean prepSwappedForCurrentPestCycle = false;
    private static boolean isSwappingWardrobe = false;
    private static long wardrobeInteractionTime = 0;
    private static int wardrobeInteractionStage = 0;
    private static boolean shouldRestartFarmingAfterSwap = false;
    private static long wardrobeOpenPendingTime = 0;
    private static int wardrobeCleanupTicks = 0;
    private static boolean isStartingFlight = false;

    // Equipment Swap Logic
    private static boolean isSwappingEquipment = false;
    private static int equipmentInteractionStage = 0;
    private static long equipmentInteractionTime = 0;
    private static boolean swappingToPestGear = false;
    private static int equipmentTargetIndex = 0; // 0-3 for the 4 pieces

    @Override
    public void onInitializeClient() {
        // Load Config
        MacroConfig.load();

        KeyMapping.Category category = new KeyMapping.Category(Identifier.fromNamespaceAndPath("ihanuat", "main"));

        configKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ihanuat.config",
                GLFW.GLFW_KEY_O,
                category));

        moveForwardKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ihanuat.move_forward",
                GLFW.GLFW_KEY_I,
                category));

        KeyMapping startScriptKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ihanuat.start_script",
                GLFW.GLFW_KEY_K,
                category));

        // Centralized Chat Listener
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (isHandlingMessage)
                return;
            try {
                isHandlingMessage = true;
                String text = message.getString();
                String lowerText = text.toLowerCase();

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
            } finally {
                isHandlingMessage = false;
            }
        });

        // Config & Script Toggle Keys (Start Tick)
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;

            while (configKey.consumeClick()) {
                client.setScreen(createConfigScreen(client.screen));
            }

            while (startScriptKey.consumeClick()) {
                if (currentState == MacroState.OFF) {
                    currentState = MacroState.FARMING;
                    if (client.getConnection() != null) {
                        swapToFarmingTool(client);
                        sendCommand(client, ".ez-startscript netherwart:1");
                    }
                    client.player.displayClientMessage(Component.literal("Â§aMacro Started: Farming Mode"), true);
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
                handleEquipmentMenu(client, (AbstractContainerScreen<?>) client.screen);
            }

            // Automated Return to Start (Distance-based)
            if (currentState == MacroState.FARMING && returnState == ReturnState.OFF && MacroConfig.endPos != null) {
                Vec3 endVec = new Vec3(MacroConfig.endPos.getX() + 0.5, MacroConfig.endPos.getY(),
                        MacroConfig.endPos.getZ() + 0.5);
                if (client.player.position().distanceTo(endVec) <= 1.0) {
                    client.player.displayClientMessage(Component.literal("Â§6End Position reached. Stopping script..."),
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
            if (shouldRestartFarmingAfterSwap && !isSwappingWardrobe && client.screen == null) {
                if (wardrobeInteractionTime != 0 && System.currentTimeMillis() - wardrobeInteractionTime >= 500) {
                    swapToFarmingTool(client);
                    if (isInEndRow(client)) {
                        client.player.displayClientMessage(
                                Component.literal(
                                        "Â§cDetected same row as End Position after swap! Triggering early return..."),
                                true);
                        isCleaningInProgress = false;
                        returnState = ReturnState.TP_START;
                        returnTickCounter = 0;
                        currentState = MacroState.OFF; // Ensure farming doesn't restart
                    } else {
                        sendCommand(client, ".ez-startscript netherwart:1");
                        currentState = MacroState.FARMING;
                        isCleaningInProgress = false; // Reset flag after swap logic finish
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
                        client.player.displayClientMessage(Component.literal("Â§bFlight stop sequence finished."),
                                true);
                    }
                }
            }

            // Flight Start Logic (Tick-based double jump)
            if (currentState != MacroState.OFF && isStartingFlight) {
                if (performFlightToggle(client, true)) {
                    isStartingFlight = false;
                    flightToggleStage = 0;
                    flightToggleTicks = 0;
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
                                                    "Â§aStart position set to: Â§f"
                                                            + MacroConfig.startPos.toShortString()
                                                            + " Â§7(Automatic Warp Plot: Â§e" + MacroConfig.startPlot
                                                            + "Â§7)"));
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
                                                    "Â§cEnd position set to: Â§f" + MacroConfig.endPos.toShortString()
                                                            + " Â§7(Plot: " + MacroConfig.endPlot + ")"));
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
                // mc.player.displayClientMessage(Component.literal("§aRotation complete."),
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

            // DEBUG: Show tracking info on screen
            mc.player.displayClientMessage(Component.literal(
                    String.format("§dTRACK: spd=%d step=%.2f diff=%.1f yaw=%.1f->%.1f",
                            MacroConfig.rotationSpeed, maxStep, totalDiff, curYaw, newYaw)),
                    true);

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

                mc.player.displayClientMessage(Component.literal("Â§dAspect Teleport! Switching to slot " + (i + 1)),
                        true);

                // Simulate Right Click
                if (mc.gameMode != null) {
                    mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                return;
            }
        }
        mc.player.displayClientMessage(Component.literal("Â§c'Aspect of the' item not found in hotbar!"), true);
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

                    String clean = name.replaceAll("Â§[0-9a-fk-or]", "").trim();
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
                    client.player.displayClientMessage(Component.literal("Â§aEquipped Farming Tool: " + name), true);
                    return;
                }
            }
        }
        client.player.displayClientMessage(Component.literal("Â§cNo farming tool found in hotbar!"), true);
    }

    private void checkTabListForPests(Minecraft client) {
        if (client.getConnection() == null || isCleaningInProgress)
            return;

        java.util.Collection<net.minecraft.client.multiplayer.PlayerInfo> players = client.getConnection()
                .getListedOnlinePlayers();
        int aliveCount = -1;
        String firstPlot = null;

        for (net.minecraft.client.multiplayer.PlayerInfo info : players) {
            String name = "";
            if (info.getTabListDisplayName() != null) {
                name = info.getTabListDisplayName().getString();
            } else if (info.getProfile() != null) {
                name = String.valueOf(info.getProfile());
            }

            String clean = name.replaceAll("Â§[0-9a-fk-or]", "").trim();
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

                if (currentState == MacroState.FARMING && cooldownSeconds != -1
                        && cooldownSeconds > 0 && !prepSwappedForCurrentPestCycle) {

                    boolean shouldEquipOption2 = MacroConfig.autoEquipment && cooldownSeconds <= 170;
                    boolean shouldWardrobeOnly = !MacroConfig.autoEquipment && MacroConfig.autoWardrobe
                            && cooldownSeconds <= 8;

                    if (shouldEquipOption2 || shouldWardrobeOnly) {
                        String msg = shouldEquipOption2 ? "§ePest cooldown <= 2m 50s. Equipping Option 2..."
                                : "§ePest cooldown <= 8s. Swapping Wardrobe...";
                        client.player.displayClientMessage(Component.literal(msg), true);

                        prepSwappedForCurrentPestCycle = true;

                        new Thread(() -> {
                            try {
                                sendCommand(client, ".ez-stopscript");
                                Thread.sleep(500); // Wait for script to stop

                                if (shouldEquipOption2) {
                                    ensureEquipment(client, false); // Option 2 (farmTargets)
                                    Thread.sleep(1000); // 1s wait for menu
                                    while (isSwappingEquipment)
                                        Thread.sleep(50);
                                    Thread.sleep(500); // 500ms buffer
                                }

                                if (MacroConfig.autoWardrobe) {
                                    targetWardrobeSlot = 2;
                                    isSwappingWardrobe = true;
                                    wardrobeInteractionTime = 0;
                                    wardrobeInteractionStage = 0;
                                    shouldRestartFarmingAfterSwap = true;
                                    sendCommand(client, "/wardrobe");
                                } else {
                                    // Resume farming if no wardrobe swap
                                    swapToFarmingTool(client);
                                    sendCommand(client, ".ez-startscript netherwart:1");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                }
            }

            if (clean.startsWith("Plots:")) {
                java.util.regex.Matcher plotMatcher = PLOTS_PATTERN.matcher(clean);
                if (plotMatcher.find()) {
                    firstPlot = plotMatcher.group(1);
                }
            }
        }

        if (aliveCount >= MacroConfig.pestThreshold && firstPlot != null) {
            isCleaningInProgress = true;
            currentState = MacroState.CLEANING;
            currentInfestedPlot = firstPlot;

            client.player.displayClientMessage(
                    Component.literal("Â§cPests detected (" + aliveCount + "). Starting cleaning sequence on Plot "
                            + currentInfestedPlot),
                    true);

            new Thread(() -> {
                try {
                    if (MacroConfig.autoWardrobe) {
                        client.player.displayClientMessage(
                                Component.literal("Â§eStarting cleaning sequence. Swapping to Wardrobe Slot 1..."),
                                true);
                        ensureWardrobeSlot(client, 1);
                        Thread.sleep(750); // Wait for menu interaction
                    }

                    // Simple 0.5s wait before starting cleaning sequence
                    Thread.sleep(500);

                    if (currentState == MacroState.OFF)
                        return;

                    // Direct sendChat to bypass the 250ms throttler
                    if (client.getConnection() != null) {
                        client.getConnection().sendChat(".ez-stopscript");
                        lastCommandTime = System.currentTimeMillis();
                    }

                    Thread.sleep(500);
                    if (currentState == MacroState.OFF)
                        return;

                    sendCommand(client, "/setspawn");
                    Thread.sleep(500);
                    if (currentState == MacroState.OFF)
                        return;

                    // Swap to Pest Gear before flight
                    if (MacroConfig.autoEquipment) {
                        client.player.displayClientMessage(Component.literal("Â§eSwapping to Pest Equipment..."), true);
                        ensureEquipment(client, true);
                        Thread.sleep(1000); // Base wait for menu opening
                        while (isSwappingEquipment)
                            Thread.sleep(50);
                    }

                    // Toggle Flight (Double Jump)
                    isStartingFlight = true;
                    Thread.sleep(600); // Wait for double jump sequence
                    while (isStartingFlight)
                        Thread.sleep(50);

                    // Always warp directly to the plot
                    sendCommand(client, "/tptoplot " + currentInfestedPlot);

                    Thread.sleep(1150); // Ensure warp is complete (750ms base + 400ms buffer)
                    if (currentState == MacroState.OFF)
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

        client.getConnection().sendChat(cmd);
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
                    returnTickCounter = 0;
                }
                break;

            case FLIGHT_START:
                if (performFlightToggle(mc, true)) {
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

                // Track target (non-blocking) — camera follows while player moves
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

                // Debug Info
                mc.player.displayClientMessage(Component.literal(
                        String.format("Â§eMode: HIGH | Dist: %.1f | LOS: %s | TgtY: %.1f",
                                distHigh, los ? "YES" : "NO", targetHigh.y)),
                        true);

                // Transition Condition: Distance Gate + LOS OR Forced Proximity Fallback
                if ((distHigh <= 15.0 && los) || distHigh <= 3.0) {
                    returnState = ReturnState.FLY_APPROACH;
                    aspectUsageCount = 0;
                    initiateReturnRotation(mc, startPosVec, 100);

                    if (los) {
                        mc.player.displayClientMessage(Component.literal("Â§aLOS Established. Approaching..."), true);
                    } else {
                        mc.player.displayClientMessage(Component.literal("Â§eProximity Fallback. Forcing Approach..."),
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

                // Debug Info
                mc.player.displayClientMessage(Component.literal(
                        String.format("Â§bMode: APPROACH | Dist: %.1f", distFinal)), true);

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
                        mc.player.displayClientMessage(Component.literal("Â§eSnapping to start position..."), true);
                        stateStartTime = System.currentTimeMillis();
                        return;
                    }

                    returnState = ReturnState.OFF;
                    currentState = MacroState.FARMING;
                    if (mc.getConnection() != null) {
                        swapToFarmingTool(mc);
                        mc.getConnection().sendChat(".ez-startscript netherwart:1");
                    }
                    mc.player.displayClientMessage(Component.literal("Â§aAuto-Restarting Macro: Farming Mode"), true);
                } else if (!onGround) {
                    // Still in the air, keep holding shift to descend
                    KeyMapping.set(mc.options.keyShift.getDefaultKey(), true);
                }
                break;
        }
    }

    // Helper: Row Safeguard
    private boolean isInEndRow(Minecraft client) {
        if (client.player == null || MacroConfig.endPos == null)
            return false;

        double currentX = client.player.getX();
        double currentZ = client.player.getZ();
        double endX = MacroConfig.endPos.getX() + 0.5;
        double endZ = MacroConfig.endPos.getZ() + 0.5;

        // Check if X or Z matches within 1.0 block
        boolean xMatch = Math.abs(currentX - endX) < 1.0;
        boolean zMatch = Math.abs(currentZ - endZ) < 1.0;

        return xMatch || zMatch;
    }

    // Helper: Perform Double Jump (Toggle Flight)
    // Returns TRUE when sequence finishes
    private boolean performFlightToggle(Minecraft mc, boolean enable) {
        flightToggleTicks++;
        if (mc.options.keyJump != null) {
            // Stage 0: Press Space
            if (flightToggleStage == 0) {
                KeyMapping.set(mc.options.keyJump.getDefaultKey(), true);
                if (flightToggleTicks >= 2) { // 100ms
                    flightToggleStage = 1;
                    flightToggleTicks = 0;
                }
            }
            // Stage 1: Release Space
            else if (flightToggleStage == 1) {
                KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);
                if (flightToggleTicks >= 3) { // 150ms gap
                    flightToggleStage = 2;
                    flightToggleTicks = 0;
                }
            }
            // Stage 2: Press Space
            else if (flightToggleStage == 2) {
                KeyMapping.set(mc.options.keyJump.getDefaultKey(), true);
                if (flightToggleTicks >= 2) { // 100ms
                    flightToggleStage = 3;
                    flightToggleTicks = 0;
                }
            }
            // Stage 3: Release Space and Finish
            else if (flightToggleStage == 3) {
                KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);
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
        client.player.displayClientMessage(Component.literal("Â§aPest cleaning finished detected."), true);

        new Thread(() -> {
            try {
                int visitors = getVisitorCount(client);
                if (visitors >= MacroConfig.visitorThreshold) {
                    client.player.displayClientMessage(
                            Component.literal(
                                    "Â§dVisitor Threshold Met (" + visitors + "). Starting Visitor Sequence..."),
                            true);

                    isStoppingFlight = true;
                    Thread.sleep(600);
                    while (isStoppingFlight)
                        Thread.sleep(50);

                    Thread.sleep(750);
                    swapToFarmingTool(client);
                    Thread.sleep(500);

                    sendCommand(client, ".ez-startscript misc:visitor");
                } else {
                    client.player.displayClientMessage(
                            Component.literal("Â§eVisitor count (" + visitors + ") low. Warping Garden."), true);

                    isStoppingFlight = true;
                    Thread.sleep(600);
                    while (isStoppingFlight)
                        Thread.sleep(50);

                    sendCommand(client, "/warp garden");
                    Thread.sleep(750);

                    finalizeReturnToFarm(client);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleVisitorScriptFinished(Minecraft client) {
        client.player.displayClientMessage(Component.literal("Â§aVisitor sequence complete. Returning to farm..."),
                true);
        new Thread(() -> {
            try {
                sendCommand(client, "/warp garden");
                Thread.sleep(1500);
                finalizeReturnToFarm(client);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void finalizeReturnToFarm(Minecraft client) {
        prepSwappedForCurrentPestCycle = false;
        if (currentState == MacroState.OFF)
            return;

        if (isInEndRow(client)) {
            client.player.displayClientMessage(
                    Component.literal("§cDetected same row as End Position! Triggering early return..."), true);
            isCleaningInProgress = false; // Reset flag before early return
            returnState = ReturnState.TP_START;
            returnTickCounter = 0;
        } else {
            swapToFarmingTool(client);
            sendCommand(client, ".ez-startscript netherwart:1");
            isCleaningInProgress = false; // Reset flag after script start
            currentState = MacroState.FARMING;
        }
    }

    private void stopMacro(Minecraft client) {
        if (currentState == MacroState.OFF && returnState == ReturnState.OFF)
            return;

        currentState = MacroState.OFF;
        returnState = ReturnState.OFF;
        isSimulatingMove = false;
        isStoppingFlight = false;
        flightStopStage = 0;
        flightStopTicks = 0;
        isRotating = false;
        isCleaningInProgress = false;
        prepSwappedForCurrentPestCycle = false;
        isSwappingWardrobe = false;
        shouldRestartFarmingAfterSwap = false;
        wardrobeOpenPendingTime = 0;
        wardrobeCleanupTicks = 0;
        targetWardrobeSlot = -1;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        returnLookTarget = null;

        // Release keys
        if (client.options != null) {
            KeyMapping.set(client.options.keyUp.getDefaultKey(), false);
            KeyMapping.set(client.options.keyDown.getDefaultKey(), false);
            KeyMapping.set(client.options.keyLeft.getDefaultKey(), false);
            KeyMapping.set(client.options.keyRight.getDefaultKey(), false);
            KeyMapping.set(client.options.keyJump.getDefaultKey(), false);
            KeyMapping.set(client.options.keyShift.getDefaultKey(), false);
            KeyMapping.set(client.options.keyAttack.getDefaultKey(), false);
            KeyMapping.set(client.options.keyUse.getDefaultKey(), false);
        }

        if (client.getConnection() != null) {
            client.getConnection().sendChat(".ez-stopscript");
        }

        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("Â§cMacro Stopped Forcefully"), true);
        }
    }

    private Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Ihanuat Config"))
                .setSavingRunnable(MacroConfig::save);

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

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

        // Auto-Wardrobe Toggle
        general.addEntry(builder.getEntryBuilder()
                .startBooleanToggle(Component.literal("Auto-Wardrobe"), MacroConfig.autoWardrobe)
                .setDefaultValue(false)
                .setSaveConsumer(newValue -> MacroConfig.autoWardrobe = newValue)
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

        // Start Position Capture
        general.addEntry(new ButtonEntry(
                Component.literal("Â§eCapture Start Position"),
                Component.literal("Â§7Current: Â§f" + MacroConfig.startPos.toShortString() + " Â§7Plot: Â§f"
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
                Component.literal("Â§bCapture End Position"),
                Component.literal("Â§7Current: Â§f" + MacroConfig.endPos.toShortString() + " Â§7Plot: Â§f"
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
     * Custom Cloth Config entry for a simple action button.
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
        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        shouldRestartFarmingAfterSwap = false; // Default, can be overridden by caller
        client.player.connection.sendChat("/wardrobe");
    }

    private void handleWardrobeMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingWardrobe || targetWardrobeSlot == -1)
            return;

        String title = screen.getTitle().getString();
        if (!title.contains("Wardrobe"))
            return;

        // Initialize time on first frame the screen is detected
        if (wardrobeInteractionTime == 0) {
            wardrobeInteractionTime = System.currentTimeMillis();
            return;
        }

        // Wait delays based on stage
        long currentDelay = 750; // Change to set: 750ms
        if (wardrobeInteractionStage == 1) {
            currentDelay = 500; // Change to close: 500ms
        }

        if (System.currentTimeMillis() - wardrobeInteractionTime < currentDelay) {
            return;
        }

        // Search for the slot button
        Slot targetSlotObj = null;
        Slot closeSlotObj = null;

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem())
                continue;
            String itemName = slot.getItem().getHoverName().getString();

            if (itemName.contains("Slot " + targetWardrobeSlot + ":")) {
                targetSlotObj = slot;
                if (itemName.contains("Equipped")) {
                    // Already equipped, just close
                    isSwappingWardrobe = false;
                    targetWardrobeSlot = -1;
                    wardrobeCleanupTicks = 10;
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
                // Click the slot
                client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, targetSlotObj.index, 0,
                        ClickType.PICKUP, client.player);
                wardrobeInteractionTime = System.currentTimeMillis();
                wardrobeInteractionStage = 1; // Wait for close click
            } else if (wardrobeInteractionStage == 1) {
                // Phase 1: Click the Close button
                if (closeSlotObj != null) {
                    client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, closeSlotObj.index, 0,
                            ClickType.PICKUP, client.player);
                }

                wardrobeInteractionTime = System.currentTimeMillis();
                wardrobeInteractionStage = 2; // Move to protocol reset phase
            } else if (wardrobeInteractionStage == 2) {
                // Phase 2: Protocol-level reset to clear any potentially stuck cursor items
                int containerId = screen.getMenu().containerId;

                // Standard protocol reset: Click outside slot (-999) for both wardrobe and
                // player inventory
                client.gameMode.handleInventoryMouseClick(containerId, -999, 0, ClickType.PICKUP, client.player);
                client.gameMode.handleInventoryMouseClick(0, -999, 0, ClickType.PICKUP, client.player);

                // Ensure client-side carried state is clear
                if (client.player != null) {
                    if (client.player.containerMenu != null)
                        client.player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                    if (client.player.inventoryMenu != null)
                        client.player.inventoryMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                }

                isSwappingWardrobe = false;
                targetWardrobeSlot = -1;
                wardrobeInteractionTime = System.currentTimeMillis(); // Reset for post-close delay
                wardrobeInteractionStage = 3; // Move to final cleanup phase
                wardrobeCleanupTicks = 20; // Trigger extended restoration period (1s)

                // Explicitly close the GUI to ensure state synchronization
                client.setScreen(null);

                if (client.mouseHandler != null) {
                    client.mouseHandler.releaseMouse();
                }
            }
        }
    }

    private void ensureEquipment(Minecraft client, boolean toPest) {
        if (!MacroConfig.autoEquipment)
            return;
        isSwappingEquipment = true;
        swappingToPestGear = toPest;
        equipmentInteractionStage = 0;
        equipmentInteractionTime = 0;
        equipmentTargetIndex = 0;
        client.player.connection.sendChat("/eq");
    }

    private void handleEquipmentMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingEquipment)
            return;

        String title = screen.getTitle().getString();
        if (!title.contains("Equipment"))
            return;

        // Initialize time on first frame
        if (equipmentInteractionTime == 0) {
            equipmentInteractionTime = System.currentTimeMillis();
            return;
        }

        // 700ms delay between clicks
        if (System.currentTimeMillis() - equipmentInteractionTime < 700) {
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
            // Search for the current target piece group
            Slot targetSlotObj = null;
            String[] currentTargetGroup = swappingToPestGear ? pestTargets[equipmentTargetIndex]
                    : farmTargets[equipmentTargetIndex];

            for (Slot slot : screen.getMenu().slots) {
                if (!slot.hasItem() || slot.index < 54)
                    continue;
                String itemName = slot.getItem().getHoverName().getString();

                for (String pattern : currentTargetGroup) {
                    if (itemName.contains(pattern)) {
                        targetSlotObj = slot;
                        break;
                    }
                }
                if (targetSlotObj != null)
                    break;
            }

            if (targetSlotObj != null) {
                client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, targetSlotObj.index, 0,
                        ClickType.PICKUP, client.player);
                equipmentInteractionTime = System.currentTimeMillis();
                equipmentTargetIndex++;
            } else {
                // If not found, skip to next index to avoid getting stuck
                equipmentTargetIndex++;
                equipmentInteractionTime = System.currentTimeMillis();
            }
        } else {
            // All 4 clicks done, close and cleanup
            isSwappingEquipment = false;
            wardrobeCleanupTicks = 10;
            client.setScreen(null);
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
                    mc.player.displayClientMessage(Component.literal("Â§7Approach Detour: Avoiding blockage..."), true);
                    return detourVec;
                }
            }
        }

        return target; // Fallback
    }
}
