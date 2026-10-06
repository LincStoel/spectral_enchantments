package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server → the miner and nearby clients: a player started (or stopped) Far Mining {@code pos}, so their clients
 * can show (or dissolve) the spectral tool. The miner's own client only uses the end, to release its lock.
 */
public record FarMineStatePayload(int entityId, boolean active, boolean broken, BlockPos pos, Direction face, ItemStack tool)
        implements CustomPacketPayload {

    public static final Type<FarMineStatePayload> TYPE = new Type<>(SpectralEnchantments.id("far_mine_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarMineStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(FarMineStatePayload::write, FarMineStatePayload::read);

    public static FarMineStatePayload start(int entityId, BlockPos pos, Direction face, ItemStack tool) {
        return new FarMineStatePayload(entityId, true, false, pos, face, tool);
    }

    public static FarMineStatePayload end(int entityId, BlockPos pos, boolean broken) {
        return new FarMineStatePayload(entityId, false, broken, pos, Direction.UP, ItemStack.EMPTY);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(active);
        buf.writeBoolean(broken);
        buf.writeBlockPos(pos);
        if (active) {
            buf.writeEnum(face);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, tool);
        }
    }

    private static FarMineStatePayload read(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean active = buf.readBoolean();
        boolean broken = buf.readBoolean();
        BlockPos pos = buf.readBlockPos();
        if (!active) {
            return end(entityId, pos, broken);
        }
        Direction face = buf.readEnum(Direction.class);
        return start(entityId, pos, face, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
