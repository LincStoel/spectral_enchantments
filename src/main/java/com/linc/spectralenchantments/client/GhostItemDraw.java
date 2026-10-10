package com.linc.spectralenchantments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws an item model as a ghost: its {@link GhostTextures} copy, tinted (white by default), at a given visibility. */
final class GhostItemDraw {
    static final float MAX_ALPHA = 0.75f;
    // Ghosts right in front of the camera fade a little so they don't crowd the view (or bloom under shaders).
    private static final float NEAR_FADE_START = 2.5f;
    private static final float NEAR_FADE_END = 1.0f;
    private static final float NEAR_FADE_MIN = 0.3f;

    private GhostItemDraw() {}

    /** Renders {@code stack} white at the current pose. {@code visibility} is 0-1, scaled by {@link #MAX_ALPHA}. */
    static void draw(Minecraft mc, GhostTextures.Ghost ghost, ItemStack stack, float visibility, boolean shaderPack,
                     PoseStack poseStack, MultiBufferSource.BufferSource buffers, int seed) {
        draw(mc, ghost, stack, visibility, GhostTint.WHITE, shaderPack, ItemDisplayContext.NONE, poseStack, buffers, seed);
    }

    /** Renders {@code stack} in {@code tint} (RGB) at the current pose, as it would be shown in {@code context}. */
    static void draw(Minecraft mc, GhostTextures.Ghost ghost, ItemStack stack, float visibility, int tint, boolean shaderPack,
                     ItemDisplayContext context, PoseStack poseStack, MultiBufferSource.BufferSource buffers, int seed) {
        draw(mc, ghost, stack, visibility, tint, shaderPack, context, false, poseStack, buffers, seed);
    }

    /** As above; {@code leftHand} mirrors a hand context's transform for an item held in the left hand. */
    static void draw(Minecraft mc, GhostTextures.Ghost ghost, ItemStack stack, float visibility, int tint, boolean shaderPack,
                     ItemDisplayContext context, boolean leftHand, PoseStack poseStack, MultiBufferSource.BufferSource buffers, int seed) {
        int alpha = (int) (Mth.clamp(visibility, 0f, 1f) * MAX_ALPHA * 255f);
        // Shaderpacks light (and often bloom) the ghost far brighter than vanilla does; dim it to match.
        int color = shaderPack && context != ItemDisplayContext.GUI ? scale(tint, SpectralClientConfig.shaderBrightness()) : tint;
        // Shaderpacks draw the glowing type opaque, so a ghost that is fading uses the lit type,
        // whose transparency they keep.
        RenderType type = context == ItemDisplayContext.GUI ? ghost.guiRenderType
                : shaderPack && visibility < 0.999f ? ghost.litRenderType : ghost.renderType;
        MultiBufferSource ghostBuffers = requested -> new GhostVertexConsumer(buffers.getBuffer(type), ghost, color, alpha);
        mc.getItemRenderer().renderStatic(null, stack, context, leftHand, poseStack, ghostBuffers, mc.level,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, seed);
    }

    private static int scale(int rgb, float factor) {
        int r = Math.round((rgb >> 16 & 0xFF) * factor);
        int g = Math.round((rgb >> 8 & 0xFF) * factor);
        int b = Math.round((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }

    static void endBatch(MultiBufferSource.BufferSource buffers, GhostTextures.Ghost ghost) {
        buffers.endBatch(ghost.renderType);
        buffers.endBatch(ghost.litRenderType);
        buffers.endBatch(ghost.guiRenderType);
    }

    /** 1 at {@link #NEAR_FADE_START} blocks from the camera or more, easing down to {@link #NEAR_FADE_MIN} at {@link #NEAR_FADE_END}. */
    static float nearFade(double distance) {
        float t = Mth.clamp((float) (distance - NEAR_FADE_END) / (NEAR_FADE_START - NEAR_FADE_END), 0f, 1f);
        return Mth.lerp(t * t * (3f - 2f * t), NEAR_FADE_MIN, 1f);
    }

    /** Redirects the item model onto its ghost texture, in the ghost's tint and fade alpha. */
    private record GhostVertexConsumer(VertexConsumer delegate, GhostTextures.Ghost ghost, int tint, int alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int a) {
            delegate.setColor(tint >> 16 & 0xFF, tint >> 8 & 0xFF, tint & 0xFF, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            // Per vertex, so nothing allocated here.
            int sprite = ghost.sprite(u, v);
            if (sprite < 0) {
                delegate.setUv(u, v);
            } else {
                delegate.setUv(ghost.u(sprite, u), ghost.v(sprite, v));
            }
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
