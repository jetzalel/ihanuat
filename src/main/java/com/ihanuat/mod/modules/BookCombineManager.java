package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;
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

    public static volatile boolean isCombining = false;
    public static volatile boolean isPreparingToCombine = false;

    public static volatile long lastCombineCallTime = 0;
    private static final long COMBINE_RECALL_DELAY_MS = 1000;

    public static void reset() {
        isCombining = false;
        isPreparingToCombine = false;
        interactionStage = 0;
        interactionTime = 0;
        pendingSlot0 = -1;
        pendingSlot1 = -1;
    }

    public static void handleAnvilMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!MacroConfig.autoBookCombine || screen == null || client.player == null)
            return;

        String rawTitle = screen.getTitle().getString();
        String title = rawTitle.replaceAll("(?i)§.", "").toLowerCase();

        if (!isCombining && !isPreparingToCombine) {
            // Failsafe: if Anvil GUI is open but we aren't combining, close it if we are
            // farming
            if (title.contains("anvil") && com.ihanuat.mod.MacroStateManager.isMacroRunning() &&
                    com.ihanuat.mod.MacroStateManager.getCurrentState() == com.ihanuat.mod.MacroState.State.FARMING) {

                int bookCount = countBooksInInventory(client);
                if (bookCount < MacroConfig.bookThreshold) {
                    client.player.displayClientMessage(
                            Component.literal("§c[Ihanuat] Unexpected Anvil GUI detected. Restarting script..."),
                            false);
                    client.player.closeContainer();

                    MacroWorkerThread.getInstance().submit("BookCombine-UnexpectedGUI-Restart", () -> {
                        if (MacroWorkerThread.shouldAbortTask(client, com.ihanuat.mod.MacroState.State.FARMING))
                            return;
                        com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
                        MacroWorkerThread.sleep(1000);
                        if (MacroWorkerThread.shouldAbortTask(client, com.ihanuat.mod.MacroState.State.FARMING))
                            return;
                        ClientUtils.sendDebugMessage(client, "Restarting script after unexpected Anvil GUI closure");
                        com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
                    });
                }
            }
            return;
        }

        if (!MacroConfig.alwaysActiveCombine && !isCombining)
            return;

        long now = System.currentTimeMillis();
        if (now - interactionTime < MacroConfig.getRandomizedDelay(MacroConfig.bookCombineDelay))
            return;

        if (!title.contains("anvil"))
            return;

        int totalSlots = screen.getMenu().slots.size();
        if (totalSlots < 54)
            return;

        // Stage 0: Search for a pair to combine
        if (interactionStage == 0) {
            Map<String, List<Integer>> bookPairs = getInventoryBooks(screen);

            for (Map.Entry<String, List<Integer>> entry : bookPairs.entrySet()) {
                String key = entry.getKey();
                List<Integer> slots = entry.getValue();

                if (slots.size() < 2)
                    continue;

                if (isMaxLevel(key))
                    continue;

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

            // No more pairs - finish
            finishCombining(client);
        }
        // Stage 1: Add the second book
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
        // Stage 2: Click the Anvil item (slot 22) to combine
        else if (interactionStage == 2) {
            Slot outputSlot = screen.getMenu().slots.get(22);
            if (outputSlot != null && outputSlot.hasItem()) {
                String itemName = outputSlot.getItem().getHoverName().getString().toLowerCase();
                String itemId = outputSlot.getItem().getItem().toString().toLowerCase();

                if (itemName.contains("combine") || itemId.contains("anvil")) {
                    client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 22, 0, ClickType.PICKUP,
                            client.player);
                    interactionTime = now;
                    interactionStage = 3;
                }
            }
        }
        // Stage 3: Click the Sign item (slot 22) to claim and reset to Stage 0
        else if (interactionStage == 3) {
            Slot outputSlot = screen.getMenu().slots.get(22);
            if (outputSlot != null && outputSlot.hasItem()) {
                String itemName = outputSlot.getItem().getHoverName().getString().toLowerCase();
                String itemId = outputSlot.getItem().getItem().toString().toLowerCase();

                if (itemName.contains("claim") || itemId.contains("sign")) {
                    client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 22, 0, ClickType.PICKUP,
                            client.player);
                    interactionTime = now;
                    interactionStage = 0; // Restart loop within GUI
                    pendingSlot0 = -1;
                    pendingSlot1 = -1;
                }
            }
        }
    }

    private static boolean isPriorityEventActive(Minecraft client) {
        return com.ihanuat.mod.MacroStateManager.getCurrentState() != com.ihanuat.mod.MacroState.State.FARMING
                || PestManager.isCleaningInProgress
                || PestPrepSwapManager.prepSwappedForCurrentPestCycle
                || VisitorManager.getVisitorCount(client) >= MacroConfig.visitorThreshold
                || WardrobeManager.isSwappingWardrobe
                || EquipmentManager.isSwappingEquipment
                || GeorgeManager.isSelling
                || GeorgeManager.isPreparingToSell
                || JunkManager.isDropping
                || JunkManager.isPreparingToDrop;
    }

    public static void update(Minecraft client) {
        if (!MacroConfig.autoBookCombine || client.player == null)
            return;

        if (isPreparingToCombine) {
            if (isPriorityEventActive(client)) {
                isPreparingToCombine = false;
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Aborting Book Combine prep due to priority event."), false);
            }
            return;
        }

        if (isCombining) {
            if (isPriorityEventActive(client)) {
                isCombining = false;
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Aborting Book Combine due to priority event."), false);
                return;
            }

            // If GUI closed but we were supposed to be combining and still have pairs
            if (client.screen == null) {
                if (interactionStage > 0) {
                    interactionStage = 0;
                }

                long now = System.currentTimeMillis();
                if (now - interactionTime > 1500 && now - lastCombineCallTime > COMBINE_RECALL_DELAY_MS) {
                    int bookCount = countBooksInInventory(client);
                    if (bookCount >= 2) {
                        client.player.displayClientMessage(
                                Component.literal(
                                        "§7GUI closed, but " + (bookCount / 2) + " pairs remain. Re-opening anvil..."),
                                false);
                        interactionTime = now;
                        interactionStage = 0;
                        lastCombineCallTime = now;
                        com.ihanuat.mod.util.ClientUtils.sendCommand(client, "/anvil", true);
                    } else {
                        finishCombining(client);
                    }
                }
            }
            return;
        }

        if (isPriorityEventActive(client)) {
            return;
        }

        if (JunkManager.countJunkItems(client) >= MacroConfig.junkThreshold) {
            return;
        }

        int bookCount = countBooksInInventory(client);
        if (bookCount >= MacroConfig.bookThreshold) {
            triggerAutomaticCombine(client, bookCount);
        } else if (bookCount >= 2) {
            // Debug: Log why we are skipping
            if (System.currentTimeMillis() - interactionTime > 60000) { // Only log once per minute to avoid spam
                ClientUtils.sendDebugMessage(client, "BookCombine: Skipping trigger. Pairs found: " + bookCount
                        + " < threshold: " + MacroConfig.bookThreshold);
                interactionTime = System.currentTimeMillis();
            }
        }
    }

    private static void triggerAutomaticCombine(Minecraft client, int count) {
        client.player.displayClientMessage(
                Component.literal("§6§lIhanuat >> §7Auto Combining books (" + count + " books in inventory)..."),
                false);

        com.ihanuat.mod.util.ClientUtils.forceReleaseKeys(client);

        isPreparingToCombine = true;
        isCombining = false;

        MacroWorkerThread.getInstance().submit("BookCombine-Trigger", () -> {
            try {
                if (MacroWorkerThread.shouldAbortTask(client, com.ihanuat.mod.MacroState.State.FARMING)) {
                    isPreparingToCombine = false;
                    return;
                }
                ClientUtils.sendDebugMessage(client, "Stopping script: Preparing book combine");
                com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);

                boolean success = true;
                for (int i = 0; i < 20; i++) {
                    MacroWorkerThread.sleep(50);
                    if (MacroWorkerThread.shouldAbortTask(client, com.ihanuat.mod.MacroState.State.FARMING)) {
                        success = false;
                        break;
                    }
                    if (!isPreparingToCombine) {
                        success = false;
                        break;
                    }
                    if (com.ihanuat.mod.MacroStateManager
                            .getCurrentState() != com.ihanuat.mod.MacroState.State.FARMING) {
                        success = false;
                        break;
                    }
                }

                if (success && isPreparingToCombine) {
                    isPreparingToCombine = false;
                    isCombining = true;
                    interactionStage = 0;
                    interactionTime = System.currentTimeMillis();
                    lastCombineCallTime = interactionTime;
                    // Use forced client-side command to allow other mods to intercept /av
                    com.ihanuat.mod.util.ClientUtils.sendCommand(client, "/anvil", true);
                } else {
                    isPreparingToCombine = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void finishCombining(Minecraft client) {
        isCombining = false;

        if (client.player != null && client.screen != null) {
            client.execute(() -> client.player.closeContainer());
        }

        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("§6Book Combine finished. Resuming script..."), true);
        }

        if (com.ihanuat.mod.MacroStateManager.getCurrentState() == com.ihanuat.mod.MacroState.State.FARMING) {
            MacroWorkerThread.getInstance().submit("BookCombine-Finish", () -> {
                if (MacroWorkerThread.shouldAbortTask(client, com.ihanuat.mod.MacroState.State.FARMING))
                    return;
                // Wait for GUI to fully close
                long guiWait = System.currentTimeMillis();
                while (client.screen != null && System.currentTimeMillis() - guiWait < 3000)
                    MacroWorkerThread.sleep(50);
                MacroWorkerThread.sleep(300);
                if (MacroWorkerThread.shouldAbortTask(client, com.ihanuat.mod.MacroState.State.FARMING))
                    return;

                client.execute(() -> {
                    GearManager.swapToFarmingTool(client);
                    ClientUtils.sendDebugMessage(client,
                            "Starting farming script after book combine: " + MacroConfig.getFullRestartCommand());
                    com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
                });
            });
        }
    }

    private static int countBooksInInventory(Minecraft client) {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem().toString().toLowerCase().contains("enchanted_book")) {
                if (!isExemptBook(stack)) {
                    String key = getBookKey(stack);
                    if (key != null && !isMaxLevel(key)) {
                        counts.put(key, counts.getOrDefault(key, 0) + 1);
                    }
                }
            }
        }

        int totalInPairs = 0;
        for (int count : counts.values()) {
            if (count >= 2) {
                totalInPairs += count;
            }
        }
        return totalInPairs;
    }

    private static boolean isExemptBook(ItemStack stack) {
        String name = stack.getHoverName().getString().toLowerCase();
        if (name.contains("sunder") || name.contains("pesterminator 5") || name.contains("pesterminator v")) {
            return true;
        }

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                String text = line.getString().toLowerCase();
                if (text.contains("sunder") || text.contains("pesterminator 5") || text.contains("pesterminator v")) {
                    return true;
                }
            }
        }
        return false;
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

            if (isExemptBook(stack))
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
