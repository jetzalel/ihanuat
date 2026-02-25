package com.ihanuat.mod.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class DynamicRestScreen extends Screen {
    private final long restEndTimeMs;
    private boolean reconnecting = false;

    public DynamicRestScreen(long restEndTimeMs) {
        super(Component.literal("Dynamic Rest"));
        this.restEndTimeMs = restEndTimeMs;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        long remaining = Math.max(0, restEndTimeMs - System.currentTimeMillis());
        long seconds = (remaining / 1000) % 60;
        long minutes = (remaining / 1000) / 60;

        String text = String.format("Dynamic Rest: %02d:%02d remaining", minutes, seconds);
        if (remaining <= 0)
            text = "Reconnecting soon...";

        graphics.drawCenteredString(this.font, text, this.width / 2, this.height / 2, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        if (System.currentTimeMillis() >= restEndTimeMs && !reconnecting) {
            reconnecting = true;
            // Reconnect logic will be handled by ReconnectScheduler
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
