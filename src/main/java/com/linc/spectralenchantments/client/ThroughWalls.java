package com.linc.spectralenchantments.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderStateShard;
import org.lwjgl.opengl.GL11;

/**
 * A render state that turns the depth test off, for outlines and glows meant to be seen through walls. Vanilla's own
 * "always" depth state doesn't: it only leaves the depth test alone, and late in the frame (after particles, say) it
 * is still on, so walls would hide what's drawn. Goes in a render type's layering slot, beside its depth test state.
 */
final class ThroughWalls {
    static final RenderStateShard.LayeringStateShard SHARD = new RenderStateShard.LayeringStateShard(
            "spectral_enchantments_through_walls", ThroughWalls::depthOff, ThroughWalls::depthBack);

    /** Whether the depth test was on before, so it's put back as it was. Render thread. */
    private static boolean depthWasOn;

    private ThroughWalls() {}

    private static void depthOff() {
        depthWasOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        RenderSystem.disableDepthTest();
    }

    private static void depthBack() {
        if (depthWasOn) {
            RenderSystem.enableDepthTest();
        }
    }
}
