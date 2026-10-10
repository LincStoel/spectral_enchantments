package com.linc.spectralenchantments;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * {@code spectral_enchantments:enchantment_enabled}: true when the named enchantment is switched on in the
 * common config. Gates the Ars Nouveau recipes, and (negated) the pack overlays in {@code pack.mcmeta} that take a
 * disabled enchantment out of enchanting tables, loot and trades.
 */
public record EnchantmentEnabledCondition(Which enchantment) implements ICondition {
    public static final MapCodec<EnchantmentEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Which.CODEC.fieldOf("enchantment").forGetter(EnchantmentEnabledCondition::enchantment))
            .apply(instance, EnchantmentEnabledCondition::new));

    public enum Which implements StringRepresentable {
        BROAD_BREAKER("broad_breaker"),
        FAR_MINER("far_miner"),
        GATHER("gather"),
        BRANCH_FINDING("branch_finding"),
        ANIMATED("animated"),
        CATAPULTING("catapulting"),
        EXPLOSIVE_BOLTS("explosive_bolts"),
        DOPPELGANGER("doppelganger"),
        POSSESSION_CURSE("possession_curse"),
        HAUNTING_CURSE("haunting_curse");

        static final Codec<Which> CODEC = StringRepresentable.fromEnum(Which::values);

        private final String name;

        Which(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public boolean test(IContext context) {
        return switch (enchantment) {
            case BROAD_BREAKER -> SpectralConfig.broadBreakerEnabled();
            case FAR_MINER -> SpectralConfig.farMinerEnabled();
            case GATHER -> SpectralConfig.gatherEnabled();
            case BRANCH_FINDING -> SpectralConfig.branchFindingEnabled();
            case ANIMATED -> SpectralConfig.animatedEnabled();
            case CATAPULTING -> SpectralConfig.catapultingEnabled();
            case EXPLOSIVE_BOLTS -> SpectralConfig.explosiveBoltsEnabled();
            case DOPPELGANGER -> SpectralConfig.doppelgangerEnabled();
            case POSSESSION_CURSE -> SpectralConfig.possessionCurseEnabled();
            case HAUNTING_CURSE -> SpectralConfig.hauntingCurseEnabled();
        };
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
