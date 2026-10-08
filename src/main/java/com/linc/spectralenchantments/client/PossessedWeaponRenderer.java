package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.curse.PossessedWeaponEntity;
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
 * Draws a possessed weapon: the real item (lit and glinting like a dropped item, so it renders normally under
 * shaderpacks and keeps any Quark rune), swung with the same poses as an Animated weapon. It flashes red when hurt
 * and keels over when slain.
 */
final class PossessedWeaponRenderer extends EntityRenderer<PossessedWeaponEntity> {
    private static final float IDLE_PITCH = 180f;

    private final ItemRenderer itemRenderer;

    PossessedWeaponRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        shadowRadius = 0.2f;
    }

    @Override
    public void render(PossessedWeaponEntity weapon, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        ItemStack stack = weapon.getItem();
        if (stack.isEmpty()) {
            return;
        }
        float age = weapon.tickCount + partialTick;
        float engage = Mth.lerp(partialTick, weapon.engageO, weapon.engage);
        float bob = Mth.sin(age * 0.12f) * 0.08f;
        // A restless tremble, point down, while it has nobody to fight.
        float tremble = Mth.sin(age * 1.7f) * 3f * (1f - engage);

        WeaponPoses.Pose idle = new WeaponPoses.Pose(IDLE_PITCH, 0f, 0f, bob, tremble);
        WeaponPoses.Pose ready = new WeaponPoses.Pose(WeaponPoses.READY_PITCH, 0f, 0f, bob * 0.5f, 0f);
        WeaponPoses.Pose pose = WeaponPoses.current(weapon, partialTick, WeaponPoses.Pose.lerp(engage, idle, ready), ready);

        poseStack.pushPose();
        poseStack.translate(0f, weapon.getBbHeight() / 2f, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, weapon.yHeadRotO, weapon.yHeadRot)));
        if (weapon.deathTime > 0) {
            float dying = Math.min(1f, (weapon.deathTime + partialTick - 1f) / 20f * 1.6f);
            poseStack.translate(0f, -0.4f * dying, 0f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(90f * Mth.sqrt(dying)));
        }
        WeaponPoses.applyGrip(poseStack, pose);
        WeaponPoses.applyTool(poseStack);
        int overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(weapon.hurtTime > 0 || weapon.deathTime > 0));
        itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, overlay, poseStack, buffers,
                weapon.level(), weapon.getId());
        poseStack.popPose();
        super.render(weapon, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PossessedWeaponEntity weapon) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
