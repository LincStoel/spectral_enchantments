package com.linc.spectralenchantments;

import com.linc.spectralenchantments.study.EnchantmentStudy;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's settings.
 *
 * <ul>
 *   <li>Common ({@code config/spectral_enchantments-common.toml}): switching each enchantment off, read when data
 *       packs load, so a change takes effect after a restart (or {@code /reload}); and switching enchantment study at
 *       lecterns off, which takes effect at once.</li>
 *   <li>Server ({@code serverconfig/spectral_enchantments-server.toml} in each world, synced to clients):
 *       Broad Breaker areas, Far Miner reaches and speed, and Branch Finding's
 *       duration, search limit and cooldown, Animated's follow range, radii, deployment time and recall distance, Catapulting's
 *       durability costs, item damage and block placing, Explosive Bolts' fire spray and explosion, Doppelganger's
 *       lure radius, range, cooldown and emote timing, how curses are revealed and how long the Cursed effect lasts, the Curse of Possession's possessed weapons, and the
 *       Curse of Haunting's ghost, and the curse chances, experience costs and timing of studying and copying
 *       enchantments at a lectern.</li>
 * </ul>
 */
public final class SpectralConfig {
    public static final int LEVELS = 3;
    private static final List<Integer> DEFAULT_BREAKER_AREAS = List.of(3, 5, 7);
    private static final List<Integer> DEFAULT_FAR_MINER_RANGES = List.of(8, 12, 16);
    /** Far Miner reaches start past normal block reach (4.5). */
    private static final int MIN_FAR_MINER_RANGE = 5;
    private static final int MAX_COMPANION_RANGE = 64;
    /** Broad Breaker's widest area. Bounds the per-tick area work, the ghost tools drawn and the area packet. */
    public static final int MAX_BREAKER_AREA = 15;
    /** The most extra blocks one Broad Breaker area can hold (a full square, less the mined block). */
    public static final int MAX_BREAKER_EXTRAS = MAX_BREAKER_AREA * MAX_BREAKER_AREA - 1;
    private static final int MAX_FAR_MINER_RANGE = 64;
    /** Keeps the outline packet well under the 1 MiB payload limit even for pathological leaf/log mixes. */
    private static final int MAX_BRANCH_FINDING_LEAVES = 16384;
    /** Hearts a catapulted item deals for each rarity: common, uncommon, rare, epic. */
    private static final List<Double> DEFAULT_CATAPULT_RARITY_HEARTS = List.of(1.0, 2.0, 3.0, 4.0);
    private static final double MAX_CATAPULT_HEARTS = 100.0;

    public static final ModConfigSpec COMMON_SPEC;
    private static final ModConfigSpec.BooleanValue BROAD_BREAKER_ENABLED;
    private static final ModConfigSpec.BooleanValue BROAD_BREAKER_TREASURE_ONLY;
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
    private static final ModConfigSpec.BooleanValue DOPPELGANGER_ENABLED;
    private static final ModConfigSpec.BooleanValue DOPPELGANGER_TREASURE_ONLY;
    private static final ModConfigSpec.BooleanValue POSSESSION_CURSE_ENABLED;
    private static final ModConfigSpec.BooleanValue POSSESSION_CURSE_TREASURE_ONLY;
    private static final ModConfigSpec.BooleanValue HAUNTING_CURSE_ENABLED;
    private static final ModConfigSpec.BooleanValue HAUNTING_CURSE_TREASURE_ONLY;
    private static final ModConfigSpec.BooleanValue STUDY_ENABLED;
    private static final ModConfigSpec.BooleanValue STUDYING_ENABLED;
    private static final ModConfigSpec.BooleanValue COPYING_ENABLED;

    public static final ModConfigSpec SERVER_SPEC;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> BREAKER_AREAS;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> FAR_MINER_RANGES;
    private static final ModConfigSpec.DoubleValue FAR_MINER_SPEED;
    private static final ModConfigSpec.IntValue BRANCH_FINDING_DURATION;
    private static final ModConfigSpec.IntValue BRANCH_FINDING_MAX_LEAVES;
    private static final ModConfigSpec.IntValue BRANCH_FINDING_COOLDOWN;
    private static final ModConfigSpec.IntValue ANIMATED_COMPANION_RANGE;
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
    private static final ModConfigSpec.BooleanValue CATAPULTING_EXPLODE_CRYSTALS;
    private static final ModConfigSpec.IntValue EXPLOSIVE_BOLTS_FIRE_RADIUS;
    private static final ModConfigSpec.IntValue EXPLOSIVE_BOLTS_BURN_SECONDS;
    private static final ModConfigSpec.IntValue EXPLOSIVE_BOLTS_GHAST_POWER;
    private static final ModConfigSpec.IntValue EXPLOSIVE_BOLTS_DOUSE_RADIUS;
    private static final ModConfigSpec.IntValue DOPPELGANGER_LURE_RADIUS;
    private static final ModConfigSpec.IntValue DOPPELGANGER_MAX_DISTANCE;
    private static final ModConfigSpec.IntValue DOPPELGANGER_COOLDOWN;
    private static final ModConfigSpec.IntValue DOPPELGANGER_EMOTE_MIN;
    private static final ModConfigSpec.IntValue DOPPELGANGER_EMOTE_MAX;
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
    private static final ModConfigSpec.IntValue HAUNTING_APPEAR_MIN;
    private static final ModConfigSpec.IntValue HAUNTING_APPEAR_MAX;
    private static final ModConfigSpec.IntValue HAUNTING_STARE_SECONDS;
    private static final ModConfigSpec.IntValue HAUNTING_STARE_MIN_DISTANCE;
    private static final ModConfigSpec.IntValue HAUNTING_STARE_MAX_DISTANCE;
    private static final ModConfigSpec.IntValue HAUNTING_APPROACH_DISTANCE;
    private static final ModConfigSpec.IntValue HAUNTING_MONSTER_RADIUS;
    private static final ModConfigSpec.IntValue HAUNTING_VANISH_DISTANCE;
    private static final ModConfigSpec.IntValue HAUNTING_BUFF_RADIUS;
    private static final ModConfigSpec.IntValue HAUNTING_BUFF_LEVEL;
    private static final ModConfigSpec.IntValue HAUNTING_BUFF_SECONDS;
    private static final ModConfigSpec.IntValue HAUNTING_BANISH_MIN;
    private static final ModConfigSpec.IntValue HAUNTING_BANISH_MAX;
    private static final ModConfigSpec.IntValue HAUNTING_CAST_MIN;
    private static final ModConfigSpec.IntValue HAUNTING_CAST_MAX;
    private static final ModConfigSpec.BooleanValue HAUNTING_MONSTER_GHOSTS;
    private static final ModConfigSpec.IntValue HAUNTING_MONSTER_GHOST_CAP;
    private static final ModConfigSpec.DoubleValue HAUNTING_NETHER_CHANCE;
    private static final ModConfigSpec.DoubleValue STUDYING_CURSE_CHANCE_MAX;
    private static final ModConfigSpec.DoubleValue STUDYING_CURSE_CHANCE_MIN;
    private static final ModConfigSpec.IntValue STUDYING_CURSE_CHANCE_MIN_LEVEL;
    private static final ModConfigSpec.IntValue STUDYING_XP_COST;
    private static final ModConfigSpec.IntValue STUDYING_DURATION;
    private static final ModConfigSpec.DoubleValue COPYING_CURSE_CHANCE_MAX;
    private static final ModConfigSpec.DoubleValue COPYING_CURSE_CHANCE_MIN;
    private static final ModConfigSpec.IntValue COPYING_CURSE_CHANCE_MIN_LEVEL;
    private static final ModConfigSpec.IntValue COPYING_XP_COST;
    private static final ModConfigSpec.IntValue COPYING_DURATION;
    private static final ModConfigSpec.DoubleValue STUDY_TREASURE_MULTIPLIER;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        BROAD_BREAKER_ENABLED = common
                .comment("Whether Broad Breaker can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.broadBreaker.enabled")
                .worldRestart()
                .define("broadBreaker.enabled", true);
        BROAD_BREAKER_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.broadBreaker.treasure_only")
                .worldRestart()
                .define("broadBreaker.treasure_only", false);
        FAR_MINER_ENABLED = common
                .comment("Whether Far Miner can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.farMiner.enabled")
                .worldRestart()
                .define("farMiner.enabled", true);
        FAR_MINER_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.farMiner.treasure_only")
                .worldRestart()
                .define("farMiner.treasure_only", false);
        GATHER_ENABLED = common
                .comment("Whether Gather can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.gather.enabled")
                .worldRestart()
                .define("gather.enabled", true);
        GATHER_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.gather.treasure_only")
                .worldRestart()
                .define("gather.treasure_only", false);
        BRANCH_FINDING_ENABLED = common
                .comment("Whether Branch Finding can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.branchFinding.enabled")
                .worldRestart()
                .define("branchFinding.enabled", true);
        BRANCH_FINDING_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.branchFinding.treasure_only")
                .worldRestart()
                .define("branchFinding.treasure_only", false);
        ANIMATED_ENABLED = common
                .comment("Whether Animated can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.animated.enabled")
                .worldRestart()
                .define("animated.enabled", true);
        ANIMATED_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.animated.treasure_only")
                .worldRestart()
                .define("animated.treasure_only", true);
        CATAPULTING_ENABLED = common
                .comment("Whether Catapulting can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.catapulting.enabled")
                .worldRestart()
                .define("catapulting.enabled", true);
        CATAPULTING_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.catapulting.treasure_only")
                .worldRestart()
                .define("catapulting.treasure_only", false);
        EXPLOSIVE_BOLTS_ENABLED = common
                .comment("Whether Explosive Bolts can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.explosiveBolts.enabled")
                .worldRestart()
                .define("explosiveBolts.enabled", true);
        EXPLOSIVE_BOLTS_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.explosiveBolts.treasure_only")
                .worldRestart()
                .define("explosiveBolts.treasure_only", false);
        DOPPELGANGER_ENABLED = common
                .comment("Whether Doppelganger can be obtained and works. When false it stays registered (so enchanted items",
                        "keep it) but does nothing and no longer turns up in enchanting tables, loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.doppelganger.enabled")
                .worldRestart()
                .define("doppelganger.enabled", true);
        DOPPELGANGER_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.doppelganger.treasure_only")
                .worldRestart()
                .define("doppelganger.treasure_only", false);
        POSSESSION_CURSE_ENABLED = common
                .comment("Whether Curse of Possession can be obtained and works. When false it stays registered (so cursed items",
                        "keep it) but does nothing and no longer turns up in loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.possessionCurse.enabled")
                .worldRestart()
                .define("possessionCurse.enabled", true);
        POSSESSION_CURSE_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.possessionCurse.treasure_only")
                .worldRestart()
                .define("possessionCurse.treasure_only", true);
        HAUNTING_CURSE_ENABLED = common
                .comment("Whether Curse of Haunting can be obtained and works. When false it stays registered (so cursed items",
                        "keep it) but does nothing and no longer turns up in loot, trades or recipes.",
                        "Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.hauntingCurse.enabled")
                .worldRestart()
                .define("hauntingCurse.enabled", true);
        HAUNTING_CURSE_TREASURE_ONLY = common
                .comment("If true, this enchantment is treasure-only: it can't be rolled at the enchanting table and is found",
                        "the way Mending is (treasure loot, villager trades). Takes effect after a restart or /reload.")
                .translation("spectral_enchantments.configuration.hauntingCurse.treasure_only")
                .worldRestart()
                .define("hauntingCurse.treasure_only", true);
        STUDY_ENABLED = common
                .comment("Whether enchantments can be studied or copied at a lectern at all. When false neither works, whatever",
                        "their own settings say.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.enabled")
                .define("enchantmentStudy.enabled", true);
        STUDYING_ENABLED = common
                .comment("Whether an enchanted item can be studied: used on a lectern holding a blank book and quill, with an",
                        "amethyst shard in the off hand, the item is destroyed to write one of its enchantments, at level I, into",
                        "the book.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.studying.enabled")
                .define("enchantmentStudy.studying.enabled", true);
        COPYING_ENABLED = common
                .comment("Whether an enchanted book can be copied: used on a lectern holding a blank book and quill, one of its",
                        "enchantments is written, at level I, into the book. The enchanted book is kept.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.copying.enabled")
                .define("enchantmentStudy.copying.enabled", true);
        COMMON_SPEC = common.build();

        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        BREAKER_AREAS = server
                .comment("Width and height of the area Broad Breaker mines, for levels I, II and III, in blocks.",
                        "Three odd numbers, each greater than 1 (at most " + MAX_BREAKER_AREA + ") and greater than the one before.",
                        "An invalid list is reset to the default.")
                .translation("spectral_enchantments.configuration.broadBreaker.areas")
                .define(List.of("broadBreaker", "areas"), () -> DEFAULT_BREAKER_AREAS,
                        value -> isValidLevels(value, MAX_BREAKER_AREA, size -> size % 2 == 1), List.class);
        FAR_MINER_RANGES = server
                .comment("The farthest Far Miner can mine from the player's eyes, for levels I, II and III, in blocks.",
                        "Three whole numbers, each at least " + MIN_FAR_MINER_RANGE + " (at most " + MAX_FAR_MINER_RANGE + ") and greater than the one before.",
                        "An invalid list is reset to the default.")
                .translation("spectral_enchantments.configuration.farMiner.ranges")
                .define(List.of("farMiner", "ranges"), () -> DEFAULT_FAR_MINER_RANGES,
                        value -> isValidLevels(value, MAX_FAR_MINER_RANGE, range -> range >= MIN_FAR_MINER_RANGE), List.class);
        FAR_MINER_SPEED = server
                .comment("Far Miner's mining speed, as a fraction of normal (like mining while airborne, which is 0.2).",
                        "Replaces vanilla's underwater and airborne penalties rather than adding to them (Mining Fatigue still applies).",
                        "Normal mining is unaffected.")
                .translation("spectral_enchantments.configuration.farMiner.speedMultiplier")
                .defineInRange(List.of("farMiner", "speedMultiplier"), 0.2, 0.01, 1.0);
        BRANCH_FINDING_DURATION = server
                .comment("How long Branch Finding's outlines last, in seconds. They fade steadily from the moment they appear.")
                .translation("spectral_enchantments.configuration.branchFinding.durationSeconds")
                .defineInRange(List.of("branchFinding", "durationSeconds"), 16, 1, 300);
        BRANCH_FINDING_MAX_LEAVES = server
                .comment("The most leaf blocks one use of Branch Finding searches, nearest first. Logs touching leaves past",
                        "this limit aren't outlined. Bounds the work done for huge or merged canopies.")
                .translation("spectral_enchantments.configuration.branchFinding.maxLeaves")
                .defineInRange(List.of("branchFinding", "maxLeaves"), 4096, 16, MAX_BRANCH_FINDING_LEAVES);
        BRANCH_FINDING_COOLDOWN = server
                .comment("Ticks (20 per second) before the tool can be used again after Branch Finding, so holding the use",
                        "button doesn't drain durability. 0 for none.")
                .translation("spectral_enchantments.configuration.branchFinding.cooldownTicks")
                .defineInRange(List.of("branchFinding", "cooldownTicks"), 20, 0, 1200);
        ANIMATED_COMPANION_RANGE = server
                .comment("How far from you an Animated weapon in your hotbar fights, in blocks. A target further away than this is dropped.")
                .translation("spectral_enchantments.configuration.animated.companionRange")
                .defineInRange(List.of("animated", "companionRange"), 12, 2, MAX_COMPANION_RANGE);
        ANIMATED_STAND_RADIUS = server
                .comment("How far from an armor stand its Animated weapon looks for hostile mobs to fight, in blocks.")
                .translation("spectral_enchantments.configuration.animated.standRadius")
                .defineInRange(List.of("animated", "standRadius"), 8, 1, 32);
        ANIMATED_DEPLOY_RADIUS = server
                .comment("How far from the spot it was left at a deployed Animated weapon fights hostile mobs, in blocks.")
                .translation("spectral_enchantments.configuration.animated.deployRadius")
                .defineInRange(List.of("animated", "deployRadius"), 4, 1, 16);
        ANIMATED_DEPLOY_SECONDS = server
                .comment("How long a deployed Animated weapon fights before flying back to its owner, in seconds.")
                .translation("spectral_enchantments.configuration.animated.deploySeconds")
                .defineInRange(List.of("animated", "deploySeconds"), 15, 1, 600);
        ANIMATED_RECALL_DISTANCE = server
                .comment("How far the owner can stray from a deployed Animated weapon before it flies back to them, in blocks.",
                        "Always at least one more than deployRadius.")
                .translation("spectral_enchantments.configuration.animated.recallDistance")
                .defineInRange(List.of("animated", "recallDistance"), 16, 2, 128);
        ANIMATED_THROWN_RECALL_DISTANCE = server
                .comment("As recallDistance, for an Animated weapon that was thrown or fired and stands guard where it landed",
                        "(and for weapons tagged spectral_enchantments:animated/thrown_weapons, however they were left).",
                        "Always at least one more than deployRadius.")
                .translation("spectral_enchantments.configuration.animated.thrownRecallDistance")
                .defineInRange(List.of("animated", "thrownRecallDistance"), 32, 2, 256);
        ANIMATED_RECALL_COOLDOWN = server
                .comment("How long, in seconds, a recalled Animated weapon can't be swung once it is back in hand. 0 for none.")
                .translation("spectral_enchantments.configuration.animated.recallCooldownSeconds")
                .defineInRange(List.of("animated", "recallCooldownSeconds"), 2.0, 0.0, 60.0);
        CATAPULTING_BOW_COST = server
                .comment("Durability a Catapulting bow loses for each shot it fires from the off hand. Unbreaking still applies.")
                .translation("spectral_enchantments.configuration.catapulting.bowDurabilityCost")
                .defineInRange(List.of("catapulting", "bowDurabilityCost"), 2, 0, 64);
        CATAPULTING_WEAPON_COST = server
                .comment("Durability a weapon or tool loses when a Catapulting bow fires it. Unbreaking still applies.",
                        "A weapon this would break isn't fired.")
                .translation("spectral_enchantments.configuration.catapulting.weaponDurabilityCost")
                .defineInRange(List.of("catapulting", "weaponDurabilityCost"), 1, 0, 64);
        CATAPULTING_RARITY_HEARTS = server
                .comment("Hearts of damage dealt by a catapulted item that is neither a weapon nor normally thrown (blocks",
                        "included), by rarity: common, uncommon, rare, epic. A full draw deals this much, a weaker one less.",
                        "Four numbers from 0 to " + (int) MAX_CATAPULT_HEARTS + ". An invalid list is reset to the default.")
                .translation("spectral_enchantments.configuration.catapulting.rarityHearts")
                .define(List.of("catapulting", "rarityHearts"), () -> DEFAULT_CATAPULT_RARITY_HEARTS,
                        SpectralConfig::isValidRarityHearts, List.class);
        CATAPULTING_PLACE_BLOCKS = server
                .comment("Whether catapulted blocks are placed where they land. When false they drop as items instead.")
                .translation("spectral_enchantments.configuration.catapulting.placeBlocks")
                .define(List.of("catapulting", "placeBlocks"), true);
        CATAPULTING_USE_ITEMS = server
                .comment("Whether fired items are used where they land, as clicking with them would (fire charges light fires,",
                        "spawn eggs hatch, buckets pour, food is eaten, armor is put on...). When false they just drop.")
                .translation("spectral_enchantments.configuration.catapulting.useItems")
                .define(List.of("catapulting", "useItems"), true);
        CATAPULTING_EXPLODE_CRYSTALS = server
                .comment("Whether a fired end crystal that can't be set down where it lands explodes there, as a crystal does.",
                        "It never does where its shooter couldn't build. When false it drops instead.")
                .translation("spectral_enchantments.configuration.catapulting.explodeCrystals")
                .define(List.of("catapulting", "explodeCrystals"), true);
        CATAPULTING_PHYSICS_BLOCKS = server
                .comment("With Sable installed: fire blocks as physics objects that set themselves down where they land, and stay",
                        "loose if they hit a creature on the way. Blocks tagged spectral_enchantments:catapulting/no_physics never are.")
                .translation("spectral_enchantments.configuration.catapulting.physicsBlocks")
                .define(List.of("catapulting", "physicsBlocks"), false);
        EXPLOSIVE_BOLTS_FIRE_RADIUS = server
                .comment("How far a blaze rod fired by Explosive Bolts sprays fire from where it lands, in blocks.")
                .translation("spectral_enchantments.configuration.explosiveBolts.blazeFireRadius")
                .defineInRange(List.of("explosiveBolts", "blazeFireRadius"), 3, 1, 8);
        EXPLOSIVE_BOLTS_BURN_SECONDS = server
                .comment("How long creatures caught in a blaze rod's spray of fire burn, in seconds.")
                .translation("spectral_enchantments.configuration.explosiveBolts.blazeBurnSeconds")
                .defineInRange(List.of("explosiveBolts", "blazeBurnSeconds"), 5, 1, 60);
        EXPLOSIVE_BOLTS_GHAST_POWER = server
                .comment("Explosion power of the fireball a ghast tear fires (a ghast's own is 1, a creeper's 3).")
                .translation("spectral_enchantments.configuration.explosiveBolts.ghastExplosionPower")
                .defineInRange(List.of("explosiveBolts", "ghastExplosionPower"), 1, 1, 5);
        EXPLOSIVE_BOLTS_DOUSE_RADIUS = server
                .comment("How far from where it bursts a water bottle fired by Explosive Bolts puts out fires, in blocks.")
                .translation("spectral_enchantments.configuration.explosiveBolts.waterDouseRadius")
                .defineInRange(List.of("explosiveBolts", "waterDouseRadius"), 4, 1, 16);
        DOPPELGANGER_LURE_RADIUS = server
                .comment("How far from a Doppelganger's double a monster after its owner can be and still be drawn to the double",
                        "instead, in blocks.")
                .translation("spectral_enchantments.configuration.doppelganger.lureRadius")
                .defineInRange(List.of("doppelganger", "lureRadius"), 16, 1, 64);
        DOPPELGANGER_MAX_DISTANCE = server
                .comment("How far the owner can stray from their double, still sneaking, before it vanishes, in blocks.")
                .translation("spectral_enchantments.configuration.doppelganger.maxDistance")
                .defineInRange(List.of("doppelganger", "maxDistance"), 32, 4, 128);
        DOPPELGANGER_COOLDOWN = server
                .comment("Ticks (20 per second) after a double appears before the next one can, so tapping sneak doesn't",
                        "keep summoning them. 0 for none.")
                .translation("spectral_enchantments.configuration.doppelganger.cooldownTicks")
                .defineInRange(List.of("doppelganger", "cooldownTicks"), 20, 0, 1200);
        DOPPELGANGER_EMOTE_MIN = server
                .comment("With Quark installed: the shortest wait between a double's emotes, in seconds.")
                .translation("spectral_enchantments.configuration.doppelganger.emoteMinSeconds")
                .defineInRange(List.of("doppelganger", "emoteMinSeconds"), 5, 1, 600);
        DOPPELGANGER_EMOTE_MAX = server
                .comment("With Quark installed: the longest wait between a double's emotes, in seconds. Never less than",
                        "emoteMinSeconds.")
                .translation("spectral_enchantments.configuration.doppelganger.emoteMaxSeconds")
                .defineInRange(List.of("doppelganger", "emoteMaxSeconds"), 20, 1, 600);
        CURSES_HIDE_UNTIL_HELD = server
                .comment("Whether an item's curses stay out of its tooltip until it has been in a player's inventory, or for",
                        "armor (anything worn), until it has been worn. Players in creative mode always see them; enchanted",
                        "books always show theirs.")
                .translation("spectral_enchantments.configuration.curses.hideUntilHeld")
                .define(List.of("curses", "hideUntilHeld"), true);
        CURSED_SECONDS = server
                .comment("How long the Cursed effect lasts, in seconds. A player gets it the first time a cursed item lands in",
                        "their inventory (or cursed armor is put on); while it lasts no cursed item they carry can be dropped or put in another container.",
                        "0 for no effect.")
                .translation("spectral_enchantments.configuration.curses.cursedSeconds")
                .defineInRange(List.of("curses", "cursedSeconds"), 60, 0, 3600);
        POSSESSION_HEALTH_PER_DURABILITY = server
                .comment("A possessed weapon's health for each point of durability the weapon had left (an iron sword has",
                        "250, a diamond sword 1561), kept within minHealth and maxHealth.")
                .translation("spectral_enchantments.configuration.possessionCurse.healthPerDurability")
                .defineInRange(List.of("possessionCurse", "healthPerDurability"), 0.05, 0.001, 10.0);
        POSSESSION_MIN_HEALTH = server
                .comment("The least health a possessed weapon (or tool) has, however worn.")
                .translation("spectral_enchantments.configuration.possessionCurse.minHealth")
                .defineInRange(List.of("possessionCurse", "minHealth"), 6, 1, 1024);
        POSSESSION_MAX_HEALTH = server
                .comment("The most health a possessed weapon or block has (40 is 20 hearts, a player's). Never less than minHealth.")
                .translation("spectral_enchantments.configuration.possessionCurse.maxHealth")
                .defineInRange(List.of("possessionCurse", "maxHealth"), 40, 1, 1024);
        POSSESSION_HEALTH_PER_HARDNESS = server
                .comment("A possessed block's health for each point of its hardness (how long it takes to mine: dirt is 0.5,",
                        "stone 1.5, planks 2, iron blocks 5, obsidian 50), kept within itemHealth and maxHealth.",
                        "Unbreakable blocks get maxHealth.")
                .translation("spectral_enchantments.configuration.possessionCurse.healthPerHardness")
                .defineInRange(List.of("possessionCurse", "healthPerHardness"), 4.0, 0.0, 1024.0);
        POSSESSION_ITEM_HEALTH = server
                .comment("The health of a possessed item without durability (neither a tool nor a block).")
                .translation("spectral_enchantments.configuration.possessionCurse.itemHealth")
                .defineInRange(List.of("possessionCurse", "itemHealth"), 1.0, 0.5, 1024.0);
        POSSESSION_ARMOR = server
                .comment("A possessed weapon's armor points (a zombie has 2).")
                .translation("spectral_enchantments.configuration.possessionCurse.armor")
                .defineInRange(List.of("possessionCurse", "armor"), 2.0, 0.0, 30.0);
        POSSESSION_DAMAGE_MULTIPLIER = server
                .comment("Multiplies the damage a possessed weapon deals, which is otherwise the weapon's own (enchantments included).")
                .translation("spectral_enchantments.configuration.possessionCurse.damageMultiplier")
                .defineInRange(List.of("possessionCurse", "damageMultiplier"), 1.0, 0.0, 10.0);
        POSSESSION_DROP_CHANCE = server
                .comment("The chance, from 0 to 1, that a slain possessed weapon drops the weapon, its curses lifted.")
                .translation("spectral_enchantments.configuration.possessionCurse.dropChance")
                .defineInRange(List.of("possessionCurse", "dropChance"), 0.1, 0.0, 1.0);
        POSSESSION_DROP_DURABILITY = server
                .comment("How much durability a weapon dropped by a slain possessed weapon has left, as a fraction of its",
                        "maximum (at least 1 point). A weapon freed by Ars Nouveau's Dispel keeps the durability it had.")
                .translation("spectral_enchantments.configuration.possessionCurse.dropDurability")
                .defineInRange(List.of("possessionCurse", "dropDurability"), 0.05, 0.0, 1.0);
        HAUNTING_APPEAR_MIN = server
                .comment("While a player wears armor with the Curse of Haunting, the shortest wait before their ghost appears",
                        "(again), in seconds.")
                .translation("spectral_enchantments.configuration.hauntingCurse.appearMinSeconds")
                .defineInRange(List.of("hauntingCurse", "appearMinSeconds"), 180, 10, 7200);
        HAUNTING_APPEAR_MAX = server
                .comment("The longest wait before a haunted player's ghost appears, in seconds. Never less than appearMinSeconds.")
                .translation("spectral_enchantments.configuration.hauntingCurse.appearMaxSeconds")
                .defineInRange(List.of("hauntingCurse", "appearMaxSeconds"), 480, 10, 7200);
        HAUNTING_STARE_SECONDS = server
                .comment("How long the ghost stares before it goes, in seconds. Time spent with monsters doesn't count.")
                .translation("spectral_enchantments.configuration.hauntingCurse.stareSeconds")
                .defineInRange(List.of("hauntingCurse", "stareSeconds"), 30, 1, 600);
        HAUNTING_STARE_MIN_DISTANCE = server
                .comment("The nearest to the player the ghost appears, in blocks.")
                .translation("spectral_enchantments.configuration.hauntingCurse.stareMinDistance")
                .defineInRange(List.of("hauntingCurse", "stareMinDistance"), 12, 4, 64);
        HAUNTING_STARE_MAX_DISTANCE = server
                .comment("The farthest from the player the ghost appears, in blocks. Never less than stareMinDistance.")
                .translation("spectral_enchantments.configuration.hauntingCurse.stareMaxDistance")
                .defineInRange(List.of("hauntingCurse", "stareMaxDistance"), 20, 4, 64);
        HAUNTING_APPROACH_DISTANCE = server
                .comment("How close the player can come to a staring ghost before it vanishes, in blocks. Always less than",
                        "stareMinDistance.")
                .translation("spectral_enchantments.configuration.hauntingCurse.approachDistance")
                .defineInRange(List.of("hauntingCurse", "approachDistance"), 8, 1, 63);
        HAUNTING_MONSTER_RADIUS = server
                .comment("How close to the player a monster must be for the ghost to leave off staring and go to it, in blocks.")
                .translation("spectral_enchantments.configuration.hauntingCurse.monsterRadius")
                .defineInRange(List.of("hauntingCurse", "monsterRadius"), 16, 1, 64);
        HAUNTING_VANISH_DISTANCE = server
                .comment("How far the player can go from a staring ghost before it vanishes, in blocks. Always more than",
                        "stareMaxDistance.")
                .translation("spectral_enchantments.configuration.hauntingCurse.vanishDistance")
                .defineInRange(List.of("hauntingCurse", "vanishDistance"), 32, 5, 128);
        HAUNTING_BUFF_RADIUS = server
                .comment("When the ghost casts its spell, how close to the monster it haunts other monsters must be to share its",
                        "Speed, Strength or Resistance, in blocks.")
                .translation("spectral_enchantments.configuration.hauntingCurse.buffRadius")
                .defineInRange(List.of("hauntingCurse", "buffRadius"), 5, 0, 32);
        HAUNTING_BUFF_LEVEL = server
                .comment("The level of Speed, Strength or Resistance the ghost's spell gives (1 is level I).")
                .translation("spectral_enchantments.configuration.hauntingCurse.buffLevel")
                .defineInRange(List.of("hauntingCurse", "buffLevel"), 1, 1, 5);
        HAUNTING_BUFF_SECONDS = server
                .comment("How long the Speed, Strength or Resistance from the ghost's spell lasts, in seconds.")
                .translation("spectral_enchantments.configuration.hauntingCurse.buffSeconds")
                .defineInRange(List.of("hauntingCurse", "buffSeconds"), 15, 1, 600);
        HAUNTING_BANISH_MIN = server
                .comment("When a player strikes the ghost, the shortest time it stays gone, in seconds.")
                .translation("spectral_enchantments.configuration.hauntingCurse.banishMinSeconds")
                .defineInRange(List.of("hauntingCurse", "banishMinSeconds"), 10, 1, 600);
        HAUNTING_BANISH_MAX = server
                .comment("When a player strikes the ghost, the longest time it stays gone, in seconds. Never less than",
                        "banishMinSeconds.")
                .translation("spectral_enchantments.configuration.hauntingCurse.banishMaxSeconds")
                .defineInRange(List.of("hauntingCurse", "banishMaxSeconds"), 20, 1, 600);
        HAUNTING_CAST_MIN = server
                .comment("While haunting a monster, the shortest wait between the ghost raising its arms like an evoker casting a",
                        "spell, in seconds.")
                .translation("spectral_enchantments.configuration.hauntingCurse.castMinSeconds")
                .defineInRange(List.of("hauntingCurse", "castMinSeconds"), 4, 1, 600);
        HAUNTING_CAST_MAX = server
                .comment("The longest wait between the ghost's spells, in seconds. Never less than castMinSeconds.")
                .translation("spectral_enchantments.configuration.hauntingCurse.castMaxSeconds")
                .defineInRange(List.of("hauntingCurse", "castMaxSeconds"), 10, 1, 600);
        HAUNTING_MONSTER_GHOSTS = server
                .comment("Whether a monster wearing armor with the Curse of Haunting has its own ghost beside it. Each ghost is an",
                        "extra entity, which adds up where many such monsters gather (mob farms).")
                .translation("spectral_enchantments.configuration.hauntingCurse.monsterGhosts")
                .define(List.of("hauntingCurse", "monsterGhosts"), true);
        HAUNTING_MONSTER_GHOST_CAP = server
                .comment("At most this many monsters' ghosts within 32 blocks of each other; past it, a haunted monster has no ghost",
                        "(until it next picks a target with fewer about). Keeps mob farms from filling with ghosts.")
                .translation("spectral_enchantments.configuration.hauntingCurse.monsterGhostCap")
                .defineInRange(List.of("hauntingCurse", "monsterGhostCap"), 4, 1, 64);
        HAUNTING_NETHER_CHANCE = server
                .comment("The chance, from 0 to 1, that a monster spawning in the Nether wears a piece of armor with the Curse of",
                        "Haunting, on top of the usual small chance anywhere.")
                .translation("spectral_enchantments.configuration.hauntingCurse.netherChance")
                .defineInRange(List.of("hauntingCurse", "netherChance"), 0.02, 0.0, 1.0);
        STUDYING_CURSE_CHANCE_MAX = server
                .comment("The chance, from 0 to 1, that studying an item at level 0 writes a random curse instead of its enchantment.",
                        "The chance falls in a straight line from this to curseChanceMin as the player's level rises to",
                        "curseChanceMinLevel.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.studying.curseChanceMax")
                .defineInRange(List.of("enchantmentStudy", "studying", "curseChanceMax"), 0.1, 0.0, 1.0);
        STUDYING_CURSE_CHANCE_MIN = server
                .comment("The chance, from 0 to 1, that studying an item writes a curse at curseChanceMinLevel and above.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.studying.curseChanceMin")
                .defineInRange(List.of("enchantmentStudy", "studying", "curseChanceMin"), 0.0005, 0.0, 1.0);
        STUDYING_CURSE_CHANCE_MIN_LEVEL = server
                .comment("The experience level at which the curse chance for studying an item bottoms out at curseChanceMin.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.studying.curseChanceMinLevel")
                .defineInRange(List.of("enchantmentStudy", "studying", "curseChanceMinLevel"), 30, 1, 1000);
        STUDYING_XP_COST = server
                .comment("Experience points studying an item costs (27 is what it takes to reach level 3 from nothing).",
                        "Free in creative.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.studying.xpCost")
                .defineInRange(List.of("enchantmentStudy", "studying", "xpCost"), 27, 0, 10000);
        STUDYING_DURATION = server
                .comment("How long use must be held to study an item, in ticks (20 a second).")
                .translation("spectral_enchantments.configuration.enchantmentStudy.studying.durationTicks")
                .defineInRange(List.of("enchantmentStudy", "studying", "durationTicks"), 40, 10, 200);
        COPYING_CURSE_CHANCE_MAX = server
                .comment("The chance, from 0 to 1, that copying a book at level 0 writes a random curse instead of its enchantment.",
                        "The chance falls in a straight line from this to curseChanceMin as the player's level rises to",
                        "curseChanceMinLevel.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.copying.curseChanceMax")
                .defineInRange(List.of("enchantmentStudy", "copying", "curseChanceMax"), 0.1, 0.0, 1.0);
        COPYING_CURSE_CHANCE_MIN = server
                .comment("The chance, from 0 to 1, that copying a book writes a curse at curseChanceMinLevel and above.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.copying.curseChanceMin")
                .defineInRange(List.of("enchantmentStudy", "copying", "curseChanceMin"), 0.0005, 0.0, 1.0);
        COPYING_CURSE_CHANCE_MIN_LEVEL = server
                .comment("The experience level at which the curse chance for copying a book bottoms out at curseChanceMin.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.copying.curseChanceMinLevel")
                .defineInRange(List.of("enchantmentStudy", "copying", "curseChanceMinLevel"), 30, 1, 1000);
        COPYING_XP_COST = server
                .comment("Experience points copying a book costs (27 is what it takes to reach level 3 from nothing).",
                        "Free in creative.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.copying.xpCost")
                .defineInRange(List.of("enchantmentStudy", "copying", "xpCost"), 27, 0, 10000);
        COPYING_DURATION = server
                .comment("How long use must be held to copy a book, in ticks (20 a second).")
                .translation("spectral_enchantments.configuration.enchantmentStudy.copying.durationTicks")
                .defineInRange(List.of("enchantmentStudy", "copying", "durationTicks"), 40, 10, 200);
        STUDY_TREASURE_MULTIPLIER = server
                .comment("Multiplies the experience cost when the enchantment studied or copied is a treasure enchantment",
                        "(Mending and the like; curses don't count). 1 for no extra cost.")
                .translation("spectral_enchantments.configuration.enchantmentStudy.treasureMultiplier")
                .defineInRange(List.of("enchantmentStudy", "treasureMultiplier"), 2.0, 1.0, 10.0);
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

    public static boolean doppelgangerTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? DOPPELGANGER_TREASURE_ONLY.get() : DOPPELGANGER_TREASURE_ONLY.getDefault();
    }

    public static boolean doppelgangerEnabled() {
        return COMMON_SPEC.isLoaded() ? DOPPELGANGER_ENABLED.get() : DOPPELGANGER_ENABLED.getDefault();
    }

    public static boolean possessionCurseTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? POSSESSION_CURSE_TREASURE_ONLY.get() : POSSESSION_CURSE_TREASURE_ONLY.getDefault();
    }

    public static boolean possessionCurseEnabled() {
        return COMMON_SPEC.isLoaded() ? POSSESSION_CURSE_ENABLED.get() : POSSESSION_CURSE_ENABLED.getDefault();
    }

    public static boolean hauntingCurseTreasureOnly() {
        return COMMON_SPEC.isLoaded() ? HAUNTING_CURSE_TREASURE_ONLY.get() : HAUNTING_CURSE_TREASURE_ONLY.getDefault();
    }

    public static boolean hauntingCurseEnabled() {
        return COMMON_SPEC.isLoaded() ? HAUNTING_CURSE_ENABLED.get() : HAUNTING_CURSE_ENABLED.getDefault();
    }

    /** Whether enchanted items can be studied at a lectern: enchantment study and studying both on. */
    public static boolean studyingEnabled() {
        return COMMON_SPEC.isLoaded() ? STUDY_ENABLED.get() && STUDYING_ENABLED.get()
                : STUDY_ENABLED.getDefault() && STUDYING_ENABLED.getDefault();
    }

    /** Whether enchanted books can be copied at a lectern: enchantment study and copying both on. */
    public static boolean copyingEnabled() {
        return COMMON_SPEC.isLoaded() ? STUDY_ENABLED.get() && COPYING_ENABLED.get()
                : STUDY_ENABLED.getDefault() && COPYING_ENABLED.getDefault();
    }

    /** Broad Breaker's area width at {@code level} (levels above III use III's). */
    public static int broadBreakerArea(int level) {
        return forLevel(BREAKER_AREAS, DEFAULT_BREAKER_AREAS, level);
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

    /** How far from its owner a hotbar Animated weapon fights, in blocks. */
    public static int animatedCompanionRange() {
        return SERVER_SPEC.isLoaded() ? ANIMATED_COMPANION_RANGE.get() : ANIMATED_COMPANION_RANGE.getDefault();
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

    /** Whether a fired end crystal that can't be set down explodes. */
    public static boolean catapultingExplodesCrystals() {
        return SERVER_SPEC.isLoaded() ? CATAPULTING_EXPLODE_CRYSTALS.get() : CATAPULTING_EXPLODE_CRYSTALS.getDefault();
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

    /** How far from a double a monster after its owner is drawn to it, in blocks. */
    public static int doppelgangerLureRadius() {
        return SERVER_SPEC.isLoaded() ? DOPPELGANGER_LURE_RADIUS.get() : DOPPELGANGER_LURE_RADIUS.getDefault();
    }

    /** How far the owner may stray from their double before it vanishes, in blocks. */
    public static int doppelgangerMaxDistance() {
        return SERVER_SPEC.isLoaded() ? DOPPELGANGER_MAX_DISTANCE.get() : DOPPELGANGER_MAX_DISTANCE.getDefault();
    }

    /** Ticks between one double appearing and the next. */
    public static int doppelgangerCooldownTicks() {
        return SERVER_SPEC.isLoaded() ? DOPPELGANGER_COOLDOWN.get() : DOPPELGANGER_COOLDOWN.getDefault();
    }

    /** Shortest wait between a double's Quark emotes, in ticks. */
    public static int doppelgangerEmoteMinTicks() {
        return 20 * (SERVER_SPEC.isLoaded() ? DOPPELGANGER_EMOTE_MIN.get() : DOPPELGANGER_EMOTE_MIN.getDefault());
    }

    /** Longest wait between a double's Quark emotes, in ticks; never below {@link #doppelgangerEmoteMinTicks()}. */
    public static int doppelgangerEmoteMaxTicks() {
        int max = 20 * (SERVER_SPEC.isLoaded() ? DOPPELGANGER_EMOTE_MAX.get() : DOPPELGANGER_EMOTE_MAX.getDefault());
        return Math.max(max, doppelgangerEmoteMinTicks());
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

    /** Shortest wait before a haunted player's ghost appears, in ticks. */
    public static int hauntingAppearMinTicks() {
        return 20 * (SERVER_SPEC.isLoaded() ? HAUNTING_APPEAR_MIN.get() : HAUNTING_APPEAR_MIN.getDefault());
    }

    /** Longest wait before a haunted player's ghost appears, in ticks; never below {@link #hauntingAppearMinTicks()}. */
    public static int hauntingAppearMaxTicks() {
        int max = 20 * (SERVER_SPEC.isLoaded() ? HAUNTING_APPEAR_MAX.get() : HAUNTING_APPEAR_MAX.getDefault());
        return Math.max(max, hauntingAppearMinTicks());
    }

    /** How long the ghost stares before going, in ticks. */
    public static int hauntingStareTicks() {
        return 20 * (SERVER_SPEC.isLoaded() ? HAUNTING_STARE_SECONDS.get() : HAUNTING_STARE_SECONDS.getDefault());
    }

    public static int hauntingStareMinDistance() {
        return SERVER_SPEC.isLoaded() ? HAUNTING_STARE_MIN_DISTANCE.get() : HAUNTING_STARE_MIN_DISTANCE.getDefault();
    }

    /** Never below {@link #hauntingStareMinDistance()}. */
    public static int hauntingStareMaxDistance() {
        int max = SERVER_SPEC.isLoaded() ? HAUNTING_STARE_MAX_DISTANCE.get() : HAUNTING_STARE_MAX_DISTANCE.getDefault();
        return Math.max(max, hauntingStareMinDistance());
    }

    /** How close the player can come to a staring ghost before it vanishes; always below {@link #hauntingStareMinDistance()}. */
    public static int hauntingApproachDistance() {
        int distance = SERVER_SPEC.isLoaded() ? HAUNTING_APPROACH_DISTANCE.get() : HAUNTING_APPROACH_DISTANCE.getDefault();
        return Math.min(distance, hauntingStareMinDistance() - 1);
    }

    public static int hauntingMonsterRadius() {
        return SERVER_SPEC.isLoaded() ? HAUNTING_MONSTER_RADIUS.get() : HAUNTING_MONSTER_RADIUS.getDefault();
    }

    /** How far from its owner the ghost can be before it goes: well past anywhere it would stare or haunt from. */
    public static int hauntingLeashDistance() {
        return 2 * Math.max(hauntingVanishDistance(), hauntingMonsterRadius() + hauntingBuffRadius());
    }

    /** How far the player can go from a staring ghost before it vanishes; always above {@link #hauntingStareMaxDistance()}. */
    public static int hauntingVanishDistance() {
        int distance = SERVER_SPEC.isLoaded() ? HAUNTING_VANISH_DISTANCE.get() : HAUNTING_VANISH_DISTANCE.getDefault();
        return Math.max(distance, hauntingStareMaxDistance() + 1);
    }

    public static int hauntingBuffRadius() {
        return SERVER_SPEC.isLoaded() ? HAUNTING_BUFF_RADIUS.get() : HAUNTING_BUFF_RADIUS.getDefault();
    }

    public static int hauntingBuffLevel() {
        return SERVER_SPEC.isLoaded() ? HAUNTING_BUFF_LEVEL.get() : HAUNTING_BUFF_LEVEL.getDefault();
    }

    public static int hauntingBuffTicks() {
        return 20 * (SERVER_SPEC.isLoaded() ? HAUNTING_BUFF_SECONDS.get() : HAUNTING_BUFF_SECONDS.getDefault());
    }

    /** Shortest time a struck ghost stays gone, in ticks. */
    public static int hauntingBanishMinTicks() {
        return 20 * (SERVER_SPEC.isLoaded() ? HAUNTING_BANISH_MIN.get() : HAUNTING_BANISH_MIN.getDefault());
    }

    /** Longest time a struck ghost stays gone, in ticks; never below {@link #hauntingBanishMinTicks()}. */
    public static int hauntingBanishMaxTicks() {
        int max = 20 * (SERVER_SPEC.isLoaded() ? HAUNTING_BANISH_MAX.get() : HAUNTING_BANISH_MAX.getDefault());
        return Math.max(max, hauntingBanishMinTicks());
    }

    /** Shortest wait between the ghost's spells, in ticks. */
    public static int hauntingCastMinTicks() {
        return 20 * (SERVER_SPEC.isLoaded() ? HAUNTING_CAST_MIN.get() : HAUNTING_CAST_MIN.getDefault());
    }

    /** Longest wait between the ghost's spells, in ticks; never below {@link #hauntingCastMinTicks()}. */
    public static int hauntingCastMaxTicks() {
        int max = 20 * (SERVER_SPEC.isLoaded() ? HAUNTING_CAST_MAX.get() : HAUNTING_CAST_MAX.getDefault());
        return Math.max(max, hauntingCastMinTicks());
    }

    /** Whether monsters wearing the Curse of Haunting get ghosts of their own. */
    public static boolean hauntingMonsterGhosts() {
        return SERVER_SPEC.isLoaded() ? HAUNTING_MONSTER_GHOSTS.get() : HAUNTING_MONSTER_GHOSTS.getDefault();
    }

    /** Most monsters' ghosts allowed near each other. */
    public static int hauntingMonsterGhostCap() {
        return SERVER_SPEC.isLoaded() ? HAUNTING_MONSTER_GHOST_CAP.get() : HAUNTING_MONSTER_GHOST_CAP.getDefault();
    }

    /** Chance a monster spawning in the Nether wears haunted armor. */
    public static double hauntingNetherChance() {
        return SERVER_SPEC.isLoaded() ? HAUNTING_NETHER_CHANCE.get() : HAUNTING_NETHER_CHANCE.getDefault();
    }

    /** The chance that studying an item writes a curse instead, for a player at {@code level}. */
    public static double studyingCurseChance(int level) {
        boolean loaded = SERVER_SPEC.isLoaded();
        return EnchantmentStudy.curseChance(level,
                loaded ? STUDYING_CURSE_CHANCE_MAX.get() : STUDYING_CURSE_CHANCE_MAX.getDefault(),
                loaded ? STUDYING_CURSE_CHANCE_MIN.get() : STUDYING_CURSE_CHANCE_MIN.getDefault(),
                loaded ? STUDYING_CURSE_CHANCE_MIN_LEVEL.get() : STUDYING_CURSE_CHANCE_MIN_LEVEL.getDefault());
    }

    /** In experience points. */
    public static int studyingXpCost() {
        return SERVER_SPEC.isLoaded() ? STUDYING_XP_COST.get() : STUDYING_XP_COST.getDefault();
    }

    public static int studyingDurationTicks() {
        return SERVER_SPEC.isLoaded() ? STUDYING_DURATION.get() : STUDYING_DURATION.getDefault();
    }

    /** The chance that copying a book writes a curse instead, for a player at {@code level}. */
    public static double copyingCurseChance(int level) {
        boolean loaded = SERVER_SPEC.isLoaded();
        return EnchantmentStudy.curseChance(level,
                loaded ? COPYING_CURSE_CHANCE_MAX.get() : COPYING_CURSE_CHANCE_MAX.getDefault(),
                loaded ? COPYING_CURSE_CHANCE_MIN.get() : COPYING_CURSE_CHANCE_MIN.getDefault(),
                loaded ? COPYING_CURSE_CHANCE_MIN_LEVEL.get() : COPYING_CURSE_CHANCE_MIN_LEVEL.getDefault());
    }

    /** In experience points. */
    public static int copyingXpCost() {
        return SERVER_SPEC.isLoaded() ? COPYING_XP_COST.get() : COPYING_XP_COST.getDefault();
    }

    public static int copyingDurationTicks() {
        return SERVER_SPEC.isLoaded() ? COPYING_DURATION.get() : COPYING_DURATION.getDefault();
    }

    /** Applies to treasure enchantments other than curses; 1 is no extra cost. */
    public static double studyTreasureMultiplier() {
        return SERVER_SPEC.isLoaded() ? STUDY_TREASURE_MULTIPLIER.get() : STUDY_TREASURE_MULTIPLIER.getDefault();
    }

    /** Defaults until the world's server config is loaded (or synced, on a client). */
    private static int forLevel(ModConfigSpec.ConfigValue<List<? extends Integer>> value, List<Integer> fallback, int level) {
        List<? extends Number> levels = SERVER_SPEC.isLoaded() ? value.get() : fallback;
        return levels.get(Mth.clamp(level, 1, LEVELS) - 1).intValue();
    }
}
