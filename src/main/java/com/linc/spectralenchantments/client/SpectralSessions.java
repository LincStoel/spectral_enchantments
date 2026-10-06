package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.AreaHelper;
import com.linc.spectralenchantments.MiningState;
import com.linc.spectralenchantments.network.SpectralMiningPayload;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Tracks every player currently area mining that this client should visualize: the local player
 * (driven by the local game mode) and nearby players (driven by {@link SpectralMiningPayload}).
 */
public final class SpectralSessions {
    static final int FADE_IN_TICKS = 4;
    static final int BREAK_FADE_TICKS = 7;
    static final int ABORT_FADE_TICKS = 4;

    static final class Session {
        final int entityId;
        final boolean local;
        final BlockPos origin;
        final Direction face;
        final List<BlockPos> extras;
        final ItemStack ghostTool;
        final boolean shovel;
        final float progressPerTick;
        final long startTick;
        long endTick = -1;
        boolean broken;
        int lastStage = -1;

        Session(int entityId, boolean local, BlockPos origin, Direction face, List<BlockPos> extras, ItemStack tool, float progressPerTick) {
            this.entityId = entityId;
            this.local = local;
            this.origin = origin;
            this.face = face;
            this.extras = List.copyOf(extras);
            this.ghostTool = tool.copy();
            this.ghostTool.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            this.shovel = tool.is(ItemTags.SHOVELS);
            this.progressPerTick = progressPerTick;
            this.startTick = ticks;
        }

        boolean active() {
            return endTick < 0;
        }

        int fadeTicks() {
            return broken ? BREAK_FADE_TICKS : ABORT_FADE_TICKS;
        }

        /** Crack-overlay breaker id for the i-th extra block; negative so it never collides with real entity ids. */
        int crackId(int i) {
            return -(entityId * 64 + i + 1);
        }
    }

    private static final Map<Integer, Session> SESSIONS = new HashMap<>();
    static long ticks;

    private SpectralSessions() {}

    static Collection<Session> all() {
        return SESSIONS.values();
    }

    public static void handleRemote(SpectralMiningPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && payload.entityId() == mc.player.getId()) {
            return; // our own visuals are driven locally
        }
        if (payload.active()) {
            start(new Session(payload.entityId(), false, payload.origin(), payload.face(), payload.extras(), payload.tool(), payload.progressPerTick()));
        } else {
            Session session = SESSIONS.get(payload.entityId());
            if (session != null) {
                end(session, payload.broken());
            }
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            SESSIONS.clear();
            return;
        }
        ticks++;
        tickLocal(mc, mc.player);

        Iterator<Session> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            Session session = it.next();
            if (session.active()) {
                if (mc.level.getBlockState(session.origin).isAir()) {
                    end(session, true);
                } else if (!session.local && ticks - session.startTick > 40 + 1.5f / Math.max(session.progressPerTick, 1e-4f)) {
                    end(session, false); // safety net in case the end packet never arrives
                } else {
                    updateCracks(mc, session);
                }
            }
            if (!session.active() && ticks - session.endTick > session.fadeTicks()) {
                it.remove();
            }
        }
    }

    private static void tickLocal(Minecraft mc, Player player) {
        MultiPlayerGameMode gameMode = mc.gameMode;
        Session current = SESSIONS.get(player.getId());
        if (gameMode == null || !gameMode.isDestroying()) {
            if (current != null && current.active()) {
                boolean broken = mc.level.getBlockState(current.origin).isAir();
                end(current, broken);
                if (broken && gameMode != null && gameMode.destroyBlockPos.equals(current.origin)) {
                    // Vanilla keeps the old target after a break, so if the spot refills (falling sand,
                    // gravel) while attack is held it mines on without sending START, and the server
                    // never learns which face is being mined. Forget the target so the next hold
                    // starts properly. (-1, -1, -1) is vanilla's own "no target" value.
                    gameMode.destroyBlockPos = new BlockPos(-1, -1, -1);
                }
            }
            return;
        }
        BlockPos pos = gameMode.destroyBlockPos;
        if (current != null && current.active() && current.origin.equals(pos)) {
            return;
        }
        Direction face = MiningState.getFace(player, pos);
        List<BlockPos> extras = face == null ? List.of() : AreaHelper.getExtraBlocks(player, pos, face);
        if (extras.isEmpty()) {
            if (current != null && current.active()) {
                end(current, false);
            }
            return;
        }
        start(new Session(player.getId(), true, pos.immutable(), face, extras, player.getMainHandItem(), 0f));
    }

    private static void start(Session session) {
        Session previous = SESSIONS.get(session.entityId);
        if (previous != null) {
            clearCracks(previous);
        }
        SESSIONS.put(session.entityId, session);
        updateCracks(Minecraft.getInstance(), session);
    }

    private static void end(Session session, boolean broken) {
        if (!session.active()) {
            return;
        }
        session.endTick = ticks;
        session.broken = broken;
        clearCracks(session);
    }

    static float progress(Minecraft mc, Session session) {
        if (session.local) {
            return mc.gameMode != null ? mc.gameMode.destroyProgress : 0f;
        }
        return Math.min(1f, (ticks - session.startTick + 1) * session.progressPerTick);
    }

    private static void updateCracks(Minecraft mc, Session session) {
        int stage = Math.min(9, (int) (progress(mc, session) * 10f));
        if (stage == session.lastStage) {
            return;
        }
        session.lastStage = stage;
        for (int i = 0; i < session.extras.size(); i++) {
            mc.levelRenderer.destroyBlockProgress(session.crackId(i), session.extras.get(i), stage);
        }
    }

    private static void clearCracks(Session session) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < session.extras.size(); i++) {
            mc.levelRenderer.destroyBlockProgress(session.crackId(i), session.extras.get(i), -1);
        }
        session.lastStage = -1;
    }
}
