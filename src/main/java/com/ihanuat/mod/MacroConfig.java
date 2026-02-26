package com.ihanuat.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class MacroConfig {
    public static int pestThreshold = 1;
    public static int visitorThreshold = 5;

    public enum GearSwapMode {
        NONE, WARDROBE, ROD
    }

    public enum UnflyMode {
        SNEAK, DOUBLE_TAP_SPACE
    }

    public static GearSwapMode gearSwapMode = GearSwapMode.NONE;
    public static UnflyMode unflyMode = UnflyMode.SNEAK;
    public static boolean autoVisitor = true;
    public static boolean autoEquipment = true;
    public static boolean autoStashManager = false;
    public static boolean autoGeorgeSell = false;
    public static int georgeSellThreshold = 10;
    public static int autoEquipmentFarmingTime = 200;

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
        data.pestThreshold = pestThreshold;
        data.visitorThreshold = visitorThreshold;
        data.gearSwapMode = gearSwapMode;
        data.unflyMode = unflyMode;
        data.autoVisitor = autoVisitor;
        data.autoEquipment = autoEquipment;
        data.autoStashManager = autoStashManager;
        data.autoGeorgeSell = autoGeorgeSell;
        data.georgeSellThreshold = georgeSellThreshold;
        data.rotationSpeed = rotationSpeed;
        data.autoEquipmentFarmingTime = autoEquipmentFarmingTime;

        data.wardrobeSlotFarming = wardrobeSlotFarming;
        data.wardrobeSlotPest = wardrobeSlotPest;
        data.wardrobeSlotVisitor = wardrobeSlotVisitor;
        data.armorSwapVisitor = armorSwapVisitor;
        data.guiClickDelay = guiClickDelay;
        data.equipmentSwapDelay = equipmentSwapDelay;
        data.restartTime = restartTime;
        data.restartScript = restartScript;

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
                pestThreshold = data.pestThreshold;
                visitorThreshold = data.visitorThreshold;
                gearSwapMode = data.gearSwapMode != null ? data.gearSwapMode : GearSwapMode.NONE;
                unflyMode = data.unflyMode != null ? data.unflyMode : UnflyMode.SNEAK;
                autoVisitor = data.autoVisitor;
                autoEquipment = data.autoEquipment;
                autoStashManager = data.autoStashManager;
                autoGeorgeSell = data.autoGeorgeSell;
                georgeSellThreshold = data.georgeSellThreshold > 0 ? data.georgeSellThreshold : 10;
                rotationSpeed = data.rotationSpeed;
                autoEquipmentFarmingTime = data.autoEquipmentFarmingTime > 0 ? data.autoEquipmentFarmingTime : 200;

                wardrobeSlotFarming = data.wardrobeSlotFarming > 0 ? data.wardrobeSlotFarming : 1;
                wardrobeSlotPest = data.wardrobeSlotPest > 0 ? data.wardrobeSlotPest : 2;
                wardrobeSlotVisitor = data.wardrobeSlotVisitor > 0 ? data.wardrobeSlotVisitor : 3;
                armorSwapVisitor = data.armorSwapVisitor;
                guiClickDelay = data.guiClickDelay > 0 ? data.guiClickDelay : 500;
                equipmentSwapDelay = data.equipmentSwapDelay > 0 ? data.equipmentSwapDelay : 500;
                restartTime = data.restartTime > 0 ? data.restartTime : 5;
                if (data.restartScript != null && !data.restartScript.isBlank())
                    restartScript = data.restartScript;

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
        int pestThreshold = 1;
        int visitorThreshold = 5;
        GearSwapMode gearSwapMode = GearSwapMode.NONE;
        UnflyMode unflyMode = UnflyMode.SNEAK;
        boolean autoVisitor = true;
        boolean autoEquipment = true;
        boolean autoStashManager = false;
        boolean autoGeorgeSell = false;
        int georgeSellThreshold = 10;
        int rotationSpeed = 200;
        int autoEquipmentFarmingTime = 200;

        int wardrobeSlotFarming = 1;
        int wardrobeSlotPest = 2;
        int wardrobeSlotVisitor = 3;
        boolean armorSwapVisitor = false;
        int guiClickDelay = 500;
        int equipmentSwapDelay = 500;
        int restartTime = 5;
        String restartScript = ".ez-startscript netherwart:1";

        int restScriptingTime = 30;
        int restScriptingTimeOffset = 3;
        int restBreakTime = 20;
        int restBreakTimeOffset = 3;
    }
}
