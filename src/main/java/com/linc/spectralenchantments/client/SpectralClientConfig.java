package com.linc.spectralenchantments.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings ({@code config/spectral_enchantments-client.toml}). */
public final class SpectralClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue SHADER_BRIGHTNESS;
    private static final ModConfigSpec.BooleanValue SPRINGY_SEATED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SHADER_BRIGHTNESS = builder
                .comment("Brightness of the ghost tools and weapons while an Iris shaderpack is in use, from 0 (black) to 1 (as without shaders).")
                .translation("spectral_enchantments.configuration.shaderBrightness")
                .defineInRange("shaderBrightness", 0.5, 0.0, 1.0);
        SPRINGY_SEATED = builder
                .comment("Players bouncing on a Springy ghost slime are drawn sitting on it.")
                .translation("spectral_enchantments.configuration.springySeated")
                .define("springySeated", true);
        SPEC = builder.build();
    }

    private SpectralClientConfig() {}

    static float shaderBrightness() {
        return SPEC.isLoaded() ? SHADER_BRIGHTNESS.get().floatValue() : SHADER_BRIGHTNESS.getDefault().floatValue();
    }

    static boolean springySeated() {
        return SPEC.isLoaded() ? SPRINGY_SEATED.get() : SPRINGY_SEATED.getDefault();
    }
}
