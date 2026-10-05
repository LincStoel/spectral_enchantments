package com.linc.broadbreaker;

import com.linc.broadbreaker.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(BroadBreaker.MOD_ID)
public final class BroadBreaker {
    public static final String MOD_ID = "broad_breaker";

    public BroadBreaker(IEventBus modBus) {
        modBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.register(CommonEvents.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
