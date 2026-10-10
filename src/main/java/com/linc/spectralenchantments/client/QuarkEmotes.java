package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.doppelganger.PlayerGhostEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Optional Quark integration, by reflection so Quark stays a soft dependency: a Doppelganger's double plays Quark's
 * emotes.
 *
 * <p>Quark only animates real players (keyed by name, on the player renderer's own model), so the emote is run here
 * on the double's model instead: {@code EmoteDescriptor.instantiate} against a stand-in player that is never added
 * to the level (Quark reads its height, and plays the emote's sounds where it stands), then {@code update()} after
 * each {@code setupAnim} and {@code rotateAndOffset} before drawing, as Quark does for players. Each new emote seed the
 * server sends picks the emote, so every client plays the same one. Any failure turns this off and the double just
 * idles.
 */
final class QuarkEmotes {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private static Field emoteMap;
    private static Method getTier;
    private static Method instantiate;
    private static Method startAllTimelines;
    private static Method update;
    private static Method rotateAndOffset;
    private static Method isDone;
    private static boolean enabled;

    private static final Map<PlayerGhostEntity, Playing> PLAYING = new WeakHashMap<>();

    static {
        if (ModList.get().isLoaded("quark")) {
            try {
                Class<?> handler = Class.forName("org.violetmoon.quark.content.tweaks.client.emote.EmoteHandler");
                Class<?> descriptor = Class.forName("org.violetmoon.quark.content.tweaks.client.emote.EmoteDescriptor");
                Class<?> base = Class.forName("org.violetmoon.quark.content.tweaks.client.emote.EmoteBase");
                emoteMap = handler.getField("emoteMap");
                getTier = descriptor.getMethod("getTier");
                instantiate = descriptor.getMethod("instantiate", Player.class, HumanoidModel.class, HumanoidModel.class, HumanoidModel.class);
                startAllTimelines = base.getMethod("startAllTimelines");
                update = base.getMethod("update");
                rotateAndOffset = base.getMethod("rotateAndOffset", PoseStack.class);
                isDone = base.getMethod("isDone");
                enabled = true;
            } catch (ReflectiveOperationException | LinkageError e) {
                LOGGER.warn("Couldn't hook Quark's emotes; Doppelganger doubles won't emote", e);
            }
        }
    }

    private QuarkEmotes() {}

    /** The double's running emote and the stand-in player it runs against. */
    private static final class Playing {
        final RemotePlayer puppet;
        int seed;
        @Nullable
        Object emote;
        @Nullable
        HumanoidModel<?> model;

        Playing(RemotePlayer puppet) {
            this.puppet = puppet;
        }
    }

    /**
     * Poses {@code model} (already set up for the idle animation) for the double's current emote, starting a new one
     * if the server has picked one. Returns the emote to pass to {@link #transform}, or null when there is none.
     */
    @Nullable
    static Object animate(PlayerGhostEntity ghost, HumanoidModel<?> model, HumanoidModel<?> armor, HumanoidModel<?> legs) {
        int seed = ghost.emoteSeed();
        if (!enabled || (seed == 0 && !PLAYING.containsKey(ghost)) || !(ghost.level() instanceof ClientLevel level)) {
            return null;
        }
        try {
            Playing playing = PLAYING.get(ghost);
            if (playing == null) {
                playing = new Playing(new RemotePlayer(level, new GameProfile(UUID.randomUUID(), "doppelganger")));
                PLAYING.put(ghost, playing);
            }
            playing.puppet.setPos(ghost.getX(), ghost.getY(), ghost.getZ());
            if (seed != playing.seed) {
                playing.seed = seed;
                playing.emote = start(seed, playing.puppet, model, armor, legs);
                playing.model = model;
            }
            // Another skin model (the skin loaded as slim) can't be driven by an emote built for the first.
            if (playing.emote == null || playing.model != model) {
                playing.emote = null;
                return null;
            }
            update.invoke(playing.emote);
            if ((boolean) isDone.invoke(playing.emote)) {
                playing.emote = null;
            }
            return playing.emote;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            disable(e);
            return null;
        }
    }

    /** Applies the emote's whole-body movement (spins, hops) at the double's feet, before its own transforms. */
    static void transform(Object emote, PoseStack poseStack) {
        try {
            rotateAndOffset.invoke(emote, poseStack);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            disable(e);
        }
    }

    @Nullable
    private static Object start(int seed, Player puppet, HumanoidModel<?> model, HumanoidModel<?> armor, HumanoidModel<?> legs)
            throws ReflectiveOperationException {
        Map<?, ?> emotes = (Map<?, ?>) emoteMap.get(null);
        List<String> names = new ArrayList<>();
        for (Map.Entry<?, ?> entry : emotes.entrySet()) {
            // Tier 0: the emotes anyone can use, not Quark's supporter ones.
            if (entry.getKey() instanceof String name && (int) getTier.invoke(entry.getValue()) == 0) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        names.sort(null);
        String name = names.get(Math.floorMod(seed, names.size()));
        LOGGER.debug("Doppelganger double plays {}", name);
        Object descriptor = emotes.get(name);
        Object emote = instantiate.invoke(descriptor, puppet, model, armor, legs);
        startAllTimelines.invoke(emote);
        return emote;
    }

    private static void disable(Throwable e) {
        enabled = false;
        PLAYING.clear();
        LOGGER.warn("Quark's emotes failed on a Doppelganger double; turning them off for doubles", e);
    }
}
