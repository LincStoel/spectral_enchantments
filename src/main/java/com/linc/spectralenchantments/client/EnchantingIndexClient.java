package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.index.EnchantingIndex;
import com.linc.spectralenchantments.network.EnchantingIndexPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Opens the Enchanting Index's page, and keeps the books picked from it glowing until they're gone or time is up. */
public final class EnchantingIndexClient {
    static final int FADE_IN_TICKS = 4;
    static final int FADE_OUT_TICKS = 20;

    /** A book glowing in {@code slot} of the holder at {@code pos}, from {@code startTick} to {@code endTick}. */
    record Glow(BlockPos pos, int slot, long startTick, long endTick) {}

    static final List<Glow> GLOWS = new ArrayList<>();
    static long ticks;
    private static ClientLevel level;

    private EnchantingIndexClient() {}

    public static void handle(EnchantingIndexPayload payload) {
        Minecraft.getInstance().setScreen(new EnchantingIndexScreen(payload.entries(), payload.truncated()));
    }

    /** Makes the books at {@code locations} glow, in place of any picked before. */
    static void mark(List<EnchantingIndex.Location> locations) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        syncLevel(mc.level);
        GLOWS.clear();
        long end = ticks + SpectralClientConfig.indexGlowSeconds() * 20L;
        for (EnchantingIndex.Location location : locations) {
            GLOWS.add(new Glow(location.pos(), location.slot(), ticks, end));
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            GLOWS.clear();
            level = null;
            return;
        }
        syncLevel(mc.level);
        ticks++;
        if (!GLOWS.isEmpty()) {
            // A book taken off its shelf needs no more finding.
            GLOWS.removeIf(glow -> ticks >= glow.endTick()
                    || mc.level.isLoaded(glow.pos()) && !BookSlotShapes.hasBook(mc.level.getBlockState(glow.pos()), glow.slot()));
        }
    }

    /** Glows belong to the dimension they were picked in. */
    private static void syncLevel(ClientLevel current) {
        if (level != current) {
            GLOWS.clear();
            level = current;
        }
    }
}
