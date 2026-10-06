package com.linc.spectralenchantments.network;

import com.linc.spectralenchantments.client.SpectralSessions;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(SpectralMiningPayload.TYPE, SpectralMiningPayload.STREAM_CODEC, (payload, context) -> {
            // Client-only class; only referenced (and loaded) when a packet actually arrives on a client.
            if (FMLEnvironment.dist.isClient()) {
                SpectralSessions.handleRemote(payload);
            }
        });
    }
}
