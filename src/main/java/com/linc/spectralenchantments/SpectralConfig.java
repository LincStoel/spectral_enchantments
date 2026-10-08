package com.linc.spectralenchantments;

import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's settings.
 *
 * <ul>
 *   <li>Common ({@code config/spectral_enchantments-common.toml}): switching each enchantment off. Read when
 *       data packs load, so a change takes effect after a restart (or {@code /reload}).</li>
 *   <li>Server ({@code serverconfig/spectral_enchantments-server.toml} in each world, synced to clients):
 *       the Spectral Weapon ranges, Broad Breaker areas, Far Miner reaches and speed, and Branch Finding's
 *       duration, search limit and cooldown, Animated's radii, deployment time and recall distance, Catapulting's
 *       durability costs, item damage and block placing, Explosive Bolts' fire spray and explosion, how curses are
 *       revealed and how long the Cursed effect lasts, and the Curse of Possession's possessed weapons.</li>
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
    /** Hearts a catapulted item deals for each rarity: common, uncommon, rare, epic. */
    private static final List<Double> DEFAULT_CATAPULT_RARITY_HEARTS = List.of(1.0, 2.0, 3.0, 4.0);
    private static final double MAX_CATAPULT_HEARTS = 100.0;

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
    private static final ModConfigSpec.BooleanValue CATAPULTING_ENABLED;
    private static final ModConfigSpec.BooleanValue CATAPULTING_TREASURE_ONLY;
    private static final ModConfigSpec.BooleanValue EXPLOSIVE_BOLTS_ENABLED;
    private static final ModConfigSpec.BooleanValue EXPLOSIVE_BOLTS_TREASURE_ONLY;
    private static final ModConfigSpec.BooleanValue POSSESSION_CURSE_ENABLED;
    private static final ModConfigSpec.BooleanValue POSSESSION_CURSE_TREASURE_ONLY;

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
    private static final ModConfigSpec.IntValue ANIMATED_THROWN_RECALL_DISTANCE;
    private static final ModConfigSpec.DoubleValue ANIMATED_RECALL_COOLDOWN;
    private static final ModConfigSpec.IntValue CATAPULTING_BOW_COST;
    private static final ModConfigSpec.IntValue CATAPULTING_WEAPON_COST;
    private static final ModConfigSpec.ConfigValue<List<? extends Double>> CATAPULTING_RARITY_HEARTS;
    private static final ModConfigSpec.BooleanValue CATAPULTING_PLACE_BLOCKS;
    private static final ModConfigSpec.BooleanValue CATAPULTING_USE_ITEMS;
    private static final ModConfigSpec.BooleanValue CATAPULTING_PHYSICS_BLOCKS;
    private static final ModConfigSpec.IntValue EXPLOSIVE_BOLTS_FIRE_RADIUS;
    private static final ModConfigSpec.IntValue EXPLOSIVE_BOLTS_BURN_SECONDS;
    private static final ModConfigSpec.IntValue EXPLOSIVE_BOLTS_GHAST_POWER;
    private static final ModConfigSpec.IntValue EXPLOSIVE_BOLTS_DOUSE_RADIUS;
    private static final ModConfigSpec.BooleanValue CURSES_HIDE_UNTIL_HELD;
    private static final ModConfigSpec.IntValue CURSED_SECONDS;
    private static final ModConfigSpec.DoubleValue POSSESSION_HEALTH_PER_DURABILITY;
    private static final ModConfigSpec.IntValue POSSESSION_MIN_HEALTH;
    private static final ModConfigSpec.IntValue POSSESSION_MAX_HEALTH;
    private static final ModConfigSpec.DoubleValue POSSESSION_HEALTH_PER_HARDNESS;
    private static final ModConfigSpec.DoubleValue POSSESSION_ITEM_HEALTH;
    private static final ModConfigSpec.DoubleValue POSSESSION_ARMOR;
    private static final ModConfigSpec.DoubleValue POSSESSION_DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue POSSESSION_DROP_CHANCE;
    private static final ModConfigSpec.DoubleValue POSSESSION_DROP_DURABILITY;

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
        CATAPULTING_ENABLED = common
                .comment("Whether Catapulting can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("catapulting.enabled", true);
        CATAPULTING_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .define("catapulting.treasure_only", false);
        EXPLOSIVE_BOLTS_ENABLED = common
                .comment("Whether Explosive Bolts can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("explosiveBolts.enabled", true);
        EXPLOSIVE_BOLTS_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .define("explosiveBolts.treasure_only", false);
        POSSESSION_CURSE_ENABLED = common
                .comment("Whether Curse of Possession can be obtained and works. When false it stays registered (so cursed items",
                        "keep it) but does nothing and no longer turns up in loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .define("possessionCurse.enabled", true);
        POSSESSION_CURSE_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .define("possessionCurse.treasure_only", true);
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
        ANIMATED_THROWN_RECALL_DISTANCE = server
                .comment("As recallDistance, for an Animated weapon that was thrown or fired and stands guard where it landed",
                        "(and for weapons tagged spectral_enchantments:animated/thrown_weapons, however they were left).",
                        "Always at least one more than deployRadius.")
                .defineInRange(List.of("animated", "thrownRecallDistance"), 32, 2, 256);
        ANIMATED_RECALL_COOLDOWN = server
                .comment("How long, in seconds, a recalled Animated weapon can't be swung once it is back in hand. 0 for none.")
                .defineInRange(List.of("animated", "recallCooldownSeconds"), 2.0, 0.0, 60.0);
        CATAPULTING_BOW_COST = server
                .comment("Durability a Catapulting bow loses for each shot it fires from the off hand. Unbreaking still applies.")
                .defineInRange(List.of("catapulting", "bowDurabilityCost"), 2, 0, 64);
        CATAPULTING_WEAPON_COST = server
                .comment("Durability a weapon or tool loses when a Catapulting bow fires it. Unbreaking still applies.",
                        "A weapon this would break isn't fired.")
                .defineInRange(List.of("catapulting", "weaponDurabilityCost"), 1, 0, 64);
        CATAPULTING_RARITY_HEARTS = server
                .comment("Hearts of damage dealt by a catapulted item that is neither a weapon nor normally thrown (blocks",
                        "included), by rarity: common, uncommon, rare, epic. A full draw deals this much, a weaker one less.",
                        "Four numbers from 0 to " + (int) MAX_CATAPULT_HEARTS + ". An invalid list is reset to the default.")
                .define(List.of("catapulting", "rarityHearts"), () -> DEFAULT_CATAPULT_RARITY_HEARTS,
                        SpectralConfig::isValidRarityHearts, List.class);
        CATAPULTING_PLACE_BLOCKS = server
                .comment("Whether catapulted blocks are placed where they land. When false they drop as items instead.")
                .define(List.of("catapulting", "placeBlocks"), true);
        CATAPULTING_USE_ITEMS = server
                .comment("Whether fired items are used where they land, as clicking with them would (fire charges light fires,",
                        "spawn eggs hatch, buckets pour, food is eaten, armor is put on...). When false they just drop.")
                .define(List.of("catapulting", "useItems"), true);
        CATAPULTING_PHYSICS_BLOCKS = server
                .comment("With Sable installed: fire blocks as physics objects that set themselves down where they land, and stay",
                        "loose if they hit a creature on the way. Blocks tagged spectral_enchantments:catapulting/no_physics never are.")
                .define(List.of("catapulting", "physicsBlocks"), false);
        EXPLOSIVE_BOLTS_FIRE_RADIUS = server
                .comment("How far a blaze rod fired by Explosive Bolts sprays fire from where it lands, in blocks.")
                .defineInRange(List.of("explosiveBolts", "blazeFireRadius"), 3, 1, 8);
        EXPLOSIVE_BOLTS_BURN_SECONDS = server
                .comment("How long creatures caught in a blaze rod's spray of fire burn, in seconds.")
                .defineInRange(List.of("explosiveBolts", "blazeBurnSeconds"), 5, 1, 60);
        EXPLOSIVE_BOLTS_GHAST_POWER = server
                .comment("Explosion power of the fireball a ghast tear fires (a ghast's own is 1, a creeper's 3).")
                .defineInRange(List.of("explosiveBolts", "ghastExplosionPower"), 1, 1, 5);
        EXPLOSIVE_BOLTS_DOUSE_RADIUS = server
                .comment("How far from where it bursts a water bottle fired by Explosive Bolts puts out fires, in blocks.")
                .defineInRange(List.of("explosiveBolts", "waterDouseRadius"), 4, 1, 16);
        CURSES_HIDE_UNTIL_HELD = server
                .comment("Whether an item's curses stay out of its tooltip until it has been in a player's inventory",
                        "(players in creative mode always see them). Enchanted books always show theirs.")
                .define(List.of("curses", "hideUntilHeld"), true);
        CURSED_SECONDS = server
                .comment("How long the Cursed effect lasts, in seconds. A player gets it the first time a cursed item lands in",
                        "their inventory; while it lasts no cursed item they carry can be dropped or put in another container.",
                        "0 for no effect.")
                .defineInRange(List.of("curses", "cursedSeconds"), 60, 0, 3600);
        POSSESSION_HEALTH_PER_DURABILITY = server
                .comment("A possessed weapon's health for each point of durability the weapon had left (an iron sword has",
                        "250, a diamond sword 1561), kept within minHealth and maxHealth.")
                .defineInRange(List.of("possessionCurse", "healthPerDurability"), 0.05, 0.001, 10.0);
        POSSESSION_MIN_HEALTH = server
                .comment("The least health a possessed weapon (or tool) has, however worn.")
                .defineInRange(List.of("possessionCurse", "minHealth"), 6, 1, 1024);
        POSSESSION_MAX_HEALTH = server
                .comment("The most health a possessed weapon or block has (40 is 20 hearts, a player's). Never less than minHealth.")
                .defineInRange(List.of("possessionCurse", "maxHealth"), 40, 1, 1024);
        POSSESSION_HEALTH_PER_HARDNESS = server
                .comment("A possessed block's health for each point of its hardness (how long it takes to mine: dirt is 0.5,",
                        "stone 1.5, planks 2, iron blocks 5, obsidian 50), kept within itemHealth and maxHealth.",
                        "Unbreakable blocks get maxHealth.")
                .defineInRange(List.of("possessionCurse", "healthPerHardness"), 4.0, 0.0, 1024.0);
        POSSESSION_ITEM_HEALTH = server
                .comment("The health of a possessed item without durability (neither a tool nor a block).")
                .defineInRange(List.of("possessionCurse", "itemHealth"), 1.0, 0.5, 1024.0);
        POSSESSION_ARMOR = server
                .comment("A possessed weapon's armor points (a zombie has 2).")
                .defineInRange(List.of("possessionCurse", "armor"), 2.0, 0.0, 30.0);
        POSSESSION_DAMAGE_MULTIPLIER = server
                .comment("Multiplies the damage a possessed weapon deals, which is otherwise the weapon's own (enchantments included).")
                .defineInRange(List.of("possessionCurse", "damageMultiplier"), 1.0, 0.0, 10.0);
        POSSESSION_DROP_CHANCE = server
                .comment("The chance, from 0 to 1, that a slain possessed weapon drops the weapon, its curses lifted.")
                .defineInRange(List.of("possessionCurse", "dropChance"), 0.1, 0.0, 1.0);
        POSSESSION_DROP_DURABILITY = server
                .comment("How much durability a weapon dropped by a slain possessed weapon has left, as a fraction of its",
                        "maximum (at least 1 point). A weapon freed by Ars Nouveau's Dispel keeps the durability it had.")
                .defineInRange(List.of("possessionCurse", "dropDurability"), 0.05, 0.0, 1.0);
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

    /** One number per rarity, each from 0 to {@link #MAX_CATAPULT_HEARTS}. */
    public static boolean isValidRarityHearts(Object value) {
        if (!(value instanceof List<?> list) || list.size() != DEFAULT_CATAPULT_RARITY_HEARTS.size()) {
            return false;
        }
        for (Object element : list) {
            if (!(element instanceof Number number) || !(number.doubleValue() >= 0) || number.doubleValue() > MAX_CATAPULT_HEARTS) {
                return false;
            }
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

    public static boolean catapultingTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? CATAPULTING_TREASURE_ONLY.get() : CATAPULTING_TREASURE_ONLY.getDefault();
    }

    public static boolean catapultingEnabled() {
        return COMMON_SPEC.isLoaded() ? CATAPULTING_ENABLED.get() : CATAPULTING_ENABLED.getDefault();
    }

    public static boolean explosiveBoltsTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? EXPLOSIVE_BOLTS_TREASURE_ONLY.get() : EXPLOSIVE_BOLTS_TREASURE_ONLY.getDefault();
    }

    public static boolean explosiveBoltsEnabled() {
        return COMMON_SPEC.isLoaded() ? EXPLOSIVE_BOLTS_ENABLED.get() : EXPLOSIVE_BOLTS_ENABLED.getDefault();
    }

    public static boolean possessionCurseTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? POSSESSION_CURSE_TREASURE_ONLY.get() : POSSESSION_CURSE_TREASURE_ONLY.getDefault();
    }

    public static boolean possessionCurseEnabled() {
        return COMMON_SPEC.isLoaded() ? POSSESSION_CURSE_ENABLED.get() : POSSESSION_CURSE_ENABLED.getDefault();
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

    /** How far the owner may stray from a thrown Animated weapon's landing spot; never within its fighting radius. */
    public static int animatedThrownRecallDistance() {
        int distance = SERVER_SPEC.isLoaded() ? ANIMATED_THROWN_RECALL_DISTANCE.get() : ANIMATED_THROWN_RECALL_DISTANCE.getDefault();
        return Math.max(distance, animatedDeployRadius() + 1);
    }

    /** How long a recalled Animated weapon can't be swung, in ticks. */
    public static int animatedRecallCooldownTicks() {
        return (int) Math.round(20 * (SERVER_SPEC.isLoaded() ? ANIMATED_RECALL_COOLDOWN.get() : ANIMATED_RECALL_COOLDOWN.getDefault()));
    }

    /** Durability a Catapulting bow loses per shot. */
    public static int catapultingBowCost() {
        return SERVER_SPEC.isLoaded() ? CATAPULTING_BOW_COST.get() : CATAPULTING_BOW_COST.getDefault();
    }

    /** Durability a catapulted weapon loses when fired. */
    public static int catapultingWeaponCost() {
        return SERVER_SPEC.isLoaded() ? CATAPULTING_WEAPON_COST.get() : CATAPULTING_WEAPON_COST.getDefault();
    }

    /** Damage (health points, not hearts) a catapulted item of this rarity deals at full draw. */
    public static float catapultingRarityDamage(Rarity rarity) {
        List<? extends Number> hearts = SERVER_SPEC.isLoaded() ? CATAPULTING_RARITY_HEARTS.get() : DEFAULT_CATAPULT_RARITY_HEARTS;
        return 2f * hearts.get(Mth.clamp(rarity.ordinal(), 0, hearts.size() - 1)).floatValue();
    }

    /** Whether catapulted blocks are placed where they land. */
    public static boolean catapultingPlacesBlocks() {
        return SERVER_SPEC.isLoaded() ? CATAPULTING_PLACE_BLOCKS.get() : CATAPULTING_PLACE_BLOCKS.getDefault();
    }

    /** Whether fired items are used where they land. */
    public static boolean catapultingUsesItems() {
        return SERVER_SPEC.isLoaded() ? CATAPULTING_USE_ITEMS.get() : CATAPULTING_USE_ITEMS.getDefault();
    }

    /** Whether, with Sable, fired blocks fly as physics objects. */
    public static boolean catapultingPhysicsBlocks() {
        return SERVER_SPEC.isLoaded() ? CATAPULTING_PHYSICS_BLOCKS.get() : CATAPULTING_PHYSICS_BLOCKS.getDefault();
    }

    /** How far a fired water bottle puts out fires, in blocks. */
    public static int explosiveBoltsDouseRadius() {
        return SERVER_SPEC.isLoaded() ? EXPLOSIVE_BOLTS_DOUSE_RADIUS.get() : EXPLOSIVE_BOLTS_DOUSE_RADIUS.getDefault();
    }

    /** How far a blaze rod's spray of fire reaches, in blocks. */
    public static int explosiveBoltsFireRadius() {
        return SERVER_SPEC.isLoaded() ? EXPLOSIVE_BOLTS_FIRE_RADIUS.get() : EXPLOSIVE_BOLTS_FIRE_RADIUS.getDefault();
    }

    /** How long creatures caught in a blaze rod's spray burn, in seconds. */
    public static int explosiveBoltsBurnSeconds() {
        return SERVER_SPEC.isLoaded() ? EXPLOSIVE_BOLTS_BURN_SECONDS.get() : EXPLOSIVE_BOLTS_BURN_SECONDS.getDefault();
    }

    /** Explosion power of a ghast tear's fireball. */
    public static int explosiveBoltsGhastPower() {
        return SERVER_SPEC.isLoaded() ? EXPLOSIVE_BOLTS_GHAST_POWER.get() : EXPLOSIVE_BOLTS_GHAST_POWER.getDefault();
    }

    /** Whether curses stay out of tooltips until the item has been in a player's inventory. */
    public static boolean cursesHideUntilHeld() {
        return SERVER_SPEC.isLoaded() ? CURSES_HIDE_UNTIL_HELD.get() : CURSES_HIDE_UNTIL_HELD.getDefault();
    }

    /** How long the Cursed effect lasts, in ticks (0 for none). */
    public static int cursedTicks() {
        return 20 * (SERVER_SPEC.isLoaded() ? CURSED_SECONDS.get() : CURSED_SECONDS.getDefault());
    }

    /** A possessed weapon's health for each point of durability left. */
    public static double possessionHealthPerDurability() {
        return SERVER_SPEC.isLoaded() ? POSSESSION_HEALTH_PER_DURABILITY.get() : POSSESSION_HEALTH_PER_DURABILITY.getDefault();
    }

    public static int possessionMinHealth() {
        return SERVER_SPEC.isLoaded() ? POSSESSION_MIN_HEALTH.get() : POSSESSION_MIN_HEALTH.getDefault();
    }

    /** Never below {@link #possessionMinHealth()}. */
    public static int possessionMaxHealth() {
        int max = SERVER_SPEC.isLoaded() ? POSSESSION_MAX_HEALTH.get() : POSSESSION_MAX_HEALTH.getDefault();
        return Math.max(max, possessionMinHealth());
    }

    /** A possessed block's health for each point of hardness. */
    public static double possessionHealthPerHardness() {
        return SERVER_SPEC.isLoaded() ? POSSESSION_HEALTH_PER_HARDNESS.get() : POSSESSION_HEALTH_PER_HARDNESS.getDefault();
    }

    public static float possessionItemHealth() {
        return (SERVER_SPEC.isLoaded() ? POSSESSION_ITEM_HEALTH.get() : POSSESSION_ITEM_HEALTH.getDefault()).floatValue();
    }

    public static double possessionArmor() {
        return SERVER_SPEC.isLoaded() ? POSSESSION_ARMOR.get() : POSSESSION_ARMOR.getDefault();
    }

    public static float possessionDamageMultiplier() {
        return (SERVER_SPEC.isLoaded() ? POSSESSION_DAMAGE_MULTIPLIER.get() : POSSESSION_DAMAGE_MULTIPLIER.getDefault()).floatValue();
    }

    public static double possessionDropChance() {
        return SERVER_SPEC.isLoaded() ? POSSESSION_DROP_CHANCE.get() : POSSESSION_DROP_CHANCE.getDefault();
    }

    /** Durability left on a slain possessed weapon's drop, as a fraction of the maximum. */
    public static double possessionDropDurability() {
        return SERVER_SPEC.isLoaded() ? POSSESSION_DROP_DURABILITY.get() : POSSESSION_DROP_DURABILITY.getDefault();
    }

    /** Defaults until the world's server config is loaded (or synced, on a client). */
    private static int forLevel(ModConfigSpec.ConfigValue<List<? extends Integer>> value, List<Integer> fallback, int level) {
        List<? extends Number> levels = SERVER_SPEC.isLoaded() ? value.get() : fallback;
        return levels.get(Mth.clamp(level, 1, LEVELS) - 1).intValue();
    }
}
