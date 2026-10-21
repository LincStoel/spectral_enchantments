package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.boots.Springy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Springy's ghost slime: while a player in Springy boots falls far enough to bounce, a small see-through slime
 * gathers out of slime flecks around their boots; it squashes and springs back each time they land and bounce, and
 * bursts in a splash of flecks once they land without bouncing or take off flying. Unlike the other ghosts it keeps a slime's own green unless a Quark rune on the boots tints it.
 * Drawn on the client only, for every player: the local player's from its own fall and bounce, others' from how far
 * they have dropped and when they touch down.
 *
 * <p>It also bounces the local player (Springy only decides that a landing bounces), and times their jumps: a jump
 * just before landing, or the tick after, bounces them almost all the way back up. Players are drawn sitting on their
 * slime ({@code HumanoidModelMixin}).
 */
public final class SpringyClient {
    private static final ResourceLocation SLIME_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");
    /** Ticks the slime takes to gather and fade in. */
    private static final int FORM_TICKS = 3;
    /** Ticks a slime can sit on the ground after a bounce before it counts as a landing without one. */
    private static final int GROUND_GRACE_TICKS = 3;
    /** Ticks it takes to fade as it bursts. */
    private static final int POP_TICKS = 2;
    /** A slime that never lands again bursts after this long. */
    private static final int MAX_TICKS = 600;
    /** Ticks another creative player must hold their height in the air to count as flying; a bounce's peak is one. */
    private static final int HOVER_TICKS = 3;
    private static final int GATHER_FLECKS = 12;
    private static final int BURST_FLECKS = 16;
    /** Between a small and a medium slime, as SlimeRenderer scales it: big enough to swallow the boots. */
    private static final float SIZE = 1.5f;

    static long ticks;

    private static final class Slime {
        final int entityId;
        final long startTick;
        long impactTick = -1;
        long popTick = -1;

        Slime(int entityId, long startTick) {
            this.entityId = entityId;
            this.startTick = startTick;
        }

        boolean popped() {
            return popTick >= 0;
        }
    }

    private static final Map<Integer, Slime> SLIMES = new HashMap<>();
    /** Other players in Springy boots who are off the ground: the highest they have been since. */
    private static final Map<Integer, Double> PEAKS = new HashMap<>();
    /** Other creative players off the ground: how many ticks in a row they have held their height. */
    private static final Map<Integer, Integer> HOVERS = new HashMap<>();

    // The local player: its last jump key press, and the bounce off its last landing (see onLocalLanding).
    private static boolean jumpWasDown;
    private static int jumpPressTick = Integer.MIN_VALUE / 2;
    private static int landingTick = Integer.MIN_VALUE / 2;
    @Nullable
    private static Vec3 pendingBounce;
    private static Vec3 timedBounce = Vec3.ZERO;
    private static boolean timed;

    @Nullable
    private static SlimeModel<Entity> inner;
    @Nullable
    private static SlimeModel<Entity> outer;

    private SpringyClient() {}

    /** Whether {@code entity} is drawn sitting on its slime: a player whose slime has formed and not burst. */
    public static boolean seated(LivingEntity entity) {
        if (!(entity instanceof Player) || SLIMES.isEmpty() || !SpectralClientConfig.springySeated()) {
            return false;
        }
        Slime slime = SLIMES.get(entity.getId());
        return slime != null && !slime.popped();
    }

    /** Resource reload: the slime models are baked again from the reloaded layers. */
    static void clearModels() {
        inner = null;
        outer = null;
    }

    /**
     * The local player lands with a bounce (see {@link Springy}), still falling: vanilla stops the fall right after,
     * so the bounce waits for the end of the tick. A jump just before counts as timed.
     */
    public static void onLocalLanding(Player player, float distance) {
        Vec3 impact = player.getDeltaMovement();
        timedBounce = Springy.bounce(impact, distance, SpectralConfig.springyPerfectRestitution());
        timed = player.tickCount - jumpPressTick <= SpectralConfig.springyPerfectWindowTicks();
        pendingBounce = timed ? timedBounce : Springy.bounce(impact, distance, SpectralConfig.springyRestitution());
        landingTick = player.tickCount;
        jumpPressTick = Integer.MIN_VALUE / 2;
    }

    /** The end of the landing tick: off it goes. */
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (pendingBounce == null || !player.level().isClientSide || player != Minecraft.getInstance().player) {
            return;
        }
        player.setDeltaMovement(pendingBounce);
        player.hasImpulse = true;
        pendingBounce = null;
        Slime slime = SLIMES.get(player.getId());
        if (slime == null || slime.popped()) {
            slime = new Slime(player.getId(), ticks - FORM_TICKS);
            SLIMES.put(player.getId(), slime);
        }
        slime.impactTick = ticks;
    }

    /**
     * Before the local player moves: notes jump presses, and the tick after a bounce keeps vanilla from jumping
     * (which would cut the bounce short) while a jump pressed right then still times it.
     */
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        Input input = event.getInput();
        boolean pressed = input.jumping && !jumpWasDown;
        jumpWasDown = input.jumping;
        if (pressed) {
            jumpPressTick = player.tickCount;
        }
        if (player.tickCount - landingTick == 1) {
            input.jumping = false;
            if (pressed && !timed) {
                timed = true;
                player.setDeltaMovement(timedBounce);
                player.hasImpulse = true;
            }
        }
    }

    static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            SLIMES.clear();
            PEAKS.clear();
            HOVERS.clear();
            return;
        }
        double minFall = SpectralConfig.springyMinFallDistance();
        for (AbstractClientPlayer player : level.players()) {
            tickPlayer(mc, level, player, minFall);
        }
        Iterator<Slime> it = SLIMES.values().iterator();
        while (it.hasNext()) {
            Slime slime = it.next();
            if (slime.popped() && ticks - slime.popTick > POP_TICKS || level.getEntity(slime.entityId) == null) {
                it.remove();
            }
        }
        PEAKS.keySet().removeIf(id -> level.getEntity(id) == null);
        HOVERS.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    private static void tickPlayer(Minecraft mc, ClientLevel level, AbstractClientPlayer player, double minFall) {
        int id = player.getId();
        Slime slime = SLIMES.get(id);
        if (player.isSpectator() || !Springy.has(player)) {
            PEAKS.remove(id);
            HOVERS.remove(id);
            if (slime != null && !slime.popped()) {
                pop(level, player, slime);
            }
            return;
        }
        boolean local = player == mc.player;
        // Taking off with an elytra or in creative flight bursts the slime, and none forms until they come down.
        if (player.isFallFlying() || flying(player, local)) {
            PEAKS.remove(id);
            if (slime != null && !slime.popped()) {
                pop(level, player, slime);
            }
            return;
        }
        boolean grounded = player.onGround() || player.isInFluidType() || player.onClimbable() || player.isFallFlying();
        double fall = 0;
        // Another player is taken to bounce when they touch down from high enough, unless sneaking. The local
        // player's bounces come from onPlayerTick.
        boolean bounced = false;
        if (local) {
            fall = player.fallDistance;
        } else {
            Double peak = PEAKS.get(id);
            if (!grounded) {
                double top = peak == null ? player.getY() : Math.max(peak, player.getY());
                PEAKS.put(id, top);
                fall = top - player.getY();
            } else if (peak != null) {
                PEAKS.remove(id);
                bounced = player.onGround() && !player.isShiftKeyDown() && peak - player.getY() > minFall;
            }
        }

        if (slime == null || slime.popped()) {
            if (!grounded && fall > minFall && slime == null) {
                slime = new Slime(id, ticks);
                SLIMES.put(id, slime);
                gather(level, player);
            }
            return;
        }
        if (bounced) {
            slime.impactTick = ticks;
        } else if (grounded) {
            // Still on the ground a moment after its last bounce, or never bounced: a landing without one.
            if (slime.impactTick < 0 || ticks - slime.impactTick > GROUND_GRACE_TICKS) {
                pop(level, player, slime);
            }
        } else if (ticks - Math.max(slime.startTick, slime.impactTick) > MAX_TICKS) {
            pop(level, player, slime);
        }
    }

    /**
     * Whether {@code player} is in creative flight. Only the local player's own abilities reach the client, so another
     * player counts as flying once they are in creative and hold their height in the air for a few ticks, and keeps
     * counting until they touch down (so flying down doesn't look like a fall).
     */
    private static boolean flying(AbstractClientPlayer player, boolean local) {
        if (local) {
            return player.getAbilities().flying;
        }
        int id = player.getId();
        if (!player.isCreative() || player.onGround() || player.isInFluidType()) {
            HOVERS.remove(id);
            return false;
        }
        int hover = HOVERS.getOrDefault(id, 0);
        if (hover >= HOVER_TICKS) {
            return true;
        }
        hover = Math.abs(player.getY() - player.yo) > 1.0E-3 ? 0 : hover + 1;
        HOVERS.put(id, hover);
        return hover >= HOVER_TICKS;
    }

    private static void gather(ClientLevel level, Player player) {
        int tint = tint(player);
        boolean rune = tint != GhostTint.WHITE;
        for (int i = 0; i < GATHER_FLECKS; i++) {
            double angle = Mth.TWO_PI * i / GATHER_FLECKS;
            double radius = 0.8 + player.getRandom().nextDouble() * 0.3;
            Vec3 from = player.position().add(Math.cos(angle) * radius, 0.2 + player.getRandom().nextDouble() * 0.8,
                    Math.sin(angle) * radius);
            Vec3 into = new Vec3((player.getRandom().nextDouble() - 0.5) * 0.5, player.getRandom().nextDouble() * 0.5,
                    (player.getRandom().nextDouble() - 0.5) * 0.5);
            Minecraft.getInstance().particleEngine.add(SlimeGhostParticle.gathering(level, from, tint, rune,
                    GhostItemDraw.MAX_ALPHA, player, into, FORM_TICKS + player.getRandom().nextInt(2)));
        }
    }

    private static void pop(ClientLevel level, Player player, Slime slime) {
        slime.popTick = ticks;
        int tint = tint(player);
        boolean rune = tint != GhostTint.WHITE;
        Vec3 center = player.position().add(0, 0.35, 0);
        for (int i = 0; i < BURST_FLECKS; i++) {
            double angle = Mth.TWO_PI * i / BURST_FLECKS + player.getRandom().nextDouble() * 0.3;
            double speed = 0.12 + player.getRandom().nextDouble() * 0.1;
            Vec3 velocity = new Vec3(Math.cos(angle) * speed, 0.1 + player.getRandom().nextDouble() * 0.15,
                    Math.sin(angle) * speed);
            Minecraft.getInstance().particleEngine.add(SlimeGhostParticle.bursting(level, center, velocity, tint, rune,
                    GhostItemDraw.MAX_ALPHA));
        }
    }

    /** White (the slime's own colours) unless a Quark rune on the boots tints it. */
    private static int tint(Player player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        return GhostTint.of(boots, player.tickCount);
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SLIMES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (inner == null || outer == null) {
            inner = new SlimeModel<>(mc.getEntityModels().bakeLayer(ModelLayers.SLIME));
            outer = new SlimeModel<>(mc.getEntityModels().bakeLayer(ModelLayers.SLIME_OUTER));
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = ticks + partialTick;
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean shaderPack = IrisCompat.shaderPackInUse();

        for (Slime slime : SLIMES.values()) {
            if (!(mc.level.getEntity(slime.entityId) instanceof Player player)) {
                continue;
            }
            float appear = Mth.clamp((now - slime.startTick) / FORM_TICKS, 0f, 1f);
            float fade = slime.popped() ? 1f - Mth.clamp((now - slime.popTick) / POP_TICKS, 0f, 1f) : 1f;
            Vec3 feet = player.getPosition(partialTick);
            float visibility = appear * fade * GhostItemDraw.nearFade(feet.add(0, 0.35, 0).distanceTo(cam));
            if (visibility <= 0.01f) {
                continue;
            }
            int tint = tint(player);
            boolean rune = tint != GhostTint.WHITE;
            if (shaderPack) {
                tint = scale(tint, SpectralClientConfig.shaderBrightness());
            }
            int color = FastColor.ARGB32.color((int) (visibility * GhostItemDraw.MAX_ALPHA * 255f), tint);
            ResourceLocation texture = rune ? GhostSkins.get(SLIME_TEXTURE) : SLIME_TEXTURE;
            RenderType type = shaderPack ? RenderType.entityTranslucent(texture) : RenderType.entityTranslucentEmissive(texture);

            // SlimeRenderer's squash and stretch: below 0 flattens, above 0 stretches tall.
            float squish = squish(slime, now);
            float s = squish / (SIZE * 0.5f + 1f);
            float wide = 1f / (s + 1f);
            // Gathering: grows in from the flecks.
            float grow = Mth.lerp(appear, 0.4f, 1f);

            poseStack.pushPose();
            poseStack.translate(feet.x - cam.x, feet.y - cam.y, feet.z - cam.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)));
            poseStack.scale(-1f, -1f, 1f);
            poseStack.scale(wide * SIZE * grow, SIZE / wide * grow, wide * SIZE * grow);
            poseStack.translate(0f, -1.501f, 0f);
            inner.renderToBuffer(poseStack, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
            outer.renderToBuffer(poseStack, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
            poseStack.popPose();
            buffers.endBatch(type);
        }
    }

    /** A gentle wobble in the air; on each landing a damped jiggle, squashed flat and springing up tall as it bounces. */
    private static float squish(Slime slime, float now) {
        float wobble = 0.1f * Mth.sin((now - slime.startTick) * 0.6f);
        if (slime.impactTick < 0) {
            return wobble;
        }
        float t = Math.max(0f, now - slime.impactTick);
        return wobble - 0.7f * (float) Math.exp(-t / 3f) * Mth.cos(t * 1.4f);
    }

    private static int scale(int rgb, float factor) {
        int r = Math.round((rgb >> 16 & 0xFF) * factor);
        int g = Math.round((rgb >> 8 & 0xFF) * factor);
        int b = Math.round((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }
}
