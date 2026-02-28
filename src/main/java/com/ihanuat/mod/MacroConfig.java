package com.ihanuat.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class MacroConfig {
    public static int pestThreshold = 5;
    public static int visitorThreshold = 5;

    public enum GearSwapMode {
        NONE, WARDROBE, ROD
    }

    public enum UnflyMode {
        SNEAK, DOUBLE_TAP_SPACE
    }

    public static GearSwapMode gearSwapMode = GearSwapMode.NONE;
    public static UnflyMode unflyMode = UnflyMode.DOUBLE_TAP_SPACE;
    public static boolean autoVisitor = true;
    public static boolean autoEquipment = true;
    public static boolean autoStashManager = false;
    public static boolean autoGeorgeSell = false;
    public static boolean autoBoosterCookie = true;
    public static java.util.List<String> boosterCookieItems = new java.util.ArrayList<>(java.util.Arrays.asList(
            "Atmospheric Filter", "Squeaky Toy", "Beady Eyes", "Clipped Wings", "Overclocker",
            "Mantid Claw", "Flowering Bouquet", "Bookworm", "Chirping Stereo", "Firefly",
            "Capsule", "Vinyl"));
    public static int georgeSellThreshold = 3;
    public static int autoEquipmentFarmingTime = 170;
    public static boolean aotvToRoof = false;

    // Wardrobe Slots
    public static int wardrobeSlotFarming = 1;
    public static int wardrobeSlotPest = 2;
    public static int wardrobeSlotVisitor = 3;
    public static boolean armorSwapVisitor = false;

    // GUI Click Delay (ms)
    public static int guiClickDelay = 500;
    public static int equipmentSwapDelay = 500;

    // Restart Time (Minutes before expected server restart to stop macro)
    public static int restartTime = 5;

    // Restart Script Command (sent to restart farming)
    public static String restartScript = ".ez-startscript netherwart:1";

    // Garden Warp Delay (ms) - configurable delay after garden warp
    public static int gardenWarpDelay = 1000;

    // Dynamic Rest (Minutes)
    public static int restScriptingTime = 30;
    public static int restScriptingTimeOffset = 3;
    public static int restBreakTime = 20;
    public static int restBreakTimeOffset = 3;
    public static int rotationTime = 500;

    public static boolean enablePlotTpRewarp = false;
    public static String plotTpNumber = "0";

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
        data.autoGeorgeSell = autoGeorgeSell;
        data.autoBoosterCookie = autoBoosterCookie;
        data.boosterCookieItems = new java.util.ArrayList<>(boosterCookieItems);
        data.georgeSellThreshold = georgeSellThreshold;
        data.rotationTime = rotationTime;
        data.autoEquipmentFarmingTime = autoEquipmentFarmingTime;
        data.aotvToRoof = aotvToRoof;

        data.wardrobeSlotFarming = wardrobeSlotFarming;
        data.wardrobeSlotPest = wardrobeSlotPest;
        data.wardrobeSlotVisitor = wardrobeSlotVisitor;
        data.armorSwapVisitor = armorSwapVisitor;
        data.guiClickDelay = guiClickDelay;
        data.equipmentSwapDelay = equipmentSwapDelay;
        data.restartTime = restartTime;
        data.restartScript = restartScript;
        data.gardenWarpDelay = gardenWarpDelay;

        data.restScriptingTime = restScriptingTime;
        data.restScriptingTimeOffset = restScriptingTimeOffset;
        data.restBreakTime = restBreakTime;
        data.restBreakTimeOffset = restBreakTimeOffset;

        data.enablePlotTpRewarp = enablePlotTpRewarp;
        data.plotTpNumber = plotTpNumber;
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
                gearSwapMode = data.gearSwapMode != null ? data.gearSwapMode : GearSwapMode.NONE;
                unflyMode = data.unflyMode != null ? data.unflyMode : UnflyMode.DOUBLE_TAP_SPACE;
                autoVisitor = data.autoVisitor;
                autoEquipment = data.autoEquipment;
                autoStashManager = data.autoStashManager;
                autoGeorgeSell = data.autoGeorgeSell;
                autoBoosterCookie = data.autoBoosterCookie;
                if (data.boosterCookieItems != null) {
                    boosterCookieItems = new java.util.ArrayList<>(data.boosterCookieItems);
                }
                georgeSellThreshold = data.georgeSellThreshold;
                rotationTime = data.rotationTime;
                autoEquipmentFarmingTime = data.autoEquipmentFarmingTime;
                aotvToRoof = data.aotvToRoof;

                wardrobeSlotFarming = data.wardrobeSlotFarming;
                wardrobeSlotPest = data.wardrobeSlotPest;
                wardrobeSlotVisitor = data.wardrobeSlotVisitor;
                armorSwapVisitor = data.armorSwapVisitor;
                guiClickDelay = data.guiClickDelay;
                equipmentSwapDelay = data.equipmentSwapDelay;
                restartTime = data.restartTime;
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
        int pestThreshold = 5;
        int visitorThreshold = 5;
        GearSwapMode gearSwapMode = GearSwapMode.NONE;
        UnflyMode unflyMode = UnflyMode.DOUBLE_TAP_SPACE;
        boolean autoVisitor = true;
        boolean autoEquipment = true;
        boolean autoStashManager = false;
        boolean autoGeorgeSell = false;
        boolean autoBoosterCookie = true;
        java.util.List<String> boosterCookieItems;
        int georgeSellThreshold = 3;
        int rotationTime = 500;
        int autoEquipmentFarmingTime = 170;
        boolean aotvToRoof = false;

        int wardrobeSlotFarming = 1;
        int wardrobeSlotPest = 2;
        int wardrobeSlotVisitor = 3;
        boolean armorSwapVisitor = false;
        int guiClickDelay = 500;
        int equipmentSwapDelay = 500;
        int restartTime = 5;
        String restartScript = ".ez-startscript netherwart:1";
        int gardenWarpDelay = 1000;

        int restScriptingTime = 30;
        int restScriptingTimeOffset = 3;
        int restBreakTime = 20;
        int restBreakTimeOffset = 3;

        boolean enablePlotTpRewarp = false;
        String plotTpNumber = "0";
        double rewarpEndX = 0;
        double rewarpEndY = 0;
        double rewarpEndZ = 0;
        boolean rewarpEndPosSet = false;
    }
}
