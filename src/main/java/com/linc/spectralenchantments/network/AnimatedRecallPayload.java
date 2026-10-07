package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the player clicked with their deployed Animated weapon in hand, so call it back. Needed because a
 * click on thin air (or a Better Combat swing) reaches the server as no event of its own.
 */
public record AnimatedRecallPayload() implements CustomPacketPayload {
    public static final AnimatedRecallPayload INSTANCE = new AnimatedRecallPayload();
    public static final Type<AnimatedRecallPayload> TYPE = new Type<>(SpectralEnchantments.id("animated_recall"));
    public static final StreamCodec<ByteBuf, AnimatedRecallPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
