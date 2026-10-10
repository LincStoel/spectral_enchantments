package com.linc.spectralenchantments.animated;

import com.linc.spectralenchantments.ModComponents;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.StackCooldowns;
import com.linc.spectralenchantments.spectral.FlyingWeaponEntity;
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
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An Animated weapon in flight: the real item, fighting on its own. The item itself never leaves the armor stand's
 * hand or the player's inventory (so nothing is lost if this entity vanishes); this entity shows it, flies it and
 * lands its hits, wearing down the real stack. Never saved. Driven by {@link AnimatedManager}.
 *
 * <ul>
 *   <li>Companion: an Animated weapon in the player's hotbar, floating at their side. It locks on to the next thing the
 *       player hits and fights it until it dies or leaves its reach; sneaking sends it home. Its kills are the player's.</li>
 *   <li>Guarding: sent out by an armor stand after hostile mobs near it; returns to the stand's hand when none
 *       are left. Its kills are nobody's: no XP, no player-only loot.</li>
 *   <li>Deployed: left floating at a spot by a player, fighting hostile mobs that come near it until time runs out
 *       or it is recalled. Its kills are the player's.</li>
 *   <li>Thrown: a thrown or fired Animated weapon that landed, standing sentry where it came down. Unlike the others
 *       it holds the item itself (so it is saved with the world); it flies back into its owner's inventory when time
 *       runs out or they stray past the thrown recall distance, and waits where it is while they are away.</li>
 * </ul>
 */
public class AnimatedWeaponEntity extends FlyingWeaponEntity {
    public static final byte GUARDING = 0;
    public static final byte DEPLOYED = 1;
    public static final byte THROWN = 2;
    public static final byte COMPANION = 3;

    private static final double SEEK_SPEED = 0.6;
    private static final double RETURN_SPEED = 0.7;
    private static final double HIT_SLACK = 2.0;
    private static final double LEASH_SLACK = Animated.LEASH_SLACK;
    /** A companion swings at this multiple of the wielder's attack interval: half speed. */
    private static final double COMPANION_INTERVAL_SCALE = 2.0;
    /** A companion that has just appeared waits this long before its first swing. */
    private static final int COMPANION_START_COOLDOWN = 8;
    /** Ticks without a {@link #claim} from the manager before a companion goes home by itself. */
    private static final int ORPHAN_TICKS = 5;
    /** Ticks between looks for a new target while it has none. */
    private static final int SEARCH_INTERVAL = 5;
    /** An idle companion further than this from its rest spot catches up, until it's within {@link #PARKED_DISTANCE}. */
    private static final double PARK_DISTANCE = 1.25;
    private static final double PARKED_DISTANCE = 0.25;

    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(AnimatedWeaponEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(AnimatedWeaponEntity.class, EntityDataSerializers.BYTE);
    /** The armor stand (guarding) or player (deployed, thrown, companion) the weapon belongs to. */
    private static final EntityDataAccessor<Integer> HOME_ID = SynchedEntityData.defineId(AnimatedWeaponEntity.class, EntityDataSerializers.INT);
    /** Guarding: the stand's hand (0 main, 1 off). Deployed or companion: the player's inventory slot holding the item. */
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
    /** Companion: the last tick the manager still wanted it out. */
    private int lastClaimTick;
    /** Companion with nothing to fight: closing in on its rest spot, rather than parked near it. */
    private boolean catchingUp = true;

    // Client state, read by the renderer: how far the hung weapon tilts and tucks in while its owner runs.
    public float tilt;
    public float tiltO;
    public float tuck;
    public float tuckO;

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

    /** Brings the player's hotbar {@code selection} out to fight beside them. */
    void companion(Player owner, Animated.Selection selection) {
        this.owner = owner;
        entityData.set(MODE, COMPANION);
        entityData.set(HOME_ID, owner.getId());
        cooldown = COMPANION_START_COOLDOWN;
        rebind(selection);
        Vec3 rest = Animated.restAnchor(owner, 1f, 0f);
        moveTo(rest.x, rest.y, rest.z, owner.yBodyRot, 0f);
        claim();
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.3f);
    }

    /** Points a companion at the (possibly changed) stack in its slot, and has it back out if it was heading home. */
    void rebind(Animated.Selection selection) {
        this.stack = selection.stack();
        entityData.set(SLOT, selection.slot());
        ItemStack display = displayCopy(stack);
        if (!ItemStack.isSameItemSameComponents(display, entityData.get(ITEM))) {
            entityData.set(ITEM, display);
            profile = null;
        }
        if (isReturning()) {
            entityData.set(RETURNING, false);
            cooldown = Math.max(cooldown, COMPANION_START_COOLDOWN);
        }
    }

    /** The manager still wants this companion out. */
    void claim() {
        lastClaimTick = tickCount;
    }

    boolean showsSameItem(Animated.Selection selection) {
        return selection.slot() == getSlot() && selection.stack().getItem() == entityData.get(ITEM).getItem();
    }

    /**
     * A companion locks on to something its owner just hit, unless it is already locked on to something else, on
     * its way home, or the target is out of its reach.
     */
    boolean lockOn(LivingEntity entity) {
        boolean locked = target != null && target.isAlive() && !target.isRemoved();
        if (!isCompanion() || owner == null || locked || isReturning() || entity.level() != level()
                || !Animated.canLockOn(owner, entity)
                || !Animated.inRange(owner.position(), entity, Animated.companionRange(), LEASH_SLACK)) {
            return false;
        }
        setTarget(entity);
        return true;
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
            if (isCompanion()) {
                playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.35f, 1.6f);
            } else {
                playSound(SoundEvents.TRIDENT_RETURN, 0.6f, 1.2f);
            }
            int cooldown = SpectralConfig.animatedRecallCooldownTicks();
            if (isDeployed() && owner != null && cooldown > 0 && !stack.isEmpty()) {
                StackCooldowns.start(stack, level(), cooldown);
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

    /** A weapon from the player's hotbar, fighting beside them. */
    public boolean isCompanion() {
        return entityData.get(MODE) == COMPANION;
    }

    public boolean isReturning() {
        return entityData.get(RETURNING);
    }

    /** Deployed or companion: the owner's inventory slot holding the item ({@link Inventory#SLOT_OFFHAND} for the off hand). */
    public int getSlot() {
        return entityData.get(SLOT);
    }

    /** Guarding: the armor stand's hand holding the item. */
    public InteractionHand getHand() {
        return entityData.get(SLOT) == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /** The player a deployed, thrown or companion weapon belongs to. */
    @Nullable
    public Player getOwner() {
        return !isGuarding() && getHome() instanceof Player player ? player : null;
    }

    private boolean isGuarding() {
        return entityData.get(MODE) == GUARDING;
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
        } else if (isCompanion()) {
            companionTick();
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
        fight(center, radius, stand, Idle.RETURN);
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
        fight(anchor, SpectralConfig.animatedDeployRadius(), null, Idle.HOVER);
    }

    private void companionTick() {
        if (owner == null || owner.isRemoved() || owner.level() != level()) {
            discard();
            return;
        }
        if (!isReturning() && tickCount - lastClaimTick > ORPHAN_TICKS) {
            recall();
        }
        if (isReturning()) {
            flyHome(owner.getEyePosition().subtract(0, 0.4, 0), owner.getYRot());
            return;
        }
        fight(owner.position(), Animated.companionRange(), null, Idle.FOLLOW);
    }

    private void thrownTick() {
        if (stack.isEmpty() || ownerId == null) {
            dropItem();
            return;
        }
        if (owner == null || owner.isRemoved()) {
            // Gone (logged out, respawned, or not joined since the world loaded): look for them again in this level.
            owner = level().getPlayerByUUID(ownerId);
            if (owner != null) {
                entityData.set(HOME_ID, owner.getId());
            }
        }
        if (owner == null || !owner.isAlive() || owner.level() != level()) {
            // Its owner is away, dead or in another dimension: wait at the spot, saved with the world, until they're
            // back. Never dropped as an item, which would despawn.
            waitForOwner();
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
        fight(anchor, SpectralConfig.animatedDeployRadius(), null, Idle.HOVER);
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

    /** Thrown, with its owner away: stops fighting and hovers still at its anchor, its time paused. */
    private void waitForOwner() {
        if (target != null) {
            setTarget(null);
        }
        impactTick = -1;
        if (isReturning()) {
            entityData.set(RETURNING, false);
        }
        moveToward(anchor, getYRot(), Math.max(0.15, position().distanceTo(anchor) * 0.3));
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

    /** What a weapon does when it has nothing to fight. */
    private enum Idle {
        /** Heads home (a guard): the target is looked for again first. */
        RETURN,
        /** Hovers at its anchor, looking for a target every few ticks. */
        HOVER,
        /** Floats at its owner's side, waiting to be pointed at something: it never looks for a target itself. */
        FOLLOW
    }

    /**
     * Chases and strikes its target, picking the nearest one in sight of {@code center} whenever it has none (but for
     * a companion, which takes what its owner hit), then does {@code idle} when there is nothing to fight.
     */
    private void fight(Vec3 center, double radius, @Nullable Entity viewer, Idle idle) {
        boolean lost = false;
        if (target != null && !stillFightable(target, center, radius)) {
            setTarget(null);
            lost = true;
        }
        // A guarding weapon looks again before heading home; a hovering one looks every few ticks.
        if (target == null && impactTick < 0 && idle != Idle.FOLLOW && (idle == Idle.RETURN || lost || tickCount % SEARCH_INTERVAL == 0)) {
            setTarget(Animated.findTarget(level(), center, radius, viewer));
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (profile == null) {
            profile = WeaponProfile.of(stack, isCompanion() ? COMPANION_INTERVAL_SCALE : 1.0);
        }

        if (target != null) {
            Vec3 strike = strikePoint(target, center);
            moveToward(strike, yawTo(target), SEEK_SPEED);
            if (cooldown <= 0 && impactTick < 0 && position().distanceTo(strike) < 1.0) {
                beginAttack(profile);
            }
        } else if (idle == Idle.FOLLOW) {
            // Clients draw an idle companion at its owner's side themselves, so the entity only needs to keep up
            // roughly: it stays put until its owner has drawn off a little, which spares a movement packet a tick.
            Vec3 rest = Animated.restAnchor(owner, 1f, 0f);
            double distance = position().distanceTo(rest);
            if (distance > PARK_DISTANCE) {
                catchingUp = true;
            }
            if (catchingUp) {
                moveToward(rest, owner.yBodyRot, Math.max(SEEK_SPEED, distance * 0.4));
                catchingUp = position().distanceTo(rest) > PARKED_DISTANCE;
            }
        } else if (impactTick < 0) {
            if (idle == Idle.RETURN) {
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

    /** Whether {@code target} is still worth chasing: alive, here, in reach, and (for all but a companion) a hostile mob. */
    private boolean stillFightable(LivingEntity target, Vec3 center, double radius) {
        return target.isAlive() && !target.isRemoved() && target.level() == level()
                && (isCompanion() || Animated.isValidTarget(target)) && Animated.inRange(center, target, radius, LEASH_SLACK);
    }

    private void strike(LivingEntity target) {
        WeaponProfile.AttackStep step = profile.step(combo - 1);
        if (!isGuarding()) {
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
            if (!isGuarding()) {
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

    @Override
    protected void clientTick() {
        super.clientTick();
        tiltO = tilt;
        tuckO = tuck;
        Player player = isCompanion() ? getOwner() : null;
        if (player != null) {
            double speed = Math.sqrt(Mth.square(player.getX() - player.xo) + Mth.square(player.getZ() - player.zo));
            boolean sprinting = player.isSprinting() && speed > 0.05;
            float targetTilt = sprinting ? 55f : speed > 0.03 ? 20f : 0f;
            tilt += (targetTilt - tilt) * 0.15f;
            tuck += ((sprinting ? 1f : 0f) - tuck) * 0.15f;
        }
    }

    /** An idle companion is drawn at its rest spot, which can be a little way from where the entity is parked. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return isCompanion() ? getBoundingBox().inflate(2.0 + PARK_DISTANCE) : super.getBoundingBoxForCulling();
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
        copy.remove(ModComponents.COOLDOWN.get());
        return copy;
    }
}
