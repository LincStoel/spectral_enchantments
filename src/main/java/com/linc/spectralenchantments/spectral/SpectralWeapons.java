package com.linc.spectralenchantments.spectral;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import com.linc.spectralenchantments.compat.BetterCombatCompat;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared (client + server) rules for the Spectral Weapon enchantment. */
public final class SpectralWeapons {
    /** Items the enchantment applies to normally; anything else can only get it from an anvil and fights weakly. */
    public static final TagKey<Item> SPECTRAL_WEAPON_ITEMS =
            TagKey.create(Registries.ITEM, SpectralEnchantments.id("enchantable/spectral_weapon"));
    /** Items that never take the enchantment (Simply Swords' uniques, by default), even through the anvil. */
    public static final TagKey<Item> EXCLUDED_ITEMS =
            TagKey.create(Registries.ITEM, SpectralEnchantments.id("enchantable/spectral_weapon_excluded"));

    /** Hotbar size; slots 0-8 of the inventory. */
    private static final int HOTBAR_SLOTS = Inventory.getSelectionSize();
    /** How far past its search radius the weapon will chase (and lock on to) the owner's target. */
    public static final double LEASH_SLACK = 2.0;

    /** Rest pose: at the player's left at about shoulder height, a little behind. */
    private static final double REST_SIDE = 0.85;
    private static final double REST_HEIGHT = 1.25;
    private static final double REST_BACK = 0.15;

    private SpectralWeapons() {}

    /**
     * The item that manifests as the player's spectral weapon.
     *
     * @param slot  inventory slot ({@link Inventory#SLOT_OFFHAND} for the off hand)
     */
    public record Selection(int slot, ItemStack stack, int level) {}

    public static int getLevel(Level level, ItemStack stack) {
        if (stack.isEmpty() || !SpectralConfig.spectralWeaponEnabled() || isExcluded(stack)) {
            return 0;
        }
        return level.registryAccess().registry(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.getHolder(ModEnchantments.SPECTRAL_WEAPON))
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
                .orElse(0);
    }

    /** Search radius around the player, in blocks (see {@link SpectralConfig}). */
    public static double radius(int level) {
        return SpectralConfig.spectralWeaponRange(level);
    }

    public static boolean isExcluded(ItemStack stack) {
        return stack.is(EXCLUDED_ITEMS);
    }

    public static boolean isMeleeWeapon(ItemStack stack) {
        return stack.is(SPECTRAL_WEAPON_ITEMS);
    }

    /** Anything Better Combat would let the player swing, or a melee weapon. */
    public static boolean isWieldedWeapon(ItemStack stack) {
        return !stack.isEmpty() && (isMeleeWeapon(stack) || BetterCombatCompat.attacks(stack) != null);
    }

    /**
     * The leftmost enchanted item as the player sees it: the off-hand slot sits left of the hotbar
     * (right of it for left-handed players), then hotbar slots 0-8. The item in the main hand never
     * manifests; it's being wielded. Nor does the off-hand item while another weapon is in the main hand,
     * so it can't be dual-wielded and fight on its own at once.
     */
    @Nullable
    public static Selection select(Player player) {
        Inventory inventory = player.getInventory();
        boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;
        boolean offhandAllowed = !isWieldedWeapon(player.getMainHandItem());
        if (!leftHanded && offhandAllowed) {
            Selection offhand = candidate(player, Inventory.SLOT_OFFHAND);
            if (offhand != null) {
                return offhand;
            }
        }
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            if (slot == inventory.selected) {
                continue;
            }
            Selection hotbar = candidate(player, slot);
            if (hotbar != null) {
                return hotbar;
            }
        }
        return leftHanded && offhandAllowed ? candidate(player, Inventory.SLOT_OFFHAND) : null;
    }

    @Nullable
    private static Selection candidate(Player player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        int level = getLevel(player.level(), stack);
        return level > 0 ? new Selection(slot, stack, level) : null;
    }

    /**
     * Whether the weapon may lock on to something its owner hit. It fights whatever the owner picks, but
     * never players, the owner's own pets, or armor stands.
     */
    public static boolean canLockOn(Player owner, LivingEntity entity) {
        if (entity == owner || entity instanceof Player || entity instanceof ArmorStand
                || !entity.isAlive() || entity.isInvulnerable() || !entity.isAttackable()) {
            return false;
        }
        return !(entity instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID()));
    }

    /** Where the weapon floats when it isn't fighting, at the owner's left. {@code tuck} (0-1) pulls it in while sprinting. */
    public static Vec3 restAnchor(Player owner, float partialTick, float tuck) {
        return restAnchor(owner, owner.getPosition(partialTick), Mth.rotLerp(partialTick, owner.yBodyRotO, owner.yBodyRot), 0, tuck);
    }

    /**
     * The rest spot relative to {@code feet} and facing {@code yawDegrees}, rather than the owner's body:
     * in first person it hangs off the camera, so it holds still in view as the body lags behind the head.
     *
     * @param forward extra distance ahead
     */
    public static Vec3 restAnchor(Player owner, Vec3 feet, float yawDegrees, double forward, float tuck) {
        float yaw = yawDegrees * Mth.DEG_TO_RAD;
        double forwardX = -Mth.sin(yaw);
        double forwardZ = Mth.cos(yaw);
        // The player's right, looking along forward.
        double rightX = -forwardZ;
        double rightZ = forwardX;
        double side = -(REST_SIDE - 0.15 * tuck);
        double back = REST_BACK + 0.25 * tuck - forward;
        double height = owner.getBbHeight() / 1.8 * REST_HEIGHT;
        return feet.add(rightX * side - forwardX * back, height, rightZ * side - forwardZ * back);
    }
}
