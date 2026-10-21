package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the local player just leapt with High Jump, at {@code power} (0 to 1) of a full charge. */
public record HighJumpPayload(float power) implements CustomPacketPayload {
    public static final Type<HighJumpPayload> TYPE = new Type<>(SpectralEnchantments.id("high_jump"));
    public static final StreamCodec<ByteBuf, HighJumpPayload> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(HighJumpPayload::new, HighJumpPayload::power);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
