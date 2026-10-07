package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.animated.AnimatedWeaponEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws an Animated weapon: the real item, lit and glinting like any dropped item (so it renders normally under
 * shaderpacks), swung with the same poses as a spectral weapon. A deployed weapon with nothing to fight hangs
 * point down and turns slowly; one flying home points the way it's going.
 */
final class AnimatedWeaponRenderer extends EntityRenderer<AnimatedWeaponEntity> {
    private static final float IDLE_PITCH = 180f;
    private static final float RETURN_PITCH = 90f;

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
        float age = weapon.tickCount + partialTick;
        float engage = Mth.lerp(partialTick, weapon.engageO, weapon.engage);
        float bob = Mth.sin(age * 0.08f) * 0.06f;
        float spin = weapon.isReturning() ? 0f : Mth.lerp(partialTick, weapon.spinO, weapon.spin);

        WeaponPoses.Pose idle = new WeaponPoses.Pose(weapon.isReturning() ? RETURN_PITCH : IDLE_PITCH, 0f, 0f, bob, 0f);
        WeaponPoses.Pose ready = new WeaponPoses.Pose(WeaponPoses.READY_PITCH, 0f, 0f, bob * 0.5f, 0f);
        WeaponPoses.Pose pose = WeaponPoses.current(weapon, partialTick, WeaponPoses.Pose.lerp(engage, idle, ready), ready);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-weapon.getViewYRot(partialTick) - spin));
        WeaponPoses.applyGrip(poseStack, pose);
        WeaponPoses.applyTool(poseStack);
        itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffers,
                weapon.level(), weapon.getId());
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(AnimatedWeaponEntity weapon) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
