package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.mixin.AccessorInventory;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GearManager {
    public static volatile boolean isSwappingWardrobe = false;
    public static volatile long wardrobeInteractionTime = 0;
    public static volatile int wardrobeInteractionStage = 0;
    public static volatile int wardrobeCleanupTicks = 0;
    public static volatile int trackedWardrobeSlot = -1;
    public static volatile int targetWardrobeSlot = -1;

    public static volatile boolean isSwappingEquipment = false;
    public static volatile int equipmentInteractionStage = 0;
    public static volatile long equipmentInteractionTime = 0;
    public static volatile boolean swappingToFarmingGear = false;
    public static volatile int equipmentTargetIndex = 0;
    public static volatile Boolean trackedIsPestGear = null;

    public static volatile boolean shouldRestartFarmingAfterSwap = false;
    public static volatile long wardrobeOpenPendingTime = 0;
    public static volatile boolean isHoldingRodUse = false;

    public static void reset() {
        isSwappingWardrobe = false;
        isSwappingEquipment = false;
        shouldRestartFarmingAfterSwap = false;
        wardrobeCleanupTicks = 0;
        trackedWardrobeSlot = -1;
        trackedIsPestGear = null;
        wardrobeGuiDetected = false;
        equipmentGuiDetected = false;
    }

    public static boolean wardrobeGuiDetected = false;
    public static boolean equipmentGuiDetected = false;

    public static void triggerPrepSwap(Minecraft client) {
        isHoldingRodUse = false;
    }

    public static void triggerWardrobeSwap(Minecraft client, int slot) {
        if (trackedWardrobeSlot == slot) {
            ClientUtils.sendDebugMessage(client, "Stopping script: Wardrobe already on target slot, restarting");
            com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
            new Thread(() -> {
                try {
                    Thread.sleep(400);
                    client.execute(() -> GearManager.swapToFarmingTool(client));
                    Thread.sleep(250);
                    ClientUtils.sendDebugMessage(client,
                            "Starting farming script after wardrobe swap: " + MacroConfig.getFullRestartCommand());
                    com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
                } catch (Exception ignored) {
                }
            }).start();
            return;
        }

        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        if (isSwappingEquipment) {
            isSwappingEquipment = false;
            ClientUtils.sendDebugMessage(client, "§eInterrupted equipment swap for wardrobe priority.");
        }
        wardrobeGuiDetected = false;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        shouldRestartFarmingAfterSwap = true;
        ClientUtils.sendDebugMessage(client, "Stopping script: Triggering wardrobe swap to slot " + slot);
        com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
        new Thread(() -> {
            try {
                Thread.sleep(375);
                client.execute(() -> ClientUtils.sendCommand(client, "/wardrobe"));
                ClientUtils.waitForWardrobeGui(client);
            } catch (Exception ignored) {
            }
        }).start();
    }

    public static void ensureWardrobeSlot(Minecraft client, int slot) {
        if (trackedWardrobeSlot == slot)
            return;
        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        if (isSwappingEquipment) {
            isSwappingEquipment = false;
            ClientUtils.sendDebugMessage(client, "§eInterrupted equipment swap for wardrobe priority.");
        }
        wardrobeGuiDetected = false;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        ClientUtils.sendCommand(client, "/wardrobe");
    }

    public static void handleWardrobeMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingWardrobe || targetWardrobeSlot == -1)
            return;

        long now = System.currentTimeMillis();
        if (now - wardrobeInteractionTime < MacroConfig.getRandomizedDelay(MacroConfig.guiClickDelay))
            return;

        String title = screen.getTitle().getString().toLowerCase();
        if (!title.contains("wardrobe"))
            return;

        // Validate that the wardrobe is actually functional before marking as detected
        int slotIdx = 35 + targetWardrobeSlot;
        if (slotIdx >= screen.getMenu().slots.size()) {
            // Wardrobe slots don't exist yet, GUI is not ready
            return;
        }

        Slot slot = screen.getMenu().slots.get(slotIdx);
        ItemStack stack = slot.getItem();

        // If the slot item is still loading (gray dye/empty), GUI is not ready yet
        if (stack.isEmpty() || stack.getItem().toString().toLowerCase().contains("air")
                || stack.getItem().toString().toLowerCase().contains("gray_dye")
                || stack.getHoverName().getString().toLowerCase().contains("gray dye")) {
            // Wardrobe GUI open but data not loaded yet
            ClientUtils.sendDebugMessage(client, "Wardrobe GUI open but data not loaded yet (gray dye/empty detected)");
            return;
        }

        // Only now mark as detected - we've validated the wardrobe is functional
        if (!wardrobeGuiDetected) {
            wardrobeGuiDetected = true;
            ClientUtils.sendDebugMessage(client, "Wardrobe GUI detected AND VALIDATED as functional");
            wardrobeInteractionTime = System.currentTimeMillis();
        }

        if (wardrobeInteractionStage == 0) {
            String itemName = stack.getItem().toString().toLowerCase();
            String hoverName = stack.getHoverName().getString().toLowerCase();

            // Check if already active (Green Dye means equipped)
            if (itemName.contains("green_dye") || hoverName.contains("green dye") || itemName.contains("lime_dye")
                    || hoverName.contains("lime dye")) {
                client.player.displayClientMessage(
                        Component.literal("§aWardrobe Slot " + targetWardrobeSlot + " is already active."), true);
                trackedWardrobeSlot = targetWardrobeSlot;
                isSwappingWardrobe = false;
                client.player.closeContainer();
                handleWardrobeCompletion(client);
                return;
            }

            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0, ClickType.PICKUP,
                    client.player);
            wardrobeInteractionTime = now;
            wardrobeInteractionStage = 1;
        } else if (wardrobeInteractionStage == 1) {
            long lastClickElapsed = now - wardrobeInteractionTime;
            if (lastClickElapsed < 150)
                return;

            int confirmSlotIdx = 35 + targetWardrobeSlot;
            if (confirmSlotIdx >= screen.getMenu().slots.size())
                return;

            ItemStack confirmStack = screen.getMenu().slots.get(confirmSlotIdx).getItem();
            if (confirmStack.isEmpty())
                return;

            String itemName = confirmStack.getItem().toString().toLowerCase();
            String hoverName = confirmStack.getHoverName().getString().toLowerCase();

            if (itemName.contains("green_dye") || hoverName.contains("green dye") || itemName.contains("lime_dye")
                    || hoverName.contains("lime dye")) {
                ClientUtils.sendDebugMessage(client, "Wardrobe swap successful");
                trackedWardrobeSlot = targetWardrobeSlot;
                isSwappingWardrobe = false;
                client.player.closeContainer();
                handleWardrobeCompletion(client);
            }
        }
    }

    private static void handleWardrobeCompletion(Minecraft client) {
        if (shouldRestartFarmingAfterSwap) {
            shouldRestartFarmingAfterSwap = false;

            if (PestManager.isCleaningInProgress) {
                client.player.displayClientMessage(
                        Component.literal("§aWardrobe swap finished. Cleaning in progress, skipping restart."), true);
                return;
            }

            client.player.displayClientMessage(Component.literal("§aWardrobe swap finished. Restarting farming..."),
                    true);
            client.execute(() -> GearManager.swapToFarmingTool(client));
            new Thread(() -> {
                try {
                    // Removed duplicate ClientUtils.waitForGearAndGui(client);
                    if (PestManager.isCleaningInProgress)
                        return;

                    finalResume(client);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    public static void finalResume(Minecraft client) {
        if (PestManager.isCleaningInProgress)
            return;

        ClientUtils.waitForGearAndGui(client);
        GearManager.swapToFarmingToolSync(client);

        if (PestManager.isCleaningInProgress)
            return;

        client.execute(() -> {
            if (PestManager.isCleaningInProgress)
                return;
            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
            ClientUtils.sendDebugMessage(client, "Finalizing gear swap. Restarting farming script...");
            com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
        });
    }

    public static void ensureEquipment(Minecraft client, boolean toFarming) {
        if (!MacroConfig.autoEquipment)
            return;

        // If called from a non-render thread, we can block/wait.
        // If from render thread, we should defer.
        if (Minecraft.getInstance().isSameThread()) {
            if (isSwappingWardrobe) {
                ClientUtils.sendDebugMessage(client, "§eEquipment swap deferred: Wardrobe busy.");
                return;
            }
        } else {
            try {
                int timeout = 0;
                boolean waited = false;
                while (isSwappingWardrobe && timeout < 100) { // Max 5 second wait
                    Thread.sleep(50);
                    timeout++;
                    waited = true;
                }
                if (isSwappingWardrobe) {
                    ClientUtils.sendDebugMessage(client,
                            "§c[GearManager] Auto-Equipment aborted: Wardrobe swap timed out.");
                    return;
                }
                if (waited) {
                    ClientUtils.sendDebugMessage(client, "§eWardrobe swap done! Triggering equipment swap");
                }
            } catch (InterruptedException ignored) {
            }
        }

        swappingToFarmingGear = toFarming;
        equipmentGuiDetected = false; // Reset GUI detection flag immediately
        equipmentInteractionTime = 0;
        isSwappingEquipment = true;
        equipmentInteractionStage = 0;
        equipmentTargetIndex = 0;
        ClientUtils.sendCommand(client, "/equipment");
    }

    public static void handleEquipmentMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingEquipment)
            return;

        if (isSwappingWardrobe) {
            isSwappingEquipment = false;
            ClientUtils.sendDebugMessage(client, "§eAborting equipment menu for wardrobe priority.");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - equipmentInteractionTime < MacroConfig.getRandomizedDelay(MacroConfig.equipmentSwapDelay))
            return;

        String title = screen.getTitle().getString().toLowerCase();
        if (!title.contains("equipment"))
            return;

        // Validate that equipment GUI is actually functional before marking as detected
        int[] guiSlots = { 10, 19, 28, 37 };
        boolean hasValidEquipmentSlots = true;

        // Check if at least one equipment slot has a valid item (not empty/loading)
        boolean foundValidSlot = false;
        for (int slotIdx : guiSlots) {
            if (slotIdx < screen.getMenu().slots.size()) {
                Slot slot = screen.getMenu().slots.get(slotIdx);
                if (slot != null && slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    // If it's not empty and not a gray dye/loading placeholder, it's valid
                    if (!stack.isEmpty() && !stack.getItem().toString().toLowerCase().contains("gray_dye")
                            && !stack.getHoverName().getString().toLowerCase().contains("gray dye")) {
                        foundValidSlot = true;
                        break;
                    }
                }
            }
        }

        if (!foundValidSlot) {
            // Equipment GUI open but data not loaded yet
            ClientUtils.sendDebugMessage(client,
                    "Equipment GUI open but data not loaded yet (no valid equipment slots detected)");
            return;
        }

        if (!equipmentGuiDetected) {
            equipmentGuiDetected = true;
            ClientUtils.sendDebugMessage(client, "Equipment GUI detected AND VALIDATED as functional");
            equipmentInteractionTime = System.currentTimeMillis();
        }

        // Necklace, Cloak/Vest, Belt, Hand (Gloves/Bracelet/Gauntlet)
        String[] keywords = { "necklace", "cloak|vest|cape", "belt", "gloves|bracelet|gauntlet" };

        int totalSlots = screen.getMenu().slots.size();
        int playerInvStart = totalSlots - 36;
        ItemStack carried = client.player.containerMenu.getCarried();

        if (equipmentTargetIndex >= guiSlots.length) {
            // Wait for inventory to be clear of target equipment
            boolean targetRemaining = false;
            for (int i = playerInvStart; i < totalSlots; i++) {
                if (i >= screen.getMenu().slots.size())
                    break;
                Slot invSlot = screen.getMenu().slots.get(i);
                if (invSlot != null && invSlot.hasItem()) {
                    String invItemName = invSlot.getItem().getHoverName().getString().toLowerCase();
                    boolean invIsFarming = invItemName.contains("lotus") || invItemName.contains("blossom")
                            || invItemName.contains("zorro");
                    boolean invIsPest = invItemName.contains("pest");
                    boolean matchesTarget = swappingToFarmingGear ? invIsFarming : invIsPest;

                    if (matchesTarget) {
                        boolean isEquipmentType = false;
                        for (String kwGroup : keywords) {
                            for (String kw : kwGroup.split("\\|")) {
                                if (invItemName.contains(kw)) {
                                    isEquipmentType = true;
                                    break;
                                }
                            }
                            if (isEquipmentType)
                                break;
                        }

                        if (isEquipmentType) {
                            // Only count as remaining if we haven't already equipped this type of gear
                            int slotGroup = -1;
                            for (int k = 0; k < keywords.length; k++) {
                                for (String kw : keywords[k].split("\\|")) {
                                    if (invItemName.contains(kw)) {
                                        slotGroup = k;
                                        break;
                                    }
                                }
                                if (slotGroup != -1)
                                    break;
                            }

                            if (slotGroup != -1) {
                                Slot targetSlot = screen.getMenu().getSlot(guiSlots[slotGroup]);
                                if (targetSlot != null && targetSlot.hasItem()) {
                                    String targetName = targetSlot.getItem().getHoverName().getString().toLowerCase();
                                    boolean targetIsFarming = targetName.contains("lotus")
                                            || targetName.contains("blossom")
                                            || targetName.contains("zorro");
                                    boolean targetIsPest = targetName.contains("pest");
                                    boolean targetMatches = swappingToFarmingGear ? targetIsFarming : targetIsPest;
                                    if (targetMatches)
                                        continue;
                                }
                            }

                            targetRemaining = true;
                            break;
                        }
                    }
                }
            }

            if (targetRemaining) {
                ClientUtils.sendDebugMessage(client,
                        "§eEquipment swap: Target items still in inventory after attempt, retrying sequence...");
                equipmentTargetIndex = 0;
                equipmentInteractionStage = 0;
                equipmentInteractionTime = now;
                return;
            }

            ClientUtils.sendDebugMessage(client, "§aEquipment swap successful!");
            trackedIsPestGear = !swappingToFarmingGear;
            isSwappingEquipment = false;
            // Close the screen client-side immediately (no server round-trip needed).
            // Send the ServerboundContainerClosePacket asynchronously after 100ms.
            int containerId = screen.getMenu().containerId;
            client.setScreen(null);
            wardrobeCleanupTicks = 10;
            equipmentInteractionStage = 0;
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    client.execute(() -> {
                        if (client.player != null && client.getConnection() != null)
                            client.getConnection().send(new ServerboundContainerClosePacket(containerId));
                    });
                } catch (InterruptedException ignored) {
                }
            }).start();
            return;
        }

        // Stage 0: Verify if swap is needed and search for desired item in inventory
        if (equipmentInteractionStage == 0) {
            if (!carried.isEmpty()) {
                String carriedName = carried.getHoverName().getString().toLowerCase();
                boolean isFarming = carriedName.contains("lotus") || carriedName.contains("blossom")
                        || carriedName.contains("zorro");
                boolean isPest = carriedName.contains("pest");
                boolean matchesTarget = swappingToFarmingGear ? isFarming : isPest;

                if (matchesTarget) {
                    for (int i = 0; i < keywords.length; i++) {
                        for (String type : keywords[i].split("\\|")) {
                            if (carriedName.contains(type)) {
                                ClientUtils.sendDebugMessage(client, "§eEquipment swap: Item " + carriedName
                                        + " stuck in cursor, trying to equip to slot " + (i + 1));
                                equipmentTargetIndex = i;
                                equipmentInteractionStage = 1;
                                return;
                            }
                        }
                    }
                }

                ClientUtils.sendDebugMessage(client,
                        "§cEquipment swap: Waiting for cursor to clear (" + carriedName + ")");
                return;
            }

            // First, check if the slot already has the correct gear
            Slot equipmentSlot = screen.getMenu().getSlot(guiSlots[equipmentTargetIndex]);
            if (equipmentSlot != null && equipmentSlot.hasItem()) {
                String itemName = equipmentSlot.getItem().getHoverName().getString().toLowerCase();
                boolean isFarming = itemName.contains("lotus") || itemName.contains("blossom")
                        || itemName.contains("zorro");
                boolean isPest = itemName.contains("pest");
                boolean matches = swappingToFarmingGear ? isFarming : isPest;

                if (matches) {
                    ClientUtils.sendDebugMessage(client,
                            "§7Slot " + (equipmentTargetIndex + 1) + " already has correct gear.");
                    equipmentTargetIndex++;
                    equipmentInteractionTime = now;
                    return;
                }
            }

            // Second, search inventory for the item
            String targetTypePattern = keywords[equipmentTargetIndex];
            for (int i = playerInvStart; i < totalSlots; i++) {
                Slot invSlot = screen.getMenu().slots.get(i);
                if (invSlot.hasItem()) {
                    String invItemName = invSlot.getItem().getHoverName().getString().toLowerCase();
                    boolean invIsFarming = invItemName.contains("lotus") || invItemName.contains("blossom")
                            || invItemName.contains("zorro");
                    boolean invIsPest = invItemName.contains("pest");
                    boolean matchesTarget = swappingToFarmingGear ? invIsFarming : invIsPest;

                    if (matchesTarget) {
                        boolean typeMatch = false;
                        for (String type : targetTypePattern.split("\\|")) {
                            if (invItemName.contains(type)) {
                                typeMatch = true;
                                break;
                            }
                        }

                        if (typeMatch) {
                            ClientUtils.sendDebugMessage(client, "§bPicking up target item: " + invItemName);
                            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, invSlot.index, 0,
                                    ClickType.PICKUP, client.player);
                            equipmentInteractionTime = now;
                            equipmentInteractionStage = 1;
                            return;
                        }
                    }
                }
            }

            // Not found in inventory? skip
            equipmentTargetIndex++;
            equipmentInteractionTime = now;
            return;
        }

        // Stage 1: Cursor has new gear. Click on the equipment slot to equip it.
        // This automatically unequips the old gear and places it in the cursor.
        // We don't need to handle the old gear placement - it can stay in cursor.
        if (equipmentInteractionStage == 1) {
            if (carried.isEmpty()) {
                ClientUtils.sendDebugMessage(client, "§cEquipment swap: Cursor empty in stage 1, resetting to search.");
                equipmentInteractionStage = 0; // Failed to pick up?
                return;
            }
            int gearSlotIdx = guiSlots[equipmentTargetIndex];
            ClientUtils.sendDebugMessage(client,
                    "§bEquipping " + carried.getHoverName().getString() + " into slot " + (equipmentTargetIndex + 1));
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, gearSlotIdx, 0,
                    ClickType.PICKUP, client.player);
            equipmentInteractionTime = now;
            equipmentInteractionStage = 0;
            equipmentTargetIndex++;
            return;
        }
    }

    public static int findFarmingToolSlot(Minecraft client) {
        if (client.player == null)
            return -1;
        String[] keywords = { "hoe", "dicer", "knife", "chopper", "cutter" };
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            String name = stack.getHoverName().getString().toLowerCase();
            for (String kw : keywords) {
                if (name.contains(kw)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static void swapToFarmingTool(Minecraft client) {
        int slot = findFarmingToolSlot(client);
        if (slot != -1) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            String name = stack.getHoverName().getString();
            ((AccessorInventory) client.player.getInventory()).setSelected(slot);
            client.player.displayClientMessage(Component.literal("\u00A7aEquipped Farming Tool: " + name), true);
        }
    }

    public static void swapToFarmingToolSync(Minecraft client) {
        int slot = findFarmingToolSlot(client);
        if (slot != -1) {
            client.execute(() -> {
                ((AccessorInventory) client.player.getInventory()).setSelected(slot);
                ItemStack stack = client.player.getInventory().getItem(slot);
                client.player.displayClientMessage(
                        Component.literal("\u00A7aEquipped Farming Tool: " + stack.getHoverName().getString()), true);
            });
            // Wait for confirmation
            try {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < 1500) {
                    if (((AccessorInventory) client.player.getInventory()).getSelected() == slot)
                        break;
                    Thread.sleep(20);
                }
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public static void executeRodSequence(Minecraft client) {
        ClientUtils.sendDebugMessage(client, "executeRodSequence called");
        // Wait for any ongoing equipment swap first
        if (isSwappingEquipment) {
            ClientUtils.sendDebugMessage(client, "Waiting for equipment swap before rod sequence...");
            long waitStart = System.currentTimeMillis();
            try {
                while (isSwappingEquipment && System.currentTimeMillis() - waitStart < 5000) {
                    Thread.sleep(50);
                }
                if (isSwappingEquipment) {
                    ClientUtils.sendDebugMessage(client,
                            "§cRod sequence: Equipment swap timed out, proceeding anyway.");
                } else {
                    ClientUtils.sendDebugMessage(client, "Equipment swap done! Starting rod sequence.");
                }
            } catch (InterruptedException ignored) {
            }
        }

        client.player.displayClientMessage(Component.literal("\u00A7eExecuting Rod Swap sequence..."), true);

        // Find the rod slot first (don't swap yet)
        int rodSlot = -1;
        for (int i = 0; i < 9; i++) {
            String rodItemName = client.player.getInventory().getItem(i).getHoverName().getString().toLowerCase();
            if (rodItemName.contains("rod")) {
                rodSlot = i;
                break;
            }
        }

        if (rodSlot == -1) {
            client.player.displayClientMessage(Component.literal("\u00A7cRod not found in hotbar!"), true);
            return;
        }

        final int finalRodSlot = rodSlot;
        try {
            // Swap to the rod first
            client.execute(() -> ((AccessorInventory) client.player.getInventory()).setSelected(finalRodSlot));

            // Wait until the client-side inventory reflects the slot change
            long swapWaitStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - swapWaitStart < 1500) {
                if (((AccessorInventory) client.player.getInventory()).getSelected() == finalRodSlot) {
                    ItemStack current = client.player.getInventory().getItem(finalRodSlot);
                    if (current.getHoverName().getString().toLowerCase().contains("rod")) {
                        break;
                    }
                }
                Thread.sleep(10);
            }

            // Configurable delay after swap is confirmed
            if (MacroConfig.rodSwapDelay > 0) {
                Thread.sleep(MacroConfig.rodSwapDelay);
            }

            // Spam right-click every tick for rodSwapDelay ms via the tick-driven flag
            isHoldingRodUse = true;
            Thread.sleep(MacroConfig.rodSwapDelay);

            // Stop spamming
            isHoldingRodUse = false;
        } catch (InterruptedException e) {
            isHoldingRodUse = false;
            e.printStackTrace();
        }
    }

    public static void cleanupTick(Minecraft client) {
        if (wardrobeCleanupTicks > 0) {
            wardrobeCleanupTicks--;
            if (client.player != null) {
                try {
                    if (client.player.containerMenu != null) {
                        client.player.containerMenu.setCarried(ItemStack.EMPTY);
                        client.player.containerMenu.broadcastChanges();
                    }
                    if (client.player.inventoryMenu != null) {
                        client.player.inventoryMenu.setCarried(ItemStack.EMPTY);
                        client.player.inventoryMenu.broadcastChanges();
                    }
                    client.player.connection.send(new ServerboundContainerClosePacket(0));
                } catch (Exception ignored) {
                }
            }
            if (client.mouseHandler != null) {
                client.mouseHandler.releaseMouse();
            }
        }
    }
}
