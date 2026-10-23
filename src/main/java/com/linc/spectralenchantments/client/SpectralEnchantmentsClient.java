package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.ModItems;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.compat.BetterCombatCompat;
import com.linc.spectralenchantments.index.EnchantingIndexItem;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SpectralEnchantments.MOD_ID, dist = Dist.CLIENT)
public final class SpectralEnchantmentsClient {
    public SpectralEnchantmentsClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SpectralClientConfig.SPEC);
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) manager -> {
                    GhostTextures.clear();
                    GhostSkins.clear();
                    SpringyClient.clearModels();
                }));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(ModEntities.ANIMATED_WEAPON.get(), AnimatedWeaponRenderer::new);
            event.registerEntityRenderer(ModEntities.CATAPULTED_WEAPON.get(), ItemArrowRenderer::new);
            event.registerEntityRenderer(ModEntities.CATAPULTED_ITEM.get(), TumblingItemRenderer::new);
            event.registerEntityRenderer(ModEntities.BLAZE_BOLT.get(), ItemArrowRenderer::new);
            event.registerEntityRenderer(ModEntities.WATER_BOLT.get(), ItemArrowRenderer::new);
            event.registerEntityRenderer(ModEntities.POSSESSED_WEAPON.get(), PossessedWeaponRenderer::new);
            event.registerEntityRenderer(ModEntities.DOPPELGANGER.get(), PlayerGhostRenderer::new);
            event.registerEntityRenderer(ModEntities.HAUNTING_GHOST.get(), PlayerGhostRenderer::new);
            // In place of vanilla's lectern renderer, which it falls back on for any book but the Enchanting Index.
            event.registerBlockEntityRenderer(BlockEntityType.LECTERN, IndexLecternRenderer::new);
        });
        // Only the cover (layer 0) takes the dye; the pages stay as they are.
        modBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register((stack, tintIndex) ->
                tintIndex == 0 ? DyedItemColor.getOrDefault(stack, EnchantingIndexItem.DEFAULT_COLOR) : -1,
                ModItems.ENCHANTING_INDEX.get()));
        modBus.addListener(SpectralSlotOverlay::register);
        modBus.addListener(HighJumpClient::registerLayers);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            SpectralSessions.tick();
            FarMinerClient.tick();
            BranchFindingClient.tick();
            AnimatedClient.tick();
            StudyClient.tick();
            SpringyClient.tick();
            EnchantingIndexClient.tick();
            IndexLecternRenderer.tick();
            AlarmingClient.tick();
        });
        NeoForge.EVENT_BUS.addListener(StudyClient::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(StudyClient::onRenderHand);
        NeoForge.EVENT_BUS.addListener(StudyClient::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(StudyClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(CurseInput::onClientTick);
        // Better Combat sends its weapon data on joining; drop what was worked out before it arrived.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> BetterCombatCompat.clearCache());
        // Last, so lines other mods add for the curse (Enchantment Descriptions) are there to remove too.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CurseTooltips::onTooltip);
        NeoForge.EVENT_BUS.addListener(AreaOutlineRenderer::render);
        NeoForge.EVENT_BUS.addListener(SpectralToolRenderer::render);
        NeoForge.EVENT_BUS.addListener(FarMinerRenderer::render);
        NeoForge.EVENT_BUS.addListener(BranchFindingRenderer::render);
        NeoForge.EVENT_BUS.addListener(EnchantingIndexRenderer::render);
        NeoForge.EVENT_BUS.addListener(EnchantingIndexScreen::onTooltipColor);
        NeoForge.EVENT_BUS.addListener(AnimatedClient::render);
        NeoForge.EVENT_BUS.addListener(SpringyClient::render);
        NeoForge.EVENT_BUS.addListener(HighJumpClient::onMovementInput);
        NeoForge.EVENT_BUS.addListener(SpringyClient::onMovementInput);
        NeoForge.EVENT_BUS.addListener(SpringyClient::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(HighJumpClient::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(AnimatedClient::onInteractionKey);
        // Last, so a renderer another mod cancels never has its hand item hidden (Post wouldn't put it back).
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AnimatedClient::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener(AnimatedClient::onRenderLivingPost);
    }
}
