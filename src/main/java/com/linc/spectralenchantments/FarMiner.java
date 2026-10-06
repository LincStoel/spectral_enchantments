package com.linc.spectralenchantments;

import com.linc.spectralenchantments.network.FarMinePayload;
import com.linc.spectralenchantments.network.FarMineStatePayload;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Far Miner: the client locks onto a block beyond normal reach (see {@code FarMinerClient}) and, while attack is
 * held, asks the server to mine it. The server is authoritative: it checks the request, accumulates break progress
 * every tick exactly as vanilla would, shows the cracks to everyone, and breaks the block through the player's
 * game mode (so drops, durability, stats and other mods' break events all behave as a normal mine).
 */
public final class FarMiner {
    /** Ticks the player must look at a distant block before the spectral tool locks on to it. */
    public static final int LOCK_TICKS = 15;
    /** Slack (blocks) on the server's range check, for movement between the client's pick and the server's check. */
    private static final double RANGE_SLACK = 0.5;
    /** Tools Far Miner never works on (Quark's pickarangs), even if one somehow carries it. */
    public static final TagKey<Item> EXCLUDED = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(SpectralEnchantments.MOD_ID, "far_miner_excluded"));

    private static final class Session {
        final ServerLevel level;
        final BlockPos pos;
        final BlockState state;
        final int slot;
        final ItemStack tool;
        float progress;
        int ticks;
        int lastStage = -1;

        Session(ServerLevel level, BlockPos pos, BlockState state, int slot, ItemStack tool) {
            this.level = level;
            this.pos = pos;
            this.state = state;
            this.slot = slot;
            this.tool = tool;
        }
    }

    private static final Map<ServerPlayer, Session> SESSIONS = Collections.synchronizedMap(new WeakHashMap<>());

    private FarMiner() {}

    public static int getLevel(Player player, ItemStack stack) {
        if (stack.isEmpty() || !SpectralConfig.farMinerEnabled() || stack.is(EXCLUDED)) {
            return 0;
        }
        return player.level().registryAccess().registry(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.getHolder(ModEnchantments.FAR_MINER))
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
                .orElse(0);
    }

    /** The main-hand tool's Far Miner level, or 0 when the player may not Far Mine right now (sneaking turns it off). */
    public static int getActiveLevel(Player player) {
        if (player.isSpectator() || !player.getAbilities().mayBuild || player.isShiftKeyDown()) {
            return 0;
        }
        return getLevel(player, player.getMainHandItem());
    }

    /** The farthest (from the eyes) Far Miner reaches at {@code level}; never short of normal block reach. */
    public static double reach(Player player, int level) {
        return Math.max(player.blockInteractionRange(), SpectralConfig.farMinerRange(level));
    }

    public static void handle(ServerPlayer player, FarMinePayload payload) {
        if (payload.start()) {
            start(player, payload.pos(), payload.face());
        } else {
            cancel(player);
        }
    }

    /** Starts mining {@code pos}; returns false (and tells the client) if the request is not allowed. */
    public static boolean start(ServerPlayer player, BlockPos pos, Direction face) {
        cancel(player);
        ServerLevel level = player.serverLevel();
        int enchantLevel = getActiveLevel(player);
        BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : null;
        ItemStack tool = player.getMainHandItem();
        if (enchantLevel <= 0
                || state == null
                || state.isAir()
                || state.getDestroySpeed(level, pos) < 0
                || !inRange(player, pos, enchantLevel)
                || !level.getWorldBorder().isWithinBounds(pos)
                || !level.mayInteract(player, pos)
                || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
                || !tool.getItem().canAttackBlock(state, level, pos, player)) {
            PacketDistributor.sendToPlayer(player, FarMineStatePayload.end(player.getId(), pos, false));
            return false;
        }
        SESSIONS.put(player, new Session(level, pos.immutable(), state, player.getInventory().selected, tool));
        PacketDistributor.sendToPlayersTrackingEntity(player, FarMineStatePayload.start(player.getId(), pos, face, tool.copy()));
        return true;
    }

    public static boolean isMining(ServerPlayer player) {
        return SESSIONS.containsKey(player);
    }

    /** Stops the player's Far Mine, if any (they let go, or started mining a block within normal reach). */
    public static void cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player);
        if (session != null) {
            end(player, session, false);
        }
    }

    private static boolean inRange(Player player, BlockPos pos, int enchantLevel) {
        // Measured to the nearest point of the block, as the client's pick measures to the face it hits.
        double max = reach(player, enchantLevel) + RANGE_SLACK;
        Vec3 eye = player.getEyePosition();
        Vec3 nearest = new Vec3(Mth.clamp(eye.x, pos.getX(), pos.getX() + 1),
                Mth.clamp(eye.y, pos.getY(), pos.getY() + 1), Mth.clamp(eye.z, pos.getZ(), pos.getZ() + 1));
        return eye.distanceToSqr(nearest) <= max * max;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.get(player);
            if (session != null) {
                tick(player, session);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player);
        }
    }

    private static void tick(ServerPlayer player, Session session) {
        ServerLevel level = session.level;
        BlockState current = level.getBlockState(session.pos);
        int enchantLevel = getActiveLevel(player);
        ItemStack held = player.getMainHandItem();
        if (player.level() != level
                || !player.isAlive()
                || !current.is(session.state.getBlock())
                || enchantLevel <= 0
                || player.getInventory().selected != session.slot
                || !ItemStack.isSameItem(held, session.tool)
                || !inRange(player, session.pos, enchantLevel)) {
            SESSIONS.remove(player);
            end(player, session, current.isAir());
            return;
        }

        // Slowed like mining while airborne, in place of (not on top of) vanilla's underwater and airborne penalties.
        session.progress += player.isCreative() ? 1f : baseProgress(player, current, level, session.pos) * SpectralConfig.farMinerSpeed();
        if (session.ticks++ % 4 == 0) {
            SoundType sound = current.getSoundType(level, session.pos, player);
            level.playSound(null, session.pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 8f, sound.getPitch() * 0.5f);
        }
        if (session.progress < 1f) {
            int stage = Math.min(9, (int) (session.progress * 10f));
            if (stage != session.lastStage) {
                session.lastStage = stage;
                level.destroyBlockProgress(crackId(player), session.pos, stage);
            }
            return;
        }

        SESSIONS.remove(player);
        level.destroyBlockProgress(crackId(player), session.pos, -1);
        boolean broken = player.gameMode.destroyBlock(session.pos);
        if (broken) {
            // playerWillDestroy shows break particles to everyone but the breaker, whose client normally predicts them.
            player.connection.send(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, session.pos, Block.getId(current), false));
        }
        sendEnd(player, session.pos, broken);
    }

    /**
     * Vanilla's per-tick break progress with its underwater and airborne (or mounted) penalties undone, since Far
     * Miner has its own. Those penalties are plain multipliers on the dig speed, which the progress is linear in.
     * Everything else (tool, Efficiency, Haste, Mining Fatigue, other mods' break-speed changes) still counts.
     */
    private static float baseProgress(ServerPlayer player, BlockState state, ServerLevel level, BlockPos pos) {
        float progress = state.getDestroyProgress(player, level, pos);
        if (player.isEyeInFluid(FluidTags.WATER)) {
            double submerged = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            if (submerged > 0) {
                progress /= (float) submerged;
            }
        }
        if (!player.onGround()) {
            progress *= 5f;
        }
        return progress;
    }

    private static void end(ServerPlayer player, Session session, boolean broken) {
        session.level.destroyBlockProgress(crackId(player), session.pos, -1);
        sendEnd(player, session.pos, broken);
    }

    private static void sendEnd(ServerPlayer player, BlockPos pos, boolean broken) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, FarMineStatePayload.end(player.getId(), pos, broken));
    }

    /**
     * Crack-overlay breaker id. Not the player's own id: the server skips the breaker when sending cracks, since
     * vanilla clients draw their own, and here the miner's client predicts nothing.
     */
    private static int crackId(Player player) {
        return Integer.MIN_VALUE + player.getId();
    }

    /** For tests: the current break progress, or null when not Far Mining. */
    @Nullable
    public static Float progress(ServerPlayer player) {
        Session session = SESSIONS.get(player);
        return session != null ? session.progress : null;
    }
}
