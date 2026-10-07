package com.linc.spectralenchantments;

import com.linc.spectralenchantments.network.BranchFindingPayload;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Branch Finding: using the enchanted hoe or shears on leaves searches the whole connected leaf mass and outlines,
 * for the user only, every log touching it, so the branches hidden inside a canopy are easy to find. Costs 1
 * durability per use.
 */
public final class BranchFinding {
    /** What gets outlined: {@code #minecraft:logs} (logs, wood and their stripped forms) by default. */
    public static final TagKey<Block> TARGETS = TagKey.create(Registries.BLOCK, SpectralEnchantments.id("branch_finding_targets"));

    private BranchFinding() {}

    public static boolean has(Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SpectralConfig.branchFindingEnabled()) {
            return false;
        }
        return level.registryAccess().registry(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.getHolder(ModEnchantments.BRANCH_FINDING))
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0)
                .orElse(false);
    }

    /** Fires on both sides: the client swings the arm, the server searches. Hoes still till, since leaves never do. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        ItemStack stack = event.getItemStack();
        // Fake players (Create's deployer, say) would only wear the tool down.
        if (player instanceof FakePlayer || player.isSpectator() || !has(level, stack)
                || player.getCooldowns().isOnCooldown(stack.getItem())
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
        List<BlockPos> found = search(player.level(), event.getPos(), SpectralConfig.branchFindingMaxLeaves());
        PacketDistributor.sendToPlayer(player, new BranchFindingPayload(found, SpectralConfig.branchFindingDurationSeconds() * 20));
        player.level().playSound(null, event.getPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f,
                found.isEmpty() ? 0.6f : 1.2f);
        int cooldown = SpectralConfig.branchFindingCooldownTicks();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(stack.getItem(), cooldown);
        }
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(event.getHand()));
    }

    /**
     * Every {@link #TARGETS} block sharing a face with the leaf mass around {@code start}, nearest first. Leaves
     * touching at an edge or corner count as connected, since canopies are often joined only diagonally. At most
     * {@code maxLeaves} leaves are searched, nearest first, and unloaded chunks are left alone.
     */
    public static List<BlockPos> search(Level level, BlockPos start, int maxLeaves) {
        List<BlockPos> found = new ArrayList<>();
        if (!level.getBlockState(start).is(BlockTags.LEAVES)) {
            return found;
        }
        LongSet visited = new LongOpenHashSet();
        LongSet foundSet = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        int searched = 0;
        while (!queue.isEmpty() && searched < maxLeaves) {
            BlockPos leaf = queue.poll();
            searched++;
            for (Direction direction : Direction.values()) {
                next.setWithOffset(leaf, direction);
                if (level.isLoaded(next) && level.getBlockState(next).is(TARGETS) && foundSet.add(next.asLong())) {
                    found.add(next.immutable());
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        next.setWithOffset(leaf, dx, dy, dz);
                        if (!visited.contains(next.asLong()) && level.isLoaded(next) && level.getBlockState(next).is(BlockTags.LEAVES)) {
                            visited.add(next.asLong());
                            queue.add(next.immutable());
                        }
                    }
                }
            }
        }
        return found;
    }
}
