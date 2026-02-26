package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VisitorManager {
    private static final Pattern VISITORS_PATTERN = Pattern.compile("Visitors:\\s*\\(?(\\d+)\\)?");

    public static int getVisitorCount(Minecraft client) {
        if (!MacroConfig.autoVisitor || client.level == null)
            return 0;

        try {
            if (client.getConnection() != null) {
                java.util.Collection<net.minecraft.client.multiplayer.PlayerInfo> players = client.getConnection()
                        .getListedOnlinePlayers();
                for (net.minecraft.client.multiplayer.PlayerInfo info : players) {
                    String name = "";
                    if (info.getTabListDisplayName() != null) {
                        name = info.getTabListDisplayName().getString();
                    } else if (info.getProfile() != null) {
                        name = String.valueOf(info.getProfile());
                    }
                    String clean = name.replaceAll("(?i)\u00A7[0-9a-fk-or]", "").trim();
                    Matcher m = VISITORS_PATTERN.matcher(clean);
                    if (m.find()) {
                        return Integer.parseInt(m.group(1));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static void handleVisitorScriptFinished(Minecraft client) {
        client.player.displayClientMessage(Component.literal("\u00A7aVisitor sequence complete. Returning to farm..."),
                true);
        new Thread(() -> {
            try {
                if (MacroConfig.unflyMode == MacroConfig.UnflyMode.DOUBLE_TAP_SPACE) {
                    PestManager.performUnfly(client);
                    Thread.sleep(150);
                }
                ClientUtils.sendCommand(client, "/warp garden");
                Thread.sleep(1000); // 1s wait for warp load
                PestManager.isReturningFromPestVisitor = true;
                finalizeReturnToFarm(client);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void finalizeReturnToFarm(Minecraft client) {
        if (MacroStateManager.getCurrentState() == MacroState.State.OFF)
            return;

        try {
            if (MacroConfig.unflyMode == MacroConfig.UnflyMode.SNEAK) {
                PestManager.performUnfly(client);
                Thread.sleep(150);
            }
        } catch (InterruptedException ignored) {
        }

        int visitors = getVisitorCount(client);
        if (visitors >= MacroConfig.visitorThreshold) {
            client.player.displayClientMessage(
                    Component.literal("\u00A7dVisitor Threshold Met (" + visitors + "). Redirecting to Visitors..."),
                    true);
            client.execute(() -> {
                GearManager.swapToFarmingTool(client);
                ClientUtils.sendCommand(client, "/setspawn");
            });
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }

            if (MacroConfig.armorSwapVisitor && MacroConfig.wardrobeSlotVisitor > 0
                    && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                client.player.displayClientMessage(Component.literal(
                        "\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor + ")..."), true);
                client.execute(() -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor));
                try {
                    Thread.sleep(400);
                    while (GearManager.isSwappingWardrobe)
                        Thread.sleep(50);
                    while (GearManager.wardrobeCleanupTicks > 0)
                        Thread.sleep(50);
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                }
            }
            client.execute(() -> {
                ClientUtils.sendCommand(client, ".ez-stopscript");
                ClientUtils.sendCommand(client, ".ez-startscript misc:visitor");
            });
            PestManager.isCleaningInProgress = false;
            return;
        }

        client.execute(() -> {
            GearManager.swapToFarmingTool(client);
            ClientUtils.sendCommand(client, "/setspawn");
        });
        try {
            Thread.sleep(250);
        } catch (InterruptedException ignored) {
        }

        if (MacroConfig.armorSwapVisitor && MacroConfig.wardrobeSlotFarming > 0
                && GearManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotFarming) {
            client.player.displayClientMessage(Component.literal(
                    "§eRestoring Farming Wardrobe (Slot " + MacroConfig.wardrobeSlotFarming + ")..."), true);
            client.execute(() -> GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming));
            try {
                Thread.sleep(400);
                while (GearManager.isSwappingWardrobe)
                    Thread.sleep(50);
                while (GearManager.wardrobeCleanupTicks > 0)
                    Thread.sleep(50);
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
        }

        if (MacroConfig.autoEquipment) {
            GearManager.ensureEquipment(client, true); // Restore farming gear
            try {
                Thread.sleep(400);
                while (GearManager.isSwappingEquipment)
                    Thread.sleep(50);
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
        }

        client.player.displayClientMessage(Component.literal("\u00A7aSetting spawn and restarting farming script..."),
                true);
        com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
        client.execute(() -> {
            ClientUtils.sendCommand(client, ".ez-stopscript");
            ClientUtils.sendCommand(client, MacroConfig.restartScript);
        });
        PestManager.isCleaningInProgress = false;
    }
}
