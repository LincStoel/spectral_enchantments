package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.boots.HighJump;
import com.linc.spectralenchantments.boots.JumpPhysics;
import com.linc.spectralenchantments.network.HighJumpPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * High Jump on the client, where the local player's jump happens. Pressing jump jumps as usual, but where vanilla
 * would jump again on landing while the key is held, High Jump holds the player down instead and vanilla's horse jump
 * bar takes the experience bar's place, charging (and sagging) just as a horse's does. Letting go leaps with whatever
 * charge it has. Leaving the ground puts the bar away, and so does holding on for
 * {@link SpectralConfig#highJumpChargeTimeoutTicks()}, until the key is let go.
 */
public final class HighJumpClient {
    public static final ResourceLocation BAR_LAYER = SpectralEnchantments.id("high_jump_bar");
    // Vanilla's own jump bar sprites (Gui.renderJumpMeter).
    private static final ResourceLocation BAR_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/jump_bar_background");
    private static final ResourceLocation BAR_PROGRESS = ResourceLocation.withDefaultNamespace("hud/jump_bar_progress");

    /** The held jump key has already had its plain jump: touching the ground again charges instead. */
    private static boolean hopped;
    private static boolean armed;
    private static int chargeTicks;
    private static float charge;
    /** The bar gave up waiting for the key to come up: nothing more until it does. */
    private static boolean timedOut;

    private HighJumpClient() {}

    static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.JUMP_METER, BAR_LAYER, HighJumpClient::renderBar);
    }

    /** Right after the keys are read, before the player moves: charge instead of jumping again, and leap on release. */
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        Input input = event.getInput();
        int level = HighJump.level(player);
        if (level <= 0 || !player.onGround() || player.isPassenger() || player.isSpectator()
                || player.getAbilities().flying || player.isInFluidType() || player.isFallFlying()) {
            // In the air between hops the key may still be held, so remember the plain jump it already had.
            boolean keepHopped = hopped && input.jumping;
            reset();
            hopped = keepHopped;
            return;
        }
        if (!input.jumping) {
            if (armed) {
                leap(player, level, HighJump.power(charge));
            }
            reset();
            return;
        }
        if (!hopped) {
            // The first time the held key finds the player on the ground, vanilla jumps as usual.
            hopped = true;
            return;
        }
        input.jumping = false;
        if (timedOut) {
            return;
        }
        if (++chargeTicks > SpectralConfig.highJumpChargeTimeoutTicks()) {
            armed = false;
            timedOut = true;
        } else {
            armed = true;
            charge = HighJump.chargeScale(chargeTicks);
        }
    }

    private static void leap(LocalPlayer player, int level, float power) {
        double speed = JumpPhysics.speedFor(HighJump.height(level, power)) + player.getJumpBoostPower();
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, speed, motion.z);
        player.hasImpulse = true;
        PacketDistributor.sendToServer(new HighJumpPayload(power));
    }

    private static void reset() {
        hopped = false;
        armed = false;
        chargeTicks = 0;
        charge = 0f;
        timedOut = false;
    }

    /** While the bar is up it stands in for the experience bar, as a horse's does. */
    static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (armed && (event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR) || event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL))) {
            event.setCanceled(true);
        }
    }

    /** Gui.renderJumpMeter, for our charge. */
    private static void renderBar(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!armed || mc.options.hideGui || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - 32 + 3;
        int filled = (int) (charge * 183f);
        graphics.blitSprite(BAR_BACKGROUND, x, y, 182, 5);
        if (filled > 0) {
            graphics.blitSprite(BAR_PROGRESS, 182, 5, 0, 0, x, y, filled, 5);
        }
    }
}
