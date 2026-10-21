package com.linc.spectralenchantments.boots;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.network.HighJumpPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Server rules for High Jump, whose leap itself happens on the jumping player's client (players move themselves):
 * landing with the jump key still held, a player charges a jump like a horse's instead of jumping again, and leaps up
 * to {@link SpectralConfig#highJumpHeight(int)} on letting go. The server checks the boots, puffs dust out
 * from the ground for everyone nearby to see, and forgives the leap's extra height on the next landing, so a full
 * leap onto level ground does no harm.
 */
public final class HighJump {
    /** A forgiven leap that hasn't landed in this long (in water, say) is forgotten. */
    private static final long FORGIVE_TICKS = 200;

    private static final Map<UUID, Leap> LEAPS = new HashMap<>();

    private record Leap(float extraHeight, long gameTime) {}

    private HighJump() {}

    public static int level(LivingEntity entity) {
        return ModEnchantments.level(entity.level(), ModEnchantments.HIGH_JUMP, entity.getItemBySlot(EquipmentSlot.FEET));
    }

    /**
     * The jump charge after {@code ticks} of charging, as a horse's (LocalPlayer.aiStep): full at half a second, then
     * sagging back toward 0.8, so the leap rewards letting go on time.
     */
    public static float chargeScale(int ticks) {
        return ticks < 10 ? ticks * 0.1f : 0.8f + 2f / (ticks - 9) * 0.1f;
    }

    /** The share of a full leap a charge gives, as for a horse: a near-full charge leaps fully, none at all 0.4. */
    public static float power(float scale) {
        return scale >= 0.9f ? 1f : 0.4f + 0.4f * scale / 0.9f;
    }

    /** How high a leap at {@code power} rises with High Jump at {@code level}: from a plain jump's height to the full one. */
    public static double height(int level, float power) {
        double full = Math.max(JumpPhysics.VANILLA_JUMP_HEIGHT, SpectralConfig.highJumpHeight(level));
        return Mth.lerp(Mth.clamp(power, 0f, 1f), JumpPhysics.VANILLA_JUMP_HEIGHT, full);
    }

    public static void handle(ServerPlayer player, HighJumpPayload payload) {
        int level = level(player);
        float power = payload.power();
        // The leap is announced the tick it starts, before the move that lifts the player off reaches the server.
        if (level <= 0 || !(power >= 0f && power <= 1f) || !player.onGround() || player.isPassenger()) {
            return;
        }
        double extra = height(level, power) - JumpPhysics.VANILLA_JUMP_HEIGHT;
        LEAPS.put(player.getUUID(), new Leap((float) extra, player.level().getGameTime()));
        puff(player.serverLevel(), player, power);
    }

    /** Dust blown out from under the player's feet, more the harder they leap. */
    private static void puff(ServerLevel level, Player player, float power) {
        double x = player.getX();
        double y = player.getY() + 0.05;
        double z = player.getZ();
        BlockState ground = level.getBlockState(player.getOnPos());
        if (!ground.isAir() && ground.getRenderShape() != RenderShape.INVISIBLE) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            int count = 8 + Math.round(8 * power);
            for (int i = 0; i < count; i++) {
                double angle = Mth.TWO_PI * i / count;
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                // A count of 0 sends the offset as the particle's speed: straight out from the feet.
                level.sendParticles(dust, x + dx * 0.3, y, z + dz * 0.3, 0, dx, 0.2, dz, 1.0);
            }
        }
        level.sendParticles(ParticleTypes.POOF, x, y + 0.1, z, 3 + Math.round(5 * power), 0.35, 0.05, 0.35, 0.03);
        level.playSound(null, x, y, z, SoundEvents.HORSE_JUMP, SoundSource.PLAYERS, 0.3f + 0.3f * power, 1.3f);
    }

    /** Takes a leap's extra height off the landing that ends it. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        // The local player's own landing is also seen on the client (sharing this map in single player); leave it be.
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        Leap leap = LEAPS.remove(player.getUUID());
        if (leap != null && player.level().getGameTime() - leap.gameTime() <= FORGIVE_TICKS) {
            event.setDistance(Math.max(0f, event.getDistance() - leap.extraHeight()));
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LEAPS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LEAPS.clear();
    }
}
