package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.animated.Animated;
import com.linc.spectralenchantments.animated.AnimatedWeaponEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Draws an Animated weapon: the real item, lit and glinting like any dropped item (so it renders normally under
 * shaderpacks), swung with {@link WeaponPoses}. A deployed weapon with nothing to fight hangs point down, still; one
 * flying home points the way it's going. A companion with nothing to fight floats at its owner's left, tilting back
 * as they run; its resting place follows the owner's interpolated position (or, in first person, the camera) rather
 * than the server-driven entity, so it holds still in view.
 */
final class AnimatedWeaponRenderer extends EntityRenderer<AnimatedWeaponEntity> {
    private static final float IDLE_PITCH = 180f;
    private static final float RETURN_PITCH = 90f;
    private static final float BLOCK_SCALE = 0.45f;
    /** In first person the rest spot is pulled forward, into the corner of the view. */
    private static final double FIRST_PERSON_FORWARD = 0.75;

    private final ItemRenderer itemRenderer;

    AnimatedWeaponRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        shadowRadius = 0f;
    }

    @Override
    public void render(AnimatedWeaponEntity weapon, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        ItemStack stack = weapon.getItem();
        if (stack.isEmpty()) {
            return;
        }
        boolean block = stack.getItem() instanceof BlockItem;
        boolean companion = weapon.isCompanion() && !weapon.isReturning();
        float age = weapon.tickCount + partialTick;
        float engage = Mth.lerp(partialTick, weapon.engageO, weapon.engage);
        // A deployed weapon at rest hangs perfectly still, point down; it only bobs once roused. A companion always bobs.
        float bob = Mth.sin(age * 0.08f) * 0.06f * (companion ? 1f : engage);

        WeaponPoses.Pose idle;
        if (companion) {
            float tilt = Mth.lerp(partialTick, weapon.tiltO, weapon.tilt);
            idle = new WeaponPoses.Pose(block ? 0f : tilt, 0f, 0f, 0f, 0f);
        } else {
            idle = new WeaponPoses.Pose(weapon.isReturning() ? RETURN_PITCH : IDLE_PITCH, 0f, 0f, bob, 0f);
        }
        WeaponPoses.Pose ready = new WeaponPoses.Pose(block ? 0f : WeaponPoses.READY_PITCH, 0f, 0f, bob * 0.5f, 0f);
        WeaponPoses.Pose pose = WeaponPoses.current(weapon, partialTick, WeaponPoses.Pose.lerp(engage, idle, ready), ready);

        poseStack.pushPose();
        float yaw = weapon.getViewYRot(partialTick);
        Player owner = companion ? weapon.getOwner() : null;
        if (owner != null) {
            // Blend from the rest spot beside the owner to the entity's own position as it engages.
            Vec3 entityPos = weapon.getPosition(partialTick);
            float restYaw;
            Vec3 rest;
            if (owner == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                // Hung off the camera rather than the body, which turns after the head, so it holds its place in view.
                restYaw = owner.getViewYRot(partialTick);
                Vec3 feet = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(0, owner.getEyeHeight(), 0);
                rest = Animated.restAnchor(owner, feet, restYaw, FIRST_PERSON_FORWARD, Mth.lerp(partialTick, weapon.tuckO, weapon.tuck));
            } else {
                restYaw = Mth.rotLerp(partialTick, owner.yBodyRotO, owner.yBodyRot);
                rest = Animated.restAnchor(owner, partialTick, Mth.lerp(partialTick, weapon.tuckO, weapon.tuck));
            }
            Vec3 pos = rest.add(0, bob, 0).lerp(entityPos, engage);
            poseStack.translate(pos.x - entityPos.x, pos.y - entityPos.y, pos.z - entityPos.z);
            yaw = Mth.rotLerp(engage, restYaw, yaw);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        WeaponPoses.applyGrip(poseStack, pose);
        if (block) {
            // A block turns slowly in place of standing like a tool.
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 2f));
            poseStack.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);
        } else {
            WeaponPoses.applyTool(poseStack);
        }
        itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffers,
                weapon.level(), weapon.getId());
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(AnimatedWeaponEntity weapon) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
