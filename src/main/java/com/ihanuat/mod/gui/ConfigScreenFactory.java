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
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ConfigScreenFactory {

        public static Screen createConfigScreen(Screen parent) {
                ConfigBuilder builder = ConfigBuilder.create()
                                .setParentScreen(parent)
                                .setTitle(Component.literal("Ihanuat Config"))
                                .setSavingRunnable(MacroConfig::save);

                // --- General Category ---
                ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

                general.addEntry(builder.entryBuilder()
                                .startTextDescription(Component.literal(
                                                "§cDisable Auto Pest, Auto Visitor, and Auto Wardrobe in Taunahi settings. Make sure Vacuum Bag Empty At Start is turned on in Taunahi also.\nDisable SkyHanni or any mods that hide pest drop messages and hide empty messages (needed for Profit Calculator). \nDisable any mods that hide Warping... message from /warp garden and PlotTP message as well."))
                                .build());

                general.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Show Macro Status HUD"), MacroConfig.showHud)
                                .setDefaultValue(MacroConfig.DEFAULT_SHOW_HUD)
                                .setTooltip(Component.literal("Display the timer and macro state panel."))
                                .setSaveConsumer(newValue -> MacroConfig.showHud = newValue)
                                .build());

                general.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("GUI only in Garden"),
                                                MacroConfig.guiOnlyInGarden)
                                .setDefaultValue(MacroConfig.DEFAULT_GUI_ONLY_IN_GARDEN)
                                .setTooltip(Component.literal(
                                                "If enabled, the macro HUDs will only be visible while you are in the Garden."))
                                .setSaveConsumer(newValue -> MacroConfig.guiOnlyInGarden = newValue)
                                .build());

                general.addEntry(builder.entryBuilder()
                                .startEnumSelector(Component.literal("Unfly Mode (after /warp garden)"),
                                                MacroConfig.UnflyMode.class,
                                                MacroConfig.unflyMode)
                                .setDefaultValue(MacroConfig.DEFAULT_UNFLY_MODE)
                                .setSaveConsumer(newValue -> MacroConfig.unflyMode = newValue)
                                .build());

                general.addEntry(builder.entryBuilder()
                                .startStringDropdownMenu(Component.literal("Farm Script Command"),
                                                MacroConfig.restartScript)
                                .setDefaultValue(MacroConfig.DEFAULT_RESTART_SCRIPT)
                                .setSelections(MacroConfig.DEFAULT_FARM_SCRIPTS)
                                .setTooltipSupplier(value -> java.util.Optional.of(new Component[] {
                                                Component.literal("§7" + MacroConfig.getScriptDescription(value)) }))
                                .setSaveConsumer(newValue -> MacroConfig.restartScript = newValue)
                                .build());

                general.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal(
                                                "Enable PlotTP Rewarp (for hyper-optimized farms that have startpos as plottp rewarp)"),
                                                MacroConfig.enablePlotTpRewarp)
                                .setDefaultValue(MacroConfig.DEFAULT_ENABLE_PLOT_TP_REWARP)
                                .setSaveConsumer(newValue -> MacroConfig.enablePlotTpRewarp = newValue)
                                .build());

                general.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Hold W Until Wall Hit"),
                                                MacroConfig.holdWUntilWall)
                                .setDefaultValue(MacroConfig.DEFAULT_HOLD_W_UNTIL_WALL)
                                .setTooltip(Component.literal(
                                                "On /plottp farms only. Holds W after rewarp until hitting a wall, then starts the script."))
                                .setSaveConsumer(newValue -> MacroConfig.holdWUntilWall = newValue)
                                .build());

                general.addEntry(builder.entryBuilder()
                                .startStrField(Component.literal("PlotTP Number"), MacroConfig.plotTpNumber)
                                .setDefaultValue(MacroConfig.DEFAULT_PLOT_TP_NUMBER)
                                .setSaveConsumer(newValue -> MacroConfig.plotTpNumber = newValue)
                                .build());

                general.addEntry(new ButtonEntry(
                                Component.literal("Capture Rewarp End Position"),
                                Component.literal("Captures your current position as the trigger for PlotTP Rewarp."),
                                button -> {
                                        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft
                                                        .getInstance();
                                        if (client.player != null) {
                                                MacroConfig.rewarpEndX = client.player.getX();
                                                MacroConfig.rewarpEndY = client.player.getY();
                                                MacroConfig.rewarpEndZ = client.player.getZ();
                                                MacroConfig.rewarpEndPosSet = true;
                                                MacroConfig.save();
                                                client.player.displayClientMessage(
                                                                net.minecraft.network.chat.Component.literal(
                                                                                "\u00A7aRewarp End Position captured and saved!"),
                                                                true);
                                        }
                                }));

                // --- Delays Category ---
                ConfigCategory delays = builder.getOrCreateCategory(Component.literal("Delays"));

                delays.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Additional Random Delay (0-1000ms)"),
                                                MacroConfig.additionalRandomDelay, 0, 1000)
                                .setDefaultValue(MacroConfig.DEFAULT_ADDITIONAL_RANDOM_DELAY)
                                .setTooltip(Component.literal(
                                                "Adds extra random delay to GUI clicks, tool swaps, warps, and rotations"))
                                .setSaveConsumer(newValue -> MacroConfig.additionalRandomDelay = newValue)
                                .build());

                delays.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Rotation Time (ms)"), MacroConfig.rotationTime,
                                                100, 3000)
                                .setDefaultValue(MacroConfig.DEFAULT_ROTATION_TIME)
                                .setSaveConsumer(newValue -> MacroConfig.rotationTime = newValue)
                                .build());

                delays.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("GUI Click Delay (ms)"), MacroConfig.guiClickDelay,
                                                100, 2000)
                                .setDefaultValue(MacroConfig.DEFAULT_GUI_CLICK_DELAY)
                                .setSaveConsumer(newValue -> MacroConfig.guiClickDelay = newValue)
                                .build());

                delays.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Equipment Swap Delay (ms)"),
                                                MacroConfig.equipmentSwapDelay, 100, 300)
                                .setDefaultValue(MacroConfig.DEFAULT_EQUIPMENT_SWAP_DELAY)
                                .setSaveConsumer(newValue -> MacroConfig.equipmentSwapDelay = newValue)
                                .build());

                delays.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Rod Swap Delay (ms)"),
                                                MacroConfig.rodSwapDelay, 50, 1000)
                                .setDefaultValue(MacroConfig.DEFAULT_ROD_SWAP_DELAY)
                                .setSaveConsumer(newValue -> MacroConfig.rodSwapDelay = newValue)
                                .build());

                delays.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Garden Warp Delay (ms)"),
                                                MacroConfig.gardenWarpDelay,
                                                0, 3000)
                                .setDefaultValue(MacroConfig.DEFAULT_GARDEN_WARP_DELAY)
                                .setSaveConsumer(newValue -> MacroConfig.gardenWarpDelay = newValue)
                                .build());

                delays.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Pest Chat Trigger Delay (ms)"),
                                                MacroConfig.pestChatTriggerDelay,
                                                0, 3000)
                                .setDefaultValue(MacroConfig.DEFAULT_PEST_CHAT_TRIGGER_DELAY)
                                .setTooltip(Component.literal(
                                                "Adds a fixed delay before triggering the pest cleaner after seeing the chat message."))
                                .setSaveConsumer(newValue -> MacroConfig.pestChatTriggerDelay = newValue)
                                .build());

                delays.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Book Combine Delay (ms)"),
                                                MacroConfig.bookCombineDelay, 100, 2000)
                                .setDefaultValue(MacroConfig.DEFAULT_BOOK_COMBINE_DELAY)
                                .setSaveConsumer(newValue -> MacroConfig.bookCombineDelay = newValue)
                                .build());

                delays.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Junk Item Drop Delay (0-1000ms)"),
                                                MacroConfig.junkItemDropDelay, 0, 1000)
                                .setDefaultValue(MacroConfig.DEFAULT_JUNK_ITEM_DROP_DELAY)
                                .setTooltip(Component.literal(
                                                "Delay between dropping junk items (in milliseconds)."))
                                .setSaveConsumer(newValue -> MacroConfig.junkItemDropDelay = newValue)
                                .build());

                // --- Wardrobe Swap Category ---
                ConfigCategory wardrobe = builder.getOrCreateCategory(Component.literal("Wardrobe Swap"));

                wardrobe.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Auto Wardrobe (Pest/Farming)"),
                                                MacroConfig.autoWardrobePest)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_WARDROBE_PEST)
                                .setTooltip(Component.literal(
                                                "Automatically swap wardrobe slots for Pest Prep (to Pest gear) and Return to Farm (to Farming gear)."))
                                .setSaveConsumer(newValue -> MacroConfig.autoWardrobePest = newValue)
                                .build());

                wardrobe.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Auto Wardrobe (Visitors)"),
                                                MacroConfig.autoWardrobeVisitor)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_WARDROBE_VISITOR)
                                .setTooltip(Component.literal(
                                                "Automatically swap wardrobe slots when visiting or returning from visitors."))
                                .setSaveConsumer(newValue -> MacroConfig.autoWardrobeVisitor = newValue)
                                .build());

                wardrobe.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Wardrobe Slot: Farming"),
                                                MacroConfig.wardrobeSlotFarming, 1, 9)
                                .setDefaultValue(MacroConfig.DEFAULT_WARDROBE_SLOT_FARMING)
                                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotFarming = newValue)
                                .build());

                wardrobe.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Wardrobe Slot: Pest"), MacroConfig.wardrobeSlotPest,
                                                1, 9)
                                .setDefaultValue(MacroConfig.DEFAULT_WARDROBE_SLOT_PEST)
                                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotPest = newValue)
                                .build());

                wardrobe.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Wardrobe Slot: Visitor"),
                                                MacroConfig.wardrobeSlotVisitor, 1, 9)
                                .setDefaultValue(MacroConfig.DEFAULT_WARDROBE_SLOT_VISITOR)
                                .setSaveConsumer(newValue -> MacroConfig.wardrobeSlotVisitor = newValue)
                                .build());

                // --- Auto Rod Category ---
                ConfigCategory autoRod = builder.getOrCreateCategory(Component.literal("Auto Rod"));

                autoRod.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Rod on Pest CD"), MacroConfig.autoRodPestCd)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_ROD_PEST_CD)
                                .setTooltip(Component.literal(
                                                "Trigger rod swap/use sequence when prep-swapping for pests (cooldown)."))
                                .setSaveConsumer(newValue -> MacroConfig.autoRodPestCd = newValue)
                                .build());

                autoRod.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Rod on Pest Spawn"),
                                                MacroConfig.autoRodPestSpawn)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_ROD_PEST_SPAWN)
                                .setTooltip(Component.literal(
                                                "Trigger rod swap/use sequence when starting a pest cleaning sequence."))
                                .setSaveConsumer(newValue -> MacroConfig.autoRodPestSpawn = newValue)
                                .build());

                autoRod.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Rod on Return to Farm"),
                                                MacroConfig.autoRodReturnToFarm)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_ROD_RETURN_TO_FARM)
                                .setTooltip(Component.literal(
                                                "Trigger rod swap/use sequence when returning to farming after pest/visitor sequence."))
                                .setSaveConsumer(newValue -> MacroConfig.autoRodReturnToFarm = newValue)
                                .build());

                // --- Equipment Swap Category ---
                ConfigCategory equipment = builder.getOrCreateCategory(Component.literal("Equipment Swap"));

                equipment.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Auto-Equipment"), MacroConfig.autoEquipment)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_EQUIPMENT)
                                .setSaveConsumer(newValue -> MacroConfig.autoEquipment = newValue)
                                .build());

                // --- Auto Pest Category ---
                ConfigCategory autoPest = builder.getOrCreateCategory(Component.literal("Auto Pest"));

                autoPest.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Pest Threshold"), MacroConfig.pestThreshold, 1, 8)
                                .setDefaultValue(MacroConfig.DEFAULT_PEST_THRESHOLD)
                                .setSaveConsumer(newValue -> MacroConfig.pestThreshold = newValue)
                                .build());

                autoPest.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Trigger Pest on Chat"),
                                                MacroConfig.triggerPestOnChat)
                                .setDefaultValue(MacroConfig.DEFAULT_TRIGGER_PEST_ON_CHAT)
                                .setTooltip(Component.literal(
                                                "Instantly trigger pest cleaner upon seeing 'yuck, pest spawned in plot #' in chat."))
                                .setSaveConsumer(newValue -> MacroConfig.triggerPestOnChat = newValue)
                                .build());

                autoPest.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Delay Pest for Crop Fever"),
                                                MacroConfig.delayPestForCropFever)
                                .setDefaultValue(MacroConfig.DEFAULT_DELAY_PEST_FOR_CROP_FEVER)
                                .setTooltip(Component.literal(
                                                "Delays the pest cleaning sequence if you currently have the Crop Fever buff active."))
                                .setSaveConsumer(newValue -> MacroConfig.delayPestForCropFever = newValue)
                                .build());

                autoPest.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("AOTV to Roof"), MacroConfig.aotvToRoof)
                                .setDefaultValue(MacroConfig.DEFAULT_AOTV_TO_ROOF)
                                .setTooltip(Component.literal(
                                                "Uses Aspect of the Void to etherwarp to the roof after /setspawn during pest cleaning."))
                                .setSaveConsumer(newValue -> MacroConfig.aotvToRoof = newValue)
                                .build());

                autoPest.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Break Blocks Before AOTV"),
                                                MacroConfig.breakBlocksBeforeAotv)
                                .setDefaultValue(MacroConfig.DEFAULT_BREAK_BLOCKS_BEFORE_AOTV)
                                .setTooltip(Component.literal(
                                                "Holds down left click for 5 ticks before starting the AOTV roof sequence."))
                                .setSaveConsumer(newValue -> MacroConfig.breakBlocksBeforeAotv = newValue)
                                .build());

                autoPest.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("AOTV Roof Pitch (45-90)"),
                                                MacroConfig.aotvRoofPitch, 45, 90)
                                .setDefaultValue(MacroConfig.DEFAULT_AOTV_ROOF_PITCH)
                                .setTooltip(Component.literal(
                                                "Base upward look angle used for AOTV roof teleport. 90 is straight up."))
                                .setSaveConsumer(newValue -> MacroConfig.aotvRoofPitch = newValue)
                                .build());

                autoPest.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("AOTV Roof Pitch Humanization (0-15)"),
                                                MacroConfig.aotvRoofPitchHumanization, 0, 15)
                                .setDefaultValue(MacroConfig.DEFAULT_AOTV_ROOF_PITCH_HUMANIZATION)
                                .setTooltip(Component.literal(
                                                "Adds random +/- pitch variation in degrees for more human-like AOTV roof looks."))
                                .setSaveConsumer(newValue -> MacroConfig.aotvRoofPitchHumanization = newValue)
                                .build());

                autoPest.addEntry(builder.entryBuilder()
                                .startStrList(Component.literal("AOTV Roof Plots"),
                                                MacroConfig.aotvRoofPlots)
                                .setDefaultValue(MacroConfig.DEFAULT_AOTV_ROOF_PLOTS)
                                .setTooltip(Component.literal(
                                                "List of plot numbers where AOTV to roof is allowed. If the list is empty, AOTV is allowed on all plots."))
                                .setExpanded(true)
                                .setSaveConsumer(newValue -> MacroConfig.aotvRoofPlots = newValue)
                                .build());

                // --- Auto Visitor Category ---
                ConfigCategory autoVisitorCat = builder.getOrCreateCategory(Component.literal("Auto Visitor"));

                autoVisitorCat.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Auto-Visitor"), MacroConfig.autoVisitor)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_VISITOR)
                                .setSaveConsumer(newValue -> MacroConfig.autoVisitor = newValue)
                                .build());

                autoVisitorCat.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Visitor Threshold"), MacroConfig.visitorThreshold, 1,
                                                5)
                                .setDefaultValue(MacroConfig.DEFAULT_VISITOR_THRESHOLD)
                                .setSaveConsumer(newValue -> MacroConfig.visitorThreshold = newValue)
                                .build());

                // --- Auto George Category ---
                ConfigCategory autoGeorge = builder.getOrCreateCategory(Component.literal("Auto George"));

                autoGeorge.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component
                                                .literal("Auto George Sell (requires abiphone with George contact)"),
                                                MacroConfig.autoGeorgeSell)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_GEORGE_SELL)
                                .setSaveConsumer(newValue -> MacroConfig.autoGeorgeSell = newValue)
                                .build());

                autoGeorge.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("George Sell Threshold (Pets)"),
                                                MacroConfig.georgeSellThreshold, 1, 35)
                                .setDefaultValue(MacroConfig.DEFAULT_GEORGE_SELL_THRESHOLD)
                                .setSaveConsumer(newValue -> MacroConfig.georgeSellThreshold = newValue)
                                .build());

                // --- Auto Sell Category ---
                ConfigCategory autoSellCat = builder.getOrCreateCategory(Component.literal("Auto Sell"));

                autoSellCat.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component
                                                .literal("Custom Autosell (triggers on opening booster cookie menu)"),
                                                MacroConfig.autoBoosterCookie)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_BOOSTER_COOKIE)
                                .setSaveConsumer(newValue -> MacroConfig.autoBoosterCookie = newValue)
                                .build());

                autoSellCat.addEntry(builder.entryBuilder()
                                .startStrList(Component.literal("Booster Cookie Autosell Items"),
                                                MacroConfig.boosterCookieItems)
                                .setDefaultValue(MacroConfig.DEFAULT_BOOSTER_COOKIE_ITEMS)
                                .setExpanded(true)
                                .setSaveConsumer(newValue -> MacroConfig.boosterCookieItems = newValue)
                                .build());

                // --- Profit Calculator Category (Restored from previous step) ---
                ConfigCategory profitTracker = builder.getOrCreateCategory(Component.literal("Profit Calculator"));

                profitTracker.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Show Session Profit HUD"),
                                                MacroConfig.showSessionProfitHud)
                                .setDefaultValue(MacroConfig.DEFAULT_SHOW_SESSION_PROFIT_HUD)
                                .setTooltip(Component.literal("Display the profit tracker for your current session."))
                                .setSaveConsumer(newValue -> MacroConfig.showSessionProfitHud = newValue)
                                .build());

                profitTracker.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Show Daily Session Profit HUD"),
                                                MacroConfig.showDailyHud)
                                .setDefaultValue(MacroConfig.DEFAULT_SHOW_DAILY_HUD)
                                .setTooltip(Component.literal(
                                                "Display the profit tracker for today (resets daily at midnight using your local timezone)."))
                                .setSaveConsumer(newValue -> MacroConfig.showDailyHud = newValue)
                                .build());

                profitTracker.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Show Lifetime Profit HUD"),
                                                MacroConfig.showLifetimeHud)
                                .setDefaultValue(MacroConfig.DEFAULT_SHOW_LIFETIME_HUD)
                                .setTooltip(Component.literal(
                                                "Display a persistent profit tracker that doesn't reset across game restarts."))
                                .setSaveConsumer(newValue -> MacroConfig.showLifetimeHud = newValue)
                                .build());

                profitTracker.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Persist Session Timer On Pause"),
                                                MacroConfig.persistSessionTimer)
                                .setDefaultValue(MacroConfig.DEFAULT_PERSIST_SESSION_TIMER)
                                .setSaveConsumer(newValue -> MacroConfig.persistSessionTimer = newValue)
                                .build());

                profitTracker.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Compact Profit Calculator"),
                                                MacroConfig.compactProfitCalculator)
                                .setDefaultValue(MacroConfig.DEFAULT_COMPACT_PROFIT_CALCULATOR)
                                .setTooltip(Component.literal(
                                                "Condenses the profit panel into Categories (Crops, Pest Items, Pets) instead of individual items."))
                                .setSaveConsumer(newValue -> MacroConfig.compactProfitCalculator = newValue)
                                .build());

                profitTracker.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Show Profit HUDs While Inactive"),
                                                MacroConfig.showProfitHudWhileInactive)
                                .setDefaultValue(MacroConfig.DEFAULT_SHOW_PROFIT_HUD_WHILE_INACTIVE)
                                .setTooltip(Component.literal(
                                                "If disabled, profit HUDs will only be visible when the macro is running."))
                                .setSaveConsumer(newValue -> MacroConfig.showProfitHudWhileInactive = newValue)
                                .build());

                profitTracker.addEntry(builder.entryBuilder()
                                .startStrList(Component.literal("Pet Tracker List"),
                                                MacroConfig.petTrackerList)
                                .setDefaultValue(MacroConfig.DEFAULT_PET_TRACKER_LIST)
                                .setTooltip(Component.literal(
                                                "Format: TAG:Name:MaxLevel:Rarity (e.g. PET_ELEPHANT:Elephant:100:LEGENDARY)"))
                                .setExpanded(true)
                                .setSaveConsumer(newValue -> MacroConfig.petTrackerList = newValue)
                                .build());

                profitTracker.addEntry(new TripleButtonEntry(
                                Component.literal("Reset Profit Data"),
                                Component.literal("Clears your tracked earnings. Cannot be undone."),
                                Component.literal("Reset Session"),
                                button -> {
                                        com.ihanuat.mod.MacroStateManager.resetSession();
                                        Minecraft client = Minecraft.getInstance();
                                        if (client.player != null) {
                                                client.player.displayClientMessage(
                                                                Component.literal("§aSession profit reset!"), true);
                                        }
                                },
                                Component.literal("Reset Daily"),
                                button -> {
                                        com.ihanuat.mod.modules.ProfitManager.resetDaily();
                                        Minecraft client = Minecraft.getInstance();
                                        if (client.player != null) {
                                                client.player.displayClientMessage(
                                                                Component.literal("§aDaily profit reset!"), true);
                                        }
                                },
                                Component.literal("Reset Lifetime"),
                                button -> {
                                        com.ihanuat.mod.modules.ProfitManager.resetLifetime();
                                        Minecraft client = Minecraft.getInstance();
                                        if (client.player != null) {
                                                client.player.displayClientMessage(
                                                                Component.literal("§aLifetime profit reset!"), true);
                                        }
                                }));

                // --- Dynamic Rest Category ---
                ConfigCategory dynamicRest = builder.getOrCreateCategory(Component.literal("Dynamic Rest"));
                dynamicRest.addEntry(builder.entryBuilder()
                                .startIntField(Component.literal("Scripting Time (Minutes)"),
                                                MacroConfig.restScriptingTime)
                                .setDefaultValue(MacroConfig.DEFAULT_REST_SCRIPTING_TIME)
                                .setSaveConsumer(newValue -> MacroConfig.restScriptingTime = newValue)
                                .build());

                dynamicRest.addEntry(builder.entryBuilder()
                                .startIntField(Component.literal("Scripting Time Offset (Minutes)"),
                                                MacroConfig.restScriptingTimeOffset)
                                .setDefaultValue(MacroConfig.DEFAULT_REST_SCRIPTING_TIME_OFFSET)
                                .setSaveConsumer(newValue -> MacroConfig.restScriptingTimeOffset = newValue)
                                .build());

                dynamicRest.addEntry(builder.entryBuilder()
                                .startIntField(Component.literal("Break Time (Minutes)"), MacroConfig.restBreakTime)
                                .setDefaultValue(MacroConfig.DEFAULT_REST_BREAK_TIME)
                                .setSaveConsumer(newValue -> MacroConfig.restBreakTime = newValue)
                                .build());

                dynamicRest.addEntry(builder.entryBuilder()
                                .startIntField(Component.literal("Break Time Offset (Minutes)"),
                                                MacroConfig.restBreakTimeOffset)
                                .setDefaultValue(MacroConfig.DEFAULT_REST_BREAK_TIME_OFFSET)
                                .setSaveConsumer(newValue -> MacroConfig.restBreakTimeOffset = newValue)
                                .build());

                // --- QOL Category ---
                ConfigCategory qol = builder.getOrCreateCategory(Component.literal("QOL"));
                qol.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Auto-Book Combine"), MacroConfig.autoBookCombine)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_BOOK_COMBINE)
                                .setSaveConsumer(newValue -> MacroConfig.autoBookCombine = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Always Active Combine"),
                                                MacroConfig.alwaysActiveCombine)
                                .setDefaultValue(MacroConfig.DEFAULT_ALWAYS_ACTIVE_COMBINE)
                                .setTooltip(Component.literal(
                                                "If enabled, Auto-Combine will trigger whenever an anvil menu is opened. If disabled, it only triggers during mod-initiated sequences."))
                                .setSaveConsumer(newValue -> MacroConfig.alwaysActiveCombine = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startStrList(Component.literal("Custom Enchantment Max Levels"),
                                                MacroConfig.customEnchantmentLevels)
                                .setDefaultValue(MacroConfig.DEFAULT_CUSTOM_ENCHANTMENT_LEVELS)
                                .setTooltip(Component.literal("Format: EnchantmentName:MaxLevel (e.g. Sharpness:7)"))
                                .setExpanded(true)
                                .setSaveConsumer(newValue -> MacroConfig.customEnchantmentLevels = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Book Threshold"),
                                                MacroConfig.bookThreshold, 1, 35)
                                .setDefaultValue(MacroConfig.DEFAULT_BOOK_THRESHOLD)
                                .setSaveConsumer(newValue -> MacroConfig.bookThreshold = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Chat Cleanup"), MacroConfig.hideFilteredChat)
                                .setDefaultValue(MacroConfig.DEFAULT_HIDE_FILTERED_CHAT)
                                .setSaveConsumer(newValue -> MacroConfig.hideFilteredChat = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Auto-Drop Junk"), MacroConfig.autoDropJunk)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_DROP_JUNK)
                                .setSaveConsumer(newValue -> MacroConfig.autoDropJunk = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startStrList(Component.literal("Junk Items List"),
                                                MacroConfig.junkItems)
                                .setDefaultValue(MacroConfig.DEFAULT_JUNK_ITEMS)
                                .setTooltip(Component.literal("Items to automatically drop from inventory."))
                                .setExpanded(true)
                                .setSaveConsumer(newValue -> MacroConfig.junkItems = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startStrField(Component.literal("Drop Junk PlotTP"), MacroConfig.dropJunkPlotTp)
                                .setDefaultValue(MacroConfig.DEFAULT_DROP_JUNK_PLOT_TP)
                                .setSaveConsumer(newValue -> MacroConfig.dropJunkPlotTp = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Junk Threshold (Items)"),
                                                MacroConfig.junkThreshold, 1, 35)
                                .setDefaultValue(MacroConfig.DEFAULT_JUNK_THRESHOLD)
                                .setTooltip(Component.literal(
                                                "Triggers the drop sequence after this many junk items accumulate."))
                                .setSaveConsumer(newValue -> MacroConfig.junkThreshold = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startIntSlider(Component.literal("Junk Item Drop Delay (0-1000ms)"),
                                                MacroConfig.junkItemDropDelay, 0, 1000)
                                .setDefaultValue(MacroConfig.DEFAULT_JUNK_ITEM_DROP_DELAY)
                                .setTooltip(Component.literal(
                                                "Delay between dropping junk items (in milliseconds)."))
                                .setSaveConsumer(newValue -> MacroConfig.junkItemDropDelay = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Stash Manager"), MacroConfig.autoStashManager)
                                .setDefaultValue(MacroConfig.DEFAULT_AUTO_STASH_MANAGER)
                                .setSaveConsumer(newValue -> MacroConfig.autoStashManager = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Send Status through Discord"),
                                                MacroConfig.sendDiscordStatus)
                                .setDefaultValue(MacroConfig.DEFAULT_SEND_DISCORD_STATUS)
                                .setSaveConsumer(newValue -> MacroConfig.sendDiscordStatus = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startStrField(Component.literal("Discord Webhook URL"), MacroConfig.discordWebhookUrl)
                                .setDefaultValue(MacroConfig.DEFAULT_DISCORD_WEBHOOK_URL)
                                .setSaveConsumer(newValue -> MacroConfig.discordWebhookUrl = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startIntField(Component.literal("Status Update Time (Minutes)"),
                                                MacroConfig.discordStatusUpdateTime)
                                .setDefaultValue(MacroConfig.DEFAULT_DISCORD_STATUS_UPDATE_TIME)
                                .setMax(1440)
                                .setSaveConsumer(newValue -> MacroConfig.discordStatusUpdateTime = newValue)
                                .build());

                qol.addEntry(builder.entryBuilder()
                                .startBooleanToggle(Component.literal("Show Debug Messages"), MacroConfig.showDebug)
                                .setDefaultValue(MacroConfig.DEFAULT_SHOW_DEBUG)
                                .setTooltip(Component
                                                .literal("Show [Debug] messages in chat for menu detections and AOTV."))
                                .setSaveConsumer(newValue -> MacroConfig.showDebug = newValue)
                                .build());

                return builder.build();
        }

        private static class ButtonEntry extends TooltipListEntry<Object> {
                private final Button button;
                private final Component fieldName;

                @SuppressWarnings("deprecation")
                public ButtonEntry(Component fieldName, Component tooltip, Button.OnPress onPress) {
                        super(fieldName, () -> Optional.of(new Component[] { tooltip }));
                        this.fieldName = fieldName;
                        this.button = Button.builder(fieldName, onPress).bounds(0, 0, 150, 20).build();
                }

                @Override
                public void render(@NotNull GuiGraphics graphics, int index, int y, int x, int entryWidth,
                                int entryHeight,
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
                public @NotNull List<? extends GuiEventListener> children() {
                        return Collections.singletonList(button);
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                        return Collections.singletonList(button);
                }

                @Override
                public @NotNull Object getValue() {
                        return "";
                }

                @Override
                public @NotNull Optional<Object> getDefaultValue() {
                        return Optional.empty();
                }

                @Override
                public void save() {
                }
        }

        private static class DualButtonEntry extends TooltipListEntry<Object> {
                private final Button button1;
                private final Button button2;
                private final Component fieldName;

                public DualButtonEntry(Component fieldName, Component tooltip,
                                Component b1Label, Button.OnPress b1OnPress,
                                Component b2Label, Button.OnPress b2OnPress) {
                        super(fieldName, () -> Optional.of(new Component[] { tooltip }));
                        this.fieldName = fieldName;
                        this.button1 = Button.builder(b1Label, b1OnPress).bounds(0, 0, 75, 20).build();
                        this.button2 = Button.builder(b2Label, b2OnPress).bounds(0, 0, 75, 20).build();
                }

                @Override
                public void render(@NotNull GuiGraphics graphics, int index, int y, int x, int entryWidth,
                                int entryHeight, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
                        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered,
                                        tickDelta);
                        this.button2.setX(x + entryWidth - 80);
                        this.button2.setY(y);
                        this.button2.setWidth(75);
                        this.button2.render(graphics, mouseX, mouseY, tickDelta);

                        this.button1.setX(x + entryWidth - 160);
                        this.button1.setY(y);
                        this.button1.setWidth(75);
                        this.button1.render(graphics, mouseX, mouseY, tickDelta);

                        graphics.drawString(Minecraft.getInstance().font, fieldName, x, y + 6, 0xFFFFFF);
                }

                @Override
                public @NotNull List<? extends GuiEventListener> children() {
                        return java.util.Arrays.asList(button1, button2);
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                        return java.util.Arrays.asList(button1, button2);
                }

                @Override
                public @NotNull Object getValue() {
                        return "";
                }

                @Override
                public @NotNull Optional<Object> getDefaultValue() {
                        return Optional.empty();
                }

                @Override
                public void save() {
                }
        }

        private static class TripleButtonEntry extends TooltipListEntry<Object> {
                private final Button button1;
                private final Button button2;
                private final Button button3;
                private final Component fieldName;

                public TripleButtonEntry(Component fieldName, Component tooltip,
                                Component b1Label, Button.OnPress b1OnPress,
                                Component b2Label, Button.OnPress b2OnPress,
                                Component b3Label, Button.OnPress b3OnPress) {
                        super(fieldName, () -> Optional.of(new Component[] { tooltip }));
                        this.fieldName = fieldName;
                        this.button1 = Button.builder(b1Label, b1OnPress).bounds(0, 0, 65, 20).build();
                        this.button2 = Button.builder(b2Label, b2OnPress).bounds(0, 0, 65, 20).build();
                        this.button3 = Button.builder(b3Label, b3OnPress).bounds(0, 0, 65, 20).build();
                }

                @Override
                public void render(@NotNull GuiGraphics graphics, int index, int y, int x, int entryWidth,
                                int entryHeight, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
                        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered,
                                        tickDelta);
                        this.button3.setX(x + entryWidth - 70);
                        this.button3.setY(y);
                        this.button3.setWidth(65);
                        this.button3.render(graphics, mouseX, mouseY, tickDelta);

                        this.button2.setX(x + entryWidth - 140);
                        this.button2.setY(y);
                        this.button2.setWidth(65);
                        this.button2.render(graphics, mouseX, mouseY, tickDelta);

                        this.button1.setX(x + entryWidth - 210);
                        this.button1.setY(y);
                        this.button1.setWidth(65);
                        this.button1.render(graphics, mouseX, mouseY, tickDelta);

                        graphics.drawString(Minecraft.getInstance().font, fieldName, x, y + 6, 0xFFFFFF);
                }

                @Override
                public @NotNull List<? extends GuiEventListener> children() {
                        return java.util.Arrays.asList(button1, button2, button3);
                }

                @Override
                public @NotNull List<? extends NarratableEntry> narratables() {
                        return java.util.Arrays.asList(button1, button2, button3);
                }

                @Override
                public @NotNull Object getValue() {
                        return "";
                }

                @Override
                public @NotNull Optional<Object> getDefaultValue() {
                        return Optional.empty();
                }

                @Override
                public void save() {
                }
        }
}
