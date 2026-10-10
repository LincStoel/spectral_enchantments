package com.linc.spectralenchantments;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * {@code spectral_enchantments:enchantment_treasure_only}: true when the named enchantment is enabled and set to
 * treasure-only in the common config. Gates the pack overlays in {@code pack.mcmeta} that move it from the
 * enchanting table to the treasure tags.
 */
public record EnchantmentTreasureOnlyCondition(EnchantmentEnabledCondition.Which enchantment) implements ICondition {
    public static final MapCodec<EnchantmentTreasureOnlyCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(EnchantmentEnabledCondition.Which.CODEC.fieldOf("enchantment")
                    .forGetter(EnchantmentTreasureOnlyCondition::enchantment))
            .apply(instance, EnchantmentTreasureOnlyCondition::new));

    @Override
    public boolean test(IContext context) {
        return switch (enchantment) {
            case BROAD_BREAKER -> SpectralConfig.broadBreakerEnabled() && SpectralConfig.broadBreakerTreasureOnly();
            case FAR_MINER -> SpectralConfig.farMinerEnabled() && SpectralConfig.farMinerTreasureOnly();
            case GATHER -> SpectralConfig.gatherEnabled() && SpectralConfig.gatherTreasureOnly();
            case BRANCH_FINDING -> SpectralConfig.branchFindingEnabled() && SpectralConfig.branchFindingTreasureOnly();
            case ANIMATED -> SpectralConfig.animatedEnabled() && SpectralConfig.animatedTreasureOnly();
            case CATAPULTING -> SpectralConfig.catapultingEnabled() && SpectralConfig.catapultingTreasureOnly();
            case EXPLOSIVE_BOLTS -> SpectralConfig.explosiveBoltsEnabled() && SpectralConfig.explosiveBoltsTreasureOnly();
            case DOPPELGANGER -> SpectralConfig.doppelgangerEnabled() && SpectralConfig.doppelgangerTreasureOnly();
            case POSSESSION_CURSE -> SpectralConfig.possessionCurseEnabled() && SpectralConfig.possessionCurseTreasureOnly();
            case HAUNTING_CURSE -> SpectralConfig.hauntingCurseEnabled() && SpectralConfig.hauntingCurseTreasureOnly();
        };
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
