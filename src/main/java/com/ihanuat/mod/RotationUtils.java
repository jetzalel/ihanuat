package com.ihanuat.mod;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RotationUtils {
    private static final Random RANDOM = new Random();

    public static class Rotation {
        public float yaw;
        public float pitch;

        public Rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    /**
     * Calculates the yaw and pitch needed to look from 'from' to 'to'.
     */
    public static Rotation calculateLookAt(Vec3 from, Vec3 to) {
        double d0 = to.x - from.x;
        double d1 = to.y - from.y;
        double d2 = to.z - from.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        float f = (float) (Mth.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90F;
        float f1 = (float) (-(Mth.atan2(d1, d3) * (double) (180F / (float) Math.PI)));

        return new Rotation(f, f1);
    }

    /**
     * Calculates the rotation at a specific point 't' (0.0 to 1.0) on a Quadratic
     * Bezier curve.
     */
    public static Rotation calculateBezierPoint(float t, Rotation start, Rotation end, Rotation control) {
        float oneMinusT = 1 - t;

        // Quadratic Bezier Formula: B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2

        // Interpolate Yaw
        // We need to handle wrapping for yaw, but since control point generation
        // handles the direction,
        // we can assume the raw values are "unwrapped" relative to each other if we
        // prepared them right.
        // However, for simplicity here, we'll just interpolate the raw values.
        // A better approach for the control point generation is to ensure 'end' and
        // 'control' are
        // adjusted to be close to 'start' (handling the 360 wrap).

        float yaw = (oneMinusT * oneMinusT * start.yaw) +
                (2 * oneMinusT * t * control.yaw) +
                (t * t * end.yaw);

        float pitch = (oneMinusT * oneMinusT * start.pitch) +
                (2 * oneMinusT * t * control.pitch) +
                (t * t * end.pitch);

        return new Rotation(yaw, pitch);
    }

    /**
     * Generates a random control point for the Bezier curve to simulate a human
     * arc.
     * Ensures the control point follows the shortest path logic.
     */
    public static Rotation generateControlPoint(Rotation start, Rotation end) {
        // Normalize yaw difference to shortest path
        float yawDiff = Mth.wrapDegrees(end.yaw - start.yaw);
        float targetYaw = start.yaw + yawDiff;

        // Random offset
        float controlYaw = start.yaw + yawDiff * 0.5f + (RANDOM.nextFloat() - 0.5f) * 20.0f; // +/- 10 degrees
        float controlPitch = start.pitch + (end.pitch - start.pitch) * 0.5f + (RANDOM.nextFloat() - 0.5f) * 10.0f; // +/-
                                                                                                                   // 5
                                                                                                                   // degrees

        // Return a rotation object. Note: targetYaw might be outside -180/180, distinct
        // from end.yaw's normalized value
        // But for the calculation it's fine.
        return new Rotation(controlYaw, controlPitch);
    }

    // Helper to get the 'unwrapped' end rotation so interpolation works linearly
    public static Rotation getAdjustedEnd(Rotation start, Rotation end) {
        float yawDiff = Mth.wrapDegrees(end.yaw - start.yaw);
        return new Rotation(start.yaw + yawDiff, end.pitch);
    }
}
