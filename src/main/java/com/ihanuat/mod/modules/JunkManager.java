package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class JunkManager {
    public static volatile boolean isDropping = false;
    public static volatile boolean isPreparingToDrop = false;
    public static volatile long interactionTime = 0;

    public static void reset() {
        isDropping = false;
        isPreparingToDrop = false;
        interactionTime = 0;
    }

    public static void update(Minecraft client) {
        if (!MacroConfig.autoDropJunk || client.player == null)
            return;

        if (isPreparingToDrop) {
            if (MacroStateManager.getCurrentState() != MacroState.State.FARMING ||
                    PestManager.isCleaningInProgress || PestPrepSwapManager.prepSwappedForCurrentPestCycle) {
                isPreparingToDrop = false;
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Aborting Junk Drop prep due to priority event."), false);
            }
            return;
        }

        if (isDropping) {
            if (MacroStateManager.getCurrentState() != MacroState.State.FARMING ||
                    PestManager.isCleaningInProgress || PestPrepSwapManager.prepSwappedForCurrentPestCycle) {
                isDropping = false;
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Aborting Junk Drop due to priority event."), false);
                return;
            }

            // If screen closed unexpectedly, we might need to re-open or finish
            if (client.screen == null && System.currentTimeMillis() - interactionTime > 1500) {
                if (countJunkItems(client) > 0) {
                    interactionTime = System.currentTimeMillis();
                    client.execute(() -> client.setScreen(new InventoryScreen(client.player)));
                } else {
                    finishDropping(client);
                }
            }
            return;
        }

        // Only check during farming
        if (MacroStateManager.getCurrentState() != MacroState.State.FARMING)
            return;

        // Don't trigger if busy
        if (WardrobeManager.isSwappingWardrobe || EquipmentManager.isSwappingEquipment ||
                PestManager.isCleaningInProgress || PestPrepSwapManager.prepSwappedForCurrentPestCycle)
            return;

        int junkCount = countJunkItems(client);
        if (junkCount >= MacroConfig.junkThreshold) {
            triggerAutomaticDrop(client, junkCount);
        }
    }

    public static int countJunkItems(Minecraft client) {
        if (client.player == null)
            return 0;
        List<String> junk = MacroConfig.junkItems;
        if (junk.isEmpty())
            return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (isJunkItem(stack, junk)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isFarmingTool(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;
        String[] keywords = { "hoe", "dicer", "knife", "chopper", "cutter", "axe", "harvester" };
        String name = stack.getHoverName().getString().toLowerCase();
        for (String kw : keywords) {
            if (name.contains(kw))
                return true;
        }
        return false;
    }

    private static boolean isJunkItem(ItemStack stack, List<String> junkList) {
        if (stack == null || stack.isEmpty())
            return false;

        // Exclude farming tools - they can be enchanted with junk items
        if (isFarmingTool(stack))
            return false;

        // Check Display Name
        String name = stack.getHoverName().getString().replaceAll("(?i)§.", "");
        for (String j : junkList) {
            if (j.isBlank()) continue;
            if (name.contains(j))
                return true;
        }

        // Check Lore
        net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) {
            for (net.minecraft.network.chat.Component line : lore.lines()) {
                String lineText = line.getString().replaceAll("(?i)§.", "");
                for (String j : junkList) {
                    if (j.isBlank()) continue;
                    if (lineText.contains(j))
                        return true;
                }
            }
        }

        return false;
    }

    private static void triggerAutomaticDrop(Minecraft client, int count) {
        client.player.displayClientMessage(
                Component.literal("§c§lIhanuat >> §7Junk detected (" + count + " items), preparing to drop..."),
                false);
        ClientUtils.forceReleaseKeys(client);
        isPreparingToDrop = true;
        isDropping = false;

        MacroWorkerThread.getInstance().submit("JunkDrop-Trigger", () -> {
            try {
                MacroWorkerThread.sleep(3000);

                if (!isPreparingToDrop)
                    return;

                ClientUtils.sendDebugMessage(client, "Stopping script: Preparing to drop junk");
                com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
                MacroWorkerThread.sleep(400); // Small safety delay after stop

                // /setspawn before warping
                com.ihanuat.mod.util.CommandUtils.setSpawn(client);

                // /plottp
                com.ihanuat.mod.util.CommandUtils.plotTp(client, MacroConfig.dropJunkPlotTp);
                MacroWorkerThread.sleep(250);

                if (!isPreparingToDrop)
                    return;

                isPreparingToDrop = false;
                isDropping = true;
                interactionTime = System.currentTimeMillis();

                // Open inventory
                client.execute(() -> client.setScreen(new InventoryScreen(client.player)));

            } catch (Exception e) {
                e.printStackTrace();
                isPreparingToDrop = false;
                isDropping = false;
            }
        });
    }

    public static void handleInventoryMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isDropping)
            return;
        if (!(screen instanceof InventoryScreen))
            return;

        long now = System.currentTimeMillis();
        if (now - interactionTime < MacroConfig.getRandomizedDelay(MacroConfig.guiClickDelay))
            return;

        int junkSlot = -1;
        List<String> junkList = MacroConfig.junkItems;

        // Scan the Slots in the container.
        // In the survival inventory GUI:
        // 0-8: Crafting/Armor (ignored)
        // 9-35: Main inventory
        // 36-44: Hotbar
        for (int i = 9; i <= 44; i++) {
            if (i >= screen.getMenu().slots.size())
                break;
            Slot slot = screen.getMenu().slots.get(i);
            if (isJunkItem(slot.getItem(), junkList)) {
                junkSlot = i;
                break;
            }
        }

        if (junkSlot != -1) {
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, junkSlot, 1, ClickType.THROW,
                    client.player);
            interactionTime = now;
        } else {
            // No more junk
            finishDropping(client);
        }
    }

    private static void finishDropping(Minecraft client) {
        if (client.player != null && client.screen != null) {
            client.player.closeContainer();
        }
        isDropping = false;
        client.player.displayClientMessage(Component.literal("§aJunk Drop finished. Resuming script..."), true);

        MacroWorkerThread.getInstance().submit("JunkDrop-Finish", () -> {
            try {
                com.ihanuat.mod.util.CommandUtils.warpGarden(client);
                MacroWorkerThread.sleep(250);

                ClientUtils.waitForGearAndGui(client);
                if (MacroStateManager.getCurrentState() == MacroState.State.FARMING) {
                    client.execute(() -> {
                        GearManager.swapToFarmingTool(client);
                        ClientUtils.sendDebugMessage(client,
                                "Starting farming script after junk drop: " + MacroConfig.getFullRestartCommand());
                        com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }
}
