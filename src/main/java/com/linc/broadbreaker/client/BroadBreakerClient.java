package com.linc.broadbreaker.client;

import com.linc.broadbreaker.BroadBreaker;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = BroadBreaker.MOD_ID, dist = Dist.CLIENT)
public final class BroadBreakerClient {
    public BroadBreakerClient(IEventBus modBus) {
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) manager -> GhostTextures.clear()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SpectralSessions.tick());
        NeoForge.EVENT_BUS.addListener(AreaOutlineRenderer::render);
        NeoForge.EVENT_BUS.addListener(SpectralToolRenderer::render);
    }
}
