package com.linc.spectralenchantments;

import com.linc.spectralenchantments.network.ModNetwork;
import com.linc.spectralenchantments.spectral.SpectralWeaponManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
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
        modBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.register(CommonEvents.class);
        NeoForge.EVENT_BUS.register(FarMiner.class);
        NeoForge.EVENT_BUS.register(Gather.class);
        NeoForge.EVENT_BUS.register(SpectralWeaponManager.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
