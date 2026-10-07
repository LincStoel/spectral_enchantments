package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.network.BranchFindingPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** The logs Branch Finding has outlined for the local player, and when each outline fades. */
public final class BranchFindingClient {
    static final int FADE_IN_TICKS = 4;

    /** Brightest just after {@code startTick}, fading steadily to nothing at {@code endTick}. */
    record Highlight(long startTick, long endTick) {}

    static final Map<BlockPos, Highlight> HIGHLIGHTS = new HashMap<>();
    static long ticks;
    private static ClientLevel level;

    private BranchFindingClient() {}

    public static void handle(BranchFindingPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        syncLevel(mc.level);
        // Searching again restarts the fade, so a fresh use always shows at full strength.
        Highlight highlight = new Highlight(ticks, ticks + payload.durationTicks());
        for (BlockPos pos : payload.positions()) {
            HIGHLIGHTS.put(pos, highlight);
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            HIGHLIGHTS.clear();
            level = null;
            return;
        }
        syncLevel(mc.level);
        ticks++;
        HIGHLIGHTS.values().removeIf(highlight -> ticks >= highlight.endTick);
    }

    /** Outlines belong to the dimension they were found in. */
    private static void syncLevel(ClientLevel current) {
        if (level != current) {
            HIGHLIGHTS.clear();
            level = current;
        }
    }
}
