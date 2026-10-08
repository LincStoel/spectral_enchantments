package com.linc.spectralenchantments.explosive;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.projectile.ItemArrow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A water bottle fired by an Explosive Bolts crossbow: it bursts where it lands, putting out every fire (and lit
 * campfire and candle) within a radius, and any creature burning there. Like a splash of water, it stings creatures
 * that can't stand water.
 */
public class WaterBolt extends ItemArrow {
    public WaterBolt(EntityType<? extends WaterBolt> type, Level level) {
        super(type, level);
    }

    private WaterBolt(Level level, LivingEntity owner, ItemStack ammo, ItemStack crossbow) {
        super(ModEntities.WATER_BOLT.get(), owner, level, ammo, crossbow);
        setItem(ammo);
    }

    /** A bolt in place of the arrow vanilla made for the loaded ammunition: same spot, heading and speed. */
    static WaterBolt from(ServerLevel level, AbstractArrow arrow, LivingEntity owner, ItemStack crossbow) {
        WaterBolt bolt = new WaterBolt(level, owner, arrow.getPickupItemStackOrigin(), crossbow);
        bolt.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
        bolt.setDeltaMovement(arrow.getDeltaMovement());
        bolt.setYRot(arrow.getYRot());
        bolt.setXRot(arrow.getXRot());
        bolt.yRotO = bolt.getYRot();
        bolt.xRotO = bolt.getXRot();
        bolt.pickup = Pickup.DISALLOWED;
        return bolt;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && !inGround) {
            level().addParticle(ParticleTypes.FALLING_WATER, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide) {
            burst(result.getEntity().getBoundingBox().getCenter());
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (level().isClientSide) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 face = Vec3.atLowerCornerOf(result.getDirection().getNormal());
        burst(result.getLocation().add(face.scale(0.25)));
        discard();
    }

    /** Bursts at {@code center}, dousing everything burning within the radius. */
    public void burst(Vec3 center) {
        ServerLevel level = (ServerLevel) level();
        int radius = SpectralConfig.explosiveBoltsDouseRadius();
        level.sendParticles(ParticleTypes.SPLASH, center.x, center.y, center.z, 80 * radius, radius * 0.45, radius * 0.3, radius * 0.45, 0.4);
        level.sendParticles(ParticleTypes.FALLING_WATER, center.x, center.y, center.z, 20 * radius, radius * 0.4, radius * 0.3, radius * 0.4, 0.1);
        level.levelEvent(LevelEvent.PARTICLES_SPELL_POTION_SPLASH, BlockPos.containing(center), 0x385DC6);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1f, 1f);

        double reachSqr = (double) radius * radius;
        BlockPos origin = BlockPos.containing(center);
        boolean doused = false;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (pos.distToCenterSqr(center) <= reachSqr && douse(level, pos.immutable())) {
                doused = true;
            }
        }
        if (doused) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.7f, 1.6f);
        }

        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (entity.getBoundingBox().distanceToSqr(center) > reachSqr) {
                continue;
            }
            if (entity.isSensitiveToWater()) {
                entity.hurt(damageSources().indirectMagic(this, getOwner()), 1f);
            }
            if (entity.isOnFire()) {
                entity.extinguishFire();
            }
            if (entity instanceof Axolotl axolotl) {
                axolotl.rehydrate();
            }
        }
    }

    /** Puts out a fire, campfire or candle at {@code pos}, with a puff of smoke. */
    private boolean douse(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.FIRE)) {
            level.removeBlock(pos, false);
        } else if (AbstractCandleBlock.isLit(state)) {
            AbstractCandleBlock.extinguish(null, state, level, pos);
        } else if (CampfireBlock.isLitCampfire(state)) {
            CampfireBlock.dowse(getOwner(), level, pos, state);
            level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
        } else {
            return false;
        }
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
        return true;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.POTION);
    }

    /** Never left lying about, whatever happens to it. */
    @Override
    protected boolean tryPickup(Player player) {
        return false;
    }
}
