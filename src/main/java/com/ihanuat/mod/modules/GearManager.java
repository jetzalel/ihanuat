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

    public static void triggerWardrobeSwap(Minecraft client, int slot) {
        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        shouldRestartFarmingAfterSwap = true;
        ClientUtils.sendCommand(client, ".ez-stopscript");
        wardrobeOpenPendingTime = System.currentTimeMillis();
    }

    public static void ensureWardrobeSlot(Minecraft client, int slot) {
        if (trackedWardrobeSlot == slot)
            return;
        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        ClientUtils.sendCommand(client, "/wardrobe");
    }

    public static void handleWardrobeMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingWardrobe || targetWardrobeSlot == -1)
            return;

        long now = System.currentTimeMillis();
        if (now - wardrobeInteractionTime < MacroConfig.guiClickDelay)
            return;

        String title = screen.getTitle().getString();
        if (!title.contains("Wardrobe"))
            return;

        if (wardrobeInteractionStage == 0) {
            int slotIdx = 35 + targetWardrobeSlot;
            if (slotIdx >= screen.getMenu().slots.size())
                return;

            Slot slot = screen.getMenu().slots.get(slotIdx);
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0, ClickType.PICKUP,
                    client.player);
            wardrobeInteractionTime = now;
            wardrobeInteractionStage = 1;
        } else if (wardrobeInteractionStage == 1) {
            trackedWardrobeSlot = targetWardrobeSlot;
            isSwappingWardrobe = false;
            wardrobeInteractionTime = now;
            client.player.closeContainer();
            wardrobeCleanupTicks = 10;
        }
    }

    public static void ensureEquipment(Minecraft client, boolean toFarming) {
        swappingToFarmingGear = toFarming;
        isSwappingEquipment = true;
        equipmentInteractionTime = 0;
        equipmentInteractionStage = 0;
        equipmentTargetIndex = 0;
        ClientUtils.sendCommand(client, "/equipment");
    }

    public static void handleEquipmentMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingEquipment)
            return;

        long now = System.currentTimeMillis();
        if (now - equipmentInteractionTime < MacroConfig.guiClickDelay)
            return;

        String title = screen.getTitle().getString();
        if (!title.contains("Equipment"))
            return;

        int[] guiSlots = { 10, 19, 28, 37 };
        if (equipmentTargetIndex >= guiSlots.length) {
            trackedIsPestGear = !swappingToFarmingGear;
            isSwappingEquipment = false;
            client.player.closeContainer();
            wardrobeCleanupTicks = 10;
            return;
        }

        Slot targetSlot = screen.getMenu().getSlot(guiSlots[equipmentTargetIndex]);
        if (targetSlot == null || !targetSlot.hasItem()) {
            equipmentTargetIndex++;
            return;
        }

        String itemName = targetSlot.getItem().getHoverName().getString().toLowerCase();
        boolean hasFarming = itemName.contains("lotus") || itemName.contains("blossom");

        if (swappingToFarmingGear != hasFarming) {
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, targetSlot.index, 0,
                    ClickType.PICKUP, client.player);
            equipmentInteractionTime = now;
            // logic for finding replacement in inventory would go here if needed
            // for now, we assume standard behavior or just trigger the click
        }
        equipmentTargetIndex++;
        equipmentInteractionTime = now;
    }

    public static void swapToFarmingTool(Minecraft client) {
        if (client.player == null)
            return;
        String[] keywords = { "hoe", "dicer", "knife", "chopper", "cutter" };
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            String name = stack.getHoverName().getString().toLowerCase();
            for (String kw : keywords) {
                if (name.contains(kw)) {
                    ((AccessorInventory) client.player.getInventory()).setSelected(i);
                    client.player.displayClientMessage(Component.literal("\u00A7aEquipped Farming Tool: " + name),
                            true);
                    return;
                }
            }
        }
    }

    public static void executeRodSequence(Minecraft client) {
        client.player.displayClientMessage(Component.literal("\u00A7eExecuting Rod Swap sequence..."), true);
        for (int i = 0; i < 9; i++) {
            String rodItemName = client.player.getInventory().getItem(i).getHoverName().getString().toLowerCase();
            if (rodItemName.contains("rod")) {
                ((AccessorInventory) client.player.getInventory()).setSelected(i);
                break;
            }
        }
        try {
            Thread.sleep(500);
            client.execute(() -> client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND));
            Thread.sleep(375);
        } catch (InterruptedException e) {
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
