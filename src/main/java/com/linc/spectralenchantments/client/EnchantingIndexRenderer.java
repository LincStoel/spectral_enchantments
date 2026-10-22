package com.linc.spectralenchantments.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the Enchanting Index's glow on each picked book: a softly pulsing amber pane over the book with a brighter
 * edge, seen through walls unless the client config says otherwise. It fades in, and out over its last second.
 */
final class EnchantingIndexRenderer {
    private static final float RED = 1.0f;
    private static final float GREEN = 0.78f;
    private static final float BLUE = 0.35f;
    private static final float FILL_ALPHA = 0.35f;
    private static final float EDGE_ALPHA = 0.9f;

    private static final RenderType FILL_THROUGH = fill("enchanting_index_fill_through", RenderStateShard.NO_DEPTH_TEST, ThroughWalls.SHARD);
    private static final RenderType FILL = fill("enchanting_index_fill", RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.NO_LAYERING);
    private static final RenderType EDGE_THROUGH = lines("enchanting_index_edge_through", RenderStateShard.NO_DEPTH_TEST, ThroughWalls.SHARD);
    private static final RenderType EDGE = lines("enchanting_index_edge", RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.NO_LAYERING);

    private EnchantingIndexRenderer() {}

    private static RenderType fill(String name, RenderStateShard.DepthTestStateShard depth, RenderStateShard.LayeringStateShard layering) {
        return RenderType.create("spectral_enchantments_" + name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                1536, false, true, RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setDepthTestState(depth)
                        .setLayeringState(layering)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    /** Like vanilla's lines type, without depth writes and with the given depth test. */
    private static RenderType lines(String name, RenderStateShard.DepthTestStateShard depth, RenderStateShard.LayeringStateShard layering) {
        return RenderType.create("spectral_enchantments_" + name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                1536, false, false, RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                        .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setDepthTestState(depth)
                        .setLayeringState(layering)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || EnchantingIndexClient.GLOWS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        boolean through = SpectralClientConfig.indexGlowThroughWalls();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float now = EnchantingIndexClient.ticks + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // A slow breath, so the glow catches the eye without flashing.
        float pulse = 0.7f + 0.3f * Mth.sin(now * 0.2f);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        RenderType fillType = through ? FILL_THROUGH : FILL;
        VertexConsumer fill = buffers.getBuffer(fillType);
        for (EnchantingIndexClient.Glow glow : EnchantingIndexClient.GLOWS) {
            float alpha = FILL_ALPHA * pulse * fade(glow, now);
            if (alpha > 0f) {
                BookSlotShapes.Shape shape = shape(mc, glow);
                place(poseStack, glow, cam, shape);
                fillBox(poseStack.last(), fill, shape.box().move(-0.5, 0, -0.5), alpha);
                poseStack.popPose();
            }
        }
        buffers.endBatch(fillType);

        RenderType edgeType = through ? EDGE_THROUGH : EDGE;
        VertexConsumer edge = buffers.getBuffer(edgeType);
        for (EnchantingIndexClient.Glow glow : EnchantingIndexClient.GLOWS) {
            float alpha = EDGE_ALPHA * fade(glow, now);
            if (alpha > 0f) {
                BookSlotShapes.Shape shape = shape(mc, glow);
                place(poseStack, glow, cam, shape);
                AreaOutlineRenderer.renderShape(poseStack, edge, Shapes.create(shape.box()), -0.5, 0, -0.5, RED, GREEN, BLUE, alpha);
                poseStack.popPose();
            }
        }
        buffers.endBatch(edgeType);
    }

    private static BookSlotShapes.Shape shape(Minecraft mc, EnchantingIndexClient.Glow glow) {
        return BookSlotShapes.shape(mc.level.getBlockState(glow.pos()), mc.level.getBlockEntity(glow.pos()), glow.slot());
    }

    /** Pushes a pose at the middle of the glow's block (at its base), turned by the shape's yaw. */
    private static void place(PoseStack poseStack, EnchantingIndexClient.Glow glow, Vec3 cam, BookSlotShapes.Shape shape) {
        poseStack.pushPose();
        poseStack.translate(glow.pos().getX() + 0.5 - cam.x, glow.pos().getY() - cam.y, glow.pos().getZ() + 0.5 - cam.z);
        if (shape.yaw() != 0f) {
            poseStack.mulPose(Axis.YP.rotation(shape.yaw()));
        }
    }

    private static float fade(EnchantingIndexClient.Glow glow, float now) {
        float in = Mth.clamp((now - glow.startTick()) / EnchantingIndexClient.FADE_IN_TICKS, 0f, 1f);
        float out = Mth.clamp((glow.endTick() - now) / EnchantingIndexClient.FADE_OUT_TICKS, 0f, 1f);
        return in * out;
    }

    private static void fillBox(PoseStack.Pose pose, VertexConsumer consumer, AABB box, float alpha) {
        float x0 = (float) box.minX;
        float y0 = (float) box.minY;
        float z0 = (float) box.minZ;
        float x1 = (float) box.maxX;
        float y1 = (float) box.maxY;
        float z1 = (float) box.maxZ;
        quad(pose, consumer, alpha, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(pose, consumer, alpha, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, consumer, alpha, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, consumer, alpha, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, consumer, alpha, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, consumer, alpha, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer consumer, float alpha,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        consumer.addVertex(pose, ax, ay, az).setColor(RED, GREEN, BLUE, alpha);
        consumer.addVertex(pose, bx, by, bz).setColor(RED, GREEN, BLUE, alpha);
        consumer.addVertex(pose, cx, cy, cz).setColor(RED, GREEN, BLUE, alpha);
        consumer.addVertex(pose, dx, dy, dz).setColor(RED, GREEN, BLUE, alpha);
    }
}
