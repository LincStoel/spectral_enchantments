package com.linc.broadbreaker;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/** Broad Breaker is data-driven (see data/broad_breaker/enchantment); its behavior lives in code. */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> BROAD_BREAKER =
            ResourceKey.create(Registries.ENCHANTMENT, BroadBreaker.id("broad_breaker"));

    private ModEnchantments() {}
}
