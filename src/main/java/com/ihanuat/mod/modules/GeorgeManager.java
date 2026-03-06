package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GeorgeManager {
    public static volatile boolean isSelling = false;
    public static volatile boolean isPreparingToSell = false;
    public static volatile int interactionStage = 0;
    public static volatile long interactionTime = 0;
    public static volatile int confirmationCount = 0;

    public static void reset() {
        isSelling = false;
        isPreparingToSell = false;
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

    public static long lastGeorgeCallTime = 0;
    private static final long GEORGE_RECALL_DELAY_MS = 3000;

    public static void handleGeorgeMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (screen == null)
            return;

        String rawTitle = screen.getTitle().getString();
        String title = rawTitle.replaceAll("(?i)§.", "").toLowerCase();

        if (!isSelling) {
            // Failsafe: if George GUI is open but we aren't selling, check if we should
            // close it
            if (title.contains("george") || title.contains("sell pets") || title.contains("pet collector")
                    || title.contains("offer pets")) {
            int petCount = countPetsInInventory(client);
            boolean georgeSequenceRunning = isSelling || isPreparingToSell;
            if (petCount < MacroConfig.georgeSellThreshold && !georgeSequenceRunning
                && MacroStateManager.isMacroRunning()) {
                    client.player.displayClientMessage(
                            Component.literal("§c[Ihanuat] Unexpected George GUI detected. Restarting script..."),
                            false);
                    client.player.closeContainer();

                    // Restart script as a safety measure
                    if (com.ihanuat.mod.MacroStateManager
                            .getCurrentState() == com.ihanuat.mod.MacroState.State.FARMING) {
                        MacroWorkerThread.getInstance().submit("George-UnexpectedGUI-Restart", () -> {
                            com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
                            MacroWorkerThread.sleep(1000);
                            ClientUtils.sendDebugMessage(client,
                                    "Restarting script after unexpected George GUI closure");
                            com.ihanuat.mod.util.CommandUtils.startScript(client,
                                    MacroConfig.getFullRestartCommand(), 0);
                        });
                    }
                }
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now - interactionTime < MacroConfig.getRandomizedDelay(MacroConfig.guiClickDelay))
            return;

        // Stage Detection based on GUI Title
        if (title.contains("offer pets") && interactionStage < 1) {
            interactionStage = 1;
            ClientUtils.sendDebugMessage(client, "Stage 1: Offer pets menu detected");
            interactionTime = now;
        } else if ((title.contains("confirm") || title.contains("are you sure")) && interactionStage < 3) {
            interactionStage = 3;
            ClientUtils.sendDebugMessage(client, "Stage 3: confirm sale gui screen");
            interactionTime = now;
        }

        switch (interactionStage) {
            case 0: // Idle or Pet Collector phase
                if (title.contains("george") || title.contains("sell pets") || title.contains("pet collector")) {
                    int petSlotIdx = findPetSlotIdx(screen);
                    if (petSlotIdx != -1) {
                        String petName = screen.getMenu().slots.get(petSlotIdx).getItem().getHoverName().getString();
                        client.player.displayClientMessage(Component.literal("§aSelling pet: " + petName), true);
                        client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, petSlotIdx, 0,
                                ClickType.QUICK_MOVE, client.player);
                        interactionTime = now;
                    } else if (countPetsInInventory(client) == 0) {
                        finishSelling(client);
                    }
                }
                break;

            case 1: // Offer pets menu detected
                Slot slot13 = screen.getMenu().slots.size() > 13 ? screen.getMenu().slots.get(13) : null;
                if (slot13 != null && slot13.hasItem() && isRatOrSlug(slot13.getItem())) {
                    interactionStage = 2;
                    ClientUtils.sendDebugMessage(client, "Stage 2: rat/slug pet in slot 13");
                    interactionTime = now;
                } else {
                    // Try to find pet in inventory part of the GUI to move it to slot 13
                    int petSlotIdx = findPetSlotIdx(screen);
                    if (petSlotIdx != -1 && petSlotIdx != 13) {
                        client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, petSlotIdx, 0,
                                ClickType.QUICK_MOVE, client.player);
                        interactionTime = now;
                    }
                }
                break;

            case 2: // Pet in slot 13, wait for "Sell" button click
                if (title.contains("offer pets")) {
                    int sellButtonIdx = findButtonSlot(screen, "sell pet", "accept", "confirm", "offer");
                    if (sellButtonIdx != -1) {
                        client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, sellButtonIdx, 0,
                                ClickType.PICKUP, client.player);
                        interactionTime = now;
                    }
                }
                break;

            case 3: // Confirmation screen
                int confirmSlotIdx = findButtonSlot(screen, "confirm", "yes", "accept", "click to accept");
                if (confirmSlotIdx != -1) {
                    client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, confirmSlotIdx, 0,
                            ClickType.PICKUP, client.player);
                    interactionTime = now + 500;
                    // interactionStage will reset in update() when gui closes, logging Stage 4
                }
                break;
        }
    }

    public static void update(Minecraft client) {
        if (!MacroConfig.autoGeorgeSell || client.player == null)
            return;

        if (isPreparingToSell) {
            if (com.ihanuat.mod.MacroStateManager.getCurrentState() != com.ihanuat.mod.MacroState.State.FARMING ||
                    PestManager.isCleaningInProgress || PestManager.prepSwappedForCurrentPestCycle ||
                    VisitorManager.getVisitorCount(client) >= MacroConfig.visitorThreshold) {
                isPreparingToSell = false;
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Aborting George prep due to priority event."), false);
            }
            return;
        }

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
                if (interactionStage > 0) {
                    if (interactionStage >= 3) {
                        ClientUtils.sendDebugMessage(client, "Stage 4: gui closed");
                    }
                    interactionStage = 0;
                }

                long now = System.currentTimeMillis();
                if (now - interactionTime > 1500 && now - lastGeorgeCallTime > GEORGE_RECALL_DELAY_MS) {
                    int remaining = countPetsInInventory(client);
                    if (remaining > 0) {
                        client.player.displayClientMessage(
                                Component.literal(
                                        "§7GUI closed, but " + remaining + " pets remain. Re-calling George..."),
                                false);
                        interactionTime = now;
                        interactionStage = 0;
                        confirmationCount = 0;
                        lastGeorgeCallTime = now;
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
            MacroWorkerThread.getInstance().submit("George-FinishSelling-Resume", () -> {
                com.ihanuat.mod.util.ClientUtils.waitForGearAndGui(client);
                client.execute(() -> GearManager.swapToFarmingTool(client));
                MacroWorkerThread.sleep(150);
                ClientUtils.sendDebugMessage(client,
                        "Starting farming script after George sell: " + MacroConfig.getFullRestartCommand());
                com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
            });
        }
    }

    private static int countPetsInInventory(Minecraft client) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String rawName = stack.getHoverName().getString();
                String name = rawName.replaceAll("(?i)§.", "").toLowerCase();

                if ((name.contains("rat") || name.contains("slug")) && name.contains("[lvl 1]")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int findPetSlotIdx(AbstractContainerScreen<?> screen) {
        for (int i = 0; i < screen.getMenu().slots.size(); i++) {
            Slot slot = screen.getMenu().slots.get(i);
            if (slot.hasItem() && isRatOrSlug(slot.getItem())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isRatOrSlug(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;
        String name = stack.getHoverName().getString().replaceAll("(?i)§.", "").toLowerCase();
        return (name.contains("rat") || name.contains("slug")) && name.contains("[lvl 1]");
    }

    private static int findButtonSlot(AbstractContainerScreen<?> screen, String... keywords) {
        for (int i = 0; i < screen.getMenu().slots.size(); i++) {
            Slot slot = screen.getMenu().slots.get(i);
            if (!slot.hasItem())
                continue;
            ItemStack stack = slot.getItem();
            String itemName = stack.getHoverName().getString().toLowerCase();
            String itemId = stack.getItem().toString().toLowerCase();

            for (String kw : keywords) {
                if (itemName.contains(kw)) {
                    if (itemId.contains("lime") || itemId.contains("green") || itemId.contains("emerald")
                            || itemId.contains("terracotta")) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static void triggerAutomaticSell(Minecraft client, int count) {
        client.player.displayClientMessage(
                Component.literal("§c§lIhanuat >> §7Selling pets (" + count + " pets in inventory)..."), false);

        // Release movement keys but keep the state as FARMING to allow the sequence to
        // finish
        com.ihanuat.mod.util.ClientUtils.forceReleaseKeys(client);

        isPreparingToSell = true;
        isSelling = false;

        MacroWorkerThread.getInstance().submit("George-TriggerSell", () -> {
            try {
                ClientUtils.sendDebugMessage(client, "Stopping script: Preparing George sell");
                com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);

                boolean success = true;
                for (int i = 0; i < 50; i++) {
                    MacroWorkerThread.sleep(100);
                    if (!isPreparingToSell) {
                        success = false;
                        break;
                    }
                    if (com.ihanuat.mod.MacroStateManager
                            .getCurrentState() != com.ihanuat.mod.MacroState.State.FARMING) {
                        success = false;
                        break;
                    }
                }

                if (success && isPreparingToSell) {
                    isPreparingToSell = false;
                    isSelling = true;
                    interactionStage = 0;
                    interactionTime = System.currentTimeMillis();
                    lastGeorgeCallTime = interactionTime;
                    confirmationCount = 0;
                    com.ihanuat.mod.util.ClientUtils.sendCommand(client, "/call george");
                } else {
                    isPreparingToSell = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
