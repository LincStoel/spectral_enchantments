package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.BranchFinding;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws Branch Finding's outlines: a soft green line over a fainter halo, both seen through the leaves (and anything
 * else) since the point is to find logs hidden inside a canopy. Meant as a hint rather than a beacon: they start
 * faint and fade steadily to nothing over the duration.
 */
final class BranchFindingRenderer {
    private static final float RED = 0.45f;
    private static final float GREEN = 0.85f;
    private static final float BLUE = 0.5f;
    /** Opacity right after a use; both fade linearly to 0. */
    private static final float CORE_ALPHA = 0.45f;
    private static final float HALO_ALPHA = 0.08f;

    private static final RenderType HALO = lines("branch_finding_halo", OptionalDouble.of(5.0));
    private static final RenderType CORE = lines("branch_finding_core", OptionalDouble.empty());

    private BranchFindingRenderer() {}

    /** Like vanilla's lines type, minus the depth test and depth writes. */
    private static RenderType lines(String name, OptionalDouble width) {
        return RenderType.create("spectral_enchantments_" + name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                1536, false, false, RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                        .setLineState(new RenderStateShard.LineStateShard(width))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BranchFindingClient.HIGHLIGHTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float now = BranchFindingClient.ticks + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer halo = buffers.getBuffer(HALO);
        drawAll(mc, poseStack, cam, now, halo, HALO_ALPHA);
        buffers.endBatch(HALO);
        VertexConsumer core = buffers.getBuffer(CORE);
        drawAll(mc, poseStack, cam, now, core, CORE_ALPHA);
        buffers.endBatch(CORE);
    }

    private static void drawAll(Minecraft mc, PoseStack poseStack, Vec3 cam, float now, VertexConsumer consumer, float alpha) {
        for (Map.Entry<BlockPos, BranchFindingClient.Highlight> entry : BranchFindingClient.HIGHLIGHTS.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = mc.level.getBlockState(pos);
            float fade = fade(entry.getValue(), now);
            // A log chopped down since the search no longer needs finding.
            if (fade <= 0f || !state.is(BranchFinding.TARGETS)) {
                continue;
            }
            AreaOutlineRenderer.renderShape(poseStack, consumer, state.getShape(mc.level, pos, CollisionContext.empty()),
                    pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, RED, GREEN, BLUE, alpha * fade);
        }
    }

    private static float fade(BranchFindingClient.Highlight highlight, float now) {
        float in = Mth.clamp((now - highlight.startTick()) / BranchFindingClient.FADE_IN_TICKS, 0f, 1f);
        float out = 1f - Mth.clamp((now - highlight.startTick()) / (highlight.endTick() - highlight.startTick()), 0f, 1f);
        return in * out;
    }
}
