package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.mixin.AccessorInventory;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.item.ItemStack;

public class GearManager {
    private static volatile int pendingFinalResumeRetries = 0;
    private static final int MAX_FINAL_RESUME_RETRIES = 3;
    private static final int FINAL_RESUME_RETRY_DELAY_MS = 700;

    public static void reset() {
        WardrobeManager.resetState();
        EquipmentManager.resetState();
        RodManager.resetState();
        pendingFinalResumeRetries = 0;
    }

    public static void triggerPrepSwap(Minecraft client) {
        RodManager.stopHoldingRodUse();
    }

    public static void triggerWardrobeSwap(Minecraft client, int slot) {
        WardrobeManager.triggerWardrobeSwap(client, slot);
    }

    public static void ensureWardrobeSlot(Minecraft client, int slot) {
        WardrobeManager.ensureWardrobeSlot(client, slot);
    }

    public static void handleWardrobeMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        WardrobeManager.handleWardrobeMenu(client, screen);
    }

    public static void ensureEquipment(Minecraft client, boolean toFarming) {
        EquipmentManager.ensureEquipment(client, toFarming);
    }

    public static void handleEquipmentMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        EquipmentManager.handleEquipmentMenu(client, screen);
    }

    public static void finalResume(Minecraft client) {
        if (PestManager.isCleaningInProgress)
            return;

        ClientUtils.waitForGearAndGui(client);
        swapToFarmingToolSync(client);

        if (!waitForContainerCloseSync(client, 3500)) {
            ClientUtils.sendDebugMessage(client,
                    "§cFinalizing gear swap aborted: container did not close in time. Not restarting script yet.");
            scheduleFinalResumeRetry(client);
            return;
        }

        pendingFinalResumeRetries = 0;

        if (PestManager.isCleaningInProgress)
            return;

        client.execute(() -> {
            if (PestManager.isCleaningInProgress)
                return;
            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
            swapToFarmingTool(client);
            ClientUtils.sendDebugMessage(client, "Finalizing gear swap. Restarting farming script...");
            com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
        });
    }

    private static void scheduleFinalResumeRetry(Minecraft client) {
        if (pendingFinalResumeRetries >= MAX_FINAL_RESUME_RETRIES) {
            ClientUtils.sendDebugMessage(client,
                    "§cFinalizing gear swap retry limit reached. Manual restart may be required.");
            return;
        }

        pendingFinalResumeRetries++;
        int attempt = pendingFinalResumeRetries;
        ClientUtils.sendDebugMessage(client,
                "§eRetrying final resume (" + attempt + "/" + MAX_FINAL_RESUME_RETRIES + ")...");

        MacroWorkerThread.getInstance().submit("GearManager-FinalResumeRetry-" + attempt, () -> {
            MacroWorkerThread.sleep(FINAL_RESUME_RETRY_DELAY_MS);
            if (PestManager.isCleaningInProgress) {
                return;
            }
            finalResume(client);
        });
    }

    private static boolean waitForContainerCloseSync(Minecraft client, long timeoutMs) {
        long start = System.currentTimeMillis();
        long lastForceCloseAttempt = 0;

        while (System.currentTimeMillis() - start < timeoutMs) {
            if (client.player == null) {
                return false;
            }

            boolean hasScreenOpen = client.screen != null;
            boolean hasServerContainerOpen = client.player.containerMenu != null
                    && client.player.inventoryMenu != null
                    && client.player.containerMenu.containerId != client.player.inventoryMenu.containerId;

            if (!hasScreenOpen && !hasServerContainerOpen) {
                return true;
            }

            if (hasServerContainerOpen && System.currentTimeMillis() - lastForceCloseAttempt >= 250) {
                int containerId = client.player.containerMenu.containerId;
                client.execute(() -> {
                    if (client.player != null && client.getConnection() != null) {
                        client.getConnection().send(new ServerboundContainerClosePacket(containerId));
                    }
                    client.setScreen(null);
                });
                lastForceCloseAttempt = System.currentTimeMillis();
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                return false;
            }
        }

        return false;
    }

    public static int findFarmingToolSlot(Minecraft client) {
        if (client.player == null)
            return -1;
        String[] keywords = { "hoe", "dicer", "knife", "chopper", "cutter", "axe", "harvester" };
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
        } else {
            ClientUtils.sendDebugMessage(client, "\u00A7c[GearManager] No farming tool found in hotbar!");
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
        } else {
            ClientUtils.sendDebugMessage(client, "\u00A7c[GearManager] Sync swap failed: No farming tool found in hotbar!");
        }
    }

    public static void executeRodSequence(Minecraft client) {
        RodManager.executeRodSequence(client);
    }

    public static void cleanupTick(Minecraft client) {
        if (WardrobeManager.wardrobeCleanupTicks > 0) {
            WardrobeManager.wardrobeCleanupTicks--;
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
