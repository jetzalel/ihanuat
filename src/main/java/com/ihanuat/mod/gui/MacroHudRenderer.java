package com.ihanuat.mod.gui;

import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.modules.DynamicRestManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the macro status panel HUD in the top-left corner of the screen.
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
 */
public class MacroHudRenderer {

    // ── Layout ───────────────────────────────────────────────────────────────
    private static final int PANEL_W      = 190;
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

    // ── Title animation ───────────────────────────────────────────────────────
    private static final String TARGET_TITLE   = "Ihanuat";
    private static final char[] SCRAMBLE_CHARS = {'*', '/', '_', '\\', '|', '#', '!', '%', '&'};
    private static final int CHAR_INTERVAL_MS  = 90;  // ms between each char starting
    private static final int SCRAMBLE_MS       = 70;  // ms each char scrambles before settling
    private static final int STAY_MS           = 7000; // ms the full title stays visible

    private static long animPhaseStartMs = -1;
    private static int  animPhase        = 0; // 0=typing  1=staying  2=untyping

    // ── Registration ─────────────────────────────────────────────────────────

    public static void register() {
        HudRenderCallback.EVENT.register((guiGraphics, delta) ->
                render(guiGraphics, Minecraft.getInstance()));
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    public static void render(GuiGraphics g, Minecraft client) {
        if (client.player == null) return;

        MacroState.State state = MacroStateManager.getCurrentState();
        if (state == MacroState.State.OFF) return;

        // ── Compute data ─────────────────────────────────────────────────────

        long sessionMs  = MacroStateManager.getSessionRunningTime();
        long lifetimeMs = MacroStateManager.getLifetimeRunningTime();

        long restTriggerMs = DynamicRestManager.getNextRestTriggerMs();
        String nextRestStr = restTriggerMs <= 0 ? "---"
                : formatTime(Math.max(0, restTriggerMs - System.currentTimeMillis()));

        String stateStr;
        int stateColor;
        switch (state) {
            case CLEANING:   stateStr = "cleaning";   stateColor = STATE_CLEANING;   break;
            case RECOVERING: stateStr = "recovering"; stateColor = STATE_RECOVERING; break;
            default:         stateStr = "farming";    stateColor = STATE_FARMING;    break;
        }

        // ── Layout ───────────────────────────────────────────────────────────

        // top-padding + title + gap + sep + gap + 4 rows + bar(h+3) + bottom-padding
        int panelH = PADDING_V + FONT_H + 3 + 1 + 3 + 4 * ROW_HEIGHT + BAR_HEIGHT + 3 + PADDING_V;
        int x = 10, y = 10;

        fillRoundedRect(g, x, y, PANEL_W, panelH, CORNER_RADIUS, BG_COLOR);

        // ── Title (animated) ─────────────────────────────────────────────────
        // Anchor X is fixed to the full title width so the text doesn't shift while typing
        int titleAnchorX = x + (PANEL_W - client.font.width(TARGET_TITLE)) / 2;
        int titleY = y + PADDING_V;
        g.drawString(client.font, getAnimatedTitle(), titleAnchorX, titleY, TITLE_COLOR, false);

        // ── Separator ────────────────────────────────────────────────────────
        int sepY = titleY + FONT_H + 3;
        g.fill(x + PADDING_H, sepY, x + PANEL_W - PADDING_H, sepY + 1, SEP_COLOR);

        // ── Rows ─────────────────────────────────────────────────────────────
        int rowY = sepY + 1 + 3;
        drawRow(g, client, x, rowY, "macro state",      stateStr, stateColor);
        rowY += ROW_HEIGHT;
        drawRow(g, client, x, rowY, "current session",  formatTime(sessionMs));
        rowY += ROW_HEIGHT;
        drawRow(g, client, x, rowY, "lifetime session", formatTime(lifetimeMs));
        rowY += ROW_HEIGHT;
        drawRow(g, client, x, rowY, "next rest",        nextRestStr);
        rowY += ROW_HEIGHT;

        // ── Progress bar ─────────────────────────────────────────────────────
        long scheduledMs = DynamicRestManager.getScheduledDurationMs();
        float progress = (scheduledMs > 0 && restTriggerMs > 0)
                ? (float)(scheduledMs - Math.max(0, restTriggerMs - System.currentTimeMillis())) / scheduledMs
                : 0f;
        drawProgressBar(g, x + BAR_INDENT, rowY, PANEL_W - BAR_INDENT * 2, BAR_HEIGHT,
                progress, BAR_BG_COLOR, BAR_FILL_COLOR);
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

        // Advance phase
        long elapsed = now - animPhaseStartMs;
        if      (animPhase == 0 && elapsed >= phaseDuration) { animPhase = 1; animPhaseStartMs = now; }
        else if (animPhase == 1 && elapsed >= STAY_MS)        { animPhase = 2; animPhaseStartMs = now; }
        else if (animPhase == 2 && elapsed >= phaseDuration)  { animPhase = 0; animPhaseStartMs = now; }
        elapsed = now - animPhaseStartMs;

        if (animPhase == 1) return TARGET_TITLE;

        StringBuilder sb = new StringBuilder();

        if (animPhase == 0) {
            // Type left → right: each char scrambles then settles
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
            // Untype right → left: each char scrambles then disappears
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
                                int panelX, int y, String label, String value) {
        drawRow(g, client, panelX, y, label, value, VALUE_COLOR);
    }

    private static void drawRow(GuiGraphics g, Minecraft client,
                                int panelX, int y, String label, String value, int valueColor) {
        g.drawString(client.font, label, panelX + PADDING_H, y, LABEL_COLOR, false);
        int valueX = panelX + PANEL_W - PADDING_H - client.font.width(value);
        g.drawString(client.font, value, valueX, y, valueColor, false);
    }

    /**
     * Pill-shaped progress bar. The fill is clipped to the rounded trough on both
     * ends using the same circle math, so no pixels bleed outside the background.
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
