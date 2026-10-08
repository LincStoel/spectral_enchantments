package com.linc.spectralenchantments;

import com.linc.spectralenchantments.curse.CursedEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SpectralEnchantments.MOD_ID);

    /** While it lasts, cursed items can't be dropped or put in another container. */
    public static final DeferredHolder<MobEffect, MobEffect> CURSED = EFFECTS.register("cursed", CursedEffect::new);

    private ModEffects() {}
}
