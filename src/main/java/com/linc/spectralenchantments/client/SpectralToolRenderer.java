package com.linc.spectralenchantments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a ghostly copy of the miner's tool in front of every extra block, swinging at it.
 *
 * <p>Each ghost lives in a "face frame": +Z points out of the mined face, +Y is "up" along the face.
 * The tool is held by an invisible hand at {@link #GRIP} and swings about the X axis so its head
 * strikes the face.
 */
final class SpectralToolRenderer {
    static final float SWING_PERIOD_TICKS = 9f;
    // Picks lead with a prong; shovels jab more steeply so the blade tip reaches the face.
    private static final float PICK_WIND_UP = 45f;
    private static final float PICK_STRIKE = -28f;
    private static final float SHOVEL_WIND_UP = 15f;
    private static final float SHOVEL_STRIKE = -62f;
    private static final Vector3f GRIP = new Vector3f(0f, -0.62f, 0.95f);
    private static final float HANDLE_TO_CENTER = 0.5f;
    private static final float TOOL_SCALE = 0.9f;

    private SpectralToolRenderer() {}

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SpectralSessions.all().isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float now = SpectralSessions.ticks + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean shaderPack = IrisCompat.shaderPackInUse();

        for (SpectralSessions.Session session : SpectralSessions.all()) {
            float fade = fade(session, now);
            GhostTextures.Ghost ghost = GhostTextures.get(session.ghostTool);
            if (fade <= 0f || ghost == null) {
                continue;
            }
            Quaternionf frame = faceFrame(session.face, mc.level.getEntity(session.entityId));
            float elapsed = now - session.startTick;
            float drift = session.active() ? 0f : (now - session.endTick) / session.fadeTicks();
            int tint = GhostTint.of(session.ghostTool, now);

            for (BlockPos pos : session.extras) {
                float phase = Mth.frac((elapsed + phaseOffset(pos)) / SWING_PERIOD_TICKS);
                drawGhost(mc, poseStack, cam, pos, session.face, frame, swingAngle(phase, session.shovel), drift, fade,
                        ghost, session.ghostTool, tint, shaderPack, buffers);
            }
            GhostItemDraw.endBatch(buffers, ghost);
        }
    }

    /**
     * Draws one ghost of {@code tool} in front of {@code face} of {@code pos}, swung to {@code angle} degrees.
     * {@code drift} (0-1) moves it back out of the face as it dissolves after a break.
     */
    static void drawGhost(Minecraft mc, PoseStack poseStack, Vec3 cam, BlockPos pos, Direction face, Quaternionf frame,
                          float angle, float drift, float visibility, GhostTextures.Ghost ghost, ItemStack tool, int tint,
                          boolean shaderPack, MultiBufferSource.BufferSource buffers) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 faceCenter = Vec3.atCenterOf(pos).add(normal.scale(0.5));
        float near = GhostItemDraw.nearFade(faceCenter.add(normal.scale(GRIP.z)).distanceTo(cam));

        poseStack.pushPose();
        poseStack.translate(faceCenter.x - cam.x, faceCenter.y - cam.y, faceCenter.z - cam.z);
        poseStack.mulPose(frame);
        poseStack.translate(GRIP.x, GRIP.y + drift * 0.25f, GRIP.z + drift * 0.35f);
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        poseStack.translate(0f, HANDLE_TO_CENTER, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(90f)); // tool plane = swing plane, front prong toward the face
        poseStack.mulPose(Axis.ZP.rotationDegrees(45f)); // item sprites are diagonal; stand the handle upright
        poseStack.scale(TOOL_SCALE, TOOL_SCALE, TOOL_SCALE);
        GhostItemDraw.draw(mc, ghost, tool, visibility * near, tint, shaderPack, ItemDisplayContext.NONE,
                poseStack, buffers, pos.hashCode());
        poseStack.popPose();
    }

    /** The raised (wound-up) angle a tool rests at between strikes. */
    static float windUp(boolean shovel) {
        return shovel ? SHOVEL_WIND_UP : PICK_WIND_UP;
    }

    private static float fade(SpectralSessions.Session session, float now) {
        float in = Mth.clamp((now - session.startTick) / SpectralSessions.FADE_IN_TICKS, 0f, 1f);
        float out = session.active() ? 1f : 1f - Mth.clamp((now - session.endTick) / session.fadeTicks(), 0f, 1f);
        return in * out;
    }

    /** Lift slowly, strike fast, then rest on the block for a moment. */
    static float swingAngle(float phase, boolean shovel) {
        float windUp = windUp(shovel);
        float strike = shovel ? SHOVEL_STRIKE : PICK_STRIKE;
        if (phase < 0.55f) {
            float t = phase / 0.55f;
            return Mth.lerp(1f - (1f - t) * (1f - t), strike, windUp);
        }
        if (phase < 0.75f) {
            float t = (phase - 0.55f) / 0.2f;
            return Mth.lerp(t * t, windUp, strike);
        }
        float t = (phase - 0.75f) / 0.25f;
        return strike + 4f * Mth.sin(t * Mth.PI); // small recoil off the block
    }

    /** Deterministic per-block offset so neighbouring ghosts don't swing in perfect lockstep. */
    private static float phaseOffset(BlockPos pos) {
        return (Mth.murmurHash3Mixer(pos.hashCode()) & 0xFF) / 255f * SWING_PERIOD_TICKS * 0.35f;
    }

    /** Rotation taking face-frame axes into world axes. */
    static Quaternionf faceFrame(Direction face, Entity miner) {
        return faceFrame(face, miner != null ? miner.getDirection() : Direction.NORTH);
    }

    /** Rotation taking face-frame axes into world axes; on floors and ceilings the swing runs along {@code look}. */
    static Quaternionf faceFrame(Direction face, Direction look) {
        Vector3f z = face.step();
        Vector3f y;
        if (face.getAxis() == Direction.Axis.Y) {
            y = look.step();
        } else {
            y = new Vector3f(0f, 1f, 0f);
        }
        Vector3f x = new Vector3f(y).cross(z);
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }
}
