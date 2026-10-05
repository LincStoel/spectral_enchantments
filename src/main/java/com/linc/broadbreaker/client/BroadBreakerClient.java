package com.linc.broadbreaker.client;

import com.linc.broadbreaker.BroadBreaker;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = BroadBreaker.MOD_ID, dist = Dist.CLIENT)
public final class BroadBreakerClient {
    public BroadBreakerClient(IEventBus modBus) {
        modBus.addListener(BroadBreakerClient::registerShaders);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SpectralSessions.tick());
        NeoForge.EVENT_BUS.addListener(AreaOutlineRenderer::render);
        NeoForge.EVENT_BUS.addListener(SpectralToolRenderer::render);
    }

    private static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), BroadBreaker.id("spectral_tool"), DefaultVertexFormat.NEW_ENTITY),
                    shader -> SpectralRenderType.spectralShader = shader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Broad Breaker spectral tool shader", e);
        }
    }
}
