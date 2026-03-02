package com.ihanuat.mod.gui;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.modules.ProfitManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Map;

/**
 * Renders the Profit Tracker HUD(s).
 * Supports both Session and Lifetime modes.
 */
public class ProfitHudRenderer {

    static final int PANEL_W = 230;
    private static final int PADDING_H = 7;
    private static final int PADDING_V = 5;
    private static final int FONT_H = 9;
    private static final int ROW_HEIGHT = 11;
    private static final int CORNER_RADIUS = 6;

    private static final int BG_COLOR = 0xFF141424;
    private static final int SEP_COLOR = 0xFF4A4A88;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int BORDER_IDLE = 0xFF6464B4;
    private static final int BORDER_DRAG = 0xFFAAAAFF;
    private static final int BORDER_RESIZE = 0xFFFFAA00;

    // Drag / resize state for Session HUD
    private static boolean isDraggingSession = false;
    private static boolean isResizingSession = false;
    private static int dragOffsetXSession = 0;
    private static int dragOffsetYSession = 0;
    private static float resizeStartScaleSession = 1f;
    private static double resizeStartMouseXSession = 0;

    // Drag / resize state for Lifetime HUD
    private static boolean isDraggingLifetime = false;
    private static boolean isResizingLifetime = false;
    private static int dragOffsetXLifetime = 0;
    private static int dragOffsetYLifetime = 0;
    private static float resizeStartScaleLifetime = 1f;
    private static double resizeStartMouseXLifetime = 0;

    public static void register() {
        HudRenderCallback.EVENT.register((guiGraphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (MacroConfig.showSessionProfitHud) {
                render(guiGraphics, client, false, false);
            }
            if (MacroConfig.showLifetimeHud) {
                render(guiGraphics, client, true, false);
            }
        });
    }

    public static void renderInEditMode(GuiGraphics g, Minecraft client) {
        if (MacroConfig.showSessionProfitHud) {
            render(g, client, false, true);
        }
        if (MacroConfig.showLifetimeHud) {
            render(g, client, true, true);
        }
    }

    public static boolean isInteracting() {
        return isDraggingSession || isResizingSession || isDraggingLifetime || isResizingLifetime;
    }

    public static boolean isHovered(double mouseX, double mouseY) {
        if (MacroConfig.showSessionProfitHud && isHoveredInternal(mouseX, mouseY, false))
            return true;
        if (MacroConfig.showLifetimeHud && isHoveredInternal(mouseX, mouseY, true))
            return true;
        return false;
    }

    private static boolean isHoveredInternal(double mouseX, double mouseY, boolean lifetime) {
        float scale = lifetime ? MacroConfig.lifetimeHudScale : MacroConfig.sessionProfitHudScale;
        int x = lifetime ? MacroConfig.lifetimeHudX : MacroConfig.sessionProfitHudX;
        int y = lifetime ? MacroConfig.lifetimeHudY : MacroConfig.sessionProfitHudY;

        double localX = (mouseX - x) / scale;
        double localY = (mouseY - y) / scale;
        return localX >= 0 && localX <= PANEL_W && localY >= 0 && localY <= panelH(lifetime);
    }

    public static void startDrag(double mouseX, double mouseY, boolean ctrl) {
        if (isHoveredInternal(mouseX, mouseY, true)) {
            if (ctrl) {
                isResizingLifetime = true;
                resizeStartScaleLifetime = MacroConfig.lifetimeHudScale;
                resizeStartMouseXLifetime = mouseX;
            } else {
                isDraggingLifetime = true;
                dragOffsetXLifetime = (int) (mouseX - MacroConfig.lifetimeHudX);
                dragOffsetYLifetime = (int) (mouseY - MacroConfig.lifetimeHudY);
            }
        } else if (isHoveredInternal(mouseX, mouseY, false)) {
            if (ctrl) {
                isResizingSession = true;
                resizeStartScaleSession = MacroConfig.sessionProfitHudScale;
                resizeStartMouseXSession = mouseX;
            } else {
                isDraggingSession = true;
                dragOffsetXSession = (int) (mouseX - MacroConfig.sessionProfitHudX);
                dragOffsetYSession = (int) (mouseY - MacroConfig.sessionProfitHudY);
            }
        }
    }

    public static void drag(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        if (isDraggingSession) {
            MacroConfig.sessionProfitHudX = (int) (mouseX - dragOffsetXSession);
            MacroConfig.sessionProfitHudY = (int) (mouseY - dragOffsetYSession);
            float s = MacroConfig.sessionProfitHudScale;
            MacroConfig.sessionProfitHudX = Math.max(0,
                    Math.min(MacroConfig.sessionProfitHudX, sw - (int) (PANEL_W * s)));
            MacroConfig.sessionProfitHudY = Math.max(0,
                    Math.min(MacroConfig.sessionProfitHudY, sh - (int) (panelH(false) * s)));
        } else if (isResizingSession) {
            double delta = mouseX - resizeStartMouseXSession;
            MacroConfig.sessionProfitHudScale = Math.max(0.5f,
                    Math.min(2.5f, resizeStartScaleSession + (float) (delta * 0.005)));
        } else if (isDraggingLifetime) {
            MacroConfig.lifetimeHudX = (int) (mouseX - dragOffsetXLifetime);
            MacroConfig.lifetimeHudY = (int) (mouseY - dragOffsetYLifetime);
            float s = MacroConfig.lifetimeHudScale;
            MacroConfig.lifetimeHudX = Math.max(0, Math.min(MacroConfig.lifetimeHudX, sw - (int) (PANEL_W * s)));
            MacroConfig.lifetimeHudY = Math.max(0, Math.min(MacroConfig.lifetimeHudY, sh - (int) (panelH(true) * s)));
        } else if (isResizingLifetime) {
            double delta = mouseX - resizeStartMouseXLifetime;
            MacroConfig.lifetimeHudScale = Math.max(0.5f,
                    Math.min(2.5f, resizeStartScaleLifetime + (float) (delta * 0.005)));
        }
    }

    public static void endDrag() {
        if (isDraggingSession || isResizingSession || isDraggingLifetime || isResizingLifetime) {
            isDraggingSession = false;
            isResizingSession = false;
            isDraggingLifetime = false;
            isResizingLifetime = false;
            MacroConfig.save();
        }
    }

    private static void render(GuiGraphics g, Minecraft client, boolean lifetime, boolean editMode) {
        if (client.player == null)
            return;

        int x = lifetime ? MacroConfig.lifetimeHudX : MacroConfig.sessionProfitHudX;
        int y = lifetime ? MacroConfig.lifetimeHudY : MacroConfig.sessionProfitHudY;
        float scale = lifetime ? MacroConfig.lifetimeHudScale : MacroConfig.sessionProfitHudScale;
        int panelH = panelH(lifetime);

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        // Border in edit mode
        if (editMode) {
            boolean dragging = lifetime ? isDraggingLifetime : isDraggingSession;
            boolean resizing = lifetime ? isResizingLifetime : isResizingSession;
            int borderColor = dragging ? BORDER_DRAG : resizing ? BORDER_RESIZE : BORDER_IDLE;
            fillRoundedRect(g, -1, -1, PANEL_W + 2, panelH + 2, CORNER_RADIUS + 1, borderColor);
        }

        fillRoundedRect(g, 0, 0, PANEL_W, panelH, CORNER_RADIUS, BG_COLOR);

        String title = lifetime ? "Lifetime Profit" : "Session Profit";
        int titleAnchorX = (PANEL_W - client.font.width(title)) / 2;
        g.drawString(client.font, title, titleAnchorX, PADDING_V, TITLE_COLOR, false);

        int rowY = PADDING_V + FONT_H + 3;
        g.fill(PADDING_H, rowY, PANEL_W - PADDING_H, rowY + 1, SEP_COLOR);
        rowY += 4;

        if (MacroConfig.compactProfitCalculator) {
            Map<String, Long> compactDrops = ProfitManager.getCompactDrops(lifetime);
            for (Map.Entry<String, Long> entry : compactDrops.entrySet()) {
                if (entry.getValue() > 0) {
                    drawRow(g, client, rowY, entry.getKey(), formatProfit(entry.getValue()), 0xFFFFFF55);
                    rowY += ROW_HEIGHT;
                }
            }
        } else {
            Map<String, Integer> drops = ProfitManager.getActiveDrops(lifetime);
            for (Map.Entry<String, Integer> entry : drops.entrySet()) {
                String itemName = entry.getKey();
                int count = entry.getValue();
                long price = ProfitManager.getItemPrice(itemName);
                long lineProfit = price * count;

                String labelText = itemName + " (x" + String.format("%,d", count) + ")";
                String valueText = formatProfit(lineProfit);

                int color = ProfitManager.isPredefinedTrackedItem(itemName) ? 0xFFFFFF55 : VALUE_COLOR;
                drawRow(g, client, rowY, labelText, valueText, color);
                rowY += ROW_HEIGHT;
            }
        }

        // Total Profit row
        if (rowY > PADDING_V + FONT_H + 3 + 4) {
            g.fill(PADDING_H, rowY + 1, PANEL_W - PADDING_H, rowY + 2, SEP_COLOR);
            rowY += 4;
            long total = ProfitManager.getTotalProfit(lifetime);
            drawRow(g, client, rowY, "Total Profit", formatProfit(total), 0xFFFFAA00);
        }

        g.pose().popMatrix();
    }

    private static int panelH(boolean lifetime) {
        int baseH = PADDING_V + FONT_H + 3 + 4;
        int itemCount = 0;
        if (MacroConfig.compactProfitCalculator) {
            Map<String, Long> compactDrops = ProfitManager.getCompactDrops(lifetime);
            itemCount = (int) compactDrops.values().stream().filter(v -> v > 0).count();
        } else {
            itemCount = ProfitManager.getActiveDrops(lifetime).size();
        }

        if (itemCount > 0) {
            baseH += itemCount * ROW_HEIGHT + 4 + ROW_HEIGHT + PADDING_V;
        } else {
            baseH += ROW_HEIGHT + PADDING_V; // Show at least one empty row or just the title
        }
        return baseH;
    }

    private static void drawRow(GuiGraphics g, Minecraft client, int y, String label, String value, int valueColor) {
        g.drawString(client.font, label, PADDING_H, y, LABEL_COLOR, false);
        int valueX = PANEL_W - PADDING_H - client.font.width(value);
        g.drawString(client.font, value, valueX, y, valueColor, false);
    }

    private static String formatProfit(long amount) {
        return String.format("%,d", amount);
    }

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
}
