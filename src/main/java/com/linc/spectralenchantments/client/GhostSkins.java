package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.SpectralEnchantments;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Pale copies of player skins for Doppelganger doubles, whitened the way {@link GhostTextures} whitens tool sprites,
 * so a double is a spectral copy of its owner whatever their skin (and a rune's tint shows on it). Each skin is
 * read back from the GPU once, when a double first wears it, so downloaded skins work as well as built-in ones.
 */
final class GhostSkins {
    private static final Map<ResourceLocation, ResourceLocation> CACHE = new HashMap<>();
    /** Textures that can't be copied (too big, or an atlas): not tried again until the next resource reload. */
    private static final Set<ResourceLocation> FAILED = new HashSet<>();
    private static int nextId;

    private GhostSkins() {}

    /** The ghost copy of {@code skin}, or the skin itself if it can't be read (not uploaded yet, say). */
    static ResourceLocation get(ResourceLocation skin) {
        ResourceLocation ghost = find(skin);
        return ghost != null ? ghost : skin;
    }

    /**
     * The ghost copy of {@code texture}, or null if it has none (yet). One that can never be copied (an atlas, or too
     * big) isn't tried again; one not uploaded yet (a skin still downloading) is, next time.
     */
    @Nullable
    static ResourceLocation find(ResourceLocation texture) {
        ResourceLocation ghost = CACHE.get(texture);
        if (ghost == null && !FAILED.contains(texture)) {
            ghost = build(texture);
            if (ghost != null) {
                CACHE.put(texture, ghost);
            }
        }
        return ghost;
    }

    /** Resource reload: skins may be reloaded too, so drop every copy. */
    static void clear() {
        Minecraft mc = Minecraft.getInstance();
        CACHE.values().forEach(mc.getTextureManager()::release);
        CACHE.clear();
        FAILED.clear();
    }

    private static ResourceLocation build(ResourceLocation skin) {
        RenderSystem.assertOnRenderThread();
        Minecraft mc = Minecraft.getInstance();
        AbstractTexture texture = mc.getTextureManager().getTexture(skin);
        if (texture instanceof TextureAtlas) {
            FAILED.add(skin); // a stitched atlas (blocks, items, trims): far too big to copy
            return null;
        }
        texture.bind();
        int width = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (width > 1024 || height > 1024) {
            FAILED.add(skin);
            return null;
        }
        if (width <= 0 || height <= 0) {
            return null; // not uploaded yet
        }
        NativeImage image = new NativeImage(width, height, false);
        image.downloadTexture(0, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, GhostTextures.ghostPixel(image.getPixelRGBA(x, y)));
            }
        }
        ResourceLocation id = SpectralEnchantments.id("ghost_skin/" + nextId++);
        mc.getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }
}
