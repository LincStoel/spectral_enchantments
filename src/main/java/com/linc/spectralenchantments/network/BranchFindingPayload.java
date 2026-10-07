package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.SpectralEnchantments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → the user: Branch Finding found these logs; outline them for {@code durationTicks}. */
public record BranchFindingPayload(List<BlockPos> positions, int durationTicks) implements CustomPacketPayload {
    public static final Type<BranchFindingPayload> TYPE = new Type<>(SpectralEnchantments.id("branch_finding"));
    public static final StreamCodec<FriendlyByteBuf, BranchFindingPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), BranchFindingPayload::positions,
            ByteBufCodecs.VAR_INT, BranchFindingPayload::durationTicks,
            BranchFindingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
