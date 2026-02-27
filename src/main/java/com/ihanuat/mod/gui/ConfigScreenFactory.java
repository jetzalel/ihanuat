package com.ihanuat.mod.gui;

import com.ihanuat.mod.MacroConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ConfigScreenFactory {

        public static Screen createConfigScreen(Screen parent) {
                ConfigBuilder builder = ConfigBuilder.create()
                                .setParentScreen(parent)
                                .setTitle(Component.literal("Ihanuat Config"))
                                .setSavingRunnable(MacroConfig::save);

                ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

                general.addEntry(builder.getEntryBuilder()
                                .startEnumSelector(Component.literal("Wardrobe/Rod Swap Mode"),
                                                MacroConfig.GearSwapMode.class,
                                                MacroConfig.gearSwapMode)
                                .setDefaultValue(MacroConfig.GearSwapMode.NONE)
                                .setSaveConsumer(newValue -> MacroConfig.gearSwapMode = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startEnumSelector(Component.literal("Unfly Mode (after /warp garden)"),
                                                MacroConfig.UnflyMode.class,
                                                MacroConfig.unflyMode)
                                .setDefaultValue(MacroConfig.UnflyMode.DOUBLE_TAP_SPACE)
                                .setSaveConsumer(newValue -> MacroConfig.unflyMode = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Pest Threshold"), MacroConfig.pestThreshold, 1, 8)
                                .setDefaultValue(1)
                                .setSaveConsumer(newValue -> MacroConfig.pestThreshold = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Visitor Threshold"), MacroConfig.visitorThreshold, 1,
                                                5)
                                .setDefaultValue(5)
                                .setSaveConsumer(newValue -> MacroConfig.visitorThreshold = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Rotation Speed (deg/s)"), MacroConfig.rotationSpeed,
                                                10, 2000)
                                .setDefaultValue(200)
                                .setSaveConsumer(newValue -> MacroConfig.rotationSpeed = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("GUI Click Delay (ms)"), MacroConfig.guiClickDelay,
                                                100, 2000)
                                .setDefaultValue(500)
                                .setSaveConsumer(newValue -> MacroConfig.guiClickDelay = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Equipment Swap Delay (ms)"),
                                                MacroConfig.equipmentSwapDelay, 100, 2000)
                                .setDefaultValue(500)
                                .setSaveConsumer(newValue -> MacroConfig.equipmentSwapDelay = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntField(Component.literal("Restart Time (Minutes)"), MacroConfig.restartTime)
                                .setDefaultValue(5)
                                .setSaveConsumer(newValue -> MacroConfig.restartTime = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startStrField(Component.literal("Restart Script Command"), MacroConfig.restartScript)
                                .setDefaultValue(".ez-startscript netherwart:1")
                                .setSaveConsumer(newValue -> MacroConfig.restartScript = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("Auto-Visitor"), MacroConfig.autoVisitor)
                                .setDefaultValue(true)
                                .setSaveConsumer(newValue -> MacroConfig.autoVisitor = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("Auto-Equipment"), MacroConfig.autoEquipment)
                                .setDefaultValue(true)
                                .setSaveConsumer(newValue -> MacroConfig.autoEquipment = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal(
                                                "Auto-Equipment Timing (time left on pest cd to swap to  pest spawn equip) (sec)"),
                                                MacroConfig.autoEquipmentFarmingTime, 1, 300)
                                .setDefaultValue(200)
                                .setSaveConsumer(newValue -> MacroConfig.autoEquipmentFarmingTime = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("Armor Swap for Visitor"),
                                                MacroConfig.armorSwapVisitor)
                                .setDefaultValue(false)
                                .setSaveConsumer(newValue -> MacroConfig.armorSwapVisitor = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Wardrobe Slot: Farming"),
                                                MacroConfig.wardrobeSlotFarming, 1, 9)
                                .setDefaultValue(1)
                                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotFarming = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Wardrobe Slot: Pest"), MacroConfig.wardrobeSlotPest,
                                                1, 9)
                                .setDefaultValue(2)
                                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotPest = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Wardrobe Slot: Visitor"),
                                                MacroConfig.wardrobeSlotVisitor, 1, 9)
                                .setDefaultValue(3)
                                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotVisitor = newValue)
                                .build());

                ConfigCategory dynamicRest = builder.getOrCreateCategory(Component.literal("Dynamic Rest"));
                dynamicRest.addEntry(builder.getEntryBuilder()
                                .startIntField(Component.literal("Scripting Time (Minutes)"),
                                                MacroConfig.restScriptingTime)
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
                                .startIntField(Component.literal("Break Time Offset (Minutes)"),
                                                MacroConfig.restBreakTimeOffset)
                                .setDefaultValue(3)
                                .setSaveConsumer(newValue -> MacroConfig.restBreakTimeOffset = newValue)
                                .build());

                ConfigCategory qol = builder.getOrCreateCategory(Component.literal("QOL"));
                qol.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("Stash Manager"), MacroConfig.autoStashManager)
                                .setDefaultValue(false)
                                .setSaveConsumer(newValue -> MacroConfig.autoStashManager = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component
                                                .literal("Auto George Sell (requires abiphone with George contact)"),
                                                MacroConfig.autoGeorgeSell)
                                .setDefaultValue(false)
                                .setSaveConsumer(newValue -> MacroConfig.autoGeorgeSell = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("George Sell Threshold (Pets)"),
                                                MacroConfig.georgeSellThreshold, 1, 35)
                                .setDefaultValue(10)
                                .setSaveConsumer(newValue -> MacroConfig.georgeSellThreshold = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startStrField(Component.literal("Custom AutoSell Items (| separated)"), MacroConfig.autoSellCustomItems)
                                .setDefaultValue("Atmospheric Filter|Squeaky Toy|Beady Eyes|Clipped Wings|Overclocker|Mantid Claw|Flowering Bouquet|Bookworm|Chirping Stereo|Firefly|Capsule|Vinyl")
                                .setSaveConsumer(newValue -> MacroConfig.autoSellCustomItems = newValue)
                                .setTooltip(Component.literal("Enter item names separated by | (pipe) character. Example: Item1|Item2|Item3"))
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("Enable PlotTP Rewarp (for hyper-optimized farms that have startpos as plottp rewarp)"),
                                                MacroConfig.enablePlotTpRewarp)
                                .setDefaultValue(false)
                                .setSaveConsumer(newValue -> MacroConfig.enablePlotTpRewarp = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startStrField(Component.literal("PlotTP Number"), MacroConfig.plotTpNumber)
                                .setDefaultValue("0")
                                .setSaveConsumer(newValue -> MacroConfig.plotTpNumber = newValue)
                                .build());

                qol.addEntry(new ButtonEntry(
                                Component.literal("Capture Rewarp End Position"),
                                Component.literal("Captures your current position as the trigger for PlotTP Rewarp."),
                                button -> {
                                        Minecraft client = Minecraft.getInstance();
                                        if (client.player != null) {
                                                MacroConfig.rewarpEndX = client.player.getX();
                                                MacroConfig.rewarpEndY = client.player.getY();
                                                MacroConfig.rewarpEndZ = client.player.getZ();
                                                MacroConfig.rewarpEndPosSet = true;
                                                client.player.displayClientMessage(
                                                                Component.literal("§aRewarp End Position captured!"),
                                                                true);
                                        }
                                }));

                return builder.build();
        }

        private static class ButtonEntry extends TooltipListEntry<Object> {
                private final Button button;
                private final Component fieldName;

                public ButtonEntry(Component fieldName, Component tooltip, Button.OnPress onPress) {
                        super(fieldName, () -> Optional.of(new Component[] { tooltip }));
                        this.fieldName = fieldName;
                        this.button = Button.builder(fieldName, onPress).bounds(0, 0, 150, 20).build();
                }

                @Override
                public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight,
                                int mouseX,
                                int mouseY, boolean isHovered, float tickDelta) {
                        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered,
                                        tickDelta);
                        this.button.setX(x + entryWidth - 160);
                        this.button.setY(y);
                        this.button.setWidth(150);
                        this.button.render(graphics, mouseX, mouseY, tickDelta);
                        graphics.drawString(Minecraft.getInstance().font, fieldName, x, y + 6, 0xFFFFFF);
                }

                @Override
                public List<? extends GuiEventListener> children() {
                        return Collections.singletonList(button);
                }

                @Override
                public List<? extends NarratableEntry> narratables() {
                        return Collections.singletonList(button);
                }

                @Override
                public Object getValue() {
                        return null;
                }

                @Override
                public Optional<Object> getDefaultValue() {
                        return Optional.empty();
                }

                @Override
                public void save() {
                }
        }
}
