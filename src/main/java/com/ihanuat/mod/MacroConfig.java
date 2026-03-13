package com.ihanuat.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class MacroConfig {
    // GearSwapMode enum removed and replaced by boolean toggles for better
    // decoupling.

    public enum PetRarity {
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC
    }

    public enum UnflyMode {
        SNEAK, DOUBLE_TAP_SPACE
    }

    public static final int DEFAULT_PEST_THRESHOLD = 5;
    public static final boolean DEFAULT_TRIGGER_PEST_ON_CHAT = true;
    public static final int DEFAULT_VISITOR_THRESHOLD = 5;
    public static final boolean DEFAULT_AUTO_WARDROBE_PEST = true;
    public static final boolean DEFAULT_AUTO_WARDROBE_VISITOR = false;
    public static final boolean DEFAULT_AUTO_ROD_PEST_CD = false;
    public static final boolean DEFAULT_AUTO_ROD_PEST_SPAWN = false;
    public static final boolean DEFAULT_AUTO_ROD_RETURN_TO_FARM = false;
    public static final UnflyMode DEFAULT_UNFLY_MODE = UnflyMode.DOUBLE_TAP_SPACE;
    public static final boolean DEFAULT_AUTO_VISITOR = true;
    public static final boolean DEFAULT_AUTO_EQUIPMENT = true;
    public static final boolean DEFAULT_AUTO_STASH_MANAGER = false;
    public static final boolean DEFAULT_AUTO_BOOK_COMBINE = false;
    public static final boolean DEFAULT_AUTO_GEORGE_SELL = false;
    public static final boolean DEFAULT_AUTO_BOOSTER_COOKIE = true;
    public static final boolean DEFAULT_ALWAYS_ACTIVE_COMBINE = false;
    public static final java.util.List<String> DEFAULT_BOOSTER_COOKIE_ITEMS = java.util.Arrays.asList(
            "Atmospheric Filter", "Squeaky Toy", "Beady Eyes", "Clipped Wings", "Overclocker",
            "Mantid Claw", "Flowering Bouquet", "Bookworm", "Chirping Stereo", "Firefly",
            "Capsule", "Vinyl");
    public static final java.util.List<String> DEFAULT_CUSTOM_ENCHANTMENT_LEVELS = java.util.Collections.emptyList();
    public static final int DEFAULT_GEORGE_SELL_THRESHOLD = 3;
    public static final int DEFAULT_ROTATION_TIME = 500;
    public static final boolean DEFAULT_AOTV_TO_ROOF = false;
    public static final int DEFAULT_AOTV_ROOF_PITCH = 88;
    public static final int DEFAULT_AOTV_ROOF_PITCH_HUMANIZATION = 3;
    public static final java.util.List<String> DEFAULT_AOTV_ROOF_PLOTS = java.util.Collections.emptyList();
    public static final int DEFAULT_WARDROBE_SLOT_FARMING = 1;
    public static final int DEFAULT_WARDROBE_SLOT_PEST = 2;
    public static final int DEFAULT_WARDROBE_SLOT_VISITOR = 3;
    public static final boolean DEFAULT_ARMOR_SWAP_VISITOR = false;
    public static final int DEFAULT_GUI_CLICK_DELAY = 500;
    public static final int DEFAULT_EQUIPMENT_SWAP_DELAY = 250;
    public static final int DEFAULT_ROD_SWAP_DELAY = 100;
    public static final int DEFAULT_BOOK_COMBINE_DELAY = 300;
    public static final int DEFAULT_BOOK_THRESHOLD = 7;
    public static final int DEFAULT_ADDITIONAL_RANDOM_DELAY = 0;
    public static final java.util.List<String> DEFAULT_FARM_SCRIPTS = java.util.Arrays.asList(
            "netherwart:1", "netherwart:0", "sugarcane:classical", "sugarcane:sshape",
            "cactus", "cocoa", "mushroom:1", "mushroom:0", "pumpkin:1");
    public static final String DEFAULT_RESTART_SCRIPT = "netherwart:1";
    public static final int DEFAULT_GARDEN_WARP_DELAY = 1000;
    public static final int DEFAULT_REST_SCRIPTING_TIME = 30;
    public static final int DEFAULT_REST_SCRIPTING_TIME_OFFSET = 3;
    public static final int DEFAULT_REST_BREAK_TIME = 20;
    public static final int DEFAULT_REST_BREAK_TIME_OFFSET = 3;
    public static final boolean DEFAULT_ENABLE_PLOT_TP_REWARP = false;
    public static final boolean DEFAULT_HOLD_W_UNTIL_WALL = false;
    public static final String DEFAULT_PLOT_TP_NUMBER = "0";
    public static final String DEFAULT_DISCORD_WEBHOOK_URL = "";
    public static final int DEFAULT_DISCORD_STATUS_UPDATE_TIME = 5;
    public static final boolean DEFAULT_SEND_DISCORD_STATUS = false;
    public static final boolean DEFAULT_PERSIST_SESSION_TIMER = true;
    public static final boolean DEFAULT_COMPACT_PROFIT_CALCULATOR = false;
    public static final boolean DEFAULT_SHOW_PROFIT_HUD_WHILE_INACTIVE = false;
    public static final boolean DEFAULT_HIDE_FILTERED_CHAT = true;
    public static final boolean DEFAULT_AUTO_DROP_JUNK = false;
    public static final java.util.List<String> DEFAULT_JUNK_ITEMS = java.util.Arrays.asList(
            "Fruit Bowl", "Farming Exp Boost", "Sunder VI");
    public static final String DEFAULT_DROP_JUNK_PLOT_TP = "0";
    public static final int DEFAULT_JUNK_THRESHOLD = 3;
    public static final int DEFAULT_JUNK_ITEM_DROP_DELAY = 300;
    public static final int DEFAULT_PEST_CHAT_TRIGGER_DELAY = 0;
    public static final boolean DEFAULT_SHOW_DEBUG = false;
    public static final boolean DEFAULT_GUI_ONLY_IN_GARDEN = true;
    public static final boolean DEFAULT_BREAK_BLOCKS_BEFORE_AOTV = false;
    public static final boolean DEFAULT_DELAY_PEST_FOR_CROP_FEVER = false;

    // Pet Tracker Defaults
    public static final java.util.List<String> DEFAULT_PET_TRACKER_LIST = java.util.Arrays.asList(
            "PET_ROSE_DRAGON:Rose Dragon:200:LEGENDARY");

    public static final int DEFAULT_HUD_X = 10;
    public static final int DEFAULT_HUD_Y = 10;
    public static final float DEFAULT_HUD_SCALE = 1.0f;
    public static final boolean DEFAULT_SHOW_HUD = true;

    public static final int DEFAULT_SESSION_PROFIT_HUD_X = 10;
    public static final int DEFAULT_SESSION_PROFIT_HUD_Y = 150;
    public static final float DEFAULT_SESSION_PROFIT_HUD_SCALE = 1.0f;
    public static final boolean DEFAULT_SHOW_SESSION_PROFIT_HUD = true;

    public static final int DEFAULT_DAILY_HUD_X = 10;
    public static final int DEFAULT_DAILY_HUD_Y = 290;
    public static final float DEFAULT_DAILY_HUD_SCALE = 1.0f;
    public static final boolean DEFAULT_SHOW_DAILY_HUD = true;

    public static final int DEFAULT_LIFETIME_HUD_X = 10;
    public static final int DEFAULT_LIFETIME_HUD_Y = 430;
    public static final float DEFAULT_LIFETIME_HUD_SCALE = 1.0f;
    public static final boolean DEFAULT_SHOW_LIFETIME_HUD = true;

    public static int pestThreshold = DEFAULT_PEST_THRESHOLD;
    public static boolean triggerPestOnChat = DEFAULT_TRIGGER_PEST_ON_CHAT;
    public static int pestChatTriggerDelay = DEFAULT_PEST_CHAT_TRIGGER_DELAY;
    public static int visitorThreshold = DEFAULT_VISITOR_THRESHOLD;
    public static boolean autoWardrobePest = DEFAULT_AUTO_WARDROBE_PEST;
    public static boolean autoWardrobeVisitor = DEFAULT_AUTO_WARDROBE_VISITOR;
    public static boolean autoRodPestCd = DEFAULT_AUTO_ROD_PEST_CD;
    public static boolean autoRodPestSpawn = DEFAULT_AUTO_ROD_PEST_SPAWN;
    public static boolean autoRodReturnToFarm = DEFAULT_AUTO_ROD_RETURN_TO_FARM;
    public static UnflyMode unflyMode = DEFAULT_UNFLY_MODE;
    public static boolean autoVisitor = DEFAULT_AUTO_VISITOR;
    public static boolean autoEquipment = DEFAULT_AUTO_EQUIPMENT;
    public static boolean autoStashManager = DEFAULT_AUTO_STASH_MANAGER;
    public static boolean autoBookCombine = DEFAULT_AUTO_BOOK_COMBINE;
    public static boolean autoGeorgeSell = DEFAULT_AUTO_GEORGE_SELL;
    public static boolean autoBoosterCookie = DEFAULT_AUTO_BOOSTER_COOKIE;
    public static boolean alwaysActiveCombine = DEFAULT_ALWAYS_ACTIVE_COMBINE;
    public static java.util.List<String> boosterCookieItems = new java.util.ArrayList<>(DEFAULT_BOOSTER_COOKIE_ITEMS);
    public static java.util.List<String> customEnchantmentLevels = new java.util.ArrayList<>(
            DEFAULT_CUSTOM_ENCHANTMENT_LEVELS);
    public static int georgeSellThreshold = DEFAULT_GEORGE_SELL_THRESHOLD;
    public static int rotationTime = DEFAULT_ROTATION_TIME;
    public static boolean aotvToRoof = DEFAULT_AOTV_TO_ROOF;
    public static int aotvRoofPitch = DEFAULT_AOTV_ROOF_PITCH;
    public static int aotvRoofPitchHumanization = DEFAULT_AOTV_ROOF_PITCH_HUMANIZATION;
    public static java.util.List<String> aotvRoofPlots = new java.util.ArrayList<>(DEFAULT_AOTV_ROOF_PLOTS);

    // Wardrobe Slots
    public static int wardrobeSlotFarming = DEFAULT_WARDROBE_SLOT_FARMING;
    public static int wardrobeSlotPest = DEFAULT_WARDROBE_SLOT_PEST;
    public static int wardrobeSlotVisitor = DEFAULT_WARDROBE_SLOT_VISITOR;

    // GUI Click Delay (ms)
    public static int guiClickDelay = DEFAULT_GUI_CLICK_DELAY;
    public static int equipmentSwapDelay = DEFAULT_EQUIPMENT_SWAP_DELAY;
    public static int rodSwapDelay = DEFAULT_ROD_SWAP_DELAY;
    public static int bookCombineDelay = DEFAULT_BOOK_COMBINE_DELAY;
    public static int bookThreshold = DEFAULT_BOOK_THRESHOLD;

    // Additional Random Delay (ms) added to gui interactions, tool swaps, warps,
    // and rotations
    public static int additionalRandomDelay = DEFAULT_ADDITIONAL_RANDOM_DELAY;

    public static int getRandomizedDelay(int baseDelay) {
        if (additionalRandomDelay <= 0)
            return baseDelay;
        return baseDelay + (int) (Math.random() * (additionalRandomDelay + 1));
    }

    // Farm Script Command (sent to restart farming)
    public static String restartScript = DEFAULT_RESTART_SCRIPT;

    public static String getFullRestartCommand() {
        if (restartScript == null || restartScript.isEmpty())
            return "";
        if (restartScript.startsWith("/") || restartScript.startsWith(".ez-startscript"))
            return restartScript;
        return ".ez-startscript " + restartScript;
    }

    public static String getScriptDescription(String script) {
        if (script == null)
            return "";
        switch (script) {
            case "netherwart:1":
                return "Wart/Crops (S-Shape)";
            case "netherwart:0":
                return "Wart/Crops (Vertical)";
            case "sugarcane:classical":
                return "Sugarcane/Flowers (Classical)";
            case "sugarcane:sshape":
                return "Sunflower (S-Shape)";
            case "cactus":
                return "Cactus";
            case "cocoa":
                return "Cocoa";
            case "mushroom:1":
                return "Mushroom (Staircase)";
            case "mushroom:0":
                return "Mushroom (Classical)";
            case "pumpkin:1":
                return "Pumpkin/Melon";
            default:
                return "Custom Script";
        }
    }

    // Garden Warp Delay (ms) - configurable delay after garden warp
    public static int gardenWarpDelay = DEFAULT_GARDEN_WARP_DELAY;

    // Dynamic Rest (Minutes)
    public static int restScriptingTime = DEFAULT_REST_SCRIPTING_TIME;
    public static int restScriptingTimeOffset = DEFAULT_REST_SCRIPTING_TIME_OFFSET;
    public static int restBreakTime = DEFAULT_REST_BREAK_TIME;
    public static int restBreakTimeOffset = DEFAULT_REST_BREAK_TIME_OFFSET;

    public static boolean enablePlotTpRewarp = DEFAULT_ENABLE_PLOT_TP_REWARP;
    public static boolean holdWUntilWall = DEFAULT_HOLD_W_UNTIL_WALL;
    public static String plotTpNumber = DEFAULT_PLOT_TP_NUMBER;
    public static String discordWebhookUrl = DEFAULT_DISCORD_WEBHOOK_URL;
    public static int discordStatusUpdateTime = DEFAULT_DISCORD_STATUS_UPDATE_TIME;
    public static boolean sendDiscordStatus = DEFAULT_SEND_DISCORD_STATUS;
    public static boolean persistSessionTimer = DEFAULT_PERSIST_SESSION_TIMER;
    public static boolean compactProfitCalculator = DEFAULT_COMPACT_PROFIT_CALCULATOR;
    public static boolean showProfitHudWhileInactive = DEFAULT_SHOW_PROFIT_HUD_WHILE_INACTIVE;
    public static boolean hideFilteredChat = DEFAULT_HIDE_FILTERED_CHAT;
    public static boolean autoDropJunk = DEFAULT_AUTO_DROP_JUNK;
    public static java.util.List<String> junkItems = new java.util.ArrayList<>(DEFAULT_JUNK_ITEMS);
    public static String dropJunkPlotTp = DEFAULT_DROP_JUNK_PLOT_TP;
    public static int junkThreshold = DEFAULT_JUNK_THRESHOLD;
    public static int junkItemDropDelay = DEFAULT_JUNK_ITEM_DROP_DELAY;
    public static boolean showDebug = DEFAULT_SHOW_DEBUG;
    public static boolean guiOnlyInGarden = DEFAULT_GUI_ONLY_IN_GARDEN;
    public static boolean breakBlocksBeforeAotv = DEFAULT_BREAK_BLOCKS_BEFORE_AOTV;
    public static boolean delayPestForCropFever = DEFAULT_DELAY_PEST_FOR_CROP_FEVER;

    public static java.util.List<String> petTrackerList = new java.util.ArrayList<>(DEFAULT_PET_TRACKER_LIST);

    public static class PetInfo {
        public String tag;
        public String name;
        public int maxLevel;
        public PetRarity rarity;

        public PetInfo(String config) {
            String[] parts = config.split(":");
            if (parts.length >= 4) {
                this.tag = parts[0].trim();
                this.name = capitalizeWords(parts[1].trim());
                try {
                    this.maxLevel = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException e) {
                    this.maxLevel = 100;
                }
                try {
                    this.rarity = PetRarity.valueOf(parts[3].trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    this.rarity = PetRarity.LEGENDARY;
                }
            } else {
                this.tag = "UNKNOWN";
                this.name = "Unknown Pet";
                this.maxLevel = 100;
                this.rarity = PetRarity.LEGENDARY;
            }
        }

        private String capitalizeWords(String input) {
            if (input == null || input.isEmpty())
                return input;
            String[] words = input.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (word.length() > 0) {
                    sb.append(Character.toUpperCase(word.charAt(0)));
                    if (word.length() > 1) {
                        sb.append(word.substring(1).toLowerCase());
                    }
                    sb.append(" ");
                }
            }
            return sb.toString().trim();
        }

        @Override
        public String toString() {
            return tag + ":" + name + ":" + maxLevel + ":" + rarity;
        }
    }

    public static long lifetimeAccumulated = 0;

    // HUD
    public static int hudX = DEFAULT_HUD_X;
    public static int hudY = DEFAULT_HUD_Y;
    public static float hudScale = DEFAULT_HUD_SCALE;
    public static boolean showHud = DEFAULT_SHOW_HUD;

    public static int sessionProfitHudX = DEFAULT_SESSION_PROFIT_HUD_X;
    public static int sessionProfitHudY = DEFAULT_SESSION_PROFIT_HUD_Y;
    public static float sessionProfitHudScale = DEFAULT_SESSION_PROFIT_HUD_SCALE;
    public static boolean showSessionProfitHud = DEFAULT_SHOW_SESSION_PROFIT_HUD;

    public static int dailyHudX = DEFAULT_DAILY_HUD_X;
    public static int dailyHudY = DEFAULT_DAILY_HUD_Y;
    public static float dailyHudScale = DEFAULT_DAILY_HUD_SCALE;
    public static boolean showDailyHud = DEFAULT_SHOW_DAILY_HUD;

    public static int lifetimeHudX = DEFAULT_LIFETIME_HUD_X;
    public static int lifetimeHudY = DEFAULT_LIFETIME_HUD_Y;
    public static float lifetimeHudScale = DEFAULT_LIFETIME_HUD_SCALE;
    public static boolean showLifetimeHud = DEFAULT_SHOW_LIFETIME_HUD;

    // Rewarp coordinates
    public static double rewarpEndX = 0;
    public static double rewarpEndY = 0;
    public static double rewarpEndZ = 0;
    public static boolean rewarpEndPosSet = false;

    public static void executePlotTpRewarp(net.minecraft.client.Minecraft client) {
        if (enablePlotTpRewarp) {
            com.ihanuat.mod.util.ClientUtils.sendCommand(client, "/plottp " + plotTpNumber);
        }
    }

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("pest_macro_config.json")
            .toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        ConfigData data = new ConfigData();
        data.pestThreshold = pestThreshold;
        data.triggerPestOnChat = triggerPestOnChat;
        data.pestChatTriggerDelay = pestChatTriggerDelay;
        data.visitorThreshold = visitorThreshold;
        data.autoWardrobePest = autoWardrobePest;
        data.autoWardrobeVisitor = autoWardrobeVisitor;
        data.autoRodPestCd = autoRodPestCd;
        data.autoRodPestSpawn = autoRodPestSpawn;
        data.autoRodReturnToFarm = autoRodReturnToFarm;
        data.unflyMode = unflyMode;
        data.autoVisitor = autoVisitor;
        data.autoEquipment = autoEquipment;
        data.autoStashManager = autoStashManager;
        data.autoBookCombine = autoBookCombine;
        data.autoGeorgeSell = autoGeorgeSell;
        data.alwaysActiveCombine = alwaysActiveCombine;
        data.autoBoosterCookie = autoBoosterCookie;
        data.boosterCookieItems = new java.util.ArrayList<>(boosterCookieItems);
        data.customEnchantmentLevels = new java.util.ArrayList<>(customEnchantmentLevels);
        data.georgeSellThreshold = georgeSellThreshold;
        data.rotationTime = rotationTime;
        data.aotvToRoof = aotvToRoof;
        data.aotvRoofPitch = aotvRoofPitch;
        data.aotvRoofPitchHumanization = aotvRoofPitchHumanization;
        data.aotvRoofPlots = new java.util.ArrayList<>(aotvRoofPlots);

        data.wardrobeSlotFarming = wardrobeSlotFarming;
        data.wardrobeSlotPest = wardrobeSlotPest;
        data.wardrobeSlotVisitor = wardrobeSlotVisitor;
        data.guiClickDelay = guiClickDelay;
        data.equipmentSwapDelay = equipmentSwapDelay;
        data.rodSwapDelay = rodSwapDelay;
        data.bookCombineDelay = bookCombineDelay;
        data.bookThreshold = bookThreshold;
        data.additionalRandomDelay = additionalRandomDelay;
        data.restartScript = restartScript;
        data.gardenWarpDelay = gardenWarpDelay;

        data.restScriptingTime = restScriptingTime;
        data.restScriptingTimeOffset = restScriptingTimeOffset;
        data.restBreakTime = restBreakTime;
        data.restBreakTimeOffset = restBreakTimeOffset;

        data.enablePlotTpRewarp = enablePlotTpRewarp;
        data.holdWUntilWall = holdWUntilWall;
        data.plotTpNumber = plotTpNumber;
        data.discordWebhookUrl = discordWebhookUrl;
        data.discordStatusUpdateTime = discordStatusUpdateTime;
        data.sendDiscordStatus = sendDiscordStatus;
        data.rewarpEndX = rewarpEndX;
        data.rewarpEndY = rewarpEndY;
        data.rewarpEndZ = rewarpEndZ;
        data.rewarpEndPosSet = rewarpEndPosSet;
        data.persistSessionTimer = persistSessionTimer;
        data.compactProfitCalculator = compactProfitCalculator;
        data.showProfitHudWhileInactive = showProfitHudWhileInactive;
        data.hideFilteredChat = hideFilteredChat;
        data.autoDropJunk = autoDropJunk;
        data.junkItems = new java.util.ArrayList<>(junkItems);
        data.dropJunkPlotTp = dropJunkPlotTp;
        data.junkThreshold = junkThreshold;
        data.junkItemDropDelay = junkItemDropDelay;
        data.showDebug = showDebug;
        data.guiOnlyInGarden = guiOnlyInGarden;
        data.breakBlocksBeforeAotv = breakBlocksBeforeAotv;
        data.delayPestForCropFever = delayPestForCropFever;

        data.petTrackerList = new java.util.ArrayList<>(petTrackerList);

        data.hudX = hudX;
        data.hudY = hudY;
        data.hudScale = hudScale;
        data.showHud = showHud;

        data.sessionProfitHudX = sessionProfitHudX;
        data.sessionProfitHudY = sessionProfitHudY;
        data.sessionProfitHudScale = sessionProfitHudScale;
        data.showSessionProfitHud = showSessionProfitHud;

        data.dailyHudX = dailyHudX;
        data.dailyHudY = dailyHudY;
        data.dailyHudScale = dailyHudScale;
        data.showDailyHud = showDailyHud;

        data.lifetimeHudX = lifetimeHudX;
        data.lifetimeHudY = lifetimeHudY;
        data.lifetimeHudScale = lifetimeHudScale;
        data.showLifetimeHud = showLifetimeHud;

        data.lifetimeAccumulated = lifetimeAccumulated;

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save(); // Create default config
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null) {
                pestThreshold = data.pestThreshold;
                triggerPestOnChat = data.triggerPestOnChat;
                pestChatTriggerDelay = data.pestChatTriggerDelay;
                visitorThreshold = data.visitorThreshold;
                autoWardrobePest = data.autoWardrobePest;
                autoWardrobeVisitor = data.autoWardrobeVisitor;
                autoRodPestCd = data.autoRodPestCd;
                autoRodPestSpawn = data.autoRodPestSpawn;
                autoRodReturnToFarm = data.autoRodReturnToFarm;
                unflyMode = data.unflyMode != null ? data.unflyMode : DEFAULT_UNFLY_MODE;
                autoVisitor = data.autoVisitor;
                autoEquipment = data.autoEquipment;
                autoStashManager = data.autoStashManager;
                autoBookCombine = data.autoBookCombine;
                autoGeorgeSell = data.autoGeorgeSell;
                alwaysActiveCombine = data.alwaysActiveCombine;
                autoBoosterCookie = data.autoBoosterCookie;
                if (data.boosterCookieItems != null) {
                    boosterCookieItems = new java.util.ArrayList<>(data.boosterCookieItems);
                }
                if (data.customEnchantmentLevels != null) {
                    customEnchantmentLevels = new java.util.ArrayList<>(data.customEnchantmentLevels);
                }
                georgeSellThreshold = data.georgeSellThreshold;
                rotationTime = data.rotationTime;
                aotvToRoof = data.aotvToRoof;
                aotvRoofPitch = Math.max(45, Math.min(90, data.aotvRoofPitch));
                aotvRoofPitchHumanization = Math.max(0, Math.min(15, data.aotvRoofPitchHumanization));
                if (data.aotvRoofPlots != null) {
                    aotvRoofPlots = new java.util.ArrayList<>(data.aotvRoofPlots);
                }

                wardrobeSlotFarming = data.wardrobeSlotFarming;
                wardrobeSlotPest = data.wardrobeSlotPest;
                wardrobeSlotVisitor = data.wardrobeSlotVisitor;
                guiClickDelay = data.guiClickDelay;
                equipmentSwapDelay = data.equipmentSwapDelay;
                rodSwapDelay = data.rodSwapDelay;
                bookCombineDelay = data.bookCombineDelay;
                bookThreshold = data.bookThreshold;
                additionalRandomDelay = data.additionalRandomDelay;
                if (data.restartScript != null && !data.restartScript.isBlank())
                    restartScript = data.restartScript;
                gardenWarpDelay = data.gardenWarpDelay;

                restScriptingTime = data.restScriptingTime;
                restScriptingTimeOffset = data.restScriptingTimeOffset;
                restBreakTime = data.restBreakTime;
                restBreakTimeOffset = data.restBreakTimeOffset;

                enablePlotTpRewarp = data.enablePlotTpRewarp;
                holdWUntilWall = data.holdWUntilWall;
                if (data.plotTpNumber != null)
                    plotTpNumber = data.plotTpNumber;
                if (data.discordWebhookUrl != null)
                    discordWebhookUrl = data.discordWebhookUrl;
                discordStatusUpdateTime = data.discordStatusUpdateTime;
                sendDiscordStatus = data.sendDiscordStatus;
                rewarpEndX = data.rewarpEndX;
                rewarpEndY = data.rewarpEndY;
                rewarpEndZ = data.rewarpEndZ;
                rewarpEndPosSet = data.rewarpEndPosSet;
                persistSessionTimer = data.persistSessionTimer;
                compactProfitCalculator = data.compactProfitCalculator;
                showProfitHudWhileInactive = data.showProfitHudWhileInactive;
                hideFilteredChat = data.hideFilteredChat;
                autoDropJunk = data.autoDropJunk;
                if (data.junkItems != null) {
                    junkItems = new java.util.ArrayList<>(data.junkItems);
                }
                if (data.dropJunkPlotTp != null) {
                    dropJunkPlotTp = data.dropJunkPlotTp;
                }
                junkThreshold = data.junkThreshold;
                junkItemDropDelay = data.junkItemDropDelay;
                showDebug = data.showDebug;
                guiOnlyInGarden = data.guiOnlyInGarden;
                breakBlocksBeforeAotv = data.breakBlocksBeforeAotv;
                delayPestForCropFever = data.delayPestForCropFever;

                if (data.petTrackerList != null) {
                    petTrackerList = new java.util.ArrayList<>(data.petTrackerList);
                }

                hudX = data.hudX;
                hudY = data.hudY;
                hudScale = data.hudScale > 0 ? data.hudScale : DEFAULT_HUD_SCALE;
                showHud = data.showHud;

                sessionProfitHudX = data.sessionProfitHudX;
                sessionProfitHudY = data.sessionProfitHudY;
                sessionProfitHudScale = data.sessionProfitHudScale > 0 ? data.sessionProfitHudScale
                        : DEFAULT_SESSION_PROFIT_HUD_SCALE;
                showSessionProfitHud = data.showSessionProfitHud;

                dailyHudX = data.dailyHudX;
                dailyHudY = data.dailyHudY;
                dailyHudScale = data.dailyHudScale > 0 ? data.dailyHudScale : DEFAULT_DAILY_HUD_SCALE;
                showDailyHud = data.showDailyHud;

                lifetimeHudX = data.lifetimeHudX;
                lifetimeHudY = data.lifetimeHudY;
                lifetimeHudScale = data.lifetimeHudScale > 0 ? data.lifetimeHudScale : DEFAULT_LIFETIME_HUD_SCALE;
                showLifetimeHud = data.showLifetimeHud;

                lifetimeAccumulated = data.lifetimeAccumulated;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ConfigData {
        int pestThreshold = DEFAULT_PEST_THRESHOLD;
        boolean triggerPestOnChat = DEFAULT_TRIGGER_PEST_ON_CHAT;
        int pestChatTriggerDelay = DEFAULT_PEST_CHAT_TRIGGER_DELAY;
        int visitorThreshold = DEFAULT_VISITOR_THRESHOLD;
        boolean autoWardrobePest = DEFAULT_AUTO_WARDROBE_PEST;
        boolean autoWardrobeVisitor = DEFAULT_AUTO_WARDROBE_VISITOR;
        boolean autoRodPestCd = DEFAULT_AUTO_ROD_PEST_CD;
        boolean autoRodPestSpawn = DEFAULT_AUTO_ROD_PEST_SPAWN;
        boolean autoRodReturnToFarm = DEFAULT_AUTO_ROD_RETURN_TO_FARM;
        UnflyMode unflyMode = DEFAULT_UNFLY_MODE;
        boolean autoVisitor = DEFAULT_AUTO_VISITOR;
        boolean autoEquipment = DEFAULT_AUTO_EQUIPMENT;
        boolean autoStashManager = DEFAULT_AUTO_STASH_MANAGER;
        boolean autoBookCombine = DEFAULT_AUTO_BOOK_COMBINE;
        boolean autoGeorgeSell = DEFAULT_AUTO_GEORGE_SELL;
        boolean alwaysActiveCombine = DEFAULT_ALWAYS_ACTIVE_COMBINE;
        boolean autoBoosterCookie = DEFAULT_AUTO_BOOSTER_COOKIE;
        java.util.List<String> boosterCookieItems = new java.util.ArrayList<>(DEFAULT_BOOSTER_COOKIE_ITEMS);
        java.util.List<String> customEnchantmentLevels = new java.util.ArrayList<>(DEFAULT_CUSTOM_ENCHANTMENT_LEVELS);
        int georgeSellThreshold = DEFAULT_GEORGE_SELL_THRESHOLD;
        int rotationTime = DEFAULT_ROTATION_TIME;
        boolean aotvToRoof = DEFAULT_AOTV_TO_ROOF;
        int aotvRoofPitch = DEFAULT_AOTV_ROOF_PITCH;
        int aotvRoofPitchHumanization = DEFAULT_AOTV_ROOF_PITCH_HUMANIZATION;
        java.util.List<String> aotvRoofPlots = new java.util.ArrayList<>(DEFAULT_AOTV_ROOF_PLOTS);

        int wardrobeSlotFarming = DEFAULT_WARDROBE_SLOT_FARMING;
        int wardrobeSlotPest = DEFAULT_WARDROBE_SLOT_PEST;
        int wardrobeSlotVisitor = DEFAULT_WARDROBE_SLOT_VISITOR;

        int guiClickDelay = DEFAULT_GUI_CLICK_DELAY;
        int equipmentSwapDelay = DEFAULT_EQUIPMENT_SWAP_DELAY;
        int rodSwapDelay = DEFAULT_ROD_SWAP_DELAY;
        int bookCombineDelay = DEFAULT_BOOK_COMBINE_DELAY;
        int bookThreshold = DEFAULT_BOOK_THRESHOLD;
        int additionalRandomDelay = DEFAULT_ADDITIONAL_RANDOM_DELAY;
        String restartScript = DEFAULT_RESTART_SCRIPT;
        int gardenWarpDelay = DEFAULT_GARDEN_WARP_DELAY;

        int restScriptingTime = DEFAULT_REST_SCRIPTING_TIME;
        int restScriptingTimeOffset = DEFAULT_REST_SCRIPTING_TIME_OFFSET;
        int restBreakTime = DEFAULT_REST_BREAK_TIME;
        int restBreakTimeOffset = DEFAULT_REST_BREAK_TIME_OFFSET;

        boolean enablePlotTpRewarp = DEFAULT_ENABLE_PLOT_TP_REWARP;
        boolean holdWUntilWall = DEFAULT_HOLD_W_UNTIL_WALL;
        String plotTpNumber = DEFAULT_PLOT_TP_NUMBER;
        String discordWebhookUrl = DEFAULT_DISCORD_WEBHOOK_URL;
        int discordStatusUpdateTime = DEFAULT_DISCORD_STATUS_UPDATE_TIME;
        boolean sendDiscordStatus = DEFAULT_SEND_DISCORD_STATUS;
        double rewarpEndX = 0;
        double rewarpEndY = 0;
        double rewarpEndZ = 0;
        boolean rewarpEndPosSet = false;
        boolean persistSessionTimer = DEFAULT_PERSIST_SESSION_TIMER;
        boolean compactProfitCalculator = DEFAULT_COMPACT_PROFIT_CALCULATOR;
        boolean showProfitHudWhileInactive = DEFAULT_SHOW_PROFIT_HUD_WHILE_INACTIVE;
        boolean hideFilteredChat = DEFAULT_HIDE_FILTERED_CHAT;
        boolean autoDropJunk = DEFAULT_AUTO_DROP_JUNK;
        java.util.List<String> junkItems = new java.util.ArrayList<>(DEFAULT_JUNK_ITEMS);
        String dropJunkPlotTp = DEFAULT_DROP_JUNK_PLOT_TP;
        int junkThreshold = DEFAULT_JUNK_THRESHOLD;
        int junkItemDropDelay = DEFAULT_JUNK_ITEM_DROP_DELAY;
        boolean showDebug = DEFAULT_SHOW_DEBUG;
        boolean guiOnlyInGarden = DEFAULT_GUI_ONLY_IN_GARDEN;
        boolean breakBlocksBeforeAotv = DEFAULT_BREAK_BLOCKS_BEFORE_AOTV;
        boolean delayPestForCropFever = DEFAULT_DELAY_PEST_FOR_CROP_FEVER;

        java.util.List<String> petTrackerList = new java.util.ArrayList<>(DEFAULT_PET_TRACKER_LIST);

        int hudX = DEFAULT_HUD_X;
        int hudY = DEFAULT_HUD_Y;
        float hudScale = DEFAULT_HUD_SCALE;
        boolean showHud = DEFAULT_SHOW_HUD;

        int sessionProfitHudX = DEFAULT_SESSION_PROFIT_HUD_X;
        int sessionProfitHudY = DEFAULT_SESSION_PROFIT_HUD_Y;
        float sessionProfitHudScale = DEFAULT_SESSION_PROFIT_HUD_SCALE;
        boolean showSessionProfitHud = DEFAULT_SHOW_SESSION_PROFIT_HUD;

        int dailyHudX = DEFAULT_DAILY_HUD_X;
        int dailyHudY = DEFAULT_DAILY_HUD_Y;
        float dailyHudScale = DEFAULT_DAILY_HUD_SCALE;
        boolean showDailyHud = DEFAULT_SHOW_DAILY_HUD;

        int lifetimeHudX = DEFAULT_LIFETIME_HUD_X;
        int lifetimeHudY = DEFAULT_LIFETIME_HUD_Y;
        float lifetimeHudScale = DEFAULT_LIFETIME_HUD_SCALE;
        boolean showLifetimeHud = DEFAULT_SHOW_LIFETIME_HUD;

        long lifetimeAccumulated = 0;
    }
}
