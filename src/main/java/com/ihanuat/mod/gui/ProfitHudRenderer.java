package com.ihanuat.mod.gui;

import java.util.Map;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.modules.ProfitManager;
import com.ihanuat.mod.util.ClientUtils;
import com.ihanuat.mod.MacroState;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the Profit Tracker HUD(s).
 * Supports both Session and Lifetime modes.
 */
public class ProfitHudRenderer {

    static final int PANEL_W = 280;
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

    // Drag / resize state for Daily HUD
    private static boolean isDraggingDaily = false;
    private static boolean isResizingDaily = false;
    private static int dragOffsetXDaily = 0;
    private static int dragOffsetYDaily = 0;
    private static float resizeStartScaleDaily = 1f;
    private static double resizeStartMouseXDaily = 0;

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
            if (MacroConfig.guiOnlyInGarden && ClientUtils.getCurrentLocation(client) != MacroState.Location.GARDEN) {
                return;
            }
            boolean running = MacroStateManager.isMacroRunning();
            boolean showAny = running || MacroConfig.showProfitHudWhileInactive;

            if (showAny) {
                if (MacroConfig.showSessionProfitHud) {
                    render(guiGraphics, client, "session", false);
                }
                if (MacroConfig.showDailyHud) {
                    render(guiGraphics, client, "daily", false);
                }
                if (MacroConfig.showLifetimeHud) {
                    render(guiGraphics, client, "lifetime", false);
                }
            }
        });
    }

    public static void renderInEditMode(GuiGraphics g, Minecraft client) {
        if (MacroConfig.showSessionProfitHud) {
            render(g, client, "session", true);
        }
        if (MacroConfig.showDailyHud) {
            render(g, client, "daily", true);
        }
        if (MacroConfig.showLifetimeHud) {
            render(g, client, "lifetime", true);
        }
    }

    public static boolean isInteracting() {
        return isDraggingSession || isResizingSession || isDraggingDaily || isResizingDaily
                || isDraggingLifetime || isResizingLifetime;
    }

    public static boolean isHovered(double mouseX, double mouseY) {
        if (MacroConfig.showSessionProfitHud && isHoveredInternal(mouseX, mouseY, "session"))
            return true;
        if (MacroConfig.showDailyHud && isHoveredInternal(mouseX, mouseY, "daily"))
            return true;
        if (MacroConfig.showLifetimeHud && isHoveredInternal(mouseX, mouseY, "lifetime"))
            return true;
        return false;
    }

    private static boolean isHoveredInternal(double mouseX, double mouseY, String mode) {
        float scale;
        int x, y;
        
        if ("daily".equals(mode)) {
            scale = MacroConfig.dailyHudScale;
            x = MacroConfig.dailyHudX;
            y = MacroConfig.dailyHudY;
        } else if ("lifetime".equals(mode)) {
            scale = MacroConfig.lifetimeHudScale;
            x = MacroConfig.lifetimeHudX;
            y = MacroConfig.lifetimeHudY;
        } else {
            scale = MacroConfig.sessionProfitHudScale;
            x = MacroConfig.sessionProfitHudX;
            y = MacroConfig.sessionProfitHudY;
        }

        double localX = (mouseX - x) / scale;
        double localY = (mouseY - y) / scale;
        return localX >= 0 && localX <= PANEL_W && localY >= 0 && localY <= panelH(mode);
    }

    public static void startDrag(double mouseX, double mouseY, boolean ctrl) {
        if (isHoveredInternal(mouseX, mouseY, "lifetime")) {
            if (ctrl) {
                isResizingLifetime = true;
                resizeStartScaleLifetime = MacroConfig.lifetimeHudScale;
                resizeStartMouseXLifetime = mouseX;
            } else {
                isDraggingLifetime = true;
                dragOffsetXLifetime = (int) (mouseX - MacroConfig.lifetimeHudX);
                dragOffsetYLifetime = (int) (mouseY - MacroConfig.lifetimeHudY);
            }
        } else if (isHoveredInternal(mouseX, mouseY, "daily")) {
            if (ctrl) {
                isResizingDaily = true;
                resizeStartScaleDaily = MacroConfig.dailyHudScale;
                resizeStartMouseXDaily = mouseX;
            } else {
                isDraggingDaily = true;
                dragOffsetXDaily = (int) (mouseX - MacroConfig.dailyHudX);
                dragOffsetYDaily = (int) (mouseY - MacroConfig.dailyHudY);
            }
        } else if (isHoveredInternal(mouseX, mouseY, "session")) {
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
                    Math.min(MacroConfig.sessionProfitHudY, sh - (int) (panelH("session") * s)));
        } else if (isResizingSession) {
            double delta = mouseX - resizeStartMouseXSession;
            MacroConfig.sessionProfitHudScale = Math.max(0.5f,
                    Math.min(2.5f, resizeStartScaleSession + (float) (delta * 0.005)));
        } else if (isDraggingDaily) {
            MacroConfig.dailyHudX = (int) (mouseX - dragOffsetXDaily);
            MacroConfig.dailyHudY = (int) (mouseY - dragOffsetYDaily);
            float s = MacroConfig.dailyHudScale;
            MacroConfig.dailyHudX = Math.max(0, Math.min(MacroConfig.dailyHudX, sw - (int) (PANEL_W * s)));
            MacroConfig.dailyHudY = Math.max(0, Math.min(MacroConfig.dailyHudY, sh - (int) (panelH("daily") * s)));
        } else if (isResizingDaily) {
            double delta = mouseX - resizeStartMouseXDaily;
            MacroConfig.dailyHudScale = Math.max(0.5f,
                    Math.min(2.5f, resizeStartScaleDaily + (float) (delta * 0.005)));
        } else if (isDraggingLifetime) {
            MacroConfig.lifetimeHudX = (int) (mouseX - dragOffsetXLifetime);
            MacroConfig.lifetimeHudY = (int) (mouseY - dragOffsetYLifetime);
            float s = MacroConfig.lifetimeHudScale;
            MacroConfig.lifetimeHudX = Math.max(0, Math.min(MacroConfig.lifetimeHudX, sw - (int) (PANEL_W * s)));
            MacroConfig.lifetimeHudY = Math.max(0, Math.min(MacroConfig.lifetimeHudY, sh - (int) (panelH("lifetime") * s)));
        } else if (isResizingLifetime) {
            double delta = mouseX - resizeStartMouseXLifetime;
            MacroConfig.lifetimeHudScale = Math.max(0.5f,
                    Math.min(2.5f, resizeStartScaleLifetime + (float) (delta * 0.005)));
        }
    }

    public static void endDrag() {
        if (isDraggingSession || isResizingSession || isDraggingDaily || isResizingDaily
                || isDraggingLifetime || isResizingLifetime) {
            isDraggingSession = false;
            isResizingSession = false;
            isDraggingDaily = false;
            isResizingDaily = false;
            isDraggingLifetime = false;
            isResizingLifetime = false;
            MacroConfig.save();
        }
    }

    private static void render(GuiGraphics g, Minecraft client, String mode, boolean editMode) {
        if (client.player == null)
            return;

        int x, y;
        float scale;
        
        if ("daily".equals(mode)) {
            x = MacroConfig.dailyHudX;
            y = MacroConfig.dailyHudY;
            scale = MacroConfig.dailyHudScale;
        } else if ("lifetime".equals(mode)) {
            x = MacroConfig.lifetimeHudX;
            y = MacroConfig.lifetimeHudY;
            scale = MacroConfig.lifetimeHudScale;
        } else {
            x = MacroConfig.sessionProfitHudX;
            y = MacroConfig.sessionProfitHudY;
            scale = MacroConfig.sessionProfitHudScale;
        }
        
        int panelH = panelH(mode);

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        // Border in edit mode
        if (editMode) {
            boolean dragging, resizing;
            if ("daily".equals(mode)) {
                dragging = isDraggingDaily;
                resizing = isResizingDaily;
            } else if ("lifetime".equals(mode)) {
                dragging = isDraggingLifetime;
                resizing = isResizingLifetime;
            } else {
                dragging = isDraggingSession;
                resizing = isResizingSession;
            }
            int borderColor = dragging ? BORDER_DRAG : resizing ? BORDER_RESIZE : BORDER_IDLE;
            fillRoundedRect(g, -1, -1, PANEL_W + 2, panelH + 2, CORNER_RADIUS + 1, borderColor);
        }

        fillRoundedRect(g, 0, 0, PANEL_W, panelH, CORNER_RADIUS, BG_COLOR);

        String title;
        if ("daily".equals(mode)) {
            title = "Daily Session Profit";
        } else if ("lifetime".equals(mode)) {
            title = "Lifetime Profit";
        } else {
            title = "Session Profit";
        }
        
        int titleAnchorX = (PANEL_W - client.font.width(title)) / 2;
        g.drawString(client.font, title, titleAnchorX, PADDING_V, TITLE_COLOR, false);

        int rowY = PADDING_V + FONT_H + 3;
        g.fill(PADDING_H, rowY, PANEL_W - PADDING_H, rowY + 1, SEP_COLOR);
        rowY += 4;

        if (MacroConfig.compactProfitCalculator) {
            Map<String, Long> compactDrops = ProfitManager.getCompactDrops(mode);
            for (Map.Entry<String, Long> entry : compactDrops.entrySet()) {
                if (entry.getValue() != 0) {
                    String label = ProfitManager.getCompactCategoryLabel(entry.getKey());
                    int valColor = entry.getKey().equals("Costs") ? 0xFFFF5555 : 0xFFFFFF55;
                    drawRow(g, client, rowY, label, formatProfit(entry.getValue()), valColor);
                    rowY += ROW_HEIGHT;
                }
            }
        } else {
            Map<String, Long> drops = ProfitManager.getActiveDrops(mode);
            for (Map.Entry<String, Long> entry : drops.entrySet()) {
                String itemName = entry.getKey();
                long count = entry.getValue();
                double price = ProfitManager.getItemPrice(itemName);
                long lineProfit = (long) (price * count);

                String categorizedName = ProfitManager.getCategorizedName(itemName);
                // Pet XP: show XP total rather than an item count
                String countDisplay;
                if (itemName.equals("[Spray] Sprayonator")) {
                    long sprayQty = ProfitManager.getSprayQuantity(mode);
                    countDisplay = "x" + String.format("%,d", sprayQty);
                } else if (itemName.startsWith("Pet XP (")) {
                    countDisplay = String.format("%,d XP", count);
                } else {
                    countDisplay = "x" + String.format("%,d", count);
                }
                String labelText = categorizedName + " §r(" + countDisplay + ")";
                String valueText = formatProfit(lineProfit);

                int color;
                if (itemName.equals("[Visitor] Visitor Cost") || itemName.equals("[Spray] Sprayonator")) {
                    color = 0xFFFF5555; // red for costs
                } else if (itemName.startsWith("[Visitor] ")) {
                    color = 0xFFFFFF55; // yellow for visitor gains
                } else {
                    color = ProfitManager.isPredefinedTrackedItem(itemName) ? 0xFFFFFF55 : VALUE_COLOR;
                }
                drawRow(g, client, rowY, labelText, valueText, color);
                rowY += ROW_HEIGHT;
            }
        }

        // Total Profit row
        if (rowY > PADDING_V + FONT_H + 3 + 4) {
            g.fill(PADDING_H, rowY + 1, PANEL_W - PADDING_H, rowY + 2, SEP_COLOR);
            rowY += 4;
            long total = ProfitManager.getTotalProfit(mode);
            drawRow(g, client, rowY, "Total Profit", formatProfit(total), 0xFFFFAA00);
            rowY += ROW_HEIGHT;

            // Session only: Coins per Hour
            if ("session".equals(mode)) {
                long sessionMs = com.ihanuat.mod.MacroStateManager.getSessionRunningTime();
                long cph = 0;
                if (sessionMs > 0) {
                    double hours = sessionMs / 3600000.0;
                    cph = (long) (total / hours);
                }
                drawRow(g, client, rowY, "Coins per Hour", formatProfit(cph), 0xFF55FFFF);
            }
        }

        g.pose().popMatrix();
    }

    private static int panelH(String mode) {
        int baseH = PADDING_V + FONT_H + 3 + 4;
        int itemCount = 0;
        if (MacroConfig.compactProfitCalculator) {
            Map<String, Long> compactDrops = ProfitManager.getCompactDrops(mode);
            itemCount = (int) compactDrops.values().stream().filter(v -> v != 0).count();
        } else {
            itemCount = ProfitManager.getActiveDrops(mode).size();
        }

        if (itemCount > 0) {
            int extraRows = "session".equals(mode) ? 1 : 0; // Session HUD has CPH row
            baseH += itemCount * ROW_HEIGHT + 4 + ROW_HEIGHT + (extraRows * ROW_HEIGHT) + PADDING_V;
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
