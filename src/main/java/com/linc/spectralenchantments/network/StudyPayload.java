package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.study.StudyMode;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Server → the player and those watching them: a study or copy at a lectern started (with the enchantment it is
 * after, for the rune stream), stopped early, or finished (with what it wrote, or a curse).
 */
public record StudyPayload(int playerId, Kind kind, StudyMode mode, BlockPos lectern, int durationTicks,
                           Optional<Holder<Enchantment>> enchantment) implements CustomPacketPayload {
    public static final Type<StudyPayload> TYPE = new Type<>(SpectralEnchantments.id("study"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StudyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StudyPayload::playerId,
            ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), StudyPayload::kind,
            ByteBufCodecs.idMapper(i -> StudyMode.values()[i], StudyMode::ordinal), StudyPayload::mode,
            BlockPos.STREAM_CODEC, StudyPayload::lectern,
            ByteBufCodecs.VAR_INT, StudyPayload::durationTicks,
            ByteBufCodecs.optional(Enchantment.STREAM_CODEC), StudyPayload::enchantment,
            StudyPayload::new);

    public enum Kind {
        /** Started; {@code enchantment} is the one being studied or copied. */
        START,
        /** Let go of, or interrupted, before it was done. */
        STOP,
        /** Done; {@code enchantment} is what was written. */
        SUCCESS,
        /** Done, but a curse was written instead. */
        CURSED
    }

    public static StudyPayload start(int playerId, StudyMode mode, BlockPos lectern, int durationTicks, Holder<Enchantment> target) {
        return new StudyPayload(playerId, Kind.START, mode, lectern, durationTicks, Optional.of(target));
    }

    public static StudyPayload end(int playerId, Kind kind, StudyMode mode, BlockPos lectern, Optional<Holder<Enchantment>> written) {
        return new StudyPayload(playerId, kind, mode, lectern, 0, written);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
