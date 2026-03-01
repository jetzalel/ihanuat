package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import com.ihanuat.mod.util.EnchantmentUtils;

public class BookCombineManager {
    public static volatile long interactionTime = 0;
    public static volatile int interactionStage = 0;

    /** Pre-computed slot indices for the current pair being combined. */
    private static volatile int pendingSlot0 = -1;
    private static volatile int pendingSlot1 = -1;

    public static void handleAnvilMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!MacroConfig.autoBookCombine || screen == null || client.player == null)
            return;

        long now = System.currentTimeMillis();
        if (now - interactionTime < MacroConfig.bookCombineDelay)
            return;

        String title = screen.getTitle().getString();
        if (!title.toLowerCase().contains("anvil"))
            return;

        int totalSlots = screen.getMenu().slots.size();
        if (totalSlots < 54)
            return;

        // Stage 0: Scan inventory, find a combinable pair. Pre-store BOTH slot indices,
        // then click the first one. We do NOT re-scan in later stages.
        if (interactionStage == 0) {
            Map<String, List<Integer>> bookPairs = getInventoryBooks(screen);

            for (Map.Entry<String, List<Integer>> entry : bookPairs.entrySet()) {
                String key = entry.getKey();
                List<Integer> slots = entry.getValue();

                if (slots.size() < 2)
                    continue;

                if (isMaxLevel(key))
                    continue;

                // Lock in both slot indices before touching anything
                pendingSlot0 = slots.get(0);
                pendingSlot1 = slots.get(1);

                client.player.displayClientMessage(
                        Component.literal("§a[BookCombine] Combining '" + key
                                + "' (slots " + pendingSlot0 + " + " + pendingSlot1 + ")"),
                        true);

                client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, pendingSlot0, 0,
                        ClickType.QUICK_MOVE, client.player);
                interactionTime = now;
                interactionStage = 1;
                return;
            }

            // No combinable pairs — nothing to do
            pendingSlot0 = -1;
            pendingSlot1 = -1;
        }
        // Stage 1: Click the pre-stored second book (same type guaranteed since both
        // slots were frozen in stage 0 before any clicking)
        else if (interactionStage == 1) {
            if (pendingSlot1 == -1) {
                interactionStage = 0;
                return;
            }
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, pendingSlot1, 0,
                    ClickType.QUICK_MOVE, client.player);
            interactionTime = now;
            interactionStage = 2;
        }
        // Stage 2: Pick up the combined result from the output slot (slot 22)
        else if (interactionStage == 2) {
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 22, 0, ClickType.PICKUP,
                    client.player);
            interactionTime = now;
            interactionStage = 3;
        }
        // Stage 3: Place the result back in inventory
        else if (interactionStage == 3) {
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 22, 0, ClickType.PICKUP,
                    client.player);
            interactionTime = now;
            interactionStage = 0;
            pendingSlot0 = -1;
            pendingSlot1 = -1;
        }
    }

    /**
     * Scans the player's inventory section of the open container and returns a map
     * from unique book key → list of container slot indices.
     *
     * Key is the first non-empty lore line of the book (e.g. "Sharpness VI"),
     * which is unique per enchantment type AND level in Hypixel Skyblock 1.21.
     */
    private static Map<String, List<Integer>> getInventoryBooks(AbstractContainerScreen<?> screen) {
        Map<String, List<Integer>> pairs = new LinkedHashMap<>();
        int totalSlots = screen.getMenu().slots.size();
        int inventoryStart = totalSlots - 36;

        for (int i = inventoryStart; i < totalSlots; i++) {
            Slot slot = screen.getMenu().slots.get(i);
            if (!slot.hasItem())
                continue;

            ItemStack stack = slot.getItem();
            if (!stack.getItem().toString().toLowerCase().contains("enchanted_book"))
                continue;

            String key = getBookKey(stack);
            if (key == null)
                continue;

            pairs.computeIfAbsent(key, k -> new ArrayList<>()).add(slot.index);
        }
        return pairs;
    }

    /**
     * Returns a unique key for this enchanted book. Uses the first non-empty
     * lore line, which in Hypixel Skyblock 1.21 is the enchantment name + level
     * (e.g. "Sharpness VI"). Falls back to hover name if no lore is present.
     * Returns null if no usable key can be determined.
     */
    private static String getBookKey(ItemStack stack) {
        // Primary: first non-empty lore line (unique per enchant+level in SB 1.21)
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (net.minecraft.network.chat.Component line : lore.lines()) {
                String text = line.getString().replaceAll("(?i)§.", "").trim();
                if (!text.isEmpty())
                    return text;
            }
        }

        // Fallback: hover name (not level-unique for vanilla enchanted books, but
        // better than nothing)
        String hover = stack.getHoverName().getString().replaceAll("(?i)§.", "").trim();
        return hover.isEmpty() ? null : hover;
    }

    /**
     * Returns true if the key represents a book that should not be combined
     * further.
     * Parses the enchantment name and level from the key and checks against
     * the known max levels for each enchantment.
     */
    private static boolean isMaxLevel(String key) {
        int lastSpace = key.lastIndexOf(' ');
        String name;
        String levelStr;

        if (lastSpace == -1) {
            name = key;
            levelStr = "1";
        } else {
            String suffix = key.substring(lastSpace + 1).trim();
            // Check if suffix is a valid Roman numeral or numeric string
            if (suffix.matches("^[IVXLCDM]+$") || suffix.matches("^[0-9]+$")) {
                name = key.substring(0, lastSpace).trim();
                levelStr = suffix;
            } else {
                name = key;
                levelStr = "1";
            }
        }

        int currentLevel = EnchantmentUtils.parseLevel(levelStr);
        int maxLevel = EnchantmentUtils.getMaxLevel(name);

        return currentLevel >= maxLevel;
    }
}
