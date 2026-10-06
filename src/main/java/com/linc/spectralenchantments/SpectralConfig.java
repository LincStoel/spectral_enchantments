package com.linc.spectralenchantments;

import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's settings.
 *
 * <ul>
 *   <li>Common ({@code config/spectral_enchantments-common.toml}): switching each enchantment off. Read when
 *       data packs load, so a change takes effect after a restart (or {@code /reload}).</li>
 *   <li>Server ({@code serverconfig/spectral_enchantments-server.toml} in each world, synced to clients):
 *       the Spectral Weapon ranges and Broad Breaker areas.</li>
 * </ul>
 */
public final class SpectralConfig {
    public static final int LEVELS = 3;
    private static final List<Integer> DEFAULT_WEAPON_RANGES = List.of(6, 12, 16);
    private static final List<Integer> DEFAULT_BREAKER_AREAS = List.of(3, 5, 7);
    private static final int MAX_WEAPON_RANGE = 64;
    private static final int MAX_BREAKER_AREA = 33;

    public static final ModConfigSpec COMMON_SPEC;
    private static final ModConfigSpec.BooleanValue BROAD_BREAKER_ENABLED;
    private static final ModConfigSpec.BooleanValue SPECTRAL_WEAPON_ENABLED;

    public static final ModConfigSpec SERVER_SPEC;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> BREAKER_AREAS;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> WEAPON_RANGES;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        BROAD_BREAKER_ENABLED = common
                .comment("Whether Broad Breaker can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("broadBreaker.enabled", true);
        SPECTRAL_WEAPON_ENABLED = common
                .comment("Whether Spectral Weapon can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("spectralWeapon.enabled", true);
        COMMON_SPEC = common.build();

        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        BREAKER_AREAS = server
                .comment("Width and height of the area Broad Breaker mines, for levels I, II and III, in blocks.",
                        "Three odd numbers, each greater than 1 (at most " + MAX_BREAKER_AREA + ") and greater than the one before.",
                        "An invalid list is reset to the default.")
                .define(List.of("broadBreaker", "areas"), () -> DEFAULT_BREAKER_AREAS,
                        value -> isValidLevels(value, MAX_BREAKER_AREA, size -> size % 2 == 1), List.class);
        WEAPON_RANGES = server
                .comment("How far from you Spectral Weapon fights, for levels I, II and III, in blocks.",
                        "A target further away than this is dropped. Three whole numbers, each greater than 1",
                        "(at most " + MAX_WEAPON_RANGE + ") and greater than the one before. An invalid list is reset to the default.")
                .define(List.of("spectralWeapon", "ranges"), () -> DEFAULT_WEAPON_RANGES,
                        value -> isValidLevels(value, MAX_WEAPON_RANGE, range -> true), List.class);
        SERVER_SPEC = server.build();
    }

    private SpectralConfig() {}

    /** One value per level, each above 1, strictly increasing, within {@code max}, and passing {@code extra}. */
    public static boolean isValidLevels(Object value, int max, IntPredicate extra) {
        if (!(value instanceof List<?> list) || list.size() != LEVELS) {
            return false;
        }
        long previous = 1;
        for (Object element : list) {
            if (!(element instanceof Integer || element instanceof Long)) {
                return false;
            }
            long current = ((Number) element).longValue();
            if (current <= previous || current > max || !extra.test((int) current)) {
                return false;
            }
            previous = current;
        }
        return true;
    }

    public static boolean broadBreakerEnabled() {
        return COMMON_SPEC.isLoaded() ? BROAD_BREAKER_ENABLED.get() : BROAD_BREAKER_ENABLED.getDefault();
    }

    public static boolean spectralWeaponEnabled() {
        return COMMON_SPEC.isLoaded() ? SPECTRAL_WEAPON_ENABLED.get() : SPECTRAL_WEAPON_ENABLED.getDefault();
    }

    /** Broad Breaker's area width at {@code level} (levels above III use III's). */
    public static int broadBreakerArea(int level) {
        return forLevel(BREAKER_AREAS, DEFAULT_BREAKER_AREAS, level);
    }

    /** Spectral Weapon's range at {@code level} (levels above III use III's). */
    public static int spectralWeaponRange(int level) {
        return forLevel(WEAPON_RANGES, DEFAULT_WEAPON_RANGES, level);
    }

    /** Defaults until the world's server config is loaded (or synced, on a client). */
    private static int forLevel(ModConfigSpec.ConfigValue<List<? extends Integer>> value, List<Integer> fallback, int level) {
        List<? extends Number> levels = SERVER_SPEC.isLoaded() ? value.get() : fallback;
        return levels.get(Mth.clamp(level, 1, LEVELS) - 1).intValue();
    }
}
