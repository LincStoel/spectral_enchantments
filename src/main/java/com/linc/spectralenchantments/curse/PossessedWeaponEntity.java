package com.linc.spectralenchantments.curse;

import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.spectral.AttackStyle;
import com.linc.spectralenchantments.spectral.SwingingWeapon;
import com.linc.spectralenchantments.spectral.WeaponProfile;
import com.linc.spectralenchantments.spectral.WeaponStrike;
import java.util.EnumSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A weapon under the Curse of Possession, loose and hostile: it flies at players (the one who freed it first) and
 * swings itself at them with its own damage and enchantments. Its health is the durability it had left
 * ({@link Possession#maxHealth}); it is armored like a zombie. Saved with the world, so the weapon is never lost to
 * despawning.
 */
public class PossessedWeaponEntity extends Monster implements SwingingWeapon {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(PossessedWeaponEntity.class, EntityDataSerializers.ITEM_STACK);
    /** Packed swing, as in FlyingWeaponEntity: sequence (bits 8+), upswing in 16ths (4-7), mirrored (3), style (0-2). */
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(PossessedWeaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_DURATION = SynchedEntityData.defineId(PossessedWeaponEntity.class, EntityDataSerializers.INT);
    /** Grip to the middle of the item, where it sits in its hitbox. */
    private static final double HALF_HEIGHT = 0.45;

    // Server state.
    /** The real weapon. */
    private ItemStack stack = ItemStack.EMPTY;
    @Nullable
    private UUID victim;
    @Nullable
    private WeaponProfile profile;
    private int cooldown;
    private int combo;
    private int impactTick = -1;

    // Client state, read by the renderer.
    public float engage;
    public float engageO;
    private int attackSeen = -1;
    private int attackStartTick = Integer.MIN_VALUE;
    private AttackStyle attackStyle = AttackStyle.SLASH_H;
    private boolean attackMirrored;
    private float attackUpswing = 0.5f;
    private int attackDuration = 1;

    public PossessedWeaponEntity(EntityType<? extends PossessedWeaponEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(ATTACK, 0);
        builder.define(ATTACK_DURATION, 1);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new StrikeGoal(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomFlyingGoal(this, 0.6));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 12f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false,
                target -> victim == null || target.getUUID().equals(victim)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Sets up a freshly possessed weapon: its item, health, armor and first victim. */
    void possess(ItemStack weapon, Player player) {
        stack = weapon;
        victim = player.getUUID();
        entityData.set(ITEM, weapon.copyWithCount(1));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Possession.maxHealth(weapon));
        getAttribute(Attributes.ARMOR).setBaseValue(SpectralConfig.possessionArmor());
        setHealth(getMaxHealth());
        setPersistenceRequired();
    }

    public ItemStack getItem() {
        return entityData.get(ITEM);
    }

    /** The real weapon (server). */
    public ItemStack getWeapon() {
        return stack;
    }

    /** Named after its item: "Steve was slain by Diamond Sword". */
    @Override
    public Component getName() {
        ItemStack item = getItem();
        return hasCustomName() || item.isEmpty() ? super.getName() : item.getHoverName();
    }

    // ---- Fighting (server) ----

    /** Beside the target, on this side, with the item's middle at about the target's middle. */
    Vec3 strikePoint(LivingEntity target) {
        Vec3 away = new Vec3(getX() - target.getX(), 0, getZ() - target.getZ());
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        double reach = target.getBbWidth() / 2 + 1.0;
        return target.position().add(away.scale(reach)).add(0, target.getBbHeight() * 0.55 + 0.3 - HALF_HEIGHT, 0);
    }

    /** Starts the next swing of the combo, if it is ready; the hit lands a few ticks later. */
    void startSwing() {
        if (cooldown > 0 || impactTick >= 0) {
            return;
        }
        WeaponProfile profile = profile();
        WeaponProfile.AttackStep step = profile.step(combo);
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
        playSound(sound != null ? sound : SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 0.8f + random.nextFloat() * 0.1f);
    }

    private WeaponProfile profile() {
        if (profile == null) {
            WeaponProfile base = WeaponProfile.of(stack, 1.0);
            profile = new WeaponProfile(base.full(), base.baseDamage() * SpectralConfig.possessionDamageMultiplier(),
                    base.intervalTicks(), base.steps());
        }
        return profile;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (stack.isEmpty()) {
            discard();
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (impactTick >= 0 && tickCount >= impactTick) {
            impactTick = -1;
            LivingEntity target = getTarget();
            if (target != null && target.isAlive()
                    && getBoundingBox().getCenter().distanceTo(target.getBoundingBox().getCenter()) < target.getBbWidth() / 2 + 2.5) {
                setYRot((float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90f);
                WeaponProfile profile = profile();
                // A copy, so its hits don't wear down the weapon it may leave behind.
                WeaponStrike.hit(this, damageSources().mobAttack(this), stack.copy(), profile, profile.step(combo - 1),
                        target, this, null);
            }
        }
    }

    // ---- Death and dispelling ----

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!stack.isEmpty() && random.nextDouble() < SpectralConfig.possessionDropChance()) {
            spawnAtLocation(Possession.slainDrop(level, stack));
        }
    }

    /** Ars Nouveau's Dispel: the spirit is driven out and the weapon drops, curses lifted, as worn as it was. */
    public void dispel() {
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        Vec3 center = getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SOUL, center.x, center.y, center.z, 20, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.ENCHANT, center.x, center.y, center.z, 30, 0.4, 0.4, 0.4, 0.5);
        playSound(SoundEvents.SOUL_ESCAPE.value(), 1.0f, 1.0f);
        playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        if (!stack.isEmpty()) {
            spawnAtLocation(Possession.dispelledDrop(level, stack));
            stack = ItemStack.EMPTY;
        }
        discard();
    }

    // ---- A weapon, not a creature ----

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    protected boolean shouldDespawnInPeaceful() {
        // Its weapon would be lost; in peaceful it can't hurt anyone anyway.
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SOUL_ESCAPE.value();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CHAIN_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ITEM_BREAK;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!stack.isEmpty()) {
            tag.put("Weapon", stack.save(registryAccess()));
        }
        if (victim != null) {
            tag.putUUID("Victim", victim);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        stack = ItemStack.parseOptional(registryAccess(), tag.getCompound("Weapon"));
        entityData.set(ITEM, stack.copyWithCount(1));
        victim = tag.hasUUID("Victim") ? tag.getUUID("Victim") : null;
    }

    // ---- Client ----

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
        }
    }

    private void clientTick() {
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
        float goal = isAggressive() || attackProgress(0f) >= 0f ? 1f : 0f;
        engage = engage < goal ? Math.min(goal, engage + 0.2f) : Math.max(goal, engage - 0.2f);
        if (random.nextInt(4) == 0 && deathTime == 0) {
            Vec3 at = getBoundingBox().getCenter();
            level().addParticle(random.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.SOUL_FIRE_FLAME,
                    at.x + (random.nextDouble() - 0.5) * 0.4, at.y + (random.nextDouble() - 0.5) * 0.8,
                    at.z + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
        }
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

    /** Flies at its target and swings when beside it. */
    private static final class StrikeGoal extends Goal {
        private final PossessedWeaponEntity weapon;
        private int repath;

        StrikeGoal(PossessedWeaponEntity weapon) {
            this.weapon = weapon;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = weapon.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void start() {
            weapon.setAggressive(true);
            repath = 0;
        }

        @Override
        public void stop() {
            weapon.setAggressive(false);
            weapon.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = weapon.getTarget();
            if (target == null) {
                return;
            }
            Vec3 strike = weapon.strikePoint(target);
            weapon.getLookControl().setLookAt(target, 30f, 30f);
            double distance = weapon.position().distanceTo(strike);
            if (distance > 3.0 || !weapon.hasLineOfSight(target)) {
                if (--repath <= 0) {
                    repath = 5;
                    weapon.getNavigation().moveTo(strike.x, strike.y, strike.z, 1.0);
                }
            } else {
                weapon.getNavigation().stop();
                weapon.getMoveControl().setWantedPosition(strike.x, strike.y, strike.z, 1.0);
            }
            if (distance < 1.5) {
                weapon.startSwing();
            }
        }
    }
}
