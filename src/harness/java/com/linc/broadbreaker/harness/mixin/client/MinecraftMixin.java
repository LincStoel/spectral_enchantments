package com.linc.broadbreaker.harness.mixin.client;

import com.linc.broadbreaker.harness.client.HarnessClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the harness is mining, it stands in for a held attack key on its target block. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void bbh$holdAttack(boolean leftClick, CallbackInfo ci) {
        if (HarnessClient.holdAttack((Minecraft) (Object) this)) {
            ci.cancel();
        }
    }
}
