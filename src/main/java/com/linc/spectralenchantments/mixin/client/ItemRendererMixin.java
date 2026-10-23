package com.linc.spectralenchantments.mixin.client;

import com.linc.spectralenchantments.client.AlarmingClient;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * An Alarming sword glows and buzzes wherever the local player sees it: slot icons, both hands, third person. Every one
 * of those is drawn here, and no event wraps them all (RenderHandEvent is first person only, and nothing lets a slot
 * icon be moved).
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V")
    private void spectral_enchantments$alarming(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
                                               MultiBufferSource buffers, int light, int overlay, BakedModel model,
                                               Operation<Void> original) {
        if (AlarmingClient.alarmed(stack)) {
            AlarmingClient.render(stack, context, leftHand, pose, buffers, light, overlay, model, original);
        } else {
            original.call(stack, context, leftHand, pose, buffers, light, overlay, model);
        }
    }
}
