package com.linc.spectralenchantments.spectral;

import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A weapon that flies about and fights on its own (an Animated weapon). Never saved,
 * never touched or hurt. The server flies it and times its swings; the client interpolates its position and
 * plays the swing it is told about (see the client's WeaponPoses).
 */
public abstract class FlyingWeaponEntity extends Entity implements SwingingWeapon {
    protected static final int NO_TARGET = -1;
    private static final double SNAP_DISTANCE = 24;

    /** The locked-on target, which the weapon is chasing. */
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(FlyingWeaponEntity.class, EntityDataSerializers.INT);
    /** Packed swing: sequence number (bits 8+), upswing in 16ths (bits 4-7), mirrored (bit 3), style (bits 0-2). */
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(FlyingWeaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_DURATION = SynchedEntityData.defineId(FlyingWeaponEntity.class, EntityDataSerializers.INT);

    // Server state.
    @Nullable
    protected LivingEntity target;
    protected int cooldown;
    protected int combo;
    protected int impactTick = -1;

    // Client state, read by the renderers.
    public float engage;
    public float engageO;
    private int attackSeen = -1;
    public int attackStartTick = Integer.MIN_VALUE;
    public AttackStyle attackStyle = AttackStyle.SLASH_H;
    public boolean attackMirrored;
    public float attackUpswing = 0.5f;
    public int attackDuration = 1;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;

    protected FlyingWeaponEntity(EntityType<? extends FlyingWeaponEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_ID, NO_TARGET);
        builder.define(ATTACK, 0);
        builder.define(ATTACK_DURATION, 1);
    }

    public boolean hasTarget() {
        return entityData.get(TARGET_ID) != NO_TARGET;
    }

    @Nullable
    public LivingEntity getTarget() {
        return target;
    }

    protected void setTarget(@Nullable LivingEntity target) {
        this.target = target;
        entityData.set(TARGET_ID, target != null ? target.getId() : NO_TARGET);
    }

    // ---- Server flight and swings ----

    /** Beside the target, on the side facing {@code from}, with the grip at about the target's middle. */
    protected Vec3 strikePoint(LivingEntity target, Vec3 from) {
        Vec3 away = new Vec3(from.x - target.getX(), 0, from.z - target.getZ());
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(getX() - target.getX(), 0, getZ() - target.getZ());
        }
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        double reach = target.getBbWidth() / 2 + 1.0;
        return target.position().add(away.scale(reach)).add(0, target.getBbHeight() * 0.55 + 0.3, 0);
    }

    protected float yawTo(Entity entity) {
        return yawTo(entity.position());
    }

    protected float yawTo(Vec3 point) {
        return (float) (Mth.atan2(point.z - getZ(), point.x - getX()) * Mth.RAD_TO_DEG) - 90f;
    }

    protected void moveToward(Vec3 destination, float yaw, double speed) {
        Vec3 delta = destination.subtract(position());
        double distance = delta.length();
        Vec3 next = distance > SNAP_DISTANCE || distance <= speed ? destination : position().add(delta.scale(speed / distance));
        setPos(next.x, next.y, next.z);
        setYRot(Mth.approachDegrees(getYRot(), yaw, 30f));
    }

    /** Starts the next swing of the combo; its hit lands at {@link #impactTick}. */
    protected void beginAttack(WeaponProfile profile) {
        WeaponProfile.AttackStep step = profile.step(combo);
        // The swing fills most of the interval but never drags on for slow weapons.
        int duration = Mth.clamp(profile.intervalTicks() - 2, 6, 14);
        boolean mirrored = (combo & 1) == 1;
        combo++;
        cooldown = profile.intervalTicks();
        impactTick = tickCount + Math.max(1, Math.round(duration * step.upswing()));
        int upswing = Mth.clamp(Math.round(step.upswing() * 16), 0, 15);
        int seq = (entityData.get(ATTACK) >>> 8) + 1;
        entityData.set(ATTACK_DURATION, duration);
        entityData.set(ATTACK, seq << 8 | upswing << 4 | (mirrored ? 8 : 0) | step.style().ordinal());

        SoundEvent sound = step.swingSound() != null ? BuiltInRegistries.SOUND_EVENT.get(step.swingSound()) : null;
        if (sound != null) {
            playSound(sound, 0.4f, 1.15f + random.nextFloat() * 0.1f);
        } else {
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.3f, 1.4f + random.nextFloat() * 0.1f);
        }
    }

    // ---- Client ----

    /** Interpolates toward the server position, picks up new swings and eases {@link #engage}. */
    protected void clientTick() {
        if (lerpSteps > 0) {
            double f = 1.0 / lerpSteps;
            setPos(getX() + (lerpX - getX()) * f, getY() + (lerpY - getY()) * f, getZ() + (lerpZ - getZ()) * f);
            setYRot(getYRot() + Mth.wrapDegrees(lerpYRot - getYRot()) * (float) f);
            lerpSteps--;
        }

        int attack = entityData.get(ATTACK);
        if (attack >>> 8 != attackSeen) {
            if (attackSeen != -1 || attack >>> 8 != 0) {
                attackStartTick = tickCount;
                attackStyle = AttackStyle.byId(attack & 7);
                attackMirrored = (attack & 8) != 0;
                attackUpswing = Mth.clamp(((attack >> 4) & 15) / 16f, 0.1f, 0.9f);
                attackDuration = Math.max(1, entityData.get(ATTACK_DURATION));
            }
            attackSeen = attack >>> 8;
        }

        engageO = engage;
        engage = approach(engage, hasTarget() || isAttacking(0f) ? 1f : 0f, 0.2f);
    }

    @Override
    public float attackProgress(float partialTick) {
        float t = (tickCount + partialTick - attackStartTick) / attackDuration;
        return t >= 0f && t < 1f ? t : -1f;
    }

    @Override
    public AttackStyle attackStyle() {
        return attackStyle;
    }

    @Override
    public boolean attackMirrored() {
        return attackMirrored;
    }

    @Override
    public float attackUpswing() {
        return attackUpswing;
    }

    public boolean isAttacking(float partialTick) {
        return attackProgress(partialTick) >= 0f;
    }

    protected static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = 3;
    }

    // ---- Nothing touches it, nothing hurts it, it is never saved. ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
