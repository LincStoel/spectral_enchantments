package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.ModAttachments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.study.EnchantmentStudy;
import com.linc.spectralenchantments.study.StudyMode;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.Filterable;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Studying and copying enchantments at a lectern. The player "holds use" by clicking the lectern every 4 ticks, as
 * the client does; the default 40-tick duration and curse chances apply, so a result may be a curse instead.
 */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StudyGameTests {
    private static final BlockPos LECTERN_POS = new BlockPos(2, 1, 2);
    private static final BlockPos PLAYER_POS = new BlockPos(2, 1, 0);
    /** Comfortably past the default 40-tick duration. */
    private static final int DONE_TICKS = 60;

    private StudyGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void studyingWritesOneOfTheItemsEnchantmentsAtLevelOne(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.AMETHYST_SHARD, 2));
        player.giveExperienceLevels(10);
        long xp = EnchantmentStudy.totalExperience(player);
        LecternBlockEntity lectern = lectern(helper, new ItemStack(Items.WRITABLE_BOOK));

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            helper.assertTrue(player.getMainHandItem().isEmpty(), "The studied item is destroyed");
            helper.assertTrue(player.getOffhandItem().getCount() == 1, "One amethyst shard is used up");
            assertWrittenFrom(helper, floating(helper).getItem(), Set.of(Enchantments.SHARPNESS, Enchantments.UNBREAKING));
            helper.assertTrue(lectern.getBook().isEmpty(), "The lectern is left empty");
            helper.assertTrue(EnchantmentStudy.totalExperience(player) == xp - 27, "Studying costs 27 experience points");
            helper.succeed();
        });
    }

    /** Only the item's own enchantments count: Quark's gold-tool Fortune, say, which it adds as the tool is used, doesn't. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anItemWithoutEnchantmentsOfItsOwnCantBeStudied(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_PICKAXE));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.AMETHYST_SHARD));
        player.giveExperienceLevels(10);
        LecternBlockEntity lectern = lectern(helper, new ItemStack(Items.WRITABLE_BOOK));

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            assertNothingHappened(helper, player, lectern, Items.GOLDEN_PICKAXE);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lettingGoPartWayUsesNothing(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 1, null, 0));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.AMETHYST_SHARD));
        player.giveExperienceLevels(10);
        long xp = EnchantmentStudy.totalExperience(player);
        LecternBlockEntity lectern = lectern(helper, new ItemStack(Items.WRITABLE_BOOK));

        hold(helper, player, 12);
        helper.runAfterDelay(13, () -> EnchantmentStudy.release(player));
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            assertNothingHappened(helper, player, lectern, Items.IRON_SWORD);
            helper.assertTrue(EnchantmentStudy.totalExperience(player) == xp, "No experience is spent");
            helper.succeed();
        });
    }

    /** The written book rises off the lectern, out of reach, then falls back down. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void theWrittenBookFloatsUpThenDrops(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 1, null, 0));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.AMETHYST_SHARD));
        player.giveExperienceLevels(10);
        lectern(helper, new ItemStack(Items.WRITABLE_BOOK));
        double top = helper.absolutePos(LECTERN_POS).getY() + 1.0;

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            ItemEntity book = floating(helper);
            helper.assertTrue(book.isNoGravity(), "It floats");
            helper.assertTrue(book.getY() > top + 1.0, "up off the lectern, at " + (book.getY() - top));
            helper.assertTrue(book.hasPickUpDelay(), "out of reach while it rises");
        });
        helper.runAfterDelay(150, () -> {
            ItemEntity book = floating(helper);
            helper.assertFalse(book.isNoGravity(), "Then it drops");
            helper.assertTrue(book.getY() < top + 0.5, "back down, at " + (book.getY() - top));
            helper.succeed();
        });
    }

    /** An enchanted book, even with amethyst in the other hand, is copied rather than studied. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anEnchantedBookIsCopiedNotStudied(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack source = book(helper, Enchantments.SHARPNESS, 3, null, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, source.copy());
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.AMETHYST_SHARD));
        player.giveExperienceLevels(10);
        lectern(helper, new ItemStack(Items.WRITABLE_BOOK));

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            helper.assertTrue(ItemStack.isSameItemSameComponents(player.getMainHandItem(), source), "The enchanted book is kept");
            helper.assertTrue(player.getOffhandItem().is(Items.AMETHYST_SHARD), "The amethyst is untouched");
            assertWrittenFrom(helper, floating(helper).getItem(), Set.of(Enchantments.SHARPNESS));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void studyingNeedsABlankBookAndQuill(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 1, null, 0));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.AMETHYST_SHARD));
        player.giveExperienceLevels(10);
        LecternBlockEntity lectern = lectern(helper, written());

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            assertNothingHappened(helper, player, lectern, Items.IRON_SWORD);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void studyingNeedsEnoughExperience(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 1, null, 0));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.AMETHYST_SHARD));
        player.giveExperiencePoints(26);
        LecternBlockEntity lectern = lectern(helper, new ItemStack(Items.WRITABLE_BOOK));

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            assertNothingHappened(helper, player, lectern, Items.IRON_SWORD);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void copyingWritesLevelOneAndKeepsTheBook(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack source = book(helper, Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, source.copy());
        player.giveExperienceLevels(10);
        long xp = EnchantmentStudy.totalExperience(player);
        LecternBlockEntity lectern = lectern(helper, new ItemStack(Items.WRITABLE_BOOK));

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            assertWrittenFrom(helper, floating(helper).getItem(), Set.of(Enchantments.SHARPNESS, Enchantments.UNBREAKING));
            helper.assertTrue(ItemStack.isSameItemSameComponents(player.getMainHandItem(), source), "The copied book is kept");
            helper.assertTrue(lectern.getBook().isEmpty(), "The lectern is left empty");
            helper.assertTrue(EnchantmentStudy.totalExperience(player) == xp - 27, "Copying costs 27 experience points");
            helper.succeed();
        });
    }

    /** Treasure can't be copied by default; whatever else the book holds still can. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void copyingLeavesOutMending(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, book(helper, Enchantments.MENDING, 1, Enchantments.UNBREAKING, 3));
        player.giveExperienceLevels(10);
        lectern(helper, new ItemStack(Items.WRITABLE_BOOK));

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            assertWrittenFrom(helper, floating(helper).getItem(), Set.of(Enchantments.UNBREAKING));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aBookOfOnlyMendingCantBeCopied(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, book(helper, Enchantments.MENDING, 1, null, 0));
        player.giveExperienceLevels(10);
        LecternBlockEntity lectern = lectern(helper, new ItemStack(Items.WRITABLE_BOOK));

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            helper.assertTrue(lectern.getBook().is(Items.WRITABLE_BOOK), "The lectern's book and quill is untouched");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void copyingNeedsABlankBookAndQuill(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, book(helper, Enchantments.SHARPNESS, 1, null, 0));
        player.giveExperienceLevels(10);
        long xp = EnchantmentStudy.totalExperience(player);
        LecternBlockEntity lectern = lectern(helper, written());

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            helper.assertTrue(lectern.getBook().is(Items.WRITABLE_BOOK), "A written-in book and quill is untouched");
            helper.assertTrue(EnchantmentStudy.totalExperience(player) == xp, "No experience is spent");
            helper.succeed();
        });
    }

    /**
     * A copy whose seed rolls a curse writes the curse and shoves the player back from the lectern. (A fake player
     * can't be hurt, so the half heart isn't checked.)
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aCurseShovesThePlayerAway(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, book(helper, Enchantments.SHARPNESS, 1, null, 0));
        // Level 0 has the highest chance; pick a seed that curses at it.
        List<Holder<Enchantment>> pool = List.of(holder(helper, Enchantments.SHARPNESS));
        List<Holder<Enchantment>> curses = EnchantmentStudy.curses(helper.getLevel());
        double chance = SpectralConfig.copyingCurseChance(0);
        int seed = 1;
        while (EnchantmentStudy.roll(seed, StudyMode.COPYING, pool, curses, chance).curse().isEmpty()) {
            seed += 2;
        }
        player.setData(ModAttachments.STUDY_SEED, seed);
        player.setGameMode(GameType.CREATIVE); // free, so level 0 can afford it
        lectern(helper, new ItemStack(Items.WRITABLE_BOOK));

        hold(helper, player, DONE_TICKS);
        helper.runAfterDelay(DONE_TICKS + 1, () -> {
            ItemStack written = floating(helper).getItem();
            helper.assertTrue(written.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().stream()
                    .allMatch(enchantment -> enchantment.is(EnchantmentTags.CURSE)), "A curse is written, got " + written);
            // The lectern is south (+z) of the player.
            helper.assertTrue(player.getDeltaMovement().z < -0.1, "Shoved away from the lectern, got " + player.getDeltaMovement());
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void theCurseChanceFallsWithLevel(GameTestHelper helper) {
        helper.assertTrue(EnchantmentStudy.curseChance(0, 0.1, 0.0005, 30) == 0.1, "10% at level 0");
        helper.assertTrue(Math.abs(EnchantmentStudy.curseChance(15, 0.1, 0.0005, 30) - 0.05025) < 1.0E-9, "Halfway at level 15");
        helper.assertTrue(Math.abs(EnchantmentStudy.curseChance(30, 0.1, 0.0005, 30) - 0.0005) < 1.0E-9, "0.05% at level 30");
        helper.assertTrue(Math.abs(EnchantmentStudy.curseChance(60, 0.1, 0.0005, 30) - 0.0005) < 1.0E-9, "and no lower above it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollsAreRepeatable(GameTestHelper helper) {
        List<Holder<Enchantment>> pool = List.of(holder(helper, Enchantments.SHARPNESS), holder(helper, Enchantments.UNBREAKING),
                holder(helper, Enchantments.LOOTING), holder(helper, Enchantments.KNOCKBACK));
        List<Holder<Enchantment>> curses = EnchantmentStudy.curses(helper.getLevel());
        helper.assertFalse(curses.isEmpty(), "There are curses to write");
        for (int seed = 1; seed < 200; seed += 2) {
            EnchantmentStudy.Roll a = EnchantmentStudy.roll(seed, StudyMode.COPYING, pool, curses, 0.5);
            EnchantmentStudy.Roll b = EnchantmentStudy.roll(seed, StudyMode.COPYING, pool.reversed(), curses, 0.5);
            helper.assertTrue(a.equals(b), "The same seed and enchantments give the same roll, whatever their order");
            helper.assertTrue(EnchantmentStudy.roll(seed, StudyMode.STUDYING, pool, curses, 1.0).curse().isPresent(), "Chance 1 always curses");
            helper.assertTrue(EnchantmentStudy.roll(seed, StudyMode.STUDYING, pool, curses, 0.0).curse().isEmpty(), "Chance 0 never does");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void treasureCostsMoreButCursesDont(GameTestHelper helper) {
        helper.assertTrue(EnchantmentStudy.xpCost(holder(helper, Enchantments.SHARPNESS), 27, 2.0) == 27, "Sharpness costs the base");
        helper.assertTrue(EnchantmentStudy.xpCost(holder(helper, Enchantments.MENDING), 27, 2.0) == 54, "Mending costs double");
        Holder<Enchantment> binding = holder(helper, Enchantments.BINDING_CURSE);
        helper.assertTrue(binding.is(EnchantmentTags.TREASURE), "Vanilla counts curses as treasure");
        helper.assertTrue(EnchantmentStudy.xpCost(binding, 27, 2.0) == 27, "but they cost the base");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void forbiddenEnchantmentsAreNamed(GameTestHelper helper) {
        Holder<Enchantment> mending = holder(helper, Enchantments.MENDING);
        Holder<Enchantment> frostWalker = holder(helper, Enchantments.FROST_WALKER);
        helper.assertTrue(EnchantmentStudy.isForbidden(mending, StudyMode.COPYING), "Mending can't be copied");
        helper.assertFalse(EnchantmentStudy.isForbidden(mending, StudyMode.STUDYING), "but it can be studied");
        Component one = EnchantmentStudy.forbiddenMessage(StudyMode.COPYING, List.of(mending));
        helper.assertTrue(key(one).equals("message.spectral_enchantments.copying.forbidden.one"), "One enchantment: " + key(one));
        Component many = EnchantmentStudy.forbiddenMessage(StudyMode.STUDYING, List.of(frostWalker, mending));
        helper.assertTrue(key(many).equals("message.spectral_enchantments.studying.forbidden.many"), "More than one: " + key(many));
        helper.assertTrue(many.getString().contains("Frost Walker") && many.getString().contains("Mending"), "Names them: " + many.getString());
        helper.succeed();
    }

    // ---- Helpers ----

    /** Clicks the lectern every 4 ticks for {@code ticks}, as a client holding use does. */
    private static void hold(GameTestHelper helper, FakePlayer player, int ticks) {
        BlockPos pos = helper.absolutePos(LECTERN_POS);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        for (int tick = 0; tick <= ticks; tick += 4) {
            helper.runAfterDelay(tick, () -> CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
        }
    }

    private static void assertWrittenFrom(GameTestHelper helper, ItemStack book, Set<ResourceKey<Enchantment>> expected) {
        helper.assertTrue(book.is(Items.ENCHANTED_BOOK), "An enchanted book is written, got " + book);
        ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertTrue(stored.size() == 1, "It holds one enchantment, got " + stored);
        Holder<Enchantment> written = stored.keySet().iterator().next();
        helper.assertTrue(stored.getLevel(written) == 1, "At level I, got " + stored.getLevel(written));
        helper.assertTrue(written.is(EnchantmentTags.CURSE) || expected.stream().anyMatch(written::is),
                "One of " + expected + " (or a curse), got " + written);
    }

    private static void assertNothingHappened(GameTestHelper helper, FakePlayer player, LecternBlockEntity lectern, net.minecraft.world.item.Item held) {
        helper.assertTrue(player.getMainHandItem().is(held), "The held item is untouched");
        helper.assertTrue(player.getOffhandItem().is(Items.AMETHYST_SHARD), "The amethyst is untouched");
        helper.assertTrue(lectern.getBook().is(Items.WRITABLE_BOOK), "The lectern's book and quill is untouched");
    }

    /** The written book, floating off the lectern or come down near it. */
    private static ItemEntity floating(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(LECTERN_POS);
        List<ItemEntity> books = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2, 6, 2),
                item -> item.getItem().is(Items.ENCHANTED_BOOK));
        helper.assertTrue(books.size() == 1, "One written book by the lectern, got " + books.size());
        return books.get(0);
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static LecternBlockEntity lectern(GameTestHelper helper, ItemStack book) {
        helper.setBlock(LECTERN_POS, Blocks.LECTERN);
        BlockPos pos = helper.absolutePos(LECTERN_POS);
        LecternBlock.tryPlaceBook(null, helper.getLevel(), pos, helper.getLevel().getBlockState(pos), book);
        return (LecternBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "study_test"));
        BlockPos stand = helper.absolutePos(PLAYER_POS);
        player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
        player.setOnGround(true);
        player.getInventory().clearContent();
        player.setExperienceLevels(0);
        player.experienceProgress = 0f;
        return player;
    }

    private static ItemStack written() {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(Filterable.passThrough("Dear diary"))));
        return book;
    }

    private static ItemStack enchanted(GameTestHelper helper, net.minecraft.world.item.Item item, ResourceKey<Enchantment> first, int firstLevel,
                                       ResourceKey<Enchantment> second, int secondLevel) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(holder(helper, first), firstLevel);
        if (second != null) {
            stack.enchant(holder(helper, second), secondLevel);
        }
        return stack;
    }

    private static ItemStack book(GameTestHelper helper, ResourceKey<Enchantment> first, int firstLevel, ResourceKey<Enchantment> second,
                                  int secondLevel) {
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder(helper, first), firstLevel));
        if (second != null) {
            ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
            stored.set(holder(helper, second), secondLevel);
            book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        }
        return book;
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}
