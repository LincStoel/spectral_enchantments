package com.linc.spectralenchantments.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings ({@code config/spectral_enchantments-client.toml}). */
public final class SpectralClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue SHADER_BRIGHTNESS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SHADER_BRIGHTNESS = builder
                .comment("Brightness of the ghost tools and weapons while an Iris shaderpack is in use, from 0 (black) to 1 (as without shaders).")
                .translation("spectral_enchantments.configuration.shaderBrightness")
                .defineInRange("shaderBrightness", 0.5, 0.0, 1.0);
        SPEC = builder.build();
    }

    private SpectralClientConfig() {}

    static float shaderBrightness() {
        return SPEC.isLoaded() ? SHADER_BRIGHTNESS.get().floatValue() : SHADER_BRIGHTNESS.getDefault().floatValue();
    }
}
