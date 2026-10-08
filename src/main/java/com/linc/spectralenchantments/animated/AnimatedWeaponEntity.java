package com.linc.spectralenchantments.animated;

import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.spectral.FlyingWeaponEntity;
import com.linc.spectralenchantments.spectral.SpectralWeapons;
import com.linc.spectralenchantments.spectral.WeaponProfile;
import com.linc.spectralenchantments.spectral.WeaponStrike;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * An Animated weapon in flight: the real item, fighting on its own. The item itself never leaves the armor stand's
 * hand or the player's inventory (so nothing is lost if this entity vanishes); this entity shows it, flies it and
 * lands its hits, wearing down the real stack. Never saved. Driven by {@link AnimatedManager}.
 *
 * <ul>
 *   <li>Guarding: sent out by an armor stand after hostile mobs near it; returns to the stand's hand when none
 *       are left. Its kills are nobody's: no XP, no player-only loot.</li>
 *   <li>Deployed: left floating at a spot by a player, fighting hostile mobs that come near it until time runs out
 *       or it is recalled. Its kills are the player's.</li>
 *   <li>Thrown: a thrown or fired Animated weapon that landed, standing sentry where it came down. Unlike the others
 *       it holds the item itself (so it is saved with the world); it flies back into its owner's inventory when time
 *       runs out or they stray past the thrown recall distance, and drops the item if they are gone.</li>
 * </ul>
 */
public class AnimatedWeaponEntity extends FlyingWeaponEntity {
    public static final byte GUARDING = 0;
    public static final byte DEPLOYED = 1;
    public static final byte THROWN = 2;

    private static final double SEEK_SPEED = 0.6;
    private static final double RETURN_SPEED = 0.7;
    private static final double HIT_SLACK = 2.0;
    private static final double LEASH_SLACK = SpectralWeapons.LEASH_SLACK;
    /** Ticks between looks for a new target while it has none. */
    private static final int SEARCH_INTERVAL = 5;
    /** How long a loaded thrown weapon waits for its owner to turn up before dropping the item. */
    private static final int OWNER_GRACE_TICKS = 100;

    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(AnimatedWeaponEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(AnimatedWeaponEntity.class, EntityDataSerializers.BYTE);
    /** The armor stand (guarding) or player (deployed) the weapon belongs to. */
    private static final EntityDataAccessor<Integer> HOME_ID = SynchedEntityData.defineId(AnimatedWeaponEntity.class, EntityDataSerializers.INT);
    /** Guarding: the stand's hand (0 main, 1 off). Deployed: the player's inventory slot holding the item. */
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(AnimatedWeaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(AnimatedWeaponEntity.class, EntityDataSerializers.BOOLEAN);

    // Server state.
    @Nullable
    private ArmorStand stand;
    @Nullable
    private Player owner;
    /** The very stack being animated; if it leaves its holder, so does the weapon. */
    private ItemStack stack = ItemStack.EMPTY;
    private Vec3 anchor = Vec3.ZERO;
    private int deployTicksLeft;
    @Nullable
    private WeaponProfile profile;
    /** Thrown: whose weapon it is, kept across saves. */
    @Nullable
    private UUID ownerId;
    /** Thrown: it was fired from the off hand, so it goes back there if it can. */
    private boolean toOffhand;
    /** Thrown: a creative-mode copy, which vanishes rather than being handed back. */
    private boolean creativeCopy;
    private int ownerMissingTicks;

    public AnimatedWeaponEntity(EntityType<? extends AnimatedWeaponEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(MODE, GUARDING);
        builder.define(HOME_ID, -1);
        builder.define(SLOT, 0);
        builder.define(RETURNING, false);
    }

    // ---- Manager API (server) ----

    /** Sends the item in the armor stand's {@code hand} out after {@code target}. */
    void guard(ArmorStand stand, InteractionHand hand, LivingEntity target) {
        this.stand = stand;
        this.stack = stand.getItemInHand(hand);
        entityData.set(MODE, GUARDING);
        entityData.set(HOME_ID, stand.getId());
        entityData.set(SLOT, hand.ordinal());
        entityData.set(ITEM, displayCopy(stack));
        Vec3 grip = Animated.handPosition(stand, hand, 1f);
        moveTo(grip.x, grip.y, grip.z, stand.yBodyRot, 0f);
        setTarget(target);
        playSound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.4f, 1.6f);
    }

    /** Leaves the player's {@code slot} item floating at {@code at}. */
    void deploy(Player owner, int slot, Vec3 at) {
        this.owner = owner;
        this.stack = owner.getInventory().getItem(slot);
        this.anchor = at;
        this.deployTicksLeft = SpectralConfig.animatedDeployTicks();
        entityData.set(MODE, DEPLOYED);
        entityData.set(HOME_ID, owner.getId());
        entityData.set(SLOT, slot);
        entityData.set(ITEM, displayCopy(stack));
        Vec3 from = owner.getEyePosition().add(owner.getLookAngle().scale(0.5)).subtract(0, 0.3, 0);
        moveTo(from.x, from.y, from.z, owner.getYRot(), 0f);
        playSound(SoundEvents.TRIDENT_THROW.value(), 0.6f, 1.3f);
    }

    /** Stands sentry at {@code at} with {@code stack}, a thrown weapon that landed at {@code from}. */
    void sentry(Player owner, ItemStack stack, Vec3 from, Vec3 at, boolean toOffhand, boolean creativeCopy) {
        this.owner = owner;
        this.ownerId = owner.getUUID();
        this.stack = stack;
        this.anchor = at;
        this.toOffhand = toOffhand;
        this.creativeCopy = creativeCopy;
        this.deployTicksLeft = SpectralConfig.animatedDeployTicks();
        entityData.set(MODE, THROWN);
        entityData.set(HOME_ID, owner.getId());
        entityData.set(ITEM, displayCopy(stack));
        moveTo(from.x, from.y, from.z, owner.getYRot(), 0f);
        playSound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.5f, 1.4f);
    }

    /**
     * Flies back to its holder, done fighting. A recalled deployed weapon is the owner's again at once, though it
     * can't be swung for a moment.
     */
    public void recall() {
        if (!isReturning()) {
            entityData.set(RETURNING, true);
            setTarget(null);
            impactTick = -1;
            playSound(SoundEvents.TRIDENT_RETURN, 0.6f, 1.2f);
            int cooldown = SpectralConfig.animatedRecallCooldownTicks();
            if (isDeployed() && owner != null && cooldown > 0 && !stack.isEmpty()) {
                owner.getCooldowns().addCooldown(stack.getItem(), cooldown);
            }
        }
    }

    /** Whether this deployed weapon is animating exactly {@code candidate}. */
    public boolean isAnimating(ItemStack candidate) {
        return !candidate.isEmpty() && candidate == stack;
    }

    // ---- Synced accessors ----

    public ItemStack getItem() {
        return entityData.get(ITEM);
    }

    public boolean isDeployed() {
        return entityData.get(MODE) == DEPLOYED;
    }

    /** A thrown weapon standing sentry where it landed. */
    public boolean isThrown() {
        return entityData.get(MODE) == THROWN;
    }

    public boolean isReturning() {
        return entityData.get(RETURNING);
    }

    /** Deployed: the owner's inventory slot holding the item ({@link Inventory#SLOT_OFFHAND} for the off hand). */
    public int getSlot() {
        return entityData.get(SLOT);
    }

    /** Guarding: the armor stand's hand holding the item. */
    public InteractionHand getHand() {
        return entityData.get(SLOT) == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /** The armor stand (guarding) or player (deployed) the weapon belongs to. */
    @Nullable
    public Entity getHome() {
        Entity home = stand != null ? stand : owner;
        return home != null ? home : level().getEntity(entityData.get(HOME_ID));
    }

    @Override
    public ItemStack getWeaponItem() {
        return level().isClientSide ? getItem() : stack;
    }

    /** Named after its item: "Zombie was slain by Diamond Sword". */
    @Override
    public Component getName() {
        ItemStack item = getItem();
        return item.isEmpty() ? super.getName() : item.getHoverName();
    }

    // ---- Ticking ----

    @Override
    public void tick() {
        if (level().isClientSide) {
            clientTick();
        } else if (isDeployed()) {
            deployedTick();
        } else if (isThrown()) {
            thrownTick();
        } else {
            guardingTick();
        }
    }

    private void guardingTick() {
        if (stand == null || stand.isRemoved() || stand.level() != level()) {
            discard();
            return;
        }
        InteractionHand hand = getHand();
        // Taken from the stand, swapped, or broken: there is nothing left to fly.
        if (stand.getItemInHand(hand) != stack || !Animated.has(level(), stack)) {
            discard();
            return;
        }
        Vec3 center = Animated.standCenter(stand);
        double radius = SpectralConfig.animatedStandRadius();
        if (isReturning()) {
            // Something new turned up on the way home: back to work.
            if (tickCount % SEARCH_INTERVAL == 0) {
                LivingEntity found = Animated.findTarget(level(), center, radius, stand);
                if (found != null) {
                    entityData.set(RETURNING, false);
                    setTarget(found);
                }
            }
            if (isReturning()) {
                flyHome(Animated.handPosition(stand, hand, 1f), stand.yBodyRot);
                return;
            }
        }
        fight(center, radius, stand, true);
    }

    private void deployedTick() {
        if (owner == null || owner.isRemoved() || !owner.isAlive() || owner.level() != level()) {
            discard();
            return;
        }
        int slot = findSlot();
        if (slot < 0 || !Animated.has(level(), stack)) {
            // Moved out of the hotbar, dropped or broken: it is the player's again at once.
            recall();
        } else {
            entityData.set(SLOT, slot);
        }
        if (!isReturning() && (--deployTicksLeft <= 0
                || owner.position().distanceTo(anchor) > Animated.recallDistance(stack, false))) {
            recall();
        }
        if (isReturning()) {
            flyHome(owner.getEyePosition().subtract(0, 0.4, 0), owner.getYRot());
            return;
        }
        fight(anchor, SpectralConfig.animatedDeployRadius(), null, false);
    }

    private void thrownTick() {
        if (owner == null || owner.isRemoved()) {
            owner = ownerId != null ? level().getPlayerByUUID(ownerId) : null;
            if (owner == null) {
                // Its owner left (or hasn't joined since the world loaded): leave the weapon where they can find it.
                if (++ownerMissingTicks > OWNER_GRACE_TICKS || ownerId == null) {
                    dropItem();
                }
                return;
            }
            entityData.set(HOME_ID, owner.getId());
        }
        ownerMissingTicks = 0;
        if (!owner.isAlive() || owner.level() != level() || stack.isEmpty()) {
            dropItem();
            return;
        }
        if (!isReturning() && (--deployTicksLeft <= 0 || !Animated.has(level(), stack)
                || owner.position().distanceTo(anchor) > Animated.recallDistance(stack, true))) {
            recall();
        }
        if (isReturning()) {
            flyHome(owner.getEyePosition().subtract(0, 0.4, 0), owner.getYRot());
            return;
        }
        fight(anchor, SpectralConfig.animatedDeployRadius(), null, false);
    }

    /** Thrown: back to the owner (the off hand if it was fired from there), else their inventory, else at their feet. */
    private void handBack() {
        if (creativeCopy || stack.isEmpty() || owner == null) {
            return;
        }
        if (toOffhand && owner.getOffhandItem().isEmpty()) {
            owner.setItemInHand(InteractionHand.OFF_HAND, stack);
        } else if (!owner.getInventory().add(stack)) {
            owner.drop(stack, false);
        }
        stack = ItemStack.EMPTY;
    }

    /** Thrown: leaves the item lying where the weapon is. */
    private void dropItem() {
        if (!creativeCopy && !stack.isEmpty()) {
            spawnAtLocation(stack, 0f);
        }
        stack = ItemStack.EMPTY;
        discard();
    }

    /** The hotbar slot (or the off hand) still holding the deployed stack, or -1 if it has left them. */
    private int findSlot() {
        Inventory inventory = owner.getInventory();
        int last = getSlot();
        if ((last == Inventory.SLOT_OFFHAND || Inventory.isHotbarSlot(last)) && inventory.getItem(last) == stack) {
            return last;
        }
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (inventory.getItem(slot) == stack) {
                return slot;
            }
        }
        return inventory.getItem(Inventory.SLOT_OFFHAND) == stack ? Inventory.SLOT_OFFHAND : -1;
    }

    /**
     * Chases and strikes its target, picking the nearest one in sight of {@code center} whenever it has none.
     * With nothing to fight, a guarding weapon heads home and a deployed one hovers at its anchor.
     */
    private void fight(Vec3 center, double radius, @Nullable Entity viewer, boolean returnWhenIdle) {
        boolean lost = false;
        if (target != null && (!target.isAlive() || target.isRemoved() || target.level() != level()
                || !Animated.isValidTarget(target) || !Animated.inRange(center, target, radius, LEASH_SLACK))) {
            setTarget(null);
            lost = true;
        }
        // A guarding weapon looks again before heading home; a hovering one looks every few ticks.
        if (target == null && impactTick < 0 && (returnWhenIdle || lost || tickCount % SEARCH_INTERVAL == 0)) {
            setTarget(Animated.findTarget(level(), center, radius, viewer));
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (profile == null) {
            profile = WeaponProfile.of(stack, 1.0);
        }

        if (target != null) {
            Vec3 strike = strikePoint(target, center);
            moveToward(strike, yawTo(target), SEEK_SPEED);
            if (cooldown <= 0 && impactTick < 0 && position().distanceTo(strike) < 1.0) {
                beginAttack(profile);
            }
        } else if (impactTick < 0) {
            if (returnWhenIdle) {
                recall();
            } else {
                moveToward(anchor, getYRot() + 4f, Math.max(0.15, position().distanceTo(anchor) * 0.3));
            }
        }

        if (impactTick >= 0 && tickCount >= impactTick) {
            impactTick = -1;
            if (target != null && position().distanceTo(target.getBoundingBox().getCenter()) < target.getBbWidth() / 2 + 1.0 + HIT_SLACK) {
                strike(target);
            }
        }
    }

    private void strike(LivingEntity target) {
        WeaponProfile.AttackStep step = profile.step(combo - 1);
        if (isDeployed() || isThrown()) {
            // The player's attack: their kill, their XP, their loot (with the weapon's Looting).
            WeaponStrike.hit(this, damageSource(DamageTypes.PLAYER_ATTACK, owner), stack, profile, step, target, owner, owner);
        } else {
            // Nobody's attack: no XP and no player-only drops. The stand wears the item down.
            WeaponStrike.hit(this, damageSource(DamageTypes.MOB_ATTACK_NO_AGGRO, null), stack, profile, step, target, stand, null);
        }
    }

    private DamageSource damageSource(ResourceKey<DamageType> type, @Nullable Entity credited) {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), this, credited);
    }

    private void flyHome(Vec3 home, float yaw) {
        double distance = position().distanceTo(home);
        if (distance < 0.35) {
            if (isDeployed() || isThrown()) {
                playSound(SoundEvents.ITEM_PICKUP, 0.3f, 1.4f);
            }
            if (isThrown()) {
                handBack();
            }
            discard();
            return;
        }
        moveToward(home, distance < 2 ? yaw : yawTo(home), Math.max(RETURN_SPEED, distance * 0.3));
    }

    /** Thrown weapons hold the real item, so they are saved; the others are only ever shown. */
    @Override
    public boolean shouldBeSaved() {
        return isThrown() && !stack.isEmpty() && (getRemovalReason() == null || getRemovalReason().shouldSave()) && !isPassenger();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (isThrown() && !stack.isEmpty()) {
            tag.put("Weapon", stack.save(registryAccess()));
            if (ownerId != null) {
                tag.putUUID("Owner", ownerId);
            }
            tag.putDouble("AnchorX", anchor.x);
            tag.putDouble("AnchorY", anchor.y);
            tag.putDouble("AnchorZ", anchor.z);
            tag.putInt("TicksLeft", deployTicksLeft);
            tag.putBoolean("ToOffhand", toOffhand);
            tag.putBoolean("Returning", isReturning());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Weapon")) {
            stack = ItemStack.parseOptional(registryAccess(), tag.getCompound("Weapon"));
            ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
            anchor = new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ"));
            deployTicksLeft = tag.getInt("TicksLeft");
            toOffhand = tag.getBoolean("ToOffhand");
            entityData.set(MODE, THROWN);
            entityData.set(ITEM, displayCopy(stack));
            entityData.set(RETURNING, tag.getBoolean("Returning"));
        }
    }

    /** Render copy: one item, durability stripped (the real item flies, glint and all). */
    private static ItemStack displayCopy(ItemStack stack) {
        ItemStack copy = stack.copyWithCount(1);
        copy.remove(DataComponents.DAMAGE);
        return copy;
    }
}
