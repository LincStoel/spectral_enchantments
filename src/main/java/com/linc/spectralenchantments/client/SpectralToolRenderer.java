package com.linc.spectralenchantments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
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
    private static final float SWING_PERIOD_TICKS = 9f;
    // Picks lead with a prong; shovels jab more steeply so the blade tip reaches the face.
    private static final float PICK_WIND_UP = 45f;
    private static final float PICK_STRIKE = -28f;
    private static final float SHOVEL_WIND_UP = 15f;
    private static final float SHOVEL_STRIKE = -62f;
    private static final Vector3f GRIP = new Vector3f(0f, -0.62f, 0.95f);
    private static final float HANDLE_TO_CENTER = 0.5f;
    private static final float TOOL_SCALE = 0.9f;
    private static final float MAX_ALPHA = 0.75f;
    // Ghosts right in front of the camera fade a little so they don't crowd the view (or bloom under shaders).
    private static final float NEAR_FADE_START = 2.5f;
    private static final float NEAR_FADE_END = 1.0f;
    private static final float NEAR_FADE_MIN = 0.3f;

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
            float windUp = session.shovel ? SHOVEL_WIND_UP : PICK_WIND_UP;
            float strike = session.shovel ? SHOVEL_STRIKE : PICK_STRIKE;

            for (BlockPos pos : session.extras) {
                float phase = Mth.frac((elapsed + phaseOffset(pos)) / SWING_PERIOD_TICKS);
                Vec3 normal = Vec3.atLowerCornerOf(session.face.getNormal());
                Vec3 faceCenter = Vec3.atCenterOf(pos).add(normal.scale(0.5));
                float near = nearFade(faceCenter.add(normal.scale(GRIP.z)).distanceTo(cam));
                float visibility = fade * near;
                int alpha = (int) (visibility * MAX_ALPHA * 255f);
                // Shaderpacks draw the glowing type opaque, so a ghost that is fading uses the lit type,
                // whose transparency they keep.
                RenderType type = shaderPack && visibility < 0.999f ? ghost.litRenderType : ghost.renderType;
                MultiBufferSource ghostBuffers = requested -> new GhostVertexConsumer(buffers.getBuffer(type), ghost, alpha);

                poseStack.pushPose();
                poseStack.translate(faceCenter.x - cam.x, faceCenter.y - cam.y, faceCenter.z - cam.z);
                poseStack.mulPose(frame);
                // After a break the ghosts drift back out of the hole as they dissolve.
                poseStack.translate(GRIP.x, GRIP.y + drift * 0.25f, GRIP.z + drift * 0.35f);
                poseStack.mulPose(Axis.XP.rotationDegrees(swingAngle(phase, windUp, strike)));
                poseStack.translate(0f, HANDLE_TO_CENTER, 0f);
                poseStack.mulPose(Axis.YP.rotationDegrees(90f)); // tool plane = swing plane, front prong toward the face
                poseStack.mulPose(Axis.ZP.rotationDegrees(45f)); // item sprites are diagonal; stand the handle upright
                poseStack.scale(TOOL_SCALE, TOOL_SCALE, TOOL_SCALE);
                mc.getItemRenderer().renderStatic(session.ghostTool, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, poseStack, ghostBuffers, mc.level, pos.hashCode());
                poseStack.popPose();
            }
            buffers.endBatch(ghost.renderType);
            buffers.endBatch(ghost.litRenderType);
        }
    }

    private static float fade(SpectralSessions.Session session, float now) {
        float in = Mth.clamp((now - session.startTick) / SpectralSessions.FADE_IN_TICKS, 0f, 1f);
        float out = session.active() ? 1f : 1f - Mth.clamp((now - session.endTick) / session.fadeTicks(), 0f, 1f);
        return in * out;
    }

    /** 1 at {@link #NEAR_FADE_START} blocks from the camera or more, easing down to {@link #NEAR_FADE_MIN} at {@link #NEAR_FADE_END}. */
    private static float nearFade(double distance) {
        float t = Mth.clamp((float) (distance - NEAR_FADE_END) / (NEAR_FADE_START - NEAR_FADE_END), 0f, 1f);
        return Mth.lerp(t * t * (3f - 2f * t), NEAR_FADE_MIN, 1f);
    }

    /** Lift slowly, strike fast, then rest on the block for a moment. */
    private static float swingAngle(float phase, float windUp, float strike) {
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
    private static Quaternionf faceFrame(Direction face, Entity miner) {
        Vector3f z = face.step();
        Vector3f y;
        if (face.getAxis() == Direction.Axis.Y) {
            // Floors and ceilings: swing along the miner's horizontal look direction.
            Direction look = miner != null ? miner.getDirection() : Direction.NORTH;
            y = look.step();
        } else {
            y = new Vector3f(0f, 1f, 0f);
        }
        Vector3f x = new Vector3f(y).cross(z);
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    /** Redirects the item model onto its ghost texture, white with the ghost's fade alpha. */
    private record GhostVertexConsumer(VertexConsumer delegate, GhostTextures.Ghost ghost, int alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int a) {
            delegate.setColor(255, 255, 255, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            float[] uv = ghost.remap(u, v);
            delegate.setUv(uv[0], uv[1]);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
