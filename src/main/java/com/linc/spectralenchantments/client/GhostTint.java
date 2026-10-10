package com.linc.spectralenchantments.client;

import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The colour a ghost (Broad Breaker or the Animated slot overlay) is drawn in: spectral white, or the colour of its enchantment
 * glint when Quark's Color Runes have etched one onto the item.
 */
public final class GhostTint {
    public static final int WHITE = 0xFFFFFF;
    /** Quark stores the etched rune as this component: the rune colour's name. */
    private static final ResourceLocation RUNE_COLOR = ResourceLocation.fromNamespaceAndPath("quark", "rune_color");
    /** Average colour of each of Quark's glint textures, dimmed for the dark ones. */
    private static final Map<String, Integer> RUNES = Map.ofEntries(
            Map.entry("white", 0xFFFFFF),
            Map.entry("orange", 0xFF880A),
            Map.entry("magenta", 0xFF45AA),
            Map.entry("light_blue", 0x4BB1E9),
            Map.entry("yellow", 0xC8CE3E),
            Map.entry("lime", 0x41FF82),
            Map.entry("pink", 0xF47EFF),
            Map.entry("gray", 0x556568),
            Map.entry("light_gray", 0x9A9FAB),
            Map.entry("cyan", 0x3FF3FF),
            Map.entry("purple", 0x7724AF),
            Map.entry("blue", 0x2019FF),
            Map.entry("brown", 0x684300),
            Map.entry("green", 0x01A300),
            Map.entry("red", 0xDB3452),
            Map.entry("black", 0x332A3C));

    @Nullable
    private static Optional<DataComponentType<?>> runeComponent;

    private GhostTint() {}

    /** RGB for the ghost of {@code stack}; {@code age} (ticks) cycles a rainbow rune. */
    public static int of(ItemStack stack, float age) {
        DataComponentType<?> type = runeComponent();
        if (type == null || !(stack.get(type) instanceof String rune)) {
            return WHITE;
        }
        if (rune.equals("rainbow")) {
            return Mth.hsvToRgb((age / 100f) % 1f, 0.55f, 1f) & 0xFFFFFF;
        }
        return RUNES.getOrDefault(rune, WHITE);
    }

    @Nullable
    private static DataComponentType<?> runeComponent() {
        if (runeComponent == null) {
            runeComponent = Optional.ofNullable(BuiltInRegistries.DATA_COMPONENT_TYPE.get(RUNE_COLOR));
        }
        return runeComponent.orElse(null);
    }
}
