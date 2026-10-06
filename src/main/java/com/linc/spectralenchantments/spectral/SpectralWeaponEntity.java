package com.linc.spectralenchantments.spectral;

import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The ghostly copy of a Spectral Weapon item. Never saved; {@link SpectralWeaponManager} spawns one
 * per player and keeps it pointed at the right inventory slot. It locks on to the next thing its owner
 * hits and fights it until it dies or leaves its reach (or the weapon is dismissed). The server flies it and lands the hits;
 * the client only fades, tilts and animates it (see the client's SpectralWeaponRenderer).
 */
public class SpectralWeaponEntity extends Entity {
    public static final int FADE_TICKS = 8;
    private static final int NO_TARGET = -1;
    private static final double SEEK_SPEED = 0.6;
    private static final double SNAP_DISTANCE = 24;
    /** The hit lands if the target is still this close to the weapon when the swing connects. */
    private static final double HIT_SLACK = 2.0;
    /** Ticks without a {@link #claim} from the manager before the weapon dismisses itself. */
    private static final int ORPHAN_TICKS = 5;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FADING = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.BOOLEAN);
    /** The locked-on target, which the weapon is chasing. */
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.INT);
    /** Packed swing: sequence number (bits 8+), upswing in 16ths (bits 4-7), mirrored (bit 3), style (bits 0-2). */
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_DURATION = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.INT);

    // Server state.
    @Nullable
    private Player owner;
    @Nullable
    private WeaponProfile profile;
    /** Locked on until it dies or leaves the weapon's reach, or the weapon is dismissed. */
    @Nullable
    private LivingEntity target;
    private int cooldown = FADE_TICKS;
    private int combo;
    private int impactTick = -1;
    private int fadingTicks;
    private int lastClaimTick;

    // Client state, read by the renderer.
    public float fade;
    public float fadeO;
    public float tilt;
    public float tiltO;
    public float tuck;
    public float tuckO;
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

    public SpectralWeaponEntity(EntityType<? extends SpectralWeaponEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ID, -1);
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(SLOT, 0);
        builder.define(FADING, false);
        builder.define(TARGET_ID, NO_TARGET);
        builder.define(ATTACK, 0);
        builder.define(ATTACK_DURATION, 1);
    }

    // ---- Manager API (server) ----

    void bind(Player owner, SpectralWeapons.Selection selection) {
        this.owner = owner;
        entityData.set(OWNER_ID, owner.getId());
        update(selection);
        Vec3 anchor = SpectralWeapons.restAnchor(owner, 1f, 0f);
        moveTo(anchor.x, anchor.y, anchor.z, owner.yBodyRot, 0f);
        claim();
    }

    /** Points the weapon at the (possibly changed) stack in its slot. */
    void update(SpectralWeapons.Selection selection) {
        entityData.set(SLOT, selection.slot());
        ItemStack display = displayCopy(selection.stack());
        if (!ItemStack.isSameItemSameComponents(display, entityData.get(ITEM))) {
            entityData.set(ITEM, display);
            profile = null;
        }
        if (isFading()) {
            // Came back before it finished vanishing (e.g. the player stopped sneaking).
            entityData.set(FADING, false);
            fadingTicks = 0;
            cooldown = Math.max(cooldown, FADE_TICKS);
        }
    }

    void claim() {
        lastClaimTick = tickCount;
    }

    boolean showsSameItem(SpectralWeapons.Selection selection) {
        return selection.slot() == getSlot() && selection.stack().getItem() == entityData.get(ITEM).getItem();
    }

    /**
     * Locks on to something the owner just hit, unless the weapon is already locked on to something
     * else, fading, or the target is out of its reach.
     */
    boolean lockOn(LivingEntity entity) {
        boolean locked = target != null && target.isAlive() && !target.isRemoved();
        if (owner == null || locked || isFading() || entity.level() != level()
                || !SpectralWeapons.canLockOn(owner, entity)) {
            return false;
        }
        int level = SpectralWeapons.getLevel(level(), getWeaponItem());
        if (level <= 0 || !inReach(entity, SpectralWeapons.radius(level))) {
            return false;
        }
        setTarget(entity);
        return true;
    }

    public void startFadeOut() {
        if (!isFading()) {
            entityData.set(FADING, true);
            fadingTicks = 0;
            setTarget(null);
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.35f, 1.6f);
        }
    }

    public boolean isFading() {
        return entityData.get(FADING);
    }

    // ---- Synced accessors ----

    public ItemStack getItem() {
        return entityData.get(ITEM);
    }

    /** The inventory slot the weapon manifests from ({@link net.minecraft.world.entity.player.Inventory#SLOT_OFFHAND} for the off hand). */
    public int getSlot() {
        return entityData.get(SLOT);
    }

    @Nullable
    public Player getOwner() {
        if (owner != null) {
            return owner;
        }
        return level().getEntity(entityData.get(OWNER_ID)) instanceof Player player ? player : null;
    }

    public boolean hasTarget() {
        return entityData.get(TARGET_ID) != NO_TARGET;
    }

    @Nullable
    public LivingEntity getTarget() {
        return target;
    }

    @Override
    public ItemStack getWeaponItem() {
        if (owner != null && !level().isClientSide) {
            return owner.getInventory().getItem(getSlot());
        }
        return getItem();
    }

    // ---- Ticking ----

    @Override
    public void tick() {
        if (level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    private void serverTick() {
        if (owner == null || owner.isRemoved() || owner.level() != level() || tickCount - lastClaimTick > ORPHAN_TICKS) {
            if (!isFading()) {
                startFadeOut();
            }
            if (owner == null || owner.isRemoved() || owner.level() != level()) {
                discard();
                return;
            }
        }
        if (isFading()) {
            if (++fadingTicks > FADE_TICKS + 2) {
                discard();
                return;
            }
            moveToward(SpectralWeapons.restAnchor(owner, 1f, 0f), owner.yBodyRot, SEEK_SPEED);
            return;
        }

        ItemStack stack = owner.getInventory().getItem(getSlot());
        int level = SpectralWeapons.getLevel(level(), stack);
        if (level <= 0) {
            startFadeOut();
            return;
        }
        if (profile == null) {
            profile = WeaponProfile.of(stack);
        }
        if (target != null && (!target.isAlive() || target.isRemoved() || target.level() != level()
                || !inReach(target, SpectralWeapons.radius(level)))) {
            // Gone, or out of reach: let go, so the owner's next hit picks a new target.
            setTarget(null);
        }
        if (cooldown > 0) {
            cooldown--;
        }

        if (target != null) {
            Vec3 strike = strikePoint(target);
            moveToward(strike, yawTo(target), SEEK_SPEED);
            if (cooldown <= 0 && impactTick < 0 && position().distanceTo(strike) < 1.0) {
                beginAttack(profile);
            }
        } else {
            Vec3 rest = SpectralWeapons.restAnchor(owner, 1f, 0f);
            moveToward(rest, owner.yBodyRot, Math.max(SEEK_SPEED, position().distanceTo(rest) * 0.4));
        }

        if (impactTick >= 0 && tickCount >= impactTick) {
            impactTick = -1;
            if (target != null && position().distanceTo(target.getBoundingBox().getCenter()) < target.getBbWidth() / 2 + 1.0 + HIT_SLACK) {
                SpectralWeaponManager.strike(this, owner, stack, profile, profile.step(combo - 1), target);
            }
        }
    }

    /** Within the search radius of the owner (plus a little slack, so it doesn't stall at the edge). */
    private boolean inReach(LivingEntity entity, double radius) {
        return entity.distanceTo(owner) - entity.getBbWidth() / 2 <= radius + SpectralWeapons.LEASH_SLACK;
    }

    private void setTarget(@Nullable LivingEntity target) {
        this.target = target;
        entityData.set(TARGET_ID, target != null ? target.getId() : NO_TARGET);
    }

    /** Beside the target, on the owner's side of it, with the grip at about the target's middle. */
    private Vec3 strikePoint(LivingEntity target) {
        Vec3 away = new Vec3(owner.getX() - target.getX(), 0, owner.getZ() - target.getZ());
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(getX() - target.getX(), 0, getZ() - target.getZ());
        }
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        double reach = target.getBbWidth() / 2 + 1.0;
        return target.position().add(away.scale(reach)).add(0, target.getBbHeight() * 0.55 + 0.3, 0);
    }

    private float yawTo(Entity entity) {
        return (float) (Mth.atan2(entity.getZ() - getZ(), entity.getX() - getX()) * Mth.RAD_TO_DEG) - 90f;
    }

    private void moveToward(Vec3 destination, float yaw, double speed) {
        Vec3 delta = destination.subtract(position());
        double distance = delta.length();
        Vec3 next = distance > SNAP_DISTANCE || distance <= speed ? destination : position().add(delta.scale(speed / distance));
        setPos(next.x, next.y, next.z);
        setYRot(Mth.approachDegrees(getYRot(), yaw, 30f));
    }

    private void beginAttack(WeaponProfile profile) {
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

    /** Damage source for a spectral hit: the weapon strikes directly, the owner gets the credit (and the player-kill loot). */
    DamageSource damageSource(Player owner) {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.PLAYER_ATTACK), this, owner);
    }

    private void clientTick() {
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

        fadeO = fade;
        fade = approach(fade, isFading() ? 0f : 1f, 1f / FADE_TICKS);
        engageO = engage;
        engage = approach(engage, hasTarget() || isAttacking(0f) ? 1f : 0f, 0.2f);

        tiltO = tilt;
        tuckO = tuck;
        Player player = getOwner();
        if (player != null) {
            double speed = Math.sqrt(Mth.square(player.getX() - player.xo) + Mth.square(player.getZ() - player.zo));
            boolean sprinting = player.isSprinting() && speed > 0.05;
            float targetTilt = sprinting ? 55f : speed > 0.03 ? 20f : 0f;
            tilt += (targetTilt - tilt) * 0.15f;
            tuck += ((sprinting ? 1f : 0f) - tuck) * 0.15f;
        }
    }

    /** Swing progress 0-1, or a negative value when not swinging. */
    public float attackProgress(float partialTick) {
        float t = (tickCount + partialTick - attackStartTick) / attackDuration;
        return t >= 0f && t < 1f ? t : -1f;
    }

    public boolean isAttacking(float partialTick) {
        return attackProgress(partialTick) >= 0f;
    }

    private static float approach(float value, float target, float step) {
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

    // ---- A ghost: nothing touches it, nothing hurts it, it is never saved. ----

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

    /** Render copy: one item, durability and glint stripped (glint quads would draw as a second ghost). */
    private static ItemStack displayCopy(ItemStack stack) {
        ItemStack copy = stack.copyWithCount(1);
        copy.remove(DataComponents.DAMAGE);
        copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        return copy;
    }
}
