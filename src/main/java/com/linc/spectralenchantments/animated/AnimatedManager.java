package com.linc.spectralenchantments.animated;

import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.catapult.CatapultedWeapon;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Animated, both ways of using it:
 *
 * <ul>
 *   <li>Armor stands holding an Animated weapon look for hostile mobs every so often and send it out after them.</li>
 *   <li>Using an Animated weapon on a block while sneaking leaves it fighting there ({@link AnimatedWeaponEntity}).
 *       Using it or attacking with it again while it is out calls it back instead; once back it can't be swung for a
 *       moment.</li>
 *   <li>A thrown (or fired) Animated weapon stands guard where it lands.</li>
 *   <li>An Animated weapon with Mending mends itself from what it slays, as if a player had wielded it.</li>
 * </ul>
 */
public final class AnimatedManager {
    /** Ticks between an idle armor stand's looks for hostile mobs. */
    private static final int STAND_SCAN_INTERVAL = 10;

    private static final Map<ArmorStand, AnimatedWeaponEntity> GUARDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Player, AnimatedWeaponEntity> DEPLOYED = Collections.synchronizedMap(new WeakHashMap<>());
    /** Creatures a player's animated weapon slew, to the weapon to mend from the experience they drop. */
    private static final Map<LivingEntity, ItemStack> MENDING = Collections.synchronizedMap(new WeakHashMap<>());

    private AnimatedManager() {}

    // ---- Armor stands ----

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof ArmorStand stand && stand.level() instanceof ServerLevel level
                && (stand.tickCount + stand.getId()) % STAND_SCAN_INTERVAL == 0) {
            standTick(stand, level);
        } else if (event.getEntity() instanceof AbstractArrow arrow && arrow.inGround && !arrow.isRemoved()
                && arrow.level() instanceof ServerLevel level) {
            landed(arrow, level);
        }
    }

    // ---- Thrown weapons ----

    /**
     * A thrown or fired Animated weapon (a trident, a spear, a weapon from a Catapulting bow) that has landed stands
     * guard over the spot, in place of lying there. Loyalty brings a weapon home instead.
     */
    @Nullable
    public static AnimatedWeaponEntity landed(AbstractArrow arrow, ServerLevel level) {
        if (!(arrow.getOwner() instanceof Player owner) || owner.level() != level || arrow.pickup == AbstractArrow.Pickup.DISALLOWED) {
            return null;
        }
        ItemStack stack = arrow.getPickupItemStackOrigin();
        if (!Animated.has(level, stack) || EnchantmentHelper.getTridentReturnToOwnerAcceleration(level, stack, arrow) > 0) {
            return null;
        }
        AnimatedWeaponEntity weapon = new AnimatedWeaponEntity(ModEntities.ANIMATED_WEAPON.get(), level);
        Vec3 from = arrow.position();
        weapon.sentry(owner, stack.copy(), from, from.add(0, 1.0, 0), arrow instanceof CatapultedWeapon,
                arrow.pickup == AbstractArrow.Pickup.CREATIVE_ONLY);
        level.addFreshEntity(weapon);
        arrow.discard();
        return weapon;
    }

    /** Sends the stand's Animated weapon after the nearest hostile mob in sight, unless it is already out. */
    public static boolean standTick(ArmorStand stand, ServerLevel level) {
        AnimatedWeaponEntity current = GUARDS.get(stand);
        if (current != null && !current.isRemoved()) {
            return false;
        }
        InteractionHand hand = Animated.animatedHand(stand);
        if (hand == null) {
            GUARDS.remove(stand);
            return false;
        }
        LivingEntity target = Animated.findTarget(level, Animated.standCenter(stand), SpectralConfig.animatedStandRadius(), stand);
        if (target == null) {
            return false;
        }
        AnimatedWeaponEntity weapon = new AnimatedWeaponEntity(ModEntities.ANIMATED_WEAPON.get(), level);
        weapon.guard(stand, hand, target);
        level.addFreshEntity(weapon);
        GUARDS.put(stand, weapon);
        return true;
    }

    @Nullable
    public static AnimatedWeaponEntity getGuard(ArmorStand stand) {
        AnimatedWeaponEntity weapon = GUARDS.get(stand);
        return weapon != null && !weapon.isRemoved() ? weapon : null;
    }

    // ---- Deploying ----

    /**
     * The player's deployed weapon, still out and not yet on its way back. On the server it is tracked; on a client
     * it is looked up among the entities around the player.
     */
    @Nullable
    public static AnimatedWeaponEntity getDeployed(Player player) {
        AnimatedWeaponEntity weapon;
        if (player.level().isClientSide) {
            double reach = Math.max(SpectralConfig.animatedRecallDistance(), SpectralConfig.animatedThrownRecallDistance())
                    + SpectralConfig.animatedDeployRadius() + 8;
            List<AnimatedWeaponEntity> found = player.level().getEntitiesOfClass(AnimatedWeaponEntity.class,
                    player.getBoundingBox().inflate(reach), entity -> entity.isDeployed() && entity.getHome() == player);
            weapon = found.isEmpty() ? null : found.getFirst();
        } else {
            weapon = DEPLOYED.get(player);
        }
        return weapon != null && !weapon.isRemoved() && !weapon.isReturning() ? weapon : null;
    }

    /** Whether the item in the player's {@code hand} is the one out fighting. */
    public static boolean isDeployedInHand(Player player, InteractionHand hand) {
        AnimatedWeaponEntity weapon = getDeployed(player);
        if (weapon == null) {
            return false;
        }
        if (player.level().isClientSide) {
            return weapon.getSlot() == slotOf(player, hand);
        }
        return weapon.isAnimating(player.getItemInHand(hand));
    }

    private static int slotOf(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
    }

    /** Leaves the item in the player's {@code hand} floating at {@code at}, calling back any weapon already out. */
    public static AnimatedWeaponEntity deploy(ServerPlayer player, InteractionHand hand, Vec3 at) {
        AnimatedWeaponEntity previous = getDeployed(player);
        if (previous != null) {
            previous.recall();
        }
        AnimatedWeaponEntity weapon = new AnimatedWeaponEntity(ModEntities.ANIMATED_WEAPON.get(), player.level());
        weapon.deploy(player, slotOf(player, hand), at);
        player.level().addFreshEntity(weapon);
        DEPLOYED.put(player, weapon);
        return weapon;
    }

    /** Calls the player's weapon back if the item in either hand is the one out. Sent by the client on a click. */
    public static void recallFromHand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (isDeployedInHand(player, hand)) {
                getDeployed(player).recall();
                return;
            }
        }
    }

    private static boolean canUse(Player player) {
        return !(player instanceof FakePlayer) && !player.isSpectator();
    }

    /**
     * Sneak-using an Animated weapon on a block deploys it just off the clicked face. Using the deployed weapon
     * on anything calls it back. Fires on both sides, so both agree the click was handled.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!canUse(player)) {
            return;
        }
        if (recallOnUse(event, player, event.getHand())) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!player.isShiftKeyDown() || !Animated.has(event.getLevel(), stack)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (player instanceof ServerPlayer serverPlayer) {
            Direction face = event.getFace() != null ? event.getFace() : Direction.UP;
            Vec3 at = event.getHitVec().getLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.75));
            if (face == Direction.UP) {
                at = at.add(0, 0.5, 0);
            }
            deploy(serverPlayer, event.getHand(), at);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (canUse(event.getEntity())) {
            recallOnUse(event, event.getEntity(), event.getHand());
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (canUse(event.getEntity())) {
            recallOnUse(event, event.getEntity(), event.getHand());
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (canUse(event.getEntity())) {
            recallOnUse(event, event.getEntity(), event.getHand());
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && player.getCooldowns().isOnCooldown(held.getItem()) && Animated.has(player.level(), held)) {
            // Just recalled: it can't be swung yet.
            event.setCanceled(true);
            return;
        }
        if (canUse(player) && isDeployedInHand(player, InteractionHand.MAIN_HAND)) {
            event.setCanceled(true);
            recall(player);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (canUse(player) && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && isDeployedInHand(player, InteractionHand.MAIN_HAND)) {
            event.setCanceled(true);
            recall(player);
        }
    }

    /** Using the deployed weapon calls it back rather than doing what the use would. */
    private static boolean recallOnUse(PlayerInteractEvent event, Player player, InteractionHand hand) {
        if (!isDeployedInHand(player, hand)) {
            return false;
        }
        if (event instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
        setResult(event, InteractionResult.sidedSuccess(player.level().isClientSide));
        recall(player);
        return true;
    }

    private static void setResult(PlayerInteractEvent event, InteractionResult result) {
        switch (event) {
            case PlayerInteractEvent.RightClickBlock e -> e.setCancellationResult(result);
            case PlayerInteractEvent.RightClickItem e -> e.setCancellationResult(result);
            case PlayerInteractEvent.EntityInteract e -> e.setCancellationResult(result);
            case PlayerInteractEvent.EntityInteractSpecific e -> e.setCancellationResult(result);
            default -> {}
        }
    }

    private static void recall(Player player) {
        if (!player.level().isClientSide) {
            AnimatedWeaponEntity weapon = getDeployed(player);
            if (weapon != null) {
                weapon.recall();
            }
        }
    }

    // ---- Mending ----

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getDirectEntity() instanceof AnimatedWeaponEntity weapon) || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = weapon.getWeaponItem();
        if (stack.isEmpty() || !stack.isDamaged() || !EnchantmentHelper.has(stack, EnchantmentEffectComponents.REPAIR_WITH_XP)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player) {
            // A player's kill drops experience: mend from that, as their own pick-up would.
            MENDING.put(victim, stack);
        } else {
            // Nobody's kill drops none: mend from what it would have been worth.
            mend(level, stack, victim.getExperienceReward(level, weapon));
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        ItemStack stack = MENDING.remove(event.getEntity());
        if (stack != null && event.getEntity().level() instanceof ServerLevel level) {
            int xp = event.getDroppedExperience();
            event.setDroppedExperience(xp - mend(level, stack, xp));
        }
    }

    /** Repairs {@code stack} from {@code xp} as Mending does; returns the experience used. */
    public static int mend(ServerLevel level, ItemStack stack, int xp) {
        if (xp <= 0 || !stack.isDamaged()) {
            return 0;
        }
        int repair = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, xp);
        int fixed = Math.min(repair, stack.getDamageValue());
        if (fixed <= 0) {
            return 0;
        }
        stack.setDamageValue(stack.getDamageValue() - fixed);
        return Math.min(xp, (int) Math.ceil(fixed * (double) xp / repair));
    }
}
