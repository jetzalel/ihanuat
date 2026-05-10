package com.ihanuat.mod;

import com.google.gson.Gson;
import java.util.concurrent.locks.ReentrantLock;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class MacroConfig {
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long CORRUPTED_EPOCH_WINDOW_MS = 30L * DAY_MS;
    public static final File CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("ihanuat").toFile();
    public static final File SOUNDS_DIR = new File(CONFIG_DIR, "sounds");

    public enum PetRarity {
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC
    }

    public enum UnflyMode {
        SNEAK, DOUBLE_TAP_SPACE
    }

    // text style for both the clickgui and HUD — none, drop shadow, or outline
    public enum TextStyle {
        NONE, SHADOW, OUTLINE
    }

    // pricing mode for the profit calculator
    public enum PricingMode {
        NPC, INSTA_SELL, INSTA_BUY
    }

    public enum Language {
        ENGLISH,
        SIMPLIFIED_CHINESE;

        @Override
        public String toString() {
            return this == SIMPLIFIED_CHINESE ? "简体中文" : "English";
        }
    }

    // ── Defaults ──────────────────────────────────────────────────────────────
    public static final int DEFAULT_PEST_THRESHOLD = 5;
    public static final boolean DEFAULT_AUTO_PEST_ENABLED = true;
    public static final boolean DEFAULT_MANUAL_PEST_CLEAN = false;
    public static final int DEFAULT_MANUAL_PEST_RETURN_DELAY = 2500;
    public static final boolean DEFAULT_MANUAL_PEST_ALERT_SOUND = false;
    public static final int DEFAULT_MANUAL_PEST_REWARP_AT = 0;
    public static final String DEFAULT_MANUAL_PEST_SOUND_PATH = "";
    public static final boolean DEFAULT_DISCO_DESTINATION_MODE = false;
    public static final int DEFAULT_DISCO_RETURN_DELAY = 2500;
    public static final int DEFAULT_DISCO_REWARP_AT = 0;
    public static final boolean DEFAULT_DISCO_TP_BEFORE_WARDROBE = false;
    public static final boolean DEFAULT_TRIGGER_PEST_ON_CHAT = true;
    public static final int DEFAULT_VISITOR_THRESHOLD = 5;
    public static final boolean DEFAULT_AUTO_WARDROBE_PEST = true;
    public static final boolean DEFAULT_AUTO_WARDROBE_VISITOR = false;
    public static final boolean DEFAULT_AUTO_ROD_PEST_CD = false;
    public static final boolean DEFAULT_AUTO_ROD_PEST_SPAWN = false;
    public static final boolean DEFAULT_AUTO_ROD_RETURN_TO_FARM = false;
    public static final boolean DEFAULT_CALL_PHILLIP_FOR_BONUS = true;
    public static final boolean DEFAULT_SPRAY_SINGLE_PLOT = false;
    public static final UnflyMode DEFAULT_UNFLY_MODE = UnflyMode.DOUBLE_TAP_SPACE;
    public static final boolean DEFAULT_AUTO_VISITOR = true;
    public static final boolean DEFAULT_AUTO_EQUIPMENT = true;
    public static final boolean DEFAULT_AUTO_STASH_MANAGER = false;
    public static final boolean DEFAULT_AUTO_BOOK_COMBINE = false;
    public static final boolean DEFAULT_AUTO_GEORGE_SELL = false;
    public static final boolean DEFAULT_AUTO_BOOSTER_COOKIE = true;
    public static final boolean DEFAULT_ALWAYS_ACTIVE_COMBINE = false;
    public static final java.util.List<String> DEFAULT_BOOSTER_COOKIE_ITEMS = java.util.Arrays.asList(
            "Atmospheric Filter","Squeaky Toy","Beady Eyes","Clipped Wings","Overclocker",
            "Mantid Claw","Flowering Bouquet","Bookworm","Chirping Stereo","Firefly","Capsule","Vinyl");
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
    public static final int DEFAULT_AUTOSELL_CLICK_DELAY = 500;
    public static final int DEFAULT_EQUIPMENT_SWAP_DELAY = 250;
    public static final int DEFAULT_ROD_SWAP_DELAY = 100;
    public static final int DEFAULT_BOOK_COMBINE_DELAY = 300;
    public static final int DEFAULT_WARDROBE_POST_SWAP_DELAY = 250;
    /** Delay (ms) after /setspawn before any wardrobe interaction during pest cleaning. */
    public static final int DEFAULT_SETSPAWN_COOLDOWN = 1000;
    /** Delay (ms) between wardrobe-swap finishing and kicking off AOTV-to-roof. */
    public static final int DEFAULT_WARDROBE_AOTV_DELAY = 250;
    /** Delay (ms) between Y-change detection (AOTV landed) and equipping vacuum. 0 = no delay. */
    public static final int DEFAULT_AOTV_VACUUM_DELAY = 0;
    public static final int DEFAULT_BOOK_THRESHOLD = 7;
    public static final int DEFAULT_ADDITIONAL_RANDOM_DELAY = 0;

    public static final java.util.List<String> DEFAULT_FARM_SCRIPTS = java.util.Arrays.asList(
            "netherwart:1","netherwart:0","sugarcane:classical","sugarcane:sshape",
            "cactus","cocoa","mushroom:1","mushroom:0","pumpkin:1");
    public static final String DEFAULT_RESTART_SCRIPT = "netherwart:1";

    public static final int DEFAULT_REST_SCRIPTING_TIME = 30;
    public static final int DEFAULT_REST_SCRIPTING_TIME_OFFSET = 3;
    public static final int DEFAULT_REST_BREAK_TIME = 20;
    public static final int DEFAULT_REST_BREAK_TIME_OFFSET = 3;
    public static final boolean DEFAULT_SUPER_CRAFT_BEFORE_REST = false;
    public static final java.util.List<String> DEFAULT_SUPER_CRAFT_CROPS = java.util.Arrays.asList(
    "enchanted hay bale", "box of seeds", "enchanted baked potato",
    "enchanted golden carrot", "polished pumpkin",
    "enchanted sugar cane", "enchanted red mushroom block",
    "enchanted brown mushroom block", "enchanted cookie", "mutant nether wart",
    "compacted sunflower", "compacted moonflower", "compacted wild rose");
    public static final boolean DEFAULT_ENABLE_PLOT_TP_REWARP = false;
    public static final boolean DEFAULT_HOLD_W_UNTIL_WALL = false;
    public static final String DEFAULT_PLOT_TP_NUMBER = "0";
    public static final String DEFAULT_DISCORD_WEBHOOK_URL = "";
    public static final int DEFAULT_DISCORD_STATUS_UPDATE_TIME = 5;
    public static final boolean DEFAULT_SEND_DISCORD_STATUS = false;
    public static final boolean DEFAULT_PERSIST_SESSION_TIMER = true;
    public static final boolean DEFAULT_COMPACT_PROFIT_CALCULATOR = false;
    public static final boolean DEFAULT_SHOW_PROFIT_HUD_WHILE_INACTIVE = false;
    public static final PricingMode DEFAULT_PRICING_MODE = PricingMode.NPC;
    public static final boolean DEFAULT_HIDE_FILTERED_CHAT = true;
    public static final boolean DEFAULT_AUTO_DROP_JUNK = false;
    public static final java.util.List<String> DEFAULT_JUNK_ITEMS = java.util.Arrays.asList(
            "Fruit Bowl","Farming Exp Boost","Sunder VI");
    public static final String DEFAULT_DROP_JUNK_PLOT_TP = "0";
    public static final int DEFAULT_JUNK_THRESHOLD = 3;

    // Kept for backward-compat (loaded/saved) but no longer shown in UI
    public static final boolean DEFAULT_SHOW_DEBUG = false;
    public static final boolean DEFAULT_LOG_DEBUG_TO_FILE = false;
    public static final boolean DEFAULT_AUTO_RECOVER_UNEXPECTED_DISCONNECT = true;
    public static final boolean DEFAULT_AUTO_RESUME_AFTER_DYNAMIC_REST = true;
    public static final boolean DEFAULT_GUI_ONLY_IN_GARDEN = true;
    public static final boolean DEFAULT_BREAK_BLOCKS_BEFORE_AOTV = false;
    public static final boolean DEFAULT_DELAY_PEST_FOR_CROP_FEVER = false;

    // Quit Threshold
    public static final double DEFAULT_QUIT_THRESHOLD_HOURS = 0.0;
    public static final boolean DEFAULT_FORCE_QUIT_MINECRAFT = false;
    public static final boolean DEFAULT_QUIT_AFTER_SESSION_LENGTH = false;

    // Pet Tracker
    public static final java.util.List<String> DEFAULT_PET_TRACKER_LIST =
            java.util.Arrays.asList("Rose Dragon, 200, 670000000, 1250000000, Legendary");

    // HUD layout
    public static final int DEFAULT_HUD_X = 10;
    public static final int DEFAULT_HUD_Y = 10;
    public static final float DEFAULT_HUD_SCALE = 1.0f;
    public static final boolean DEFAULT_SHOW_HUD = true;
    public static final boolean DEFAULT_SHOW_TOTAL_TODAY = true;
    public static final boolean DEFAULT_SHOW_TOTAL_FARMED = true;

    // Session profit HUD
    public static final int DEFAULT_SESSION_PROFIT_HUD_X = 10;
    public static final int DEFAULT_SESSION_PROFIT_HUD_Y = 150;
    public static final float DEFAULT_SESSION_PROFIT_HUD_SCALE = 1.0f;
    public static final boolean DEFAULT_SHOW_SESSION_PROFIT_HUD = true;

    // Lifetime profit HUD (daily HUD removed entirely)
    public static final int DEFAULT_LIFETIME_HUD_X = 10;
    public static final int DEFAULT_LIFETIME_HUD_Y = 290;
    public static final float DEFAULT_LIFETIME_HUD_SCALE = 1.0f;
    public static final boolean DEFAULT_SHOW_LIFETIME_HUD = true;

    // ── HUD color defaults (stored as 0xRRGGBB) ───────────────────────────────
    public static final int DEFAULT_HUD_BG_COLOR             = 0x141424;
    public static final int DEFAULT_HUD_ACCENT_COLOR         = 0x4A4A88;
    public static final int DEFAULT_HUD_TITLE_COLOR          = 0xFFFFFF;
    public static final int DEFAULT_HUD_LABEL_COLOR          = 0xAAAAAA;
    public static final int DEFAULT_HUD_VALUE_COLOR          = 0xFFFFFF;
    public static final int DEFAULT_HUD_BAR_BG_COLOR         = 0x1A1A32;
    public static final int DEFAULT_HUD_BAR_FILL_COLOR       = 0x6464B4;
    public static final int DEFAULT_HUD_STATE_OFF_COLOR      = 0xFF5555;
    public static final int DEFAULT_HUD_STATE_FARMING_COLOR      = 0x55FF55;
    public static final int DEFAULT_HUD_STATE_CLEANING_COLOR     = 0xFFAA00;
    public static final int DEFAULT_HUD_STATE_RECOVERING_COLOR   = 0xFF5555;
    public static final int DEFAULT_HUD_STATE_VISITING_COLOR     = 0x55FFFF;
    public static final int DEFAULT_HUD_STATE_AUTOSELLING_COLOR  = 0xAA55FF;
    public static final int DEFAULT_HUD_STATE_SPRAYING_COLOR     = 0xFF55FF;

    // ── Live fields ───────────────────────────────────────────────────────────
    public static int pestThreshold = DEFAULT_PEST_THRESHOLD;
    public static boolean autoPestEnabled = DEFAULT_AUTO_PEST_ENABLED;
    public static boolean manualPestClean = DEFAULT_MANUAL_PEST_CLEAN;
    public static int manualPestReturnDelay = DEFAULT_MANUAL_PEST_RETURN_DELAY;
    public static boolean manualPestAlertSound = DEFAULT_MANUAL_PEST_ALERT_SOUND;
    public static int manualPestRewarpAt = DEFAULT_MANUAL_PEST_REWARP_AT;
    public static String manualPestSoundPath = DEFAULT_MANUAL_PEST_SOUND_PATH;
    public static boolean discoDestinationMode = DEFAULT_DISCO_DESTINATION_MODE;
    public static int discoReturnDelay = DEFAULT_DISCO_RETURN_DELAY;
    public static int discoRewarpAt = DEFAULT_DISCO_REWARP_AT;
    public static boolean discoTpBeforeWardrobe = DEFAULT_DISCO_TP_BEFORE_WARDROBE;

    /**
     * True when the pest cleaner should bypass the Taunahi CLI script and
     * use the in-mod return-detection flow (alive-count → rewarp threshold →
     * delay → return). Both Manual Pest Clean and Disco Destination Mode
     * share this flow; Disco additionally automates holding right-click.
     */
    public static boolean usesManualPestReturnFlow() {
        return manualPestClean || discoDestinationMode;
    }

    /**
     * The alive-pest count at which the in-mod return flow rewarps to farming.
     * Disco wins when both modes are toggled (consistent with sequencer
     * precedence in {@code PestCleaningSequencer.submitCleaningSequence}).
     */
    public static int getActiveRewarpAt() {
        return discoDestinationMode ? discoRewarpAt : manualPestRewarpAt;
    }

    /** How long the alive count must stay at/below the rewarp threshold before returning. */
    public static int getActiveReturnDelayMs() {
        return discoDestinationMode ? discoReturnDelay : manualPestReturnDelay;
    }
    public static boolean triggerPestOnChat = DEFAULT_TRIGGER_PEST_ON_CHAT;
    public static final int DEFAULT_PEST_CHAT_TRIGGER_DELAY = 0;
    public static int pestChatTriggerDelay = DEFAULT_PEST_CHAT_TRIGGER_DELAY;
    public static int visitorThreshold = DEFAULT_VISITOR_THRESHOLD;
    public static boolean autoWardrobePest = DEFAULT_AUTO_WARDROBE_PEST;
    public static boolean autoWardrobeVisitor = DEFAULT_AUTO_WARDROBE_VISITOR;
    public static boolean autoRodPestCd = DEFAULT_AUTO_ROD_PEST_CD;
    public static boolean autoRodPestSpawn = DEFAULT_AUTO_ROD_PEST_SPAWN;
    public static boolean autoRodReturnToFarm = DEFAULT_AUTO_ROD_RETURN_TO_FARM;
    public static boolean callPhillipForBonus = DEFAULT_CALL_PHILLIP_FOR_BONUS;
    public static boolean spraySinglePlot = DEFAULT_SPRAY_SINGLE_PLOT;
    public static UnflyMode unflyMode = DEFAULT_UNFLY_MODE;
    public static boolean autoVisitor = DEFAULT_AUTO_VISITOR;
    public static boolean autoEquipment = DEFAULT_AUTO_EQUIPMENT;
    public static boolean autoStashManager = DEFAULT_AUTO_STASH_MANAGER;
    public static boolean autoBookCombine = DEFAULT_AUTO_BOOK_COMBINE;
    public static boolean autoGeorgeSell = DEFAULT_AUTO_GEORGE_SELL;
    public static boolean autoBoosterCookie = DEFAULT_AUTO_BOOSTER_COOKIE;
    public static boolean alwaysActiveCombine = DEFAULT_ALWAYS_ACTIVE_COMBINE;
    public static java.util.List<String> boosterCookieItems = new java.util.ArrayList<>(DEFAULT_BOOSTER_COOKIE_ITEMS);
    public static java.util.List<String> customEnchantmentLevels = new java.util.ArrayList<>(DEFAULT_CUSTOM_ENCHANTMENT_LEVELS);
    public static int georgeSellThreshold = DEFAULT_GEORGE_SELL_THRESHOLD;
    public static int rotationTime = DEFAULT_ROTATION_TIME;
    public static boolean aotvToRoof = DEFAULT_AOTV_TO_ROOF;
    public static int aotvRoofPitch = DEFAULT_AOTV_ROOF_PITCH;
    public static int aotvRoofPitchHumanization = DEFAULT_AOTV_ROOF_PITCH_HUMANIZATION;
    public static java.util.List<String> aotvRoofPlots = new java.util.ArrayList<>(DEFAULT_AOTV_ROOF_PLOTS);
    public static int wardrobeSlotFarming = DEFAULT_WARDROBE_SLOT_FARMING;
    public static int wardrobeSlotPest = DEFAULT_WARDROBE_SLOT_PEST;
    public static int wardrobeSlotVisitor = DEFAULT_WARDROBE_SLOT_VISITOR;
    public static int guiClickDelay = DEFAULT_GUI_CLICK_DELAY;
    public static int autosellClickDelay = DEFAULT_AUTOSELL_CLICK_DELAY;
    public static int equipmentSwapDelay = DEFAULT_EQUIPMENT_SWAP_DELAY;
    public static int rodSwapDelay = DEFAULT_ROD_SWAP_DELAY;
    public static int postRodReturnDelay = 80; // ms delay after restoring slot
    public static int bookCombineDelay = DEFAULT_BOOK_COMBINE_DELAY;
    public static int wardrobePostSwapDelay = DEFAULT_WARDROBE_POST_SWAP_DELAY;
    public static int setspawnCooldown = DEFAULT_SETSPAWN_COOLDOWN;
    public static int wardrobeAotvDelay = DEFAULT_WARDROBE_AOTV_DELAY;
    public static int aotvVacuumDelay = DEFAULT_AOTV_VACUUM_DELAY;
    public static int bookThreshold = DEFAULT_BOOK_THRESHOLD;
    public static int additionalRandomDelay = DEFAULT_ADDITIONAL_RANDOM_DELAY;
    public static String restartScript = DEFAULT_RESTART_SCRIPT;
    // Dynamic Rest: farming session duration range (minutes)
    public static int restScriptingTimeMin = DEFAULT_REST_SCRIPTING_TIME - DEFAULT_REST_SCRIPTING_TIME_OFFSET;
    public static int restScriptingTimeMax = DEFAULT_REST_SCRIPTING_TIME + DEFAULT_REST_SCRIPTING_TIME_OFFSET;
    // Dynamic Rest: break duration range (minutes)
    public static int restBreakTimeMin = DEFAULT_REST_BREAK_TIME - DEFAULT_REST_BREAK_TIME_OFFSET;
    public static int restBreakTimeMax = DEFAULT_REST_BREAK_TIME + DEFAULT_REST_BREAK_TIME_OFFSET;
    public static boolean superCraftBeforeRest = DEFAULT_SUPER_CRAFT_BEFORE_REST;
    public static java.util.List<String> superCraftCrops = new java.util.ArrayList<>(DEFAULT_SUPER_CRAFT_CROPS);
    public static boolean enablePlotTpRewarp = DEFAULT_ENABLE_PLOT_TP_REWARP;
    public static boolean holdWUntilWall = DEFAULT_HOLD_W_UNTIL_WALL;
    public static String plotTpNumber = DEFAULT_PLOT_TP_NUMBER;
    public static String discordWebhookUrl = DEFAULT_DISCORD_WEBHOOK_URL;
    public static int discordStatusUpdateTime = DEFAULT_DISCORD_STATUS_UPDATE_TIME;
    public static boolean sendDiscordStatus = DEFAULT_SEND_DISCORD_STATUS;
    public static boolean persistSessionTimer = DEFAULT_PERSIST_SESSION_TIMER;
    public static boolean compactProfitCalculator = DEFAULT_COMPACT_PROFIT_CALCULATOR;
    public static boolean showProfitHudWhileInactive = DEFAULT_SHOW_PROFIT_HUD_WHILE_INACTIVE;
    public static PricingMode pricingMode = DEFAULT_PRICING_MODE;
    public static boolean hideFilteredChat = DEFAULT_HIDE_FILTERED_CHAT;
    public static boolean autoDropJunk = DEFAULT_AUTO_DROP_JUNK;
    public static java.util.List<String> junkItems = new java.util.ArrayList<>(DEFAULT_JUNK_ITEMS);
    public static String dropJunkPlotTp = DEFAULT_DROP_JUNK_PLOT_TP;
    public static int junkThreshold = DEFAULT_JUNK_THRESHOLD;
    public static boolean showDebug = DEFAULT_SHOW_DEBUG;
    public static boolean logDebugToFile = DEFAULT_LOG_DEBUG_TO_FILE;
    public static boolean autoRecoverUnexpectedDisconnect = DEFAULT_AUTO_RECOVER_UNEXPECTED_DISCONNECT;
    public static boolean autoResumeAfterDynamicRest = DEFAULT_AUTO_RESUME_AFTER_DYNAMIC_REST;
    public static boolean guiOnlyInGarden = DEFAULT_GUI_ONLY_IN_GARDEN;
    public static boolean breakBlocksBeforeAotv = DEFAULT_BREAK_BLOCKS_BEFORE_AOTV;
    public static boolean delayPestForCropFever = DEFAULT_DELAY_PEST_FOR_CROP_FEVER;
    public static double quitThresholdHours = DEFAULT_QUIT_THRESHOLD_HOURS;
    public static boolean forceQuitMinecraft = DEFAULT_FORCE_QUIT_MINECRAFT;
    public static boolean quitAfterSessionLength = DEFAULT_QUIT_AFTER_SESSION_LENGTH;
    public static long quitThresholdAccumulatedMs = 0;
    public static Language language = Language.ENGLISH;
    public static java.util.List<String> petXpTrackedPets = new java.util.ArrayList<>(DEFAULT_PET_TRACKER_LIST);
    // Legacy field kept only for backward compatibility with older config files.
    public static java.util.List<String> petTrackerList = new java.util.ArrayList<>(DEFAULT_PET_TRACKER_LIST);
    public static final java.util.List<String> DEFAULT_CHAT_RULES = java.util.Collections.emptyList();
    public static java.util.List<String> chatRules = new java.util.ArrayList<>(DEFAULT_CHAT_RULES);
    public static java.util.List<String> savedThemes = new java.util.ArrayList<>();

    // HUD layout
    public static int hudX = DEFAULT_HUD_X;
    public static int hudY = DEFAULT_HUD_Y;
    public static float hudScale = DEFAULT_HUD_SCALE;
    public static boolean showHud = DEFAULT_SHOW_HUD;
    public static boolean showTotalToday = DEFAULT_SHOW_TOTAL_TODAY;
    public static boolean showTotalFarmed = DEFAULT_SHOW_TOTAL_FARMED;

    // Session profit HUD
    public static int sessionProfitHudX = DEFAULT_SESSION_PROFIT_HUD_X;
    public static int sessionProfitHudY = DEFAULT_SESSION_PROFIT_HUD_Y;
    public static float sessionProfitHudScale = DEFAULT_SESSION_PROFIT_HUD_SCALE;
    public static boolean showSessionProfitHud = DEFAULT_SHOW_SESSION_PROFIT_HUD;

    // Lifetime profit HUD
    public static int lifetimeHudX = DEFAULT_LIFETIME_HUD_X;
    public static int lifetimeHudY = DEFAULT_LIFETIME_HUD_Y;
    public static float lifetimeHudScale = DEFAULT_LIFETIME_HUD_SCALE;
    public static boolean showLifetimeHud = DEFAULT_SHOW_LIFETIME_HUD;

    // Persisted timing
    public static long lifetimeAccumulated = 0;
    /** Calendar date string yyyy-MM-dd for "total today" tracking. */
    public static String todayDateStr = "";
    /** Accumulated farming ms for today (excludes dynamic rest breaks). */
    public static long todayAccumulatedMs = 0;

    // Rewarp
    public static double rewarpEndX = 0;
    public static double rewarpEndY = 0;
    public static double rewarpEndZ = 0;
    public static boolean rewarpEndPosSet = false;
    public static boolean armorSwapVisitor = false;
    public static int[][] clickGuiPanelPositions = new int[14][3];
    public static int themePanelBg     = 0xF0101018;
    public static int themePanelHeader = 0xFF18182C;
    public static int themeAccent      = 0xFF5050A0;
    public static int themeText        = 0xFFCCCCCC;
    public static int themeTextDim     = 0xFF666677;
    public static int themeToggleOn    = 0xFF4444BB;
    public static int themeToggleOff   = 0xFF2A2A3A;
    public static int themeSliderFill  = 0xFF3A3A99;
    public static int themeButtonHover = 0xFF4444BB;
    public static TextStyle themeTextStyle = TextStyle.NONE;
    public static final int DEFAULT_THEME_OUTLINE_SIZE = 1;
    public static final int DEFAULT_THEME_SHADOW_OPACITY = 180;
    // outline pixel radius (1-3), shadow uses this as nothing extra needed
    public static int themeOutlineSize = 1;
    // shadow color opacity (0-255), 0 = fully transparent, 255 = fully opaque black
    public static int themeShadowOpacity = 180;

    // ── HUD colors (0xRRGGBB — no alpha channel stored) ──────────────────────
    public static int hudBgColor            = DEFAULT_HUD_BG_COLOR;
    public static int hudAccentColor        = DEFAULT_HUD_ACCENT_COLOR;
    public static int hudTitleColor         = DEFAULT_HUD_TITLE_COLOR;
    public static int hudLabelColor         = DEFAULT_HUD_LABEL_COLOR;
    public static int hudValueColor         = DEFAULT_HUD_VALUE_COLOR;
    public static int hudBarBgColor         = DEFAULT_HUD_BAR_BG_COLOR;
    public static int hudBarFillColor       = DEFAULT_HUD_BAR_FILL_COLOR;
    public static int hudStateOffColor      = DEFAULT_HUD_STATE_OFF_COLOR;
    public static int hudStateFarmingColor      = DEFAULT_HUD_STATE_FARMING_COLOR;
    public static int hudStateCleaningColor     = DEFAULT_HUD_STATE_CLEANING_COLOR;
    public static int hudStateRecoveringColor   = DEFAULT_HUD_STATE_RECOVERING_COLOR;
    public static int hudStateVisitingColor     = DEFAULT_HUD_STATE_VISITING_COLOR;
    public static int hudStateAutosellingColor  = DEFAULT_HUD_STATE_AUTOSELLING_COLOR;
    public static int hudStateSprayingColor     = DEFAULT_HUD_STATE_SPRAYING_COLOR;
    public static int hudDynamicRestBgColor     = 0x000000;  // background color for the dynamic rest screen

    // ── Helper panel theme colors ──────────────────────────────────────────

    // ── Color helpers ─────────────────────────────────────────────────────────

    /** Converts stored 0xRRGGBB to fully-opaque 0xFFRRGGBB ARGB. */
    public static int toArgb(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }

    /** Parses a 6-char RRGGBB hex string (with or without #) to 0xRRGGBB. */
    public static int parseHexColor(String hex, int fallback) {
        if (hex == null) return fallback & 0xFFFFFF;
        String h = hex.trim().replaceFirst("^#", "");
        if (h.length() == 6) {
            try { return Integer.parseInt(h, 16) & 0xFFFFFF; }
            catch (NumberFormatException ignored) {}
        }
        return fallback & 0xFFFFFF;
    }

    /** Formats 0xRRGGBB as 6-char uppercase hex string. */
    public static String toHexString(int rgb) { return String.format("%06X", rgb & 0xFFFFFF); }

    /**
     * draws text respecting the current themeTextStyle
     * shadow = built-in mc shadow, outline = manual 1px outline trick
     */
    public static void drawStyledText(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        switch (themeTextStyle) {
            case SHADOW:
                // shadow — mc built-in shadow but we tint it with themeShadowOpacity
                // mc doesn't let us control shadow color directly so we fake it:
                // draw a semi-transparent black offset copy then the real text on top
                if (themeShadowOpacity > 0) {
                    int shadowColor = (themeShadowOpacity << 24) | 0x000000;
                    g.drawString(font, text, x + 1, y + 1, shadowColor, false);
                }
                g.drawString(font, text, x, y, color, false);
                break;
            case OUTLINE: {
                // outline — draw shadow color at each pixel in the outline radius, then text on top
                int shadowColor = (themeShadowOpacity << 24) | 0x000000;
                int r = Math.max(1, Math.min(3, themeOutlineSize));
                for (int ox = -r; ox <= r; ox++) {
                    for (int oy = -r; oy <= r; oy++) {
                        if (ox == 0 && oy == 0) continue;
                        // skip corners for sizes > 1 to get a rounder look
                        if (r > 1 && Math.abs(ox) == r && Math.abs(oy) == r) continue;
                        g.drawString(font, text, x + ox, y + oy, shadowColor, false);
                    }
                }
                g.drawString(font, text, x, y, color, false);
                break;
            }
            default:
                g.drawString(font, text, x, y, color, false);
                break;
        }
    }

    private static long sanitizeLifetimeAccumulated(long savedValue) {
        long normalized = Math.max(0L, savedValue);
        long now = System.currentTimeMillis();
        if (Math.abs(normalized - now) <= CORRUPTED_EPOCH_WINDOW_MS) {
            System.err.println("[Ihanuat] Ignoring corrupted lifetime timer value that matched epoch time: " + normalized);
            return 0L;
        }
        return normalized;
    }

    public static int getRandomizedDelay(int baseDelay) {
        if (additionalRandomDelay <= 0) return baseDelay;
        return baseDelay + (int) (Math.random() * (additionalRandomDelay + 1));
    }

    // ── Script helpers ────────────────────────────────────────────────────────

    public static String getFullRestartCommand() {
        if (restartScript == null || restartScript.isEmpty()) return "";
        if (restartScript.startsWith("/") || restartScript.startsWith(".ez-startscript")) return restartScript;
        return ".ez-startscript " + restartScript;
    }

    public static String getScriptDescription(String script) {
        if (script == null) return "";
        switch (script) {
            case "netherwart:1":      return "Wart/Crops (S-Shape)";
            case "netherwart:0":      return "Wart/Crops (Vertical)";
            case "sugarcane:classical": return "Sugarcane/Flowers (Classical)";
            case "sugarcane:sshape":  return "Sunflower (S-Shape)";
            case "cactus":            return "Cactus";
            case "cocoa":             return "Cocoa";
            case "mushroom:1":        return "Mushroom (Staircase)";
            case "mushroom:0":        return "Mushroom (Classical)";
            case "pumpkin:1":         return "Pumpkin/Melon";
            default:                  return "Custom Script";
        }
    }

    public static void executePlotTpRewarp(net.minecraft.client.Minecraft client) {
        if (enablePlotTpRewarp)
            com.ihanuat.mod.util.ClientUtils.sendCommand(client, "/plottp " + plotTpNumber);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static final File CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("ihanuat_config.json").toFile();
    // written once on first launch, never overwritten — source of truth for "reset to default"
    private static final File DEFAULTS_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("ihanuat_defaults.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Guards concurrent save() calls from different threads (game thread + worker thread). */
    private static final ReentrantLock SAVE_LOCK = new ReentrantLock();

    /** Writes the factory-default ConfigData to disk if the file does not yet exist. */
    public static void saveDefaultsIfAbsent() {
        if (DEFAULTS_FILE.exists()) return;
        try (FileWriter w = new FileWriter(DEFAULTS_FILE)) {
            GSON.toJson(new ConfigData(), w); // ConfigData field initialisers ARE the defaults
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Returns the stored default value for a named field from ihanuat_defaults.json.
     * Falls back to an empty string / "0" if the file is missing or the field is absent.
     */
    public static String getDefaultString(String field) {
        return readDefaultField(field, "");
    }
    public static String getDefaultInt(String field) {
        return readDefaultField(field, "0");
    }
    public static String getDefaultDouble(String field) {
        return readDefaultField(field, "0");
    }
    public static String getDefaultList(String field) {
        // returns newline-joined list, or empty string
        try {
            if (!DEFAULTS_FILE.exists()) return "";
            try (java.io.FileReader r = new java.io.FileReader(DEFAULTS_FILE)) {
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                if (!obj.has(field)) return "";
                com.google.gson.JsonElement el = obj.get(field);
                if (!el.isJsonArray()) return "";
                java.util.List<String> lines = new java.util.ArrayList<>();
                for (com.google.gson.JsonElement item : el.getAsJsonArray())
                    lines.add(item.getAsString());
                return String.join("\n", lines);
            }
        } catch (Exception e) { return ""; }
    }

    private static String readDefaultField(String field, String fallback) {
        try {
            if (!DEFAULTS_FILE.exists()) return fallback;
            try (java.io.FileReader r = new java.io.FileReader(DEFAULTS_FILE)) {
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                if (!obj.has(field)) return fallback;
                return obj.get(field).getAsString();
            }
        } catch (Exception e) { return fallback; }
    }

    public static void save() {
        if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs();
        if (!SOUNDS_DIR.exists()) SOUNDS_DIR.mkdirs();
        ConfigData d = new ConfigData();
        d.pestThreshold = pestThreshold;
        d.autoPestEnabled = autoPestEnabled;
        d.manualPestClean = manualPestClean;
        d.manualPestReturnDelay = manualPestReturnDelay;
        d.manualPestAlertSound = manualPestAlertSound;
        d.manualPestRewarpAt = manualPestRewarpAt;
        d.manualPestSoundPath = manualPestSoundPath;
        d.discoDestinationMode = discoDestinationMode;
        d.discoReturnDelay = discoReturnDelay;
        d.discoRewarpAt = discoRewarpAt;
        d.discoTpBeforeWardrobe = discoTpBeforeWardrobe;
        d.triggerPestOnChat = triggerPestOnChat;
        d.pestChatTriggerDelay = pestChatTriggerDelay;
        d.visitorThreshold = visitorThreshold;
        d.autoWardrobePest = autoWardrobePest;
        d.autoWardrobeVisitor = autoWardrobeVisitor;
        d.autoRodPestCd = autoRodPestCd;
        d.autoRodPestSpawn = autoRodPestSpawn;
        d.autoRodReturnToFarm = autoRodReturnToFarm;
        d.callPhillipForBonus = callPhillipForBonus;
        d.spraySinglePlot = spraySinglePlot;
        d.unflyMode = unflyMode;
        d.autoVisitor = autoVisitor;
        d.autoEquipment = autoEquipment;
        d.autoStashManager = autoStashManager;
        d.autoBookCombine = autoBookCombine;
        d.autoGeorgeSell = autoGeorgeSell;
        d.alwaysActiveCombine = alwaysActiveCombine;
        d.autoBoosterCookie = autoBoosterCookie;
        d.boosterCookieItems = new java.util.ArrayList<>(boosterCookieItems);
        d.customEnchantmentLevels = new java.util.ArrayList<>(customEnchantmentLevels);
        d.georgeSellThreshold = georgeSellThreshold;
        d.rotationTime = rotationTime;
        d.aotvToRoof = aotvToRoof;
        d.aotvRoofPitch = aotvRoofPitch;
        d.aotvRoofPitchHumanization = aotvRoofPitchHumanization;
        d.aotvRoofPlots = new java.util.ArrayList<>(aotvRoofPlots);
        d.wardrobeSlotFarming = wardrobeSlotFarming;
        d.wardrobeSlotPest = wardrobeSlotPest;
        d.wardrobeSlotVisitor = wardrobeSlotVisitor;
        d.guiClickDelay = guiClickDelay;
        d.autosellClickDelay = autosellClickDelay;
        d.equipmentSwapDelay = equipmentSwapDelay;
        d.rodSwapDelay = rodSwapDelay;
        d.postRodReturnDelay = postRodReturnDelay;
        d.bookCombineDelay = bookCombineDelay;
        d.wardrobePostSwapDelay = wardrobePostSwapDelay;
        d.setspawnCooldown = setspawnCooldown;
        d.wardrobeAotvDelay = wardrobeAotvDelay;
        d.aotvVacuumDelay = aotvVacuumDelay;
        d.bookThreshold = bookThreshold;
        d.additionalRandomDelay = additionalRandomDelay;
        d.restartScript = restartScript;
        d.restScriptingTimeMin = restScriptingTimeMin;
        d.restScriptingTimeMax = restScriptingTimeMax;
        d.restBreakTimeMin = restBreakTimeMin;
        d.restBreakTimeMax = restBreakTimeMax;
        d.superCraftBeforeRest = superCraftBeforeRest;
        d.superCraftCrops = new java.util.ArrayList<>(superCraftCrops);
        d.enablePlotTpRewarp = enablePlotTpRewarp;
        d.holdWUntilWall = holdWUntilWall;
        d.plotTpNumber = plotTpNumber;
        d.discordWebhookUrl = discordWebhookUrl;
        d.discordStatusUpdateTime = discordStatusUpdateTime;
        d.sendDiscordStatus = sendDiscordStatus;
        d.rewarpEndX = rewarpEndX;
        d.rewarpEndY = rewarpEndY;
        d.rewarpEndZ = rewarpEndZ;
        d.rewarpEndPosSet = rewarpEndPosSet;
        d.armorSwapVisitor = armorSwapVisitor;
        d.clickGuiPanelPositions = clickGuiPanelPositions;
        d.themePanelBg     = themePanelBg;
        d.themePanelHeader = themePanelHeader;
        d.themeAccent      = themeAccent;
        d.themeText        = themeText;
        d.themeTextDim     = themeTextDim;
        d.themeToggleOn    = themeToggleOn;
        d.themeToggleOff   = themeToggleOff;
        d.themeSliderFill  = themeSliderFill;
        d.themeButtonHover = themeButtonHover;
        d.themeTextStyle   = themeTextStyle;
        d.themeOutlineSize    = themeOutlineSize;
        d.themeShadowOpacity  = themeShadowOpacity;
        d.persistSessionTimer = persistSessionTimer;
        d.compactProfitCalculator = compactProfitCalculator;
        d.showProfitHudWhileInactive = showProfitHudWhileInactive;
        d.pricingMode = pricingMode;
        d.hideFilteredChat = hideFilteredChat;
        d.autoDropJunk = autoDropJunk;
        d.junkItems = new java.util.ArrayList<>(junkItems);
        d.dropJunkPlotTp = dropJunkPlotTp;
        d.junkThreshold = junkThreshold;
        d.showDebug = showDebug;
        d.logDebugToFile = logDebugToFile;
        d.autoRecoverUnexpectedDisconnect = autoRecoverUnexpectedDisconnect;
        d.autoResumeAfterDynamicRest = autoResumeAfterDynamicRest;
        d.guiOnlyInGarden = guiOnlyInGarden;
        d.breakBlocksBeforeAotv = breakBlocksBeforeAotv;
        d.delayPestForCropFever = delayPestForCropFever;
        d.quitThresholdHours = quitThresholdHours;
        d.forceQuitMinecraft = forceQuitMinecraft;
        d.quitAfterSessionLength = quitAfterSessionLength;
        d.quitThresholdAccumulatedMs = quitThresholdAccumulatedMs;
        d.language = language;
        d.petXpTrackedPets = new java.util.ArrayList<>(petXpTrackedPets);
        d.petTrackerList = new java.util.ArrayList<>(petTrackerList);
        d.chatRules = new java.util.ArrayList<>(chatRules);
        d.savedThemes = new java.util.ArrayList<>(savedThemes);
        d.hudX = hudX; d.hudY = hudY; d.hudScale = hudScale; d.showHud = showHud;
        d.showTotalToday = showTotalToday;
        d.showTotalFarmed = showTotalFarmed;
        d.sessionProfitHudX = sessionProfitHudX; d.sessionProfitHudY = sessionProfitHudY;
        d.sessionProfitHudScale = sessionProfitHudScale; d.showSessionProfitHud = showSessionProfitHud;
        d.lifetimeHudX = lifetimeHudX; d.lifetimeHudY = lifetimeHudY;
        d.lifetimeHudScale = lifetimeHudScale; d.showLifetimeHud = showLifetimeHud;
        d.lifetimeAccumulated = lifetimeAccumulated;
        d.todayDateStr = todayDateStr;
        d.todayAccumulatedMs = todayAccumulatedMs;
        d.hudBgColorHex           = toHexString(hudBgColor);
        d.hudAccentColorHex       = toHexString(hudAccentColor);
        d.hudTitleColorHex        = toHexString(hudTitleColor);
        d.hudLabelColorHex        = toHexString(hudLabelColor);
        d.hudValueColorHex        = toHexString(hudValueColor);
        d.hudBarBgColorHex        = toHexString(hudBarBgColor);
        d.hudBarFillColorHex      = toHexString(hudBarFillColor);
        d.hudStateOffColorHex     = toHexString(hudStateOffColor);
        d.hudStateFarmingColorHex     = toHexString(hudStateFarmingColor);
        d.hudStateCleaningColorHex    = toHexString(hudStateCleaningColor);
        d.hudStateRecoveringColorHex  = toHexString(hudStateRecoveringColor);
        d.hudStateVisitingColorHex    = toHexString(hudStateVisitingColor);
        d.hudStateAutosellingColorHex = toHexString(hudStateAutosellingColor);
        d.hudStateSprayingColorHex    = toHexString(hudStateSprayingColor);
        d.hudDynamicRestBgColor       = hudDynamicRestBgColor;
        SAVE_LOCK.lock();
        try (FileWriter w = new FileWriter(CONFIG_FILE)) { GSON.toJson(d, w); }
        catch (Exception e) { e.printStackTrace(); }
        finally { SAVE_LOCK.unlock(); }
    }

    public static void load() {
        if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs();
        if (!SOUNDS_DIR.exists()) SOUNDS_DIR.mkdirs();
        saveDefaultsIfAbsent(); // write defaults file once on first launch
        if (!CONFIG_FILE.exists()) { save(); return; }
        boolean shouldRewriteConfig = false;
        try (FileReader r = new FileReader(CONFIG_FILE)) {
            ConfigData d = GSON.fromJson(r, ConfigData.class);
            if (d == null) return;
            pestThreshold = d.pestThreshold;
            autoPestEnabled = d.autoPestEnabled;
            manualPestClean = d.manualPestClean;
            manualPestReturnDelay = Math.max(0, Math.min(10000, d.manualPestReturnDelay));
            manualPestAlertSound = d.manualPestAlertSound;
            manualPestRewarpAt = Math.max(0, Math.min(8, d.manualPestRewarpAt));
            manualPestSoundPath = d.manualPestSoundPath != null ? d.manualPestSoundPath : DEFAULT_MANUAL_PEST_SOUND_PATH;
            discoDestinationMode = d.discoDestinationMode;
            discoReturnDelay = Math.max(0, Math.min(10000, d.discoReturnDelay));
            discoRewarpAt = Math.max(0, Math.min(8, d.discoRewarpAt));
            discoTpBeforeWardrobe = d.discoTpBeforeWardrobe;
            triggerPestOnChat = d.triggerPestOnChat;
            pestChatTriggerDelay = d.pestChatTriggerDelay;
            visitorThreshold = d.visitorThreshold;
            autoWardrobePest = d.autoWardrobePest;
            autoWardrobeVisitor = d.autoWardrobeVisitor;
            autoRodPestCd = d.autoRodPestCd;
            autoRodPestSpawn = d.autoRodPestSpawn;
            autoRodReturnToFarm = d.autoRodReturnToFarm;
            callPhillipForBonus = d.callPhillipForBonus;
            spraySinglePlot = d.spraySinglePlot;
            unflyMode = d.unflyMode != null ? d.unflyMode : DEFAULT_UNFLY_MODE;
            autoVisitor = d.autoVisitor;
            autoEquipment = d.autoEquipment;
            autoStashManager = d.autoStashManager;
            autoBookCombine = d.autoBookCombine;
            autoGeorgeSell = d.autoGeorgeSell;
            alwaysActiveCombine = d.alwaysActiveCombine;
            autoBoosterCookie = d.autoBoosterCookie;
            if (d.boosterCookieItems != null) boosterCookieItems = new java.util.ArrayList<>(d.boosterCookieItems);
            if (d.customEnchantmentLevels != null) customEnchantmentLevels = new java.util.ArrayList<>(d.customEnchantmentLevels);
            georgeSellThreshold = d.georgeSellThreshold;
            rotationTime = d.rotationTime;
            aotvToRoof = d.aotvToRoof;
            aotvRoofPitch = Math.max(45, Math.min(90, d.aotvRoofPitch));
            aotvRoofPitchHumanization = Math.max(0, Math.min(15, d.aotvRoofPitchHumanization));
            if (d.aotvRoofPlots != null) aotvRoofPlots = new java.util.ArrayList<>(d.aotvRoofPlots);
            wardrobeSlotFarming = d.wardrobeSlotFarming;
            wardrobeSlotPest = d.wardrobeSlotPest;
            wardrobeSlotVisitor = d.wardrobeSlotVisitor;
            guiClickDelay = d.guiClickDelay;
            autosellClickDelay = d.autosellClickDelay;
            equipmentSwapDelay = d.equipmentSwapDelay;
            rodSwapDelay = d.rodSwapDelay;
            postRodReturnDelay = d.postRodReturnDelay;
            bookCombineDelay = d.bookCombineDelay;
            wardrobePostSwapDelay = Math.max(0, Math.min(2000, d.wardrobePostSwapDelay));
            setspawnCooldown = Math.max(250, Math.min(3000, d.setspawnCooldown));
            wardrobeAotvDelay = Math.max(0, Math.min(1000, d.wardrobeAotvDelay));
            aotvVacuumDelay = Math.max(0, Math.min(1000, d.aotvVacuumDelay));
            bookThreshold = d.bookThreshold;
            additionalRandomDelay = d.additionalRandomDelay;
            if (d.restartScript != null && !d.restartScript.isBlank()) restartScript = d.restartScript;
            if (d.restScriptingTimeMin != 0) restScriptingTimeMin = d.restScriptingTimeMin;
            if (d.restScriptingTimeMax != 0) restScriptingTimeMax = d.restScriptingTimeMax;
            if (d.restBreakTimeMin != 0) restBreakTimeMin = d.restBreakTimeMin;
            if (d.restBreakTimeMax != 0) restBreakTimeMax = d.restBreakTimeMax;
            // Legacy migration: if only the old base+offset fields were saved, derive min/max from them
            if (d.restScriptingTimeMin == 0 && d.restScriptingTimeMax == 0 && d.restScriptingTime != 0) {
                int off = d.restScriptingTimeOffset > 0 ? d.restScriptingTimeOffset : 0;
                restScriptingTimeMin = Math.max(1, d.restScriptingTime - off);
                restScriptingTimeMax = d.restScriptingTime + off;
            }
            if (d.restBreakTimeMin == 0 && d.restBreakTimeMax == 0 && d.restBreakTime != 0) {
                int off = d.restBreakTimeOffset > 0 ? d.restBreakTimeOffset : 0;
                restBreakTimeMin = Math.max(1, d.restBreakTime - off);
                restBreakTimeMax = d.restBreakTime + off;
            }
            superCraftBeforeRest = d.superCraftBeforeRest;
            if (d.superCraftCrops != null) superCraftCrops = new java.util.ArrayList<>(d.superCraftCrops);
            enablePlotTpRewarp = d.enablePlotTpRewarp;
            holdWUntilWall = d.holdWUntilWall;
            if (d.plotTpNumber != null) plotTpNumber = d.plotTpNumber;
            if (d.discordWebhookUrl != null) discordWebhookUrl = d.discordWebhookUrl;
            discordStatusUpdateTime = d.discordStatusUpdateTime;
            sendDiscordStatus = d.sendDiscordStatus;
            rewarpEndX = d.rewarpEndX; rewarpEndY = d.rewarpEndY; rewarpEndZ = d.rewarpEndZ;
            rewarpEndPosSet = d.rewarpEndPosSet;
            armorSwapVisitor = d.armorSwapVisitor;
            if (d.clickGuiPanelPositions != null) clickGuiPanelPositions = d.clickGuiPanelPositions;
            themePanelBg     = d.themePanelBg;
            themePanelHeader = d.themePanelHeader;
            themeAccent      = d.themeAccent;
            themeText        = d.themeText;
            themeTextDim     = d.themeTextDim;
            themeToggleOn    = d.themeToggleOn;
            themeToggleOff   = d.themeToggleOff;
            themeSliderFill  = d.themeSliderFill;
            themeButtonHover = d.themeButtonHover;
            if (d.themeTextStyle != null) themeTextStyle = d.themeTextStyle;
            themeOutlineSize   = Math.max(1, Math.min(3, d.themeOutlineSize > 0 ? d.themeOutlineSize : 1));
            themeShadowOpacity = Math.max(0, Math.min(255, d.themeShadowOpacity));
            persistSessionTimer = d.persistSessionTimer;
            compactProfitCalculator = d.compactProfitCalculator;
            showProfitHudWhileInactive = d.showProfitHudWhileInactive;
            pricingMode = d.pricingMode != null ? d.pricingMode : DEFAULT_PRICING_MODE;
            hideFilteredChat = d.hideFilteredChat;
            autoDropJunk = d.autoDropJunk;
            if (d.junkItems != null) junkItems = new java.util.ArrayList<>(d.junkItems);
            if (d.dropJunkPlotTp != null) dropJunkPlotTp = d.dropJunkPlotTp;
            junkThreshold = d.junkThreshold;
            showDebug = d.showDebug;
            logDebugToFile = d.logDebugToFile;
            if (!logDebugToFile) DebugLogger.getInstance().close();
            autoRecoverUnexpectedDisconnect = d.autoRecoverUnexpectedDisconnect;
            autoResumeAfterDynamicRest = d.autoResumeAfterDynamicRest;
            guiOnlyInGarden = d.guiOnlyInGarden;
            breakBlocksBeforeAotv = d.breakBlocksBeforeAotv;
            delayPestForCropFever = d.delayPestForCropFever;
            quitThresholdHours = Math.max(0.0, d.quitThresholdHours);
            forceQuitMinecraft = d.forceQuitMinecraft;
            quitAfterSessionLength = d.quitAfterSessionLength;
            quitThresholdAccumulatedMs = Math.max(0L, d.quitThresholdAccumulatedMs);
            language = d.language != null ? d.language : Language.ENGLISH;
            if (d.petXpTrackedPets != null) petXpTrackedPets = new java.util.ArrayList<>(d.petXpTrackedPets);
            if (d.petTrackerList != null) petTrackerList = new java.util.ArrayList<>(d.petTrackerList);
            if (d.chatRules != null) chatRules = new java.util.ArrayList<>(d.chatRules);
            if (d.savedThemes != null) savedThemes = new java.util.ArrayList<>(d.savedThemes);
            hudX = d.hudX; hudY = d.hudY;
            hudScale = d.hudScale > 0 ? d.hudScale : DEFAULT_HUD_SCALE;
            showHud = d.showHud;
            showTotalToday = d.showTotalToday;
            showTotalFarmed = d.showTotalFarmed;
            sessionProfitHudX = d.sessionProfitHudX; sessionProfitHudY = d.sessionProfitHudY;
            sessionProfitHudScale = d.sessionProfitHudScale > 0 ? d.sessionProfitHudScale : DEFAULT_SESSION_PROFIT_HUD_SCALE;
            showSessionProfitHud = d.showSessionProfitHud;
            lifetimeHudX = d.lifetimeHudX; lifetimeHudY = d.lifetimeHudY;
            lifetimeHudScale = d.lifetimeHudScale > 0 ? d.lifetimeHudScale : DEFAULT_LIFETIME_HUD_SCALE;
            showLifetimeHud = d.showLifetimeHud;
            lifetimeAccumulated = sanitizeLifetimeAccumulated(d.lifetimeAccumulated);
            shouldRewriteConfig = lifetimeAccumulated != Math.max(0L, d.lifetimeAccumulated);
            todayDateStr = d.todayDateStr != null ? d.todayDateStr : "";
            todayAccumulatedMs = Math.max(0, d.todayAccumulatedMs);
            hudBgColor           = parseHexColor(d.hudBgColorHex, DEFAULT_HUD_BG_COLOR);
            hudAccentColor       = parseHexColor(d.hudAccentColorHex, DEFAULT_HUD_ACCENT_COLOR);
            hudTitleColor        = parseHexColor(d.hudTitleColorHex, DEFAULT_HUD_TITLE_COLOR);
            hudLabelColor        = parseHexColor(d.hudLabelColorHex, DEFAULT_HUD_LABEL_COLOR);
            hudValueColor        = parseHexColor(d.hudValueColorHex, DEFAULT_HUD_VALUE_COLOR);
            hudBarBgColor        = parseHexColor(d.hudBarBgColorHex, DEFAULT_HUD_BAR_BG_COLOR);
            hudBarFillColor      = parseHexColor(d.hudBarFillColorHex, DEFAULT_HUD_BAR_FILL_COLOR);
            hudStateOffColor     = parseHexColor(d.hudStateOffColorHex, DEFAULT_HUD_STATE_OFF_COLOR);
            hudStateFarmingColor     = parseHexColor(d.hudStateFarmingColorHex, DEFAULT_HUD_STATE_FARMING_COLOR);
            hudStateCleaningColor    = parseHexColor(d.hudStateCleaningColorHex, DEFAULT_HUD_STATE_CLEANING_COLOR);
            hudStateRecoveringColor  = parseHexColor(d.hudStateRecoveringColorHex, DEFAULT_HUD_STATE_RECOVERING_COLOR);
            hudStateVisitingColor    = parseHexColor(d.hudStateVisitingColorHex, DEFAULT_HUD_STATE_VISITING_COLOR);
            hudStateAutosellingColor = parseHexColor(d.hudStateAutosellingColorHex, DEFAULT_HUD_STATE_AUTOSELLING_COLOR);
            hudStateSprayingColor    = parseHexColor(d.hudStateSprayingColorHex, DEFAULT_HUD_STATE_SPRAYING_COLOR);
            if (d.hudDynamicRestBgColor != 0) hudDynamicRestBgColor = d.hudDynamicRestBgColor;
        } catch (Exception e) { e.printStackTrace(); }
        if (shouldRewriteConfig) save();
    }

    // ── PetInfo ───────────────────────────────────────────────────────────────

    public static class PetInfo {
        private static final java.util.regex.Pattern NEW_FORMAT = java.util.regex.Pattern.compile(
                "^\\s*(.+?)\\s*,\\s*(100|200)\\s*,\\s*([\\d,]+)\\s*,\\s*([\\d,]+)\\s*,\\s*([A-Za-z]+)\\s*$");
        public String tag; public String name; public int maxLevel; public long level1Price; public long maxLevelPrice; public PetRarity rarity;
        public PetInfo(String config) {
            java.util.regex.Matcher m = NEW_FORMAT.matcher(config == null ? "" : config);
            if (m.matches()) {
                tag = "";
                name = cap(m.group(1).trim());
                try { maxLevel = Integer.parseInt(m.group(2).trim()); } catch (NumberFormatException e) { maxLevel = 100; }
                level1Price = parseCoins(m.group(3), 0L);
                maxLevelPrice = parseCoins(m.group(4), 0L);
                try { rarity = PetRarity.valueOf(m.group(5).trim().toUpperCase()); } catch (IllegalArgumentException e) { rarity = PetRarity.LEGENDARY; }
            } else {
                String[] p = config == null ? new String[0] : config.split(":");
                if (p.length >= 4) {
                    tag = p[0].trim();
                    name = cap(p[1].trim());
                    try { maxLevel = Integer.parseInt(p[2].trim()); } catch (NumberFormatException e) { maxLevel = 100; }
                    rarity = parseLegacyRarity(p[3]);
                } else {
                    tag = "";
                    name = "Rose Dragon";
                    maxLevel = 200;
                    rarity = PetRarity.LEGENDARY;
                }
                level1Price = 670_000_000L;
                maxLevelPrice = 1_250_000_000L;
            }
        }
        private static PetRarity parseLegacyRarity(String raw) {
            try { return PetRarity.valueOf(raw.trim().toUpperCase()); }
            catch (IllegalArgumentException e) { return PetRarity.LEGENDARY; }
        }
        private static long parseCoins(String raw, long fallback) {
            try { return Long.parseLong(raw.replace(",", "").trim()); }
            catch (NumberFormatException e) { return fallback; }
        }
        private String cap(String s) {
            if (s == null || s.isEmpty()) return s;
            StringBuilder sb = new StringBuilder();
            for (String w : s.split("\\s+")) if (!w.isEmpty()) { sb.append(Character.toUpperCase(w.charAt(0))); if (w.length()>1) sb.append(w.substring(1).toLowerCase()); sb.append(' '); }
            return sb.toString().trim();
        }
        @Override public String toString() {
            return String.format("%s, %d, %,d, %,d, %s", name, maxLevel, level1Price, maxLevelPrice, cap(rarity.name()));
        }
    }

    // ── ConfigData ────────────────────────────────────────────────────────────

    private static class ConfigData {
        int pestThreshold = DEFAULT_PEST_THRESHOLD;
        boolean autoPestEnabled = DEFAULT_AUTO_PEST_ENABLED;
        boolean manualPestClean = DEFAULT_MANUAL_PEST_CLEAN;
        int manualPestReturnDelay = DEFAULT_MANUAL_PEST_RETURN_DELAY;
        boolean manualPestAlertSound = DEFAULT_MANUAL_PEST_ALERT_SOUND;
        int manualPestRewarpAt = DEFAULT_MANUAL_PEST_REWARP_AT;
        String manualPestSoundPath = DEFAULT_MANUAL_PEST_SOUND_PATH;
        boolean discoDestinationMode = DEFAULT_DISCO_DESTINATION_MODE;
        int discoReturnDelay = DEFAULT_DISCO_RETURN_DELAY;
        int discoRewarpAt = DEFAULT_DISCO_REWARP_AT;
        boolean discoTpBeforeWardrobe = DEFAULT_DISCO_TP_BEFORE_WARDROBE;
        boolean triggerPestOnChat = DEFAULT_TRIGGER_PEST_ON_CHAT;
        int pestChatTriggerDelay = 0;
        int visitorThreshold = DEFAULT_VISITOR_THRESHOLD;
        boolean autoWardrobePest = DEFAULT_AUTO_WARDROBE_PEST;
        boolean autoWardrobeVisitor = DEFAULT_AUTO_WARDROBE_VISITOR;
        boolean autoRodPestCd = DEFAULT_AUTO_ROD_PEST_CD;
        boolean autoRodPestSpawn = DEFAULT_AUTO_ROD_PEST_SPAWN;
        boolean autoRodReturnToFarm = DEFAULT_AUTO_ROD_RETURN_TO_FARM;
        boolean callPhillipForBonus = DEFAULT_CALL_PHILLIP_FOR_BONUS;
        boolean spraySinglePlot = DEFAULT_SPRAY_SINGLE_PLOT;
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
        int autosellClickDelay = DEFAULT_AUTOSELL_CLICK_DELAY;
        int equipmentSwapDelay = DEFAULT_EQUIPMENT_SWAP_DELAY;
        int rodSwapDelay = DEFAULT_ROD_SWAP_DELAY;
        int postRodReturnDelay = 80;
        int bookCombineDelay = DEFAULT_BOOK_COMBINE_DELAY;
        int wardrobePostSwapDelay = DEFAULT_WARDROBE_POST_SWAP_DELAY;
        int setspawnCooldown = DEFAULT_SETSPAWN_COOLDOWN;
        int wardrobeAotvDelay = DEFAULT_WARDROBE_AOTV_DELAY;
        int aotvVacuumDelay = DEFAULT_AOTV_VACUUM_DELAY;
        int bookThreshold = DEFAULT_BOOK_THRESHOLD;
        int additionalRandomDelay = DEFAULT_ADDITIONAL_RANDOM_DELAY;
        String restartScript = DEFAULT_RESTART_SCRIPT;
        // Legacy fields kept for migration from old configs
        int restScriptingTime = 0;
        int restScriptingTimeOffset = 0;
        int restBreakTime = 0;
        int restBreakTimeOffset = 0;
        // Primary fields
        int restScriptingTimeMin = 0;
        int restScriptingTimeMax = 0;
        int restBreakTimeMin = 0;
        int restBreakTimeMax = 0;
        boolean superCraftBeforeRest = DEFAULT_SUPER_CRAFT_BEFORE_REST;
        java.util.List<String> superCraftCrops = new java.util.ArrayList<>(DEFAULT_SUPER_CRAFT_CROPS);
        boolean enablePlotTpRewarp = DEFAULT_ENABLE_PLOT_TP_REWARP;
        boolean holdWUntilWall = DEFAULT_HOLD_W_UNTIL_WALL;
        String plotTpNumber = DEFAULT_PLOT_TP_NUMBER;
        String discordWebhookUrl = DEFAULT_DISCORD_WEBHOOK_URL;
        int discordStatusUpdateTime = DEFAULT_DISCORD_STATUS_UPDATE_TIME;
        boolean sendDiscordStatus = DEFAULT_SEND_DISCORD_STATUS;
        double rewarpEndX = 0, rewarpEndY = 0, rewarpEndZ = 0;
        boolean rewarpEndPosSet = false;
        boolean armorSwapVisitor = false;
        int[][] clickGuiPanelPositions = new int[14][3];
        int themePanelBg     = 0xF0101018;
        int themePanelHeader = 0xFF18182C;
        int themeAccent      = 0xFF5050A0;
        int themeText        = 0xFFCCCCCC;
        int themeTextDim     = 0xFF666677;
        int themeToggleOn    = 0xFF4444BB;
        int themeToggleOff   = 0xFF2A2A3A;
        int themeSliderFill  = 0xFF3A3A99;
        int themeButtonHover = 0xFF4444BB;
        TextStyle themeTextStyle = TextStyle.NONE;
        int themeOutlineSize   = 1;
        int themeShadowOpacity = 180;
        boolean persistSessionTimer = DEFAULT_PERSIST_SESSION_TIMER;
        boolean compactProfitCalculator = DEFAULT_COMPACT_PROFIT_CALCULATOR;
        boolean showProfitHudWhileInactive = DEFAULT_SHOW_PROFIT_HUD_WHILE_INACTIVE;
        PricingMode pricingMode = DEFAULT_PRICING_MODE;
        boolean hideFilteredChat = DEFAULT_HIDE_FILTERED_CHAT;
        boolean autoDropJunk = DEFAULT_AUTO_DROP_JUNK;
        java.util.List<String> junkItems = new java.util.ArrayList<>(DEFAULT_JUNK_ITEMS);
        String dropJunkPlotTp = DEFAULT_DROP_JUNK_PLOT_TP;
        int junkThreshold = DEFAULT_JUNK_THRESHOLD;
        boolean showDebug = DEFAULT_SHOW_DEBUG;
        boolean logDebugToFile = DEFAULT_LOG_DEBUG_TO_FILE;
        boolean autoRecoverUnexpectedDisconnect = DEFAULT_AUTO_RECOVER_UNEXPECTED_DISCONNECT;
        boolean autoResumeAfterDynamicRest = DEFAULT_AUTO_RESUME_AFTER_DYNAMIC_REST;
        boolean guiOnlyInGarden = DEFAULT_GUI_ONLY_IN_GARDEN;
        boolean breakBlocksBeforeAotv = DEFAULT_BREAK_BLOCKS_BEFORE_AOTV;
        boolean delayPestForCropFever = DEFAULT_DELAY_PEST_FOR_CROP_FEVER;
        double quitThresholdHours = DEFAULT_QUIT_THRESHOLD_HOURS;
        boolean forceQuitMinecraft = DEFAULT_FORCE_QUIT_MINECRAFT;
        boolean quitAfterSessionLength = DEFAULT_QUIT_AFTER_SESSION_LENGTH;
        long quitThresholdAccumulatedMs = 0;
        Language language = Language.ENGLISH;
        java.util.List<String> petXpTrackedPets = new java.util.ArrayList<>(DEFAULT_PET_TRACKER_LIST);
        java.util.List<String> petTrackerList = new java.util.ArrayList<>(DEFAULT_PET_TRACKER_LIST);
        java.util.List<String> chatRules = new java.util.ArrayList<>(DEFAULT_CHAT_RULES);
        java.util.List<String> savedThemes = new java.util.ArrayList<>();
        int hudX = DEFAULT_HUD_X, hudY = DEFAULT_HUD_Y;
        float hudScale = DEFAULT_HUD_SCALE;
        boolean showHud = DEFAULT_SHOW_HUD;
        boolean showTotalToday = DEFAULT_SHOW_TOTAL_TODAY;
        boolean showTotalFarmed = DEFAULT_SHOW_TOTAL_FARMED;
        int sessionProfitHudX = DEFAULT_SESSION_PROFIT_HUD_X, sessionProfitHudY = DEFAULT_SESSION_PROFIT_HUD_Y;
        float sessionProfitHudScale = DEFAULT_SESSION_PROFIT_HUD_SCALE;
        boolean showSessionProfitHud = DEFAULT_SHOW_SESSION_PROFIT_HUD;
        int lifetimeHudX = DEFAULT_LIFETIME_HUD_X, lifetimeHudY = DEFAULT_LIFETIME_HUD_Y;
        float lifetimeHudScale = DEFAULT_LIFETIME_HUD_SCALE;
        boolean showLifetimeHud = DEFAULT_SHOW_LIFETIME_HUD;
        long lifetimeAccumulated = 0;
        String todayDateStr = "";
        long todayAccumulatedMs = 0;
        String hudBgColorHex           = toHexString(DEFAULT_HUD_BG_COLOR);
        String hudAccentColorHex       = toHexString(DEFAULT_HUD_ACCENT_COLOR);
        String hudTitleColorHex        = toHexString(DEFAULT_HUD_TITLE_COLOR);
        String hudLabelColorHex        = toHexString(DEFAULT_HUD_LABEL_COLOR);
        String hudValueColorHex        = toHexString(DEFAULT_HUD_VALUE_COLOR);
        String hudBarBgColorHex        = toHexString(DEFAULT_HUD_BAR_BG_COLOR);
        String hudBarFillColorHex      = toHexString(DEFAULT_HUD_BAR_FILL_COLOR);
        String hudStateOffColorHex     = toHexString(DEFAULT_HUD_STATE_OFF_COLOR);
        String hudStateFarmingColorHex     = toHexString(DEFAULT_HUD_STATE_FARMING_COLOR);
        String hudStateCleaningColorHex    = toHexString(DEFAULT_HUD_STATE_CLEANING_COLOR);
        String hudStateRecoveringColorHex  = toHexString(DEFAULT_HUD_STATE_RECOVERING_COLOR);
        String hudStateVisitingColorHex    = toHexString(DEFAULT_HUD_STATE_VISITING_COLOR);
        String hudStateAutosellingColorHex = toHexString(DEFAULT_HUD_STATE_AUTOSELLING_COLOR);
        String hudStateSprayingColorHex    = toHexString(DEFAULT_HUD_STATE_SPRAYING_COLOR);
        int hudDynamicRestBgColor = 0;
    }
}
