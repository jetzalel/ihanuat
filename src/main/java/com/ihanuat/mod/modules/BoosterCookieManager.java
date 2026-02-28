package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public class BoosterCookieManager {
    public static volatile long interactionTime = 0;
    public static volatile int interactionStage = 0;

    private static final List<String> TARGET_ITEMS = Arrays.asList(
            "atmospheric filter", "squeaky toy", "beady eyes", "clipped wings", "overclocker",
            "mantid claw", "flowering bouquet", "bookworm", "chirping stereo", "firefly",
            "capsule", "vinyl");

    public static void handleBoosterCookieMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!MacroConfig.autoBoosterCookie || screen == null || client.player == null)
            return;

        long now = System.currentTimeMillis();
        if (now - interactionTime < MacroConfig.guiClickDelay)
            return;

        String title = screen.getTitle().getString();
        String lowerTitle = title.toLowerCase();

        if (!lowerTitle.contains("booster cookie"))
            return;

        // Stage 0: Search inventory and click matching items
        if (interactionStage == 0) {
            int foundSlotIdx = -1;

            int totalSlots = screen.getMenu().slots.size();
            int inventoryStart = totalSlots - 36;

            for (int i = inventoryStart; i < totalSlots; i++) {
                Slot slot = screen.getMenu().slots.get(i);
                if (!slot.hasItem())
                    continue;

                ItemStack stack = slot.getItem();
                String name = stack.getHoverName().getString().replaceAll("(?i)§.", "").toLowerCase();

                for (String target : TARGET_ITEMS) {
                    if (name.contains(target)) {
                        foundSlotIdx = i;
                        break;
                    }
                }
                if (foundSlotIdx != -1)
                    break;
            }

            if (foundSlotIdx != -1) {
                client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, foundSlotIdx, 0,
                        ClickType.QUICK_MOVE, client.player);
                interactionTime = now;
            }
        }
    }
}
