package com.linc.spectralenchantments.spectral;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server side of Spectral Weapon: keeps one {@link SpectralWeaponEntity} per player in sync with their
 * inventory, points it at what they hit, lands its hits, and lets the anvil put the enchantment on any item.
 *
 * <p>Only real players get a spectral weapon: mobs never run this, and fake players (machines acting as
 * players, such as deployers) are skipped.
 */
public final class SpectralWeaponManager {
    private static final Map<Player, SpectralWeaponEntity> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());

    /** While a spectral hit is being dealt: the owner's main-hand stack, and the Looting it should borrow from the spectral weapon. */
    private record Strike(ItemStack mainHand, int looting) {}

    private static final ThreadLocal<Strike> STRIKE = new ThreadLocal<>();

    private SpectralWeaponManager() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (isRealPlayer(event.getEntity())) {
            tick(event.getEntity());
        }
    }

    private static boolean isRealPlayer(Player player) {
        return player instanceof ServerPlayer && !(player instanceof FakePlayer);
    }

    /** When a player lands a hit (melee or projectile), their spectral weapon locks on to the victim if it is free. */
    @SubscribeEvent
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof Player player && isRealPlayer(player)
                && !(source.getDirectEntity() instanceof SpectralWeaponEntity) && !source.is(DamageTypes.THORNS)) {
            ownerHit(player, event.getEntity());
        }
    }

    /** {@code owner} hit {@code victim}: the weapon locks on unless it is already fighting something. */
    public static boolean ownerHit(Player owner, LivingEntity victim) {
        SpectralWeaponEntity weapon = getActive(owner);
        return weapon != null && weapon.lockOn(victim);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SpectralWeaponEntity weapon = ACTIVE.remove(event.getEntity());
        if (weapon != null) {
            weapon.discard();
        }
    }

    /** Spawns, updates or dismisses the player's spectral weapon. Called every server tick per player. */
    public static void tick(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        SpectralWeaponEntity current = ACTIVE.get(player);
        if (current != null && (current.isRemoved() || current.level() != level)) {
            if (!current.isRemoved()) {
                current.discard();
            }
            ACTIVE.remove(player);
            current = null;
        }

        SpectralWeapons.Selection selection = isSuppressed(player) ? null : SpectralWeapons.select(player);
        if (selection == null) {
            if (current != null) {
                current.claim();
                current.startFadeOut();
            }
            return;
        }
        if (current != null && !current.showsSameItem(selection)) {
            // A different item takes over: the old ghost fades away on its own while the new one fades in.
            current.startFadeOut();
            ACTIVE.remove(player);
            current = null;
        }
        if (current == null) {
            SpectralWeaponEntity weapon = new SpectralWeaponEntity(ModEntities.SPECTRAL_WEAPON.get(), level);
            weapon.bind(player, selection);
            level.addFreshEntity(weapon);
            ACTIVE.put(player, weapon);
            weapon.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.3f);
        } else {
            current.update(selection);
            current.claim();
        }
    }

    /** Sneaking dismisses the weapon, as does being dead, a spectator, or asleep. */
    private static boolean isSuppressed(Player player) {
        return player.isShiftKeyDown() || player.isSpectator() || !player.isAlive() || player.isSleeping();
    }

    @Nullable
    public static SpectralWeaponEntity getActive(Player player) {
        SpectralWeaponEntity weapon = ACTIVE.get(player);
        return weapon != null && !weapon.isRemoved() ? weapon : null;
    }

    /**
     * One spectral hit. Counts as the owner's attack for loot and XP (the owner is the damage source's
     * attacker) and wears the real item like a normal hit, so Unbreaking and Mending apply.
     */
    static void strike(SpectralWeaponEntity weapon, Player owner, ItemStack stack,
                       WeaponProfile profile, WeaponProfile.AttackStep step, LivingEntity target) {
        ServerLevel level = (ServerLevel) weapon.level();
        DamageSource source = weapon.damageSource(owner);
        float damage = WeaponProfile.WEAK_DAMAGE;
        int looting = 0;
        if (profile.full()) {
            damage = EnchantmentHelper.modifyDamage(level, stack, target, source, profile.baseDamage() * step.damageMultiplier());
            looting = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.LOOTING)
                    .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
                    .orElse(0);
        }

        boolean hurt;
        STRIKE.set(new Strike(owner.getMainHandItem(), looting));
        try {
            hurt = target.hurt(source, damage);
        } finally {
            STRIKE.remove();
        }
        if (!hurt) {
            return;
        }

        if (profile.full()) {
            float knockback = EnchantmentHelper.modifyKnockback(level, stack, target, source, 0f);
            if (knockback > 0f) {
                float yaw = weapon.getYRot() * Mth.DEG_TO_RAD;
                target.knockback(knockback * 0.5f, Mth.sin(yaw), -Mth.cos(yaw));
            }
            EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, stack);
        }
        if (!stack.isEmpty() && stack.hurtEnemy(target, owner)) {
            stack.postHurtEnemy(target, owner);
        }
        owner.setLastHurtMob(target);

        Vec3 at = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, target.getBbWidth() * 0.3, target.getBbHeight() * 0.25, target.getBbWidth() * 0.3, 0.1);
        level.playSound(null, at.x, at.y, at.z, profile.full() ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                SoundSource.PLAYERS, 0.45f, 1.3f);
    }

    /** Loot is rolled for the owner's main-hand item; during a spectral hit it borrows the spectral weapon's Looting. */
    @SubscribeEvent
    public static void onGetEnchantmentLevel(GetEnchantmentLevelEvent event) {
        Strike strike = STRIKE.get();
        if (strike == null || strike.looting() <= 0 || event.getStack() != strike.mainHand() || !event.isTargetting(Enchantments.LOOTING)) {
            return;
        }
        event.getHolder(Enchantments.LOOTING).ifPresent(holder -> {
            ItemEnchantments.Mutable enchantments = event.getEnchantments();
            if (enchantments.getLevel(holder) < strike.looting()) {
                enchantments.set(holder, strike.looting());
            }
        });
    }

    /**
     * Lets an enchanted book put Spectral Weapon on any item, not just melee weapons (those go through
     * vanilla), except those in {@link SpectralWeapons#EXCLUDED_ITEMS}. Such items fight weakly; see {@link WeaponProfile}.
     */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        Player player = event.getPlayer();
        if (player == null || left.isEmpty() || !right.is(Items.ENCHANTED_BOOK) || !SpectralConfig.spectralWeaponEnabled()
                || left.is(Items.BOOK) || left.is(Items.ENCHANTED_BOOK) || SpectralWeapons.isMeleeWeapon(left)
                || SpectralWeapons.isExcluded(left)) {
            return;
        }
        Holder<Enchantment> holder = player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ModEnchantments.SPECTRAL_WEAPON).orElse(null);
        if (holder == null) {
            return;
        }
        int book = right.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder);
        if (book <= 0) {
            return;
        }
        ItemEnchantments current = left.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int existing = current.getLevel(holder);
        int result = existing == book ? Math.min(book + 1, holder.value().getMaxLevel()) : Math.max(existing, book);
        if (result <= existing || !EnchantmentHelper.isEnchantmentCompatible(current.keySet(), holder) && existing == 0) {
            return;
        }

        ItemStack output = left.copy();
        EnchantmentHelper.updateEnchantments(output, enchantments -> enchantments.set(holder, result));
        int priorWork = left.getOrDefault(DataComponents.REPAIR_COST, 0) + right.getOrDefault(DataComponents.REPAIR_COST, 0);
        int cost = holder.value().getAnvilCost() * result + priorWork;
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
}
