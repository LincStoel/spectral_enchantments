package com.linc.spectralenchantments.boots;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.client.SpringyClient;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerFlyableFallEvent;

/**
 * Springy: a player in Springy boots who lands from higher than {@link SpectralConfig#springyMinFallDistance()}
 * bounces instead of taking fall damage. Straight down they rise {@link SpectralConfig#springyRestitution()} of the
 * fall; landing at an angle tips the bounce forward by the same angle, trading height for speed. Sneaking on landing
 * takes the fall as usual. Blocks that already cancel fall damage (a slime block's own bounce) or are tagged
 * {@code spectral_enchantments:springy_ignored} don't bounce.
 *
 * Jumping just as they land bounces them almost all the way back up ({@link SpectralConfig#springyPerfectRestitution()}).
 *
 * <p>Both sides cancel the damage; the bounce itself happens on the player's own client, which moves them
 * ({@code SpringyClient}).
 */
public final class Springy {
    public static final TagKey<Block> IGNORED = TagKey.create(Registries.BLOCK, SpectralEnchantments.id("springy_ignored"));

    private Springy() {}

    public static boolean has(LivingEntity entity) {
        return ModEnchantments.has(entity.level(), ModEnchantments.SPRINGY, entity.getItemBySlot(EquipmentSlot.FEET));
    }

    /** Whether {@code player} landing from {@code distance} with this fall-damage multiplier bounces. */
    public static boolean bounces(Player player, float distance, float damageMultiplier) {
        return damageMultiplier > 0f && distance > SpectralConfig.springyMinFallDistance() && !player.isShiftKeyDown()
                && !player.isFallFlying() && !player.getAbilities().flying && !player.isSpectator() && has(player)
                && !player.level().getBlockState(player.getOnPos()).is(IGNORED);
    }

    /**
     * The bounce off a landing at {@code impact} after falling {@code fallDistance}: the speed that rises
     * {@code restitution} of the fall, mirrored off the ground at the angle it struck, so a straight-down landing
     * goes straight up and a glancing one carries on forward.
     */
    public static Vec3 bounce(Vec3 impact, double fallDistance, double restitution) {
        double speed = Math.min(JumpPhysics.speedFor(restitution * fallDistance), SpectralConfig.springyMaxBounceSpeed());
        double horizontal = Math.sqrt(impact.x * impact.x + impact.z * impact.z);
        double down = Math.max(-impact.y, 0.0);
        double length = Math.sqrt(horizontal * horizontal + down * down);
        if (length < 1.0E-6 || horizontal < 1.0E-6) {
            return new Vec3(0, speed, 0);
        }
        double forward = speed * horizontal / length;
        return new Vec3(impact.x / horizontal * forward, speed * down / length, impact.z / horizontal * forward);
    }

    // Before High Jump takes a leap's height off the fall: a Springy landing bounces off the whole drop.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && land(player, event.getDistance(), event.getDamageMultiplier())) {
            event.setCanceled(true);
        }
    }

    /**
     * Creative players take no fall damage, so they land through this event instead. (NeoForge hands it the
     * distance as the multiplier too, so blocks that cancel falls are told apart by the ignored tag alone.)
     */
    @SubscribeEvent
    public static void onFlyableFall(PlayerFlyableFallEvent event) {
        land(event.getEntity(), event.getDistance(), 1f);
    }

    /** A landing on either side: whether it bounces. The client bounces its own player; the server plays the sound. */
    private static boolean land(Player player, float distance, float damageMultiplier) {
        if (!bounces(player, distance, damageMultiplier)) {
            return false;
        }
        if (player.level().isClientSide) {
            SpringyClient.onLocalLanding(player, distance);
        } else {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP_SMALL,
                    SoundSource.PLAYERS, 0.8f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        }
        return true;
    }
}
