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
                    client.execute(() -> GearManager.swapToFarmingTool(client));
                    Thread.sleep(250);
                    ClientUtils.sendCommand(client, MacroConfig.restartScript);
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

            // Wait for item to load (not be air/empty or gray dye)
            if (stack.isEmpty() || stack.getItem().toString().toLowerCase().contains("air")
                    || stack.getItem().toString().toLowerCase().contains("gray_dye")
                    || stack.getHoverName().getString().toLowerCase().contains("gray dye")) {
                return;
            }

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

            trackedWardrobeSlot = targetWardrobeSlot;
            isSwappingWardrobe = false;
            client.player.closeContainer();
            handleWardrobeCompletion(client);
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
            new Thread(() -> {
                try {
                    Thread.sleep(600);
                    if (PestManager.isCleaningInProgress)
                        return;
                    client.execute(() -> GearManager.swapToFarmingTool(client));
                    Thread.sleep(250);
                    ClientUtils.sendCommand(client, ".ez-stopscript");
                    Thread.sleep(250);
                    ClientUtils.sendCommand(client, MacroConfig.restartScript);
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

        // Necklace, Cloak/Vest, Belt, Hand (Gloves/Bracelet/Gauntlet)
        int[] guiSlots = { 10, 19, 28, 37 };
        String[] keywords = { "necklace", "cloak|vest|cape", "belt", "gloves|bracelet|gauntlet" };

        if (equipmentTargetIndex >= guiSlots.length) {
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

        int totalSlots = screen.getMenu().slots.size();
        int playerInvStart = totalSlots - 36;
        ItemStack carried = client.player.containerMenu.getCarried();

        // Stage 0: Verify if swap is needed and search for desired item in inventory
        if (equipmentInteractionStage == 0) {
            if (!carried.isEmpty())
                return; // Wait for cursor to be empty

            // First, check if the slot already has the correct gear
            Slot equipmentSlot = screen.getMenu().getSlot(guiSlots[equipmentTargetIndex]);
            if (equipmentSlot != null && equipmentSlot.hasItem()) {
                String itemName = equipmentSlot.getItem().getHoverName().getString().toLowerCase();
                boolean isFarming = itemName.contains("lotus") || itemName.contains("blossom")
                        || itemName.contains("zorro");
                boolean isPest = itemName.contains("pest");
                boolean matches = swappingToFarmingGear ? isFarming : isPest;

                if (matches) {
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

        // Stage 1: Cursor has new gear. Swap it directly with the equipment slot.
        if (equipmentInteractionStage == 1) {
            if (carried.isEmpty()) {
                equipmentInteractionStage = 0; // Failed to pick up?
                return;
            }
            int gearSlotIdx = guiSlots[equipmentTargetIndex];
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, gearSlotIdx, 0,
                    ClickType.PICKUP, client.player);
            equipmentInteractionTime = now;
            equipmentInteractionStage = 2;
            return;
        }

        // Stage 2: Cursor has old gear. Put it back in an empty inventory slot.
        if (equipmentInteractionStage == 2) {
            if (carried.isEmpty()) {
                // Done swapping (happens if old slot was empty)
                equipmentInteractionStage = 0;
                equipmentTargetIndex++;
                equipmentInteractionTime = now;
                return;
            }
            for (int i = playerInvStart; i < totalSlots; i++) {
                Slot invSlot = screen.getMenu().slots.get(i);
                if (!invSlot.hasItem()) {
                    client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, invSlot.index, 0,
                            ClickType.PICKUP, client.player);
                    equipmentInteractionTime = now;
                    equipmentInteractionStage = 0;
                    equipmentTargetIndex++;
                    return;
                }
            }
            // No empty slot found? We're stuck with item in cursor.
            // Just move to next and hope for the best, or log error.
            equipmentInteractionStage = 0;
            equipmentTargetIndex++;
            equipmentInteractionTime = now;
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
