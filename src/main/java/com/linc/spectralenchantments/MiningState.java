package com.linc.spectralenchantments;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player mining target, recorded identically on client and server from the START left-click
 * so that both sides compute the same area (and therefore the same break speed).
 */
public final class MiningState {
    public record Target(BlockPos pos, Direction face) {}

    // Keyed by Player instance, so the integrated server's and the client's players never collide.
    private static final Map<Player, Target> TARGETS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Set while we query per-block destroy progress, which re-fires BreakSpeed. */
    public static final ThreadLocal<Boolean> CALCULATING_SPEED = ThreadLocal.withInitial(() -> false);
    /** Set while the server breaks the extra blocks, which re-fires BreakEvent. */
    public static final ThreadLocal<Boolean> BREAKING_AREA = ThreadLocal.withInitial(() -> false);

    private MiningState() {}

    public static void setTarget(Player player, BlockPos pos, Direction face) {
        TARGETS.put(player, new Target(pos.immutable(), face));
    }

    @Nullable
    public static Target getTarget(Player player) {
        return TARGETS.get(player);
    }

    @Nullable
    public static Direction getFace(Player player, BlockPos pos) {
        Target target = TARGETS.get(player);
        return target != null && target.pos().equals(pos) ? target.face() : null;
    }

    public static void clear(Player player) {
        TARGETS.remove(player);
    }
}
