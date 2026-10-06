package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.spectral.SpectralWeaponEntity;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.ref.WeakReference;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * While the local player's spectral weapon is out, its item's slot icon (hotbar and inventory) is washed
 * with a ghostly overlay: the item drawn again over itself in its ghost colour, fading with the weapon.
 */
final class SpectralSlotOverlay {
    /** How strongly the overlay covers the icon, 0-1 (scaled by the ghost's own maximum alpha). */
    private static final float STRENGTH = 0.8f;

    private static WeakReference<SpectralWeaponEntity> localWeapon = new WeakReference<>(null);


    private SpectralSlotOverlay() {}

    /** Any item can carry the enchantment (the anvil allows it), so every item gets the decorator. */
    static void register(RegisterItemDecorationsEvent event) {
        IItemDecorator decorator = SpectralSlotOverlay::render;
        for (Item item : BuiltInRegistries.ITEM) {
            event.register(item, decorator);
        }
    }

    /** Finds the local player's weapon once a tick, rather than per slot drawn. */
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        SpectralWeaponEntity found = null;
        if (mc.level != null && mc.player != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof SpectralWeaponEntity weapon && weapon.getOwner() == mc.player
                        && (found == null || !weapon.isFading())) {
                    found = weapon;
                }
            }
        }
        localWeapon = new WeakReference<>(found);
    }

    /** The local player's spectral weapon (possibly fading), or null when none is out. */
    @Nullable
    static SpectralWeaponEntity activeWeapon() {
        SpectralWeaponEntity weapon = localWeapon.get();
        return weapon != null && !weapon.isRemoved() ? weapon : null;
    }

    private static boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        SpectralWeaponEntity weapon = activeWeapon();
        // The very stack in the weapon's slot, so a look-alike elsewhere in the inventory stays plain.
        if (player == null || weapon == null || stack != player.getInventory().getItem(weapon.getSlot())) {
            return false;
        }
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        float fade = Mth.lerp(partialTick, weapon.fadeO, weapon.fade);
        GhostTextures.Ghost ghost = fade > 0f ? GhostTextures.get(stack) : null;
        if (ghost == null) {
            return false;
        }

        // Same placement as GuiGraphics.renderItem, a hair in front of the icon.
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, player, 0);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 8f, y + 8f, 151f);
        pose.scale(16f, -16f, 16f);
        boolean flat = !model.usesBlockLight();
        if (flat) {
            Lighting.setupForFlatItems();
        }
        // The ghost's entity shader applies world fog, and GUI geometry sits thousands of blocks from the
        // camera: without this the overlay comes out in the fog colour.
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        // Without its glint: the glint pass would be drawn through the same ghost buffer and garble the quads.
        ItemStack plain = stack.copyWithCount(1);
        plain.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        GhostItemDraw.draw(mc, ghost, plain, fade * STRENGTH, GhostTint.of(stack, weapon.tickCount + partialTick), false,
                ItemDisplayContext.GUI, pose, graphics.bufferSource(), 0);
        GhostItemDraw.endBatch(graphics.bufferSource(), ghost);
        RenderSystem.setShaderFogStart(fogStart);
        if (flat) {
            Lighting.setupFor3DItems();
        }
        pose.popPose();
        return true;
    }
}
