package com.linc.spectralenchantments.doppelganger;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server rules for the Doppelganger enchantment: starting to sneak in an enchanted chestplate leaves a
 * {@link DoppelgangerEntity} behind (once the owner is on the ground, if they started in the air), which fades away
 * when the owner stands up; the monsters already after it stay on it while it fades. Monsters within
 * {@link SpectralConfig#doppelgangerLureRadius()} of it that are after the owner go after it instead, both those
 * already chasing them when it appears and any that pick the owner later (caught as they choose their target, so
 * nothing is scanned per tick). A monster after the double that the owner hurts sees through the trick for good:
 * it turns on the owner and is tagged {@value #IMMUNE_TAG}, which no double fools again (the tag is saved with the
 * monster and can be given or taken with {@code /tag}).
 */
public final class Doppelganger {
    /** Entity tag on monsters that doubles no longer fool. */
    public static final String IMMUNE_TAG = "spectral_enchantments.doppelganger_immune";

    private static final Map<UUID, State> STATES = new HashMap<>();

    private Doppelganger() {}

    private static final class State {
        @Nullable
        DoppelgangerEntity ghost;
        boolean sneaking;
        /** Sneaking began but no double was left yet, as the owner wasn't on the ground. */
        boolean pending;
        /** Game time of the last double, or -1 before the first. */
        long lastSpawn = -1;

        void clearGhost() {
            if (ghost != null) {
                ghost.discard();
                ghost = null;
            }
        }
    }

    public static boolean has(LivingEntity entity) {
        return ModEnchantments.has(entity.level(), ModEnchantments.DOPPELGANGER, entity.getItemBySlot(EquipmentSlot.CHEST));
    }

    /** Whether doubles have stopped fooling {@code entity}. */
    public static boolean isImmune(Entity entity) {
        return entity.getTags().contains(IMMUNE_TAG);
    }

    /** The owner's double, if it is still standing and not fading away. */
    @Nullable
    public static DoppelgangerEntity ghostOf(Player player) {
        DoppelgangerEntity ghost = anyGhostOf(player);
        return ghost == null || ghost.isFading() ? null : ghost;
    }

    /** The owner's double, standing or fading away. */
    @Nullable
    private static DoppelgangerEntity anyGhostOf(Player player) {
        State state = STATES.get(player.getUUID());
        return state == null || state.ghost == null || state.ghost.isRemoved() ? null : state.ghost;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Fake players (deployers and the like) "sneak" without anyone behind them.
        if (!event.getEntity().level().isClientSide && !(event.getEntity() instanceof FakePlayer)) {
            update(event.getEntity());
        }
    }

    /**
     * Leaves a double behind as {@code player} starts sneaking (or lands, if they started in the air), and fades it
     * away once they stop.
     */
    public static void update(Player player) {
        boolean sneaking = player.isShiftKeyDown() && player.isAlive() && !player.isSpectator();
        State state = STATES.get(player.getUUID());
        if (state == null) {
            if (!sneaking) {
                return;
            }
            state = new State();
            STATES.put(player.getUUID(), state);
        }
        if (sneaking && !state.sneaking) {
            state.pending = true;
        } else if (!sneaking) {
            state.pending = false;
        }
        state.sneaking = sneaking;
        if (state.ghost != null) {
            if (state.ghost.isRemoved()) {
                state.ghost = null;
            } else if (!sneaking || !has(player)) {
                state.ghost.fadeOut();
            }
        }
        if (!state.pending || !player.onGround()) {
            return;
        }
        state.pending = false;
        if (!has(player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (state.lastSpawn >= 0 && now - state.lastSpawn < SpectralConfig.doppelgangerCooldownTicks()) {
            return;
        }
        // One still fading away goes at once.
        state.clearGhost();
        DoppelgangerEntity ghost = DoppelgangerEntity.create(player);
        if (level.addFreshEntity(ghost)) {
            state.ghost = ghost;
            state.lastSpawn = now;
            lure(level, ghost, player);
        }
    }

    /** Turns the monsters already after {@code owner} near the double onto it. */
    private static void lure(ServerLevel level, DoppelgangerEntity ghost, Player owner) {
        double radius = SpectralConfig.doppelgangerLureRadius();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, ghost.getBoundingBox().inflate(radius),
                mob -> mob.distanceToSqr(ghost) <= radius * radius && !isImmune(mob))) {
            if (mob.getTarget() == owner) {
                mob.setTarget(ghost);
            }
            Brain<?> brain = mob.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == owner) {
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, ghost);
            }
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof Player owner) || event.getEntity().level().isClientSide
                || isImmune(event.getEntity())) {
            return;
        }
        DoppelgangerEntity ghost = ghostOf(owner);
        double radius = SpectralConfig.doppelgangerLureRadius();
        if (ghost != null && ghost.level() == event.getEntity().level() && event.getEntity().distanceToSqr(ghost) <= radius * radius) {
            event.setNewAboutToBeSetTarget(ghost);
        }
    }

    /** A monster after the double that the owner hurts drops it for them for good, while the rest stay fooled. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(event.getSource().getEntity() instanceof Player owner)
                || mob.level().isClientSide) {
            return;
        }
        DoppelgangerEntity ghost = anyGhostOf(owner);
        if (ghost == null) {
            return;
        }
        Brain<?> brain = mob.getBrain();
        boolean goalTarget = mob.getTarget() == ghost;
        boolean brainTarget = brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == ghost;
        if (!goalTarget && !brainTarget) {
            return;
        }
        // Marked first so that turning it on the owner below isn't redirected straight back.
        mob.addTag(IMMUNE_TAG);
        if (goalTarget) {
            mob.setTarget(owner);
        }
        if (brainTarget) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, owner);
        }
    }

    /** The owner's (and other players') shots pass through the double; monsters' stop at it. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof DoppelgangerEntity
                && projectile.getOwner() instanceof Player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        State state = STATES.remove(event.getEntity().getUUID());
        if (state != null) {
            state.clearGhost();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }
}
