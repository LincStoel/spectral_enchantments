package com.linc.spectralenchantments.catapult;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Anything a Catapulting bow fires that is neither a weapon nor normally thrown. Flies like an arrow and hits for
 * damage set by the item's rarity. A block is placed where it lands, as if its owner had placed it there, and shatters
 * if it hits a creature; any other item drops where it comes down.
 */
public class CatapultedItem extends ThrowableItemProjectile {
    /** How far the bow was drawn (0-1): a full draw hits for the rarity's full damage. */
    private float power = 1f;

    public CatapultedItem(EntityType<? extends CatapultedItem> type, Level level) {
        super(type, level);
    }

    public CatapultedItem(Level level, LivingEntity owner, ItemStack stack, float power) {
        super(ModEntities.CATAPULTED_ITEM.get(), owner, level);
        this.power = power;
        setItem(stack);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Power", power);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        power = tag.contains("Power") ? tag.getFloat("Power") : 1f;
    }

    /** An arrow's gravity, so it carries as far as one. */
    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBBLESTONE;
    }

    private boolean isBlock() {
        return getItem().getItem() instanceof BlockItem;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = result.getEntity();
        Entity owner = getOwner();
        float damage = SpectralConfig.catapultingRarityDamage(getItem().getRarity()) * power;
        if (damage > 0) {
            target.hurt(damageSources().thrown(this, owner == null ? this : owner), damage);
        }
        if (owner instanceof LivingEntity living) {
            living.setLastHurtMob(target);
        }
        if (getItem().getItem() instanceof BlockItem blockItem) {
            // Shatters: break particles and sound, nothing dropped.
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, blockPosition(), Block.getId(blockItem.getBlock().defaultBlockState()));
        } else {
            drop();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        if (!isBlock() || !place(result)) {
            drop();
        }
    }

    /** Leaves the item where it came down, unless it was a creative-mode copy. */
    private void drop() {
        if (!(getOwner() instanceof Player player && player.hasInfiniteMaterials())) {
            spawnAtLocation(getItem().copy(), 0.1f);
        }
    }

    /**
     * Places the block against the face it hit, as its owner would by clicking there: their facing sets its
     * orientation, and their building rights (spawn protection, adventure mode, protection mods) apply.
     */
    private boolean place(BlockHitResult hit) {
        if (!SpectralConfig.catapultingPlacesBlocks()
                || !(getOwner() instanceof ServerPlayer player)
                || player.level() != level()
                || !player.mayInteract(level(), hit.getBlockPos())) {
            return false;
        }
        ItemStack stack = getItem().copy();
        UseOnContext context = new UseOnContext(level(), player, InteractionHand.OFF_HAND, stack, hit);
        return CommonHooks.onPlaceItemIntoWorld(context).consumesAction();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            discard();
        }
    }
}
