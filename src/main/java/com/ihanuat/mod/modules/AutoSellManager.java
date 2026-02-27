package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

public class AutoSellManager {
    public static volatile boolean isAutoSelling = false;
    public static volatile boolean isPreparingToAutoSell = false;
    public static volatile int interactionStage = 0;
    public static volatile long interactionTime = 0;
    
    // List of items to click in the trades GUI
    private static final String[] ITEMS_TO_CLICK = {
        "Atmospheric Filter",
        "Squeaky Toy", 
        "Beady Eyes",
        "Clipped Wings",
        "Overclocker",
        "Mantid Claw",
        "Flowering Bouquet",
        "Bookworm",
        "Chirping Stereo",
        "Firefly",
        "Capsule",
        "Vinyl"
    };

    public static void reset() {
        isAutoSelling = false;
        isPreparingToAutoSell = false;
        interactionStage = 0;
        interactionTime = 0;
    }

    public static void handleTradesMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isAutoSelling)
            return;

        if (screen == null)
            return;

        long now = System.currentTimeMillis();
        if (now - interactionTime < MacroConfig.guiClickDelay)
            return;

        String title = screen.getTitle().getString();

        // Stage 0: Trades GUI - click items
        if (interactionStage == 0) {
            if (!title.toLowerCase().contains("trades"))
                return;

            boolean foundAndClicked = false;
            
            // Only check player inventory slots (bottom 4 rows = 27 slots)
            // Total slots in trades GUI: typically 54 (3 rows chest + 4 rows inventory)
            // Player inventory starts at slot 27 and goes to slot 53
            int totalSlots = screen.getMenu().slots.size();
            int playerInvStart = Math.max(0, totalSlots - 36); // Start of player inventory (4 rows = 36 slots)
            
            for (int i = playerInvStart; i < totalSlots; i++) {
                Slot slot = screen.getMenu().slots.get(i);
                if (!slot.hasItem())
                    continue;

                String itemName = slot.getItem().getHoverName().getString();
                
                // Check if this item matches any of our target items
                for (String targetItem : ITEMS_TO_CLICK) {
                    if (itemName.contains(targetItem)) {
                        client.player.displayClientMessage(Component.literal("§aAutoSell: Clicking " + itemName), true);
                        client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0,
                                ClickType.PICKUP, client.player);
                        interactionTime = now;
                        foundAndClicked = true;
                        break;
                    }
                }
                
                if (foundAndClicked) {
                    break;
                }
            }

            // If no more items found, we're done
            if (!foundAndClicked) {
                finishAutoSelling(client);
            }
        }
    }

    public static void update(Minecraft client) {
        if (!MacroConfig.autoSell || client.player == null)
            return;

        if (isPreparingToAutoSell) {
            // Abort if no longer farming or if a priority event occurs
            if (MacroStateManager.getCurrentState() != MacroState.State.FARMING ||
                    PestManager.isCleaningInProgress || PestManager.prepSwappedForCurrentPestCycle ||
                    VisitorManager.getVisitorCount(client) >= MacroConfig.visitorThreshold) {
                isPreparingToAutoSell = false;
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Aborting AutoSell prep due to priority event."), false);
            }
            return;
        }

        if (isAutoSelling) {
            // Abort if no longer farming or if a priority event occurs
            if (MacroStateManager.getCurrentState() != MacroState.State.FARMING ||
                    PestManager.isCleaningInProgress || PestManager.prepSwappedForCurrentPestCycle ||
                    VisitorManager.getVisitorCount(client) >= MacroConfig.visitorThreshold) {
                isAutoSelling = false;
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Aborting AutoSell due to priority event."), false);
                return;
            }

            // Handle GUI closing
            if (client.screen == null) {
                long now = System.currentTimeMillis();
                if (now - interactionTime > 1000) {
                    finishAutoSelling(client);
                }
            }
            return;
        }
    }

    public static void onAutoSellDetected(Minecraft client) {
        if (!MacroConfig.autoSell || !com.ihanuat.mod.MacroStateManager.isMacroRunning())
            return;

        // Don't auto-sell if we are currently swapping gear or cleaning pests
        if (GearManager.isSwappingWardrobe || GearManager.isSwappingEquipment ||
                PestManager.isCleaningInProgress)
            return;

        if (VisitorManager.getVisitorCount(client) >= MacroConfig.visitorThreshold)
            return;

        // Allow auto-sell during farming or pest spawn cycles
        if (MacroStateManager.getCurrentState() != MacroState.State.FARMING && 
            MacroStateManager.getCurrentState() != MacroState.State.CLEANING)
            return;

        triggerAutoSell(client);
    }

    private static void finishAutoSelling(Minecraft client) {
        if (client.player != null && client.screen != null) {
            client.player.closeContainer();
        }
        isAutoSelling = false;
        client.player.displayClientMessage(Component.literal("§aAutoSell finished. Resuming script..."), true);

        // Resume farming script if we were farming
        if (MacroStateManager.getCurrentState() == MacroState.State.FARMING) {
            new Thread(() -> {
                try {
                    ClientUtils.waitForGearAndGui(client);
                    client.execute(() -> {
                        GearManager.swapToFarmingTool(client);
                        ClientUtils.sendCommand(client, ".ez-stopscript");
                        try {
                            Thread.sleep(250);
                        } catch (InterruptedException ignored) {
                        }
                        ClientUtils.sendCommand(client, MacroConfig.restartScript);
                    });
                } catch (Exception ignored) {
                }
            }).start();
        }
    }

    private static void triggerAutoSell(Minecraft client) {
        client.player.displayClientMessage(
                Component.literal("§c§lIhanuat >> §7Starting AutoSell sequence..."), false);

        // Release movement keys but keep the state as FARMING to allow the sequence to finish
        ClientUtils.forceReleaseKeys(client);

        isPreparingToAutoSell = true;
        isAutoSelling = false;

        new Thread(() -> {
            try {
                ClientUtils.sendCommand(client, ".ez-stopscript");

                boolean success = true;
                for (int i = 0; i < 50; i++) {
                    Thread.sleep(100);
                    if (!isPreparingToAutoSell) {
                        success = false;
                        break;
                    }
                    if (MacroStateManager.getCurrentState() != MacroState.State.FARMING) {
                        success = false;
                        break;
                    }
                }

                if (success && isPreparingToAutoSell) {
                    isPreparingToAutoSell = false;
                    isAutoSelling = true;
                    interactionStage = 0;
                    interactionTime = System.currentTimeMillis();
                    ClientUtils.sendCommand(client, "/trades");
                } else {
                    isPreparingToAutoSell = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}