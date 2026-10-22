package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.index.EnchantingIndex;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → the user: the enchanted books the Enchanting Index found; open its page. {@code truncated} when there were
 * more than {@link EnchantingIndex#MAX_BOOKS}. {@code dye} is the index's dye colour (RGB), which the books picked from
 * it glow in; an undyed index glows in the client's configured colour.
 */
public record EnchantingIndexPayload(List<EnchantingIndex.Entry> entries, boolean truncated, Optional<Integer> dye)
        implements CustomPacketPayload {
    public static final Type<EnchantingIndexPayload> TYPE = new Type<>(SpectralEnchantments.id("enchanting_index"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantingIndexPayload> STREAM_CODEC = StreamCodec.composite(
            EnchantingIndex.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), EnchantingIndexPayload::entries,
            ByteBufCodecs.BOOL, EnchantingIndexPayload::truncated,
            ByteBufCodecs.optional(ByteBufCodecs.INT), EnchantingIndexPayload::dye,
            EnchantingIndexPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
