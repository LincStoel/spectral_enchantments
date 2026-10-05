package com.linc.broadbreaker.client;

import com.linc.broadbreaker.BroadBreaker;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.inventory.InventoryMenu;

/** Extends RenderType only to reach its protected render-state shards. */
final class SpectralRenderType extends RenderType {
    static ShaderInstance spectralShader;

    /** Translucent, unlit, no depth writes: ghosts never hide each other or the blocks behind them. */
    static final RenderType GHOST_TOOL = create(
            BroadBreaker.MOD_ID + ":ghost_tool",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> spectralShader))
                    .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private SpectralRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                               boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
