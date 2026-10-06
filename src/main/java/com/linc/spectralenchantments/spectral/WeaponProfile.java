package com.linc.spectralenchantments.spectral;

import com.linc.spectralenchantments.compat.BetterCombatCompat;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

/**
 * How a spectral weapon fights: its damage, its attack interval (half the wielder's speed) and the
 * sequence of swings it cycles through.
 *
 * @param full false for items that are not melee weapons (only reachable through the anvil):
 *             they bonk for half a heart, very slowly
 */
public record WeaponProfile(boolean full, float baseDamage, int intervalTicks, List<AttackStep> steps) {
    /** One swing of a combo. {@code upswing} is the fraction of the swing spent winding up before the hit lands. */
    public record AttackStep(AttackStyle style, float damageMultiplier, float upswing, @Nullable ResourceLocation swingSound) {
        static AttackStep of(AttackStyle style) {
            return new AttackStep(style, 1f, 0.5f, null);
        }
    }

    public static final float WEAK_DAMAGE = 1.0f;
    public static final int WEAK_INTERVAL_TICKS = 80;
    /** Player base values of the two attributes. */
    private static final double BASE_ATTACK_DAMAGE = 1.0;
    private static final double BASE_ATTACK_SPEED = 4.0;

    public static WeaponProfile of(ItemStack stack) {
        List<AttackStep> combat = BetterCombatCompat.attacks(stack);
        if (combat == null && !SpectralWeapons.isMeleeWeapon(stack)) {
            return new WeaponProfile(false, WEAK_DAMAGE, WEAK_INTERVAL_TICKS, List.of(AttackStep.of(AttackStyle.BONK)));
        }
        float damage = (float) mainHandValue(stack, Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE);
        double speed = Math.max(0.1, mainHandValue(stack, Attributes.ATTACK_SPEED, BASE_ATTACK_SPEED));
        // Half the wielder's speed: twice the vanilla cooldown of 20 / attackSpeed ticks.
        int interval = Math.max(4, (int) Math.round(2.0 * 20.0 / speed));
        return new WeaponProfile(true, damage, interval, combat != null ? combat : fallbackSteps(stack));
    }

    public AttackStep step(int comboIndex) {
        return steps.get(Math.floorMod(comboIndex, steps.size()));
    }

    /** Swing style by item type, for weapons Better Combat doesn't describe (or when it isn't installed). */
    private static List<AttackStep> fallbackSteps(ItemStack stack) {
        if (stack.is(Items.TRIDENT) || stack.is(Tags.Items.TOOLS_SPEAR)) {
            return List.of(AttackStep.of(AttackStyle.STAB));
        }
        if (stack.is(Items.MACE) || stack.is(Tags.Items.TOOLS_MACE)) {
            return List.of(AttackStep.of(AttackStyle.SMASH));
        }
        if (stack.is(ItemTags.AXES)) {
            return List.of(AttackStep.of(AttackStyle.SLASH_V));
        }
        return List.of(AttackStep.of(AttackStyle.SLASH_H));
    }

    /** The attribute's value for a player holding only this item in the main hand. */
    private static double mainHandValue(ItemStack stack, Holder<Attribute> attribute, double base) {
        double[] add = {0};
        double[] multiplyBase = {0};
        double[] multiplyTotal = {1};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (holder, modifier) -> {
            if (!holder.equals(attribute)) {
                return;
            }
            switch (modifier.operation()) {
                case ADD_VALUE -> add[0] += modifier.amount();
                case ADD_MULTIPLIED_BASE -> multiplyBase[0] += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multiplyTotal[0] *= 1 + modifier.amount();
            }
        });
        double value = base + add[0];
        return (value + value * multiplyBase[0]) * multiplyTotal[0];
    }
}
