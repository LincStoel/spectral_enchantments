package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.animated.Animated;
import com.linc.spectralenchantments.animated.AnimatedWeaponEntity;
import com.linc.spectralenchantments.network.AnimatedRecallPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of Animated.
 *
 * <ul>
 *   <li>While an armor stand's weapon is out, the stand's hand shows a faint ghost of it in place of the item.</li>
 *   <li>Clicking with the local player's deployed weapon in hand calls it back (see {@link AnimatedRecallPayload}).</li>
 *   <li>Tracks the local player's deployed weapon for the inventory overlay ({@link SpectralSlotOverlay}).</li>
 * </ul>
 */
final class AnimatedClient {
    /** How visible the ghost in an armor stand's empty hand is, 0-1. */
    private static final float STAND_GHOST_VISIBILITY = 0.45f;

    /** Armor stand id to the weapon it has sent out. */
    private static final Map<Integer, AnimatedWeaponEntity> GUARDS = new HashMap<>();
    private static WeakReference<AnimatedWeaponEntity> localDeployed = new WeakReference<>(null);
    /** The deployed weapon a recall was last asked for, so a held button asks once. */
    private static int recallSentFor = -1;

    /** Armor stand whose hand item is hidden for the current render, and the item to put back. */
    @Nullable
    private static ArmorStand hiddenStand;
    private static int hiddenIndex;
    private static ItemStack hiddenItem = ItemStack.EMPTY;

    private AnimatedClient() {}

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (hiddenStand != null) {
            // A render that never finished: put the item back.
            hiddenStand.handItems.set(hiddenIndex, hiddenItem);
            hiddenStand = null;
            hiddenItem = ItemStack.EMPTY;
        }
        GUARDS.clear();
        AnimatedWeaponEntity deployed = null;
        if (mc.level != null && mc.player != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof AnimatedWeaponEntity weapon) || weapon.isRemoved()) {
                    continue;
                }
                Entity home = weapon.getHome();
                if (weapon.isDeployed()) {
                    if (home == mc.player && !weapon.isReturning()) {
                        deployed = weapon;
                    }
                } else if (home instanceof ArmorStand) {
                    GUARDS.put(home.getId(), weapon);
                }
            }
        }
        localDeployed = new WeakReference<>(deployed);
        if (deployed == null) {
            recallSentFor = -1;
        } else if (mc.player != null && mc.screen == null && mc.options.keyAttack.isDown()
                && deployed.getSlot() == mc.player.getInventory().selected) {
            // Better Combat swings without the click event below; catch a held attack here instead.
            requestRecall(deployed);
        }
    }

    /** The local player's deployed weapon while it is out (not on its way back), or null. */
    @Nullable
    static AnimatedWeaponEntity localDeployed() {
        AnimatedWeaponEntity weapon = localDeployed.get();
        return weapon != null && !weapon.isRemoved() && !weapon.isReturning() ? weapon : null;
    }

    /** Attacking or using with the deployed weapon in hand calls it back instead. */
    static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        AnimatedWeaponEntity weapon = localDeployed();
        if (player == null || weapon == null || !(event.isAttack() || event.isUseItem())) {
            return;
        }
        int slot = event.getHand() == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        if (weapon.getSlot() != slot) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        requestRecall(weapon);
    }

    private static void requestRecall(AnimatedWeaponEntity weapon) {
        if (recallSentFor != weapon.getId()) {
            recallSentFor = weapon.getId();
            PacketDistributor.sendToServer(AnimatedRecallPayload.INSTANCE);
        }
    }

    // ---- The ghost in the armor stand's hand ----

    /** The stand's real item is hidden while its weapon is out; the ghost is drawn in its place at {@link #render}. */
    static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity() instanceof ArmorStand stand) {
            AnimatedWeaponEntity weapon = GUARDS.get(stand.getId());
            if (weapon != null && !weapon.isRemoved()) {
                hiddenStand = stand;
                hiddenIndex = weapon.getHand().ordinal();
                hiddenItem = stand.handItems.get(hiddenIndex);
                stand.handItems.set(hiddenIndex, ItemStack.EMPTY);
            }
        }
    }

    static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (hiddenStand != null && event.getEntity() == hiddenStand) {
            hiddenStand.handItems.set(hiddenIndex, hiddenItem);
            hiddenStand = null;
            hiddenItem = ItemStack.EMPTY;
        }
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || GUARDS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean shaderPack = IrisCompat.shaderPackInUse();
        for (Map.Entry<Integer, AnimatedWeaponEntity> entry : GUARDS.entrySet()) {
            AnimatedWeaponEntity weapon = entry.getValue();
            if (weapon.isRemoved() || !(mc.level.getEntity(entry.getKey()) instanceof ArmorStand stand) || stand.isInvisible()) {
                continue;
            }
            InteractionHand hand = weapon.getHand();
            ItemStack stack = stand.getItemInHand(hand);
            GhostTextures.Ghost ghost = stack.isEmpty() ? null : GhostTextures.get(stack);
            if (ghost == null) {
                continue;
            }
            // Without its glint: the glint pass would be drawn through the same ghost buffer and garble the quads.
            ItemStack plain = stack.copyWithCount(1);
            plain.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            boolean left = Animated.isLeftArm(stand, hand);
            Vec3 feet = stand.getPosition(partialTick);
            float near = GhostItemDraw.nearFade(Animated.handPosition(stand, hand, partialTick).distanceTo(cam));

            poseStack.pushPose();
            poseStack.translate(feet.x - cam.x, feet.y - cam.y, feet.z - cam.z);
            poseStack.mulPose(Animated.handTransform(stand, hand, partialTick));
            GhostItemDraw.draw(mc, ghost, plain, STAND_GHOST_VISIBILITY * near, GhostTint.of(stack, stand.tickCount + partialTick),
                    shaderPack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, poseStack, buffers, stand.getId());
            poseStack.popPose();
            GhostItemDraw.endBatch(buffers, ghost);
        }
    }
}
