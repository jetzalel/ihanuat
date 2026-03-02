package com.ihanuat.mod.gui;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.modules.DynamicRestManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the macro status panel HUD.
 *
 * During gameplay:  rendered via HudRenderCallback (respects showHud config).
 * During inventory: always rendered in edit-mode so the user can drag/resize it.
 *
 * Layout:
 *   ┌───────────────────────┐
 *   │        Ihanuat        │  ← animated typewriter title
 *   ├───────────────────────┤
 *   │ macro state   farming │
 *   │ current session  0:00 │
 *   │ lifetime session 0:00 │
 *   │ next rest        0:00 │
 *   │ [====progress bar===] │
 *   └───────────────────────┘
 *
 * Drag to reposition, Ctrl+Drag to resize (scale).
 */
public class MacroHudRenderer {

    // ── Base layout (at scale = 1.0) ─────────────────────────────────────────
    static final int PANEL_W      = 190;
    private static final int PADDING_H    = 7;
    private static final int PADDING_V    = 5;
    private static final int FONT_H       = 9;
    private static final int ROW_HEIGHT   = 11;
    private static final int CORNER_RADIUS = 6;
    private static final int BAR_HEIGHT   = 4;
    private static final int BAR_INDENT   = PADDING_H * 2;

    // ── Colors (ARGB) ────────────────────────────────────────────────────────
    private static final int BG_COLOR          = 0xFF141424;
    private static final int SEP_COLOR         = 0xFF4A4A88;
    private static final int TITLE_COLOR       = 0xFFFFFFFF;
    private static final int LABEL_COLOR       = 0xFFAAAAAA;
    private static final int VALUE_COLOR       = 0xFFFFFFFF;
    private static final int STATE_FARMING     = 0xFF55FF55;
    private static final int STATE_CLEANING    = 0xFFFFAA00;
    private static final int STATE_RECOVERING  = 0xFFFF5555;
    private static final int BAR_BG_COLOR      = 0xFF1A1A32;
    private static final int BAR_FILL_COLOR    = 0xFF6464B4;

    // Edit-mode border colors
    private static final int BORDER_IDLE    = 0xFF6464B4;  // accent purple
    private static final int BORDER_DRAG    = 0xFFAAAAFF;  // bright blue while moving
    private static final int BORDER_RESIZE  = 0xFFFFAA00;  // orange while resizing

    // ── Title animation ───────────────────────────────────────────────────────
    private static final String TARGET_TITLE   = "Ihanuat";
    private static final char[] SCRAMBLE_CHARS = {'*', '/', '_', '\\', '|', '#', '!', '%', '&'};
    private static final int CHAR_INTERVAL_MS  = 90;
    private static final int SCRAMBLE_MS       = 70;
    private static final int STAY_MS           = 7000;

    private static long animPhaseStartMs = -1;
    private static int  animPhase        = 0; // 0=typing  1=staying  2=untyping

    // ── Drag / resize state ───────────────────────────────────────────────────
    private static boolean isDragging   = false;
    private static boolean isResizing   = false;
    private static int     dragOffsetX  = 0;
    private static int     dragOffsetY  = 0;
    private static float   resizeStartScale  = 1f;
    private static double  resizeStartMouseX = 0;

    // ── Registration ─────────────────────────────────────────────────────────

    public static void register() {
        HudRenderCallback.EVENT.register((guiGraphics, delta) -> {
            if (MacroConfig.showHud)
                render(guiGraphics, Minecraft.getInstance(), false);
        });
    }

    // ── Public API for ScreenEvents ──────────────────────────────────────────

    /** Call from inventory afterRender to show the panel in edit mode (respects showHud). */
    public static void renderInEditMode(GuiGraphics g, Minecraft client) {
        if (!MacroConfig.showHud) return;
        render(g, client, true);
    }

    /** True while a drag or resize gesture is in progress. */
    public static boolean isInteracting() {
        return isDragging || isResizing;
    }

    /** Returns true when screen-space (mouseX, mouseY) is over the HUD panel. */
    public static boolean isHovered(double mouseX, double mouseY) {
        float scale = MacroConfig.hudScale;
        double localX = (mouseX - MacroConfig.hudX) / scale;
        double localY = (mouseY - MacroConfig.hudY) / scale;
        return localX >= 0 && localX <= PANEL_W && localY >= 0 && localY <= panelH();
    }

    /**
     * Begin drag or resize.
     * @param ctrl  true → resize (Ctrl held), false → move
     */
    public static void startDrag(double mouseX, double mouseY, boolean ctrl) {
        if (ctrl) {
            isResizing = true;
            resizeStartScale  = MacroConfig.hudScale;
            resizeStartMouseX = mouseX;
        } else {
            isDragging   = true;
            dragOffsetX  = (int)(mouseX - MacroConfig.hudX);
            dragOffsetY  = (int)(mouseY - MacroConfig.hudY);
        }
    }

    /** Update position or scale during an active gesture. */
    public static void drag(double mouseX, double mouseY) {
        if (isDragging) {
            MacroConfig.hudX = (int)(mouseX - dragOffsetX);
            MacroConfig.hudY = (int)(mouseY - dragOffsetY);
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() != null) {
                int sw = mc.getWindow().getGuiScaledWidth();
                int sh = mc.getWindow().getGuiScaledHeight();
                float s = MacroConfig.hudScale;
                MacroConfig.hudX = Math.max(0, Math.min(MacroConfig.hudX, sw - (int)(PANEL_W * s)));
                MacroConfig.hudY = Math.max(0, Math.min(MacroConfig.hudY, sh - (int)(panelH() * s)));
            }
        } else if (isResizing) {
            double delta = mouseX - resizeStartMouseX;
            MacroConfig.hudScale = Math.max(0.5f, Math.min(2.5f, resizeStartScale + (float)(delta * 0.005)));
        }
    }

    /** End gesture and persist position/scale to config. */
    public static void endDrag() {
        if (isDragging || isResizing) {
            isDragging = false;
            isResizing = false;
            MacroConfig.save();
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    private static void render(GuiGraphics g, Minecraft client, boolean editMode) {
        if (client.player == null) return;

        MacroState.State state = MacroStateManager.getCurrentState();
        if (!editMode && state == MacroState.State.OFF) return;

        // ── Compute data ─────────────────────────────────────────────────────

        // When the macro is OFF (edit mode preview), show dummy "farming" state
        String stateStr   = "farming";
        int    stateColor = STATE_FARMING;
        if (state == MacroState.State.CLEANING) {
            stateStr = "cleaning"; stateColor = STATE_CLEANING;
        } else if (state == MacroState.State.RECOVERING) {
            stateStr = "recovering"; stateColor = STATE_RECOVERING;
        }

        long sessionMs  = (state != MacroState.State.OFF) ? MacroStateManager.getSessionRunningTime()  : 0;
        long lifetimeMs = (state != MacroState.State.OFF) ? MacroStateManager.getLifetimeRunningTime() : 0;

        long restTriggerMs = DynamicRestManager.getNextRestTriggerMs();
        String nextRestStr = restTriggerMs <= 0 ? "---"
                : formatTime(Math.max(0, restTriggerMs - System.currentTimeMillis()));

        int panelH = panelH();

        // ── Apply position + scale transform ─────────────────────────────────
        float scale = MacroConfig.hudScale;
        g.pose().pushMatrix();
        g.pose().translate(MacroConfig.hudX, MacroConfig.hudY);
        g.pose().scale(scale, scale);

        // ── Edit-mode border ─────────────────────────────────────────────────
        if (editMode) {
            int borderColor = isDragging ? BORDER_DRAG : isResizing ? BORDER_RESIZE : BORDER_IDLE;
            fillRoundedRect(g, -1, -1, PANEL_W + 2, panelH + 2, CORNER_RADIUS + 1, borderColor);
        }

        // ── Panel background ─────────────────────────────────────────────────
        fillRoundedRect(g, 0, 0, PANEL_W, panelH, CORNER_RADIUS, BG_COLOR);

        // ── Title (animated) ─────────────────────────────────────────────────
        int titleAnchorX = (PANEL_W - client.font.width(TARGET_TITLE)) / 2;
        int titleY = PADDING_V;
        g.drawString(client.font, getAnimatedTitle(), titleAnchorX, titleY, TITLE_COLOR, false);

        // ── Separator ────────────────────────────────────────────────────────
        int sepY = titleY + FONT_H + 3;
        g.fill(PADDING_H, sepY, PANEL_W - PADDING_H, sepY + 1, SEP_COLOR);

        // ── Rows ─────────────────────────────────────────────────────────────
        int rowY = sepY + 1 + 3;
        drawRow(g, client, rowY, "macro state",      stateStr, stateColor);
        rowY += ROW_HEIGHT;
        drawRow(g, client, rowY, "current session",  formatTime(sessionMs));
        rowY += ROW_HEIGHT;
        drawRow(g, client, rowY, "lifetime session", formatTime(lifetimeMs));
        rowY += ROW_HEIGHT;
        drawRow(g, client, rowY, "next rest",        nextRestStr);
        rowY += ROW_HEIGHT;

        // ── Progress bar ─────────────────────────────────────────────────────
        long scheduledMs = DynamicRestManager.getScheduledDurationMs();
        float progress = (scheduledMs > 0 && restTriggerMs > 0)
                ? (float)(scheduledMs - Math.max(0, restTriggerMs - System.currentTimeMillis())) / scheduledMs
                : 0f;
        drawProgressBar(g, BAR_INDENT, rowY, PANEL_W - BAR_INDENT * 2, BAR_HEIGHT,
                progress, BAR_BG_COLOR, BAR_FILL_COLOR);

        // ── Edit-mode hint ───────────────────────────────────────────────────
        if (editMode) {
            String hint = isDragging ? "moving..." : isResizing ? "resizing..." : "drag \u2022 ctrl+drag to resize";
            int hintX = (PANEL_W - client.font.width(hint)) / 2;
            g.drawString(client.font, hint, hintX, panelH + 3, LABEL_COLOR, false);
        }

        g.pose().popMatrix();
    }

    // ── Panel height helper ───────────────────────────────────────────────────

    // top-padding + title + gap + sep + gap + 4 rows + bar(h+3) + bottom-padding
    static int panelH() {
        return PADDING_V + FONT_H + 3 + 1 + 3 + 4 * ROW_HEIGHT + BAR_HEIGHT + 3 + PADDING_V;
    }

    // ── Title animation ───────────────────────────────────────────────────────

    private static String getAnimatedTitle() {
        long now = System.currentTimeMillis();

        if (animPhaseStartMs < 0) {
            animPhaseStartMs = now;
            animPhase = 0;
        }

        int n = TARGET_TITLE.length();
        long phaseDuration = (long)(n - 1) * CHAR_INTERVAL_MS + SCRAMBLE_MS;

        long elapsed = now - animPhaseStartMs;
        if      (animPhase == 0 && elapsed >= phaseDuration) { animPhase = 1; animPhaseStartMs = now; }
        else if (animPhase == 1 && elapsed >= STAY_MS)        { animPhase = 2; animPhaseStartMs = now; }
        else if (animPhase == 2 && elapsed >= phaseDuration)  { animPhase = 0; animPhaseStartMs = now; }
        elapsed = now - animPhaseStartMs;

        if (animPhase == 1) return TARGET_TITLE;

        StringBuilder sb = new StringBuilder();

        if (animPhase == 0) {
            for (int i = 0; i < n; i++) {
                long charStart = (long) i * CHAR_INTERVAL_MS;
                if (elapsed < charStart) break;
                if (elapsed < charStart + SCRAMBLE_MS) {
                    sb.append(SCRAMBLE_CHARS[(int)((elapsed - charStart) / 20) % SCRAMBLE_CHARS.length]);
                    break;
                }
                sb.append(TARGET_TITLE.charAt(i));
            }
        } else {
            for (int i = 0; i < n; i++) {
                long charStart = (long)(n - 1 - i) * CHAR_INTERVAL_MS;
                if (elapsed < charStart) {
                    sb.append(TARGET_TITLE.charAt(i));
                } else if (elapsed < charStart + SCRAMBLE_MS) {
                    sb.append(SCRAMBLE_CHARS[(int)((elapsed - charStart) / 20) % SCRAMBLE_CHARS.length]);
                    break;
                } else {
                    break;
                }
            }
        }

        return sb.toString();
    }

    // ── Draw helpers ─────────────────────────────────────────────────────────

    private static void drawRow(GuiGraphics g, Minecraft client,
                                int y, String label, String value) {
        drawRow(g, client, y, label, value, VALUE_COLOR);
    }

    private static void drawRow(GuiGraphics g, Minecraft client,
                                int y, String label, String value, int valueColor) {
        g.drawString(client.font, label, PADDING_H, y, LABEL_COLOR, false);
        int valueX = PANEL_W - PADDING_H - client.font.width(value);
        g.drawString(client.font, value, valueX, y, valueColor, false);
    }

    /**
     * Pill-shaped progress bar. Fill is clipped to the rounded trough on both
     * ends using the same circle math so no pixels bleed outside the background.
     */
    private static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h,
                                        float progress, int bgColor, int fillColor) {
        int r = h / 2;
        fillRoundedRect(g, x, y, w, h, r, bgColor);

        int fillW = Math.round(w * Math.max(0f, Math.min(1f, progress)));
        if (fillW <= 0) return;

        for (int row = 0; row < h; row++) {
            int indent = 0;
            if (row < r) {
                double d = r - row - 0.5;
                indent = (int) Math.ceil(r - 0.5 - Math.sqrt(r * r - d * d));
            } else if (row >= h - r) {
                double d = row - (h - r) + 0.5;
                indent = (int) Math.ceil(r - 0.5 - Math.sqrt(r * r - d * d));
            }
            int rowStart = x + indent;
            int rowEnd   = Math.min(x + fillW, x + w - indent);
            if (rowStart < rowEnd)
                g.fill(rowStart, y + row, rowEnd, y + row + 1, fillColor);
        }
    }

    /**
     * Filled rectangle with rounded corners, rendered row-by-row using circle math.
     */
    private static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        for (int row = 0; row < h; row++) {
            int indent = 0;
            if (row < r) {
                double d = r - row - 0.5;
                indent = (int) Math.ceil(r - 0.5 - Math.sqrt(r * r - d * d));
            } else if (row >= h - r) {
                double d = row - (h - r) + 0.5;
                indent = (int) Math.ceil(r - 0.5 - Math.sqrt(r * r - d * d));
            }
            g.fill(x + indent, y + row, x + w - indent, y + row + 1, color);
        }
    }

    private static String formatTime(long ms) {
        long totalSecs = ms / 1000;
        long hours = totalSecs / 3600;
        long mins  = (totalSecs % 3600) / 60;
        long secs  = totalSecs % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, mins, secs)
                : String.format("%02d:%02d", mins, secs);
    }
}
