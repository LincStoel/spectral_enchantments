package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.FarMiner;
import com.linc.spectralenchantments.network.FarMinePayload;
import com.linc.spectralenchantments.network.FarMineStatePayload;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Far Miner on the client: locks the local player's spectral tool on to a distant block they keep looking at,
 * drives the mine while attack is held, and tracks the spectral tools of nearby Far Mining players.
 *
 * <p>A lock forms after {@link FarMiner#LOCK_TICKS} on the same block beyond normal reach. Until the player
 * starts mining, looking away for a moment dissolves it; once they hold attack it stays put wherever they look,
 * until the block breaks, attack is released, the tool changes, they start mining a block within reach, or they
 * sneak (sneaking turns Far Miner off).
 */
public final class FarMinerClient {
    static final int FADE_IN_TICKS = 6;
    static final int FADE_OUT_TICKS = 7;
    private static final int LOOK_AWAY_TICKS = 10;

    static final class Ghost {
        final int entityId;
        final boolean local;
        final BlockPos pos;
        final Direction face;
        /** Horizontal direction the miner faced when the lock formed: the swing plane on floors and ceilings. */
        final Direction look;
        final ItemStack tool;
        final ItemStack ghostTool;
        final boolean shovel;
        final int slot;
        final long startTick;
        long miningTick = -1;
        long endTick = -1;
        boolean broken;
        int lookAway;

        Ghost(int entityId, boolean local, BlockPos pos, Direction face, Direction look, ItemStack tool, int slot) {
            this.entityId = entityId;
            this.local = local;
            this.pos = pos.immutable();
            this.face = face;
            this.look = look;
            this.tool = tool;
            this.ghostTool = tool.copy();
            this.ghostTool.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            this.shovel = tool.is(ItemTags.SHOVELS);
            this.slot = slot;
            this.startTick = ticks;
        }

        boolean active() {
            return endTick < 0;
        }

        boolean mining() {
            return miningTick >= 0;
        }
    }

    private static final Map<Integer, Ghost> GHOSTS = new HashMap<>();
    @Nullable
    private static BlockPos dwellPos;
    private static int dwellTicks;
    static long ticks;

    private FarMinerClient() {}

    static Collection<Ghost> all() {
        return GHOSTS.values();
    }

    public static void handleState(FarMineStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Ghost current = GHOSTS.get(payload.entityId());
        if (payload.entityId() == mc.player.getId()) {
            // Our own lock is driven locally; the server only reports that it ended (broken, or refused).
            if (!payload.active() && current != null && current.active() && current.pos.equals(payload.pos())) {
                end(current, payload.broken());
            }
            return;
        }
        if (payload.active()) {
            Entity miner = mc.level.getEntity(payload.entityId());
            Ghost ghost = new Ghost(payload.entityId(), false, payload.pos(), payload.face(),
                    miner != null ? miner.getDirection() : Direction.NORTH, payload.tool(), -1);
            ghost.miningTick = ticks;
            GHOSTS.put(payload.entityId(), ghost);
        } else if (current != null) {
            end(current, payload.broken());
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            GHOSTS.clear();
            dwellPos = null;
            return;
        }
        ticks++;
        tickLocal(mc, mc.player);

        Iterator<Ghost> it = GHOSTS.values().iterator();
        while (it.hasNext()) {
            Ghost ghost = it.next();
            if (ghost.active() && !ghost.local) {
                if (mc.level.getBlockState(ghost.pos).isAir()) {
                    end(ghost, true);
                } else if (mc.level.getEntity(ghost.entityId) == null) {
                    end(ghost, false); // the miner left view, so its end packet may never come
                }
            }
            if (!ghost.active() && ticks - ghost.endTick > FADE_OUT_TICKS) {
                it.remove();
            }
        }
    }

    private static void tickLocal(Minecraft mc, LocalPlayer player) {
        int level = FarMiner.getActiveLevel(player);
        boolean nearMining = mc.gameMode != null && mc.gameMode.isDestroying();
        boolean attacking = mc.options.keyAttack.isDown();
        Ghost lock = GHOSTS.get(player.getId());

        if (lock != null && lock.active()) {
            if (mc.level.getBlockState(lock.pos).isAir()) {
                release(lock, true);
            } else if (level <= 0 || nearMining
                    || player.getInventory().selected != lock.slot
                    || !ItemStack.isSameItem(player.getMainHandItem(), lock.tool)) {
                release(lock, false);
            } else if (lock.mining()) {
                if (attacking) {
                    player.swing(InteractionHand.MAIN_HAND);
                } else {
                    release(lock, false);
                }
            } else {
                BlockHitResult hit = farPick(mc, player, level);
                lock.lookAway = hit != null && hit.getBlockPos().equals(lock.pos) ? 0 : lock.lookAway + 1;
                if (lock.lookAway > LOOK_AWAY_TICKS) {
                    release(lock, false);
                } else if (attacking && !targetingNear(mc)) {
                    lock.miningTick = ticks;
                    PacketDistributor.sendToServer(FarMinePayload.start(lock.pos, lock.face));
                    player.swing(InteractionHand.MAIN_HAND);
                }
            }
            return;
        }

        BlockHitResult hit = level > 0 && !nearMining && !targetingNear(mc) ? farPick(mc, player, level) : null;
        if (hit == null) {
            dwellPos = null;
            return;
        }
        if (!hit.getBlockPos().equals(dwellPos)) {
            dwellPos = hit.getBlockPos().immutable();
            dwellTicks = 0;
        }
        if (++dwellTicks >= FarMiner.LOCK_TICKS) {
            dwellPos = null;
            GHOSTS.put(player.getId(), new Ghost(player.getId(), true, hit.getBlockPos(), hit.getDirection(),
                    player.getDirection(), player.getMainHandItem().copy(), player.getInventory().selected));
        }
    }

    /** Whether vanilla is already targeting something (a block or entity) within normal reach. */
    private static boolean targetingNear(Minecraft mc) {
        return mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS;
    }

    /** The block the player looks at beyond normal reach but within Far Miner's, or null. */
    @Nullable
    private static BlockHitResult farPick(Minecraft mc, LocalPlayer player, int level) {
        HitResult hit = player.pick(FarMiner.reach(player, level), 1f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || player.getEyePosition().distanceTo(hit.getLocation()) <= player.blockInteractionRange()
                || mc.level.getBlockState(blockHit.getBlockPos()).isAir()) {
            return null;
        }
        return blockHit;
    }

    private static void release(Ghost lock, boolean broken) {
        if (lock.mining() && !broken) {
            PacketDistributor.sendToServer(FarMinePayload.stop());
        }
        end(lock, broken);
    }

    private static void end(Ghost ghost, boolean broken) {
        if (ghost.active()) {
            ghost.endTick = ticks;
            ghost.broken = broken;
        }
    }
}
