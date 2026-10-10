package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.spectral.AttackStyle;
import com.linc.spectralenchantments.spectral.SwingingWeapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * How a weapon fighting on its own (Animated) is posed and swung. The weapon sits in a frame
 * facing along its yaw (+Z forward, +Y up, +X to its left) and is held by an invisible hand at its grip:
 * pitch 0 points the tip up, 90 points it forward.
 */
final class WeaponPoses {
    static final float READY_PITCH = 35f;
    private static final float TOOL_SCALE = 0.9f;
    /** Grip to the middle of the item, which is where the weapon's position sits. */
    private static final float HANDLE_TO_CENTER = 0.45f;

    /** One frame of the weapon's pose relative to its grip. */
    record Pose(float pitch, float swing, float thrust, float lift, float roll) {
        static Pose lerp(float t, Pose a, Pose b) {
            return new Pose(Mth.lerp(t, a.pitch, b.pitch), Mth.lerp(t, a.swing, b.swing), Mth.lerp(t, a.thrust, b.thrust),
                    Mth.lerp(t, a.lift, b.lift), Mth.lerp(t, a.roll, b.roll));
        }
    }

    private WeaponPoses() {}

    /** The weapon's swing pose if it is mid-swing, else {@code idle}. Swings start and end at {@code ready}. */
    static Pose current(SwingingWeapon weapon, float partialTick, Pose idle, Pose ready) {
        float progress = weapon.attackProgress(partialTick);
        if (progress < 0f) {
            return idle;
        }
        return attackPose(weapon.attackStyle(), weapon.attackMirrored() ? -1f : 1f, weapon.attackUpswing(), progress, ready);
    }

    /** From the weapon's position (the middle of the item), swings it about its grip into {@code pose}. */
    static void applyGrip(PoseStack poseStack, Pose pose) {
        poseStack.translate(0f, pose.lift - HANDLE_TO_CENTER, pose.thrust);
        poseStack.mulPose(Axis.YP.rotationDegrees(pose.swing));
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(pose.roll));
        poseStack.translate(0f, HANDLE_TO_CENTER, 0f);
    }

    /** Stands a tool or weapon sprite upright, blade edge leading. */
    static void applyTool(PoseStack poseStack) {
        poseStack.mulPose(Axis.YP.rotationDegrees(90f)); // blade edge leads, flat faces sideways
        poseStack.mulPose(Axis.ZP.rotationDegrees(45f)); // item sprites are diagonal; stand the handle upright
        poseStack.scale(TOOL_SCALE, TOOL_SCALE, TOOL_SCALE);
    }

    /**
     * Ready, wind up, strike (landing at {@code upswing}, when the server deals the hit), then recover to ready.
     *
     * @param mirror 1 or -1; alternate swings of a combo come from the other side
     */
    private static Pose attackPose(AttackStyle style, float mirror, float upswing, float t, Pose ready) {
        Pose wind;
        Pose hit;
        Pose recovered = ready;
        switch (style) {
            case SLASH_H -> {
                wind = new Pose(90f, 80f * mirror, 0f, 0.1f, 90f);
                hit = new Pose(90f, -80f * mirror, 0.2f, 0f, 90f);
            }
            case SLASH_V -> {
                wind = new Pose(-35f, 10f * mirror, -0.1f, 0.35f, 0f);
                hit = new Pose(125f, -5f * mirror, 0.15f, -0.15f, 0f);
            }
            case STAB -> {
                wind = new Pose(90f, 0f, -0.4f, 0f, 0f);
                hit = new Pose(90f, 0f, 0.85f, 0f, 0f);
            }
            case SMASH -> {
                wind = new Pose(-50f, 0f, -0.15f, 0.7f, 0f);
                hit = new Pose(150f, 0f, 0.2f, -0.3f, 0f);
            }
            default -> {
                // Tumble end over end into the target, then settle without spinning back.
                wind = new Pose(-40f, 0f, -0.25f, 0.35f, 0f);
                hit = new Pose(300f, 0f, 0.6f, -0.1f, 0f);
                recovered = new Pose(ready.pitch + 360f, ready.swing, ready.thrust, ready.lift, ready.roll);
            }
        }
        float strikeStart = Math.max(0.05f, upswing - 0.18f);
        if (t < strikeStart) {
            float k = t / strikeStart;
            return Pose.lerp(1f - (1f - k) * (1f - k), ready, wind);
        }
        if (t < upswing) {
            float k = (t - strikeStart) / (upswing - strikeStart);
            return Pose.lerp(k * k, wind, hit);
        }
        float k = (t - upswing) / (1f - upswing);
        return Pose.lerp(k * k * (3f - 2f * k), hit, recovered);
    }
}
