package com.linc.spectralenchantments;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/** The enchantments are data-driven (see data/spectral_enchantments/enchantment); their behavior lives in code. */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> BROAD_BREAKER =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("broad_breaker"));
    public static final ResourceKey<Enchantment> SPECTRAL_WEAPON =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("spectral_weapon"));
    public static final ResourceKey<Enchantment> FAR_MINER =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("far_miner"));
    public static final ResourceKey<Enchantment> GATHER =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("gather"));
    public static final ResourceKey<Enchantment> BRANCH_FINDING =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("branch_finding"));
    public static final ResourceKey<Enchantment> ANIMATED =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("animated"));
    public static final ResourceKey<Enchantment> CATAPULTING =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("catapulting"));
    public static final ResourceKey<Enchantment> EXPLOSIVE_BOLTS =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("explosive_bolts"));
    public static final ResourceKey<Enchantment> POSSESSION_CURSE =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("possession_curse"));

    private ModEnchantments() {}
}
