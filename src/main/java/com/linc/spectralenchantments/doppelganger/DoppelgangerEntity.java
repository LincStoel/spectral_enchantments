package com.linc.spectralenchantments.doppelganger;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * A Doppelganger's ghostly double: stands where its owner started sneaking, drawing the monsters after them, until
 * they stand up. Nothing can hurt it, and it never moves. With Quark installed it bumps {@link #emoteSeed()} every
 * so often, and each client plays the Quark emote that seed picks.
 */
public class DoppelgangerEntity extends PlayerGhostEntity {
    private static final boolean QUARK = ModList.get().isLoaded("quark");

    private int emoteTimer;

    public DoppelgangerEntity(EntityType<? extends DoppelgangerEntity> type, Level level) {
        super(type, level);
    }

    public static DoppelgangerEntity create(Player owner) {
        DoppelgangerEntity ghost = new DoppelgangerEntity(ModEntities.DOPPELGANGER.get(), owner.level());
        float yaw = owner.getYRot();
        ghost.moveTo(owner.getX(), owner.getY(), owner.getZ(), yaw, 0f);
        ghost.bind(owner, owner.getItemBySlot(EquipmentSlot.CHEST), yaw);
        ghost.emoteTimer = ghost.nextEmoteDelay();
        return ghost;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            return;
        }
        LivingEntity owner = owner();
        double maxDistance = SpectralConfig.doppelgangerMaxDistance();
        if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level()
                || owner.distanceToSqr(this) > maxDistance * maxDistance) {
            discard();
            return;
        }
        if (QUARK && --emoteTimer <= 0) {
            int seed;
            do {
                seed = random.nextInt();
            } while (seed == 0 || seed == emoteSeed());
            setEmoteSeed(seed);
            emoteTimer = nextEmoteDelay();
        }
    }

    private int nextEmoteDelay() {
        int min = SpectralConfig.doppelgangerEmoteMinTicks();
        return min + random.nextInt(SpectralConfig.doppelgangerEmoteMaxTicks() - min + 1);
    }

    /** Untouchable, but not "invulnerable": that would make monsters lose interest in it. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !level().isClientSide) {
            discard();
        }
        return false;
    }

    /** Players (and Better Combat sweeps) pass straight through it. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** Monsters' arrows and fireballs stop at it; see {@link Doppelganger} for players' own shots. */
    @Override
    public boolean canBeHitByProjectile() {
        return isAlive();
    }
}
