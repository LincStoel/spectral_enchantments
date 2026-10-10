package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.StackCooldowns;
import com.linc.spectralenchantments.BranchFinding;
import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for Branch Finding. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BranchFindingGameTests {
    private static final BlockPos CANOPY_TOP = new BlockPos(4, 6, 4);

    private BranchFindingGameTests() {}

    @GameTest(template = "empty")
    public static void findsOnlyLogsTouchingTheLeafMass(GameTestHelper helper) {
        buildTree(helper);
        Set<BlockPos> found = new HashSet<>(BranchFinding.search(helper.getLevel(), helper.absolutePos(CANOPY_TOP), 4096));
        Set<BlockPos> expected = absolute(helper, new BlockPos(4, 3, 4), new BlockPos(4, 4, 4), new BlockPos(4, 5, 4), new BlockPos(6, 5, 6));
        helper.assertTrue(found.equals(expected), "Expected the trunk inside the canopy and the branch, got " + found);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void searchStopsAtTheLeafLimit(GameTestHelper helper) {
        buildTree(helper);
        Set<BlockPos> found = new HashSet<>(BranchFinding.search(helper.getLevel(), helper.absolutePos(CANOPY_TOP), 1));
        helper.assertTrue(found.equals(absolute(helper, new BlockPos(4, 5, 4))), "A one-leaf search should only see the log under it, got " + found);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void useCostsOneDurabilityAndCoolsDown(GameTestHelper helper) {
        buildTree(helper);
        ServerPlayer player = player(helper, Items.SHEARS);
        ItemStack shears = player.getMainHandItem();
        helper.assertTrue(rightClick(helper, player, new BlockPos(1, 1, 1)) == false, "Using it on stone should do nothing");
        helper.assertTrue(shears.getDamageValue() == 0, "Using it on stone should cost nothing");
        helper.assertTrue(rightClick(helper, player, CANOPY_TOP), "Using it on leaves should be handled");
        helper.assertTrue(shears.getDamageValue() == 1, "A use should cost 1 durability, cost " + shears.getDamageValue());
        helper.assertTrue(StackCooldowns.isActive(player.getMainHandItem(), player.level()), "The shears should be on cooldown");
        helper.assertFalse(player.getCooldowns().isOnCooldown(Items.SHEARS), "Only these shears: other pairs stay free");
        rightClick(helper, player, CANOPY_TOP);
        helper.assertTrue(shears.getDamageValue() == 1, "A use during the cooldown should cost nothing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void goesOnHoesAndShearsOnly(GameTestHelper helper) {
        Enchantment branchFinding = holder(helper).value();
        helper.assertTrue(branchFinding.isSupportedItem(new ItemStack(Items.IRON_HOE)), "Hoes should take Branch Finding");
        helper.assertTrue(branchFinding.isSupportedItem(new ItemStack(Items.SHEARS)), "Shears should take Branch Finding");
        helper.assertTrue(!branchFinding.isSupportedItem(new ItemStack(Items.IRON_AXE)), "Axes should not take Branch Finding");
        helper.succeed();
    }

    /**
     * Leaves filling x 2..6, y 3..6, z 2..6 around an oak trunk at x 4, z 4 (y 1..5), with an oak wood branch at
     * (6, 5, 6). Not found: the trunk below the canopy, stone touching the leaves, a log touching them only at a
     * corner, and a log on a separate clump of leaves.
     */
    private static void buildTree(GameTestHelper helper) {
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int x = 2; x <= 6; x++) {
            for (int y = 3; y <= 6; y++) {
                for (int z = 2; z <= 6; z++) {
                    helper.setBlock(new BlockPos(x, y, z), leaves);
                }
            }
        }
        for (int y = 1; y <= 5; y++) {
            helper.setBlock(new BlockPos(4, y, 4), Blocks.OAK_LOG);
        }
        helper.setBlock(new BlockPos(6, 5, 6), Blocks.OAK_WOOD);
        helper.setBlock(new BlockPos(7, 4, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(7, 7, 7), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(0, 7, 0), leaves);
        helper.setBlock(new BlockPos(0, 8, 0), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
    }

    /** Compared in world coordinates: GameTestHelper#relativePos doesn't undo the test's rotation. */
    private static Set<BlockPos> absolute(GameTestHelper helper, BlockPos... relative) {
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : relative) {
            result.add(helper.absolutePos(pos));
        }
        return result;
    }

    /** Fires the right-click event the way the server does for a real use; true if Branch Finding took it. */
    private static boolean rightClick(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        BranchFinding.onRightClickBlock(event);
        return event.isCanceled();
    }

    private static Holder<Enchantment> holder(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModEnchantments.BRANCH_FINDING);
    }

    /** A real server player (Branch Finding ignores fake ones) on a fake player's connection, which discards packets. */
    private static ServerPlayer player(GameTestHelper helper, net.minecraft.world.item.Item tool) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "spectral_enchantments_branch");
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
        player.connection = FakePlayerFactory.get(helper.getLevel(), profile).connection;
        BlockPos stand = helper.absolutePos(new BlockPos(4, 1, 0));
        player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
        ItemStack stack = new ItemStack(tool);
        stack.enchant(holder(helper), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }
}
