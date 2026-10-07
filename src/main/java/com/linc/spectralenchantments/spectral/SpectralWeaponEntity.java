package com.linc.spectralenchantments.spectral;

import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The ghostly copy of a Spectral Weapon item. Never saved; {@link SpectralWeaponManager} spawns one
 * per player and keeps it pointed at the right inventory slot. It locks on to the next thing its owner
 * hits and fights it until it dies or leaves its reach (or the weapon is dismissed). The server flies it and lands the hits;
 * the client only fades, tilts and animates it (see the client's SpectralWeaponRenderer).
 */
public class SpectralWeaponEntity extends FlyingWeaponEntity {
    public static final int FADE_TICKS = 8;
    private static final double SEEK_SPEED = 0.6;
    /** The hit lands if the target is still this close to the weapon when the swing connects. */
    private static final double HIT_SLACK = 2.0;
    /** Ticks without a {@link #claim} from the manager before the weapon dismisses itself. */
    private static final int ORPHAN_TICKS = 5;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FADING = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.BOOLEAN);

    // Server state.
    @Nullable
    private Player owner;
    @Nullable
    private WeaponProfile profile;
    private int fadingTicks;
    private int lastClaimTick;

    // Client state, read by the renderer.
    public float fade;
    public float fadeO;
    public float tilt;
    public float tiltO;
    public float tuck;
    public float tuckO;

    public SpectralWeaponEntity(EntityType<? extends SpectralWeaponEntity> type, Level level) {
        super(type, level);
        cooldown = FADE_TICKS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_ID, -1);
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(SLOT, 0);
        builder.define(FADING, false);
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
            Vec3 strike = strikePoint(target, owner.position());
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

    /** Damage source for a spectral hit: the weapon strikes directly, the owner gets the credit (and the player-kill loot). */
    DamageSource damageSource(Player owner) {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.PLAYER_ATTACK), this, owner);
    }

    @Override
    protected void clientTick() {
        super.clientTick();
        fadeO = fade;
        fade = approach(fade, isFading() ? 0f : 1f, 1f / FADE_TICKS);

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

    /** Render copy: one item, durability and glint stripped (glint quads would draw as a second ghost). */
    private static ItemStack displayCopy(ItemStack stack) {
        ItemStack copy = stack.copyWithCount(1);
        copy.remove(DataComponents.DAMAGE);
        copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        return copy;
    }
}
