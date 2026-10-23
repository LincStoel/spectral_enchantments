package com.linc.spectralenchantments.mixin.client;

import com.linc.spectralenchantments.client.AlarmingClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * An Alarming sword in hand lights its surroundings under an Iris shaderpack, in the glow colour, by raising the light
 * Iris reads from the held item for its {@code heldBlockLightValue} and {@code heldBlockLightColor} uniforms (and their
 * off-hand twins). Skipped without Iris; nothing is required, so an Iris that reworks this class only loses the light.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.IdMapUniforms$HeldItemSupplier")
public abstract class IrisHeldItemSupplierMixin {
    @ModifyExpressionValue(method = {"update", "applyOldHandLighting"}, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/api/v0/item/IrisItemLightProvider;getLightEmission(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)I"))
    private int spectral_enchantments$alarmingLight(int light, @Local ItemStack stack) {
        return Math.max(light, AlarmingClient.heldLight(stack));
    }

    @ModifyExpressionValue(method = "update", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/api/v0/item/IrisItemLightProvider;getLightColor(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lorg/joml/Vector3f;"))
    private Vector3f spectral_enchantments$alarmingLightColor(Vector3f color, @Local ItemStack stack) {
        return AlarmingClient.heldLight(stack) > 0 ? AlarmingClient.heldLightColor() : color;
    }
}
