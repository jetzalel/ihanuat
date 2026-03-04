package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import com.ihanuat.mod.util.ClientUtils;

public class ProfitManager {
    private static final Map<String, Long> sessionCounts = new LinkedHashMap<>();
    private static final Map<String, Long> lifetimeCounts = new LinkedHashMap<>();
    private static final Map<String, Long> prevInventoryCounts = new LinkedHashMap<>();
    private static final Map<String, Double> bazaarPrices = new LinkedHashMap<>();
    private static final Map<String, Long> petLvl1Prices = new java.util.HashMap<>();
    private static final Map<String, Long> petMaxLvlPrices = new java.util.HashMap<>();
    private static long lastCultivatingValue = -1;
    private static String currentFarmedCrop = "Wheat";
    private static long lastBazaarFetchTime = 0;
    private static long lastPurseBalance = -1;

    // Spray cost tracking: quantity is tracked separately from coins
    private static long spraySessionQuantity = 0;
    private static long sprayLifetimeQuantity = 0;
    public static volatile boolean isSprayPhaseActive = false;

    public static void startSprayPhase() {
        if (!isSprayPhaseActive) {
            isSprayPhaseActive = true;
        }
    }

    public static void stopSprayPhase() {
        if (isSprayPhaseActive) {
            isSprayPhaseActive = false;
        }
    }

    private static final java.io.File LIFETIME_FILE = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("pest_macro_profit_lifetime.json").toFile();
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    private static final Set<String> CROPS_SET = Set.of(
            "Wheat", "Enchanted Wheat", "Enchanted Hay Bale",
            "Seeds", "Enchanted Seeds", "Box of Seeds",
            "Potato", "Enchanted Potato", "Enchanted Baked Potato",
            "Carrot", "Enchanted Carrot", "Enchanted Golden Carrot",
            "Melon Slice", "Melon Block", "Enchanted Melon Slice", "Enchanted Melon",
            "Pumpkin", "Enchanted Pumpkin", "Polished Pumpkin",
            "Sugar Cane", "Enchanted Sugar", "Enchanted Sugar Cane",
            "Cactus", "Enchanted Cactus Green", "Enchanted Cactus",
            "Mushroom", "Red Mushroom", "Brown Mushroom",
            "Enchanted Red Mushroom", "Enchanted Brown Mushroom",
            "Enchanted Red Mushroom Block", "Enchanted Brown Mushroom Block",
            "Cocoa Beans", "Enchanted Cocoa Beans", "Enchanted Cookie",
            "Nether Wart", "Enchanted Nether Wart", "Mutant Nether Wart",
            "Sunflower", "Enchanted Sunflower", "Compacted Sunflower",
            "Moonflower", "Enchanted Moonflower", "Compacted Moonflower",
            "Wild Rose", "Enchanted Wild Rose", "Compacted Wild Rose");

    private static final Set<String> PEST_ITEMS_SET = Set.of(
            "Beady Eyes", "Chirping Stereo", "Sunder VI Book", "Clipped Wings",
            "Bookworm's Favorite Book", "Atmospheric Filter", "Wriggling Larva",
            "Pesterminator I Book", "Squeaky Toy", "Squeaky Mousemat",
            "Fire in a Bottle", "Vermin Vaporizer Chip", "Mantid Claw",
            "Overclocker 3000", "Vinyl",
            "Dung", "Honey Jar", "Plant Matter", "Tasty Cheese", "Compost", "Jelly",
            "Pest Shard");

    private static final Set<String> PETS_SET = Set.of("Epic Slug", "Legendary Slug", "Rat");

    private static final Set<String> MISC_DROPS_SET = Set.of("Cropie", "Squash", "Fermento", "Helianthus",
            "Tool EXP Capsule", "Pet XP", "Purse");

    private static final Set<String> BASE_CROPS = Set.of(
            "Wheat", "Potato", "Carrot", "Melon Slice", "Pumpkin",
            "Sugar Cane", "Cactus", "Nether Wart", "Cocoa Beans",
            "Red Mushroom", "Brown Mushroom",
            "Sunflower", "Moonflower", "Wild Rose", "Seeds");

    private static final Map<String, Double> TRACKED_ITEMS = Map.ofEntries(
            // Crops
            Map.entry("Wheat", 6.0), Map.entry("Enchanted Wheat", 960.0), Map.entry("Enchanted Hay Bale", 153600.0),
            Map.entry("Seeds", 3.0), Map.entry("Enchanted Seeds", 480.0), Map.entry("Box of Seeds", 76800.0),
            Map.entry("Potato", 3.0), Map.entry("Enchanted Potato", 480.0),
            Map.entry("Enchanted Baked Potato", 76800.0),
            Map.entry("Carrot", 3.0), Map.entry("Enchanted Carrot", 480.0),
            Map.entry("Enchanted Golden Carrot", 76800.0),
            Map.entry("Melon Slice", 2.0), Map.entry("Melon Block", 18.0), Map.entry("Enchanted Melon Slice", 320.0),
            Map.entry("Enchanted Melon", 51200.0),
            Map.entry("Pumpkin", 10.0), Map.entry("Enchanted Pumpkin", 1600.0), Map.entry("Polished Pumpkin", 256000.0),
            Map.entry("Sugar Cane", 4.0), Map.entry("Enchanted Sugar", 640.0),
            Map.entry("Enchanted Sugar Cane", 102400.0),
            Map.entry("Cactus", 4.0), Map.entry("Enchanted Cactus Green", 640.0),
            Map.entry("Enchanted Cactus", 102400.0),
            Map.entry("Mushroom", 10.0), Map.entry("Red Mushroom", 10.0), Map.entry("Brown Mushroom", 10.0),
            Map.entry("Enchanted Red Mushroom", 1600.0), Map.entry("Enchanted Brown Mushroom", 1600.0),
            Map.entry("Enchanted Red Mushroom Block", 256000.0), Map.entry("Enchanted Brown Mushroom Block", 256000.0),
            Map.entry("Cocoa Beans", 3.0), Map.entry("Enchanted Cocoa Beans", 480.0),
            Map.entry("Enchanted Cookie", 76800.0),
            Map.entry("Nether Wart", 4.0), Map.entry("Enchanted Nether Wart", 640.0),
            Map.entry("Mutant Nether Wart", 102400.0),
            Map.entry("Sunflower", 4.0), Map.entry("Enchanted Sunflower", 640.0),
            Map.entry("Compacted Sunflower", 102400.0),
            Map.entry("Moonflower", 4.0), Map.entry("Enchanted Moonflower", 640.0),
            Map.entry("Compacted Moonflower", 102400.0),
            Map.entry("Wild Rose", 4.0), Map.entry("Enchanted Wild Rose", 640.0),
            Map.entry("Compacted Wild Rose", 102400.0),
            // Pest Items
            Map.entry("Beady Eyes", 25000.0), Map.entry("Chirping Stereo", 100000.0), Map.entry("Sunder VI Book", 0.0),
            Map.entry("Clipped Wings", 25000.0), Map.entry("Bookworm's Favorite Book", 10000.0),
            Map.entry("Atmospheric Filter", 100000.0),
            Map.entry("Wriggling Larva", 250000.0), Map.entry("Pesterminator I Book", 0.0),
            Map.entry("Squeaky Toy", 10000.0),
            Map.entry("Squeaky Mousemat", 1000000.0), Map.entry("Fire in a Bottle", 100000.0),
            Map.entry("Vermin Vaporizer Chip", 0.0),
            Map.entry("Mantid Claw", 75000.0),
            Map.entry("Overclocker 3000", 250000.0),
            Map.entry("Vinyl", 50000.0),
            Map.entry("Dung", 0.0), Map.entry("Honey Jar", 0.0), Map.entry("Plant Matter", 0.0),
            Map.entry("Tasty Cheese", 0.0), Map.entry("Compost", 0.0), Map.entry("Jelly", 0.0),
            // Pets
            Map.entry("Epic Slug", 500000.0), Map.entry("Legendary Slug", 5000000.0), Map.entry("Rat", 5000.0),
            // Misc Drops
            Map.entry("Cropie", 25000.0), Map.entry("Squash", 75000.0), Map.entry("Fermento", 250000.0),
            Map.entry("Helianthus", 0.0), Map.entry("Tool EXP Capsule", 100000.0),
            // Pet XP (price per XP point, will be fetched)
            Map.entry("Pet XP", 0.0),
            Map.entry("Pest Shard", 0.0),
            // AH Items
            Map.entry("Biofuel", 0.0), Map.entry("Farming Exp Boost", 0.0),
            Map.entry("Harvest Harbinger V Potion", 0.0),
            Map.entry("Velvet Top Hat", 0.0), Map.entry("Cashmere Hat", 0.0), Map.entry("Satin Trousers", 0.0),
            Map.entry("Oxford Shoes", 0.0), Map.entry("Space Helmet", 0.0), Map.entry("Wild Strawberry Dye", 0.0),
            Map.entry("Copper Dye", 0.0), Map.entry("Clouds Rune I", 0.0), Map.entry("Fire Spiral Rune I", 0.0),
            Map.entry("Gem Rune I", 0.0), Map.entry("Golden Rune I", 0.0), Map.entry("Hearts Rune I", 0.0),
            Map.entry("Hot Rune I", 0.0), Map.entry("Lava Rune I", 0.0), Map.entry("Lightning Rune I", 0.0),
            Map.entry("Magical Rune I", 0.0), Map.entry("Music Rune I", 0.0), Map.entry("Rainbow Rune I", 0.0),
            Map.entry("Redstone Rune I", 0.0), Map.entry("Smokey Rune I", 0.0), Map.entry("Snow Rune I", 0.0),
            Map.entry("Sparkling Rune I", 0.0), Map.entry("Wake Rune I", 0.0), Map.entry("White Spiral Rune I", 0.0),
            Map.entry("Zap Rune I", 0.0),
            // Visitor / Rare Drops
            Map.entry("Overgrown Grass", 0.0), Map.entry("Flowering Bouqet", 0.0), Map.entry("Green Bandana", 0.0),
            Map.entry("Hypercharge Chip", 0.0), Map.entry("Quickdraw Chip", 0.0), Map.entry("Superboom TNT", 0.0),
            Map.entry("Green Candy", 0.0), Map.entry("Purple Candy", 0.0), Map.entry("Ice Essence", 0.0),
            Map.entry("Diamond Essence", 0.0), Map.entry("Gold Essence", 0.0), Map.entry("Jacob's Ticket", 0.0),
            Map.entry("Turbo-Cacti I Book", 0.0), Map.entry("Turbo-Cane I Book", 0.0),
            Map.entry("Turbo-Carrot I Book", 0.0),
            Map.entry("Turbo-Cocoa I Book", 0.0), Map.entry("Turbo-Melon I Book", 0.0),
            Map.entry("Turbo-Moonflower I Book", 0.0),
            Map.entry("Turbo-Mushrooms I Book", 0.0), Map.entry("Turbo-Potato I Book", 0.0),
            Map.entry("Turbo-Pumpkin I Book", 0.0),
            Map.entry("Turbo-Rose I Book", 0.0), Map.entry("Turbo-Sunflower I Book", 0.0),
            Map.entry("Turbo-Warts I Book", 0.0),
            Map.entry("Turbo-Wheat I Book", 0.0), Map.entry("Cultivating I Book", 0.0),
            Map.entry("Delicate V Book", 0.0),
            Map.entry("Replenish I Book", 0.0), Map.entry("Dedication IV Book", 0.0), Map.entry("Jungle Key", 0.0),
            Map.entry("Pet Cake", 0.0), Map.entry("Fine Flour", 0.0), Map.entry("Arachne Fragment", 0.0),
            Map.entry("Purse", 1.0));

    private static final Map<String, String> BAZAAR_MAPPING = Map.ofEntries(
            Map.entry("Sunder VI Book", "ENCHANTMENT_SUNDER_6"),
            Map.entry("Pesterminator I Book", "ENCHANTMENT_PESTERMINATOR_1"),
            Map.entry("Dung", "DUNG"),
            Map.entry("Honey Jar", "HONEY_JAR"),
            Map.entry("Plant Matter", "PLANT_MATTER"),
            Map.entry("Tasty Cheese", "CHEESE_FUEL"),
            Map.entry("Compost", "COMPOST"),
            Map.entry("Jelly", "JELLY"),
            Map.entry("Helianthus", "HELIANTHUS"),
            Map.entry("Vermin Vaporizer Chip", "VERMIN_VAPORIZER_GARDEN_CHIP"),
            Map.entry("ENCHANTMENT_GREEN_THUMB_1", "ENCHANTMENT_GREEN_THUMB_1"),
            Map.entry("Pest Shard", "SHARD_PEST"),
            // Visitor / Rare Drops Mappings
            Map.entry("Overgrown Grass", "OVERGROWN_GRASS"),
            Map.entry("Flowering Bouqet", "FLOWERING_BOUQUET"),
            Map.entry("Green Bandana", "GREEN_BANDANA"),
            Map.entry("Hypercharge Chip", "HYPERCHARGE_GARDEN_CHIP"),
            Map.entry("Quickdraw Chip", "QUICKDRAW_GARDEN_CHIP"),
            Map.entry("Superboom TNT", "SUPERBOOM_TNT"),
            Map.entry("Green Candy", "GREEN_CANDY"),
            Map.entry("Purple Candy", "PURPLE_CANDY"),
            Map.entry("Ice Essence", "ESSENCE_ICE"),
            Map.entry("Diamond Essence", "ESSENCE_DIAMOND"),
            Map.entry("Gold Essence", "ESSENCE_GOLD"),
            Map.entry("Jacob's Ticket", "JACOBS_TICKET"),
            Map.entry("Turbo-Cacti I Book", "ENCHANTMENT_TURBO_CACTUS_1"),
            Map.entry("Turbo-Cane I Book", "ENCHANTMENT_TURBO_CANE_1"),
            Map.entry("Turbo-Carrot I Book", "ENCHANTMENT_TURBO_CARROT_1"),
            Map.entry("Turbo-Cocoa I Book", "ENCHANTMENT_TURBO_COCO_1"),
            Map.entry("Turbo-Melon I Book", "ENCHANTMENT_TURBO_MELON_1"),
            Map.entry("Turbo-Moonflower I Book", "ENCHANTMENT_TURBO_MOONFLOWER_1"),
            Map.entry("Turbo-Mushrooms I Book", "ENCHANTMENT_TURBO_MUSHROOMS_1"),
            Map.entry("Turbo-Potato I Book", "ENCHANTMENT_TURBO_POTATO_1"),
            Map.entry("Turbo-Pumpkin I Book", "ENCHANTMENT_TURBO_PUMPKIN_1"),
            Map.entry("Turbo-Rose I Book", "ENCHANTMENT_TURBO_ROSE_1"),
            Map.entry("Turbo-Sunflower I Book", "ENCHANTMENT_TURBO_SUNFLOWER_1"),
            Map.entry("Turbo-Warts I Book", "ENCHANTMENT_TURBO_WARTS_1"),
            Map.entry("Turbo-Wheat I Book", "ENCHANTMENT_TURBO_WHEAT_1"),
            Map.entry("Cultivating I Book", "ENCHANTMENT_CULTIVATING_1"),
            Map.entry("Delicate V Book", "ENCHANTMENT_DELICATE_5"),
            Map.entry("Replenish I Book", "ENCHANTMENT_REPLENISH_1"),
            Map.entry("Dedication IV Book", "ENCHANTMENT_DEDICATION_4"),
            Map.entry("Jungle Key", "JUNGLE_KEY"),
            Map.entry("Pet Cake", "PET_CAKE"),
            Map.entry("Fine Flour", "FINE_FLOUR"),
            Map.entry("Arachne Fragment", "ARACHNE_FRAGMENT"),
            // AH Items Mappings
            Map.entry("Biofuel", "BIOFUEL"),
            Map.entry("Farming Exp Boost", "PET_ITEM_FARMING_SKILL_BOOST_UNCOMMON"),
            Map.entry("Harvest Harbinger V Potion", "POTION_harvest_harbinger"),
            Map.entry("Velvet Top Hat", "VELVET_TOP_HAT"),
            Map.entry("Cashmere Hat", "CASHMERE_JACKET"),
            Map.entry("Satin Trousers", "SATIN_TROUSERS"),
            Map.entry("Oxford Shoes", "OXFORD_SHOES"),
            Map.entry("Space Helmet", "DCTR_SPACE_HELM"),
            Map.entry("Wild Strawberry Dye", "DYE_WILD_STRAWBERRY"),
            Map.entry("Copper Dye", "DYE_COPPER"),
            Map.entry("Clouds Rune I", "RUNE_CLOUDS"),
            Map.entry("Fire Spiral Rune I", "RUNE_FIRE_SPIRAL"),
            Map.entry("Gem Rune I", "RUNE_GEM"),
            Map.entry("Golden Rune I", "RUNE_GOLDEN"),
            Map.entry("Hearts Rune I", "RUNE_HEARTS"),
            Map.entry("Hot Rune I", "RUNE_HOT"),
            Map.entry("Lava Rune I", "RUNE_LAVA"),
            Map.entry("Lightning Rune I", "RUNE_LIGHTNING"),
            Map.entry("Magical Rune I", "RUNE_MAGIC"),
            Map.entry("Music Rune I", "RUNE_MUSIC"),
            Map.entry("Rainbow Rune I", "RUNE_RAINBOW"),
            Map.entry("Redstone Rune I", "RUNE_REDSTONE"),
            Map.entry("Smokey Rune I", "RUNE_SMOKEY"),
            Map.entry("Snow Rune I", "RUNE_SNOW"),
            Map.entry("Sparkling Rune I", "RUNE_SPARKLING"),
            Map.entry("Wake Rune I", "RUNE_WAKE"),
            Map.entry("White Spiral Rune I", "RUNE_WHITE_SPIRAL"),
            Map.entry("Zap Rune I", "RUNE_ZAP"));

    private static final Pattern PEST_PATTERN = Pattern.compile("received\\s+(\\d+)x\\s+(.+?)\\s+for\\s+killing",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RARE_DROP_PATTERN = Pattern.compile(
            "(?:UNCOMMON|RARE|CRAZY RARE|PRAY TO RNGESUS) DROP!\\s+(?:You dropped\\s+)?(?:an?\\s+)?(?:(\\d+)x\\s+)?(.+?)(?=\\s*(?:§[0-9a-fk-or])*\\s*[\\(!]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PET_DROP_PATTERN = Pattern.compile(
            "PET DROP!\\s+.*?§([0-9a-f])(?:§[0-9a-fk-or])*\\s*(?:(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC)\\s+(?:§[0-9a-fk-or])*)?(.+?)(?=\\s*(?:§[0-9a-fk-or])*\\s*[\\(!]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RARE_CROP_PATTERN = Pattern.compile(
            "RARE CROP!\\s+(.+?)(?=\\s*(?:§[0-9a-fk-or])*\\s*[\\(!]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OVERFLOW_DROP_PATTERN = Pattern.compile(
            "OVERFLOW!\\s+.*?\\s+has\\s+just\\s+dropped\\s+(?:an?\\s+)?(?:(\\d+)x\\s+)?(.+?)(?=\\s*\\(!|!|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PEST_SHARD_PATTERN = Pattern.compile(
            "charmed\\s+a\\s+Pest\\s+and\\s+captured\\s+(?:its\\s+Shard|(\\d+)\\s+Shards)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");

    private static final Pattern BAZAAR_BUY_PATTERN = Pattern.compile(
            "\\[Bazaar\\] Bought (\\d+)x (.+?) for [\\d,]+ coins!",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPRAY_PATTERN = Pattern.compile(
            "SPRAYONATOR! You sprayed Plot - \\d+ with (.+?)(?:!|$)",
            Pattern.CASE_INSENSITIVE);
    private static long lastBazaarSprayBuyTime = 0;
    private static boolean isTrackingVisitorRewards = false;
    private static boolean copperSeenInRewards = false;

    public static void handleChatMessage(Component component) {
        String text = toLegacyText(component);
        // PET DROP needs raw text to detect color-coded rarity
        Matcher petMatcher = PET_DROP_PATTERN.matcher(text);
        if (petMatcher.find()) {
            String colorCode = petMatcher.group(1).toLowerCase(); // 5 = Epic, 6 = Legendary, 9 = Rare
            String petName = petMatcher.group(2).trim();
            String finalName = petName;

            if (petName.equalsIgnoreCase("Slug")) {
                if (colorCode.equals("5") || colorCode.equals("d")) {
                    finalName = "Epic Slug";
                } else if (colorCode.equals("6")) {
                    finalName = "Legendary Slug";
                }
            } else if (petName.equalsIgnoreCase("Rat")) {
                finalName = "Rat";
            }
            addDrop(finalName, 1);
            return;
        }

        Matcher cropMatcher = RARE_CROP_PATTERN.matcher(text);
        if (cropMatcher.find()) {
            addDrop(cropMatcher.group(1).trim(), 1);
            return;
        }

        // Plain text processing for standard drops
        String plainText = STRIP_COLOR_PATTERN.matcher(text).replaceAll("").trim();

        Matcher overflowMatcher = OVERFLOW_DROP_PATTERN.matcher(plainText);
        if (overflowMatcher.find()) {
            try {
                String countStr = overflowMatcher.group(1);
                int count = (countStr != null) ? Integer.parseInt(countStr) : 1;
                String itemName = overflowMatcher.group(2).trim();
                addDrop(itemName, count);
                return;
            } catch (Exception ignored) {
            }
        }

        Matcher pestMatcher = PEST_PATTERN.matcher(plainText);
        if (pestMatcher.find()) {
            try {
                int count = Integer.parseInt(pestMatcher.group(1));
                String itemName = pestMatcher.group(2).trim();
                addDrop(itemName, count);
                return;
            } catch (Exception ignored) {
            }
        }

        Matcher rareMatcher = RARE_DROP_PATTERN.matcher(plainText);
        if (rareMatcher.find()) {
            try {
                String countStr = rareMatcher.group(1);
                int count = (countStr != null) ? Integer.parseInt(countStr) : 1;
                String itemName = rareMatcher.group(2).trim();
                addDrop(itemName, count);
            } catch (Exception ignored) {
            }
        }

        Matcher shardMatcher = PEST_SHARD_PATTERN.matcher(plainText);
        if (shardMatcher.find()) {
            try {
                String countStr = shardMatcher.group(1);
                int count = (countStr != null) ? Integer.parseInt(countStr) : 1;
                addDrop("Pest Shard", count);
            } catch (Exception ignored) {
            }
        }

        Matcher bazaarMatcher = BAZAAR_BUY_PATTERN.matcher(plainText);
        if (bazaarMatcher.find()) {
            if (MacroStateManager.getCurrentState() == MacroState.State.VISITING) {
                ClientUtils.sendDebugMessage(Minecraft.getInstance(), "Bazaar buy ignored (Visiting state)");
                return;
            }
            try {
                int count = Integer.parseInt(bazaarMatcher.group(1));
                String itemName = bazaarMatcher.group(2).trim();
                ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                        "Bazaar buy detected: " + count + "x " + itemName);
                addDrop(itemName, -count);
                lastBazaarSprayBuyTime = System.currentTimeMillis();
            } catch (Exception ignored) {
            }
        }

        // ── Visitor Rewards Tracking ──
        if (plainText.equalsIgnoreCase("REWARDS")) {
            isTrackingVisitorRewards = true;
            copperSeenInRewards = false;
            return;
        }

        if (isTrackingVisitorRewards) {
            if (plainText.isEmpty()) {
                isTrackingVisitorRewards = false;
                return;
            }

            if (plainText.contains("Farming XP") || plainText.contains("Garden Experience")) {
                return;
            }

            if (plainText.contains("Copper")) {
                copperSeenInRewards = true;
            }

            if (copperSeenInRewards) {
                // Parse reward line
                Matcher m = Pattern.compile("^\\+?([\\d,.]+)[xX]?\\s+(.+)").matcher(plainText);
                if (m.find()) {
                    String item = m.group(2).trim();
                    String countStr = m.group(1).replace(",", "");
                    long count = 1;
                    try {
                        if (countStr.toLowerCase().endsWith("k")) {
                            count = (long) (Double.parseDouble(countStr.substring(0, countStr.length() - 1)) * 1000);
                        } else {
                            count = Long.parseLong(countStr);
                        }
                    } catch (Exception ignored) {
                    }
                    addVisitorGain(item, count);
                } else {
                    addVisitorGain(plainText, 1);
                }
            }
            return;
        }

        Matcher sprayMatcher = SPRAY_PATTERN.matcher(plainText);
        if (sprayMatcher.find()) {
            String baitName = sprayMatcher.group(1).trim();
            long now = System.currentTimeMillis();
            if (now - lastBazaarSprayBuyTime < 15000) {
                ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                        "Sprayonator use ignored due to recent Bazaar buy.");
            } else {
                ClientUtils.sendDebugMessage(Minecraft.getInstance(), "Sprayonator use detected (" + baitName + ").");
                addDrop(baitName, -1);
            }
        }
    }

    private static String toLegacyText(Component component) {
        StringBuilder sb = new StringBuilder();
        component.visit((style, part) -> {
            net.minecraft.network.chat.TextColor color = style.getColor();
            if (color != null) {
                int rgb = color.getValue();
                String code = "f";
                if (rgb == 16755200)
                    code = "6"; // Gold
                else if (rgb == 11141290)
                    code = "5"; // Dark Purple
                else if (rgb == 5636095)
                    code = "b"; // Aqua
                else if (rgb == 16733695)
                    code = "d"; // Light Purple
                else if (rgb == 5592405)
                    code = "8"; // Dark Gray
                else if (rgb == 11184810)
                    code = "7"; // Gray
                else if (rgb == 5592575)
                    code = "9"; // Blue
                else if (rgb == 5635925)
                    code = "a"; // Green
                else if (rgb == 16711680)
                    code = "c"; // Red
                else if (rgb == 16777045)
                    code = "e"; // Yellow
                sb.append("§").append(code);
            }
            if (style.isBold())
                sb.append("§l");
            if (style.isItalic())
                sb.append("§o");
            sb.append(part);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    private static void addDrop(String itemName, long count) {
        // Handle items with suffix counts like "Mutant Nether Wart X9"
        String processedName = STRIP_COLOR_PATTERN.matcher(itemName).replaceAll("").trim();
        long multiplier = 1;

        Matcher suffixMatcher = Pattern.compile("\\s+[xX](\\d+)$").matcher(processedName);
        if (suffixMatcher.find()) {
            try {
                multiplier = Long.parseLong(suffixMatcher.group(1));
                processedName = processedName.substring(0, suffixMatcher.start()).trim();
            } catch (Exception ignored) {
            }
        }

        long finalCount = count * multiplier;

        // Group all Vinyl items together
        if (processedName.toLowerCase().endsWith("vinyl")) {
            processedName = "Vinyl";
        }

        // Find the tracked item name that matches (case-insensitive) for pretty
        // formatting
        String matchedName = null;
        for (String tracked : TRACKED_ITEMS.keySet()) {
            if (tracked.equalsIgnoreCase(processedName)) {
                matchedName = tracked;
                break;
            }
        }

        if (matchedName == null) {
            if (processedName.toLowerCase().startsWith("pet xp (")) {
                matchedName = processedName; // Preserve casing for Pet XP
            } else {
                matchedName = normalizeName(processedName);
            }
        }

        // Only add to session counts if macro is running
        if (com.ihanuat.mod.MacroStateManager.isMacroRunning()) {
            sessionCounts.put(matchedName, sessionCounts.getOrDefault(matchedName, 0L) + finalCount);
        }

        lifetimeCounts.put(matchedName, lifetimeCounts.getOrDefault(matchedName, 0L) + finalCount);
        saveLifetime();
    }

    public static void addVisitorGain(String itemName, long count) {
        String cleanName = STRIP_COLOR_PATTERN.matcher(itemName).replaceAll("").replace("+", "").trim();
        long multiplier = 1;
        Matcher m = Pattern.compile("\\s+[xX](\\d+)$").matcher(cleanName);
        if (m.find()) {
            try {
                multiplier = Long.parseLong(m.group(1));
                cleanName = cleanName.substring(0, m.start()).trim();
            } catch (Exception ignored) {
            }
        }

        String key = cleanName.startsWith("[Visitor] ") ? cleanName : "[Visitor] " + cleanName;
        long totalCount = count * multiplier;

        if (com.ihanuat.mod.MacroStateManager.isMacroRunning()) {
            sessionCounts.put(key, sessionCounts.getOrDefault(key, 0L) + totalCount);
        }
        lifetimeCounts.put(key, lifetimeCounts.getOrDefault(key, 0L) + totalCount);
        saveLifetime();
    }

    public static void addVisitorCost(long coinsSpent) {
        String key = "[Visitor] Visitor Cost";
        sessionCounts.put(key, sessionCounts.getOrDefault(key, 0L) - coinsSpent);
        lifetimeCounts.put(key, lifetimeCounts.getOrDefault(key, 0L) - coinsSpent);
        saveLifetime();
    }

    public static void addSprayCost(int quantity, long coins) {
        String key = "[Spray] Sprayonator";
        spraySessionQuantity += quantity;
        sprayLifetimeQuantity += quantity;
        if (com.ihanuat.mod.MacroStateManager.isMacroRunning()) {
            sessionCounts.put(key, sessionCounts.getOrDefault(key, 0L) - coins);
        }
        lifetimeCounts.put(key, lifetimeCounts.getOrDefault(key, 0L) - coins);
        saveLifetime();
    }

    public static long getSprayQuantity(boolean lifetime) {
        return lifetime ? sprayLifetimeQuantity : spraySessionQuantity;
    }

    public static String getCategorizedName(String name) {
        if (name.equals("[Spray] Sprayonator")) {
            return "§c§l[COST] §fSprayonator";
        }
        if (name.equals("[Visitor] Visitor Cost")) {
            return "§c§l[COST] §fVisitor Cost";
        }
        if (name.startsWith("[Visitor] ")) {
            return "§5§l[VISITOR] §f" + name.substring(10);
        }
        String color = "§7";
        String tag = "OTHER";

        if (CROPS_SET.contains(name)) {
            color = "§a";
            tag = "CROP";
        } else if (PEST_ITEMS_SET.contains(name)) {
            color = "§d";
            tag = "PEST";
        } else if (PETS_SET.contains(name)) {
            color = "§6";
            tag = "PET";
        } else if (MISC_DROPS_SET.contains(name) || name.toLowerCase().startsWith("pet xp (")) {
            color = "§b";
            tag = "MISC";
        }

        String displayName = name.replace("Enchanted ", "Ench. ");
        if (name.toLowerCase().startsWith("pet xp (")) {
            displayName = name.substring(8, name.length() - 1) + " XP";
        }
        return color + "§l[" + tag + "] §f" + displayName;
    }

    public static String getCompactCategoryLabel(String category) {
        switch (category) {
            case "Crops":
                return "§a§l[CROP]";
            case "Pest Items":
                return "§d§l[PEST]";
            case "Pets":
                return "§6§l[PET]";
            case "Misc Drops":
                return "§b§l[MISC]";
            case "Visitor":
                return "§5§l[VISITOR]";
            case "Costs":
                return "§c§l[COST]";
            default:
                return "§7§l[OTHER]";
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.isEmpty())
            return "Unknown Item";

        StringBuilder b = new StringBuilder();
        boolean nextUpper = true;
        for (char c : name.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                nextUpper = true;
                b.append(c);
            } else if (nextUpper) {
                b.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                b.append(Character.toLowerCase(c));
            }
        }
        return b.toString();
    }

    public static Map<String, Long> getActiveDrops() {
        return getActiveDrops(false);
    }

    public static Map<String, Long> getActiveDrops(boolean lifetime) {
        Map<String, Long> counts = lifetime ? lifetimeCounts : sessionCounts;

        // Sort by total profit (count * price) descending
        return counts.entrySet().stream()
                .sorted((e1, e2) -> {
                    double p1 = getItemPrice(e1.getKey()) * e1.getValue();
                    double p2 = getItemPrice(e2.getKey()) * e2.getValue();
                    return Double.compare(p2, p1);
                })
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        LinkedHashMap::new));
    }

    public static Map<String, Long> getCompactDrops() {
        return getCompactDrops(false);
    }

    public static Map<String, Long> getCompactDrops(boolean lifetime) {
        Map<String, Long> compact = new LinkedHashMap<>();
        compact.put("Crops", 0L);
        compact.put("Pest Items", 0L);
        compact.put("Pets", 0L);
        compact.put("Misc Drops", 0L);
        compact.put("Visitor", 0L);
        compact.put("Costs", 0L);
        compact.put("Others", 0L);

        Map<String, Long> targetCounts = lifetime ? lifetimeCounts : sessionCounts;
        for (Map.Entry<String, Long> entry : targetCounts.entrySet()) {
            String name = entry.getKey();
            long count = entry.getValue();
            double price = getItemPrice(name);
            double profit = price * count;

            if (CROPS_SET.contains(name)) {
                compact.put("Crops", compact.get("Crops") + (long) profit);
            } else if (PEST_ITEMS_SET.contains(name)) {
                compact.put("Pest Items", compact.get("Pest Items") + (long) profit);
            } else if (PETS_SET.contains(name)) {
                compact.put("Pets", compact.get("Pets") + (long) profit);
            } else if (MISC_DROPS_SET.contains(name)) {
                compact.put("Misc Drops", compact.get("Misc Drops") + (long) profit);
            } else if (name.equals("[Visitor] Visitor Cost") || name.equals("[Spray] Sprayonator")) {
                compact.put("Costs", compact.get("Costs") + (long) profit);
            } else if (name.startsWith("[Visitor] ")) {
                compact.put("Visitor", compact.get("Visitor") + (long) profit);
            } else if (profit < 0) {
                compact.put("Costs", compact.get("Costs") + (long) profit);
            } else {
                compact.put("Others", compact.get("Others") + (long) profit);
            }
        }

        // Sort compact map by value descending
        return compact.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        LinkedHashMap::new));
    }

    public static void reset() {
        sessionCounts.clear();
        PetXpTracker.reset();
        lastBazaarSprayBuyTime = 0;
        lastPurseBalance = -1;
    }

    public static void resetLifetime() {
        lifetimeCounts.clear();
        saveLifetime();
    }

    public static long getTotalProfit() {
        return getTotalProfit(false);
    }

    public static long getTotalProfit(boolean lifetime) {
        double total = 0;
        Map<String, Long> targetCounts = lifetime ? lifetimeCounts : sessionCounts;
        for (Map.Entry<String, Long> entry : targetCounts.entrySet()) {
            double price = getItemPrice(entry.getKey());
            total += price * entry.getValue();
        }
        return (long) total;
    }

    private static void saveLifetime() {
        try (java.io.FileWriter writer = new java.io.FileWriter(LIFETIME_FILE)) {
            GSON.toJson(lifetimeCounts, writer);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadLifetime() {
        if (!LIFETIME_FILE.exists())
            return;
        try (java.io.FileReader reader = new java.io.FileReader(LIFETIME_FILE)) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, Long>>() {
            }.getType();
            Map<String, Long> data = GSON.fromJson(reader, type);
            if (data != null) {
                lifetimeCounts.clear();
                lifetimeCounts.putAll(data);
            }
        } catch (Exception e) {
            System.err.println("[Ihanuat] Failed to load lifetime profit data: " + e.getMessage());
        }
    }

    public static double getItemPrice(String itemName) {
        if (itemName.startsWith("[Visitor] ")) {
            String realName = itemName.substring(10);
            if ("Visitor Cost".equals(realName))
                return 1.0;
            if ("Copper".equals(realName)) {
                double greenThumbPrice = bazaarPrices.getOrDefault("ENCHANTMENT_GREEN_THUMB_1", 0.0);
                if (greenThumbPrice <= 0) {
                    greenThumbPrice = TRACKED_ITEMS.getOrDefault("ENCHANTMENT_GREEN_THUMB_1", 0.0);
                }
                if (greenThumbPrice > 0) {
                    return greenThumbPrice / 1500.0;
                }
                return 0.0;
            }
            return getItemPrice(realName); // Recursive call for the actual item price
        }

        // Visitor cost: count IS the coin amount, so price = 1.0
        if ("[Spray] Sprayonator".equals(itemName) || "Purse".equals(itemName)) {
            return 1.0;
        }
        double price = TRACKED_ITEMS.getOrDefault(itemName, 0.0);
        if (price == 0.0) {
            price = bazaarPrices.getOrDefault(itemName, 0.0);
        }
        return price;
    }

    public static boolean isPredefinedTrackedItem(String itemName) {
        if (itemName == null)
            return false;
        if (itemName.toLowerCase().startsWith("pet xp ("))
            return true;
        for (String tracked : TRACKED_ITEMS.keySet()) {
            if (tracked.equalsIgnoreCase(itemName)) {
                return true;
            }
        }
        return false;
    }

    public static void update(net.minecraft.client.Minecraft client) {
        if (client.player == null)
            return;

        // 1. Detect which crop increased in inventory
        String detectedCrop = null;
        long maxIncrease = 0;

        Map<String, Long> currentCounts = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty())
                continue;
            String name = stack.getHoverName().getString().replaceAll("\u00A7[0-9a-fk-or]", "").trim();
            if (BASE_CROPS.contains(name)) {
                currentCounts.put(name, currentCounts.getOrDefault(name, 0L) + stack.getCount());
            }
        }

        for (Map.Entry<String, Long> entry : currentCounts.entrySet()) {
            String name = entry.getKey();
            long count = entry.getValue();
            long prev = prevInventoryCounts.getOrDefault(name, 0L);
            if (count > prev) {
                long diff = count - prev;
                if (diff > maxIncrease) {
                    maxIncrease = diff;
                    detectedCrop = name;
                }
            }
        }
        prevInventoryCounts.clear();
        prevInventoryCounts.putAll(currentCounts);

        if (detectedCrop != null) {
            currentFarmedCrop = detectedCrop;
        }

        // 2. Track Cultivating counter on held item
        net.minecraft.world.item.ItemStack held = client.player.getMainHandItem();
        if (held != null && !held.isEmpty()) {
            long newValue = -1;

            // Hypixel 1.21 stores this in custom_data
            net.minecraft.world.item.component.CustomData custom = held.get(DataComponents.CUSTOM_DATA);
            if (custom != null) {
                net.minecraft.nbt.CompoundTag tag = custom.copyTag();
                if (tag.contains("farmed_cultivating")) {
                    newValue = tag.getLong("farmed_cultivating").get();
                }
            }

            if (newValue != -1) {
                if (lastCultivatingValue != -1 && newValue > lastCultivatingValue) {
                    long delta = newValue - lastCultivatingValue;
                    if (currentFarmedCrop != null) {
                        if (currentFarmedCrop.equalsIgnoreCase("Wheat")
                                || currentFarmedCrop.equalsIgnoreCase("Seeds")) {
                            // Ratio 1 Wheat : 1.5 Seeds (Total 2.5)
                            long wheatDelta = Math.round(delta / 2.5);
                            long seedsDelta = delta - wheatDelta;
                            if (wheatDelta > 0)
                                addDrop("Wheat", wheatDelta);
                            if (seedsDelta > 0)
                                addDrop("Seeds", seedsDelta);
                        } else {
                            addDrop(currentFarmedCrop, delta);
                        }
                    }
                }
                lastCultivatingValue = newValue;
            } else {
                lastCultivatingValue = -1;
            }
        } else {
            lastCultivatingValue = -1;
        }

        // 3. Track Purse
        long currentPurse = ClientUtils.getPurse(client);
        if (currentPurse != -1) {
            if (lastPurseBalance != -1) {
                if (currentPurse > lastPurseBalance) {
                    if (com.ihanuat.mod.MacroStateManager.getCurrentState() != MacroState.State.OFF &&
                            com.ihanuat.mod.MacroStateManager.getCurrentState() != MacroState.State.AUTOSELLING) {
                        long delta = currentPurse - lastPurseBalance;
                        if (delta <= 50000) {
                            addDrop("Purse", delta);
                        } else if (com.ihanuat.mod.MacroConfig.showDebug) {
                            ClientUtils.sendDebugMessage(client, "Dismissed large purse change: +" + delta);
                        }
                    }
                }
            }
            lastPurseBalance = currentPurse;
        }

        // Track Pet XP from tab list
        PetXpTracker.update(client);

        // Refresh bazaar prices every hour
        long now = System.currentTimeMillis();
        if (now - lastBazaarFetchTime > 3600000L) {
            fetchBazaarPrices();
        }
    }

    /**
     * Sends the current Rose Dragon BIN price data to the player's chat.
     * Call this when the macro starts so you can verify the fetched prices.
     */
    public static void printPetXpPriceDebug(net.minecraft.client.Minecraft client) {
        if (client.player == null)
            return;

        client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§b[Pet XP Tracker] §fCurrently tracking:"), false);

        for (String petConfig : MacroConfig.petTrackerList) {
            MacroConfig.PetInfo info = new MacroConfig.PetInfo(petConfig);
            long lvl1 = petLvl1Prices.getOrDefault(info.name, 0L);
            long lvlMax = petMaxLvlPrices.getOrDefault(info.name, 0L);
            double pricePerXp = bazaarPrices.getOrDefault("Pet XP (" + info.name + ")", 0.0);

            String lvl1Str = lvl1 > 0 ? String.format("%,d", lvl1) : "not found";
            String lvlMaxStr = lvlMax > 0 ? String.format("%,d", lvlMax) : "not found";
            String marginStr = pricePerXp > 0 ? String.format("%.3f", pricePerXp) : "not fetched";

            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            " §8> §e" + info.name + "§f: §7L1: §6" + lvl1Str + " §7Max: §6" + lvlMaxStr + " §7-> §a"
                                    + marginStr + " §7C/XP"),
                    false);
        }
    }

    /**
     * Called by {@link PetXpTracker} to record XP gained this tick.
     * Uses the same session/lifetime accounting as other drops.
     */
    public static void addPetXp(String petName, long xpAmount) {
        if (xpAmount <= 0)
            return;
        addDrop("Pet XP (" + petName + ")", xpAmount);
    }

    private static int startupPetPriceRetryCount = 3;

    public static void startStartupPriceFetch() {
        startupPetPriceRetryCount = 0;
        fetchBazaarPrices();
    }

    private static synchronized void fetchBazaarPrices() {
        lastBazaarFetchTime = System.currentTimeMillis();
        new Thread(() -> {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            performFetchInternal(client);

            // Startup retry logic: if any pet price is missing, retry up to 3 times every 5
            // seconds
            if (startupPetPriceRetryCount < 3) {
                boolean missingAny = false;
                for (String petConfig : MacroConfig.petTrackerList) {
                    MacroConfig.PetInfo info = new MacroConfig.PetInfo(petConfig);
                    if (!bazaarPrices.containsKey("Pet XP (" + info.name + ")")) {
                        missingAny = true;
                        break;
                    }
                }

                if (missingAny) {
                    startupPetPriceRetryCount++;
                    System.out.println("[Ihanuat] Pet XP prices not fully fetched, retry " + startupPetPriceRetryCount
                            + "/3 in 5s...");
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException ignored) {
                    }
                    fetchBazaarPrices();
                } else {
                    startupPetPriceRetryCount = 3; // Success, don't retry again
                }
            }
        }).start();
    }

    private static void performFetchInternal(java.net.http.HttpClient client) {
        for (Map.Entry<String, String> entry : BAZAAR_MAPPING.entrySet()) {
            String itemName = entry.getKey();
            String itemTag = entry.getValue();

            try {
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://sky.coflnet.com/api/item/price/" + itemTag + "/current"))
                        .GET()
                        .build();

                java.net.http.HttpResponse<String> response = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    BazaarApiResponse data = GSON.fromJson(response.body(), BazaarApiResponse.class);
                    if (data != null && data.buy > 0) {
                        bazaarPrices.put(itemName, data.buy);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch bazaar price for " + itemName + ": " + e.getMessage());
            }
        }
        // Also fetch Pet XP price
        fetchPetXpPrice(client);
    }

    /**
     * Fetches the lowest BIN for level-1 and level-max for all configured pets,
     * deriving the coin value of a single XP point for each.
     */
    private static void fetchPetXpPrice(java.net.http.HttpClient http) {
        for (String petConfig : MacroConfig.petTrackerList) {
            MacroConfig.PetInfo info = new MacroConfig.PetInfo(petConfig);
            long[] table = PetXpTracker.getXpTable(info.rarity, info.maxLevel);
            final long TOTAL_XP = table[info.maxLevel];

            try {
                // ── Level 1 ─────────────────────────────────────────────────────
                long lvl1Price = 0;
                String url1 = "https://sky.coflnet.com/api/auctions/tag/" + info.tag
                        + "/active/overview?query%5BRarity%5D=" + info.rarity.name();

                java.net.http.HttpRequest req1 = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url1))
                        .GET().build();
                java.net.http.HttpResponse<String> resp1 = http.send(req1,
                        java.net.http.HttpResponse.BodyHandlers.ofString());

                if (resp1.statusCode() == 200) {
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<OverviewEntry>>() {
                    }.getType();
                    java.util.List<OverviewEntry> listings = GSON.fromJson(resp1.body(), listType);
                    if (listings != null) {
                        for (OverviewEntry entry : listings) {
                            if (entry.price > 0 && (lvl1Price == 0 || entry.price < lvl1Price)) {
                                lvl1Price = entry.price;
                            }
                        }
                    }
                }

                // ── Max Level ────────────────────────────────────────────────────
                long lvlMaxPrice = 0;
                String urlMax = "https://sky.coflnet.com/api/auctions/tag/" + info.tag
                        + "/active/overview?query%5BRarity%5D=" + info.rarity.name()
                        + "&query%5BPetLevel%5D=" + info.maxLevel;

                java.net.http.HttpRequest reqMax = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(urlMax))
                        .GET().build();
                java.net.http.HttpResponse<String> respMax = http.send(reqMax,
                        java.net.http.HttpResponse.BodyHandlers.ofString());

                if (respMax.statusCode() == 200) {
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<OverviewEntry>>() {
                    }.getType();
                    java.util.List<OverviewEntry> listings = GSON.fromJson(respMax.body(), listType);
                    if (listings != null) {
                        for (OverviewEntry entry : listings) {
                            if (entry.price > 0 && (lvlMaxPrice == 0 || entry.price < lvlMaxPrice)) {
                                lvlMaxPrice = entry.price;
                            }
                        }
                    }
                }

                if (lvl1Price > 0)
                    petLvl1Prices.put(info.name, lvl1Price);
                if (lvlMaxPrice > 0)
                    petMaxLvlPrices.put(info.name, lvlMaxPrice);

                if (lvlMaxPrice > lvl1Price && lvl1Price > 0) {
                    double pricePerXp = (double) (lvlMaxPrice - lvl1Price) / TOTAL_XP;
                    if (pricePerXp > 0) {
                        bazaarPrices.put("Pet XP (" + info.name + ")", pricePerXp);
                    }
                }
            } catch (Exception e) {
                System.err.println("[Ihanuat] Failed to fetch Pet XP price for " + info.name + ": " + e.getMessage());
            }
        }
    }

    // ── Cofl API Item ID Cache ──
    private static final java.util.Map<String, String> idByNameCache = new java.util.concurrent.ConcurrentHashMap<>();

    public static String fetchIdByName(String name) {
        if (name == null || name.isEmpty())
            return null;
        if (idByNameCache.containsKey(name)) {
            String cached = idByNameCache.get(name);
            return cached.isEmpty() ? null : cached;
        }

        try {
            String encoded = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8);
            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3)).build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://sky.coflnet.com/api/items/search/" + encoded + "?limit=1"))
                    .GET().build();
            java.net.http.HttpResponse<String> resp = http.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(body).getAsJsonArray();
                if (arr.size() > 0) {
                    String tag = arr.get(0).getAsJsonObject().get("tag").getAsString();
                    idByNameCache.put(name, tag);
                    return tag;
                }
            }
        } catch (Exception e) {
            System.err.println("[Ihanuat] Cofl item ID lookup failed for '" + name + "': " + e.getMessage());
        }
        idByNameCache.put(name, "");
        return null;
    }

    private static class BazaarApiResponse {
        double buy;
    }

    /** One entry from /api/auctions/tag/{tag}/active/overview */
    private static class OverviewEntry {
        long price;
        String uuid;
    }
}
