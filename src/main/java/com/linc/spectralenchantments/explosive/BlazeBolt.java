package com.linc.spectralenchantments.explosive;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.projectile.ItemArrow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.EventHooks;

/**
 * A blaze rod fired by an Explosive Bolts crossbow: a burning bolt that hits like an arrow, then sprays fire over
 * everything within a radius of where it lands that it can reach in a straight line, setting creatures there alight.
 */
public class BlazeBolt extends ItemArrow {
    public BlazeBolt(EntityType<? extends BlazeBolt> type, Level level) {
        super(type, level);
    }

    private BlazeBolt(Level level, LivingEntity owner, ItemStack ammo, ItemStack crossbow) {
        super(ModEntities.BLAZE_BOLT.get(), owner, level, ammo, crossbow);
        setItem(ammo);
    }

    /** A bolt in place of the arrow vanilla made for the loaded ammunition: same spot, heading, speed and crit. */
    static BlazeBolt from(ServerLevel level, AbstractArrow arrow, LivingEntity owner, ItemStack crossbow) {
        BlazeBolt bolt = new BlazeBolt(level, owner, arrow.getPickupItemStackOrigin(), crossbow);
        bolt.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
        bolt.setDeltaMovement(arrow.getDeltaMovement());
        bolt.setYRot(arrow.getYRot());
        bolt.setXRot(arrow.getXRot());
        bolt.yRotO = bolt.getYRot();
        bolt.xRotO = bolt.getXRot();
        bolt.setCritArrow(arrow.isCritArrow());
        bolt.setSoundEvent(SoundEvents.CROSSBOW_HIT);
        bolt.pickup = Pickup.DISALLOWED;
        bolt.igniteForSeconds(100f);
        return bolt;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && !inGround) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide) {
            spray(position());
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
        spray(result.getLocation().add(face.scale(0.25)));
        discard();
    }

    private void spray(Vec3 center) {
        ServerLevel level = (ServerLevel) level();
        int radius = SpectralConfig.explosiveBoltsFireRadius();
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 24 * radius, radius * 0.4, radius * 0.3, radius * 0.4, 0.05);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 0.9f + random.nextFloat() * 0.2f);

        double reachSqr = (double) radius * radius;
        int burnSeconds = SpectralConfig.explosiveBoltsBurnSeconds();
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (entity.getBoundingBox().distanceToSqr(center) <= reachSqr && reaches(center, entity.getBoundingBox().getCenter())) {
                entity.igniteForSeconds(burnSeconds);
            }
        }

        Player player = getOwner() instanceof Player owner ? owner : null;
        if (player != null ? !player.mayBuild() : !EventHooks.canEntityGrief(level, this)) {
            return;
        }
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (pos.distToCenterSqr(center) > reachSqr
                    || !BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)
                    || (player != null && !player.mayInteract(level, pos))
                    || !reaches(center, Vec3.atCenterOf(pos))) {
                continue;
            }
            BlockPos at = pos.immutable();
            level.setBlockAndUpdate(at, BaseFireBlock.getState(level, at));
        }
    }

    /** Nothing solid between the spray's center and {@code to}. */
    private boolean reaches(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.MISS;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.BLAZE_ROD);
    }

    /** Never left lying about, whatever happens to it. */
    @Override
    protected boolean tryPickup(Player player) {
        return false;
    }
}
