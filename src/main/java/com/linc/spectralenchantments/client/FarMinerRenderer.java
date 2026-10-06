package com.linc.spectralenchantments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws each Far Miner's spectral tool at its locked block: poised and bobbing while the lock waits, swinging
 * while the block is mined, drifting back as it dissolves. The local player's locked block is also outlined,
 * since vanilla only outlines blocks within reach.
 */
final class FarMinerRenderer {
    private static final float HOVER_AMPLITUDE = 6f;
    private static final float HOVER_SPEED = 0.15f;

    private FarMinerRenderer() {}

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FarMinerClient.all().isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float now = FarMinerClient.ticks + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean shaderPack = IrisCompat.shaderPackInUse();

        for (FarMinerClient.Ghost ghost : FarMinerClient.all()) {
            if (ghost.local && ghost.active()) {
                outline(mc, event, poseStack, cam, buffers, ghost);
            }
            float fade = fade(ghost, now);
            GhostTextures.Ghost texture = GhostTextures.get(ghost.ghostTool);
            if (fade <= 0f || texture == null) {
                continue;
            }
            float angle = ghost.mining()
                    ? SpectralToolRenderer.swingAngle(Mth.frac((now - ghost.miningTick) / SpectralToolRenderer.SWING_PERIOD_TICKS), ghost.shovel)
                    : SpectralToolRenderer.windUp(ghost.shovel) + HOVER_AMPLITUDE * Mth.sin((now - ghost.startTick) * HOVER_SPEED);
            float drift = ghost.active() ? 0f : (now - ghost.endTick) / FarMinerClient.FADE_OUT_TICKS;
            SpectralToolRenderer.drawGhost(mc, poseStack, cam, ghost.pos, ghost.face,
                    SpectralToolRenderer.faceFrame(ghost.face, ghost.look), angle, drift, fade, texture, ghost.ghostTool,
                    GhostTint.of(ghost.ghostTool, now), shaderPack, buffers);
            GhostItemDraw.endBatch(buffers, texture);
        }
    }

    private static void outline(Minecraft mc, RenderLevelStageEvent event, PoseStack poseStack, Vec3 cam,
                                MultiBufferSource.BufferSource buffers, FarMinerClient.Ghost ghost) {
        VoxelShape shape = mc.level.getBlockState(ghost.pos).getShape(mc.level, ghost.pos, CollisionContext.of(event.getCamera().getEntity()));
        AreaOutlineRenderer.renderShape(poseStack, buffers.getBuffer(RenderType.lines()), shape,
                ghost.pos.getX() - cam.x, ghost.pos.getY() - cam.y, ghost.pos.getZ() - cam.z);
        buffers.endBatch(RenderType.lines());
    }

    private static float fade(FarMinerClient.Ghost ghost, float now) {
        float in = Mth.clamp((now - ghost.startTick) / FarMinerClient.FADE_IN_TICKS, 0f, 1f);
        float out = ghost.active() ? 1f : 1f - Mth.clamp((now - ghost.endTick) / FarMinerClient.FADE_OUT_TICKS, 0f, 1f);
        return in * out;
    }
}
