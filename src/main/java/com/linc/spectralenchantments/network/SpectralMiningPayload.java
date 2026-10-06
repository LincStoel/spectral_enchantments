package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server → nearby clients: another player started (or stopped) area mining, so their clients
 * can show that player's spectral tools and cracks.
 */
public record SpectralMiningPayload(
        int entityId,
        boolean active,
        boolean broken,
        BlockPos origin,
        Direction face,
        List<BlockPos> extras,
        ItemStack tool,
        float progressPerTick) implements CustomPacketPayload {

    public static final Type<SpectralMiningPayload> TYPE = new Type<>(SpectralEnchantments.id("spectral_mining"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpectralMiningPayload> STREAM_CODEC =
            StreamCodec.ofMember(SpectralMiningPayload::write, SpectralMiningPayload::read);

    public static SpectralMiningPayload start(int entityId, BlockPos origin, Direction face, List<BlockPos> extras, ItemStack tool, float progressPerTick) {
        return new SpectralMiningPayload(entityId, true, false, origin, face, extras, tool, progressPerTick);
    }

    public static SpectralMiningPayload end(int entityId, boolean broken) {
        return new SpectralMiningPayload(entityId, false, broken, BlockPos.ZERO, Direction.UP, List.of(), ItemStack.EMPTY, 0f);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(active);
        buf.writeBoolean(broken);
        if (active) {
            buf.writeBlockPos(origin);
            buf.writeEnum(face);
            buf.writeVarInt(extras.size());
            extras.forEach(buf::writeBlockPos);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, tool);
            buf.writeFloat(progressPerTick);
        }
    }

    private static SpectralMiningPayload read(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean active = buf.readBoolean();
        boolean broken = buf.readBoolean();
        if (!active) {
            return end(entityId, broken);
        }
        BlockPos origin = buf.readBlockPos();
        Direction face = buf.readEnum(Direction.class);
        int count = Math.min(buf.readVarInt(), 64);
        List<BlockPos> extras = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            extras.add(buf.readBlockPos());
        }
        ItemStack tool = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        return start(entityId, origin, face, extras, tool, buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
