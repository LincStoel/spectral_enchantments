package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.catapult.CatapultedItem;
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

/** Draws a catapulted item or block tumbling end over end along its flight. */
final class TumblingItemRenderer extends EntityRenderer<CatapultedItem> {
    private static final float SCALE = 0.6f;
    private static final float DEGREES_PER_TICK = 25f;

    private final ItemRenderer itemRenderer;

    TumblingItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        shadowRadius = 0.15f;
    }

    @Override
    public void render(CatapultedItem projectile, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        ItemStack stack = projectile.getItem();
        if (!stack.isEmpty() && projectile.tickCount > 0) {
            poseStack.pushPose();
            poseStack.translate(0f, projectile.getBbHeight() / 2f, 0f);
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, projectile.yRotO, projectile.getYRot())));
            poseStack.mulPose(Axis.XP.rotationDegrees((projectile.tickCount + partialTick) * DEGREES_PER_TICK));
            poseStack.scale(SCALE, SCALE, SCALE);
            itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffers,
                    projectile.level(), projectile.getId());
            poseStack.popPose();
        }
        super.render(projectile, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CatapultedItem projectile) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
