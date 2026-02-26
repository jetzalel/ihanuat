package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GeorgeManager {
    public static volatile boolean isSelling = false;
    public static volatile int interactionStage = 0;
    public static volatile long interactionTime = 0;
    public static volatile int confirmationCount = 0;

    public static void reset() {
        isSelling = false;
        interactionStage = 0;
        interactionTime = 0;
        confirmationCount = 0;
    }

    public static void onCallGeorgeSent() {
        isSelling = true;
        interactionStage = 0;
        interactionTime = System.currentTimeMillis();
        confirmationCount = 0;
    }

    public static void handleGeorgeMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSelling)
            return;

        if (screen == null)
            return;

        long now = System.currentTimeMillis();
        if (now - interactionTime < MacroConfig.guiClickDelay)
            return;

        String title = screen.getTitle().getString();

        // Stage 0: Initial George GUI or Pet Selection
        if (interactionStage == 0) {
            // Log title to help debug
            client.player.displayClientMessage(Component.literal("§7[Debug] George Title: " + title), false);

            if (!title.toLowerCase().contains("george") && !title.toLowerCase().contains("sell pets")
                    && !title.toLowerCase().contains("pet collector") && !title.toLowerCase().contains("offer pets"))
                return;

            int petSlotIdx = -1;
            for (int i = 0; i < screen.getMenu().slots.size(); i++) {
                Slot slot = screen.getMenu().slots.get(i);
                if (!slot.hasItem())
                    continue;

                String rawName = slot.getItem().getHoverName().getString();
                String name = rawName.replaceAll("(?i)§.", "").toLowerCase();

                // Debug log every item in GUI
                if (name.contains("rat") || name.contains("slug")) {
                    client.player.displayClientMessage(Component.literal("§7[Debug] GUI Slot " + i + ": " + name),
                            false);
                }

                // Detailed debug for found items if requested, but for now let's just broaden
                // match
                if ((name.contains("rat") || name.contains("slug")) && name.contains("[lvl 1]")) {
                    petSlotIdx = i;
                    break;
                }
            }

            if (petSlotIdx != -1) {
                String petName = screen.getMenu().slots.get(petSlotIdx).getItem().getHoverName().getString();
                client.player.displayClientMessage(Component.literal("§aSelling pet: " + petName), true);
                client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, petSlotIdx, 0,
                        ClickType.QUICK_MOVE, client.player);
                interactionTime = now;
                interactionStage = 1;
                confirmationCount = 0;
            } else {
                // No pets found in this current GUI menu.
                // If pets remain, 'update()' will handle re-calling George when the GUI closes.
                // If no pets remain, we are done.
                if (countPetsInInventory(client) == 0) {
                    finishSelling(client);
                }
            }
        }
        // Stage 1: Confirmation Screen
        else if (interactionStage == 1) {
            boolean isConfirmScreen = title.contains("Confirm") || title.contains("Are you sure");
            boolean isGeorgeScreen = title.toLowerCase().contains("george")
                    || title.toLowerCase().contains("offer pets") || title.toLowerCase().contains("sell pets");

            if (!isConfirmScreen && !isGeorgeScreen) {
                if (now - interactionTime > 2000) {
                    interactionStage = 0;
                }
                return;
            }

            int confirmSlotIdx = -1;
            for (int i = 0; i < screen.getMenu().slots.size(); i++) {
                Slot slot = screen.getMenu().slots.get(i);
                if (!slot.hasItem())
                    continue;
                ItemStack stack = slot.getItem();
                String itemId = stack.getItem().toString().toLowerCase();
                String itemName = stack.getHoverName().getString().toLowerCase();

                if (itemId.contains("lime") || itemId.contains("green") || itemId.contains("terracotta")
                        || itemName.contains("confirm") || itemName.contains("yes") || itemName.contains("accept")
                        || itemName.contains("click to accept") || itemName.contains("accept offer")) {
                    confirmSlotIdx = i;
                    break;
                }
            }

            if (confirmSlotIdx != -1) {
                client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, confirmSlotIdx, 0,
                        ClickType.PICKUP, client.player);
                interactionTime = now + 500; // Extra delay for confirmation
                confirmationCount++;
                if (confirmationCount >= 2) {
                    interactionStage = 0; // Return to selection
                }
            } else if (now - interactionTime > 3000) {
                // Button not found?
                interactionStage = 0;
            }
        }
    }

    public static void update(Minecraft client) {
        if (!MacroConfig.autoGeorgeSell || client.player == null)
            return;

        if (isSelling) {
            // Abort if no longer farming or if a priority event occurs
            if (com.ihanuat.mod.MacroStateManager.getCurrentState() != com.ihanuat.mod.MacroState.State.FARMING ||
                    PestManager.isCleaningInProgress || PestManager.prepSwappedForCurrentPestCycle ||
                    VisitorManager.getVisitorCount(client) >= MacroConfig.visitorThreshold) {
                isSelling = false;
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Aborting George sell due to priority event."), false);
                return;
            }

            // Handle GUI closing or failing to open
            if (client.screen == null) {
                long now = System.currentTimeMillis();
                if (now - interactionTime > 1000) {
                    int remaining = countPetsInInventory(client);
                    if (remaining > 0) {
                        client.player.displayClientMessage(
                                Component.literal(
                                        "§7GUI closed, but " + remaining + " pets remain. Re-calling George..."),
                                false);
                        interactionTime = now;
                        interactionStage = 0;
                        confirmationCount = 0;
                        com.ihanuat.mod.util.ClientUtils.sendCommand(client, "/call george");
                    } else {
                        finishSelling(client);
                    }
                }
            }
            return;
        }

        // Don't sell if we are currently swapping gear, cleaning pests, or dealing with
        // visitors
        if (GearManager.isSwappingWardrobe || GearManager.isSwappingEquipment ||
                PestManager.isCleaningInProgress || PestManager.prepSwappedForCurrentPestCycle)
            return;

        if (VisitorManager.getVisitorCount(client) >= MacroConfig.visitorThreshold)
            return;

        // Only check during farming
        if (com.ihanuat.mod.MacroStateManager.getCurrentState() != com.ihanuat.mod.MacroState.State.FARMING)
            return;

        int petCount = countPetsInInventory(client);
        if (petCount >= MacroConfig.georgeSellThreshold) {
            triggerAutomaticSell(client, petCount);
        }
    }

    private static void finishSelling(Minecraft client) {
        if (client.player != null && client.screen != null) {
            client.player.closeContainer();
        }
        isSelling = false;
        client.player.displayClientMessage(Component.literal("§aGeorge Autosell finished. Resuming script..."), true);

        // Resume farming script if we were farming
        if (com.ihanuat.mod.MacroStateManager.getCurrentState() == com.ihanuat.mod.MacroState.State.FARMING) {
            new Thread(() -> {
                try {
                    Thread.sleep(800);
                    client.execute(() -> {
                        GearManager.swapToFarmingTool(client);
                        com.ihanuat.mod.util.ClientUtils.sendCommand(client, MacroConfig.restartScript);
                    });
                } catch (Exception ignored) {
                }
            }).start();
        }
    }

    private static int countPetsInInventory(Minecraft client) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String rawName = stack.getHoverName().getString();
                String name = rawName.replaceAll("(?i)§.", "").toLowerCase();

                // Debug log every item to console/chat to find why detection fails
                if (name.contains("rat") || name.contains("slug")) {
                    client.player.displayClientMessage(Component.literal("§7[Debug] Inv Slot " + i + ": " + name),
                            false);
                }

                if ((name.contains("rat") || name.contains("slug")) && name.contains("[lvl 1]")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void triggerAutomaticSell(Minecraft client, int count) {
        client.player.displayClientMessage(
                Component.literal("§c§lIhanuat >> §7Selling pets (" + count + " pets in inventory)..."), false);

        // Release movement keys but keep the state as FARMING to allow the sequence to
        // finish
        com.ihanuat.mod.util.ClientUtils.forceReleaseKeys(client);

        isSelling = true;
        interactionStage = 0;
        interactionTime = System.currentTimeMillis();
        confirmationCount = 0;

        new Thread(() -> {
            try {
                com.ihanuat.mod.util.ClientUtils.sendCommand(client, ".ez-stopscript");
                Thread.sleep(3000); // Wait 3 seconds to allow user to abort if needed
                com.ihanuat.mod.util.ClientUtils.sendCommand(client, "/call george");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
