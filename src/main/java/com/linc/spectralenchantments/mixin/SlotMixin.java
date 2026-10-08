package com.linc.spectralenchantments.mixin;

import com.linc.spectralenchantments.curse.Curses;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A click placing the carried item into an empty slot goes through safeInsert's own mayPlace check; see {@link AbstractContainerMenuMixin}. */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void spectral_enchantments$safeInsert(ItemStack stack, int increment, CallbackInfoReturnable<ItemStack> cir) {
        Player player = Curses.clickingPlayer();
        if (player != null && !Curses.mayPlace(player, (Slot) (Object) this, stack)) {
            cir.setReturnValue(stack);
        }
    }
}
