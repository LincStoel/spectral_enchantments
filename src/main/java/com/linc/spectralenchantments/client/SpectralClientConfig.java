package com.linc.spectralenchantments.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings ({@code config/spectral_enchantments-client.toml}). */
public final class SpectralClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue SHADER_BRIGHTNESS;
    private static final ModConfigSpec.BooleanValue SPRINGY_SEATED;
    private static final ModConfigSpec.IntValue INDEX_GLOW_SECONDS;
    private static final ModConfigSpec.BooleanValue INDEX_GLOW_THROUGH_WALLS;
    private static final ModConfigSpec.ConfigValue<String> INDEX_GLOW_COLOR;
    private static final ModConfigSpec.ConfigValue<String> ALARMING_GLOW_COLOR;
    private static final ModConfigSpec.IntValue ALARMING_SHADER_LIGHT;

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
        INDEX_GLOW_SECONDS = builder
                .comment("How long a book picked in the Enchanting Index glows on its shelf, in seconds. It stops sooner once the",
                        "book is taken out.")
                .translation("spectral_enchantments.configuration.enchantingIndex.glowSeconds")
                .defineInRange("enchantingIndex.glowSeconds", 30, 1, 600);
        INDEX_GLOW_THROUGH_WALLS = builder
                .comment("Whether a book picked in the Enchanting Index can be seen glowing through walls.")
                .translation("spectral_enchantments.configuration.enchantingIndex.glowThroughWalls")
                .define("enchantingIndex.glowThroughWalls", true);
        INDEX_GLOW_COLOR = builder
                .comment("The colour books picked in an undyed Enchanting Index glow, as #RRGGBB. A dyed index's books glow in",
                        "its dye colour.")
                .translation("spectral_enchantments.configuration.enchantingIndex.glowColor")
                .define("enchantingIndex.glowColor", "#FFC759", value -> value instanceof String text && parseColor(text) >= 0);
        ALARMING_GLOW_COLOR = builder
                .comment("The colour an Alarming sword glows when monsters are near, as #RRGGBB.")
                .translation("spectral_enchantments.configuration.alarming.glowColor")
                .define("alarming.glowColor", "#7CD3FF", value -> value instanceof String text && parseColor(text) >= 0);
        ALARMING_SHADER_LIGHT = builder
                .comment("How much light an Alarming sword in hand gives off, in the glow colour, under an Iris shaderpack with",
                        "handheld lighting: this much with a monster within a block, fading to none at the edge of the radius.",
                        "0 turns it off.")
                .translation("spectral_enchantments.configuration.alarming.shaderLight")
                .defineInRange("alarming.shaderLight", 8, 0, 15);
        SPEC = builder.build();
    }

    private SpectralClientConfig() {}

    static float shaderBrightness() {
        return SPEC.isLoaded() ? SHADER_BRIGHTNESS.get().floatValue() : SHADER_BRIGHTNESS.getDefault().floatValue();
    }

    static boolean springySeated() {
        return SPEC.isLoaded() ? SPRINGY_SEATED.get() : SPRINGY_SEATED.getDefault();
    }

    public static int indexGlowSeconds() {
        return SPEC.isLoaded() ? INDEX_GLOW_SECONDS.get() : INDEX_GLOW_SECONDS.getDefault();
    }

    public static boolean indexGlowThroughWalls() {
        return SPEC.isLoaded() ? INDEX_GLOW_THROUGH_WALLS.get() : INDEX_GLOW_THROUGH_WALLS.getDefault();
    }

    /** The glow colour for an undyed Enchanting Index, as RGB. */
    public static int indexGlowColor() {
        return parseColor(SPEC.isLoaded() ? INDEX_GLOW_COLOR.get() : INDEX_GLOW_COLOR.getDefault());
    }

    /** The colour an Alarming sword glows, as RGB. */
    static int alarmingGlowColor() {
        return parseColor(SPEC.isLoaded() ? ALARMING_GLOW_COLOR.get() : ALARMING_GLOW_COLOR.getDefault());
    }

    /** The light an Alarming sword in hand gives off under a shaderpack with a monster within a block (0-15). */
    static int alarmingShaderLight() {
        return SPEC.isLoaded() ? ALARMING_SHADER_LIGHT.get() : ALARMING_SHADER_LIGHT.getDefault();
    }

    /** {@code #RRGGBB} (the # optional) as RGB, or -1 if it isn't one. */
    private static int parseColor(String text) {
        String hex = text.strip();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
