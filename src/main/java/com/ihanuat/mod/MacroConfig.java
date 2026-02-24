package com.ihanuat.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class MacroConfig {
    public static BlockPos startPos = BlockPos.ZERO;
    public static String startPlot = "None";

    public static BlockPos endPos = BlockPos.ZERO;
    public static String endPlot = "None";

    public static int pestThreshold = 1;
    public static int visitorThreshold = 5;

    public enum GearSwapMode {
        NONE, WARDROBE, ROD
    }

    public static GearSwapMode gearSwapMode = GearSwapMode.NONE;
    public static boolean autoVisitor = true;
    public static boolean autoEquipment = true;
    public static boolean autoStashManager = false;

    // Dynamic Rest (Minutes)
    public static int restScriptingTime = 30;
    public static int restScriptingTimeOffset = 3;
    public static int restBreakTime = 20;
    public static int restBreakTimeOffset = 3;
    public static int rotationSpeed = 200;

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("pest_macro_config.json")
            .toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        ConfigData data = new ConfigData();
        data.startPos = startPos;
        data.startPlot = startPlot;
        data.endPos = endPos;
        data.endPlot = endPlot;
        data.pestThreshold = pestThreshold;
        data.visitorThreshold = visitorThreshold;
        data.gearSwapMode = gearSwapMode;
        data.autoVisitor = autoVisitor;
        data.autoEquipment = autoEquipment;
        data.autoStashManager = autoStashManager;
        data.rotationSpeed = rotationSpeed;

        data.restScriptingTime = restScriptingTime;
        data.restScriptingTimeOffset = restScriptingTimeOffset;
        data.restBreakTime = restBreakTime;
        data.restBreakTimeOffset = restBreakTimeOffset;

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
                if (data.startPos != null)
                    startPos = data.startPos;
                if (data.startPlot != null)
                    startPlot = data.startPlot;
                if (data.endPos != null)
                    endPos = data.endPos;
                if (data.endPlot != null)
                    endPlot = data.endPlot;
                pestThreshold = data.pestThreshold;
                visitorThreshold = data.visitorThreshold;
                gearSwapMode = data.gearSwapMode != null ? data.gearSwapMode : GearSwapMode.NONE;
                autoVisitor = data.autoVisitor;
                autoEquipment = data.autoEquipment;
                autoStashManager = data.autoStashManager;
                rotationSpeed = data.rotationSpeed;

                restScriptingTime = data.restScriptingTime;
                restScriptingTimeOffset = data.restScriptingTimeOffset;
                restBreakTime = data.restBreakTime;
                restBreakTimeOffset = data.restBreakTimeOffset;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ConfigData {
        BlockPos startPos = BlockPos.ZERO;
        String startPlot = "None";
        BlockPos endPos = BlockPos.ZERO;
        String endPlot = "None";
        int pestThreshold = 1;
        int visitorThreshold = 5;
        GearSwapMode gearSwapMode = GearSwapMode.NONE;
        boolean autoVisitor = true;
        boolean autoEquipment = true;
        boolean autoStashManager = false;
        int rotationSpeed = 200;

        int restScriptingTime = 30;
        int restScriptingTimeOffset = 3;
        int restBreakTime = 20;
        int restBreakTimeOffset = 3;
    }
}
