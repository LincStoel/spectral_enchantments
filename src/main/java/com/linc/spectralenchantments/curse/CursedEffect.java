package com.linc.spectralenchantments.curse;

import java.util.Set;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * Cursed: given the first time a cursed item lands in a player's inventory. Does nothing by itself; while it
 * lasts {@link Curses} keeps every cursed item the player carries from being dropped or put away. Nothing cures
 * it (not milk, not Ars Nouveau's Dispel, which only removes curable effects): it runs its course.
 */
public class CursedEffect extends MobEffect {
    public CursedEffect() {
        super(MobEffectCategory.HARMFUL, 0x4B1D5E);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {}
}
