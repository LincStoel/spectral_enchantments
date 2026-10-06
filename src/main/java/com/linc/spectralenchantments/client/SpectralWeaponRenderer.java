package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.spectral.AttackStyle;
import com.linc.spectralenchantments.spectral.SpectralWeaponEntity;
import com.linc.spectralenchantments.spectral.SpectralWeapons;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws Spectral Weapon ghosts. Drawn in the same level stage as the Broad Breaker ghosts (rather than by
 * an entity renderer) so they go through the path known to work under shaderpacks.
 *
 * <p>Each ghost is posed in a frame facing along its yaw (+Z forward, +Y up, +X to its left) and held by
 * an invisible hand at its grip: pitch 0 points the tip up, 90 points it forward.
 */
final class SpectralWeaponRenderer {
    private static final float TOOL_SCALE = 0.9f;
    private static final float BLOCK_SCALE = 0.45f;
    /** Grip to the middle of the item, which is where the weapon's position sits. */
    private static final float HANDLE_TO_CENTER = 0.45f;
    private static final float READY_PITCH = 35f;
    /** In first person the rest spot is pulled forward, into the corner of the view. */
    private static final double FIRST_PERSON_FORWARD = 0.75;
    private static final float FIRST_PERSON_MIN_NEAR = 0.6f;

    /** One frame of the weapon's pose relative to its grip. */
    private record Pose(float pitch, float swing, float thrust, float lift, float roll) {
        static Pose lerp(float t, Pose a, Pose b) {
            return new Pose(Mth.lerp(t, a.pitch, b.pitch), Mth.lerp(t, a.swing, b.swing), Mth.lerp(t, a.thrust, b.thrust),
                    Mth.lerp(t, a.lift, b.lift), Mth.lerp(t, a.roll, b.roll));
        }
    }

    private SpectralWeaponRenderer() {}

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean shaderPack = IrisCompat.shaderPackInUse();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof SpectralWeaponEntity weapon) {
                renderWeapon(mc, weapon, partialTick, cam, event.getPoseStack(), buffers, shaderPack);
            }
        }
    }

    private static void renderWeapon(Minecraft mc, SpectralWeaponEntity weapon, float partialTick, Vec3 cam,
                                     PoseStack poseStack, MultiBufferSource.BufferSource buffers, boolean shaderPack) {
        float fade = Mth.lerp(partialTick, weapon.fadeO, weapon.fade);
        ItemStack stack = weapon.getItem();
        if (fade <= 0f || stack.isEmpty()) {
            return;
        }
        GhostTextures.Ghost ghost = GhostTextures.get(stack);
        if (ghost == null) {
            return;
        }
        boolean block = stack.getItem() instanceof BlockItem;
        float engage = Mth.lerp(partialTick, weapon.engageO, weapon.engage);
        float tilt = Mth.lerp(partialTick, weapon.tiltO, weapon.tilt);
        float tuck = Mth.lerp(partialTick, weapon.tuckO, weapon.tuck);
        float age = weapon.tickCount + partialTick;
        float bob = Mth.sin(age * 0.08f) * 0.06f;

        // At rest the ghost follows the owner's interpolated position exactly; while fighting it uses the
        // server-driven entity position. Engage blends between the two.
        Vec3 entityPos = weapon.getPosition(partialTick);
        Vec3 pos = entityPos;
        float yaw = weapon.getViewYRot(partialTick);
        Player owner = weapon.getOwner();
        boolean firstPersonOwner = owner == mc.player && mc.options.getCameraType().isFirstPerson();
        if (owner != null) {
            float restYaw;
            Vec3 rest;
            if (firstPersonOwner) {
                // Hung off the camera rather than the body, which turns after the head, so it holds its place in view.
                restYaw = owner.getViewYRot(partialTick);
                Vec3 feet = cam.subtract(0, owner.getEyeHeight(), 0);
                rest = SpectralWeapons.restAnchor(owner, feet, restYaw, FIRST_PERSON_FORWARD, tuck);
            } else {
                restYaw = Mth.rotLerp(partialTick, owner.yBodyRotO, owner.yBodyRot);
                rest = SpectralWeapons.restAnchor(owner, partialTick, tuck);
            }
            rest = rest.add(0, bob, 0);
            pos = rest.lerp(entityPos, engage);
            yaw = Mth.rotLerp(engage, restYaw, yaw);
        }

        Pose restPose = new Pose(block ? 0f : tilt, 0f, 0f, 0f, 0f);
        Pose ready = new Pose(block ? 0f : READY_PITCH, 0f, 0f, bob * 0.5f, 0f);
        Pose pose = Pose.lerp(engage, restPose, ready);
        float progress = weapon.attackProgress(partialTick);
        if (progress >= 0f) {
            pose = attackPose(weapon.attackStyle, weapon.attackMirrored ? -1f : 1f, weapon.attackUpswing, progress, ready);
        }

        float near = GhostItemDraw.nearFade(pos.distanceTo(cam));
        if (firstPersonOwner) {
            near = Math.max(near, FIRST_PERSON_MIN_NEAR);
        }

        poseStack.pushPose();
        poseStack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.translate(0f, pose.lift - HANDLE_TO_CENTER, pose.thrust);
        poseStack.mulPose(Axis.YP.rotationDegrees(pose.swing));
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(pose.roll));
        poseStack.translate(0f, HANDLE_TO_CENTER, 0f);
        if (block) {
            // A ghostly block turns slowly in place of tilting.
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 2f));
            poseStack.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(90f)); // blade edge leads, flat faces sideways
            poseStack.mulPose(Axis.ZP.rotationDegrees(45f)); // item sprites are diagonal; stand the handle upright
            poseStack.scale(TOOL_SCALE, TOOL_SCALE, TOOL_SCALE);
        }
        GhostItemDraw.draw(mc, ghost, stack, fade * near, GhostTint.of(stack, age), shaderPack, ItemDisplayContext.NONE,
                poseStack, buffers, weapon.getId());
        poseStack.popPose();
        GhostItemDraw.endBatch(buffers, ghost);
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
