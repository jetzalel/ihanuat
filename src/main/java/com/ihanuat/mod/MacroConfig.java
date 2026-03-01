package com.ihanuat.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class MacroConfig {
    public enum GearSwapMode {
        NONE, WARDROBE, ROD
    }

    public enum UnflyMode {
        SNEAK, DOUBLE_TAP_SPACE
    }

    public static final int DEFAULT_PEST_THRESHOLD = 5;
    public static final int DEFAULT_VISITOR_THRESHOLD = 5;
    public static final GearSwapMode DEFAULT_GEAR_SWAP_MODE = GearSwapMode.NONE;
    public static final UnflyMode DEFAULT_UNFLY_MODE = UnflyMode.DOUBLE_TAP_SPACE;
    public static final boolean DEFAULT_AUTO_VISITOR = true;
    public static final boolean DEFAULT_AUTO_EQUIPMENT = true;
    public static final boolean DEFAULT_AUTO_STASH_MANAGER = false;
    public static final boolean DEFAULT_AUTO_BOOK_COMBINE = false;
    public static final boolean DEFAULT_AUTO_GEORGE_SELL = false;
    public static final boolean DEFAULT_AUTO_BOOSTER_COOKIE = true;
    public static final java.util.List<String> DEFAULT_BOOSTER_COOKIE_ITEMS = java.util.Arrays.asList(
            "Atmospheric Filter", "Squeaky Toy", "Beady Eyes", "Clipped Wings", "Overclocker",
            "Mantid Claw", "Flowering Bouquet", "Bookworm", "Chirping Stereo", "Firefly",
            "Capsule", "Vinyl");
    public static final java.util.List<String> DEFAULT_CUSTOM_ENCHANTMENT_LEVELS = java.util.Collections.emptyList();
    public static final int DEFAULT_GEORGE_SELL_THRESHOLD = 3;
    public static final int DEFAULT_AUTO_EQUIPMENT_FARMING_TIME = 170;
    public static final int DEFAULT_ROTATION_TIME = 500;
    public static final boolean DEFAULT_AOTV_TO_ROOF = false;
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
    public static final String DEFAULT_RESTART_SCRIPT = ".ez-startscript netherwart:1";
    public static final int DEFAULT_GARDEN_WARP_DELAY = 1000;
    public static final int DEFAULT_REST_SCRIPTING_TIME = 30;
    public static final int DEFAULT_REST_SCRIPTING_TIME_OFFSET = 3;
    public static final int DEFAULT_REST_BREAK_TIME = 20;
    public static final int DEFAULT_REST_BREAK_TIME_OFFSET = 3;
    public static final boolean DEFAULT_ENABLE_PLOT_TP_REWARP = false;
    public static final String DEFAULT_PLOT_TP_NUMBER = "0";
    public static final String DEFAULT_DISCORD_WEBHOOK_URL = "";
    public static final int DEFAULT_DISCORD_STATUS_UPDATE_TIME = 5;
    public static final boolean DEFAULT_SEND_DISCORD_STATUS = false;

    public static int pestThreshold = DEFAULT_PEST_THRESHOLD;
    public static int visitorThreshold = DEFAULT_VISITOR_THRESHOLD;
    public static GearSwapMode gearSwapMode = DEFAULT_GEAR_SWAP_MODE;
    public static UnflyMode unflyMode = DEFAULT_UNFLY_MODE;
    public static boolean autoVisitor = DEFAULT_AUTO_VISITOR;
    public static boolean autoEquipment = DEFAULT_AUTO_EQUIPMENT;
    public static boolean autoStashManager = DEFAULT_AUTO_STASH_MANAGER;
    public static boolean autoBookCombine = DEFAULT_AUTO_BOOK_COMBINE;
    public static boolean autoGeorgeSell = DEFAULT_AUTO_GEORGE_SELL;
    public static boolean autoBoosterCookie = DEFAULT_AUTO_BOOSTER_COOKIE;
    public static java.util.List<String> boosterCookieItems = new java.util.ArrayList<>(DEFAULT_BOOSTER_COOKIE_ITEMS);
    public static java.util.List<String> customEnchantmentLevels = new java.util.ArrayList<>(
            DEFAULT_CUSTOM_ENCHANTMENT_LEVELS);
    public static int georgeSellThreshold = DEFAULT_GEORGE_SELL_THRESHOLD;
    public static int autoEquipmentFarmingTime = DEFAULT_AUTO_EQUIPMENT_FARMING_TIME;
    public static int rotationTime = DEFAULT_ROTATION_TIME;
    public static boolean aotvToRoof = DEFAULT_AOTV_TO_ROOF;

    // Wardrobe Slots
    public static int wardrobeSlotFarming = DEFAULT_WARDROBE_SLOT_FARMING;
    public static int wardrobeSlotPest = DEFAULT_WARDROBE_SLOT_PEST;
    public static int wardrobeSlotVisitor = DEFAULT_WARDROBE_SLOT_VISITOR;
    public static boolean armorSwapVisitor = DEFAULT_ARMOR_SWAP_VISITOR;

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

    // Restart Script Command (sent to restart farming)
    public static String restartScript = DEFAULT_RESTART_SCRIPT;

    // Garden Warp Delay (ms) - configurable delay after garden warp
    public static int gardenWarpDelay = DEFAULT_GARDEN_WARP_DELAY;

    // Dynamic Rest (Minutes)
    public static int restScriptingTime = DEFAULT_REST_SCRIPTING_TIME;
    public static int restScriptingTimeOffset = DEFAULT_REST_SCRIPTING_TIME_OFFSET;
    public static int restBreakTime = DEFAULT_REST_BREAK_TIME;
    public static int restBreakTimeOffset = DEFAULT_REST_BREAK_TIME_OFFSET;

    public static boolean enablePlotTpRewarp = DEFAULT_ENABLE_PLOT_TP_REWARP;
    public static String plotTpNumber = DEFAULT_PLOT_TP_NUMBER;
    public static String discordWebhookUrl = DEFAULT_DISCORD_WEBHOOK_URL;
    public static int discordStatusUpdateTime = DEFAULT_DISCORD_STATUS_UPDATE_TIME;
    public static boolean sendDiscordStatus = DEFAULT_SEND_DISCORD_STATUS;

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
        data.visitorThreshold = visitorThreshold;
        data.gearSwapMode = gearSwapMode;
        data.unflyMode = unflyMode;
        data.autoVisitor = autoVisitor;
        data.autoEquipment = autoEquipment;
        data.autoStashManager = autoStashManager;
        data.autoBookCombine = autoBookCombine;
        data.autoGeorgeSell = autoGeorgeSell;
        data.autoBoosterCookie = autoBoosterCookie;
        data.boosterCookieItems = new java.util.ArrayList<>(boosterCookieItems);
        data.customEnchantmentLevels = new java.util.ArrayList<>(customEnchantmentLevels);
        data.georgeSellThreshold = georgeSellThreshold;
        data.autoEquipmentFarmingTime = autoEquipmentFarmingTime;
        data.rotationTime = rotationTime;
        data.aotvToRoof = aotvToRoof;

        data.wardrobeSlotFarming = wardrobeSlotFarming;
        data.wardrobeSlotPest = wardrobeSlotPest;
        data.wardrobeSlotVisitor = wardrobeSlotVisitor;
        data.armorSwapVisitor = armorSwapVisitor;
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
        data.plotTpNumber = plotTpNumber;
        data.discordWebhookUrl = discordWebhookUrl;
        data.discordStatusUpdateTime = discordStatusUpdateTime;
        data.sendDiscordStatus = sendDiscordStatus;
        data.rewarpEndX = rewarpEndX;
        data.rewarpEndY = rewarpEndY;
        data.rewarpEndZ = rewarpEndZ;
        data.rewarpEndPosSet = rewarpEndPosSet;

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
                visitorThreshold = data.visitorThreshold;
                gearSwapMode = data.gearSwapMode != null ? data.gearSwapMode : DEFAULT_GEAR_SWAP_MODE;
                unflyMode = data.unflyMode != null ? data.unflyMode : DEFAULT_UNFLY_MODE;
                autoVisitor = data.autoVisitor;
                autoEquipment = data.autoEquipment;
                autoStashManager = data.autoStashManager;
                autoBookCombine = data.autoBookCombine;
                autoGeorgeSell = data.autoGeorgeSell;
                autoBoosterCookie = data.autoBoosterCookie;
                if (data.boosterCookieItems != null) {
                    boosterCookieItems = new java.util.ArrayList<>(data.boosterCookieItems);
                }
                if (data.customEnchantmentLevels != null) {
                    customEnchantmentLevels = new java.util.ArrayList<>(data.customEnchantmentLevels);
                }
                georgeSellThreshold = data.georgeSellThreshold;
                autoEquipmentFarmingTime = data.autoEquipmentFarmingTime;
                rotationTime = data.rotationTime;
                aotvToRoof = data.aotvToRoof;

                wardrobeSlotFarming = data.wardrobeSlotFarming;
                wardrobeSlotPest = data.wardrobeSlotPest;
                wardrobeSlotVisitor = data.wardrobeSlotVisitor;
                armorSwapVisitor = data.armorSwapVisitor;
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
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ConfigData {
        int pestThreshold = DEFAULT_PEST_THRESHOLD;
        int visitorThreshold = DEFAULT_VISITOR_THRESHOLD;
        GearSwapMode gearSwapMode = DEFAULT_GEAR_SWAP_MODE;
        UnflyMode unflyMode = DEFAULT_UNFLY_MODE;
        boolean autoVisitor = DEFAULT_AUTO_VISITOR;
        boolean autoEquipment = DEFAULT_AUTO_EQUIPMENT;
        boolean autoStashManager = DEFAULT_AUTO_STASH_MANAGER;
        boolean autoBookCombine = DEFAULT_AUTO_BOOK_COMBINE;
        boolean autoGeorgeSell = DEFAULT_AUTO_GEORGE_SELL;
        boolean autoBoosterCookie = DEFAULT_AUTO_BOOSTER_COOKIE;
        java.util.List<String> boosterCookieItems = new java.util.ArrayList<>(DEFAULT_BOOSTER_COOKIE_ITEMS);
        java.util.List<String> customEnchantmentLevels = new java.util.ArrayList<>(DEFAULT_CUSTOM_ENCHANTMENT_LEVELS);
        int georgeSellThreshold = DEFAULT_GEORGE_SELL_THRESHOLD;
        int autoEquipmentFarmingTime = DEFAULT_AUTO_EQUIPMENT_FARMING_TIME;
        int rotationTime = DEFAULT_ROTATION_TIME;
        boolean aotvToRoof = DEFAULT_AOTV_TO_ROOF;

        int wardrobeSlotFarming = DEFAULT_WARDROBE_SLOT_FARMING;
        int wardrobeSlotPest = DEFAULT_WARDROBE_SLOT_PEST;
        int wardrobeSlotVisitor = DEFAULT_WARDROBE_SLOT_VISITOR;
        boolean armorSwapVisitor = DEFAULT_ARMOR_SWAP_VISITOR;
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
        String plotTpNumber = DEFAULT_PLOT_TP_NUMBER;
        String discordWebhookUrl = DEFAULT_DISCORD_WEBHOOK_URL;
        int discordStatusUpdateTime = DEFAULT_DISCORD_STATUS_UPDATE_TIME;
        boolean sendDiscordStatus = DEFAULT_SEND_DISCORD_STATUS;
        double rewarpEndX = 0;
        double rewarpEndY = 0;
        double rewarpEndZ = 0;
        boolean rewarpEndPosSet = false;
    }
}
