package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.mixin.AccessorInventory;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class PestAotvManager {
    public static volatile boolean isSneakingForAotv = false;

    public static void resetState() {
        isSneakingForAotv = false;
    }

    public static boolean shouldDoAotvOnCurrentPlot(Minecraft client, String currentInfestedPlot, boolean isSamePlot) {
        if (!MacroConfig.aotvToRoof)
            return false;

        if (MacroConfig.aotvRoofPlots.isEmpty())
            return isSamePlot;

        boolean inAllowedList = MacroConfig.aotvRoofPlots.contains(currentInfestedPlot);
        ClientUtils.sendDebugMessage(client,
                inAllowedList ? "plot in list, performing aotv" : "plot not in list, skipping aotv");
        return inAllowedList;
    }

    public static void performAotvToRoof(Minecraft client) throws InterruptedException {
        if (MacroConfig.breakBlocksBeforeAotv && client.options != null && client.gameMode != null) {
            // Ensure we are on the farming tool first
            GearManager.swapToFarmingToolSync(client);
            
            client.execute(() -> client.options.keyAttack.setDown(true));
            Thread.sleep(250); // 5 ticks
            client.execute(() -> client.options.keyAttack.setDown(false));
            Thread.sleep(50);
        }

        isSneakingForAotv = true;
        Vec3 eyePos = client.player.getEyePosition();
        float yawRad = (float) Math.toRadians(client.player.getYRot());
        int baseUpPitch = Math.max(45, Math.min(90, MacroConfig.aotvRoofPitch));
        int humanization = Math.max(0, Math.min(15, MacroConfig.aotvRoofPitchHumanization));
        double randomizedUpPitch = baseUpPitch + ((Math.random() * 2.0) - 1.0) * humanization;
        randomizedUpPitch = Math.max(45.0, Math.min(90.0, randomizedUpPitch));
        float targetMcPitch = (float) -randomizedUpPitch;
        double pitchRad = Math.toRadians(targetMcPitch);

        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);
        Vec3 targetPos = eyePos.add(dirX * 100.0, dirY * 100.0, dirZ * 100.0);
        int rotTime = (int) (MacroConfig.rotationTime * (0.92 + Math.random() * 0.16));

        RotationManager.initiateRotation(client, targetPos, rotTime);
        ClientUtils.waitForRotationToComplete(client, targetMcPitch, rotTime);

        int aotvSlot = ClientUtils.findAspectOfTheVoidSlot(client);
        if (aotvSlot != -1 && aotvSlot < 9) {
            client.execute(() -> ((AccessorInventory) client.player.getInventory()).setSelected(aotvSlot));
            Thread.sleep(150);
            double startY = client.player.getY();
            Thread.sleep(50 + (long) (Math.random() * 80));
            client.execute(() -> client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND));

            ClientUtils.waitForYChange(client, startY, 1500);
            isSneakingForAotv = false;
            client.execute(() -> client.options.keyShift.setDown(false));
        } else {
            isSneakingForAotv = false;
            client.execute(() -> client.options.keyShift.setDown(false));
        }
    }
}
