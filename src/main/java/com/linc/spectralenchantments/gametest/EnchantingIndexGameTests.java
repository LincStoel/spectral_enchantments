package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.index.EnchantingIndex;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Enchanting Index's search, listing and renaming. Searches around a point use small radii, so shelves other
 * tests build nearby never land in them.
 */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnchantingIndexGameTests {
    private EnchantingIndexGameTests() {}

    @GameTest(template = "empty")
    public static void theSearchAroundFindsShelvesInsideTheCubeOnly(GameTestHelper helper) {
        BlockPos near = shelf(helper, new BlockPos(1, 1, 1));
        BlockPos far = shelf(helper, new BlockPos(4, 1, 4));
        List<BlockPos> found = EnchantingIndex.holdersAround(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)), 2);
        helper.assertTrue(found.contains(near), "The shelf two blocks away is found");
        helper.assertFalse(found.contains(far), "The shelf four blocks away isn't");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void identicalBooksShareAnEntryAndPlainBooksAreLeftOut(GameTestHelper helper) {
        BlockPos pos = shelf(helper, new BlockPos(1, 1, 1));
        ChiseledBookShelfBlockEntity shelf = (ChiseledBookShelfBlockEntity) helper.getLevel().getBlockEntity(pos);
        shelf.setItem(0, book(helper, Enchantments.SHARPNESS, 5));
        shelf.setItem(1, new ItemStack(Items.BOOK));
        shelf.setItem(2, book(helper, Enchantments.SHARPNESS, 5));
        shelf.setItem(4, book(helper, Enchantments.MENDING, 1));
        EnchantingIndex.Catalogue catalogue = EnchantingIndex.catalogue(helper.getLevel(), List.of(pos));
        helper.assertTrue(catalogue.entries().size() == 2, "Two kinds of enchanted book: " + catalogue.entries().size());
        EnchantingIndex.Entry sharpness = catalogue.entries().getFirst();
        helper.assertTrue(sharpness.locations().equals(List.of(new EnchantingIndex.Location(pos, 0), new EnchantingIndex.Location(pos, 2))),
                "Both Sharpness books share one entry: " + sharpness.locations());
        helper.assertTrue(sharpness.book().getCount() == 1, "An entry holds one copy");
        helper.assertFalse(catalogue.truncated(), "Nothing was left out");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void joinedShelvesAreFollowedThroughCornersUpToTheLimit(GameTestHelper helper) {
        BlockPos first = shelf(helper, new BlockPos(0, 1, 0));
        BlockPos second = shelf(helper, new BlockPos(1, 2, 1));
        BlockPos third = shelf(helper, new BlockPos(2, 3, 2));
        BlockPos apart = shelf(helper, new BlockPos(4, 1, 4));
        List<BlockPos> all = EnchantingIndex.holdersJoined(helper.getLevel(), first, 100);
        helper.assertTrue(all.equals(List.of(first, second, third)), "Shelves touching only at corners are joined: " + all);
        helper.assertFalse(all.contains(apart), "A shelf on its own isn't");
        helper.assertTrue(EnchantingIndex.holdersJoined(helper.getLevel(), first, 2).equals(List.of(first, second)),
                "The search stops at the limit, nearest first");
        helper.assertTrue(EnchantingIndex.holdersJoined(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 0)), 100).isEmpty(),
                "Nothing is found from a block that isn't a shelf");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void renamingTouchesOnlyListedBooksThatHaventChanged(GameTestHelper helper) {
        BlockPos listedPos = shelf(helper, new BlockPos(1, 1, 1));
        BlockPos otherPos = shelf(helper, new BlockPos(3, 1, 3));
        ChiseledBookShelfBlockEntity listed = (ChiseledBookShelfBlockEntity) helper.getLevel().getBlockEntity(listedPos);
        ChiseledBookShelfBlockEntity other = (ChiseledBookShelfBlockEntity) helper.getLevel().getBlockEntity(otherPos);
        ItemStack mending = book(helper, Enchantments.MENDING, 1);
        listed.setItem(0, mending.copy());
        listed.setItem(1, mending.copy());
        other.setItem(0, mending.copy());
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "index_test"));
        EnchantingIndex.open(player, List.of(listedPos));

        List<EnchantingIndex.Location> locations = List.of(new EnchantingIndex.Location(listedPos, 0),
                new EnchantingIndex.Location(listedPos, 1), new EnchantingIndex.Location(otherPos, 0));
        helper.assertTrue(EnchantingIndex.rename(player, locations, book(helper, Enchantments.SHARPNESS, 1), "Wrong") == 0,
                "A book that isn't the one renamed is left alone");
        helper.assertTrue(EnchantingIndex.rename(player, locations, mending, "  Fix-It  ") == 2, "Both listed copies are renamed");
        helper.assertTrue(Component.literal("Fix-It").equals(listed.getItem(0).get(DataComponents.CUSTOM_NAME)),
                "The name is trimmed: " + listed.getItem(0).get(DataComponents.CUSTOM_NAME));
        helper.assertFalse(other.getItem(0).has(DataComponents.CUSTOM_NAME), "A shelf the index didn't list is left alone");

        ItemStack named = listed.getItem(0).copy();
        helper.assertTrue(EnchantingIndex.rename(player, locations.subList(0, 1), named, " ") == 1, "A blank name");
        helper.assertFalse(listed.getItem(0).has(DataComponents.CUSTOM_NAME), "takes the custom name off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theIndexCanBeCrafted(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(SpectralEnchantments.id("enchanting_index")).isPresent(),
                "The recipe is there while the index is switched on");
        helper.succeed();
    }

    private static BlockPos shelf(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, Blocks.CHISELED_BOOKSHELF);
        return helper.absolutePos(relative);
    }

    private static ItemStack book(GameTestHelper helper, ResourceKey<Enchantment> enchantment, int level) {
        Holder<Enchantment> holder = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, level));
    }
}
