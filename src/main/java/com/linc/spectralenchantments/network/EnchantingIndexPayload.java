package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.index.EnchantingIndex;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → the user: the enchanted books the Enchanting Index found; open its page. {@code truncated} when there were
 * more than {@link EnchantingIndex#MAX_BOOKS}.
 */
public record EnchantingIndexPayload(List<EnchantingIndex.Entry> entries, boolean truncated) implements CustomPacketPayload {
    public static final Type<EnchantingIndexPayload> TYPE = new Type<>(SpectralEnchantments.id("enchanting_index"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantingIndexPayload> STREAM_CODEC = StreamCodec.composite(
            EnchantingIndex.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), EnchantingIndexPayload::entries,
            ByteBufCodecs.BOOL, EnchantingIndexPayload::truncated,
            EnchantingIndexPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
