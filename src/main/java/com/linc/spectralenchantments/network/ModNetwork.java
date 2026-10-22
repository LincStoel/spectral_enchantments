package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.FarMiner;
import com.linc.spectralenchantments.animated.AnimatedManager;
import com.linc.spectralenchantments.boots.HighJump;
import com.linc.spectralenchantments.client.BranchFindingClient;
import com.linc.spectralenchantments.client.EnchantingIndexClient;
import com.linc.spectralenchantments.client.FarMinerClient;
import com.linc.spectralenchantments.client.SpectralSessions;
import com.linc.spectralenchantments.client.StudyClient;
import com.linc.spectralenchantments.index.EnchantingIndex;
import com.linc.spectralenchantments.study.EnchantmentStudy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SpectralMiningPayload.TYPE, SpectralMiningPayload.STREAM_CODEC, (payload, context) -> {
            // Client-only class; only referenced (and loaded) when a packet actually arrives on a client.
            if (FMLEnvironment.dist.isClient()) {
                SpectralSessions.handleRemote(payload);
            }
        });
        registrar.playToClient(FarMineStatePayload.TYPE, FarMineStatePayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist.isClient()) {
                FarMinerClient.handleState(payload);
            }
        });
        registrar.playToClient(BranchFindingPayload.TYPE, BranchFindingPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist.isClient()) {
                BranchFindingClient.handle(payload);
            }
        });
        registrar.playToClient(StudyPayload.TYPE, StudyPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist.isClient()) {
                StudyClient.handle(payload);
            }
        });
        registrar.playToClient(EnchantingIndexPayload.TYPE, EnchantingIndexPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist.isClient()) {
                EnchantingIndexClient.handle(payload);
            }
        });
        registrar.playToServer(FarMinePayload.TYPE, FarMinePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                FarMiner.handle(player, payload);
            }
        });
        registrar.playToServer(StudyReleasePayload.TYPE, StudyReleasePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EnchantmentStudy.release(player);
            }
        });
        registrar.playToServer(HighJumpPayload.TYPE, HighJumpPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                HighJump.handle(player, payload);
            }
        });
        registrar.playToServer(AnimatedRecallPayload.TYPE, AnimatedRecallPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                AnimatedManager.recallFromHand(player);
            }
        });
        registrar.playToServer(EnchantingIndexRenamePayload.TYPE, EnchantingIndexRenamePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EnchantingIndex.rename(player, payload.locations(), payload.book(), payload.name());
            }
        });
    }
}
