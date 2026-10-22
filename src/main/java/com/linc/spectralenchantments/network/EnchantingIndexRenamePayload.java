package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.index.EnchantingIndex;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Client → server: rename the copies of {@code book} at {@code locations} from the Enchanting Index's page. */
public record EnchantingIndexRenamePayload(List<EnchantingIndex.Location> locations, ItemStack book, String name)
        implements CustomPacketPayload {
    public static final Type<EnchantingIndexRenamePayload> TYPE = new Type<>(SpectralEnchantments.id("enchanting_index_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantingIndexRenamePayload> STREAM_CODEC = StreamCodec.composite(
            EnchantingIndex.Location.STREAM_CODEC.apply(ByteBufCodecs.list()), EnchantingIndexRenamePayload::locations,
            ItemStack.STREAM_CODEC, EnchantingIndexRenamePayload::book,
            ByteBufCodecs.stringUtf8(EnchantingIndex.MAX_NAME_LENGTH), EnchantingIndexRenamePayload::name,
            EnchantingIndexRenamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
