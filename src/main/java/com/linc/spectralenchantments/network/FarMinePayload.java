package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: start Far Mining the locked block (beyond normal reach), or stop. */
public record FarMinePayload(boolean start, BlockPos pos, Direction face) implements CustomPacketPayload {
    public static final Type<FarMinePayload> TYPE = new Type<>(SpectralEnchantments.id("far_mine"));
    public static final StreamCodec<FriendlyByteBuf, FarMinePayload> STREAM_CODEC =
            StreamCodec.ofMember(FarMinePayload::write, FarMinePayload::read);

    public static FarMinePayload start(BlockPos pos, Direction face) {
        return new FarMinePayload(true, pos, face);
    }

    public static FarMinePayload stop() {
        return new FarMinePayload(false, BlockPos.ZERO, Direction.UP);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(start);
        if (start) {
            buf.writeBlockPos(pos);
            buf.writeEnum(face);
        }
    }

    private static FarMinePayload read(FriendlyByteBuf buf) {
        return buf.readBoolean() ? start(buf.readBlockPos(), buf.readEnum(Direction.class)) : stop();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
