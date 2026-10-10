package com.linc.spectralenchantments.compat;

import com.linc.spectralenchantments.spectral.AttackStyle;
import com.linc.spectralenchantments.spectral.WeaponProfile;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Optional Better Combat integration, by reflection so Better Combat stays a soft dependency.
 *
 * <p>Reads the weapon's attack combo (hitbox shape, damage multiplier, upswing, swing sound) so an
 * Animated weapon slashes with slashing weapons and stabs with stabbing ones. Simply Swords describes
 * its weapons through Better Combat's weapon_attributes data, so it is covered here too.
 */
public final class BetterCombatCompat {
    @Nullable
    private static final Method GET_ATTRIBUTES;
    private static final Method ATTACKS;
    private static final Method HITBOX;
    private static final Method DAMAGE_MULTIPLIER;
    private static final Method UPSWING;
    private static final Method ANIMATION;
    private static final Method SWING_SOUND;
    private static final Method SOUND_ID;

    static {
        Method getAttributes = null;
        Method attacks = null;
        Method hitbox = null;
        Method damageMultiplier = null;
        Method upswing = null;
        Method animation = null;
        Method swingSound = null;
        Method soundId = null;
        if (ModList.get() != null && ModList.get().isLoaded("bettercombat")) {
            try {
                Class<?> attributes = Class.forName("net.bettercombat.api.WeaponAttributes");
                Class<?> attack = Class.forName("net.bettercombat.api.WeaponAttributes$Attack");
                attacks = attributes.getMethod("attacks");
                hitbox = attack.getMethod("hitbox");
                damageMultiplier = attack.getMethod("damageMultiplier");
                upswing = attack.getMethod("upswing");
                animation = attack.getMethod("animation");
                swingSound = attack.getMethod("swingSound");
                soundId = Class.forName("net.bettercombat.api.WeaponAttributes$Sound").getMethod("id");
                getAttributes = Class.forName("net.bettercombat.logic.WeaponRegistry").getMethod("getAttributes", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                getAttributes = null;
            }
        }
        GET_ATTRIBUTES = getAttributes;
        ATTACKS = attacks;
        HITBOX = hitbox;
        DAMAGE_MULTIPLIER = damageMultiplier;
        UPSWING = upswing;
        ANIMATION = animation;
        SWING_SOUND = swingSound;
        SOUND_ID = soundId;
    }

    /** Per item, whether Better Combat describes it: asked every tick for held items, so worth remembering. */
    private static final Map<Item, Boolean> WEAPONS = new ConcurrentHashMap<>();

    private BetterCombatCompat() {}

    /** Whether Better Combat would let a player swing this item (it describes an attack combo for it). */
    public static boolean isWeapon(ItemStack stack) {
        if (GET_ATTRIBUTES == null || stack.isEmpty()) {
            return false;
        }
        return WEAPONS.computeIfAbsent(stack.getItem(), item -> attacks(stack) != null);
    }

    /** Better Combat reloads its weapon data with the data packs (and syncs it on joining a server). */
    public static void clearCache() {
        WEAPONS.clear();
    }

    /** The weapon's Better Combat attack combo, or null if Better Combat is absent or doesn't know this item. */
    @Nullable
    public static List<WeaponProfile.AttackStep> attacks(ItemStack stack) {
        if (GET_ATTRIBUTES == null || stack.isEmpty()) {
            return null;
        }
        try {
            Object attributes = GET_ATTRIBUTES.invoke(null, stack);
            if (attributes == null) {
                return null;
            }
            Object array = ATTACKS.invoke(attributes);
            if (array == null || Array.getLength(array) == 0) {
                return null;
            }
            List<WeaponProfile.AttackStep> steps = new ArrayList<>();
            for (int i = 0; i < Array.getLength(array); i++) {
                Object attack = Array.get(array, i);
                if (attack != null) {
                    steps.add(toStep(attack));
                }
            }
            return steps.isEmpty() ? null : List.copyOf(steps);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static WeaponProfile.AttackStep toStep(Object attack) throws ReflectiveOperationException {
        Object shape = HITBOX.invoke(attack);
        String animation = String.valueOf(ANIMATION.invoke(attack)).toLowerCase(Locale.ROOT);
        // Slams are named by their animation whatever their hitbox (the vanilla mace and hammers slam with a forward box).
        boolean slam = animation.contains("slam") || animation.contains("smash");
        AttackStyle style = slam ? AttackStyle.SMASH : switch (shape instanceof Enum<?> e ? e.name() : "") {
            case "FORWARD_BOX" -> AttackStyle.STAB;
            case "VERTICAL_PLANE" -> AttackStyle.SLASH_V;
            default -> animation.contains("stab") || animation.contains("thrust") ? AttackStyle.STAB : AttackStyle.SLASH_H;
        };
        float multiplier = ((Number) DAMAGE_MULTIPLIER.invoke(attack)).floatValue();
        float upswing = ((Number) UPSWING.invoke(attack)).floatValue();
        ResourceLocation sound = null;
        Object swing = SWING_SOUND.invoke(attack);
        if (swing != null) {
            Object id = SOUND_ID.invoke(swing);
            if (id != null) {
                sound = ResourceLocation.tryParse(id.toString());
            }
        }
        return new WeaponProfile.AttackStep(style, multiplier > 0 ? multiplier : 1f,
                upswing > 0.05f && upswing < 0.95f ? upswing : 0.5f, sound);
    }
}
