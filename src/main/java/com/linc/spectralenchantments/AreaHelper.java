package com.linc.spectralenchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Shared (client + server) geometry and filtering for the Broad Breaker mining area. */
public final class AreaHelper {
    /** How long a cached area is trusted before it is looked at again, in ticks (blocks in it may have changed). */
    private static final int CACHE_TICKS = 4;

    /**
     * One player's area for the block they are mining, kept between ticks: break speed is asked every tick (on both
     * sides) and the outline every frame, and the area only changes when the target, the tool or the blocks do.
     */
    public static final class Area {
        final BlockPos origin;
        final Direction face;
        final ItemStack tool;
        final int level;
        final long time;
        public final List<BlockPos> extras;
        /** The slowest block's break speed over the origin's, or NaN until {@link CommonEvents} works it out. */
        public float slowestRatio = Float.NaN;

        Area(BlockPos origin, Direction face, ItemStack tool, int level, long time, List<BlockPos> extras) {
            this.origin = origin;
            this.face = face;
            this.tool = tool;
            this.level = level;
            this.time = time;
            this.extras = extras;
        }

        boolean matches(Player player, BlockPos origin, Direction face, int level) {
            return this.origin.equals(origin) && this.face == face && this.tool == player.getMainHandItem()
                    && this.level == level && Math.abs(player.level().getGameTime() - time) < CACHE_TICKS;
        }
    }

    // Keyed by Player instance, so the integrated server's and the client's players never collide.
    private static final Map<Player, Area> AREAS = Collections.synchronizedMap(new WeakHashMap<>());

    private AreaHelper() {}

    /** As {@link #getExtraBlocks}, reusing the area worked out a moment ago for the same target and tool. */
    public static Area area(Player player, BlockPos origin, Direction face) {
        int level = getActiveLevel(player);
        Area area = AREAS.get(player);
        if (area == null || !area.matches(player, origin, face, level)) {
            area = new Area(origin.immutable(), face, player.getMainHandItem(), level, player.level().getGameTime(),
                    level > 0 ? List.copyOf(getExtraBlocks(player, origin, face)) : List.of());
            AREAS.put(player, area);
        }
        return area;
    }

    public static int getLevel(Player player, ItemStack stack) {
        return ModEnchantments.level(player.level(), ModEnchantments.BROAD_BREAKER, stack);
    }

    /** Whether the player's area mining is currently switched on (enchanted tool, not sneaking, may build). */
    public static int getActiveLevel(Player player) {
        if (player.isShiftKeyDown() || player.isSpectator() || !player.getAbilities().mayBuild) {
            return 0;
        }
        return getLevel(player, player.getMainHandItem());
    }

    /**
     * The blocks that the spectral tools will break alongside {@code origin}, excluding the origin itself.
     * Empty when area mining does not apply to this block.
     */
    public static List<BlockPos> getExtraBlocks(Player player, BlockPos origin, Direction face) {
        int level = getActiveLevel(player);
        if (level <= 0) {
            return List.of();
        }
        Level world = player.level();
        ItemStack tool = player.getMainHandItem();
        if (!canAreaMine(world, tool, origin, world.getBlockState(origin))) {
            return List.of();
        }

        int radius = radius(level);
        List<BlockPos> result = new ArrayList<>();
        if (face.getAxis() == Direction.Axis.Y) {
            // Mining a floor or ceiling: centered square.
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    addIfMineable(result, world, tool, origin, origin.offset(a, 0, b));
                }
            }
        } else {
            // Mining a wall: the mined block is centered horizontally and second from the bottom.
            Direction.Axis across = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            int top = 2 * radius - 1;
            for (int dy = top; dy >= -1; dy--) {
                for (int h = -radius; h <= radius; h++) {
                    BlockPos pos = across == Direction.Axis.X ? origin.offset(h, dy, 0) : origin.offset(0, dy, h);
                    addIfMineable(result, world, tool, origin, pos);
                }
            }
        }
        return result;
    }

    /** Blocks either side of the mined one; the area is {@code 2 * radius + 1} blocks across (see {@link SpectralConfig}). */
    public static int radius(int level) {
        return SpectralConfig.broadBreakerArea(level) / 2;
    }

    private static void addIfMineable(List<BlockPos> out, Level world, ItemStack tool, BlockPos origin, BlockPos pos) {
        if (!pos.equals(origin) && world.isLoaded(pos) && canAreaMine(world, tool, pos, world.getBlockState(pos))) {
            out.add(pos.immutable());
        }
    }

    /** Tool type and tier rules: only blocks the tool can correctly harvest are part of the area. */
    public static boolean canAreaMine(Level world, ItemStack tool, BlockPos pos, BlockState state) {
        return !state.isAir()
                && state.getDestroySpeed(world, pos) >= 0
                && tool.isCorrectToolForDrops(state)
                && world.getWorldBorder().isWithinBounds(pos);
    }

    /** Total durability cost for breaking {@code blocksBroken} blocks at once. */
    public static int durabilityCost(int blocksBroken) {
        return Math.max(1, blocksBroken / 2);
    }
}
