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
 *       the Spectral Weapon ranges, Broad Breaker areas, Far Miner reaches and speed, and Branch Finding's
 *       duration, search limit and cooldown, and Animated's radii, deployment time and recall distance.</li>
 * </ul>
 */
public final class SpectralConfig {
    public static final int LEVELS = 3;
    private static final List<Integer> DEFAULT_WEAPON_RANGES = List.of(6, 12, 16);
    private static final List<Integer> DEFAULT_BREAKER_AREAS = List.of(3, 5, 7);
    private static final List<Integer> DEFAULT_FAR_MINER_RANGES = List.of(8, 12, 16);
    /** Far Miner reaches start past normal block reach (4.5). */
    private static final int MIN_FAR_MINER_RANGE = 5;
    private static final int MAX_WEAPON_RANGE = 64;
    private static final int MAX_BREAKER_AREA = 33;
    private static final int MAX_FAR_MINER_RANGE = 64;
    /** Keeps the outline packet well under the 1 MiB payload limit even for pathological leaf/log mixes. */
    private static final int MAX_BRANCH_FINDING_LEAVES = 16384;

    public static final ModConfigSpec COMMON_SPEC;
    private static final ModConfigSpec.BooleanValue BROAD_BREAKER_ENABLED;
    private static final ModConfigSpec.BooleanValue BROAD_BREAKER_TREASURE_ONLY;
    private static final ModConfigSpec.BooleanValue SPECTRAL_WEAPON_ENABLED;
    private static final ModConfigSpec.BooleanValue SPECTRAL_WEAPON_TREASURE_ONLY;
    private static final ModConfigSpec.BooleanValue FAR_MINER_ENABLED;
    private static final ModConfigSpec.BooleanValue FAR_MINER_TREASURE_ONLY;
    private static final ModConfigSpec.BooleanValue GATHER_ENABLED;
    private static final ModConfigSpec.BooleanValue GATHER_TREASURE_ONLY;
    private static final ModConfigSpec.BooleanValue BRANCH_FINDING_ENABLED;
    private static final ModConfigSpec.BooleanValue BRANCH_FINDING_TREASURE_ONLY;
    private static final ModConfigSpec.BooleanValue ANIMATED_ENABLED;
    private static final ModConfigSpec.BooleanValue ANIMATED_TREASURE_ONLY;

    public static final ModConfigSpec SERVER_SPEC;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> BREAKER_AREAS;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> WEAPON_RANGES;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> FAR_MINER_RANGES;
    private static final ModConfigSpec.DoubleValue FAR_MINER_SPEED;
    private static final ModConfigSpec.IntValue BRANCH_FINDING_DURATION;
    private static final ModConfigSpec.IntValue BRANCH_FINDING_MAX_LEAVES;
    private static final ModConfigSpec.IntValue BRANCH_FINDING_COOLDOWN;
    private static final ModConfigSpec.IntValue ANIMATED_STAND_RADIUS;
    private static final ModConfigSpec.IntValue ANIMATED_DEPLOY_RADIUS;
    private static final ModConfigSpec.IntValue ANIMATED_DEPLOY_SECONDS;
    private static final ModConfigSpec.IntValue ANIMATED_RECALL_DISTANCE;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        BROAD_BREAKER_ENABLED = common
                .comment("Whether Broad Breaker can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("broadBreaker.enabled", true);
        BROAD_BREAKER_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .define("broadBreaker.treasure_only", false);
        SPECTRAL_WEAPON_ENABLED = common
                .comment("Whether Spectral Weapon can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("spectralWeapon.enabled", true);
        SPECTRAL_WEAPON_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .define("spectralWeapon.treasure_only", false);
        FAR_MINER_ENABLED = common
                .comment("Whether Far Miner can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("farMiner.enabled", true);
        FAR_MINER_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .define("farMiner.treasure_only", false);
        GATHER_ENABLED = common
                .comment("Whether Gather can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("gather.enabled", true);
        GATHER_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .define("gather.treasure_only", false);
        BRANCH_FINDING_ENABLED = common
                .comment("Whether Branch Finding can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("branchFinding.enabled", true);
        BRANCH_FINDING_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .define("branchFinding.treasure_only", false);
        ANIMATED_ENABLED = common
                .comment("Whether Animated can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("animated.enabled", true);
        ANIMATED_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .define("animated.treasure_only", true);
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
        FAR_MINER_RANGES = server
                .comment("The farthest Far Miner can mine from the player's eyes, for levels I, II and III, in blocks.",
                        "Three whole numbers, each at least " + MIN_FAR_MINER_RANGE + " (at most " + MAX_FAR_MINER_RANGE + ") and greater than the one before.",
                        "An invalid list is reset to the default.")
                .define(List.of("farMiner", "ranges"), () -> DEFAULT_FAR_MINER_RANGES,
                        value -> isValidLevels(value, MAX_FAR_MINER_RANGE, range -> range >= MIN_FAR_MINER_RANGE), List.class);
        FAR_MINER_SPEED = server
                .comment("Far Miner's mining speed, as a fraction of normal (like mining while airborne, which is 0.2).",
                        "Replaces vanilla's underwater and airborne penalties rather than adding to them (Mining Fatigue still applies).",
                        "Normal mining is unaffected.")
                .defineInRange(List.of("farMiner", "speedMultiplier"), 0.2, 0.01, 1.0);
        BRANCH_FINDING_DURATION = server
                .comment("How long Branch Finding's outlines last, in seconds. They fade steadily from the moment they appear.")
                .defineInRange(List.of("branchFinding", "durationSeconds"), 16, 1, 300);
        BRANCH_FINDING_MAX_LEAVES = server
                .comment("The most leaf blocks one use of Branch Finding searches, nearest first. Logs touching leaves past",
                        "this limit aren't outlined. Bounds the work done for huge or merged canopies.")
                .defineInRange(List.of("branchFinding", "maxLeaves"), 4096, 16, MAX_BRANCH_FINDING_LEAVES);
        BRANCH_FINDING_COOLDOWN = server
                .comment("Ticks (20 per second) before the tool can be used again after Branch Finding, so holding the use",
                        "button doesn't drain durability. 0 for none.")
                .defineInRange(List.of("branchFinding", "cooldownTicks"), 20, 0, 1200);
        ANIMATED_STAND_RADIUS = server
                .comment("How far from an armor stand its Animated weapon looks for hostile mobs to fight, in blocks.")
                .defineInRange(List.of("animated", "standRadius"), 8, 1, 32);
        ANIMATED_DEPLOY_RADIUS = server
                .comment("How far from the spot it was left at a deployed Animated weapon fights hostile mobs, in blocks.")
                .defineInRange(List.of("animated", "deployRadius"), 4, 1, 16);
        ANIMATED_DEPLOY_SECONDS = server
                .comment("How long a deployed Animated weapon fights before flying back to its owner, in seconds.")
                .defineInRange(List.of("animated", "deploySeconds"), 15, 1, 600);
        ANIMATED_RECALL_DISTANCE = server
                .comment("How far the owner can stray from a deployed Animated weapon before it flies back to them, in blocks.",
                        "Always at least one more than deployRadius.")
                .defineInRange(List.of("animated", "recallDistance"), 16, 2, 128);
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

    public static boolean broadBreakerTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? BROAD_BREAKER_TREASURE_ONLY.get() : BROAD_BREAKER_TREASURE_ONLY.getDefault();
    }

    public static boolean broadBreakerEnabled() {
        return COMMON_SPEC.isLoaded() ? BROAD_BREAKER_ENABLED.get() : BROAD_BREAKER_ENABLED.getDefault();
    }

    public static boolean spectralWeaponTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? SPECTRAL_WEAPON_TREASURE_ONLY.get() : SPECTRAL_WEAPON_TREASURE_ONLY.getDefault();
    }

    public static boolean spectralWeaponEnabled() {
        return COMMON_SPEC.isLoaded() ? SPECTRAL_WEAPON_ENABLED.get() : SPECTRAL_WEAPON_ENABLED.getDefault();
    }

    public static boolean farMinerTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? FAR_MINER_TREASURE_ONLY.get() : FAR_MINER_TREASURE_ONLY.getDefault();
    }

    public static boolean farMinerEnabled() {
        return COMMON_SPEC.isLoaded() ? FAR_MINER_ENABLED.get() : FAR_MINER_ENABLED.getDefault();
    }

    public static boolean gatherTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? GATHER_TREASURE_ONLY.get() : GATHER_TREASURE_ONLY.getDefault();
    }

    public static boolean gatherEnabled() {
        return COMMON_SPEC.isLoaded() ? GATHER_ENABLED.get() : GATHER_ENABLED.getDefault();
    }

    public static boolean branchFindingTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? BRANCH_FINDING_TREASURE_ONLY.get() : BRANCH_FINDING_TREASURE_ONLY.getDefault();
    }

    public static boolean branchFindingEnabled() {
        return COMMON_SPEC.isLoaded() ? BRANCH_FINDING_ENABLED.get() : BRANCH_FINDING_ENABLED.getDefault();
    }

    public static boolean animatedTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? ANIMATED_TREASURE_ONLY.get() : ANIMATED_TREASURE_ONLY.getDefault();
    }

    public static boolean animatedEnabled() {
        return COMMON_SPEC.isLoaded() ? ANIMATED_ENABLED.get() : ANIMATED_ENABLED.getDefault();
    }

    /** Broad Breaker's area width at {@code level} (levels above III use III's). */
    public static int broadBreakerArea(int level) {
        return forLevel(BREAKER_AREAS, DEFAULT_BREAKER_AREAS, level);
    }

    /** Spectral Weapon's range at {@code level} (levels above III use III's). */
    public static int spectralWeaponRange(int level) {
        return forLevel(WEAPON_RANGES, DEFAULT_WEAPON_RANGES, level);
    }

    /** The farthest Far Miner mines (from the eyes) at {@code level} (levels above III use III's). */
    public static int farMinerRange(int level) {
        return forLevel(FAR_MINER_RANGES, DEFAULT_FAR_MINER_RANGES, level);
    }

    /** Far Miner's mining speed as a fraction of normal. */
    public static float farMinerSpeed() {
        return (SERVER_SPEC.isLoaded() ? FAR_MINER_SPEED.get() : FAR_MINER_SPEED.getDefault()).floatValue();
    }

    /** How long Branch Finding's outlines take to fade away, in seconds. */
    public static int branchFindingDurationSeconds() {
        return SERVER_SPEC.isLoaded() ? BRANCH_FINDING_DURATION.get() : BRANCH_FINDING_DURATION.getDefault();
    }

    /** The most leaves one use of Branch Finding searches. */
    public static int branchFindingMaxLeaves() {
        return SERVER_SPEC.isLoaded() ? BRANCH_FINDING_MAX_LEAVES.get() : BRANCH_FINDING_MAX_LEAVES.getDefault();
    }

    /** Item cooldown after a use of Branch Finding, in ticks. */
    public static int branchFindingCooldownTicks() {
        return SERVER_SPEC.isLoaded() ? BRANCH_FINDING_COOLDOWN.get() : BRANCH_FINDING_COOLDOWN.getDefault();
    }

    /** How far from its armor stand an Animated weapon looks for targets. */
    public static int animatedStandRadius() {
        return SERVER_SPEC.isLoaded() ? ANIMATED_STAND_RADIUS.get() : ANIMATED_STAND_RADIUS.getDefault();
    }

    /** How far from its anchor a deployed Animated weapon looks for targets. */
    public static int animatedDeployRadius() {
        return SERVER_SPEC.isLoaded() ? ANIMATED_DEPLOY_RADIUS.get() : ANIMATED_DEPLOY_RADIUS.getDefault();
    }

    /** How long a deployment lasts, in ticks. */
    public static int animatedDeployTicks() {
        return 20 * (SERVER_SPEC.isLoaded() ? ANIMATED_DEPLOY_SECONDS.get() : ANIMATED_DEPLOY_SECONDS.getDefault());
    }

    /** How far the owner may stray from a deployed weapon's anchor; never within its fighting radius. */
    public static int animatedRecallDistance() {
        int distance = SERVER_SPEC.isLoaded() ? ANIMATED_RECALL_DISTANCE.get() : ANIMATED_RECALL_DISTANCE.getDefault();
        return Math.max(distance, animatedDeployRadius() + 1);
    }

    /** Defaults until the world's server config is loaded (or synced, on a client). */
    private static int forLevel(ModConfigSpec.ConfigValue<List<? extends Integer>> value, List<Integer> fallback, int level) {
        List<? extends Number> levels = SERVER_SPEC.isLoaded() ? value.get() : fallback;
        return levels.get(Mth.clamp(level, 1, LEVELS) - 1).intValue();
    }
}
