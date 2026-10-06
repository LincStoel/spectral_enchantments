package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.AreaHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** Outlines every block in the area exactly like vanilla outlines the targeted block. */
final class AreaOutlineRenderer {
    private AreaOutlineRenderer() {}

    static void render(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        BlockHitResult hit = event.getTarget();
        List<BlockPos> extras = AreaHelper.getExtraBlocks(mc.player, hit.getBlockPos(), hit.getDirection());
        if (extras.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        CollisionContext context = CollisionContext.of(event.getCamera().getEntity());
        for (BlockPos pos : extras) {
            VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos, context);
            renderShape(event.getPoseStack(), lines, shape, pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        }
    }

    /** Same as the private LevelRenderer#renderShape, with vanilla's outline color (black, 40% alpha). */
    private static void renderShape(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape, double x, double y, double z) {
        PoseStack.Pose pose = poseStack.last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length;
            dy /= length;
            dz /= length;
            consumer.addVertex(pose, (float) (x1 + x), (float) (y1 + y), (float) (z1 + z)).setColor(0f, 0f, 0f, 0.4f).setNormal(pose, dx, dy, dz);
            consumer.addVertex(pose, (float) (x2 + x), (float) (y2 + y), (float) (z2 + z)).setColor(0f, 0f, 0f, 0.4f).setNormal(pose, dx, dy, dz);
        });
    }
}
