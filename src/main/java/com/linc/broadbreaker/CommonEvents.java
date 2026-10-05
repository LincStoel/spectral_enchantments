package com.linc.broadbreaker;

import com.linc.broadbreaker.network.SpectralMiningPayload;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Game-bus handlers shared by both sides: target tracking, break speed, and (server) area breaking. */
public final class CommonEvents {
    /** Server players whose spectral tools are currently shown to other players. */
    private static final Set<Player> BROADCASTING = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private CommonEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        switch (event.getAction()) {
            case START -> {
                if (event.getFace() == null) {
                    return;
                }
                MiningState.setTarget(player, event.getPos(), event.getFace());
                if (player instanceof ServerPlayer serverPlayer) {
                    startBroadcast(serverPlayer, event.getPos(), event.getFace());
                }
            }
            case ABORT -> {
                if (player instanceof ServerPlayer serverPlayer) {
                    endBroadcast(serverPlayer, false);
                }
            }
            default -> {}
        }
    }

    /** The whole area takes as long as its slowest block. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (MiningState.CALCULATING_SPEED.get()) {
            return;
        }
        BlockPos origin = event.getPosition().orElse(null);
        if (origin == null) {
            return;
        }
        Player player = event.getEntity();
        Direction face = MiningState.getFace(player, origin);
        if (face == null) {
            return;
        }
        List<BlockPos> extras = AreaHelper.getExtraBlocks(player, origin, face);
        if (extras.isEmpty()) {
            return;
        }

        float self;
        float slowest;
        MiningState.CALCULATING_SPEED.set(true);
        try {
            self = event.getState().getDestroyProgress(player, player.level(), origin);
            slowest = self;
            for (BlockPos pos : extras) {
                slowest = Math.min(slowest, player.level().getBlockState(pos).getDestroyProgress(player, player.level(), pos));
            }
        } finally {
            MiningState.CALCULATING_SPEED.set(false);
        }
        if (self > 0 && slowest < self) {
            event.setNewSpeed(event.getNewSpeed() * (slowest / self));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (MiningState.BREAKING_AREA.get()
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos origin = event.getPos();
        Direction face = MiningState.getFace(player, origin);
        if (face == null) {
            return;
        }
        endBroadcast(player, true);
        List<BlockPos> extras = AreaHelper.getExtraBlocks(player, origin, face);
        MiningState.clear(player);
        if (extras.isEmpty()) {
            return;
        }

        ItemStack tool = player.getMainHandItem();
        ItemStack toolSnapshot = tool.copy();
        boolean creative = player.isCreative();
        int broken = 1; // the origin, which vanilla breaks after this event
        MiningState.BREAKING_AREA.set(true);
        try {
            for (BlockPos pos : extras) {
                if (breakExtraBlock(level, player, pos, toolSnapshot, creative)) {
                    broken++;
                }
            }
        } finally {
            MiningState.BREAKING_AREA.set(false);
        }

        // Vanilla charges 1 durability for the origin; charge the rest once the origin is done,
        // so a tool that runs out here still drops the origin block.
        int extraCost = AreaHelper.durabilityCost(broken) - 1;
        if (extraCost > 0 && !creative) {
            level.getServer().tell(new TickTask(level.getServer().getTickCount(), () -> {
                if (!tool.isEmpty() && player.getMainHandItem() == tool) {
                    tool.hurtAndBreak(extraCost, player, EquipmentSlot.MAINHAND);
                }
            }));
        }
    }

    /** Mirrors ServerPlayerGameMode#destroyBlock, minus the per-block tool damage. */
    private static boolean breakExtraBlock(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack tool, boolean creative) {
        BlockState state = level.getBlockState(pos);
        GameType gameType = player.gameMode.getGameModeForPlayer();
        if (!AreaHelper.canAreaMine(level, tool, pos, state)
                || !level.mayInteract(player, pos)
                || player.blockActionRestricted(level, pos, gameType)
                || (state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks())) {
            return false;
        }
        if (CommonHooks.fireBlockBreak(level, gameType, player, pos, state).isCanceled()) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        Block block = state.getBlock();
        BlockState destroyed = block.playerWillDestroy(level, pos, state, player);
        // playerWillDestroy shows break particles to everyone except the breaker, whose client only predicts the origin.
        player.connection.send(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state), false));

        boolean canHarvest = !creative && destroyed.canHarvestBlock(level, pos, player);
        if (!destroyed.onDestroyedByPlayer(level, pos, player, canHarvest, level.getFluidState(pos))) {
            return false;
        }
        block.destroy(level, pos, destroyed);
        if (canHarvest) {
            block.playerDestroy(level, player, pos, destroyed, blockEntity, tool);
        }
        return true;
    }

    private static void startBroadcast(ServerPlayer player, BlockPos origin, Direction face) {
        List<BlockPos> extras = AreaHelper.getExtraBlocks(player, origin, face);
        float rate = player.isCreative() ? 1f : player.level().getBlockState(origin).getDestroyProgress(player, player.level(), origin);
        if (extras.isEmpty() || rate >= 1f) {
            endBroadcast(player, false);
            return;
        }
        BROADCASTING.add(player);
        PacketDistributor.sendToPlayersTrackingEntity(player,
                SpectralMiningPayload.start(player.getId(), origin, face, extras, player.getMainHandItem().copy(), rate));
    }

    private static void endBroadcast(ServerPlayer player, boolean broken) {
        if (BROADCASTING.remove(player)) {
            PacketDistributor.sendToPlayersTrackingEntity(player, SpectralMiningPayload.end(player.getId(), broken));
        }
    }
}
