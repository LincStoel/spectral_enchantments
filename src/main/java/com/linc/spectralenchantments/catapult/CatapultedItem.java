package com.linc.spectralenchantments.catapult;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Anything a Catapulting bow fires that is neither a weapon nor normally thrown. Flies like an arrow.
 *
 * <ul>
 *   <li>A block is placed where it lands, as if its owner had placed it there. It ploughs on through creatures in
 *       its way, hitting each (by rarity) and knocking it back a little, slowing as it goes. An anvil hits and
 *       chips as if it had fallen as far as it flew.</li>
 *   <li>Any other item does what it would if used where it lands (see {@link ItemImpacts}).</li>
 * </ul>
 */
public class CatapultedItem extends ThrowableItemProjectile {
    /** How much speed a block keeps each time it ploughs through a creature. */
    private static final double PASS_THROUGH_SLOWDOWN = 0.7;

    /** How far the bow was drawn (0-1): a full draw hits for the rarity's full damage. */
    private float power = 1f;
    /** Where it was fired from, for an anvil's "fall". */
    @Nullable
    private Vec3 launchedFrom;
    /** Creatures a block has already ploughed through. */
    private final IntSet passedThrough = new IntOpenHashSet();

    public CatapultedItem(EntityType<? extends CatapultedItem> type, Level level) {
        super(type, level);
    }

    public CatapultedItem(Level level, LivingEntity owner, ItemStack stack, float power) {
        super(ModEntities.CATAPULTED_ITEM.get(), owner, level);
        this.power = power;
        this.launchedFrom = position();
        setItem(stack);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Power", power);
        if (launchedFrom != null) {
            tag.putDouble("FromX", launchedFrom.x);
            tag.putDouble("FromY", launchedFrom.y);
            tag.putDouble("FromZ", launchedFrom.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        power = tag.contains("Power") ? tag.getFloat("Power") : 1f;
        launchedFrom = tag.contains("FromX") ? new Vec3(tag.getDouble("FromX"), tag.getDouble("FromY"), tag.getDouble("FromZ")) : null;
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

    /** A block, placed where it lands (see {@link Catapulting#fliesAsBlock}). */
    private boolean isBlock() {
        return Catapulting.fliesAsBlock(getItem());
    }

    private boolean isAnvil() {
        return getItem().getItem() instanceof BlockItem block && block.getBlock() instanceof AnvilBlock;
    }

    /** As a falling block's ceil(fallDistance - 1), with how far it flew for the fall. */
    private int anvilFall() {
        return launchedFrom == null ? 0 : Math.max(0, Mth.ceil(position().distanceTo(launchedFrom) - 1.0));
    }

    @Nullable
    private Player shooter() {
        return getOwner() instanceof ServerPlayer player && player.level() == level() ? player : null;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !passedThrough.contains(target.getId());
    }

    @Override
    protected void onHit(HitResult result) {
        if (result instanceof EntityHitResult hit && isBlock()) {
            // Blocks plough on through.
            if (level() instanceof ServerLevel level) {
                passThrough(level, hit.getEntity());
            }
            return;
        }
        super.onHit(result);
        if (!level().isClientSide) {
            discard();
        }
    }

    private void passThrough(ServerLevel level, Entity target) {
        passedThrough.add(target.getId());
        Entity owner = getOwner();
        float damage;
        DamageSource source;
        if (isAnvil()) {
            // As a falling anvil: two points a block fallen, at most 40.
            damage = Math.min(anvilFall() * 2, 40);
            source = damageSources().anvil(this);
        } else {
            damage = SpectralConfig.catapultingRarityDamage(getItem().getRarity()) * power;
            source = damageSources().thrown(this, owner == null ? this : owner);
        }
        if (damage > 0) {
            target.hurt(source, damage);
        }
        if (owner instanceof LivingEntity living) {
            living.setLastHurtMob(target);
        }
        Vec3 push = getDeltaMovement().multiply(1, 0, 1);
        if (push.lengthSqr() > 1.0E-6) {
            push = push.normalize().scale(0.4);
            target.push(push.x, 0.1, push.z);
            target.hurtMarked = true;
        }
        if (getItem().getItem() instanceof BlockItem block) {
            BlockState state = block.getBlock().defaultBlockState();
            level.playSound(null, getX(), getY(), getZ(), state.getSoundType(level, blockPosition(), this).getHitSound(),
                    SoundSource.NEUTRAL, 1f, 0.8f);
        }
        setDeltaMovement(getDeltaMovement().scale(PASS_THROUGH_SLOWDOWN));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level() instanceof ServerLevel level) {
            if (getOwner() instanceof LivingEntity living) {
                living.setLastHurtMob(result.getEntity());
            }
            float damage = SpectralConfig.catapultingRarityDamage(getItem().getRarity()) * power;
            ItemImpacts.hitEntity(this, level, result.getEntity(), shooter(), damage);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (!isBlock()) {
            ItemImpacts.hitBlock(this, level, result, shooter());
            return;
        }
        ItemStack stack = getItem().copy();
        if (isAnvil()) {
            // Chips, or breaks, as if it had fallen as far as it flew.
            int fall = anvilFall();
            if (random.nextFloat() < 0.05f + fall * 0.05f) {
                BlockState damaged = AnvilBlock.damage(((BlockItem) stack.getItem()).getBlock().defaultBlockState());
                if (damaged == null) {
                    level.levelEvent(LevelEvent.SOUND_ANVIL_BROKEN, result.getBlockPos(), 0);
                    return;
                }
                ItemStack chipped = new ItemStack(damaged.getBlock().asItem());
                chipped.applyComponents(stack.getComponentsPatch());
                stack = chipped;
            }
            level.levelEvent(LevelEvent.SOUND_ANVIL_LAND, result.getBlockPos(), 0);
        }
        if (!place(stack, result)) {
            drop(stack);
        }
    }

    /** Leaves {@code stack} where this came down. */
    void drop(ItemStack stack) {
        if (!stack.isEmpty()) {
            spawnAtLocation(stack, 0.1f);
        }
    }

    /**
     * Places the block against the face it hit, as its owner would by clicking there: their facing sets its
     * orientation, and their building rights (spawn protection, adventure mode, protection mods) apply.
     */
    private boolean place(ItemStack stack, BlockHitResult hit) {
        Player player = shooter();
        if (!SpectralConfig.catapultingPlacesBlocks() || player == null || !player.mayInteract(level(), hit.getBlockPos())) {
            return false;
        }
        UseOnContext context = new UseOnContext(level(), player, InteractionHand.OFF_HAND, stack, hit);
        return CommonHooks.onPlaceItemIntoWorld(context).consumesAction();
    }
}
