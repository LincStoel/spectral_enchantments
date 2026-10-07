package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralEnchantments;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SpectralEnchantments.MOD_ID, dist = Dist.CLIENT)
public final class SpectralEnchantmentsClient {
    public SpectralEnchantmentsClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SpectralClientConfig.SPEC);
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) manager -> GhostTextures.clear()));
        // Spectral weapons are drawn by SpectralWeaponRenderer in a level stage, not by an entity renderer.
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(ModEntities.SPECTRAL_WEAPON.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.ANIMATED_WEAPON.get(), AnimatedWeaponRenderer::new);
        });
        modBus.addListener(SpectralSlotOverlay::register);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            SpectralSessions.tick();
            FarMinerClient.tick();
            BranchFindingClient.tick();
            AnimatedClient.tick();
        });
        NeoForge.EVENT_BUS.addListener(SpectralSlotOverlay::tick);
        NeoForge.EVENT_BUS.addListener(AreaOutlineRenderer::render);
        NeoForge.EVENT_BUS.addListener(SpectralToolRenderer::render);
        NeoForge.EVENT_BUS.addListener(FarMinerRenderer::render);
        NeoForge.EVENT_BUS.addListener(BranchFindingRenderer::render);
        NeoForge.EVENT_BUS.addListener(SpectralWeaponRenderer::render);
        NeoForge.EVENT_BUS.addListener(AnimatedClient::render);
        NeoForge.EVENT_BUS.addListener(AnimatedClient::onInteractionKey);
        // Last, so a renderer another mod cancels never has its hand item hidden (Post wouldn't put it back).
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AnimatedClient::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener(AnimatedClient::onRenderLivingPost);
    }
}
