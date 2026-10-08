package com.linc.spectralenchantments.catapult;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.animated.AnimatedManager;
import com.linc.spectralenchantments.compat.SableCompat;
import com.linc.spectralenchantments.spectral.SpectralWeapons;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;

/**
 * Catapulting: a bow in the main hand fires whatever is in the off hand in place of an arrow, as fast and far as
 * an arrow. Arrows in the off hand are still shot as arrows.
 *
 * <ul>
 *   <li>Weapons and tools fly point first and stick where they land (see {@link CatapultedWeapon}).</li>
 *   <li>Things normally thrown (snowballs, eggs, ender pearls, splash potions, a fishing rod's lure...) are thrown,
 *       at bow speed. Snowballs leave a layer of snow where they land.</li>
 *   <li>Fireworks fly straight, as from a crossbow, and burst where they hit.</li>
 *   <li>A bucket of fish (or any mob) flings the creature itself; the empty bucket stays in hand.</li>
 *   <li>Anything else flies as an item: blocks are placed where they land, other items do what they would if used
 *       there (see {@link CatapultedItem}, {@link ItemImpacts}). A full bucket's empty bucket stays in hand.</li>
 * </ul>
 *
 * <p>The off-hand item is offered as the bow's ammunition, so the bow draws with nothing else to shoot; on release
 * the vanilla shot is cancelled and the item launched here instead.
 */
public final class Catapulting {
    /** Fired as weapons, besides anything Spectral Weapon counts as one (and so anything Better Combat swings). */
    public static final TagKey<Item> WEAPONS = TagKey.create(Registries.ITEM, SpectralEnchantments.id("catapulting/weapons"));
    /** Thrown the way they are by hand. */
    public static final TagKey<Item> THROWABLES = TagKey.create(Registries.ITEM, SpectralEnchantments.id("catapulting/throwables"));
    /** Never fired: a shield or totem in the off hand stays put, and the bow shoots arrows as usual. */
    public static final TagKey<Item> EXCLUDED = TagKey.create(Registries.ITEM, SpectralEnchantments.id("catapulting/excluded"));
    /** Marks projectiles a Catapulting bow threw, for what they do on landing. */
    public static final String CATAPULTED = SpectralEnchantments.MOD_ID + ".catapulted";
    /** A crossbow's firework speed. */
    private static final float FIREWORK_SPEED = 1.6f;

    /** An arrow's speed at full draw, in blocks a tick. */
    public static final float FULL_SPEED = 3.0f;

    /** Projectiles that joined the level while a throwable's own use was running, to be sped up to bow speed. */
    private static final ThreadLocal<List<Projectile>> CAPTURED = new ThreadLocal<>();

    public enum Kind { WEAPON, THROWABLE, FIREWORK, MOB_BUCKET, BLOCK, ITEM }

    private Catapulting() {}

    public static boolean has(Level level, ItemStack bow) {
        if (bow.isEmpty() || !SpectralConfig.catapultingEnabled()) {
            return false;
        }
        return level.registryAccess().registry(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.getHolder(ModEnchantments.CATAPULTING))
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, bow) > 0)
                .orElse(false);
    }

    /** What firing the player's off-hand item from {@code bow} would do, or null if the bow shoots normally. */
    @Nullable
    public static Kind kindFor(Player player, ItemStack bow) {
        if (!(bow.getItem() instanceof BowItem bowItem) || player.getMainHandItem() != bow || !has(player.level(), bow)) {
            return null;
        }
        ItemStack ammo = player.getOffhandItem();
        if (ammo.isEmpty() || ammo.is(EXCLUDED) || bowItem.getSupportedHeldProjectiles(bow).test(ammo)
                || AnimatedManager.isDeployedInHand(player, InteractionHand.OFF_HAND)) {
            return null;
        }
        if (ammo.is(THROWABLES)) {
            return player.getCooldowns().isOnCooldown(ammo.getItem()) ? null : Kind.THROWABLE;
        }
        if (isWeapon(ammo)) {
            return wouldBreak(player, ammo) ? null : Kind.WEAPON;
        }
        if (ammo.getItem() instanceof FireworkRocketItem) {
            return Kind.FIREWORK;
        }
        if (ammo.getItem() instanceof MobBucketItem) {
            return Kind.MOB_BUCKET;
        }
        return fliesAsBlock(ammo) ? Kind.BLOCK : Kind.ITEM;
    }

    /**
     * Blocks fly as blocks: placed where they land, ploughing through creatures. Not a powder snow bucket (poured
     * like other buckets) nor food that happens to plant (carrots, berries): those are used, and so eaten or planted.
     */
    public static boolean fliesAsBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem && !(stack.getItem() instanceof SolidBucketItem) && !stack.has(DataComponents.FOOD);
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.is(WEAPONS) || SpectralWeapons.isWieldedWeapon(stack);
    }

    /** Whether firing would use up the weapon's last durability. */
    private static boolean wouldBreak(Player player, ItemStack weapon) {
        int cost = SpectralConfig.catapultingWeaponCost();
        return cost > 0 && !player.hasInfiniteMaterials() && weapon.isDamageableItem()
                && weapon.getDamageValue() + cost >= weapon.getMaxDamage();
    }

    /** Offers the off-hand item as ammunition, so the bow can be drawn with it (and only it). Both sides. */
    @SubscribeEvent
    public static void onGetProjectile(LivingGetProjectileEvent event) {
        if (event.getEntity() instanceof Player player && kindFor(player, event.getProjectileWeaponItemStack()) != null) {
            event.setProjectileItemStack(player.getOffhandItem());
        }
    }

    /** Fires the off-hand item in place of the arrow. Cancelled on both sides, so the bow never shoots it as one. */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        ItemStack bow = event.getBow();
        if (kindFor(player, bow) == null) {
            return;
        }
        event.setCanceled(true);
        float power = BowItem.getPowerForTime(event.getCharge());
        if (power >= 0.1f && player instanceof ServerPlayer serverPlayer) {
            fire(serverPlayer, bow, power);
        }
    }

    /**
     * Fires the player's off-hand item from {@code bow} at {@code power} (0-1, as a bow's draw), wearing the bow.
     *
     * @return whether anything was fired
     */
    public static boolean fire(ServerPlayer player, ItemStack bow, float power) {
        Kind kind = kindFor(player, bow);
        if (kind == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        ItemStack ammo = player.getOffhandItem();
        boolean creative = player.hasInfiniteMaterials();
        float speed = power * FULL_SPEED;

        switch (kind) {
            case WEAPON -> {
                ItemStack weapon = creative ? ammo.copyWithCount(1) : ammo.split(1);
                int cost = SpectralConfig.catapultingWeaponCost();
                if (!creative && cost > 0) {
                    weapon.hurtAndBreak(cost, level, player, item -> {});
                }
                CatapultedWeapon projectile = new CatapultedWeapon(level, player, weapon, bow, power);
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, speed, 1f);
                if (creative) {
                    projectile.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }
                // Bow enchantments that act on what it shoots: Flame sets the weapon alight.
                EnchantmentHelper.onProjectileSpawned(level, bow, projectile, item -> {});
                level.addFreshEntity(projectile);
            }
            case THROWABLE -> {
                if (!throwFromOffhand(player, speed)) {
                    return false;
                }
            }
            case FIREWORK -> {
                ItemStack rocket = creative ? ammo.copyWithCount(1) : ammo.split(1);
                FireworkRocketEntity projectile = new FireworkRocketEntity(level, rocket, player, player.getX(),
                        player.getEyeY() - 0.15, player.getZ(), true);
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, power * FIREWORK_SPEED, 1f);
                level.addFreshEntity(projectile);
            }
            case MOB_BUCKET -> {
                if (!flingFromBucket(player, ammo, speed)) {
                    return false;
                }
                if (!creative) {
                    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BUCKET));
                }
            }
            case BLOCK, ITEM -> {
                ItemStack item = creative ? ammo.copyWithCount(1) : ammo.split(1);
                if (!creative && ItemImpacts.isFilledBucket(item)) {
                    // The bucket's contents fly; the bucket stays.
                    giveBack(player, new ItemStack(Items.BUCKET));
                }
                Vec3 direction = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
                if (kind == Kind.BLOCK && SableCompat.fire(player, item, direction, power)) {
                    break;
                }
                CatapultedItem projectile = new CatapultedItem(level, player, item, power);
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, speed, 1f);
                level.addFreshEntity(projectile);
            }
        }

        int bowCost = SpectralConfig.catapultingBowCost();
        if (bowCost > 0) {
            bow.hurtAndBreak(bowCost, player, EquipmentSlot.MAINHAND);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                1f, 1f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        player.awardStat(Stats.ITEM_USED.get(bow.getItem()));
        return true;
    }

    /**
     * Throws the off-hand item just as using it would (consuming it, starting its cooldown...), then speeds
     * whatever it threw up to bow speed.
     */
    private static boolean throwFromOffhand(ServerPlayer player, float speed) {
        List<Projectile> thrown = new ArrayList<>();
        CAPTURED.set(thrown);
        try {
            player.getOffhandItem().use(player.level(), player, InteractionHand.OFF_HAND);
        } finally {
            CAPTURED.remove();
        }
        for (Projectile projectile : thrown) {
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, speed, 1f);
            projectile.addTag(CATAPULTED);
        }
        return !thrown.isEmpty();
    }

    /** Lets the creature out of a bucket of fish (axolotl, tadpole...) and flings it, at bow speed. */
    private static boolean flingFromBucket(ServerPlayer player, ItemStack bucket, float speed) {
        ServerLevel level = player.serverLevel();
        EntityType<?> type = ((MobBucketItem) bucket.getItem()).type;
        Entity creature = type.spawn(level, bucket, null, player.blockPosition(), MobSpawnType.BUCKET, false, false);
        if (creature == null) {
            return false;
        }
        if (creature instanceof Bucketable bucketable) {
            bucketable.loadFromBucketTag(bucket.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).copyTag());
            bucketable.setFromBucket(true);
        }
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(0.6)).subtract(0, creature.getBbHeight() / 2, 0);
        creature.moveTo(from.x, from.y, from.z, player.getYRot(), 0f);
        creature.setDeltaMovement(look.scale(speed));
        creature.hurtMarked = true;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_EMPTY_FISH, SoundSource.PLAYERS, 1f, 1f);
        return true;
    }

    /** Into the off hand if it's free, else the inventory, else at the player's feet. */
    private static void giveBack(Player player, ItemStack stack) {
        if (player.getOffhandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** A snowball off a Catapulting bow leaves a layer of snow where it lands. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Snowball snowball) || !snowball.getTags().contains(CATAPULTED)
                || !(event.getRayTraceResult() instanceof BlockHitResult hit) || !(snowball.level() instanceof ServerLevel level)) {
            return;
        }
        Player player = snowball.getOwner() instanceof Player owner ? owner : null;
        BlockPos pos = hit.getBlockPos();
        if (!addSnow(level, pos, player)) {
            addSnow(level, pos.relative(hit.getDirection()), player);
        }
    }

    /** Adds a layer to the snow at {@code pos}, or starts one there if snow can lie on it. */
    private static boolean addSnow(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (player != null && !player.mayInteract(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers < SnowLayerBlock.MAX_HEIGHT) {
                level.setBlockAndUpdate(pos, state.setValue(SnowLayerBlock.LAYERS, layers + 1));
            }
            return true;
        }
        BlockState snow = Blocks.SNOW.defaultBlockState();
        if (state.canBeReplaced() && state.getFluidState().isEmpty() && snow.canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, snow);
            level.playSound(null, pos, SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 0.6f, 1f);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        List<Projectile> thrown = CAPTURED.get();
        if (thrown != null && !event.loadedFromDisk() && event.getEntity() instanceof Projectile projectile) {
            thrown.add(projectile);
        }
    }
}
