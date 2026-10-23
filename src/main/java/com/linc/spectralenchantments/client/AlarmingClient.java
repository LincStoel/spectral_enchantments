package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.joml.Vector3f;

/**
 * Alarming, like Sting: while a monster is near, every Alarming sword the local player carries glows and buzzes. It is
 * all on the client, which already knows the entities around the player, so nothing is sent.
 *
 * <p>Each buzz is a short burst of jiggling, and the buzzes come closer together, and the glow brightens, as the nearest
 * monster closes in. A slot icon gets its glow as a ghostly wash in the glow colour drawn over it; a sword in hand is
 * tinted and lit by the glow instead, which adds no geometry, so shaderpacks and Better Combat draw it as usual. Under an
 * Iris shaderpack a sword in hand also lights its surroundings, like a held torch, in the glow colour.
 */
public final class AlarmingClient {
    /** Mobs that never set an Alarming sword off, though they count as monsters. */
    private static final TagKey<EntityType<?>> IGNORED =
            TagKey.create(Registries.ENTITY_TYPE, SpectralEnchantments.id("alarming/ignored"));
    /** How often the nearest monster is looked for, in ticks. */
    private static final int SCAN_INTERVAL = 4;
    private static final int BUZZ_TICKS = 6;
    private static final float WIGGLES_PER_BUZZ = 3f;
    /** Ticks from one buzz to the next with the monster at the edge of the radius, and within a block. */
    private static final float FAR_INTERVAL = 30f;
    private static final float NEAR_INTERVAL = 10f;
    /** The glow (0-1) between buzzes with the monster at the edge of the radius, and within a block. */
    private static final float FAR_GLOW = 0.35f;
    private static final float NEAR_GLOW = 0.75f;
    /** Added to the glow at the height of each buzz. */
    private static final float BUZZ_GLOW = 0.25f;
    /** How far a buzz turns the sword, in degrees, at its height. */
    private static final float GUI_JIGGLE = 6f;
    private static final float FIRST_PERSON_JIGGLE = 2.5f;
    private static final float THIRD_PERSON_JIGGLE = 5f;
    /** While the nearest monster is out of sight, the jiggle, the wiggles in a buzz and how often buzzes come are all scaled by this. */
    private static final float HIDDEN_SCALE = 0.5f;
    /** The icon's glow sits this far in front of it, in GUI pixels, so it is never hidden by the icon itself. */
    private static final float GUI_GLOW_DEPTH = 1f;

    /** The very stacks (not look-alikes) of the local player's Alarming swords; empty unless a monster is near. */
    private static final Set<ItemStack> ALARMED = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Where {@link #scan} drops the monster within a block that ends it; emptied straight after. */
    private static final List<Entity> SCAN_RESULT = new ArrayList<>(1);
    /** The nearest monster found by a {@link #scan} so far, and its squared distance; dropped when the scan ends. */
    private static Entity nearest;
    private static double nearestSqr;
    private static boolean monsterNear;
    /** Whether the nearest monster is out of the player's sight, behind a block. */
    private static boolean hidden;
    /** {@link #hidden} as of the latest buzz's start, so a buzz keeps one pace even if the monster steps into view. */
    private static boolean buzzHidden;
    /** 0 with the nearest monster at the edge of the radius, 1 within a block of the player. */
    private static float proximity;
    private static int ticks;
    private static int buzzStart = Integer.MIN_VALUE / 2;
    private static int nextBuzz;
    /** The glow colour as Iris takes a light colour (0-1 per channel), and the RGB it was made from. */
    private static final Vector3f LIGHT_COLOR = new Vector3f();
    private static int lightColorRgb = -1;

    private AlarmingClient() {}

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        ALARMED.clear();
        LocalPlayer player = mc.player;
        Holder<Enchantment> alarming = player == null ? null : ModEnchantments.holder(player.level().registryAccess(), ModEnchantments.ALARMING);
        if (alarming == null || alarming.is(ModEnchantments.DISABLED)) {
            monsterNear = false;
            return;
        }
        ticks++;
        // Rebuilt every tick: the server replaces a slot's stack whenever it changes (a hit costing durability, say).
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            collect(inventory.getItem(slot), alarming);
        }
        collect(player.containerMenu.getCarried(), alarming);
        if (ALARMED.isEmpty()) {
            monsterNear = false;
            return;
        }
        if (ticks % SCAN_INTERVAL == 0) {
            scan(player);
        }
        if (!monsterNear) {
            ALARMED.clear();
            return;
        }
        if (ticks >= nextBuzz) {
            buzzStart = ticks;
            buzzHidden = hidden;
            float interval = Mth.lerp(proximity, FAR_INTERVAL, NEAR_INTERVAL);
            nextBuzz = ticks + Math.round(hidden ? interval / HIDDEN_SCALE : interval);
        }
    }

    private static void collect(ItemStack stack, Holder<Enchantment> alarming) {
        if (!stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(alarming, stack) > 0) {
            ALARMED.add(stack);
        }
    }

    /** Finds the nearest monster within the radius, through walls, and whether the player can see it. */
    private static void scan(LocalPlayer player) {
        int radius = SpectralConfig.alarmingRadius();
        nearest = null;
        nearestSqr = Double.MAX_VALUE;
        // The filter tracks the nearest monster itself and accepts only one within a block, the closest that counts,
        // so the search stops at the first of those.
        player.level().getEntities(EntityTypeTest.forClass(Entity.class), player.getBoundingBox().inflate(radius), entity -> {
            if (!(entity instanceof Enemy) || !entity.isAlive() || entity.getType().is(IGNORED)) {
                return false;
            }
            double distanceSqr = entity.distanceToSqr(player);
            if (distanceSqr < nearestSqr) {
                nearest = entity;
                nearestSqr = distanceSqr;
            }
            return nearestSqr <= 1.0;
        }, SCAN_RESULT, 1);
        SCAN_RESULT.clear();
        monsterNear = nearestSqr <= (double) radius * radius;
        if (monsterNear) {
            proximity = 1f - Mth.clamp(((float) Math.sqrt(nearestSqr) - 1f) / (radius - 1f), 0f, 1f);
            // One ray, eye to eye, for the nearest monster only: a nearer hidden one damps the jiggle even if a farther
            // one is in plain view.
            hidden = !player.hasLineOfSight(nearest);
        }
        nearest = null;
    }

    /** Whether {@code stack} is one of the local player's Alarming swords, with a monster near. Called for every item drawn. */
    public static boolean alarmed(ItemStack stack) {
        return !ALARMED.isEmpty() && ALARMED.contains(stack);
    }

    /**
     * The light {@code stack}, in the local player's hand, gives off under an Iris shaderpack: 0 unless it is
     * {@link #alarmed}, rising to the configured maximum as the nearest monster closes to within a block. Iris asks every
     * frame, for each hand.
     */
    public static int heldLight(ItemStack stack) {
        return alarmed(stack) ? Math.round(proximity * SpectralClientConfig.alarmingShaderLight()) : 0;
    }

    /** The colour of {@link #heldLight}: the glow colour. Iris keeps the vector it is given, so it changes only with the config. */
    public static Vector3f heldLightColor() {
        int color = SpectralClientConfig.alarmingGlowColor();
        if (color != lightColorRgb) {
            lightColorRgb = color;
            LIGHT_COLOR.set((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f);
        }
        return LIGHT_COLOR;
    }

    /** Draws an {@link #alarmed} stack in place of {@code ItemRenderer.render}, which {@code original} calls. */
    public static void render(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose, MultiBufferSource buffers,
                              int light, int overlay, BakedModel model, Operation<Void> original) {
        Minecraft mc = Minecraft.getInstance();
        float progress = (ticks - buzzStart + mc.getTimer().getGameTimeDeltaPartialTick(true)) / BUZZ_TICKS;
        float buzz = progress < 1f ? Mth.sin(progress * Mth.PI) : 0f;
        float glow = Mth.lerp(proximity, FAR_GLOW, NEAR_GLOW) + BUZZ_GLOW * buzz;
        int color = SpectralClientConfig.alarmingGlowColor();

        pose.pushPose();
        if (buzz > 0f) {
            float jiggle = context == ItemDisplayContext.GUI ? GUI_JIGGLE
                    : context.firstPerson() ? FIRST_PERSON_JIGGLE : THIRD_PERSON_JIGGLE;
            float wiggles = WIGGLES_PER_BUZZ;
            if (buzzHidden) {
                jiggle *= HIDDEN_SCALE;
                wiggles *= HIDDEN_SCALE;
            }
            pose.mulPose(Axis.ZP.rotationDegrees(jiggle * buzz * Mth.sin(progress * wiggles * Mth.TWO_PI)));
        }
        if (context == ItemDisplayContext.GUI) {
            original.call(stack, context, leftHand, pose, buffers, light, overlay, model);
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                drawIconGlow(mc, stack, pose, source, glow, color);
            }
        } else {
            original.call(stack, context, leftHand, pose, glowing(buffers, glow, color), light, overlay, model);
        }
        pose.popPose();
    }

    /** The icon washed with its ghost in the glow colour, as {@link SpectralSlotOverlay} washes an Animated weapon's. */
    private static void drawIconGlow(Minecraft mc, ItemStack stack, PoseStack pose, MultiBufferSource.BufferSource source,
                                     float glow, int color) {
        GhostTextures.Ghost ghost = GhostTextures.get(stack);
        if (ghost == null) {
            return;
        }
        // The icon goes down first, or it would be drawn over its glow.
        source.endBatch();
        pose.pushPose();
        pose.translate(0f, 0f, GUI_GLOW_DEPTH / 16f);
        // As in SpectralSlotOverlay: without this the ghost's shader fogs GUI geometry into the fog colour.
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        // Without its glint, which would be drawn through the ghost buffer and garble it.
        ItemStack plain = stack.copyWithCount(1);
        plain.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        GhostItemDraw.draw(mc, ghost, plain, glow, color, false, ItemDisplayContext.GUI, pose, source, 0);
        GhostItemDraw.endBatch(source, ghost);
        RenderSystem.setShaderFogStart(fogStart);
        pose.popPose();
    }

    /** {@code buffers}, with what is drawn into them tinted toward {@code color} and lit up, both by {@code glow} (0-1). */
    private static MultiBufferSource glowing(MultiBufferSource buffers, float glow, int color) {
        float strength = Mth.clamp(glow, 0f, 1f);
        int red = Math.round(Mth.lerp(strength, 255f, color >> 16 & 0xFF));
        int green = Math.round(Mth.lerp(strength, 255f, color >> 8 & 0xFF));
        int blue = Math.round(Mth.lerp(strength, 255f, color & 0xFF));
        int lit = Math.round(strength * (LightTexture.FULL_BRIGHT & 0xFFFF));
        return type -> new GlowVertexConsumer(buffers.getBuffer(type), red, green, blue, lit);
    }

    /** Multiplies each vertex's colour by the tint (0-255 per channel) and raises its light to at least {@code lit}. */
    private record GlowVertexConsumer(VertexConsumer delegate, int red, int green, int blue, int lit) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(r * red / 255, g * green / 255, b * blue / 255, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(Math.max(u, lit), Math.max(v, lit));
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
