package com.linc.spectralenchantments;

import com.linc.spectralenchantments.network.BranchFindingPayload;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Branch Finding: using the enchanted hoe or shears on leaves searches the whole connected leaf mass and outlines,
 * for the user only, every log touching it, so the branches hidden inside a canopy are easy to find. Costs 1
 * durability per use.
 */
public final class BranchFinding {
    /** What gets outlined: {@code #minecraft:logs} (logs, wood and their stripped forms) by default. */
    public static final TagKey<Block> TARGETS = TagKey.create(Registries.BLOCK, SpectralEnchantments.id("branch_finding_targets"));

    /** Leaves searched per tick; at the default limit a search is done at once. */
    private static final int LEAVES_PER_TICK = 4096;
    /** Searches too big for one tick, carried on over the next. Server thread. */
    private static final Map<ServerPlayer, Search> SEARCHES = new WeakHashMap<>();

    private BranchFinding() {}

    public static boolean has(Level level, ItemStack stack) {
        return ModEnchantments.has(level, ModEnchantments.BRANCH_FINDING, stack);
    }

    /** Fires on both sides: the client swings the arm, the server searches. Hoes still till, since leaves never do. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        ItemStack stack = event.getItemStack();
        // Fake players (Create's deployer, say) would only wear the tool down.
        if (player instanceof FakePlayer || player.isSpectator() || !has(level, stack)
                || StackCooldowns.isActive(stack, level)
                || !level.getBlockState(event.getPos()).is(BlockTags.LEAVES)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (player instanceof ServerPlayer serverPlayer) {
            use(serverPlayer, event);
        }
    }

    private static void use(ServerPlayer player, PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Search search = new Search(player.serverLevel(), event.getPos(), SpectralConfig.branchFindingMaxLeaves());
        if (search.step(LEAVES_PER_TICK)) {
            finish(player, search);
        } else {
            // A huge canopy: carry on over the next ticks rather than stall this one.
            SEARCHES.put(player, search);
        }
        StackCooldowns.start(stack, player.level(), SpectralConfig.branchFindingCooldownTicks());
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(event.getHand()));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (SEARCHES.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Search search = SEARCHES.get(player);
        if (search == null) {
            return;
        }
        if (search.level != player.level()) {
            SEARCHES.remove(player); // the outlines belong to the dimension it was used in
        } else if (search.step(LEAVES_PER_TICK)) {
            SEARCHES.remove(player);
            finish(player, search);
        }
    }

    private static void finish(ServerPlayer player, Search search) {
        PacketDistributor.sendToPlayer(player, new BranchFindingPayload(search.found, SpectralConfig.branchFindingDurationSeconds() * 20));
        player.level().playSound(null, search.start, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f,
                search.found.isEmpty() ? 0.6f : 1.2f);
    }

    /**
     * Every {@link #TARGETS} block sharing a face with the leaf mass around {@code start}, nearest first. Leaves
     * touching at an edge or corner count as connected, since canopies are often joined only diagonally. At most
     * {@code maxLeaves} leaves are searched, nearest first, and unloaded chunks are left alone.
     */
    public static List<BlockPos> search(Level level, BlockPos start, int maxLeaves) {
        Search search = new Search(level, start, maxLeaves);
        search.step(Integer.MAX_VALUE);
        return search.found;
    }

    /** A breadth-first walk through a leaf mass, done a budget of leaves at a time. */
    static final class Search {
        final Level level;
        final BlockPos start;
        final List<BlockPos> found = new ArrayList<>();
        private final int maxLeaves;
        private final LongSet visited = new LongOpenHashSet();
        private final LongSet foundSet = new LongOpenHashSet();
        private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        private final BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        private int searched;

        Search(Level level, BlockPos start, int maxLeaves) {
            this.level = level;
            this.start = start.immutable();
            this.maxLeaves = maxLeaves;
            if (level.getBlockState(start).is(BlockTags.LEAVES)) {
                visited.add(start.asLong());
                queue.add(this.start);
            }
        }

        /** Searches up to {@code budget} more leaves; returns whether the search is done. */
        boolean step(int budget) {
            int stop = (int) Math.min((long) searched + budget, maxLeaves);
            while (!queue.isEmpty() && searched < stop) {
                BlockPos leaf = queue.poll();
                searched++;
                // Each of the 26 neighbours is looked at once: a log against a face is found, leaves anywhere join.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            next.setWithOffset(leaf, dx, dy, dz);
                            long key = next.asLong();
                            if (visited.contains(key) || !level.isLoaded(next)) {
                                continue;
                            }
                            BlockState state = level.getBlockState(next);
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1 && state.is(TARGETS) && foundSet.add(key)) {
                                found.add(next.immutable());
                            }
                            if (state.is(BlockTags.LEAVES)) {
                                visited.add(key);
                                queue.add(next.immutable());
                            }
                        }
                    }
                }
            }
            return queue.isEmpty() || searched >= maxLeaves;
        }
    }
}
