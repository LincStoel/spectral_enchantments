package com.linc.spectralenchantments;

import com.linc.spectralenchantments.index.EnchantingIndexItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SpectralEnchantments.MOD_ID);

    /** Lists the enchanted books on the shelves around it; see {@link EnchantingIndexItem}. Shimmers like an enchanted book. */
    public static final DeferredItem<EnchantingIndexItem> ENCHANTING_INDEX =
            ITEMS.registerItem("enchanting_index", EnchantingIndexItem::new, new Item.Properties().stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    private ModItems() {}
}
