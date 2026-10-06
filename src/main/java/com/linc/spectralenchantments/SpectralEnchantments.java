package com.linc.spectralenchantments;

import com.linc.spectralenchantments.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(SpectralEnchantments.MOD_ID)
public final class SpectralEnchantments {
    public static final String MOD_ID = "spectral_enchantments";

    public SpectralEnchantments(IEventBus modBus) {
        modBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.register(CommonEvents.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
