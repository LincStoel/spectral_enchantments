package com.linc.spectralenchantments;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SpectralEnchantments.MOD_ID);

    /** Set once a cursed item has been in a player's inventory: from then on its tooltip shows its curses. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> CURSES_REVEALED =
            COMPONENTS.registerComponentType("curses_revealed", builder -> builder
                    .persistent(Unit.CODEC)
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    /** A cooldown on this very stack; see {@link StackCooldowns}. Synced, never saved. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StackCooldowns.Cooldown>> COOLDOWN =
            COMPONENTS.registerComponentType("cooldown", builder -> builder
                    .networkSynchronized(StackCooldowns.Cooldown.STREAM_CODEC));

    private ModComponents() {}
}
