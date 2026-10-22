package com.linc.spectralenchantments.mixin;

import com.linc.spectralenchantments.ModItems;
import com.linc.spectralenchantments.SpectralEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Vanilla tells clients nothing about a lectern's book; this tells them when it's the Enchanting Index, so it can be
 * drawn floating like an enchanting table's book. Other books stay unsent (a written book can be large, and nothing
 * needs it). The update goes out whenever the lectern's book is put on or taken off, since both change its state.
 */
@Mixin(LecternBlockEntity.class)
public abstract class LecternBlockEntityMixin extends BlockEntity {
    private LecternBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    public abstract ItemStack getBook();

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // Read back by the lectern's own load, under the key it saves its book to.
        if (getBook().is(ModItems.ENCHANTING_INDEX)) {
            tag.put("Book", getBook().save(registries));
        }
        // Never empty: the client skips an empty update, and would go on drawing an index taken off.
        tag.putBoolean(SpectralEnchantments.MOD_ID + ":synced", true);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
