package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.StackCooldowns;
import com.linc.spectralenchantments.animated.AnimatedWeaponEntity;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * While the local player's Animated weapon is out, its item's slot icon (hotbar and inventory) is washed with a
 * ghostly overlay: the item drawn again over itself in its ghost colour. A weapon fighting beside the player leaves
 * the icon showing through; a deployed one, out of the player's hands until it comes back, hides it. A stack on its own
 * cooldown (a recalled Animated weapon, Branch Finding) gets vanilla's cooldown sweep.
 */
final class SpectralSlotOverlay {
    /** How strongly the overlay covers the icon of a weapon fighting beside the player, 0-1 (scaled by the ghost's own maximum alpha). */
    private static final float COMPANION_STRENGTH = 0.8f;
    /** A deployed Animated weapon's icon is washed out completely. */
    private static final float DEPLOYED_STRENGTH = 1f;

    private SpectralSlotOverlay() {}

    /** Any item can carry the enchantment (the anvil allows it), so every item gets the decorator. */
    static void register(RegisterItemDecorationsEvent event) {
        IItemDecorator decorator = SpectralSlotOverlay::render;
        for (Item item : BuiltInRegistries.ITEM) {
            event.register(item, decorator);
        }
    }

    private static boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return false;
        }
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        boolean drawn = drawGhosts(graphics, mc, player, stack, x, y, partialTick);
        return drawCooldown(graphics, mc, stack, x, y, partialTick) || drawn;
    }

    /** The sweep vanilla draws over an item on cooldown, for this stack's own cooldown ({@link StackCooldowns}). */
    private static boolean drawCooldown(GuiGraphics graphics, Minecraft mc, ItemStack stack, int x, int y, float partialTick) {
        float left = mc.level == null ? 0f : StackCooldowns.remaining(stack, mc.level, partialTick);
        if (left <= 0f) {
            return false;
        }
        int top = y + Mth.floor(16f * (1f - left));
        graphics.fill(RenderType.guiOverlay(), x, top, x + 16, top + Mth.ceil(16f * left), Integer.MAX_VALUE);
        return true;
    }

    private static boolean drawGhosts(GuiGraphics graphics, Minecraft mc, Player player, ItemStack stack, int x, int y, float partialTick) {
        // The very stack in the weapon's slot, so a look-alike elsewhere in the inventory stays plain.
        AnimatedWeaponEntity companion = AnimatedClient.localCompanion();
        if (companion != null && stack == player.getInventory().getItem(companion.getSlot())) {
            return drawGhost(graphics, mc, player, stack, x, y, COMPANION_STRENGTH, GhostTint.of(stack, companion.tickCount + partialTick));
        }
        // A deployed weapon is out of the player's hands until it comes back.
        AnimatedWeaponEntity deployed = AnimatedClient.localDeployed();
        if (deployed != null && stack == player.getInventory().getItem(deployed.getSlot())) {
            return drawGhost(graphics, mc, player, stack, x, y, DEPLOYED_STRENGTH, GhostTint.of(stack, deployed.tickCount + partialTick));
        }
        return false;
    }

    /** Draws the ghost of {@code stack} over its slot icon at {@code visibility} (0-1). */
    private static boolean drawGhost(GuiGraphics graphics, Minecraft mc, Player player, ItemStack stack, int x, int y,
                                     float visibility, int tint) {
        GhostTextures.Ghost ghost = GhostTextures.get(stack);
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
        GhostItemDraw.draw(mc, ghost, plain, visibility, tint, false, ItemDisplayContext.GUI, pose, graphics.bufferSource(), 0);
        GhostItemDraw.endBatch(graphics.bufferSource(), ghost);
        RenderSystem.setShaderFogStart(fogStart);
        if (flat) {
            Lighting.setupFor3DItems();
        }
        pose.popPose();
        return true;
    }
}
