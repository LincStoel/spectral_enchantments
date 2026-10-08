package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.projectile.ItemArrow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a catapulted weapon or blaze bolt: the real item (lit and glinting, Quark runes included, so it renders
 * normally under shaderpacks) flying point first, its tip buried a little way into whatever it sticks in.
 */
final class ItemArrowRenderer<T extends ItemArrow> extends EntityRenderer<T> {
    /** Item sprites point from the bottom-left corner (handle) to the top-right (tip). */
    private static final float SPRITE_TILT = -45f;
    /** Items whose in-world model isn't their sprite (a trident's): drawn as their inventory icon instead. */
    private static final TagKey<Item> RENDER_AS_ICON = TagKey.create(Registries.ITEM, SpectralEnchantments.id("catapulting/render_as_icon"));
    /** Items whose sprite points the other way, from the bottom-right (handle) to the top-left (tip). */
    private static final TagKey<Item> FLIPPED_SPRITE = TagKey.create(Registries.ITEM, SpectralEnchantments.id("catapulting/flipped_sprite"));
    /** From the sprite's center to its tip pixel, along the diagonal. */
    private static final float CENTER_TO_TIP = 0.62f;
    /** How far past the entity's position the tip reaches: into the block it's stuck in. */
    private static final float TIP_DEPTH = 0.25f;

    private final ItemRenderer itemRenderer;

    ItemArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        shadowRadius = 0f;
    }

    @Override
    public void render(T projectile, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        ItemStack stack = projectile.getItem();
        if (!stack.isEmpty()) {
            poseStack.pushPose();
            // As ArrowRenderer: afterwards +X points along the flight.
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, projectile.yRotO, projectile.getYRot()) - 90f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, projectile.xRotO, projectile.getXRot())));
            float shake = projectile.shakeTime - partialTick;
            if (shake > 0f) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(shake * 3f) * shake));
            }
            poseStack.translate(TIP_DEPTH - CENTER_TO_TIP, 0f, 0f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(SPRITE_TILT));
            if (stack.is(FLIPPED_SPRITE)) {
                // Turned about, so its tip points the way the others' do.
                poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            }
            ItemDisplayContext context = stack.is(RENDER_AS_ICON) ? ItemDisplayContext.GUI : ItemDisplayContext.NONE;
            itemRenderer.renderStatic(stack, context, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffers,
                    projectile.level(), projectile.getId());
            poseStack.popPose();
        }
        super.render(projectile, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T projectile) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
