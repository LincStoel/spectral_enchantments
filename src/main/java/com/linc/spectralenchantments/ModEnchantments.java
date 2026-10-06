package com.linc.spectralenchantments;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/** Broad Breaker is data-driven (see data/spectral_enchantments/enchantment); its behavior lives in code. */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> BROAD_BREAKER =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("broad_breaker"));

    private ModEnchantments() {}
}
