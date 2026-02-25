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

    public static void reset() {
        isSwappingWardrobe = false;
        isSwappingEquipment = false;
        shouldRestartFarmingAfterSwap = false;
        wardrobeCleanupTicks = 0;
        trackedWardrobeSlot = -1;
        trackedIsPestGear = null;
    }

    public static void triggerWardrobeSwap(Minecraft client, int slot) {
        if (trackedWardrobeSlot == slot) {
            ClientUtils.sendCommand(client, ".ez-stopscript");
            new Thread(() -> {
                try {
                    Thread.sleep(400);
                    client.execute(() -> {
                        GearManager.swapToFarmingTool(client);
                        ClientUtils.sendCommand(client, MacroConfig.restartScript);
                    });
                } catch (Exception ignored) {
                }
            }).start();
            return;
        }

        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        shouldRestartFarmingAfterSwap = true;
        ClientUtils.sendCommand(client, ".ez-stopscript");
        new Thread(() -> {
            try {
                Thread.sleep(375);
                client.execute(() -> ClientUtils.sendCommand(client, "/wardrobe"));
            } catch (Exception ignored) {
            }
        }).start();
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
            ItemStack stack = slot.getItem();
            String itemName = stack.getItem().toString().toLowerCase();
            String hoverName = stack.getHoverName().getString().toLowerCase();

            if (itemName.contains("lime_dye") || itemName.contains("green_dye")
                    || hoverName.contains("lime dye") || hoverName.contains("green dye")) {
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
            trackedWardrobeSlot = targetWardrobeSlot;
            isSwappingWardrobe = false;
            client.player.closeContainer();
            handleWardrobeCompletion(client);
        }
    }

    private static void handleWardrobeCompletion(Minecraft client) {
        if (shouldRestartFarmingAfterSwap) {
            shouldRestartFarmingAfterSwap = false;
            client.player.displayClientMessage(Component.literal("§aWardrobe swap finished. Restarting farming..."),
                    true);
            new Thread(() -> {
                try {
                    Thread.sleep(600);
                    client.execute(() -> {
                        GearManager.swapToFarmingTool(client);
                        ClientUtils.sendCommand(client, MacroConfig.restartScript);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
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
        if (now - equipmentInteractionTime < MacroConfig.equipmentSwapDelay)
            return;

        String title = screen.getTitle().getString();
        if (!title.contains("Equipment"))
            return;

        int[] guiSlots = { 10, 19, 28, 37 };
        String[] keywords = { "necklace", "cloak", "belt", "gloves" };

        if (equipmentTargetIndex >= guiSlots.length) {
            trackedIsPestGear = !swappingToFarmingGear;
            isSwappingEquipment = false;
            client.player.closeContainer();
            wardrobeCleanupTicks = 10;
            equipmentInteractionStage = 0;
            return;
        }

        Slot targetSlot = screen.getMenu().getSlot(guiSlots[equipmentTargetIndex]);
        if (targetSlot == null) {
            equipmentTargetIndex++;
            return;
        }

        String targetType = keywords[equipmentTargetIndex];

        // Stage 0: Check if current item is correct or pick up old item to start swap
        if (equipmentInteractionStage == 0) {
            boolean hasItem = targetSlot.hasItem();
            boolean isCorrect = false;
            if (hasItem) {
                String itemName = targetSlot.getItem().getHoverName().getString().toLowerCase();
                boolean hasFarming = itemName.contains("lotus") || itemName.contains("blossom");
                boolean hasPest = itemName.contains("pest");
                isCorrect = swappingToFarmingGear ? hasFarming : hasPest;
            }

            if (isCorrect) {
                equipmentTargetIndex++;
                equipmentInteractionTime = now;
                return;
            }

            if (hasItem) {
                // Pick up old item
                client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, targetSlot.index, 0,
                        ClickType.PICKUP, client.player);
                equipmentInteractionTime = now;
                equipmentInteractionStage = 1;
            } else {
                // Slot empty, just go find replacement
                equipmentInteractionStage = 1;
            }
            return;
        }

        // Stage 1: Cursor might have old item. Find replacement and swap it.
        if (equipmentInteractionStage == 1) {
            int totalSlots = screen.getMenu().slots.size();
            int playerInvStart = totalSlots - 36;

            for (int i = playerInvStart; i < totalSlots; i++) {
                Slot invSlot = screen.getMenu().slots.get(i);
                if (invSlot.hasItem()) {
                    String invItemName = invSlot.getItem().getHoverName().getString().toLowerCase();
                    boolean invIsFarming = invItemName.contains("lotus") || invItemName.contains("blossom");
                    boolean invIsPest = invItemName.contains("pest");
                    boolean matchesTarget = swappingToFarmingGear ? invIsFarming : invIsPest;

                    if (matchesTarget && invItemName.contains(targetType)) {
                        // Click replacement (swaps with cursor if cursor has old item)
                        client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, invSlot.index, 0,
                                ClickType.PICKUP, client.player);
                        equipmentInteractionTime = now;
                        equipmentInteractionStage = 2;
                        return;
                    }
                }
            }

            // If we didn't find a replacement, proceed to Stage 2 to put back what we have
            // or just continue
            equipmentInteractionStage = 2;
            return;
        }

        // Stage 2: Cursor has new item (or old item if replace failed). Place in
        // equipment slot.
        if (equipmentInteractionStage == 2) {
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, targetSlot.index, 0,
                    ClickType.PICKUP, client.player);
            equipmentInteractionTime = now;
            equipmentInteractionStage = 0;
            equipmentTargetIndex++;
        }
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
