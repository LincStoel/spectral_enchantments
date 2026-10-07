package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.AreaHelper;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.MiningState;
import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for Broad Breaker. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpectralEnchantmentsGameTests {
    // Wall plane at relative z = 4; the mined block sits mid-wall so a 7x7 area fits inside the 9x9x9 test volume.
    private static final BlockPos ORIGIN = new BlockPos(4, 2, 4);

    private SpectralEnchantmentsGameTests() {}

    @GameTest(template = "empty")
    public static void wallAreaKeepsMinedBlockSecondFromBottom(GameTestHelper helper) {
        for (int level = 1; level <= 3; level++) {
            fillWall(helper, Blocks.STONE, 3);
            FakePlayer player = player(helper, Items.DIAMOND_PICKAXE, level);
            BlockPos origin = helper.absolutePos(ORIGIN);
            Set<BlockPos> area = new HashSet<>(AreaHelper.getExtraBlocks(player, origin, Direction.NORTH));
            area.add(origin);

            Set<BlockPos> expected = new HashSet<>();
            for (int dx = -level; dx <= level; dx++) {
                for (int dy = -1; dy <= 2 * level - 1; dy++) {
                    expected.add(origin.offset(dx, dy, 0));
                }
            }
            int size = 2 * level + 1;
            helper.assertTrue(area.equals(expected), "Level " + level + " wall area should be " + size + "x" + size + " with the mined block 2nd from the bottom, got " + area.size() + " blocks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void floorAreaIsCentered(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
            }
        }
        FakePlayer player = player(helper, Items.DIAMOND_SHOVEL, 3);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        List<BlockPos> extras = AreaHelper.getExtraBlocks(player, origin, Direction.UP);
        helper.assertTrue(extras.size() == 48, "Level 3 floor area should be a centered 7x7, got " + (extras.size() + 1));
        for (BlockPos pos : extras) {
            helper.assertTrue(Math.abs(pos.getX() - origin.getX()) <= 3 && Math.abs(pos.getZ() - origin.getZ()) <= 3 && pos.getY() == origin.getY(),
                    "Floor area block out of place: " + pos);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void skipsBlocksTheToolCannotHarvest(GameTestHelper helper) {
        fillWall(helper, Blocks.STONE, 1);
        helper.setBlock(ORIGIN.offset(-1, 1, 0), Blocks.OBSIDIAN); // above the iron pick's tier
        helper.setBlock(ORIGIN.offset(1, 1, 0), Blocks.DIRT);      // wrong tool type
        FakePlayer player = player(helper, Items.IRON_PICKAXE, 1);

        mine(helper, player, ORIGIN, Direction.NORTH);

        helper.assertBlockPresent(Blocks.OBSIDIAN, ORIGIN.offset(-1, 1, 0));
        helper.assertBlockPresent(Blocks.DIRT, ORIGIN.offset(1, 1, 0));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dy != 1 || dx == 0) {
                    helper.assertBlockPresent(Blocks.AIR, ORIGIN.offset(dx, dy, 0));
                }
            }
        }
        // 7 blocks broken -> 7 / 2 = 3 durability.
        expectDamage(helper, player, 3);
    }

    @GameTest(template = "empty")
    public static void slowestBlockSetsMiningTime(GameTestHelper helper) {
        fillWall(helper, Blocks.STONE, 1);
        BlockPos obsidian = ORIGIN.offset(1, 0, 0);
        helper.setBlock(obsidian, Blocks.OBSIDIAN);
        FakePlayer player = player(helper, Items.DIAMOND_PICKAXE, 1);
        BlockPos origin = helper.absolutePos(ORIGIN);
        BlockPos obsidianAbs = helper.absolutePos(obsidian);

        float obsidianAlone;
        MiningState.CALCULATING_SPEED.set(true); // read the obsidian's own speed, without area scaling
        try {
            obsidianAlone = Blocks.OBSIDIAN.defaultBlockState().getDestroyProgress(player, player.level(), obsidianAbs);
        } finally {
            MiningState.CALCULATING_SPEED.set(false);
        }
        float stoneAlone = Blocks.STONE.defaultBlockState().getDestroyProgress(player, player.level(), origin);

        CommonHooks.onLeftClickBlock(player, origin, Direction.NORTH, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        float areaProgress = player.level().getBlockState(origin).getDestroyProgress(player, player.level(), origin);

        helper.assertTrue(Math.abs(areaProgress - obsidianAlone) < 1e-6f,
                "Area should mine at the obsidian's rate " + obsidianAlone + " but was " + areaProgress + " (stone alone " + stoneAlone + ")");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullAreaCostsHalfTheBlocks(GameTestHelper helper) {
        fillWall(helper, Blocks.STONE, 3);
        FakePlayer player = player(helper, Items.DIAMOND_PICKAXE, 3);
        mine(helper, player, ORIGIN, Direction.NORTH);
        // 49 blocks broken -> 49 / 2 = 24 durability.
        expectDamage(helper, player, 24);
    }

    @GameTest(template = "empty")
    public static void sneakingMinesOneBlock(GameTestHelper helper) {
        fillWall(helper, Blocks.STONE, 1);
        FakePlayer player = player(helper, Items.DIAMOND_PICKAXE, 1);
        player.setShiftKeyDown(true);
        mine(helper, player, ORIGIN, Direction.NORTH);
        helper.assertBlockPresent(Blocks.STONE, ORIGIN.offset(1, 0, 0));
        expectDamage(helper, player, 1);
    }

    @GameTest(template = "empty")
    public static void breakAfterAbortIsNotAreaMined(GameTestHelper helper) {
        // E.g. an Ars Nouveau spell, cast by the player, breaking the block they had started and given up on.
        fillWall(helper, Blocks.STONE, 1);
        FakePlayer player = player(helper, Items.DIAMOND_PICKAXE, 1);
        BlockPos pos = helper.absolutePos(ORIGIN);
        CommonHooks.onLeftClickBlock(player, pos, Direction.NORTH, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        CommonHooks.onLeftClickBlock(player, pos, Direction.NORTH, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "Origin block should break");
        helper.assertBlockPresent(Blocks.STONE, ORIGIN.offset(1, 0, 0));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remineWithoutStartStillAreaMines(GameTestHelper helper) {
        // Vanilla clients re-mining a refilled position send only STOP (no START); its face must still count.
        fillWall(helper, Blocks.STONE, 1);
        FakePlayer player = player(helper, Items.DIAMOND_PICKAXE, 1);
        mine(helper, player, ORIGIN, Direction.NORTH);
        fillWall(helper, Blocks.STONE, 1);
        BlockPos pos = helper.absolutePos(ORIGIN);
        CommonHooks.onLeftClickBlock(player, pos, Direction.NORTH, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "Origin block should break");
        helper.assertBlockPresent(Blocks.AIR, ORIGIN.offset(1, 0, 0));
        helper.assertBlockPresent(Blocks.AIR, ORIGIN.offset(-1, 1, 0));
        helper.succeed();
    }

    private static void fillWall(GameTestHelper helper, Block block, int level) {
        for (int dx = -level; dx <= level; dx++) {
            for (int dy = -1; dy <= 2 * level - 1; dy++) {
                helper.setBlock(ORIGIN.offset(dx, dy, 0), block);
            }
        }
    }

    /** The config switches flow through to the data: a disabled enchantment fits nothing and is never offered. */
    @GameTest(template = "empty")
    public static void configSwitchesControlAvailability(GameTestHelper helper) {
        checkAvailability(helper, ModEnchantments.BROAD_BREAKER, SpectralConfig.broadBreakerEnabled(), Items.DIAMOND_PICKAXE);
        checkAvailability(helper, ModEnchantments.SPECTRAL_WEAPON, SpectralConfig.spectralWeaponEnabled(), Items.DIAMOND_SWORD);
        checkAvailability(helper, ModEnchantments.FAR_MINER, SpectralConfig.farMinerEnabled(), Items.DIAMOND_PICKAXE);
        checkAvailability(helper, ModEnchantments.GATHER, SpectralConfig.gatherEnabled(), Items.DIAMOND_SWORD);
        checkAvailability(helper, ModEnchantments.BRANCH_FINDING, SpectralConfig.branchFindingEnabled(), Items.IRON_HOE);
        helper.succeed();
    }

    private static void checkAvailability(GameTestHelper helper, ResourceKey<Enchantment> key, boolean enabled, Item tool) {
        Holder<Enchantment> holder = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        helper.assertTrue(holder.is(EnchantmentTags.IN_ENCHANTING_TABLE) == enabled,
                key.location() + " should " + (enabled ? "" : "not ") + "be in the enchanting table");
        helper.assertTrue(new ItemStack(tool).supportsEnchantment(holder) == enabled,
                key.location() + " should " + (enabled ? "" : "not ") + "apply to " + tool);
    }

    private static FakePlayer player(GameTestHelper helper, Item tool, int enchantLevel) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "spectral_enchantments_test"));
        BlockPos stand = helper.absolutePos(ORIGIN.north(2));
        player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
        player.setOnGround(true);
        ItemStack stack = new ItemStack(tool);
        stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModEnchantments.BROAD_BREAKER), enchantLevel);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    /** What the server does when a survival player finishes mining: START (records the face), then destroy. */
    private static void mine(GameTestHelper helper, FakePlayer player, BlockPos relative, Direction face) {
        BlockPos pos = helper.absolutePos(relative);
        CommonHooks.onLeftClickBlock(player, pos, face, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "Origin block should break");
    }

    private static void expectDamage(GameTestHelper helper, FakePlayer player, int expected) {
        ItemStack tool = player.getMainHandItem();
        // The extra durability is charged on a deferred server task; give it a few ticks to land.
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(tool.getDamageValue() == expected, "Expected " + expected + " durability used, got " + tool.getDamageValue());
            helper.succeed();
        });
    }
}
