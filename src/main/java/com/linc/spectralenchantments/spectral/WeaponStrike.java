package com.linc.spectralenchantments.spectral;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

/**
 * One hit from a weapon fighting on its own (Spectral Weapon, Animated): damage with the item's enchantments,
 * knockback, post-attack effects, and durability worn off the real item, so Unbreaking (and Mending) apply.
 */
public final class WeaponStrike {
    /** While a hit credited to a player is being dealt: their main-hand stack, and the Looting it should borrow. */
    private record Strike(ItemStack mainHand, int looting) {}

    private static final ThreadLocal<Strike> STRIKE = new ThreadLocal<>();

    private WeaponStrike() {}

    /**
     * @param weapon   the entity landing the hit (the damage's direct entity; its yaw sets the knockback)
     * @param stack    the real item, worn down by the hit
     * @param wielder  who the item counts as being used by, for durability
     * @param credited the player credited with the hit (the damage source's attacker), who borrows the weapon's
     *                 Looting for the loot roll; null when nobody is
     * @return whether the target took damage
     */
    public static boolean hit(Entity weapon, DamageSource source, ItemStack stack, WeaponProfile profile,
                              WeaponProfile.AttackStep step, LivingEntity target, LivingEntity wielder, @Nullable Player credited) {
        ServerLevel level = (ServerLevel) weapon.level();
        float damage = WeaponProfile.WEAK_DAMAGE;
        int looting = 0;
        if (profile.full()) {
            damage = EnchantmentHelper.modifyDamage(level, stack, target, source, profile.baseDamage() * step.damageMultiplier());
            looting = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.LOOTING)
                    .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
                    .orElse(0);
        }

        boolean hurt;
        if (credited != null) {
            STRIKE.set(new Strike(credited.getMainHandItem(), looting));
        }
        try {
            hurt = target.hurt(source, damage);
        } finally {
            STRIKE.remove();
        }
        if (!hurt) {
            return false;
        }

        if (profile.full()) {
            float knockback = EnchantmentHelper.modifyKnockback(level, stack, target, source, 0f);
            if (knockback > 0f) {
                float yaw = weapon.getYRot() * Mth.DEG_TO_RAD;
                target.knockback(knockback * 0.5f, Mth.sin(yaw), -Mth.cos(yaw));
            }
            EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, stack);
        }
        if (!stack.isEmpty() && wielder instanceof Player player) {
            if (stack.hurtEnemy(target, player)) {
                stack.postHurtEnemy(target, player);
            }
        } else if (!stack.isEmpty() && stack.getItem().hurtEnemy(stack, target, wielder)) {
            // The stack's own versions take a player (for stats); a non-player wielder goes straight to the item.
            stack.getItem().postHurtEnemy(stack, target, wielder);
        }
        if (credited != null) {
            credited.setLastHurtMob(target);
        }

        var at = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, target.getBbWidth() * 0.3, target.getBbHeight() * 0.25, target.getBbWidth() * 0.3, 0.1);
        level.playSound(null, at.x, at.y, at.z, profile.full() ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                SoundSource.PLAYERS, 0.45f, 1.3f);
        return true;
    }

    /** Loot is rolled for the credited player's main-hand item; during a hit it borrows the weapon's Looting. */
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
}
