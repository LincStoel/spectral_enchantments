package com.linc.spectralenchantments.catapult;

import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * What a fired item (not a weapon, not a block) does when it lands, much as using it would.
 *
 * <ul>
 *   <li>On a block it is used there, as its owner clicking it would (fire charges light fires, spawn eggs hatch,
 *       paintings and item frames hang, bone meal grows...). Full buckets pour out; end crystals that can't be set
 *       down explode.</li>
 *   <li>On a creature: armor is put on if it can wear it and the slot is free; food is eaten (whatever its hunger);
 *       drinks are drunk; spawn eggs hatch; fire charges set it alight; shears and the like are used on it.</li>
 *   <li>Anything else hits for damage by rarity.</li>
 * </ul>
 *
 * <p>Whatever wasn't used up pops out where it landed.
 */
public final class ItemImpacts {
    /** Fired items that are never used where they land, only dropped. */
    public static final TagKey<Item> NO_USE = TagKey.create(Registries.ITEM, SpectralEnchantments.id("catapulting/no_use"));
    /** Mobs a fired piece of armor is put on (players and armor stands always are). */
    public static final TagKey<EntityType<?>> WEARS_ARMOR = TagKey.create(Registries.ENTITY_TYPE, SpectralEnchantments.id("catapulting/wears_armor"));

    private ItemImpacts() {}

    /** Buckets whose contents are poured out (or drunk) where they land, the empty bucket staying with the shooter. */
    public static boolean isFilledBucket(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BucketItem bucket && !(item instanceof MobBucketItem) && bucket.content != Fluids.EMPTY
                || item instanceof SolidBucketItem || item instanceof MilkBucketItem;
    }

    // ---- Blocks ----

    static void hitBlock(CatapultedItem projectile, ServerLevel level, BlockHitResult hit, @Nullable Player player) {
        ItemStack stack = projectile.getItem().copy();
        BlockPos pos = hit.getBlockPos();
        if (!uses(stack)) {
            projectile.drop(stack);
            return;
        }
        if (stack.getItem() instanceof EndCrystalItem) {
            if (!use(level, player, stack, hit) && !explodeCrystal(projectile, level, player, pos)) {
                projectile.drop(stack);
            }
            return;
        }
        if (isFilledBucket(stack)) {
            if (!pour(level, player, pos, hit, stack)) {
                projectile.drop(stack);
            }
            return;
        }
        if (use(level, player, stack, hit)) {
            if (!stack.isEmpty()) {
                projectile.drop(stack);
            }
            return;
        }
        projectile.drop(stack);
    }

    /** Whether fired items are used where they land at all, and this one in particular. */
    private static boolean uses(ItemStack stack) {
        return SpectralConfig.catapultingUsesItems() && !stack.is(NO_USE);
    }

    /** Uses {@code stack} (which it may use up) on the block that was hit, as the player clicking it would. */
    private static boolean use(ServerLevel level, @Nullable Player player, ItemStack stack, BlockHitResult hit) {
        if (player == null || !player.mayInteract(level, hit.getBlockPos())) {
            return false;
        }
        InteractionResult result = CommonHooks.onPlaceItemIntoWorld(new UseOnContext(level, player, InteractionHand.OFF_HAND, stack, hit));
        return result.consumesAction();
    }

    /**
     * Pours a bucket out at {@code pos} (or against the face hit, if {@code pos} can't hold it), as the shooter would.
     * Returns false, having changed nothing, when there is no shooter (gone, or in another dimension) to answer for it.
     */
    private static boolean pour(ServerLevel level, @Nullable Player player, BlockPos pos, @Nullable BlockHitResult hit, ItemStack stack) {
        if (stack.getItem() instanceof MilkBucketItem) {
            level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.1);
            return true;
        }
        if (player == null) {
            return false;
        }
        if (stack.getItem() instanceof SolidBucketItem) {
            if (hit != null && use(level, player, stack, hit)) {
                return true;
            }
            BlockPos at = hit != null ? pos.relative(hit.getDirection()) : pos;
            if (level.getBlockState(at).canBeReplaced() && player.mayInteract(level, at)) {
                level.setBlockAndUpdate(at, ((SolidBucketItem) stack.getItem()).getBlock().defaultBlockState());
                return true;
            }
            return false;
        }
        BucketItem bucket = (BucketItem) stack.getItem();
        BlockState state = level.getBlockState(pos);
        BlockPos at = state.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(player, level, pos, state, bucket.content) || hit == null ? pos : pos.relative(hit.getDirection());
        return player.mayInteract(level, at) && bucket.emptyContents(player, level, at, hit, stack);
    }

    /**
     * An end crystal that can't be set down goes off where it landed, as the shooter's explosion (so protection mods
     * and kill credit see who fired it). Nothing happens, and false is returned, when that's switched off, there is no
     * shooter, or the shooter couldn't build there.
     */
    private static boolean explodeCrystal(CatapultedItem projectile, ServerLevel level, @Nullable Player player, BlockPos at) {
        if (!SpectralConfig.catapultingExplodesCrystals() || player == null || !player.mayInteract(level, at)) {
            return false;
        }
        Vec3 center = projectile.position();
        level.explode(projectile, center.x, center.y, center.z, 6.0f, Level.ExplosionInteraction.BLOCK);
        return true;
    }

    // ---- Creatures ----

    static void hitEntity(CatapultedItem projectile, ServerLevel level, Entity target, @Nullable Player player, float damage) {
        ItemStack stack = projectile.getItem().copy();
        Item item = stack.getItem();
        LivingEntity living = target instanceof LivingEntity l ? l : null;
        boolean uses = uses(stack);

        if (uses && item instanceof EndCrystalItem) {
            if (!explodeCrystal(projectile, level, player, target.blockPosition())) {
                projectile.drop(stack);
            }
            return;
        }
        if (uses && isFilledBucket(stack)) {
            if (item instanceof MilkBucketItem && living != null) {
                stack.finishUsingItem(level, living);
            } else if (!pour(level, player, target.blockPosition(), null, stack)) {
                projectile.drop(stack);
            }
            return;
        }
        if (uses && living != null) {
            if (equip(living, stack) || feed(projectile, level, living, stack, player)) {
                return;
            }
            if (stack.getUseAnimation() == UseAnim.DRINK) {
                stack.finishUsingItem(level, living);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.NEUTRAL, 0.6f, 1.2f);
                return;
            }
        }
        if (uses && item instanceof SpawnEggItem egg && player != null && player.mayInteract(level, target.blockPosition())) {
            egg.getType(stack).spawn(level, stack, player, target.blockPosition(), MobSpawnType.SPAWN_EGG, true, false);
            return;
        }
        if (uses && (item instanceof FireChargeItem || item instanceof FlintAndSteelItem)) {
            ignite(level, target, player);
            if (item instanceof FlintAndSteelItem && player != null) {
                stack.hurtAndBreak(1, level, player, broken -> {});
                if (!stack.isEmpty()) {
                    projectile.drop(stack);
                }
            }
            return;
        }
        if (uses && living != null && player != null) {
            ItemStack used = stack.copy();
            if (used.interactLivingEntity(player, living, InteractionHand.OFF_HAND).consumesAction()) {
                projectile.drop(used);
                return;
            }
        }

        if (damage > 0) {
            Entity owner = projectile.getOwner();
            target.hurt(projectile.damageSources().thrown(projectile, owner == null ? projectile : owner), damage);
        }
        projectile.drop(stack);
    }

    /** Puts a piece of armor on a creature that can wear it, if that slot is free. */
    static boolean equip(LivingEntity living, ItemStack stack) {
        EquipmentSlot slot = living.getEquipmentSlotForItem(stack);
        boolean body = slot == EquipmentSlot.BODY;
        if (!(slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || body) || !living.getItemBySlot(slot).isEmpty()) {
            return false;
        }
        boolean wears = living instanceof Player || living instanceof ArmorStand
                || living instanceof Mob mob && (body ? mob.isBodyArmorItem(stack) : living.getType().is(WEARS_ARMOR));
        if (!wears) {
            return false;
        }
        living.setItemSlot(slot, stack.copyWithCount(1));
        if (living instanceof Mob mob) {
            mob.setGuaranteedDrop(slot);
            mob.setPersistenceRequired();
        }
        living.level().playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.NEUTRAL, 1f, 1f);
        return true;
    }

    /** Feeds a creature, however full it is: players eat it, animals that like it grow or fall in love, any mob heals. */
    static boolean feed(CatapultedItem projectile, ServerLevel level, LivingEntity living, ItemStack stack, @Nullable Player player) {
        FoodProperties food = stack.getFoodProperties(living);
        if (food == null) {
            return false;
        }
        if (living instanceof Animal animal && animal.isFood(stack)) {
            if (animal.isBaby()) {
                animal.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-animal.getAge()), true);
            } else if (player != null && animal.canFallInLove()) {
                animal.setInLove(player);
            }
        }
        living.eat(level, stack.copy(), food);
        if (!(living instanceof Player)) {
            living.heal(food.nutrition());
        }
        food.usingConvertsTo().ifPresent(projectile::drop);
        return true;
    }

    /** Sets a creature alight, and the ground at its feet too where fire can go and the shooter could light it. */
    private static void ignite(ServerLevel level, Entity target, @Nullable Player player) {
        target.igniteForSeconds(8f);
        BlockPos feet = target.blockPosition();
        if (player != null && BaseFireBlock.canBePlacedAt(level, feet, Direction.UP) && player.mayInteract(level, feet)) {
            level.setBlockAndUpdate(feet, BaseFireBlock.getState(level, feet));
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1f, 1f);
    }
}
