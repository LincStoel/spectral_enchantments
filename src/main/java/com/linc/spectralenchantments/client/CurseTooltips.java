package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.curse.Curses;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Keeps an item's curses out of its tooltip until it has been in a player's inventory (see {@link Curses}), along
 * with any description line a mod such as Enchantment Descriptions adds beneath them. Creative players see all.
 */
final class CurseTooltips {
    private CurseTooltips() {}

    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (!SpectralConfig.cursesHideUntilHeld() || Curses.isRevealed(stack) || player != null && player.isCreative()) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        for (Object2IntMap.Entry<Holder<Enchantment>> curse : Curses.curses(stack)) {
            Component line = Enchantment.getFullname(curse.getKey(), curse.getIntValue());
            String descKey = curse.getKey().unwrapKey()
                    .map(key -> Util.makeDescriptionId("enchantment", key.location()) + ".desc")
                    .orElse(null);
            for (int i = 0; i < tooltip.size(); i++) {
                if (tooltip.get(i).equals(line)) {
                    tooltip.remove(i);
                    while (descKey != null && i < tooltip.size() && mentions(tooltip.get(i), descKey)) {
                        tooltip.remove(i);
                    }
                    break;
                }
            }
        }
    }

    /** Whether the line is, or contains, the translation {@code key}. */
    private static boolean mentions(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            if (translatable.getKey().equals(key)) {
                return true;
            }
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component inner && mentions(inner, key)) {
                    return true;
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (mentions(sibling, key)) {
                return true;
            }
        }
        return false;
    }
}
