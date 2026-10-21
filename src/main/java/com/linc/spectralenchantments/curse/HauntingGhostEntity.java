package com.linc.spectralenchantments.curse;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.doppelganger.PlayerGhostEntity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
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
 *   <li>A player's ghost stands somewhere off from them and stares. Struck by a player it vanishes for a while (see
 *       {@link Haunting}); if they come too close or go too far, it vanishes in a puff. With monsters about it
 *       floats over to them and haunts the nearest; once they're gone, so is it.</li>
 *   <li>A monster's ghost haunts it for as long as it wears the armor, floating at its shoulder. Struck by a player it
 *       fades out for a while, then returns.</li>
 * </ul>
 *
 * A haunted monster slowly heals, and every so often the ghost raises its arms like an evoker casting a spell
 * ({@link #isCasting()}), giving it and the monsters around it Speed, Strength or Resistance for a while. Moves through
 * blocks; monsters never notice it.
 */
public class HauntingGhostEntity extends PlayerGhostEntity {
    private static final EntityDataAccessor<Boolean> CASTING =
            SynchedEntityData.defineId(HauntingGhostEntity.class, EntityDataSerializers.BOOLEAN);
    /** The colour of the spell it casts, for its particles. */
    private static final EntityDataAccessor<Integer> SPELL_COLOR =
            SynchedEntityData.defineId(HauntingGhostEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HAUNTING =
            SynchedEntityData.defineId(HauntingGhostEntity.class, EntityDataSerializers.BOOLEAN);
    /** How long a spell-casting gesture lasts, in ticks. */
    public static final int CAST_TICKS = 40;
    /** What its spells may give the monsters. */
    private static final List<Holder<MobEffect>> SPELLS =
            List.of(MobEffects.MOVEMENT_SPEED, MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE);
    /** How often a haunted monster heals half a heart, in ticks: as Regeneration I, which undead would ignore. */
    private static final int HEAL_INTERVAL = 50;
    private static final float[] HEAL_PARTICLE_COLOR = {0.8f, 0.36f, 0.67f};
    /** How often it looks for monsters near its owner, in ticks. */
    private static final int SEARCH_INTERVAL = 10;
    /** How often a monster's ghost checks that its owner still wears the curse, in ticks. */
    private static final int ARMOR_CHECK_INTERVAL = 20;
    private static final double DRIFT_SPEED = 0.3;
    /** A monster's ghost further than this from it, in blocks, catches up at once. */
    private static final double ESCORT_SNAP_DISTANCE = 12;

    private int stareTicks;
    /** Ticks until it next casts while haunting, then ticks left of the gesture while {@link #isCasting()}. */
    private int castTimer;
    /** Ticks a struck monster's ghost stays faded out, or 0 while it is out. */
    private int hiddenTicks;
    /** The monster a player's ghost haunts, or null while it stares. */
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
        ghost.castTimer = ghost.nextCastDelay();
        return ghost;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CASTING, false);
        builder.define(SPELL_COLOR, 0xFFFFFF);
        builder.define(HAUNTING, false);
    }

    /** Ticks before the next ghost should appear, or -1 for the usual wait. */
    public int returnDelay() {
        return returnDelay;
    }

    /** Whether it's in the middle of an evoker's spell-casting gesture. */
    public boolean isCasting() {
        return entityData.get(CASTING);
    }

    /** Whether it's floating by a monster, haunting it, rather than standing and staring. */
    public boolean isHaunting() {
        return entityData.get(HAUNTING);
    }

    /**
     * Raises its arms for {@link #CAST_TICKS} and gives {@code mob}, and the monsters within the spell radius of it, one
     * of Speed, Strength or Resistance. Server only.
     */
    public void cast(Mob mob) {
        Holder<MobEffect> spell = SPELLS.get(random.nextInt(SPELLS.size()));
        entityData.set(CASTING, true);
        entityData.set(SPELL_COLOR, spell.value().getColor());
        castTimer = CAST_TICKS;
        double radius = SpectralConfig.hauntingBuffRadius();
        int ticks = SpectralConfig.hauntingBuffTicks();
        int amplifier = SpectralConfig.hauntingBuffLevel() - 1;
        for (Mob monster : level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(radius),
                m -> m == mob || m instanceof Enemy && m.isAlive() && m.distanceToSqr(mob) <= radius * radius)) {
            monster.addEffect(new MobEffectInstance(spell, ticks, amplifier), this);
        }
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
        if (tickCount % SEARCH_INTERVAL == 0 || focus != null && !focus.isAlive()) {
            focus = findFocus(owner);
        }
        if (focus != null) {
            // Just behind the monster, as its owner sees it.
            Vec3 away = focus.position().subtract(owner.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(focus.getBbWidth() / 2 + 1.2);
            driftTo(focus.position().add(away).add(0, 0.4, 0), DRIFT_SPEED);
            haunt(focus);
        } else if (isHaunting()) {
            // Its monsters are gone, and it's left floating.
            vanish(-1);
        } else {
            stare(owner);
        }
    }

    /**
     * Stands where it is, watching; vanishes if its owner comes too close (unless monsters have turned up since it last
     * looked, as when a mob's blow calls it out) or goes too far, and once it has stared long enough.
     */
    private void stare(Player owner) {
        lookAt(owner);
        double distance = owner.distanceToSqr(this);
        if (distance < Mth.square(SpectralConfig.hauntingApproachDistance())) {
            focus = findFocus(owner);
            if (focus == null) {
                vanish(-1);
            }
        } else if (distance > Mth.square(SpectralConfig.hauntingVanishDistance()) || --stareTicks <= 0) {
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
        if (hiddenTicks > 0) {
            if (--hiddenTicks == 0) {
                setInvisible(false);
            }
            return;
        }
        haunt(owner);
    }

    /** Just behind and above the monster's shoulder. */
    private Vec3 escortSpot(Mob owner) {
        Vec3 behind = Vec3.directionFromRotation(0, owner.yBodyRot + 180).scale(owner.getBbWidth() / 2 + getBbWidth() / 2 + 0.4);
        return owner.position().add(behind).add(0, 0.3, 0);
    }

    // ---- Both ----

    /** Reaches out to the monster it haunts, healing it slowly and casting its spell every so often. */
    private void haunt(Mob mob) {
        entityData.set(HAUNTING, true);
        lookAt(mob);
        if (tickCount % HEAL_INTERVAL == 0 && mob.getHealth() < mob.getMaxHealth()) {
            mob.heal(1f);
            ((ServerLevel) level()).sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT,
                            HEAL_PARTICLE_COLOR[0], HEAL_PARTICLE_COLOR[1], HEAL_PARTICLE_COLOR[2]),
                    mob.getX(), mob.getY(0.5), mob.getZ(), 4, mob.getBbWidth() / 2, mob.getBbHeight() / 4, mob.getBbWidth() / 2, 0);
        }
        if (--castTimer <= 0) {
            if (isCasting()) {
                entityData.set(CASTING, false);
                castTimer = nextCastDelay();
            } else {
                cast(mob);
            }
        }
    }

    private int nextCastDelay() {
        int min = SpectralConfig.hauntingCastMinTicks();
        return min + random.nextInt(SpectralConfig.hauntingCastMaxTicks() - min + 1);
    }

    /** Wisps the colour of its spell rising from both raised hands, as from a casting evoker's. Client only. */
    private void castParticles() {
        float angle = yBodyRot * Mth.DEG_TO_RAD + Mth.cos(tickCount * 0.6662f) * 0.25f;
        double cos = Mth.cos(angle) * 0.6;
        double sin = Mth.sin(angle) * 0.6;
        ColorParticleOption particle = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT,
                FastColor.ARGB32.opaque(entityData.get(SPELL_COLOR)));
        level().addParticle(particle, getX() + cos, getY() + 1.8, getZ() + sin, 0, 0, 0);
        level().addParticle(particle, getX() - cos, getY() + 1.8, getZ() - sin, 0, 0, 0);
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
