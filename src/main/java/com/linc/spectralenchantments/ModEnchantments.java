package com.linc.spectralenchantments;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/** The enchantments are data-driven (see data/spectral_enchantments/enchantment); their behavior lives in code. */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> BROAD_BREAKER =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("broad_breaker"));
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
    public static final ResourceKey<Enchantment> DOPPELGANGER =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("doppelganger"));
    public static final ResourceKey<Enchantment> POSSESSION_CURSE =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("possession_curse"));
    public static final ResourceKey<Enchantment> HAUNTING_CURSE =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("haunting_curse"));
    public static final ResourceKey<Enchantment> HIGH_JUMP =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("high_jump"));
    public static final ResourceKey<Enchantment> SPRINGY =
            ResourceKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("springy"));

    /**
     * Enchantments switched off in the common config. Filled by the {@code overlay_disable_<name>} pack overlays, so
     * it reaches clients with the other tags: both sides agree on what is off, whatever each one's own config says.
     */
    public static final TagKey<Enchantment> DISABLED = TagKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("disabled"));

    private ModEnchantments() {}

    @Nullable
    public static Holder<Enchantment> holder(RegistryAccess access, ResourceKey<Enchantment> key) {
        return access.registry(Registries.ENCHANTMENT).flatMap(registry -> registry.getHolder(key)).orElse(null);
    }

    /** Whether the enchantment exists and isn't switched off (see {@link #DISABLED}). */
    public static boolean isEnabled(Level level, ResourceKey<Enchantment> key) {
        Holder<Enchantment> holder = holder(level.registryAccess(), key);
        return holder != null && !holder.is(DISABLED);
    }

    /** The stack's level of the enchantment, or 0 if it has none or the enchantment is switched off. */
    public static int level(Level level, ResourceKey<Enchantment> key, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Holder<Enchantment> holder = holder(level.registryAccess(), key);
        return holder == null || holder.is(DISABLED) ? 0 : EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    public static boolean has(Level level, ResourceKey<Enchantment> key, @Nullable ItemStack stack) {
        return level(level, key, stack) > 0;
    }
}
