package com.linc.spectralenchantments.explosive;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;

/**
 * Explosive Bolts: a crossbow loads and fires explosive ammunition, each tag its own behavior.
 *
 * <ul>
 *   <li>{@code explosive_ammunition/fire_charge} (fire charges): a small fireball, as a dispenser fires one.</li>
 *   <li>{@code explosive_ammunition/blaze_spray} (blaze rods): a burning bolt that sprays fire around where it
 *       lands and sets creatures there alight (see {@link BlazeBolt}).</li>
 *   <li>{@code explosive_ammunition/ghast_fireball} (ghast tears): a ghast's exploding fireball.</li>
 *   <li>{@code explosive_ammunition/water_burst} (water bottles; any other potion in it must be water): bursts where
 *       it lands, putting out fires around (see {@link WaterBolt}).</li>
 *   <li>{@code explosive_ammunition/thrown_potion} (splash and lingering potions): the potion, thrown at bolt speed.</li>
 * </ul>
 *
 * <p>The ammunition is offered to the crossbow alongside arrows and fireworks, so it loads like them (Multishot and
 * Quick Charge apply). When the loaded crossbow fires, vanilla makes an arrow carrying the ammunition; that arrow is
 * swapped for the real projectile as it enters the level.
 */
public final class ExplosiveBolts {
    public static final TagKey<Item> FIRE_CHARGE = tag("fire_charge");
    public static final TagKey<Item> BLAZE_SPRAY = tag("blaze_spray");
    public static final TagKey<Item> GHAST_FIREBALL = tag("ghast_fireball");
    public static final TagKey<Item> WATER_BURST = tag("water_burst");
    public static final TagKey<Item> THROWN_POTION = tag("thrown_potion");

    public enum Behavior { FIRE_CHARGE, BLAZE_SPRAY, GHAST_FIREBALL, WATER_BURST, THROWN_POTION }

    private ExplosiveBolts() {}

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, SpectralEnchantments.id("explosive_ammunition/" + name));
    }

    public static boolean has(Level level, ItemStack crossbow) {
        return ModEnchantments.has(level, ModEnchantments.EXPLOSIVE_BOLTS, crossbow);
    }

    /** What the item does when fired, or null if it isn't explosive ammunition. */
    @Nullable
    public static Behavior behavior(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(FIRE_CHARGE)) {
            return Behavior.FIRE_CHARGE;
        }
        if (stack.is(BLAZE_SPRAY)) {
            return Behavior.BLAZE_SPRAY;
        }
        if (stack.is(GHAST_FIREBALL)) {
            return Behavior.GHAST_FIREBALL;
        }
        if (stack.is(WATER_BURST) && (!stack.is(Items.POTION)
                || stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER))) {
            return Behavior.WATER_BURST;
        }
        return stack.is(THROWN_POTION) ? Behavior.THROWN_POTION : null;
    }

    public static boolean isAmmo(ItemStack stack) {
        return behavior(stack) != null;
    }

    /**
     * Searches for ammunition as vanilla does (hands first, then the inventory in order), with explosive
     * ammunition counted alongside arrows and fireworks; picks it when it comes first. Both sides.
     */
    @SubscribeEvent
    public static void onGetProjectile(LivingGetProjectileEvent event) {
        LivingEntity shooter = event.getEntity();
        ItemStack crossbow = event.getProjectileWeaponItemStack();
        if (!(crossbow.getItem() instanceof CrossbowItem item) || !has(shooter.level(), crossbow)) {
            return;
        }
        ItemStack found = ProjectileWeaponItem.getHeldProjectile(shooter, item.getSupportedHeldProjectiles(crossbow).or(ExplosiveBolts::isAmmo));
        if (found.isEmpty() && shooter instanceof Player player) {
            Predicate<ItemStack> any = item.getAllSupportedProjectiles(crossbow).or(ExplosiveBolts::isAmmo);
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize() && found.isEmpty(); slot++) {
                if (any.test(inventory.getItem(slot))) {
                    found = inventory.getItem(slot);
                }
            }
        }
        if (isAmmo(found)) {
            event.setProjectileItemStack(found);
        }
    }

    /** Swaps the arrow vanilla makes for loaded explosive ammunition for the projectile it really fires. */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof AbstractArrow arrow) || arrow instanceof BlazeBolt || arrow instanceof WaterBolt) {
            return;
        }
        Behavior behavior = behavior(arrow.getPickupItemStackOrigin());
        ItemStack crossbow = arrow.getWeaponItem();
        if (behavior == null || crossbow == null || !(crossbow.getItem() instanceof CrossbowItem) || !has(level, crossbow)
                || !(arrow.getOwner() instanceof LivingEntity owner)) {
            return;
        }
        event.setCanceled(true);
        level.addFreshEntity(replacement(level, arrow, owner, behavior, crossbow));
    }

    private static Projectile replacement(ServerLevel level, AbstractArrow arrow, LivingEntity owner, Behavior behavior, ItemStack crossbow) {
        Vec3 velocity = arrow.getDeltaMovement();
        return switch (behavior) {
            case FIRE_CHARGE -> launched(new SmallFireball(level, owner, velocity), arrow);
            case GHAST_FIREBALL -> launched(new LargeFireball(level, owner, velocity, SpectralConfig.explosiveBoltsGhastPower()), arrow);
            case BLAZE_SPRAY -> BlazeBolt.from(level, arrow, owner, crossbow);
            case WATER_BURST -> WaterBolt.from(level, arrow, owner, crossbow);
            case THROWN_POTION -> {
                ThrownPotion potion = new ThrownPotion(level, owner);
                potion.setItem(arrow.getPickupItemStackOrigin().copyWithCount(1));
                potion.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
                potion.setDeltaMovement(velocity);
                yield potion;
            }
        };
    }

    /** Puts a fireball where the arrow was, heading the same way just as fast (it then settles to its usual speed). */
    private static AbstractHurtingProjectile launched(AbstractHurtingProjectile fireball, AbstractArrow arrow) {
        fireball.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
        fireball.setDeltaMovement(arrow.getDeltaMovement());
        return fireball;
    }
}
