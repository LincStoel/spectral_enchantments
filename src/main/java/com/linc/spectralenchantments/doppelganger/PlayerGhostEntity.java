package com.linc.spectralenchantments.doppelganger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A ghostly copy of its owner, a player drawn in their (whitened) skin or (for a Curse of Haunting) a monster: a
 * Doppelganger's double or a Curse of Haunting's ghost.
 * Passes through blocks, never falls, pushes nothing and is never saved; vanishes in a puff of smoke.
 */
public abstract class PlayerGhostEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(PlayerGhostEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** The enchanted armor piece, so the ghost takes the colour of a Quark rune etched on it. */
    private static final EntityDataAccessor<ItemStack> TINT_ITEM =
            SynchedEntityData.defineId(PlayerGhostEntity.class, EntityDataSerializers.ITEM_STACK);
    /** 0 until the first emote; each new value starts a new one. */
    private static final EntityDataAccessor<Integer> EMOTE_SEED =
            SynchedEntityData.defineId(PlayerGhostEntity.class, EntityDataSerializers.INT);

    /** Server only. */
    @Nullable
    private LivingEntity owner;

    protected PlayerGhostEntity(EntityType<? extends PlayerGhostEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Ties a new ghost to {@code owner}, tinted after {@code tintItem}, facing {@code yaw}. */
    protected void bind(LivingEntity owner, ItemStack tintItem, float yaw) {
        this.owner = owner;
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(TINT_ITEM, tintItem.copyWithCount(1));
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(TINT_ITEM, ItemStack.EMPTY);
        builder.define(EMOTE_SEED, 0);
    }

    @Nullable
    public UUID ownerId() {
        return entityData.get(OWNER).orElse(null);
    }

    /** Server only. */
    @Nullable
    public LivingEntity owner() {
        return owner;
    }

    public ItemStack tintItem() {
        return entityData.get(TINT_ITEM);
    }

    public int emoteSeed() {
        return entityData.get(EMOTE_SEED);
    }

    protected void setEmoteSeed(int seed) {
        entityData.set(EMOTE_SEED, seed);
    }

    /** Whether the renderer turns the whole ghost to face where it looks, rather than idly looking about. */
    public boolean stares() {
        return false;
    }

    /** Vanishes in a puff of white smoke (not when it's merely unloaded with its chunk). */
    @Override
    public void remove(RemovalReason reason) {
        if (!isRemoved() && reason.shouldDestroy() && puffsOnRemoval() && level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 24,
                    getBbWidth() / 2, getBbHeight() / 3, getBbWidth() / 2, 0.02);
        }
        super.remove(reason);
    }

    /** Whether it vanishes in a puff of smoke rather than having already faded away. */
    protected boolean puffsOnRemoval() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {}

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean isSilent() {
        return true;
    }

    @Override
    public boolean dampensVibrations() {
        return true;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {}

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
