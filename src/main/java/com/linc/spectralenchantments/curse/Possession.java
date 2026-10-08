package com.linc.spectralenchantments.curse;

import com.linc.spectralenchantments.ModComponents;
import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Curse of Possession: the first time a weapon carrying it lands in a player's inventory it tears itself free and
 * turns on them as a {@link PossessedWeaponEntity}. Slain, it may leave the weapon behind, curses lifted and worn
 * almost through; dispelled (Ars Nouveau), it always does, as worn as it was. Either way the freed weapon is Animated.
 */
public final class Possession {
    private Possession() {}

    public static boolean has(Level level, ItemStack stack) {
        return SpectralConfig.possessionCurseEnabled() && level(level, stack, ModEnchantments.POSSESSION_CURSE) > 0;
    }

    /** Whether revealing this item's curses sets it loose. Never in peaceful, where it couldn't fight. */
    static boolean canPossess(ServerPlayer player, ItemStack stack) {
        return has(player.level(), stack) && player.level().getDifficulty() != Difficulty.PEACEFUL;
    }

    /** Takes the item out of the player's {@code slot} and sets it on them. */
    public static PossessedWeaponEntity possess(ServerPlayer player, int slot) {
        ServerLevel level = player.serverLevel();
        ItemStack stack = player.getInventory().removeItemNoUpdate(slot);
        PossessedWeaponEntity weapon = new PossessedWeaponEntity(ModEntities.POSSESSED_WEAPON.get(), level);
        weapon.possess(stack, player);

        // A couple of blocks in front of the player's face, or right above them if that is inside a wall.
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
        Vec3 at = player.getEyePosition().add(look.scale(2.0)).subtract(0, weapon.getBbHeight() / 2, 0);
        float yaw = player.getYRot() + 180f;
        weapon.moveTo(at.x, at.y, at.z, yaw, 0f);
        if (!level.noCollision(weapon)) {
            weapon.moveTo(player.getX(), player.getEyeY() - weapon.getBbHeight() / 2, player.getZ(), yaw, 0f);
        }
        weapon.setYHeadRot(yaw);
        level.addFreshEntity(weapon);
        weapon.setTarget(player);

        Vec3 center = weapon.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SOUL, center.x, center.y, center.z, 16, 0.3, 0.3, 0.3, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 8, 0.2, 0.2, 0.2, 0.01);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.6f, 1.6f);
        player.displayClientMessage(Component.translatable("message.spectral_enchantments.possessed", stack.getHoverName())
                .withStyle(ChatFormatting.DARK_RED), true);
        return weapon;
    }

    /**
     * Health from the durability the item had left: the more, the hardier. A block's comes from how long it takes to
     * mine; an item without durability barely holds together. Never more than a player's (by default).
     */
    public static float maxHealth(ItemStack stack) {
        int max = SpectralConfig.possessionMaxHealth();
        if (!stack.isDamageableItem()) {
            float item = SpectralConfig.possessionItemHealth();
            if (!(stack.getItem() instanceof BlockItem block)) {
                return item;
            }
            float hardness = block.getBlock().defaultDestroyTime();
            return hardness < 0 ? max : (float) Mth.clamp(hardness * SpectralConfig.possessionHealthPerHardness(), item, max);
        }
        int min = SpectralConfig.possessionMinHealth();
        double health = (stack.getMaxDamage() - stack.getDamageValue()) * SpectralConfig.possessionHealthPerDurability();
        return (float) Mth.clamp(health, min, SpectralConfig.possessionMaxHealth());
    }

    /** What a slain possessed weapon leaves: the weapon, freed, with only a sliver of durability. */
    public static ItemStack slainDrop(Level level, ItemStack stack) {
        int damage = stack.getDamageValue();
        if (stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            int left = Math.max(1, (int) Math.round(max * SpectralConfig.possessionDropDurability()));
            damage = Math.max(damage, max - left);
        }
        return freed(level, stack, damage);
    }

    /** What a dispelled possessed weapon leaves: the weapon, freed, as worn as it was. */
    public static ItemStack dispelledDrop(Level level, ItemStack stack) {
        return freed(level, stack, stack.getDamageValue());
    }

    /** The weapon with its curses lifted and {@code damage} worn off, Animated by what possessed it. */
    private static ItemStack freed(Level level, ItemStack stack, int damage) {
        ItemStack freed = Curses.withoutCurses(stack);
        freed.remove(ModComponents.CURSES_REVEALED.get());
        if (freed.isDamageableItem()) {
            freed.setDamageValue(damage);
        }
        if (SpectralConfig.animatedEnabled()) {
            level.registryAccess().registry(Registries.ENCHANTMENT)
                    .flatMap(registry -> registry.getHolder(ModEnchantments.ANIMATED))
                    .ifPresent(animated -> freed.enchant(animated, 1));
        }
        return freed;
    }

    private static int level(Level level, ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return level.registryAccess().registry(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.getHolder(key))
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel((Holder<Enchantment>) holder, stack))
                .orElse(0);
    }
}
