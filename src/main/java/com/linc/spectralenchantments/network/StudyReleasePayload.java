package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the player let go of use while studying or copying. Without it the server only notices once the
 * held use stops repeating, a few ticks late, and could finish a study the player had already let go of.
 */
public record StudyReleasePayload() implements CustomPacketPayload {
    public static final StudyReleasePayload INSTANCE = new StudyReleasePayload();
    public static final Type<StudyReleasePayload> TYPE = new Type<>(SpectralEnchantments.id("study_release"));
    public static final StreamCodec<ByteBuf, StudyReleasePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
