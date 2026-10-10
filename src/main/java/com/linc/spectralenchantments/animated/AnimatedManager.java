package com.linc.spectralenchantments.animated;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.StackCooldowns;
import com.linc.spectralenchantments.catapult.CatapultedWeapon;
import com.linc.spectralenchantments.spectral.WeaponProfile;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Animated, in all its ways of fighting:
 *
 * <ul>
 *   <li>An Animated weapon in a player's hotbar fights beside them: it locks on to what they hit ({@link #ownerHit}).
 *       The anvil puts the enchantment on any item (which then fights weakly).</li>
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
    private static final Map<Player, AnimatedWeaponEntity> COMPANIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Player, AnimatedWeaponEntity> DEPLOYED = Collections.synchronizedMap(new WeakHashMap<>());
    /** Creatures a player's animated weapon slew, to the weapon to mend from the experience they drop. */
    private static final Map<LivingEntity, ItemStack> MENDING = Collections.synchronizedMap(new WeakHashMap<>());
    /** Server armor stands holding an Animated item: the only ones that look for mobs. */
    private static final Set<ArmorStand> ARMED_STANDS = Collections.newSetFromMap(new WeakHashMap<>());
    /** Players' Animated projectiles that just struck a block, to ticks left to settle into it. */
    private static final Map<AbstractArrow, Integer> LANDING = new WeakHashMap<>();
    private static final int LANDING_TICKS = 3;

    private static final class SelectionCache {
        final ItemStack[] stacks = new ItemStack[SELECTION_SLOTS];
        int selected = -1;
        int deployedId = -1;
        long time = -1;
        @Nullable
        Animated.Selection result;
    }

    /** The hotbar's slots and the off hand. */
    private static final int SELECTION_SLOTS = Inventory.getSelectionSize() + 1;
    private static final int SELECTION_REFRESH_TICKS = 20;
    private static final Map<Player, SelectionCache> SELECTIONS = Collections.synchronizedMap(new WeakHashMap<>());

    private AnimatedManager() {}

    // ---- Hotbar companions ----

    /** Only real players get a companion: mobs never run this, and fake players (machines such as deployers) are skipped. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (isRealPlayer(event.getEntity())) {
            tickCompanion(event.getEntity());
        }
    }

    private static boolean isRealPlayer(Player player) {
        return player instanceof ServerPlayer && !(player instanceof FakePlayer);
    }

    /** When a player lands a hit (melee or projectile), their companion locks on to the victim if it is free. */
    @SubscribeEvent
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof Player player && isRealPlayer(player)
                && !(source.getDirectEntity() instanceof AnimatedWeaponEntity) && !source.is(DamageTypes.THORNS)) {
            ownerHit(player, event.getEntity());
        }
    }

    /** {@code owner} hit {@code victim}: their companion locks on unless it is already fighting something. */
    public static boolean ownerHit(Player owner, LivingEntity victim) {
        AnimatedWeaponEntity weapon = getCompanion(owner);
        return weapon != null && weapon.lockOn(victim);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        AnimatedWeaponEntity weapon = COMPANIONS.remove(event.getEntity());
        if (weapon != null) {
            weapon.discard();
        }
    }

    /** Brings out, updates or sends home the player's companion. Called every server tick per player. */
    public static void tickCompanion(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        AnimatedWeaponEntity current = COMPANIONS.get(player);
        if (current != null && (current.isRemoved() || current.level() != level)) {
            if (!current.isRemoved()) {
                current.discard();
            }
            COMPANIONS.remove(player);
            current = null;
        }

        Animated.Selection selection = isSuppressed(player) ? null : select(player);
        if (selection == null) {
            if (current != null) {
                current.recall();
            }
            return;
        }
        if (current != null && !current.showsSameItem(selection)) {
            // A different item takes over: the old one flies home while the new one comes out.
            current.recall();
            COMPANIONS.remove(player);
            current = null;
        }
        if (current == null) {
            AnimatedWeaponEntity weapon = new AnimatedWeaponEntity(ModEntities.ANIMATED_WEAPON.get(), level);
            weapon.companion(player, selection);
            level.addFreshEntity(weapon);
            COMPANIONS.put(player, weapon);
        } else {
            current.rebind(selection);
            current.claim();
        }
    }

    /**
     * {@link Animated#select}, remembered while the player's hotbar, off hand, selected slot and deployed weapon stay as
     * they were (by stack identity), so an unchanged hotbar isn't searched every tick. Looked at afresh every so often
     * anyway, in case an item was changed where it lies.
     */
    @Nullable
    private static Animated.Selection select(Player player) {
        Inventory inventory = player.getInventory();
        AnimatedWeaponEntity deployed = DEPLOYED.get(player);
        int deployedId = deployed != null && !deployed.isRemoved() ? deployed.getId() : -1;
        long now = player.level().getGameTime();
        SelectionCache cache = SELECTIONS.computeIfAbsent(player, p -> new SelectionCache());
        boolean fresh = cache.time >= 0 && now - cache.time < SELECTION_REFRESH_TICKS && now >= cache.time
                && cache.selected == inventory.selected && cache.deployedId == deployedId
                && (cache.result == null || !cache.result.stack().isEmpty());
        for (int i = 0; fresh && i < SELECTION_SLOTS; i++) {
            fresh = cache.stacks[i] == inventory.getItem(selectionSlot(i));
        }
        if (!fresh) {
            for (int i = 0; i < SELECTION_SLOTS; i++) {
                cache.stacks[i] = inventory.getItem(selectionSlot(i));
            }
            cache.selected = inventory.selected;
            cache.deployedId = deployedId;
            cache.time = now;
            cache.result = Animated.select(player, stack -> isOut(player, stack));
        }
        return cache.result;
    }

    /** The hotbar's slots, then the off hand. */
    private static int selectionSlot(int i) {
        return i < Inventory.getSelectionSize() ? i : Inventory.SLOT_OFFHAND;
    }

    /** Animated items may have joined or left the excluded or disabled tags. */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        SELECTIONS.clear();
    }

    /** Sneaking sends the companion home, as does being dead, a spectator, or asleep. */
    private static boolean isSuppressed(Player player) {
        return player.isShiftKeyDown() || player.isSpectator() || !player.isAlive() || player.isSleeping();
    }

    /** Whether {@code stack} is already out as the player's deployed weapon (possibly still flying home). */
    private static boolean isOut(Player player, ItemStack stack) {
        AnimatedWeaponEntity deployed = DEPLOYED.get(player);
        return deployed != null && !deployed.isRemoved() && deployed.isAnimating(stack);
    }

    @Nullable
    public static AnimatedWeaponEntity getCompanion(Player player) {
        AnimatedWeaponEntity weapon = COMPANIONS.get(player);
        return weapon != null && !weapon.isRemoved() ? weapon : null;
    }

    /**
     * Lets an enchanted book put Animated on any item, not just melee weapons (those go through vanilla), except
     * those in {@link Animated#EXCLUDED_ITEMS}. Such items fight weakly; see {@link WeaponProfile}.
     */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        Player player = event.getPlayer();
        if (player == null || left.isEmpty() || !right.is(Items.ENCHANTED_BOOK)
                || left.is(Items.BOOK) || left.is(Items.ENCHANTED_BOOK) || Animated.isMeleeWeapon(left)
                || Animated.isExcluded(left)) {
            return;
        }
        Holder<Enchantment> holder = ModEnchantments.holder(player.level().registryAccess(), ModEnchantments.ANIMATED);
        if (holder == null || holder.is(ModEnchantments.DISABLED) || right.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder) <= 0) {
            return;
        }
        ItemEnchantments current = left.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (current.getLevel(holder) > 0 || !EnchantmentHelper.isEnchantmentCompatible(current.keySet(), holder)) {
            return;
        }

        ItemStack output = left.copy();
        EnchantmentHelper.updateEnchantments(output, enchantments -> enchantments.set(holder, 1));
        int priorWork = left.getOrDefault(DataComponents.REPAIR_COST, 0) + right.getOrDefault(DataComponents.REPAIR_COST, 0);
        int cost = holder.value().getAnvilCost() + priorWork;
        String name = event.getName();
        if (name != null && !StringUtil.isBlank(name) && !name.equals(left.getHoverName().getString())) {
            output.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            cost++;
        }
        output.set(DataComponents.REPAIR_COST, AnvilMenu.calculateIncreasedRepairCost(
                Math.max(left.getOrDefault(DataComponents.REPAIR_COST, 0), right.getOrDefault(DataComponents.REPAIR_COST, 0))));
        event.setOutput(output);
        event.setCost(cost);
        event.setMaterialCost(1);
    }

    // ---- Armor stands ----

    /**
     * An armor stand's hands changed (which also happens on its first tick after loading): track it while it holds an
     * Animated item, so only those stands are ever scanned.
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ArmorStand stand && !stand.level().isClientSide
                && event.getSlot().getType() == EquipmentSlot.Type.HAND) {
            if (Animated.animatedHand(stand) != null) {
                ARMED_STANDS.add(stand);
            } else {
                ARMED_STANDS.remove(stand);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ArmorStand stand) {
            ARMED_STANDS.remove(stand);
        } else if (event.getEntity() instanceof AbstractArrow arrow) {
            LANDING.remove(arrow);
        }
    }

    /** A player's Animated weapon (a trident, a weapon off a Catapulting bow) struck a block: it lands this tick. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof AbstractArrow arrow && event.getRayTraceResult() instanceof BlockHitResult
                && arrow.level() instanceof ServerLevel level && arrow.getOwner() instanceof Player
                && Animated.has(level, arrow.getPickupItemStackOrigin())) {
            LANDING.put(arrow, LANDING_TICKS);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!LANDING.isEmpty()) {
            // A copy: landing discards the arrow, which takes it out of the map.
            for (Map.Entry<AbstractArrow, Integer> entry : List.copyOf(LANDING.entrySet())) {
                AbstractArrow arrow = entry.getKey();
                if (arrow.level() != level) {
                    continue;
                }
                int left = entry.getValue();
                if (arrow.isRemoved() || arrow.inGround || left <= 1) {
                    LANDING.remove(arrow);
                    if (!arrow.isRemoved() && arrow.inGround) {
                        landed(arrow, level);
                    }
                } else {
                    LANDING.put(arrow, left - 1);
                }
            }
        }
        if (!ARMED_STANDS.isEmpty()) {
            for (ArmorStand stand : List.copyOf(ARMED_STANDS)) {
                if (stand.level() == level && !stand.isRemoved() && (stand.tickCount + stand.getId()) % STAND_SCAN_INTERVAL == 0) {
                    standTick(stand, level);
                }
            }
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
            ARMED_STANDS.remove(stand);
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
        AnimatedWeaponEntity companion = getCompanion(player);
        if (companion != null && companion.isAnimating(player.getItemInHand(hand))) {
            companion.recall();
        }
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
        if (!held.isEmpty() && StackCooldowns.isActive(held, player.level()) && Animated.has(player.level(), held)) {
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
