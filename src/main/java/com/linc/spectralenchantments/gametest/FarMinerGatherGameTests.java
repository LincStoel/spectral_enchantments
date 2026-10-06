package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.FarMiner;
import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for Far Miner and Gather. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarMinerGatherGameTests {
    private static final BlockPos FAR_TARGET = new BlockPos(4, 1, 8);

    private FarMinerGatherGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void farMinerBreaksDistantBlock(GameTestHelper helper) {
        helper.setBlock(FAR_TARGET, Blocks.STONE);
        // Eyes about 8 blocks from the stone: beyond normal reach (4.5), within level II's 12.
        FakePlayer player = farMiner(helper, new BlockPos(4, 1, 0), 2);
        helper.assertTrue(FarMiner.start(player, helper.absolutePos(FAR_TARGET), Direction.NORTH), "Level II should reach the stone");
        // Fake players don't tick, so drive the mine by hand.
        helper.onEachTick(() -> {
            if (FarMiner.isMining(player)) {
                FarMiner.onPlayerTick(new PlayerTickEvent.Post(player));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, FAR_TARGET);
            helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "Breaking should cost 1 durability");
            helper.assertTrue(!FarMiner.isMining(player), "Session should end after the break");
        });
    }

    @GameTest(template = "empty")
    public static void farMinerRejectsBlocksBeyondRange(GameTestHelper helper) {
        helper.setBlock(FAR_TARGET, Blocks.STONE);
        // About 14 blocks away: too far for level I (8), close enough for level III (16).
        BlockPos stand = new BlockPos(4, 1, -6);
        helper.assertTrue(!FarMiner.start(farMiner(helper, stand, 1), helper.absolutePos(FAR_TARGET), Direction.NORTH),
                "Level I should not reach 14 blocks");
        FakePlayer far = farMiner(helper, stand, 3);
        helper.assertTrue(FarMiner.start(far, helper.absolutePos(FAR_TARGET), Direction.NORTH), "Level III should reach 14 blocks");
        FarMiner.cancel(far);
        // About 18 blocks away: past level III's 16.
        helper.assertTrue(!FarMiner.start(farMiner(helper, new BlockPos(4, 1, -10), 3), helper.absolutePos(FAR_TARGET), Direction.NORTH),
                "Level III should not reach 18 blocks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void farMinerHasItsOwnSpeedPenalty(GameTestHelper helper) {
        helper.setBlock(FAR_TARGET, Blocks.STONE);
        FakePlayer player = farMiner(helper, new BlockPos(4, 1, 0), 2);
        BlockPos pos = helper.absolutePos(FAR_TARGET);
        float normal = helper.getLevel().getBlockState(pos).getDestroyProgress(player, helper.getLevel(), pos);
        helper.assertTrue(FarMiner.start(player, pos, Direction.NORTH), "Level II should reach the stone");
        FarMiner.onPlayerTick(new PlayerTickEvent.Post(player));
        Float progress = FarMiner.progress(player);
        FarMiner.cancel(player);
        helper.assertTrue(progress != null && Math.abs(progress - normal * SpectralConfig.farMinerSpeed()) < 1e-6f,
                "One tick of Far Mining should make " + SpectralConfig.farMinerSpeed() + " of a normal tick's progress, got " + progress);
        // Airborne, vanilla mining slows to a fifth, but Far Miner's penalty replaces that one.
        player.setOnGround(false);
        helper.assertTrue(FarMiner.start(player, pos, Direction.NORTH), "Level II should reach the stone");
        FarMiner.onPlayerTick(new PlayerTickEvent.Post(player));
        Float airborne = FarMiner.progress(player);
        FarMiner.cancel(player);
        helper.assertTrue(airborne != null && Math.abs(airborne - progress) < 1e-6f,
                "Being airborne should not slow Far Mining further, got " + airborne + " vs " + progress);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void farMinerIsOffWhileSneaking(GameTestHelper helper) {
        helper.setBlock(FAR_TARGET, Blocks.STONE);
        FakePlayer player = farMiner(helper, new BlockPos(4, 1, 0), 2);
        player.setShiftKeyDown(true);
        helper.assertTrue(FarMiner.getActiveLevel(player) == 0, "Sneaking should turn Far Miner off");
        helper.assertTrue(!FarMiner.start(player, helper.absolutePos(FAR_TARGET), Direction.NORTH), "A sneaking player should not Far Mine");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void farMinerSkipsPickarangs(GameTestHelper helper) {
        Enchantment farMiner = holder(helper, ModEnchantments.FAR_MINER).value();
        helper.assertTrue(farMiner.isSupportedItem(new ItemStack(Items.DIAMOND_PICKAXE)), "Pickaxes should take Far Miner");
        for (String id : new String[] {"pickarang", "flamerang"}) {
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("quark", id)).ifPresent(item -> {
                ItemStack stack = new ItemStack(item);
                helper.assertTrue(!farMiner.isSupportedItem(stack), "Quark's " + id + " should not take Far Miner");
                stack.enchant(holder(helper, ModEnchantments.FAR_MINER), 3);
                helper.assertTrue(FarMiner.getLevel(farMiner(helper, new BlockPos(4, 1, 0), 1), stack) == 0,
                        "Far Miner should do nothing on Quark's " + id);
            });
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void farMinerAndBroadBreakerAreExclusive(GameTestHelper helper) {
        helper.assertTrue(!Enchantment.areCompatible(holder(helper, ModEnchantments.FAR_MINER), holder(helper, ModEnchantments.BROAD_BREAKER)),
                "Far Miner and Broad Breaker should be mutually exclusive");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gatherPutsBlockDropsInInventory(GameTestHelper helper) {
        ServerPlayer player = gatherer(helper, Items.DIAMOND_PICKAXE);
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos, Blocks.STONE);
        drop(helper, player, pos);
        helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 1, "Cobblestone should go straight to the inventory");
        helper.assertTrue(itemsOnGround(helper) == 0, "Nothing should drop on the ground");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gatherDropsWhatDoesNotFit(GameTestHelper helper) {
        ServerPlayer player = gatherer(helper, Items.DIAMOND_PICKAXE);
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            if (slot != player.getInventory().selected) {
                player.getInventory().items.set(slot, new ItemStack(Items.DIRT, 64));
            }
        }
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos, Blocks.STONE);
        drop(helper, player, pos);
        helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 0, "A full inventory should take nothing");
        helper.assertTrue(itemsOnGround(helper) == 1, "The cobblestone should drop as normal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gatherPutsMobDropsInInventory(GameTestHelper helper) {
        ServerPlayer player = gatherer(helper, Items.DIAMOND_SWORD);
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(4, 2, 4));
        sheep.hurt(player.damageSources().playerAttack(player), 1000f);
        helper.assertTrue(player.getInventory().countItem(Items.WHITE_WOOL) >= 1, "The sheep's wool should go straight to the inventory");
        helper.assertTrue(itemsOnGround(helper) == 0, "Nothing should drop on the ground");
        helper.succeed();
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(holder(helper, key), level);
        return stack;
    }

    private static FakePlayer farMiner(GameTestHelper helper, BlockPos relative, int level) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "spectral_enchantments_far"));
        BlockPos stand = helper.absolutePos(relative);
        player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
        player.setOnGround(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.DIAMOND_PICKAXE, ModEnchantments.FAR_MINER, level));
        return player;
    }

    /**
     * A real (not fake) server player, since Gather leaves fake players' drops alone. It is never logged in (other
     * mods would push their payloads at it), and it borrows a fake player's connection, which discards packets.
     */
    private static ServerPlayer gatherer(GameTestHelper helper, Item tool) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "spectral_enchantments_gather");
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
        player.connection = FakePlayerFactory.get(helper.getLevel(), profile).connection;
        BlockPos stand = helper.absolutePos(new BlockPos(4, 2, 2));
        player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, tool, ModEnchantments.GATHER, 1));
        return player;
    }

    /** Drops the block's loot as the player breaking it with their held tool would. */
    private static void drop(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        Block.dropResources(helper.getLevel().getBlockState(pos), helper.getLevel(), pos, null, player, player.getMainHandItem());
        helper.getLevel().removeBlock(pos, false);
    }

    private static int itemsOnGround(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).size();
    }
}
