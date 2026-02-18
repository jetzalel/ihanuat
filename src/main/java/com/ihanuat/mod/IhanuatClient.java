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
    private static RotationUtils.Rotation controlRot;
    private static long rotationStartTime;
    private static long rotationDuration;

    // Regex for Pest Control
    private static int tickCounter = 0;
    private static final java.util.regex.Pattern PESTS_ALIVE_PATTERN = java.util.regex.Pattern
            .compile("Alive:\\s*(\\d+)");
    private static final java.util.regex.Pattern PLOTS_PATTERN = java.util.regex.Pattern.compile("Plots:\\s*(\\d+)");
    private static final java.util.regex.Pattern VISITORS_PATTERN = java.util.regex.Pattern
            .compile("Visitors:\\s*\\(?(\\d+)\\)?");

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

        // Chat Listener for "Cleaning Finished"
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString();
            if (text.contains("Pest Cleaner") && text.contains("Finished")) {
                // Relaxed detection: Check for "Pest Cleaner" and "Finished" independently
                if (currentState == MacroState.CLEANING) {
                    Minecraft.getInstance().player
                            .displayClientMessage(Component.literal("§aPest cleaning finished detected."), true);

                    // Check for Visitors before warping
                    int visitors = getVisitorCount(Minecraft.getInstance());
                    if (visitors >= MacroConfig.visitorThreshold) {
                        Minecraft.getInstance().player
                                .displayClientMessage(
                                        Component.literal("§dVisitor Threshold Met (" + visitors + " >= "
                                                + MacroConfig.visitorThreshold + "). Starting Visitor Script..."),
                                        true);

                        // Start Visitor Sequence via Thread
                        if (currentState == MacroState.CLEANING) {
                            new Thread(() -> {
                                try {
                                    // 1. Stop Flight (Double Jump)
                                    isStoppingFlight = true;
                                    sleepRandom(1000, 1200); // Wait for flight stop
                                    while (isStoppingFlight) {
                                        if (currentState == MacroState.OFF)
                                            return;
                                        Thread.sleep(50);
                                    }

                                    // 1.5. Wait 1500ms buffer as requested
                                    Thread.sleep(1500);
                                    if (currentState == MacroState.OFF)
                                        return;

                                    // 2. Ensure Farming Tool
                                    if (Minecraft.getInstance().player != null) {
                                        swapToFarmingTool(Minecraft.getInstance());
                                    }
                                    sleepRandom(1000, 1500);
                                    if (currentState == MacroState.OFF)
                                        return;

                                    // 2. Start Script
                                    if (Minecraft.getInstance().player != null
                                            && Minecraft.getInstance().getConnection() != null) {
                                        Minecraft.getInstance().getConnection()
                                                .sendChat(".ez-startscript misc:visitor");
                                    }

                                    // 3. Wait for "Visitor Script Finished" (Handled in chat listener)
                                    // For now, we just let the chat listener handle the transition to /warp garden
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }).start();
                            return; // Do NOT warp garden yet
                        }
                    } else {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§eVisitor count (" + visitors + ") below threshold. Skipping."),
                                true);

                        // Original Logic (Moved inside else)
                        if (currentState == MacroState.CLEANING) {
                            new Thread(() -> {
                                try {
                                    // Restore flight stop logic (User requested to keep it)
                                    isStoppingFlight = true;
                                    sleepRandom(1000, 1200); // Wait for flight stop
                                    while (isStoppingFlight) {
                                        if (currentState == MacroState.OFF)
                                            return;
                                        Thread.sleep(50); // Wait for flight stop to complete
                                    }

                                    if (Minecraft.getInstance().player != null
                                            && Minecraft.getInstance().getConnection() != null) {
                                        Minecraft.getInstance().getConnection().sendChat("/warp garden");
                                    }
                                    sleepRandom(1000, 1500);
                                    if (currentState == MacroState.OFF)
                                        return;

                                    if (Minecraft.getInstance().player != null
                                            && Minecraft.getInstance().getConnection() != null) {
                                        if (isInEndRow(Minecraft.getInstance())) {
                                            Minecraft.getInstance().player.displayClientMessage(
                                                    Component.literal(
                                                            "§cDetected same row as End Position! Triggering early return..."),
                                                    true);
                                            returnState = ReturnState.TP_START;
                                            returnTickCounter = 0;
                                        } else {
                                            swapToFarmingTool(Minecraft.getInstance());
                                            Minecraft.getInstance().getConnection()
                                                    .sendChat(".ez-startscript netherwart:1");
                                            currentState = MacroState.FARMING;
                                        }
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                    }
                }
            }

            // Hook for Visitor Script Finished
            // Prevent recursion: Check for "visitor" and "finished", but ignore our own
            // confirmation message
            // Also ensure we are in the correct state
            if (currentState == MacroState.CLEANING && text.toLowerCase().contains("visitor")
                    && text.toLowerCase().contains("finished")
                    && !text.contains("sequence complete")) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§aVisitor sequence complete. Returning to farm..."), true);
                // We rely on currentState still being CLEANING or similar tracking
                // But visitor script runs during CLEANING state in our logic above
                // Let's just blindly trigger the return-to-garden logic

                new Thread(() -> {
                    try {
                        // User requested to remove double jump (stop flight) from here
                        // Proceed directly to warp garden

                        if (Minecraft.getInstance().player != null && Minecraft.getInstance().getConnection() != null) {
                            Minecraft.getInstance().getConnection().sendChat("/warp garden");
                        }
                        sleepRandom(1000, 1500);
                        if (currentState == MacroState.OFF)
                            return;

                        if (Minecraft.getInstance().player != null && Minecraft.getInstance().getConnection() != null) {
                            if (isInEndRow(Minecraft.getInstance())) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal(
                                                "§cDetected same row as End Position! Triggering early return..."),
                                        true);
                                returnState = ReturnState.TP_START;
                                returnTickCounter = 0;
                            } else {
                                swapToFarmingTool(Minecraft.getInstance());
                                Minecraft.getInstance().getConnection().sendChat(".ez-startscript netherwart:1");
                                currentState = MacroState.FARMING;
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
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

            while (startScriptKey.consumeClick()) {
                if (currentState == MacroState.OFF) {
                    currentState = MacroState.FARMING;
                    if (client.getConnection() != null) {
                        swapToFarmingTool(client);
                        client.getConnection().sendChat(".ez-startscript netherwart:1");
                    }
                    client.player.displayClientMessage(Component.literal("§aMacro Started: Farming Mode"), true);
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

            // Automated Return to Start (Distance-based)
            if (currentState == MacroState.FARMING && returnState == ReturnState.OFF && MacroConfig.endPos != null) {
                Vec3 endVec = new Vec3(MacroConfig.endPos.getX() + 0.5, MacroConfig.endPos.getY(),
                        MacroConfig.endPos.getZ() + 0.5);
                if (client.player.position().distanceTo(endVec) <= 1.0) {
                    client.player.displayClientMessage(Component.literal("§6End Position reached. Stopping script..."),
                            true);

                    // Immediately stop script
                    if (client.getConnection() != null) {
                        client.getConnection().sendChat(".ez-stopscript");
                    }

                    // 250ms pre-sequence wait
                    returnState = ReturnState.TP_PRE_WAIT;
                    stateStartTime = System.currentTimeMillis();
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

            // Handle 'I' key press to start simulation
            while (moveForwardKey.consumeClick()) {
                if (!isSimulatingMove) {
                    isSimulatingMove = true;
                    startMovePos = client.player.position();
                    client.player.displayClientMessage(Component.literal("§aStarting 5-block movement..."), true);
                }
            }

            // Flight Stop Logic (Tick-based)
            if (isStoppingFlight) {
                flightStopTicks++;
                Minecraft mc = client;
                if (mc.options.keyJump != null) {
                    // Stage 0: Press Space (Hold for ~2 ticks / ~100ms)
                    if (flightStopStage == 0) {
                        KeyMapping.set(mc.options.keyJump.getDefaultKey(), true);
                        if (flightStopTicks >= 2) {
                            flightStopStage = 1;
                            flightStopTicks = 0;
                        }
                    }
                    // Stage 1: Release Space (Gap for ~3 ticks / ~150ms)
                    else if (flightStopStage == 1) {
                        KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);
                        if (flightStopTicks >= 3) {
                            flightStopStage = 2;
                            flightStopTicks = 0;
                        }
                    }
                    // Stage 2: Press Space (Hold for ~2 ticks / ~100ms)
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
                        client.player.displayClientMessage(Component.literal("§bFlight stop sequence finished."), true);
                    }
                }
            }

            // Movement simulation logic
            if (isSimulatingMove) {
                double distanceTravelled = client.player.position().distanceTo(startMovePos);

                if (distanceTravelled >= MOVE_TARGET_DISTANCE) {
                    isSimulatingMove = false;
                    // Stop simulating 'W' and release all suppressed keys
                    KeyMapping.set(client.options.keyUp.getDefaultKey(), false);
                    client.player.displayClientMessage(Component.literal("§6Movement finished."), true);
                } else {
                    // Simulate pressing 'W'
                    KeyMapping.set(client.options.keyUp.getDefaultKey(), true);

                    // Suppress manual input: A, S, D, Left Click, Right Click
                    KeyMapping.set(client.options.keyLeft.getDefaultKey(), false);
                    while (client.options.keyLeft.consumeClick())
                        ;

                    KeyMapping.set(client.options.keyDown.getDefaultKey(), false);
                    while (client.options.keyDown.consumeClick())
                        ;

                    KeyMapping.set(client.options.keyRight.getDefaultKey(), false);
                    while (client.options.keyRight.consumeClick())
                        ;

                    KeyMapping.set(client.options.keyAttack.getDefaultKey(), false);
                    while (client.options.keyAttack.consumeClick())
                        ;

                    KeyMapping.set(client.options.keyUse.getDefaultKey(), false);
                    while (client.options.keyUse.consumeClick())
                        ;
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
                                    // Sanity: Strip "Plot" if it somehow leaked through
                                    plot = plot.toLowerCase().replaceAll("plot", "").trim();
                                    MacroConfig.startPlot = plot;
                                    context.getSource()
                                            .sendFeedback(Component.literal(
                                                    "§aStart position set to: §f" + MacroConfig.startPos.toShortString()
                                                            + " §7(Automatic Warp Plot: §e" + MacroConfig.startPlot
                                                            + "§7)"));
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
                                                    "§cEnd position set to: §f" + MacroConfig.endPos.toShortString()
                                                            + " §7(Plot: " + MacroConfig.endPlot + ")"));
                                }
                                // Return 1 to indicate success
                                return 1;
                            })));

            dispatcher.register(
                    ClientCommandManager.literal("visitortest").executes(context -> {
                        Minecraft client = Minecraft.getInstance();
                        int count = getVisitorCount(client);
                        context.getSource()
                                .sendFeedback(Component.literal("§e[Debug] Current Visitor Count: " + count));
                        return 1;
                    }));
        });
    }

    // Return Sequence State

    public static void updateRotation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        // Priority 1: Debug 'R' Bezier Rotation
        if (isRotating && startRot != null && targetRot != null && controlRot != null) {
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
                    // Keep looking at the final Bezier point for this frame
                }
            }

            RotationUtils.Rotation current = RotationUtils.calculateBezierPoint(t, startRot, targetRot, controlRot);
            mc.player.setYRot(current.yaw);
            mc.player.setXRot(current.pitch);
            // Snap for Bezier too just in case
            mc.player.yRotO = current.yaw;
            mc.player.xRotO = current.pitch;
            return;
        }

        // Priority 2: Return Sequence Tracking (Smoothed)
        if (returnState != ReturnState.OFF && returnLookTarget != null) {
            // 1. Calculate Target Rotation
            RotationUtils.Rotation target = RotationUtils.calculateLookAt(mc.player.getEyePosition(), returnLookTarget);

            // 2. Smoothly Interpolate (Frame-based)
            // Use a factor (0.4 roughly corresponds to a 2-3 frame lag, smoothing out
            // jitters)
            float smoothFactor = 0.4f;

            float curYaw = mc.player.getYRot();
            float curPitch = mc.player.getXRot();

            // Handle Yaw Wrapping (shortest path)
            float yawDiff = net.minecraft.util.Mth.wrapDegrees(target.yaw - curYaw);
            float newYaw = curYaw + yawDiff * smoothFactor;

            float pitchDiff = target.pitch - curPitch;
            float newPitch = curPitch + pitchDiff * smoothFactor;

            mc.player.setYRot(newYaw);
            mc.player.setXRot(newPitch);

            // Snap previous rotation to current to prevent engine-interpolation lag
            mc.player.yRotO = newYaw;
            mc.player.xRotO = newPitch;
            return;
        }
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

                mc.player.displayClientMessage(Component.literal("§dAspect Teleport! Switching to slot " + (i + 1)),
                        true);

                // Simulate Right Click
                if (mc.gameMode != null) {
                    mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                return;
            }
        }
        mc.player.displayClientMessage(Component.literal("§c'Aspect of the' item not found in hotbar!"), true);
    }

    private int getVisitorCount(Minecraft client) {
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

                    String clean = name.replaceAll("§[0-9a-fk-or]", "").trim();
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
                    client.player.displayClientMessage(Component.literal("§aEquipped Farming Tool: " + name), true);
                    return;
                }
            }
        }
        client.player.displayClientMessage(Component.literal("§cNo farming tool found in hotbar!"), true);
    }

    private void checkTabListForPests(Minecraft client) {
        if (client.getConnection() == null)
            return;

        java.util.Collection<net.minecraft.client.multiplayer.PlayerInfo> players = client.getConnection()
                .getListedOnlinePlayers();
        int aliveCount = -1;
        String firstPlot = null;
        boolean bonusInactive = false;

        for (net.minecraft.client.multiplayer.PlayerInfo info : players) {
            String name = "";
            if (info.getTabListDisplayName() != null) {
                name = info.getTabListDisplayName().getString();
            } else if (info.getProfile() != null) {
                // Fallback to simple string representation to avoid mapping issues
                name = String.valueOf(info.getProfile());
            }

            // Strip colors
            String clean = name.replaceAll("§[0-9a-fk-or]", "").trim();

            // Check "Alive: <N>"
            java.util.regex.Matcher aliveMatcher = PESTS_ALIVE_PATTERN.matcher(clean);
            if (aliveMatcher.find()) {
                try {
                    aliveCount = Integer.parseInt(aliveMatcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }

            // Check "Bonus: INACTIVE"
            if (clean.contains("Bonus:") && clean.contains("INACTIVE")) {
                bonusInactive = true;
            }

            // Check "Plots: <ID>, ..."
            if (clean.startsWith("Plots:")) {
                // Example: "Plots: 3, 5" -> match 3
                java.util.regex.Matcher plotMatcher = PLOTS_PATTERN.matcher(clean);
                if (plotMatcher.find()) {
                    firstPlot = plotMatcher.group(1);
                }
            }
        }

        if (aliveCount >= MacroConfig.pestThreshold && firstPlot != null) {
            // Trigger Pest Cleaning
            client.player.displayClientMessage(
                    Component.literal(
                            "§cPests detected (" + aliveCount + "). Starting cleaning sequence on Plot " + firstPlot),
                    true);
            currentState = MacroState.CLEANING;

            final String plotId = firstPlot;
            final boolean isBonusInactive = bonusInactive;

            // Use a separate thread to avoid blocking the main game loop during sleeps
            new Thread(() -> {
                try {
                    if (client.getConnection() != null) {
                        // Wardrobe Swap Detection Setup
                        java.util.concurrent.CountDownLatch wardrobeLatch = new java.util.concurrent.CountDownLatch(1);
                        java.util.concurrent.atomic.AtomicBoolean wardrobeDetected = new java.util.concurrent.atomic.AtomicBoolean(
                                false);

                        // Temporary listener for Wardrobe Swap
                        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME
                                .register((message, overlay) -> {
                                    String msg = message.getString().toLowerCase();
                                    if (msg.contains("wardrobe swap") && msg.contains("activated")) {
                                        wardrobeDetected.set(true);
                                    }
                                    if (msg.contains("wardrobe swap") && msg.contains("finished")) {
                                        wardrobeLatch.countDown();
                                    }
                                });

                        sleepRandom(4000, 4500); // Wait 4-4.5s before stopping script
                        if (currentState == MacroState.OFF)
                            return;

                        // Check if Wardrobe Swap was triggered during the wait
                        if (wardrobeDetected.get()) {
                            client.player.displayClientMessage(
                                    Component.literal("§eWardrobe Swap detected! Waiting for completion..."), true);
                            wardrobeLatch.await(30, java.util.concurrent.TimeUnit.SECONDS); // 30s timeout safety
                            Thread.sleep(200); // User requested 200ms delay after finish
                            if (currentState == MacroState.OFF)
                                return;
                        }

                        client.getConnection().sendChat(".ez-stopscript");
                        sleepRandom(1000, 1000);
                        if (currentState == MacroState.OFF)
                            return;

                        client.getConnection().sendChat("/setspawn");
                        sleepRandom(1000, 1000);
                        if (currentState == MacroState.OFF)
                            return;

                        // Start Script EARLY (so it is "on" for the warp sequence)
                        client.getConnection().sendChat(".ez-startscript misc:pestCleaner");
                        sleepRandom(1000, 1000);
                        if (currentState == MacroState.OFF)
                            return;

                        // Conditional Warp based on Bonus status
                        if (isBonusInactive) {
                            client.player.displayClientMessage(Component.literal("§eBonus Inactive! Warping to Barn."),
                                    true);
                            client.getConnection().sendChat("/tptoplot barn");

                            // Wait for "Thanks for the" chat message
                            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

                            // Register temporary listener
                            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME
                                    .register((message, overlay) -> {
                                        if (latch.getCount() > 0 && message.getString().contains("Thanks for the")) {
                                            latch.countDown();
                                        }
                                    });

                            // Wait with timeout
                            for (int i = 0; i < 200; i++) { // 10 seconds (200 * 50ms)
                                if (currentState == MacroState.OFF)
                                    return;
                                if (latch.await(50, java.util.concurrent.TimeUnit.MILLISECONDS))
                                    break;
                            }

                            if (currentState == MacroState.OFF)
                                return;

                            // IMMEDIATELY warp to infested plot (no delay)
                            client.getConnection().sendChat("/tptoplot " + plotId);
                        } else {
                            client.getConnection().sendChat("/tptoplot " + plotId);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void sleepRandom(int min, int max) throws InterruptedException {
        // Random sleep between min and max (inclusive)
        long sleepTime = min + (long) (Math.random() * (max - min + 1));
        Thread.sleep(sleepTime);
    }

    private void initiateReturnRotation(Minecraft mc, Vec3 targetPos, long duration) {
        if (mc.player == null)
            return;

        // 1. Clear the instant-look target so updateRotation uses isRotating logic
        returnLookTarget = null;

        // 2. Setup Bezier Rotation
        startRot = new RotationUtils.Rotation(mc.player.getYRot(), mc.player.getXRot());
        targetRot = RotationUtils.calculateLookAt(mc.player.getEyePosition(), targetPos);
        controlRot = RotationUtils.generateControlPoint(startRot, targetRot);

        rotationStartTime = System.currentTimeMillis();
        rotationDuration = duration;
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

                // Lock view to target (instant) after smooth rotation finished
                returnLookTarget = targetHigh;

                // Priority 1: Gated Rotation Wait
                if (isRotating) {
                    KeyMapping.set(mc.options.keyUp.getDefaultKey(), false); // Ensure W is off
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
                        String.format("§eMode: HIGH | Dist: %.1f | LOS: %s | TgtY: %.1f",
                                distHigh, los ? "YES" : "NO", targetHigh.y)),
                        true);

                // Transition Condition
                if (distHigh <= 15.0) {
                    if (los) {
                        returnState = ReturnState.FLY_APPROACH;
                        aspectUsageCount = 0;

                        initiateReturnRotation(mc, startPosVec, 100);

                        mc.player.displayClientMessage(Component.literal("§aLOS Stable. Rotating to Target..."), true);
                    }
                }

                // Phase 1 Action: > 15 blocks -> Aspect every 500ms
                if (distHigh > 15) {
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

                // 1. Disable W (Persistent)
                KeyMapping.set(mc.options.keyUp.getDefaultKey(), false);

                // 2. Lock View
                returnLookTarget = startPosVec2;

                // 3. Gated Action: Wait for Rotation
                if (isRotating) {
                    // Do nothing (W is off, Aspect is off)
                    return;
                }

                double distFinal = mc.player.position().distanceTo(startPosVec2);

                // Debug Info
                mc.player.displayClientMessage(Component.literal(
                        String.format("§bMode: APPROACH | Dist: %.1f", distFinal)), true);

                // 4. Action: Use Aspect if aligned and far enough
                // Gated: !isRotating (implied here) AND dist > 1.0
                if (distFinal > 1.0) {
                    long now = System.currentTimeMillis();
                    if (now - lastAspectUsageTime >= 500) { // Keep 500ms cooldown to avoid spam
                        useAspectItem();
                        lastAspectUsageTime = now;
                    }
                }

                if (distFinal < 1.0) {
                    KeyMapping.set(mc.options.keyUp.getDefaultKey(), false); // Release W
                    returnState = ReturnState.LANDING_SHIFT;
                    stateStartTime = System.currentTimeMillis();
                    returnLookTarget = null; // Stop looking
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
                // Wait 290ms after landing before restarting macro
                if (System.currentTimeMillis() - stateStartTime >= 290) {
                    returnState = ReturnState.OFF;
                    currentState = MacroState.FARMING;
                    if (mc.getConnection() != null) {
                        swapToFarmingTool(mc);
                        mc.getConnection().sendChat(".ez-startscript netherwart:1");
                    }
                    mc.player.displayClientMessage(Component.literal("§aAuto-Restarting Macro: Farming Mode"), true);
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
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player));

        // If we hit something, and it's not the target (block pos), then LOS is
        // blocked.
        // Actually clip returns MISS if nothing hit.
        // If it returns BLOCK, we hit something.
        if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            // Check if the hit block is close to target?
            // Actually user said "Glass and see through blocks count as blockage".
            // ClipContext.Block.COLLIDER includes glass? No, glass is usually not a
            // collider for movement but for rays check visual?
            // Actually, COLLIDER checks physical collision. VISUAL checks visual.
            // User said "glass ... count as blockage". Glass blocks movement? No, glass is
            // solid.
            // Wait, user said "yes glass and see through blocks count as a blockage".
            // If they mean "Line of Sight", usually means "Can I see it?".
            // But context is "flying towards it".
            // Glass blocks movement. So COLLIDER is correct.

            // If hit result is close to target, it's fine.
            // But clip() stops at the first block.
            // If result.getLocation() is close to target...
            // Let's rely on Type.MISS means clear path.
            return false;
        }
        return true;
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
            client.player.displayClientMessage(Component.literal("§cMacro Stopped Forcefully"), true);
        }
    }

    private Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Ihanuat Config"));

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

        // Start Position Capture
        general.addEntry(new ButtonEntry(
                Component.literal("§eCapture Start Position"),
                Component.literal("§7Current: §f" + MacroConfig.startPos.toShortString() + " §7Plot: §f"
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
                Component.literal("§bCapture End Position"),
                Component.literal("§7Current: §f" + MacroConfig.endPos.toShortString() + " §7Plot: §f"
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
}
