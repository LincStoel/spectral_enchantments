package com.linc.spectralenchantments;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * {@code spectral_enchantments:feature_enabled}: true when the named feature (anything other than an enchantment; see
 * {@link EnchantmentEnabledCondition} for those) is switched on in the common config. Gates the feature's recipes.
 */
public record FeatureEnabledCondition(Which feature) implements ICondition {
    public static final MapCodec<FeatureEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Which.CODEC.fieldOf("feature").forGetter(FeatureEnabledCondition::feature))
            .apply(instance, FeatureEnabledCondition::new));

    public enum Which implements StringRepresentable {
        ENCHANTING_INDEX("enchanting_index");

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
        return switch (feature) {
            case ENCHANTING_INDEX -> SpectralConfig.enchantingIndexEnabled();
        };
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
