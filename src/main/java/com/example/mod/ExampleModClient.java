package com.example.mod;

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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ExampleModClient implements ClientModInitializer {

    private static KeyMapping configKey;
    private static KeyMapping moveForwardKey;

    // Pest Control State
    private enum MacroState {
        OFF,
        FARMING,
        CLEANING
    }

    private static MacroState currentState = MacroState.OFF;
    private static int tickCounter = 0;
    private static final java.util.regex.Pattern PESTS_ALIVE_PATTERN = java.util.regex.Pattern
            .compile("Alive:\\s*(\\d+)");
    private static final java.util.regex.Pattern PLOTS_PATTERN = java.util.regex.Pattern.compile("Plots:\\s*(\\d+)");

    // Movement simulation state
    private static boolean isSimulatingMove = false;
    private static net.minecraft.world.phys.Vec3 startMovePos = null;
    private static final double MOVE_TARGET_DISTANCE = 5.0;

    // Flight Stop State
    private static boolean isStoppingFlight = false;
    private static int flightStopTicks = 0;
    private static int flightStopStage = 0;

    @Override
    public void onInitializeClient() {
        // Load Config
        MacroConfig.load();

        KeyMapping.Category category = new KeyMapping.Category(Identifier.fromNamespaceAndPath("example-mod", "main"));

        configKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.example-mod.config",
                GLFW.GLFW_KEY_O,
                category));

        moveForwardKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.example-mod.move_forward",
                GLFW.GLFW_KEY_I,
                category));

        KeyMapping startScriptKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.example-mod.start_script",
                GLFW.GLFW_KEY_Y,
                category));

        // Chat Listener for "Cleaning Finished"
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (currentState == MacroState.CLEANING) {
                String text = message.getString();
                // Relaxed detection: Check for "Pest Cleaner" and "Finished" independently
                if (text.contains("Pest Cleaner") && text.contains("Finished")) {
                    // Cleaner finished, warp back and resume farming
                    Minecraft.getInstance().player
                            .displayClientMessage(Component.literal("§aPests cleaned. Resuming farming..."), true);

                    // Use a separate thread to avoid blocking the main game loop during sleeps
                    new Thread(() -> {
                        try {
                            // Trigger Flight Stop Sequence in Main Thread
                            isStoppingFlight = true;
                            // Wait for it to finish (approx 10 ticks = 500ms + buffer)
                            while (isStoppingFlight) {
                                Thread.sleep(50);
                            }

                            sleepRandom(1000, 1000); // 1s delay after flight stop

                            if (Minecraft.getInstance().getConnection() != null) {
                                Minecraft.getInstance().getConnection().sendChat("/warp garden");
                                sleepRandom(1000, 1000);
                                Minecraft.getInstance().getConnection().sendChat(".ez-startscript netherwart:1");
                            }

                            // Resume farming state ONLY after sequence completes
                            currentState = MacroState.FARMING;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            }
        });

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
                        client.getConnection().sendChat(".ez-startscript netherwart:1");
                    }
                    client.player.displayClientMessage(Component.literal("§aMacro Started: Farming Mode"), true);
                } else {
                    currentState = MacroState.OFF;
                    client.player.displayClientMessage(Component.literal("§cMacro Stopped"), true);
                }
            }

            // Check if Pause Menu is open to stop macro
            if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                if (currentState != MacroState.OFF) {
                    currentState = MacroState.OFF;
                    client.player.displayClientMessage(Component.literal("§cMacro Stopped via Escape (Pause Menu)"),
                            true);
                }
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
                    // We must both set the down state to false AND consume any clicks
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

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("setpos")
                    .then(ClientCommandManager.literal("start")
                            .executes(context -> {
                                Minecraft client = Minecraft.getInstance();
                                if (client.player != null) {
                                    MacroConfig.startPos = client.player.blockPosition();
                                    MacroConfig.startPlot = PlotUtils.getPlotName(client);
                                    context.getSource()
                                            .sendFeedback(Component.literal(
                                                    "§aStart position set to: §f" + MacroConfig.startPos.toShortString()
                                                            + " §7(Plot: " + MacroConfig.startPlot + ")"));
                                }
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("end")
                            .executes(context -> {
                                Minecraft client = Minecraft.getInstance();
                                if (client.player != null) {
                                    MacroConfig.endPos = client.player.blockPosition();
                                    MacroConfig.endPlot = PlotUtils.getPlotName(client);
                                    context.getSource()
                                            .sendFeedback(Component.literal(
                                                    "§cEnd position set to: §f" + MacroConfig.endPos.toShortString()
                                                            + " §7(Plot: " + MacroConfig.endPlot + ")"));
                                }
                                return 1;
                            })));
        });
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
                        sleepRandom(4000, 4500); // Wait 4-4.5s before stopping script
                        client.getConnection().sendChat(".ez-stopscript");
                        sleepRandom(1000, 1000);
                        client.getConnection().sendChat("/setspawn");
                        sleepRandom(1000, 1000);

                        // Conditional Warp based on Bonus status
                        if (isBonusInactive) {
                            client.player.displayClientMessage(Component.literal("§eBonus Inactive! Warping to Barn."),
                                    true);
                            client.getConnection().sendChat("/tptoplot barn");
                        } else {
                            client.getConnection().sendChat("/tptoplot " + plotId);
                        }

                        sleepRandom(1000, 1000);
                        client.getConnection().sendChat(".ez-startscript misc:pestCleaner");
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

    private Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Example Mod Config"));

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        // Pest Threshold Slider
        general.addEntry(builder.getEntryBuilder()
                .startIntSlider(Component.literal("Pest Threshold"), MacroConfig.pestThreshold, 1, 8)
                .setDefaultValue(1)
                .setSaveConsumer(newValue -> MacroConfig.pestThreshold = newValue)
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
