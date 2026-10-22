package com.linc.spectralenchantments.index;

import com.linc.spectralenchantments.ModItems;
import com.linc.spectralenchantments.SpectralConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The Enchanting Index on a lectern. It's set there like any lectern book (it's in {@code #minecraft:lectern_books});
 * using the lectern then lists the books in a cube {@link SpectralConfig#enchantingIndexLecternSize()} wide around it,
 * in place of vanilla's reading screen, and crouching with an empty hand pops the index back off. Clients are told
 * which lecterns hold it (see LecternBlockEntityMixin) so it can be drawn floating like an enchanting table's book.
 */
public final class IndexLectern {
    private IndexLectern() {}

    /** Whether the lectern holds the Enchanting Index. Works on both sides. */
    public static boolean holdsIndex(LecternBlockEntity lectern) {
        return lectern.getBlockState().getValue(LecternBlock.HAS_BOOK) && lectern.getBook().is(ModItems.ENCHANTING_INDEX);
    }

    /** Runs on both sides, so the client doesn't predict vanilla's reading screen for a lectern the server handles. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) || !holdsIndex(lectern)) {
            return;
        }
        Player player = event.getEntity();
        boolean emptyHand = player.getMainHandItem().isEmpty();
        // Crouching with something in hand is that item's own use, as at any lectern.
        if (player.isSecondaryUseActive() && !emptyHand || player.isSpectator() || !level.mayInteract(player, pos)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) {
            return;
        }
        if (player.isSecondaryUseActive()) {
            takeOff(player, level, pos, lectern);
            return;
        }
        ItemStack index = lectern.getBook();
        // Shares the held index's cooldown, so moving it to a lectern doesn't get around it.
        if (!SpectralConfig.enchantingIndexEnabled() || !(index.getItem() instanceof EnchantingIndexItem item)
                || player.getCooldowns().isOnCooldown(item) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        EnchantingIndex.open(serverPlayer, EnchantingIndex.holdersAroundLectern(level, pos,
                SpectralConfig.enchantingIndexLecternSize()), index);
        item.startCooldown(player);
    }

    private static void takeOff(Player player, Level level, BlockPos pos, LecternBlockEntity lectern) {
        ItemStack index = lectern.getBook().copy();
        BlockState state = level.getBlockState(pos);
        lectern.clearContent();
        LecternBlock.resetBookState(player, level, pos, state, false);
        Block.popResourceFromFace(level, pos, Direction.UP, index);
        level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1f);
    }
}
