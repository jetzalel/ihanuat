package com.ihanuat.mod.modules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfitManager {
    private static final Map<String, Integer> sessionCounts = new LinkedHashMap<>();
    private static final Map<String, Integer> lifetimeCounts = new LinkedHashMap<>();

    private static final java.io.File LIFETIME_FILE = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("pest_macro_profit_lifetime.json").toFile();
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    private static final Set<String> CROPS_SET = Set.of(
            "Wheat", "Enchanted Wheat", "Enchanted Hay Bale",
            "Seeds", "Enchanted Seeds", "Box of Seeds",
            "Potato", "Enchanted Potato", "Enchanted Baked Potato",
            "Carrot", "Enchanted Carrot", "Enchanted Golden Carrot",
            "Melon Slice", "Melon Block", "Enchanted Melon Slice", "Enchanted Melon Block",
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
            "Dung", "Honey Jar", "Plant Matter", "Tasty Cheese", "Compost", "Jelly");

    private static final Set<String> PETS_SET = Set.of("Epic Slug", "Legendary Slug", "Rat");

    private static final Map<String, Long> TRACKED_ITEMS = Map.ofEntries(
            // Crops
            Map.entry("Wheat", 6L), Map.entry("Enchanted Wheat", 960L), Map.entry("Enchanted Hay Bale", 153600L),
            Map.entry("Seeds", 3L), Map.entry("Enchanted Seeds", 480L), Map.entry("Box of Seeds", 76800L),
            Map.entry("Potato", 3L), Map.entry("Enchanted Potato", 480L), Map.entry("Enchanted Baked Potato", 76800L),
            Map.entry("Carrot", 3L), Map.entry("Enchanted Carrot", 480L), Map.entry("Enchanted Golden Carrot", 76800L),
            Map.entry("Melon Slice", 2L), Map.entry("Melon Block", 18L), Map.entry("Enchanted Melon Slice", 320L),
            Map.entry("Enchanted Melon Block", 51200L),
            Map.entry("Pumpkin", 10L), Map.entry("Enchanted Pumpkin", 1600L), Map.entry("Polished Pumpkin", 256000L),
            Map.entry("Sugar Cane", 4L), Map.entry("Enchanted Sugar", 640L), Map.entry("Enchanted Sugar Cane", 102400L),
            Map.entry("Cactus", 4L), Map.entry("Enchanted Cactus Green", 640L), Map.entry("Enchanted Cactus", 102400L),
            Map.entry("Mushroom", 10L), Map.entry("Red Mushroom", 10L), Map.entry("Brown Mushroom", 10L),
            Map.entry("Enchanted Red Mushroom", 1600L), Map.entry("Enchanted Brown Mushroom", 1600L),
            Map.entry("Enchanted Red Mushroom Block", 256000L), Map.entry("Enchanted Brown Mushroom Block", 256000L),
            Map.entry("Cocoa Beans", 3L), Map.entry("Enchanted Cocoa Beans", 480L),
            Map.entry("Enchanted Cookie", 76800L),
            Map.entry("Nether Wart", 4L), Map.entry("Enchanted Nether Wart", 640L),
            Map.entry("Mutant Nether Wart", 102400L),
            Map.entry("Sunflower", 4L), Map.entry("Enchanted Sunflower", 640L),
            Map.entry("Compacted Sunflower", 102400L),
            Map.entry("Moonflower", 4L), Map.entry("Enchanted Moonflower", 640L),
            Map.entry("Compacted Moonflower", 102400L),
            Map.entry("Wild Rose", 4L), Map.entry("Enchanted Wild Rose", 640L),
            Map.entry("Compacted Wild Rose", 102400L),

            // Pest Items
            Map.entry("Beady Eyes", 25000L), Map.entry("Chirping Stereo", 100000L), Map.entry("Sunder VI Book", 0L),
            Map.entry("Clipped Wings", 25000L), Map.entry("Bookworm's Favorite Book", 10000L),
            Map.entry("Atmospheric Filter", 100000L),
            Map.entry("Wriggling Larva", 2500000L), Map.entry("Pesterminator I Book", 0L),
            Map.entry("Squeaky Toy", 10000L),
            Map.entry("Squeaky Mousemat", 1000000L), Map.entry("Fire in a Bottle", 100000L),
            Map.entry("Vermin Vaporizer Chip", 100000L),
            Map.entry("Mantid Claw", 75000L),
            Map.entry("Overclocker 3000", 1000000L),
            Map.entry("Vinyl", 50000L),
            Map.entry("Dung", 0L), Map.entry("Honey Jar", 0L), Map.entry("Plant Matter", 0L),
            Map.entry("Tasty Cheese", 0L), Map.entry("Compost", 0L), Map.entry("Jelly", 0L),

            // Pets
            Map.entry("Epic Slug", 500000L), Map.entry("Legendary Slug", 5000000L), Map.entry("Rat", 5000L));

    private static final Pattern PEST_PATTERN = Pattern.compile("received\\s+(\\d+)x\\s+(.+?)\\s+for\\s+killing",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RARE_DROP_PATTERN = Pattern.compile(
            "RARE DROP!\\s+(?:You dropped\\s+)?(?:(\\d+)x\\s+)?(.+?)(?:\\s+\\(|!|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PET_DROP_PATTERN = Pattern.compile(
            "PET DROP!\\s+(?:§[0-9a-fk-or])*§([56])(?:§[0-9a-fk-or])*([\\w\\s]+?)(?:\\s+\\(|!|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");

    public static void handleChatMessage(String text) {
        // PET DROP needs raw text to detect color-coded rarity
        Matcher petMatcher = PET_DROP_PATTERN.matcher(text);
        if (petMatcher.find()) {
            String colorCode = petMatcher.group(1); // 5 = Epic, 6 = Legendary
            String petName = petMatcher.group(2).trim();
            String finalName = petName;

            if (petName.equalsIgnoreCase("Slug")) {
                if (colorCode.equals("5")) {
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

        String cleanText = STRIP_COLOR_PATTERN.matcher(text).replaceAll("");

        Matcher pestMatcher = PEST_PATTERN.matcher(cleanText);
        if (pestMatcher.find()) {
            try {
                int count = Integer.parseInt(pestMatcher.group(1));
                String itemName = pestMatcher.group(2).trim();
                addDrop(itemName, count);
                return;
            } catch (Exception ignored) {
            }
        }

        Matcher rareMatcher = RARE_DROP_PATTERN.matcher(cleanText);
        if (rareMatcher.find()) {
            try {
                String countStr = rareMatcher.group(1);
                int count = (countStr != null) ? Integer.parseInt(countStr) : 1;
                String itemName = rareMatcher.group(2).trim();
                addDrop(itemName, count);
            } catch (Exception ignored) {
            }
        }
    }

    private static void addDrop(String itemName, int count) {
        // Handle items with suffix counts like "Mutant Nether Wart X9"
        String processedName = itemName.trim();
        int multiplier = 1;

        Matcher suffixMatcher = Pattern.compile("\\s+[xX](\\d+)$").matcher(processedName);
        if (suffixMatcher.find()) {
            try {
                multiplier = Integer.parseInt(suffixMatcher.group(1));
                processedName = processedName.substring(0, suffixMatcher.start()).trim();
            } catch (Exception ignored) {
            }
        }

        int finalCount = count * multiplier;

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

        // If not in the "crop" list, we still track it but use a normalized version of
        // the name
        if (matchedName == null) {
            matchedName = normalizeName(processedName);
        }

        sessionCounts.put(matchedName, sessionCounts.getOrDefault(matchedName, 0) + finalCount);
        lifetimeCounts.put(matchedName, lifetimeCounts.getOrDefault(matchedName, 0) + finalCount);
        saveLifetime();
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

    public static Map<String, Integer> getActiveDrops() {
        return getActiveDrops(false);
    }

    public static Map<String, Integer> getActiveDrops(boolean lifetime) {
        return new LinkedHashMap<>(lifetime ? lifetimeCounts : sessionCounts);
    }

    public static Map<String, Long> getCompactDrops() {
        return getCompactDrops(false);
    }

    public static Map<String, Long> getCompactDrops(boolean lifetime) {
        Map<String, Long> compact = new LinkedHashMap<>();
        compact.put("Crops", 0L);
        compact.put("Pest Items", 0L);
        compact.put("Pets", 0L);
        compact.put("Others", 0L);

        Map<String, Integer> targetCounts = lifetime ? lifetimeCounts : sessionCounts;
        for (Map.Entry<String, Integer> entry : targetCounts.entrySet()) {
            String name = entry.getKey();
            int count = entry.getValue();
            long price = getItemPrice(name);
            long profit = price * count;

            if (CROPS_SET.contains(name)) {
                compact.put("Crops", compact.get("Crops") + profit);
            } else if (PEST_ITEMS_SET.contains(name)) {
                compact.put("Pest Items", compact.get("Pest Items") + profit);
            } else if (PETS_SET.contains(name)) {
                compact.put("Pets", compact.get("Pets") + profit);
            } else {
                compact.put("Others", compact.get("Others") + profit);
            }
        }
        return compact;
    }

    public static void reset() {
        sessionCounts.clear();
    }

    public static void resetLifetime() {
        lifetimeCounts.clear();
        saveLifetime();
    }

    public static long getTotalProfit() {
        return getTotalProfit(false);
    }

    public static long getTotalProfit(boolean lifetime) {
        long total = 0;
        Map<String, Integer> targetCounts = lifetime ? lifetimeCounts : sessionCounts;
        for (Map.Entry<String, Integer> entry : targetCounts.entrySet()) {
            Long price = TRACKED_ITEMS.get(entry.getKey());
            if (price != null) {
                total += price * entry.getValue();
            }
        }
        return total;
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
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, Integer>>() {
            }.getType();
            Map<String, Integer> data = GSON.fromJson(reader, type);
            if (data != null) {
                lifetimeCounts.clear();
                lifetimeCounts.putAll(data);
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    public static long getItemPrice(String itemName) {
        return TRACKED_ITEMS.getOrDefault(itemName, 0L);
    }

    public static boolean isPredefinedTrackedItem(String itemName) {
        if (itemName == null)
            return false;
        for (String tracked : TRACKED_ITEMS.keySet()) {
            if (tracked.equalsIgnoreCase(itemName)) {
                return true;
            }
        }
        return false;
    }
}
