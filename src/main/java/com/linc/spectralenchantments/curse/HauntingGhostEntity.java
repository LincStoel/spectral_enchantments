package com.linc.spectralenchantments.curse;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.doppelganger.PlayerGhostEntity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Curse of Haunting's ghost: a pale copy of whoever wears the cursed armor.
 *
 * <ul>
 *   <li>A player's ghost stares at them from afar. With monsters about it drifts over to them and lends Strength to
 *       every monster near it. Struck by a player it vanishes for a while (see {@link Haunting}); approached while
 *       staring, it flees for a second and is gone. Every so often it raises its arms like an evoker casting a
 *       spell ({@link #isCasting()}).</li>
 *   <li>A monster's ghost stays at its shoulder for as long as it wears the armor,
 *       lending Strength to the monsters around it. Struck by a player it fades out for a while, then returns.</li>
 * </ul>
 *
 * Moves through blocks; monsters never notice it.
 */
public class HauntingGhostEntity extends PlayerGhostEntity {
    private static final EntityDataAccessor<Boolean> CASTING =
            SynchedEntityData.defineId(HauntingGhostEntity.class, EntityDataSerializers.BOOLEAN);
    /** How long a spell-casting gesture lasts, in ticks. */
    public static final int CAST_TICKS = 40;
    /** An evoker's spell particles, pale like the ghost. */
    private static final float[] CAST_PARTICLE_COLOR = {0.8f, 0.8f, 0.9f};
    /** How often it looks for monsters near its owner, in ticks. */
    private static final int SEARCH_INTERVAL = 10;
    /** How often it hands out Strength, and for how long each lasts, in ticks. */
    private static final int STRENGTH_INTERVAL = 20;
    private static final int STRENGTH_TICKS = 60;
    /** How often a monster's ghost checks that its owner still wears the curse, in ticks. */
    private static final int ARMOR_CHECK_INTERVAL = 20;
    private static final int FLEE_TICKS = 20;
    private static final double DRIFT_SPEED = 0.3;
    private static final double FLEE_SPEED = 0.45;
    /** A monster's ghost further than this from it, in blocks, catches up at once. */
    private static final double ESCORT_SNAP_DISTANCE = 12;

    private int stareTicks;
    /** Ticks until a player's ghost next raises its arms, then ticks left of the gesture while {@link #isCasting()}. */
    private int castTimer;
    /** Ticks of fleeing left, or 0 while not fleeing. */
    private int fleeTicks;
    /** Ticks a struck monster's ghost stays faded out, or 0 while it is out. */
    private int hiddenTicks;
    /** The monster a player's ghost hovers by, or null while it stares. */
    @Nullable
    private Mob focus;
    /** Ticks before it comes back once gone, or -1 to wait the usual time. Server only. */
    private int returnDelay = -1;

    public HauntingGhostEntity(EntityType<? extends HauntingGhostEntity> type, Level level) {
        super(type, level);
    }

    /** A new ghost of {@code owner} at {@code pos}, already looking at them. */
    public static HauntingGhostEntity create(Player owner, ItemStack cursedArmor, Vec3 pos) {
        HauntingGhostEntity ghost = new HauntingGhostEntity(ModEntities.HAUNTING_GHOST.get(), owner.level());
        ghost.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
        ghost.bind(owner, cursedArmor, 0f);
        ghost.lookAt(owner);
        ghost.stareTicks = SpectralConfig.hauntingStareTicks();
        ghost.castTimer = ghost.nextCastDelay();
        return ghost;
    }

    /** A new ghost of the monster {@code owner}, at its shoulder. */
    public static HauntingGhostEntity create(Mob owner, ItemStack cursedArmor) {
        HauntingGhostEntity ghost = new HauntingGhostEntity(ModEntities.HAUNTING_GHOST.get(), owner.level());
        Vec3 pos = ghost.escortSpot(owner);
        ghost.moveTo(pos.x, pos.y, pos.z, owner.getYRot(), 0f);
        ghost.bind(owner, cursedArmor, owner.getYRot());
        return ghost;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CASTING, false);
    }

    /** Ticks before the next ghost should appear, or -1 for the usual wait. */
    public int returnDelay() {
        return returnDelay;
    }

    /** Whether it's in the middle of an evoker's spell-casting gesture. */
    public boolean isCasting() {
        return entityData.get(CASTING);
    }

    /** Raises its arms for {@link #CAST_TICKS}. Server only. */
    public void startCasting() {
        entityData.set(CASTING, true);
        castTimer = CAST_TICKS;
    }

    public boolean isFleeing() {
        return fleeTicks > 0;
    }

    /** Whether a struck monster's ghost is faded out for now. */
    public boolean isHidden() {
        return hiddenTicks > 0;
    }

    @Nullable
    public Mob focus() {
        return focus;
    }

    @Override
    public boolean stares() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        // Turn as a whole, so the body faces where it looks (head turning would let it idle-turn on the client).
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (level().isClientSide) {
            if (isCasting()) {
                castParticles();
            }
            return;
        }
        LivingEntity owner = owner();
        if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level()) {
            discard();
        } else if (owner instanceof Player player) {
            tickPlayerGhost(player);
        } else if (owner instanceof Mob mob) {
            tickMonsterGhost(mob);
        }
    }

    // ---- A player's ghost ----

    private void tickPlayerGhost(Player owner) {
        if (owner.distanceToSqr(this) > Mth.square(SpectralConfig.hauntingLeashDistance())) {
            discard();
            return;
        }
        if (fleeTicks > 0) {
            entityData.set(CASTING, false);
            flee(owner);
            return;
        }
        if (--castTimer <= 0) {
            if (isCasting()) {
                entityData.set(CASTING, false);
                castTimer = nextCastDelay();
            } else {
                startCasting();
            }
        }
        if (tickCount % SEARCH_INTERVAL == 0 || focus != null && !focus.isAlive()) {
            focus = findFocus(owner);
        }
        if (focus != null) {
            haunt(owner, focus);
        } else {
            stare(owner);
        }
    }

    /**
     * Hovers where it is, watching; flees if its owner comes too close (unless monsters have turned up since it last
     * looked, as when a mob's blow calls it out), and leaves once it has stared long enough.
     */
    private void stare(Player owner) {
        lookAt(owner);
        if (owner.distanceToSqr(this) < Mth.square(SpectralConfig.hauntingApproachDistance())) {
            focus = findFocus(owner);
            if (focus == null) {
                fleeTicks = FLEE_TICKS;
            }
        } else if (--stareTicks <= 0) {
            vanish(-1);
        }
    }

    /** Drifts to just behind the monster (as its owner sees it) and lends Strength to the monsters around it. */
    private void haunt(Player owner, Mob mob) {
        Vec3 away = mob.position().subtract(owner.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(mob.getBbWidth() / 2 + 1.2);
        driftTo(mob.position().add(away).add(0, 0.4, 0), DRIFT_SPEED);
        lookAt(owner);
        strengthen(null);
    }

    /** Darts away from its owner, then is gone. */
    private void flee(Player owner) {
        Vec3 away = position().subtract(owner.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, getYRot() + 180) : away.normalize();
        driftTo(position().add(away.scale(FLEE_SPEED)).add(0, 0.05, 0), FLEE_SPEED);
        setYRot((float) (Mth.atan2(away.z, away.x) * Mth.RAD_TO_DEG) - 90f);
        setXRot(0f);
        if (--fleeTicks <= 0) {
            vanish(-1);
        }
    }

    /** The monster nearest its owner within the haunting radius, or null if there's none. */
    @Nullable
    private Mob findFocus(Player owner) {
        double radius = SpectralConfig.hauntingMonsterRadius();
        List<Mob> monsters = level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(radius),
                m -> m instanceof Enemy && m.isAlive() && m.distanceToSqr(owner) <= radius * radius);
        Mob nearest = null;
        double best = Double.MAX_VALUE;
        for (Mob monster : monsters) {
            double distance = monster.distanceToSqr(owner);
            if (distance < best) {
                best = distance;
                nearest = monster;
            }
        }
        return nearest;
    }

    private int nextCastDelay() {
        int min = SpectralConfig.hauntingCastMinTicks();
        return min + random.nextInt(SpectralConfig.hauntingCastMaxTicks() - min + 1);
    }

    /** Wisps rising from both raised hands, as from a casting evoker's. Client only. */
    private void castParticles() {
        float angle = yBodyRot * Mth.DEG_TO_RAD + Mth.cos(tickCount * 0.6662f) * 0.25f;
        double cos = Mth.cos(angle) * 0.6;
        double sin = Mth.sin(angle) * 0.6;
        ColorParticleOption particle = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT,
                CAST_PARTICLE_COLOR[0], CAST_PARTICLE_COLOR[1], CAST_PARTICLE_COLOR[2]);
        level().addParticle(particle, getX() + cos, getY() + 1.8, getZ() + sin, 0, 0, 0);
        level().addParticle(particle, getX() - cos, getY() + 1.8, getZ() - sin, 0, 0, 0);
    }

    // ---- A monster's ghost ----

    private void tickMonsterGhost(Mob owner) {
        if (tickCount % ARMOR_CHECK_INTERVAL == 0 && Haunting.cursedArmor(owner).isEmpty()) {
            discard();
            return;
        }
        Vec3 spot = escortSpot(owner);
        if (spot.distanceToSqr(position()) > ESCORT_SNAP_DISTANCE * ESCORT_SNAP_DISTANCE) {
            setPos(spot);
        } else {
            driftTo(spot, Math.max(DRIFT_SPEED, spot.distanceTo(position()) * 0.3));
        }
        LivingEntity target = owner.getTarget();
        if (target != null && target.isAlive()) {
            lookAt(target);
        } else {
            setYRot(owner.getYHeadRot());
            setXRot(owner.getXRot());
        }
        if (hiddenTicks > 0) {
            if (--hiddenTicks == 0) {
                setInvisible(false);
            }
            return;
        }
        strengthen(owner);
    }

    /** Just behind and above the monster's shoulder. */
    private Vec3 escortSpot(Mob owner) {
        Vec3 behind = Vec3.directionFromRotation(0, owner.yBodyRot + 180).scale(owner.getBbWidth() / 2 + getBbWidth() / 2 + 0.4);
        return owner.position().add(behind).add(0, 0.3, 0);
    }

    // ---- Both ----

    /** Every so often, lends Strength to the monsters near it (and its owner, if it's a monster). */
    private void strengthen(@Nullable Mob owner) {
        if (tickCount % STRENGTH_INTERVAL != 0) {
            return;
        }
        double radius = SpectralConfig.hauntingStrengthRadius();
        int amplifier = SpectralConfig.hauntingStrengthLevel() - 1;
        for (Mob monster : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(radius),
                m -> (m instanceof Enemy || m == owner) && m.isAlive() && m.distanceToSqr(this) <= radius * radius)) {
            monster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, STRENGTH_TICKS, amplifier, true, true), this);
        }
    }

    private void driftTo(Vec3 target, double speed) {
        Vec3 step = target.subtract(position());
        if (step.lengthSqr() > speed * speed) {
            step = step.normalize().scale(speed);
        }
        setPos(position().add(step));
    }

    private void lookAt(Entity entity) {
        double dx = entity.getX() - getX();
        double dz = entity.getZ() - getZ();
        double dy = entity.getEyeY() - getEyeY();
        setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f);
        setXRot((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide && owner() instanceof Mob mob) {
            Haunting.forget(mob, this);
        }
    }

    /** Goes in a puff of smoke, to return after {@code delay} ticks (-1 for the usual wait). */
    public void vanish(int delay) {
        returnDelay = delay;
        discard();
    }

    /**
     * A player's blow (or shot) banishes it for a little while: a player's ghost goes (see {@link Haunting}), a
     * monster's fades out where it is. Nothing else touches it.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            discard();
        } else if (source.getEntity() instanceof Player && !isHidden()) {
            int min = SpectralConfig.hauntingBanishMinTicks();
            int delay = min + random.nextInt(SpectralConfig.hauntingBanishMaxTicks() - min + 1);
            if (owner() instanceof Player) {
                vanish(delay);
            } else {
                hiddenTicks = delay;
                setInvisible(true);
                ((ServerLevel) level()).sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 24,
                        getBbWidth() / 2, getBbHeight() / 3, getBbWidth() / 2, 0.02);
            }
        }
        return false;
    }

    /** Monsters never notice it; it's on their side. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    /** Players can swing at it (Better Combat sweeps included), unless it has faded out. */
    @Override
    public boolean isPickable() {
        return isAlive() && !isInvisible();
    }

    @Override
    public boolean isAttackable() {
        return !isInvisible();
    }

    /** Players' shots strike it; see {@link Haunting} for monsters', which pass through. */
    @Override
    public boolean canBeHitByProjectile() {
        return isAlive() && !isInvisible();
    }
}
