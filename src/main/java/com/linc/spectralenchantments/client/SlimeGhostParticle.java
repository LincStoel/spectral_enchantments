package com.linc.spectralenchantments.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A see-through fleck of a Springy ghost slime: a slime ball's crumb (a snowball's, pale, when a rune tints it). It
 * either gathers in on a point that moves with the falling player, or bursts out and drops. Made and added straight
 * to the particle engine on the client, so no particle type is registered.
 */
final class SlimeGhostParticle extends BreakingItemParticle {
    private static final ItemStack SLIME = new ItemStack(Items.SLIME_BALL);
    private static final ItemStack PALE = new ItemStack(Items.SNOWBALL);

    /** Where a gathering fleck heads for, relative to the player's feet; null for a bursting one. */
    private final Vec3 offset;
    private final Entity target;
    private final float startAlpha;

    private SlimeGhostParticle(ClientLevel level, double x, double y, double z, int tint, boolean rune, float alpha,
                               Entity target, Vec3 offset) {
        super(level, x, y, z, rune ? PALE : SLIME);
        this.target = target;
        this.offset = offset;
        this.startAlpha = alpha;
        setColor((tint >> 16 & 0xFF) / 255f, (tint >> 8 & 0xFF) / 255f, (tint & 0xFF) / 255f);
        setAlpha(alpha);
    }

    /** A fleck at {@code from} drawn in to {@code offset} from the target's feet over about {@code ticks}. */
    static SlimeGhostParticle gathering(ClientLevel level, Vec3 from, int tint, boolean rune, float alpha,
                                        Entity target, Vec3 offset, int ticks) {
        SlimeGhostParticle particle = new SlimeGhostParticle(level, from.x, from.y, from.z, tint, rune, alpha, target, offset);
        particle.setParticleSpeed(0, 0, 0);
        particle.gravity = 0f;
        particle.hasPhysics = false;
        particle.lifetime = ticks;
        return particle;
    }

    /** A fleck thrown out from {@code from} at {@code speed}, falling as it fades. */
    static SlimeGhostParticle bursting(ClientLevel level, Vec3 from, Vec3 speed, int tint, boolean rune, float alpha) {
        SlimeGhostParticle particle = new SlimeGhostParticle(level, from.x, from.y, from.z, tint, rune, alpha, null, null);
        particle.setParticleSpeed(speed.x, speed.y, speed.z);
        particle.gravity = 0.6f;
        particle.lifetime = 10 + particle.random.nextInt(6);
        return particle;
    }

    @Override
    public void tick() {
        if (target == null) {
            super.tick();
            alpha = startAlpha * (1f - (float) age / lifetime);
            return;
        }
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        // Close a share of the gap each tick, all of it on the last, so it lands in the slime as it forms.
        Vec3 goal = target.position().add(offset);
        double share = 1.0 / Math.max(1, lifetime - age + 1);
        setPos(x + (goal.x - x) * share, y + (goal.y - y) * share, z + (goal.z - z) * share);
    }
}
