package com.ihanuat.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class MovementUtils {

    public static void releaseMovementKeys(Minecraft mc) {
        if (mc.options == null)
            return;
        KeyMapping.set(mc.options.keyUp.getDefaultKey(), false);
        KeyMapping.set(mc.options.keyDown.getDefaultKey(), false);
        KeyMapping.set(mc.options.keyLeft.getDefaultKey(), false);
        KeyMapping.set(mc.options.keyRight.getDefaultKey(), false);
        KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);
        KeyMapping.set(mc.options.keyShift.getDefaultKey(), false);
    }

    public static void moveTowards(Minecraft mc, Vec3 target) {
        if (mc.player == null)
            return;

        Vec3 pos = mc.player.position();
        double dx = target.x - pos.x;
        double dy = target.y - pos.y;
        double dz = target.z - pos.z;

        float yaw = mc.player.getYRot();
        double rad = Math.toRadians(yaw);
        double lookX = -Math.sin(rad);
        double lookZ = Math.cos(rad);

        double sideX = -Math.sin(rad + Math.PI / 2);
        double sideZ = Math.cos(rad + Math.PI / 2);

        double dotForward = dx * lookX + dz * lookZ;
        double dotSide = dx * sideX + dz * sideZ;

        KeyMapping.set(mc.options.keyUp.getDefaultKey(), dotForward > 0.3);
        KeyMapping.set(mc.options.keyDown.getDefaultKey(), dotForward < -0.3);
        KeyMapping.set(mc.options.keyLeft.getDefaultKey(), dotSide > 0.3);
        KeyMapping.set(mc.options.keyRight.getDefaultKey(), dotSide < -0.3);

        if (dy > 0.1) {
            KeyMapping.set(mc.options.keyJump.getDefaultKey(), true);
            KeyMapping.set(mc.options.keyShift.getDefaultKey(), false);
        } else if (dy < -0.5) {
            KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);
            KeyMapping.set(mc.options.keyShift.getDefaultKey(), true);
        } else {
            KeyMapping.set(mc.options.keyJump.getDefaultKey(), false);
            KeyMapping.set(mc.options.keyShift.getDefaultKey(), false);
        }
    }

    public static Vec3 getDetourTarget(Minecraft mc, Vec3 target) {
        if (mc.player == null || mc.level == null)
            return target;

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 dir = target.subtract(eyePos).normalize();
        double dist = eyePos.distanceTo(target);

        for (double d = 0.5; d < dist; d += 0.5) {
            BlockPos p = BlockPos.containing(eyePos.add(dir.scale(d)));
            if (!mc.level.getBlockState(p).isAir()) {
                return target.add(0, 2, 0);
            }
        }
        return target;
    }
}
