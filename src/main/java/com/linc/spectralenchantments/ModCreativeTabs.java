package com.linc.spectralenchantments;

import java.util.Comparator;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SpectralEnchantments.MOD_ID);

    /**
     * Every level of each of the mod's enchantments as a book, curses last; switched-off ones are left out. The books
     * also appear in the vanilla Ingredients tab with the rest, as for any enchantment.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ENCHANTMENTS = TABS.register("enchantments", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.spectral_enchantments"))
            .icon(() -> new ItemStack(Items.ENCHANTED_BOOK))
            .displayItems((parameters, output) -> parameters.holders().lookup(Registries.ENCHANTMENT).ifPresent(lookup -> lookup.listElements()
                    .filter(holder -> holder.key().location().getNamespace().equals(SpectralEnchantments.MOD_ID) && !holder.is(ModEnchantments.DISABLED))
                    .sorted(Comparator.<Holder.Reference<Enchantment>, Boolean>comparing(holder -> holder.is(EnchantmentTags.CURSE))
                            .thenComparing(holder -> holder.key().location().getPath()))
                    .forEach(holder -> {
                        for (int level = holder.value().getMinLevel(); level <= holder.value().getMaxLevel(); level++) {
                            output.accept(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, level)),
                                    CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
                        }
                    })))
            .build());

    private ModCreativeTabs() {}
}
