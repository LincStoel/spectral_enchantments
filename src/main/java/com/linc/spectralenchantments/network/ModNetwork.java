package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.FarMiner;
import com.linc.spectralenchantments.client.FarMinerClient;
import com.linc.spectralenchantments.client.SpectralSessions;
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
        registrar.playToServer(FarMinePayload.TYPE, FarMinePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                FarMiner.handle(player, payload);
            }
        });
    }
}
