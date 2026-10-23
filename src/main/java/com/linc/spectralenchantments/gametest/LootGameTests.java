package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralEnchantments;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The enchantments in loot and trades: everywhere vanilla's own curses are, and the gear the bundled loot pack
 * (datapacks/loot) adds. Rates are checked loosely, over many rolls.
 */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LootGameTests {
    private static final List<ResourceKey<Enchantment>> ALL = List.of(ModEnchantments.BROAD_BREAKER, ModEnchantments.FAR_MINER,
            ModEnchantments.GATHER, ModEnchantments.BRANCH_FINDING, ModEnchantments.ANIMATED, ModEnchantments.CATAPULTING,
            ModEnchantments.EXPLOSIVE_BOLTS, ModEnchantments.DOPPELGANGER, ModEnchantments.POSSESSION_CURSE,
            ModEnchantments.HAUNTING_CURSE, ModEnchantments.HIGH_JUMP, ModEnchantments.SPRINGY,
            ModEnchantments.ALARMING);

    private LootGameTests() {}

    /** As Binding and Vanishing are: rolled on random loot and sold by librarians, whichever way treasure_only is set. */
    @GameTest(template = "empty")
    public static void everyEnchantmentIsInLootAndTrades(GameTestHelper helper) {
        for (ResourceKey<Enchantment> key : ALL) {
            Holder<Enchantment> holder = holder(helper, key);
            helper.assertTrue(holder.is(EnchantmentTags.ON_RANDOM_LOOT), key.location() + " should turn up in random loot");
            helper.assertTrue(holder.is(EnchantmentTags.TRADEABLE), key.location() + " should be sold by librarians");
        }
        helper.assertTrue(holder(helper, ModEnchantments.POSSESSION_CURSE).is(EnchantmentTags.CURSE), "Possession is a curse");
        helper.assertTrue(holder(helper, ModEnchantments.HAUNTING_CURSE).is(EnchantmentTags.CURSE), "Haunting is a curse");
        helper.succeed();
    }

    /** The loot additions ship as a built-in data pack, on unless a world turns it off. */
    @GameTest(template = "empty")
    public static void lootPackIsOnByDefault(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getServer().getPackRepository().getSelectedIds().stream()
                .anyMatch(id -> id.contains(SpectralEnchantments.MOD_ID) && id.contains("loot")), "The bundled loot pack should be enabled");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void mineshaftsHoldBroadBreakerPickaxes(GameTestHelper helper) {
        // 0.08 a chest: about 80 in 1000.
        int picks = count(helper, helper.getLevel(), BuiltInLootTables.ABANDONED_MINESHAFT, 1000, ModEnchantments.BROAD_BREAKER,
                stack -> stack.is(Items.IRON_PICKAXE) || stack.is(Items.STONE_PICKAXE));
        helper.assertTrue(picks >= 40 && picks <= 130, "Expected about 80 Broad Breaker pickaxes in 1000 mineshaft chests, got " + picks);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void dungeonArmorIsHaunted(GameTestHelper helper) {
        // Dungeon chests hold no armor of their own: 0.06 a chest, about 60 in 1000.
        int pieces = count(helper, helper.getLevel(), BuiltInLootTables.SIMPLE_DUNGEON, 1000, ModEnchantments.HAUNTING_CURSE,
                stack -> !stack.is(Items.ENCHANTED_BOOK));
        helper.assertTrue(pieces >= 25 && pieces <= 110, "Expected about 60 haunted pieces of armor in 1000 dungeon chests, got " + pieces);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void strongholdWeaponsArePossessed(GameTestHelper helper) {
        // 0.06 a chest: about 60 in 1000.
        int weapons = count(helper, helper.getLevel(), BuiltInLootTables.STRONGHOLD_CORRIDOR, 1000, ModEnchantments.POSSESSION_CURSE,
                stack -> stack.is(Items.IRON_SWORD) || stack.is(Items.IRON_AXE));
        helper.assertTrue(weapons >= 25 && weapons <= 110, "Expected about 60 possessed weapons in 1000 stronghold corridors, got " + weapons);
        helper.succeed();
    }

    /** Nether chests carry cursed golden gear, but only in the Nether (ruined portals turn up in both). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void netherChestsCarryCursedGold(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The Nether should exist");
        Predicate<ItemStack> gold = stack -> stack.getItem().getDescriptionId().contains("golden_");
        // 0.025 a chest for each curse: about 50 in 1000.
        int inNether = count(helper, nether, BuiltInLootTables.BASTION_TREASURE, 1000, ModEnchantments.POSSESSION_CURSE, gold)
                + count(helper, nether, BuiltInLootTables.BASTION_TREASURE, 1000, ModEnchantments.HAUNTING_CURSE, gold);
        int inOverworld = count(helper, helper.getLevel(), BuiltInLootTables.BASTION_TREASURE, 1000, ModEnchantments.POSSESSION_CURSE, gold)
                + count(helper, helper.getLevel(), BuiltInLootTables.BASTION_TREASURE, 1000, ModEnchantments.HAUNTING_CURSE, gold);
        helper.assertTrue(inNether >= 25 && inNether <= 110, "Expected about 50 cursed golden pieces in Nether bastion chests, got " + inNether);
        helper.assertTrue(inOverworld == 0, "The same chest outside the Nether gets none, got " + inOverworld);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void piglinsBarterCursedGold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Piglin piglin = helper.spawnWithNoFreeWill(EntityType.PIGLIN, new BlockPos(4, 2, 4));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.PIGLIN_BARTERING);
        Holder<Enchantment> possession = holder(helper, ModEnchantments.POSSESSION_CURSE);
        Holder<Enchantment> haunting = holder(helper, ModEnchantments.HAUNTING_CURSE);
        int cursed = 0;
        for (int i = 0; i < 8000; i++) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, piglin)
                    .create(LootContextParamSets.PIGLIN_BARTER);
            for (ItemStack stack : table.getRandomItems(params)) {
                if (level(stack, possession) > 0 || level(stack, haunting) > 0) {
                    cursed++;
                }
            }
        }
        // 0.005 a barter for each curse: about 80 in 8000.
        helper.assertTrue(cursed >= 40 && cursed <= 130, "Expected about 80 cursed barters in 8000, got " + cursed);
        piglin.discard();
        helper.succeed();
    }

    /** How many items in {@code rolls} rolls of {@code table} match {@code what} and carry {@code enchantment}. */
    private static int count(GameTestHelper helper, ServerLevel level, ResourceKey<LootTable> table, int rolls,
                             ResourceKey<Enchantment> enchantment, Predicate<ItemStack> what) {
        Holder<Enchantment> holder = holder(helper, enchantment);
        int count = 0;
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : chest(level, table, BlockPos.ZERO)) {
                if (what.test(stack) && level(stack, holder) > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static List<ItemStack> chest(ServerLevel level, ResourceKey<LootTable> key, BlockPos at) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
                .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
    }

    /** The level on the item itself, or stored in it if it's a book. */
    private static int level(ItemStack stack, Holder<Enchantment> enchantment) {
        return Math.max(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment),
                stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment));
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}
