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
        SPECTRAL_WEAPON("spectral_weapon");

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
            case SPECTRAL_WEAPON -> SpectralConfig.spectralWeaponEnabled();
        };
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
