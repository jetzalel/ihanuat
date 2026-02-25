package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class RotationManager {
    private static boolean isRotating = false;
    private static RotationUtils.Rotation startRot;
    private static RotationUtils.Rotation targetRot;
    private static long rotationStartTime;
    private static long rotationDuration;
    private static long lastRotationUpdateTime = 0;
    private static Vec3 returnLookTarget = null;

    public static void setReturnLookTarget(Vec3 target) {
        returnLookTarget = target;
    }

    public static Vec3 getReturnLookTarget() {
        return returnLookTarget;
    }

    public static boolean isRotating() {
        return isRotating;
    }

    public static void initiateRotation(Minecraft mc, Vec3 targetPos, long minDuration) {
        if (mc.player == null)
            return;

        returnLookTarget = null;
        startRot = new RotationUtils.Rotation(mc.player.getYRot(), mc.player.getXRot());
        targetRot = RotationUtils.calculateLookAt(mc.player.getEyePosition(), targetPos);
        targetRot = RotationUtils.getAdjustedEnd(startRot, targetRot);

        float yawDiff = Math.abs(net.minecraft.util.Mth.wrapDegrees(targetRot.yaw - startRot.yaw));
        float pitchDiff = Math.abs(targetRot.pitch - startRot.pitch);
        float totalDistance = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

        long calculatedDuration = (long) ((totalDistance / ((float) MacroConfig.rotationSpeed * 1.5f)) * 1000f);
        rotationDuration = Math.max(150, Math.max(calculatedDuration, minDuration));
        rotationStartTime = System.currentTimeMillis();
        isRotating = true;
    }

    public static void update(Minecraft mc, MacroState.ReturnState returnState) {
        if (mc.player == null)
            return;

        if (isRotating && startRot != null && targetRot != null) {
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - rotationStartTime;
            float t = (float) elapsed / (float) rotationDuration;

            if (t >= 1.0f) {
                t = 1.0f;
                isRotating = false;
            }

            float currentYaw = startRot.yaw + (targetRot.yaw - startRot.yaw) * t;
            float currentPitch = startRot.pitch + (targetRot.pitch - startRot.pitch) * t;

            mc.player.setYRot(currentYaw);
            mc.player.setXRot(currentPitch);
            mc.player.yRotO = currentYaw;
            mc.player.xRotO = currentPitch;
            return;
        }

        if (returnState != MacroState.ReturnState.OFF && returnLookTarget != null) {
            RotationUtils.Rotation target = RotationUtils.calculateLookAt(mc.player.getEyePosition(), returnLookTarget);
            long now = System.currentTimeMillis();
            float deltaSeconds = (lastRotationUpdateTime == 0) ? (1.0f / 60.0f)
                    : (now - lastRotationUpdateTime) / 1000.0f;
            deltaSeconds = Math.min(deltaSeconds, 0.1f);
            lastRotationUpdateTime = now;

            float maxStep = (float) MacroConfig.rotationSpeed * deltaSeconds;
            float curYaw = mc.player.getYRot();
            float curPitch = mc.player.getXRot();

            float yawDiff = net.minecraft.util.Mth.wrapDegrees(target.yaw - curYaw);
            float pitchDiff = target.pitch - curPitch;

            float stepYaw = Math.min(maxStep, Math.max(-maxStep, yawDiff));
            float stepPitch = Math.min(maxStep, Math.max(-maxStep, pitchDiff));

            mc.player.setYRot(curYaw + stepYaw);
            mc.player.setXRot(curPitch + stepPitch);
        } else {
            lastRotationUpdateTime = 0;
        }
    }
}
