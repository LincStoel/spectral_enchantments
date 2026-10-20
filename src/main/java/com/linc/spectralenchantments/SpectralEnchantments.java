package com.linc.spectralenchantments;

import com.linc.spectralenchantments.animated.AnimatedManager;
import com.linc.spectralenchantments.catapult.Catapulting;
import com.linc.spectralenchantments.compat.ArsNouveauCompat;
import com.linc.spectralenchantments.compat.BetterCombatCompat;
import com.linc.spectralenchantments.compat.SableCompat;
import com.linc.spectralenchantments.curse.Curses;
import com.linc.spectralenchantments.curse.Haunting;
import com.linc.spectralenchantments.curse.PossessedWeaponEntity;
import com.linc.spectralenchantments.doppelganger.Doppelganger;
import com.linc.spectralenchantments.explosive.ExplosiveBolts;
import com.linc.spectralenchantments.network.ModNetwork;
import com.linc.spectralenchantments.spectral.WeaponStrike;
import com.linc.spectralenchantments.study.EnchantmentStudy;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(SpectralEnchantments.MOD_ID)
public final class SpectralEnchantments {
    public static final String MOD_ID = "spectral_enchantments";

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, MOD_ID);

    static {
        CONDITION_CODECS.register("enchantment_enabled", () -> EnchantmentEnabledCondition.CODEC);
        CONDITION_CODECS.register("enchantment_treasure_only", () -> EnchantmentTreasureOnlyCondition.CODEC);
    }

    public SpectralEnchantments(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SpectralConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.SERVER, SpectralConfig.SERVER_SPEC);
        CONDITION_CODECS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> {
            event.put(ModEntities.POSSESSED_WEAPON.get(), PossessedWeaponEntity.createAttributes().build());
            event.put(ModEntities.DOPPELGANGER.get(), LivingEntity.createLivingAttributes().build());
            event.put(ModEntities.HAUNTING_GHOST.get(), LivingEntity.createLivingAttributes().build());
        });
        modBus.addListener(ModNetwork::register);
        modBus.addListener(SpectralEnchantments::addPacks);
        NeoForge.EVENT_BUS.register(CommonEvents.class);
        NeoForge.EVENT_BUS.register(FarMiner.class);
        NeoForge.EVENT_BUS.register(Gather.class);
        NeoForge.EVENT_BUS.register(BranchFinding.class);
        NeoForge.EVENT_BUS.register(WeaponStrike.class);
        NeoForge.EVENT_BUS.register(AnimatedManager.class);
        NeoForge.EVENT_BUS.register(Catapulting.class);
        NeoForge.EVENT_BUS.register(ExplosiveBolts.class);
        NeoForge.EVENT_BUS.register(Doppelganger.class);
        NeoForge.EVENT_BUS.register(Curses.class);
        NeoForge.EVENT_BUS.register(Haunting.class);
        NeoForge.EVENT_BUS.register(EnchantmentStudy.class);
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> BetterCombatCompat.clearCache());
        ArsNouveauCompat.register();
        SableCompat.register();
    }

    /**
     * The mod's loot additions (cursed gear in Nether chests, dungeons and strongholds, Broad Breaker picks in
     * mineshafts, piglin barters) ship as a built-in data pack, on by default: plain loot tables any data pack can
     * override, and that can be switched off as a whole from the data pack list.
     */
    private static void addPacks(AddPackFindersEvent event) {
        event.addPackFinders(id("datapacks/loot"), PackType.SERVER_DATA, Component.translatable("pack.spectral_enchantments.loot"),
                PackSource.BUILT_IN, false, Pack.Position.TOP);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
