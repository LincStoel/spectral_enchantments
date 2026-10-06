package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.SpectralEnchantments;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Pale, translucent copies of tool sprites, so a ghost is the tool's own shape and shading in spectral
 * white. Drawn with a vanilla render type, which shader packs (Iris) render correctly, unlike a custom
 * core shader.
 */
final class GhostTextures {
    /** One ghost texture: the model's sprites side by side, whitened. */
    static final class Ghost {
        final RenderType renderType;
        /** Non-emissive variant: lit by the world, but shaderpacks keep its transparency, so it can fade. */
        final RenderType litRenderType;
        private final ResourceLocation id;
        private final TextureAtlasSprite[] sprites;
        private final float[] slotU0;
        private final float[] slotU1;
        private final float[] slotV1;

        private Ghost(ResourceLocation id, TextureAtlasSprite[] sprites, float[] slotU0, float[] slotU1, float[] slotV1) {
            this.id = id;
            this.renderType = RenderType.entityTranslucentEmissive(id);
            this.litRenderType = RenderType.entityTranslucent(id);
            this.sprites = sprites;
            this.slotU0 = slotU0;
            this.slotU1 = slotU1;
            this.slotV1 = slotV1;
        }

        /** Maps a block-atlas UV of one of the model's sprites to the same texel in this texture. */
        float[] remap(float u, float v) {
            for (int i = 0; i < sprites.length; i++) {
                TextureAtlasSprite s = sprites[i];
                if (u >= s.getU0() - 1e-6f && u <= s.getU1() + 1e-6f && v >= s.getV0() - 1e-6f && v <= s.getV1() + 1e-6f) {
                    float fu = (u - s.getU0()) / (s.getU1() - s.getU0());
                    float fv = (v - s.getV0()) / (s.getV1() - s.getV0());
                    return new float[] {slotU0[i] + fu * (slotU1[i] - slotU0[i]), fv * slotV1[i]};
                }
            }
            return new float[] {u, v};
        }
    }

    private static final Map<BakedModel, Ghost> CACHE = new IdentityHashMap<>();
    private static int nextId;

    private GhostTextures() {}

    @Nullable
    static Ghost get(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
        Ghost ghost = CACHE.get(model);
        if (ghost == null && !CACHE.containsKey(model)) {
            ghost = build(mc, stack, model);
            CACHE.put(model, ghost);
        }
        return ghost;
    }

    /** Resource reload: sprites and models are rebuilt, so drop every ghost texture. */
    static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Ghost ghost : CACHE.values()) {
            if (ghost != null) {
                mc.getTextureManager().release(ghost.id);
            }
        }
        CACHE.clear();
    }

    @Nullable
    private static Ghost build(Minecraft mc, ItemStack stack, BakedModel model) {
        Set<TextureAtlasSprite> found = new LinkedHashSet<>();
        RandomSource random = RandomSource.create(42L);
        for (BakedModel pass : model.getRenderPasses(stack, false)) {
            for (Direction side : new Direction[] {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                for (BakedQuad quad : pass.getQuads(null, side, random, ModelData.EMPTY, null)) {
                    found.add(quad.getSprite());
                }
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        List<TextureAtlasSprite> sprites = new ArrayList<>(found);
        int width = 0;
        int height = 0;
        for (TextureAtlasSprite sprite : sprites) {
            width += sprite.contents().width();
            height = Math.max(height, sprite.contents().height());
        }

        NativeImage image = new NativeImage(width, height, true);
        float[] slotU0 = new float[sprites.size()];
        float[] slotU1 = new float[sprites.size()];
        float[] slotV1 = new float[sprites.size()];
        int x0 = 0;
        for (int i = 0; i < sprites.size(); i++) {
            SpriteContents contents = sprites.get(i).contents();
            NativeImage source = contents.getOriginalImage(); // first animation frame sits at (0, 0)
            for (int y = 0; y < contents.height(); y++) {
                for (int x = 0; x < contents.width(); x++) {
                    image.setPixelRGBA(x0 + x, y, ghostPixel(source.getPixelRGBA(x, y)));
                }
            }
            slotU0[i] = (float) x0 / width;
            slotU1[i] = (float) (x0 + contents.width()) / width;
            slotV1[i] = (float) contents.height() / height;
            x0 += contents.width();
        }

        ResourceLocation id = SpectralEnchantments.id("ghost/" + nextId++);
        mc.getTextureManager().register(id, new DynamicTexture(image));
        return new Ghost(id, sprites.toArray(TextureAtlasSprite[]::new), slotU0, slotU1, slotV1);
    }

    /** Cool spectral white that keeps the sprite's shading (via luminance); darker texels are more see-through. */
    private static int ghostPixel(int abgr) {
        int alpha = FastColor.ABGR32.alpha(abgr);
        if (alpha < 26) {
            return 0;
        }
        float luminance = (0.299f * FastColor.ABGR32.red(abgr) + 0.587f * FastColor.ABGR32.green(abgr)
                + 0.114f * FastColor.ABGR32.blue(abgr)) / 255f;
        int red = (int) (255 * (0.78f + 0.22f * luminance));
        int green = (int) (255 * (0.86f + 0.14f * luminance));
        int outAlpha = (int) (alpha * (0.55f + 0.45f * luminance));
        return FastColor.ABGR32.color(outAlpha, 255, green, red);
    }
}
