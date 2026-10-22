package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.ModItems;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.index.EnchantingIndex;
import com.linc.spectralenchantments.index.IndexLectern;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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
        EnchantingIndex.open(player, List.of(listedPos), new ItemStack(ModItems.ENCHANTING_INDEX.get()));

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

    @GameTest(template = "empty")
    public static void theIndexIsDyedLikeLeatherAndWashedInACauldron(GameTestHelper helper) {
        CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(ModItems.ENCHANTING_INDEX.get()), new ItemStack(Items.RED_DYE)));
        ItemStack dyed = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(recipe -> recipe.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        DyedItemColor color = dyed.get(DataComponents.DYED_COLOR);
        helper.assertTrue(color != null && color.rgb() == 0xB02E26, "Red dye turns the cover red: " + color);

        helper.setBlock(new BlockPos(1, 1, 1), Blocks.WATER_CAULDRON);
        BlockPos cauldron = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState state = helper.getLevel().getBlockState(cauldron);
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "index_test"));
        player.setItemInHand(InteractionHand.MAIN_HAND, dyed);
        CauldronInteraction wash = CauldronInteraction.WATER.map().get(ModItems.ENCHANTING_INDEX.get());
        helper.assertTrue(wash != null, "A water cauldron takes the index");
        wash.interact(state, helper.getLevel(), cauldron, player, InteractionHand.MAIN_HAND, dyed);
        helper.assertFalse(dyed.has(DataComponents.DYED_COLOR), "and washes the dye off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theIndexSitsOnALecternAndClientsAreToldOfIt(GameTestHelper helper) {
        ItemStack index = new ItemStack(ModItems.ENCHANTING_INDEX.get());
        helper.assertTrue(index.is(ItemTags.LECTERN_BOOKS), "The index is a lectern book");
        LecternBlockEntity lectern = lectern(helper, new BlockPos(1, 1, 1), index);
        helper.assertTrue(IndexLectern.holdsIndex(lectern), "The lectern holds the index");
        CompoundTag update = lectern.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(update.contains("Book"), "Clients are sent the index: " + update);

        LecternBlockEntity plain = lectern(helper, new BlockPos(3, 1, 1), new ItemStack(Items.WRITABLE_BOOK));
        helper.assertFalse(IndexLectern.holdsIndex(plain), "A book and quill isn't the index");
        CompoundTag plainUpdate = plain.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertFalse(plainUpdate.contains("Book"), "Other books aren't sent");
        helper.assertFalse(plainUpdate.isEmpty(), "but the update isn't empty, so a client drops an index taken off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theLecternSearchIsCentredAndEvenWidthsReachFurtherNegative(GameTestHelper helper) {
        BlockPos lectern = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos west = shelf(helper, new BlockPos(1, 2, 3));
        BlockPos east = shelf(helper, new BlockPos(5, 2, 3));
        BlockPos nearEast = shelf(helper, new BlockPos(4, 2, 3));
        List<BlockPos> even = EnchantingIndex.holdersAroundLectern(helper.getLevel(), lectern, 4);
        helper.assertTrue(even.contains(west) && even.contains(nearEast), "4 wide reaches 2 west and 1 east: " + even);
        helper.assertFalse(even.contains(east), "but not 2 east");
        List<BlockPos> odd = EnchantingIndex.holdersAroundLectern(helper.getLevel(), lectern, 5);
        helper.assertTrue(odd.contains(west) && odd.contains(east), "5 wide reaches 2 each way: " + odd);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void usingTheLecternOpensTheIndexAndCrouchingPopsItOff(GameTestHelper helper) {
        LecternBlockEntity lectern = lectern(helper, new BlockPos(1, 1, 1), new ItemStack(ModItems.ENCHANTING_INDEX.get()));
        BlockPos pos = lectern.getBlockPos();
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "index_test"));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        PlayerInteractEvent.RightClickBlock use = useOn(player, pos);
        helper.assertTrue(use.isCanceled(), "Using the lectern is taken over from vanilla's reading screen");
        helper.assertTrue(player.getCooldowns().isOnCooldown(ModItems.ENCHANTING_INDEX.get()), "and the index cools down");
        helper.assertTrue(IndexLectern.holdsIndex(lectern), "The index stays on the lectern");

        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertFalse(useOn(player, pos).isCanceled(), "Crouching with something in hand is left to that item");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(useOn(player, pos).isCanceled(), "Crouching with an empty hand");
        helper.assertFalse(helper.getLevel().getBlockState(pos).getValue(LecternBlock.HAS_BOOK), "takes the index off");
        helper.assertItemEntityPresent(ModItems.ENCHANTING_INDEX.get(), new BlockPos(1, 2, 1), 2);
        helper.succeed();
    }

    private static LecternBlockEntity lectern(GameTestHelper helper, BlockPos relative, ItemStack book) {
        helper.setBlock(relative, Blocks.LECTERN);
        BlockPos pos = helper.absolutePos(relative);
        LecternBlock.tryPlaceBook(null, helper.getLevel(), pos, helper.getLevel().getBlockState(pos), book);
        return (LecternBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static PlayerInteractEvent.RightClickBlock useOn(FakePlayer player, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        IndexLectern.onRightClickBlock(event);
        return event;
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
