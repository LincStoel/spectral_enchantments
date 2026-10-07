package com.linc.spectralenchantments.client;

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
 * <p>Each ghost is posed in a frame facing along its yaw and swung by {@link WeaponPoses}.
 */
final class SpectralWeaponRenderer {
    private static final float BLOCK_SCALE = 0.45f;
    /** In first person the rest spot is pulled forward, into the corner of the view. */
    private static final double FIRST_PERSON_FORWARD = 0.75;
    private static final float FIRST_PERSON_MIN_NEAR = 0.6f;

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

        WeaponPoses.Pose restPose = new WeaponPoses.Pose(block ? 0f : tilt, 0f, 0f, 0f, 0f);
        WeaponPoses.Pose ready = new WeaponPoses.Pose(block ? 0f : WeaponPoses.READY_PITCH, 0f, 0f, bob * 0.5f, 0f);
        WeaponPoses.Pose pose = WeaponPoses.current(weapon, partialTick, WeaponPoses.Pose.lerp(engage, restPose, ready), ready);

        float near = GhostItemDraw.nearFade(pos.distanceTo(cam));
        if (firstPersonOwner) {
            near = Math.max(near, FIRST_PERSON_MIN_NEAR);
        }

        poseStack.pushPose();
        poseStack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        WeaponPoses.applyGrip(poseStack, pose);
        if (block) {
            // A ghostly block turns slowly in place of tilting.
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 2f));
            poseStack.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);
        } else {
            WeaponPoses.applyTool(poseStack);
        }
        GhostItemDraw.draw(mc, ghost, stack, fade * near, GhostTint.of(stack, age), shaderPack, ItemDisplayContext.NONE,
                poseStack, buffers, weapon.getId());
        poseStack.popPose();
        GhostItemDraw.endBatch(buffers, ghost);
    }
}
