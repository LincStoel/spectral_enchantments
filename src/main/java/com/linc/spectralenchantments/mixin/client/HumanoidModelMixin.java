package com.linc.spectralenchantments.mixin.client;

import com.linc.spectralenchantments.client.SpringyClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player riding a Springy ghost slime sits on it. The renderer sets {@code riding} from {@code isPassenger()} just
 * before posing the model, after RenderLivingEvent.Pre, so it is set here instead.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void spectral_enchantments$sitOnSlime(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                  float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (SpringyClient.seated(entity)) {
            ((HumanoidModel<?>) (Object) this).riding = true;
        }
    }
}
