package com.linc.broadbreaker;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Shared (client + server) geometry and filtering for the Broad Breaker mining area. */
public final class AreaHelper {
    private AreaHelper() {}

    public static int getLevel(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return player.level().registryAccess().registry(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.getHolder(ModEnchantments.BROAD_BREAKER))
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
                .orElse(0);
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

        List<BlockPos> result = new ArrayList<>();
        if (face.getAxis() == Direction.Axis.Y) {
            // Mining a floor or ceiling: centered square.
            for (int a = -level; a <= level; a++) {
                for (int b = -level; b <= level; b++) {
                    addIfMineable(result, world, tool, origin, origin.offset(a, 0, b));
                }
            }
        } else {
            // Mining a wall: the mined block is centered horizontally and second from the bottom.
            Direction.Axis across = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            int top = 2 * level - 1;
            for (int dy = top; dy >= -1; dy--) {
                for (int h = -level; h <= level; h++) {
                    BlockPos pos = across == Direction.Axis.X ? origin.offset(h, dy, 0) : origin.offset(0, dy, h);
                    addIfMineable(result, world, tool, origin, pos);
                }
            }
        }
        return result;
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
