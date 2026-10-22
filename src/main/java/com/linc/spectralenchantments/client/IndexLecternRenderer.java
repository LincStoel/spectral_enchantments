package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.index.IndexLectern;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Draws a lectern holding the Enchanting Index as an enchanting table's book floating just above it, turning toward
 * and opening for a player who comes near, as the table's does. Any other lectern is drawn as vanilla draws it. The
 * books' animation is kept here, since lecterns don't tick.
 */
final class IndexLecternRenderer implements BlockEntityRenderer<LecternBlockEntity> {
    /** Where the book hovers, above the lectern's base; the table's is 0.75, its top. */
    private static final float HEIGHT = 1.0f;
    /** How near a player brings the book open, as at the table. */
    private static final double OPEN_RANGE = 3.0;
    private static final RandomSource RANDOM = RandomSource.create();

    /** Each drawn index lectern's book. Weak, so lecterns unloaded with their chunk drop out. Client thread. */
    private static final Map<LecternBlockEntity, Book> BOOKS = new WeakHashMap<>();

    /** The enchanting table's book animation (EnchantingTableBlockEntity's fields). */
    private static final class Book {
        int time;
        float flip;
        float oFlip;
        float flipT;
        float flipA;
        float open;
        float oOpen;
        float rot;
        float oRot;
        float tRot;
    }

    private final LecternRenderer vanilla;
    private final BookModel bookModel;

    IndexLecternRenderer(BlockEntityRendererProvider.Context context) {
        vanilla = new LecternRenderer(context);
        bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(LecternBlockEntity lectern, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!IndexLectern.holdsIndex(lectern)) {
            vanilla.render(lectern, partialTick, poseStack, buffers, packedLight, packedOverlay);
            return;
        }
        Book book = BOOKS.computeIfAbsent(lectern, key -> new Book());
        // As EnchantTableRenderer draws it, raised to the lectern's top.
        poseStack.pushPose();
        poseStack.translate(0.5f, HEIGHT, 0.5f);
        float time = book.time + partialTick;
        poseStack.translate(0f, 0.1f + Mth.sin(time * 0.1f) * 0.01f, 0f);
        float turn = wrap(book.rot - book.oRot);
        poseStack.mulPose(Axis.YP.rotation(-(book.oRot + turn * partialTick)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(80f));
        float flip = Mth.lerp(partialTick, book.oFlip, book.flip);
        float left = Mth.frac(flip + 0.25f) * 1.6f - 0.3f;
        float right = Mth.frac(flip + 0.75f) * 1.6f - 0.3f;
        bookModel.setupAnim(time, Mth.clamp(left, 0f, 1f), Mth.clamp(right, 0f, 1f),
                Mth.lerp(partialTick, book.oOpen, book.open));
        VertexConsumer consumer = EnchantTableRenderer.BOOK_LOCATION.buffer(buffers, RenderType::entitySolid);
        bookModel.render(poseStack, consumer, packedLight, packedOverlay, -1);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(LecternBlockEntity lectern) {
        BlockPos pos = lectern.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
    }

    /** Moves each book on, as EnchantingTableBlockEntity.bookAnimationTick does, dropping those no longer shown. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BOOKS.clear();
            return;
        }
        for (Iterator<Map.Entry<LecternBlockEntity, Book>> it = BOOKS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LecternBlockEntity, Book> entry = it.next();
            LecternBlockEntity lectern = entry.getKey();
            if (lectern == null || lectern.isRemoved() || lectern.getLevel() != mc.level || !IndexLectern.holdsIndex(lectern)) {
                it.remove();
            } else {
                tick(lectern, entry.getValue());
            }
        }
    }

    private static void tick(LecternBlockEntity lectern, Book book) {
        BlockPos pos = lectern.getBlockPos();
        book.oOpen = book.open;
        book.oRot = book.rot;
        Player player = lectern.getLevel().getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, OPEN_RANGE, false);
        if (player != null) {
            book.tRot = (float) Mth.atan2(player.getZ() - (pos.getZ() + 0.5), player.getX() - (pos.getX() + 0.5));
            book.open += 0.1f;
            if (book.open < 0.5f || RANDOM.nextInt(40) == 0) {
                float was = book.flipT;
                do {
                    book.flipT += RANDOM.nextInt(4) - RANDOM.nextInt(4);
                } while (was == book.flipT);
            }
        } else {
            book.tRot += 0.02f;
            book.open -= 0.1f;
        }
        book.rot = wrap(book.rot);
        book.tRot = wrap(book.tRot);
        book.rot += wrap(book.tRot - book.rot) * 0.4f;
        book.open = Mth.clamp(book.open, 0f, 1f);
        book.time++;
        book.oFlip = book.flip;
        float pull = Mth.clamp((book.flipT - book.flip) * 0.4f, -0.2f, 0.2f);
        book.flipA += (pull - book.flipA) * 0.9f;
        book.flip += book.flipA;
    }

    /** {@code angle} brought into [-π, π). */
    private static float wrap(float angle) {
        while (angle >= Mth.PI) {
            angle -= Mth.TWO_PI;
        }
        while (angle < -Mth.PI) {
            angle += Mth.TWO_PI;
        }
        return angle;
    }
}
