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
                                .setDefaultValue(MacroConfig.DEFAULT_GEAR_SWAP_MODE)
                                .setSaveConsumer(newValue -> MacroConfig.gearSwapMode = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startEnumSelector(Component.literal("Unfly Mode (after /warp garden)"),
                                                MacroConfig.UnflyMode.class,
                                                MacroConfig.unflyMode)
                                .setDefaultValue(MacroConfig.DEFAULT_UNFLY_MODE)
                                .setSaveConsumer(newValue -> MacroConfig.unflyMode = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("AOTV to Roof"), MacroConfig.aotvToRoof)
                                .setDefaultValue(MacroConfig.DEFAULT_AOTV_TO_ROOF)
                                .setSaveConsumer(newValue -> MacroConfig.aotvToRoof = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Rotation Time (ms)"), MacroConfig.rotationTime,
                                                100, 3000)
                                .setDefaultValue(MacroConfig.DEFAULT_ROTATION_TIME)
                                .setSaveConsumer(newValue -> MacroConfig.rotationTime = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Pest Threshold"), MacroConfig.pestThreshold, 1, 8)
                                .setDefaultValue(MacroConfig.DEFAULT_PEST_THRESHOLD)
                                .setSaveConsumer(newValue -> MacroConfig.pestThreshold = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Visitor Threshold"), MacroConfig.visitorThreshold, 1,
                                                5)
                                .setDefaultValue(MacroConfig.DEFAULT_VISITOR_THRESHOLD)
                                .setSaveConsumer(newValue -> MacroConfig.visitorThreshold = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("GUI Click Delay (ms)"), MacroConfig.guiClickDelay,
                                                100, 2000)
                                .setDefaultValue(MacroConfig.DEFAULT_GUI_CLICK_DELAY)
                                .setSaveConsumer(newValue -> MacroConfig.guiClickDelay = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Equipment Swap Delay (ms)"),
                                                MacroConfig.equipmentSwapDelay, 100, 300)
                                .setDefaultValue(MacroConfig.DEFAULT_EQUIPMENT_SWAP_DELAY)
                                .setSaveConsumer(newValue -> MacroConfig.equipmentSwapDelay = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Rod Swap Delay (ms)"),
                                                MacroConfig.rodSwapDelay, 50, 1000)
                                .setDefaultValue(MacroConfig.DEFAULT_ROD_SWAP_DELAY)
                                .setSaveConsumer(newValue -> MacroConfig.rodSwapDelay = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntField(Component.literal("Restart Time (Minutes)"), MacroConfig.restartTime)
                                .setDefaultValue(MacroConfig.DEFAULT_RESTART_TIME)
                                .setSaveConsumer(newValue -> MacroConfig.restartTime = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startStrField(Component.literal("Restart Script Command"), MacroConfig.restartScript)
                                .setDefaultValue(MacroConfig.DEFAULT_RESTART_SCRIPT)
                                .setSaveConsumer(newValue -> MacroConfig.restartScript = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Garden Warp Delay (ms)"),
                                                MacroConfig.gardenWarpDelay,
                                                0, 3000)
                                .setDefaultValue(MacroConfig.DEFAULT_GARDEN_WARP_DELAY)
                                .setSaveConsumer(newValue -> MacroConfig.gardenWarpDelay = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("Auto-Visitor"), MacroConfig.autoVisitor)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_VISITOR)
                                .setSaveConsumer(newValue -> MacroConfig.autoVisitor = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("Auto-Equipment"), MacroConfig.autoEquipment)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_EQUIPMENT)
                                .setSaveConsumer(newValue -> MacroConfig.autoEquipment = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal(
                                                "Auto-Equipment Timing (Leave on 170 unless you know what you're doing) (sec)"),
                                                MacroConfig.autoEquipmentFarmingTime, 1, 300)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_EQUIPMENT_FARMING_TIME)
                                .setSaveConsumer(newValue -> MacroConfig.autoEquipmentFarmingTime = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("Armor Swap for Visitor"),
                                                MacroConfig.armorSwapVisitor)
                                .setDefaultValue(MacroConfig.DEFAULT_ARMOR_SWAP_VISITOR)
                                .setSaveConsumer(newValue -> MacroConfig.armorSwapVisitor = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Wardrobe Slot: Farming"),
                                                MacroConfig.wardrobeSlotFarming, 1, 9)
                                .setDefaultValue(MacroConfig.DEFAULT_WARDROBE_SLOT_FARMING)
                                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotFarming = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Wardrobe Slot: Pest"), MacroConfig.wardrobeSlotPest,
                                                1, 9)
                                .setDefaultValue(MacroConfig.DEFAULT_WARDROBE_SLOT_PEST)
                                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotPest = newValue)
                                .build());

                general.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Wardrobe Slot: Visitor"),
                                                MacroConfig.wardrobeSlotVisitor, 1, 9)
                                .setDefaultValue(MacroConfig.DEFAULT_WARDROBE_SLOT_VISITOR)
                                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotVisitor = newValue)
                                .build());

                ConfigCategory dynamicRest = builder.getOrCreateCategory(Component.literal("Dynamic Rest"));
                dynamicRest.addEntry(builder.getEntryBuilder()
                                .startIntField(Component.literal("Scripting Time (Minutes)"),
                                                MacroConfig.restScriptingTime)
                                .setDefaultValue(MacroConfig.DEFAULT_REST_SCRIPTING_TIME)
                                .setSaveConsumer(newValue -> MacroConfig.restScriptingTime = newValue)
                                .build());

                dynamicRest.addEntry(builder.getEntryBuilder()
                                .startIntField(Component.literal("Scripting Time Offset (Minutes)"),
                                                MacroConfig.restScriptingTimeOffset)
                                .setDefaultValue(MacroConfig.DEFAULT_REST_SCRIPTING_TIME_OFFSET)
                                .setSaveConsumer(newValue -> MacroConfig.restScriptingTimeOffset = newValue)
                                .build());

                dynamicRest.addEntry(builder.getEntryBuilder()
                                .startIntField(Component.literal("Break Time (Minutes)"), MacroConfig.restBreakTime)
                                .setDefaultValue(MacroConfig.DEFAULT_REST_BREAK_TIME)
                                .setSaveConsumer(newValue -> MacroConfig.restBreakTime = newValue)
                                .build());

                dynamicRest.addEntry(builder.getEntryBuilder()
                                .startIntField(Component.literal("Break Time Offset (Minutes)"),
                                                MacroConfig.restBreakTimeOffset)
                                .setDefaultValue(MacroConfig.DEFAULT_REST_BREAK_TIME_OFFSET)
                                .setSaveConsumer(newValue -> MacroConfig.restBreakTimeOffset = newValue)
                                .build());

                ConfigCategory qol = builder.getOrCreateCategory(Component.literal("QOL"));
                qol.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("Auto-Book Combine"), MacroConfig.autoBookCombine)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_BOOK_COMBINE)
                                .setSaveConsumer(newValue -> MacroConfig.autoBookCombine = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("Book Combine Delay (ms)"),
                                                MacroConfig.bookCombineDelay, 100, 2000)
                                .setDefaultValue(MacroConfig.DEFAULT_BOOK_COMBINE_DELAY)
                                .setSaveConsumer(newValue -> MacroConfig.bookCombineDelay = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startStrList(Component.literal("Custom Enchantment Max Levels"),
                                                MacroConfig.customEnchantmentLevels)
                                .setDefaultValue(MacroConfig.DEFAULT_CUSTOM_ENCHANTMENT_LEVELS)
                                .setTooltip(Component.literal("Format: EnchantmentName:MaxLevel (e.g. Sharpness:7)"))
                                .setExpanded(true)
                                .setSaveConsumer(newValue -> MacroConfig.customEnchantmentLevels = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal("Stash Manager"), MacroConfig.autoStashManager)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_STASH_MANAGER)
                                .setSaveConsumer(newValue -> MacroConfig.autoStashManager = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component
                                                .literal("Auto George Sell (requires abiphone with George contact)"),
                                                MacroConfig.autoGeorgeSell)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_GEORGE_SELL)
                                .setSaveConsumer(newValue -> MacroConfig.autoGeorgeSell = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startIntSlider(Component.literal("George Sell Threshold (Pets)"),
                                                MacroConfig.georgeSellThreshold, 1, 35)
                                .setDefaultValue(MacroConfig.DEFAULT_GEORGE_SELL_THRESHOLD)
                                .setSaveConsumer(newValue -> MacroConfig.georgeSellThreshold = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component
                                                .literal("Custom Autosell (triggers on opening booster cookie menu)"),
                                                MacroConfig.autoBoosterCookie)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_BOOSTER_COOKIE)
                                .setSaveConsumer(newValue -> MacroConfig.autoBoosterCookie = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startStrList(Component.literal("Booster Cookie Autosell Items"),
                                                MacroConfig.boosterCookieItems)
                                .setDefaultValue(MacroConfig.DEFAULT_BOOSTER_COOKIE_ITEMS)
                                .setExpanded(true)
                                .setSaveConsumer(newValue -> MacroConfig.boosterCookieItems = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startBooleanToggle(Component.literal(
                                                "Enable PlotTP Rewarp (for hyper-optimized farms that have startpos as plottp rewarp)"),
                                                MacroConfig.enablePlotTpRewarp)
                                .setDefaultValue(MacroConfig.DEFAULT_ENABLE_PLOT_TP_REWARP)
                                .setSaveConsumer(newValue -> MacroConfig.enablePlotTpRewarp = newValue)
                                .build());

                qol.addEntry(builder.getEntryBuilder()
                                .startStrField(Component.literal("PlotTP Number"), MacroConfig.plotTpNumber)
                                .setDefaultValue(MacroConfig.DEFAULT_PLOT_TP_NUMBER)
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
                                                MacroConfig.save();
                                                client.player.displayClientMessage(
                                                                Component.literal(
                                                                                "§aRewarp End Position captured and saved!"),
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
