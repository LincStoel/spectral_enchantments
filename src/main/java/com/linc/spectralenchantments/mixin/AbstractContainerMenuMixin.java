package com.linc.spectralenchantments.mixin;

import com.linc.spectralenchantments.curse.Curses;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a player is Cursed their cursed items stay with them: a menu click that would drop one, or put one in a
 * slot outside their own inventory, does nothing. Runs on both sides, so the client's prediction agrees.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Shadow
    public abstract ItemStack getCarried();

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void spectral_enchantments$beginClick(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        Curses.beginClick(player);
        if (Curses.isCursed(player) && dropsLockedItem(slotId, clickType, player)) {
            Curses.endClick();
            if (!player.level().isClientSide) {
                Curses.refuse(player);
            }
            ci.cancel();
        }
    }

    @Inject(method = "doClick", at = @At("RETURN"))
    private void spectral_enchantments$endClick(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        Curses.endClick();
    }

    /** Dropping the carried item outside the window, or throwing one from a slot. */
    private boolean dropsLockedItem(int slotId, ClickType clickType, Player player) {
        if (slotId == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE) {
            return (clickType == ClickType.PICKUP || clickType == ClickType.THROW) && Curses.isLocked(player, getCarried());
        }
        return clickType == ClickType.THROW && slotId >= 0 && slotId < slots.size()
                && Curses.isLocked(player, slots.get(slotId).getItem());
    }

    /** Placing (click, swap, drag) and shift-click moves. */
    @WrapOperation(method = {"doClick", "moveItemStackTo"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean spectral_enchantments$mayPlace(Slot slot, ItemStack stack, Operation<Boolean> original) {
        Player player = Curses.clickingPlayer();
        return original.call(slot, stack) && (player == null || Curses.mayPlace(player, slot, stack));
    }
}
