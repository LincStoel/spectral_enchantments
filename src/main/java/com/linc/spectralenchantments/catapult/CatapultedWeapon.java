package com.linc.spectralenchantments.catapult;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.projectile.ItemArrow;
import com.linc.spectralenchantments.spectral.WeaponProfile;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A weapon (or tool) a Catapulting bow fired from the off hand. Flies like an arrow, point first, and sticks where it
 * lands to be picked up again. Hits for the weapon's own attack damage (with its enchantments), scaled by the draw.
 *
 * <p>Comes back like a thrown trident when it carries Loyalty; one carrying Animated stands guard where it lands (see
 * AnimatedManager#landed). Mirrors ThrownTrident; never despawns while it can still be picked up.
 */
public class CatapultedWeapon extends ItemArrow {
    private static final EntityDataAccessor<Byte> RETURN_SPEED = SynchedEntityData.defineId(CatapultedWeapon.class, EntityDataSerializers.BYTE);

    /** How far the bow was drawn (0-1): a full draw hits for the weapon's full damage. */
    private float power = 1f;
    private boolean dealtDamage;
    private int returnTicks;

    public CatapultedWeapon(EntityType<? extends CatapultedWeapon> type, Level level) {
        super(type, level);
    }

    public CatapultedWeapon(Level level, LivingEntity owner, ItemStack weapon, ItemStack bow, float power) {
        super(ModEntities.CATAPULTED_WEAPON.get(), owner, level, weapon, bow);
        this.power = power;
        setItem(weapon);
        updateReturn();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RETURN_SPEED, (byte) 0);
    }

    private void updateReturn() {
        if (level() instanceof ServerLevel level) {
            ItemStack stack = getPickupItemStackOrigin();
            int loyalty = EnchantmentHelper.getTridentReturnToOwnerAcceleration(level, stack, this);
            entityData.set(RETURN_SPEED, (byte) Mth.clamp(loyalty, 0, 127));
        }
    }

    public boolean isReturning() {
        return isNoPhysics() && entityData.get(RETURN_SPEED) > 0;
    }

    public boolean isStuck() {
        return inGround;
    }

    @Override
    public void tick() {
        if (inGroundTime > 4) {
            dealtDamage = true;
        }
        Entity owner = getOwner();
        int speed = entityData.get(RETURN_SPEED);
        if (speed > 0 && (dealtDamage || isNoPhysics()) && owner != null) {
            if (!isAcceptableReturnOwner(owner)) {
                if (!level().isClientSide && pickup == Pickup.ALLOWED) {
                    spawnAtLocation(getPickupItem(), 0.1f);
                }
                discard();
            } else {
                setNoPhysics(true);
                Vec3 toOwner = owner.getEyePosition().subtract(position());
                setPosRaw(getX(), getY() + toOwner.y * 0.015 * speed, getZ());
                if (level().isClientSide) {
                    yOld = getY();
                }
                setDeltaMovement(getDeltaMovement().scale(0.95).add(toOwner.normalize().scale(0.05 * speed)));
                if (returnTicks == 0) {
                    playSound(SoundEvents.TRIDENT_RETURN, 10f, 1f);
                }
                returnTicks++;
            }
        }
        super.tick();
    }

    private static boolean isAcceptableReturnOwner(Entity owner) {
        return owner.isAlive() && !(owner instanceof ServerPlayer && owner.isSpectator());
    }

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
        return dealtDamage ? null : super.findHitEntity(start, end);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        ItemStack weapon = getWeaponItem();
        Entity owner = getOwner();
        DamageSource source = damageSources().arrow(this, owner == null ? this : owner);
        float damage = WeaponProfile.attackDamage(weapon) * power;
        if (level() instanceof ServerLevel level) {
            damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damage);
        }
        if (owner instanceof LivingEntity living) {
            living.setLastHurtMob(target);
        }
        boolean enderman = target.getType() == EntityType.ENDERMAN;
        int fireTicks = target.getRemainingFireTicks();
        if (isOnFire() && !enderman) {
            target.igniteForSeconds(5f);
        }

        dealtDamage = true;
        if (target.hurt(source, damage)) {
            if (enderman) {
                return;
            }
            if (level() instanceof ServerLevel level) {
                EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);
            }
            if (target instanceof LivingEntity living) {
                doKnockback(living, source);
                doPostHurtEffects(living);
            }
        } else {
            target.setRemainingFireTicks(fireTicks);
        }
        setDeltaMovement(getDeltaMovement().multiply(-0.01, -0.1, -0.01));
        playSound(SoundEvents.TRIDENT_HIT, 1f, 1f);
    }

    /** The weapon itself, so its enchantments (not the bow's) act on what it hits. */
    @Override
    public ItemStack getWeaponItem() {
        return getPickupItemStackOrigin();
    }

    /**
     * The owner takes the weapon back into their off hand, ready to fire again, when it's free (whether it came back
     * to them or they picked it up); otherwise it goes into their inventory.
     */
    @Override
    protected boolean tryPickup(Player player) {
        if (ownedBy(player) && pickup == Pickup.ALLOWED && player.getOffhandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, getPickupItem());
            return true;
        }
        return super.tryPickup(player) || isNoPhysics() && ownedBy(player) && player.getInventory().add(getPickupItem());
    }

    /** Only the owner (or anyone, once the owner is gone) can take it back, as with a trident. */
    @Override
    public void playerTouch(Player player) {
        if (ownedBy(player) || getOwner() == null) {
            super.playerTouch(player);
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.IRON_SWORD);
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dealtDamage = tag.getBoolean("DealtDamage");
        power = tag.contains("Power") ? tag.getFloat("Power") : 1f;
        setItem(getPickupItemStackOrigin());
        updateReturn();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DealtDamage", dealtDamage);
        tag.putFloat("Power", power);
    }

    /** A weapon left lying about is never lost to despawning (unless it's a creative-mode copy). */
    @Override
    protected void tickDespawn() {
        if (pickup != Pickup.ALLOWED) {
            super.tickDespawn();
        }
    }

    @Override
    protected float getWaterInertia() {
        return 0.99f;
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return true;
    }
}
