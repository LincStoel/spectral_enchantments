package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.curse.HauntingGhostEntity;
import com.linc.spectralenchantments.doppelganger.PlayerGhostEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Draws a ghostly copy of a player (a Doppelganger's double, a Curse of Haunting's ghost): a pale {@link GhostSkins}
 * copy of a skin as a glowing ghost, white or the colour of a Quark rune etched on the enchanted armor. A double wears
 * its owner's skin; a haunting ghost (a player's or a monster's) is always Steve, with Herobrine's glowing white eyes.
 * A double idly sways (or plays a Quark emote). A haunting ghost stands and stares, or floats by the monster it haunts
 * as if slowly swimming to it, reaching out to it and now and then raising its arms like a casting evoker. Shaderpacks draw the glowing type opaque, so with one active it uses the lit translucent type
 * instead, as the other ghosts do.
 */
final class PlayerGhostRenderer extends EntityRenderer<PlayerGhostEntity> {
    /** Ticks the double takes to fade in. */
    private static final float APPEAR_TICKS = 6f;
    /** Vanilla's Steve, in the wide model: a haunting ghost wears him, whoever it haunts. */
    private static final PlayerSkin STEVE = new PlayerSkin(
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"), null, null, null, PlayerSkin.Model.WIDE, true);
    /** Just Steve's eyes, white, where they sit on his face. */
    private static final ResourceLocation HAUNTING_EYES = SpectralEnchantments.id("textures/entity/haunting_ghost_eyes.png");
    /** The player renderer's scale. */
    private static final float PLAYER_SCALE = 0.9375f;

    private final PlayerModel<PlayerGhostEntity> wide;
    private final PlayerModel<PlayerGhostEntity> slim;
    /** Quark emotes also animate armor models; these are never drawn. */
    private final HumanoidModel<PlayerGhostEntity> armor;
    private final HumanoidModel<PlayerGhostEntity> armorLegs;

    PlayerGhostRenderer(EntityRendererProvider.Context context) {
        super(context);
        wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        armor = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        armorLegs = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        shadowRadius = 0f;
    }

    @Override
    public void render(PlayerGhostEntity ghost, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        if (ghost.isInvisible()) {
            return;
        }
        float age = ghost.tickCount + partialTick;
        boolean shaderPack = IrisCompat.shaderPackInUse();
        float appear = Mth.clamp(age / APPEAR_TICKS, 0f, 1f);
        double cameraDistance = Math.sqrt(entityRenderDispatcher.distanceToSqr(ghost));
        float visibility = appear * GhostItemDraw.nearFade(cameraDistance);
        int tint = GhostTint.of(ghost.tintItem(), age);
        if (shaderPack) {
            tint = scale(tint, SpectralClientConfig.shaderBrightness());
        }
        int color = FastColor.ARGB32.color((int) (visibility * GhostItemDraw.MAX_ALPHA * 255f), tint);
        HauntingGhostEntity haunting = ghost instanceof HauntingGhostEntity h ? h : null;
        // A haunting ghost that isn't haunting stands on the ground.
        float hover = haunting != null && !haunting.isHaunting() ? 0f : 0.04f + Mth.sin(age * 0.08f) * 0.03f;
        float swell = Mth.lerp(appear, 1.15f, 1f);

        PlayerSkin skin = skinOf(ghost);
        PlayerModel<PlayerGhostEntity> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;

        resetPose(model);
        model.attackTime = 0f;
        model.riding = false;
        model.young = false;
        model.crouching = false;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        // Standing still; HumanoidModel sways the arms with age. A staring ghost looks where it faces; otherwise the
        // head drifts slowly from side to side.
        float lookYaw = ghost.stares() ? 0f : Mth.sin(age * 0.021f) * 20f;
        float lookPitch = ghost.stares() ? Mth.lerp(partialTick, ghost.xRotO, ghost.getXRot())
                : Mth.sin(age * 0.013f + 1f) * 6f;
        model.setupAnim(ghost, 0f, 0f, age, lookYaw, lookPitch);
        if (haunting != null && haunting.isHaunting()) {
            hauntingPose(model, age);
        }
        if (haunting != null && haunting.isCasting()) {
            castingPose(model, age);
        }
        Object emote = QuarkEmotes.animate(ghost, model, armor, armorLegs);


        poseStack.pushPose();
        if (emote != null) {
            QuarkEmotes.transform(emote, poseStack);
        }
        // A gentle hover, and a little swell as it pops in.
        poseStack.translate(0f, hover, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partialTick, ghost.yBodyRotO, ghost.yBodyRot)));
        poseStack.scale(-PLAYER_SCALE * swell, -PLAYER_SCALE * swell, PLAYER_SCALE * swell);
        poseStack.translate(0f, -1.501f, 0f);
        ResourceLocation texture = GhostSkins.get(skin.texture());
        RenderType type = shaderPack ? RenderType.entityTranslucent(texture) : RenderType.entityTranslucentEmissive(texture);
        model.renderToBuffer(poseStack, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        if (haunting != null) {
            glowingEyes(model, poseStack, buffers, visibility);
        }
        poseStack.popPose();
        // No name tag: super.render would only add one.
    }

    /**
     * Herobrine's eyes: the head drawn again with only its eyes, solid glowing white over the faded ghost (as a
     * spider's eyes are drawn), untinted by any rune.
     */
    private static void glowingEyes(PlayerModel<PlayerGhostEntity> model, PoseStack poseStack, MultiBufferSource buffers,
                                    float visibility) {
        model.setAllVisible(false);
        model.head.visible = true;
        int white = FastColor.ARGB32.colorFromFloat(1f, visibility, visibility, visibility);
        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(HAUNTING_EYES)), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, white);
        model.setAllVisible(true);
    }

    /** A Doppelganger's double wears its owner's skin; a Curse of Haunting's ghost, a player's or a monster's, is always Steve. */
    private static PlayerSkin skinOf(PlayerGhostEntity ghost) {
        return ghost instanceof HauntingGhostEntity ? STEVE : skin(ghost.ownerId());
    }

    /**
     * Floating as if slowly swimming to the monster it haunts: arms reaching out to it (following its gaze a little),
     * drifting gently; legs trailing back, kicking softly by turns.
     */
    private static void hauntingPose(PlayerModel<?> model, float age) {
        float reach = -Mth.HALF_PI + model.head.xRot * 0.5f;
        float drift = age * 0.07f;
        model.rightArm.xRot = reach + Mth.sin(drift) * 0.1f;
        model.leftArm.xRot = reach + Mth.sin(drift + 2f) * 0.1f;
        model.rightArm.yRot = -0.1f + Mth.cos(drift * 0.7f) * 0.06f;
        model.leftArm.yRot = 0.1f - Mth.cos(drift * 0.7f + 1f) * 0.06f;
        model.rightArm.zRot = 0.05f;
        model.leftArm.zRot = -0.05f;
        float kick = Mth.sin(age * 0.12f) * 0.15f;
        model.rightLeg.xRot = 0.3f + kick;
        model.leftLeg.xRot = 0.3f - kick;
        model.rightLeg.yRot = 0f;
        model.leftLeg.yRot = 0f;
        model.rightLeg.zRot = 0.04f;
        model.leftLeg.zRot = -0.04f;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    /** Both arms raised out to the sides and waving, as an evoker's when it casts a spell (IllagerModel's pose). */
    private static void castingPose(PlayerModel<?> model, float age) {
        float wave = Mth.cos(age * 0.6662f) * 0.25f;
        model.rightArm.xRot = wave;
        model.leftArm.xRot = wave;
        model.rightArm.yRot = 0f;
        model.leftArm.yRot = 0f;
        model.rightArm.zRot = Mth.PI * 0.75f;
        model.leftArm.zRot = -Mth.PI * 0.75f;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    /** Undoes whatever the last emote (on any double) left in the shared model's parts. */
    private static void resetPose(PlayerModel<?> model) {
        for (ModelPart part : new ModelPart[] {model.head, model.hat, model.body, model.jacket, model.rightArm, model.rightSleeve,
                model.leftArm, model.leftSleeve, model.rightLeg, model.rightPants, model.leftLeg, model.leftPants}) {
            part.resetPose();
        }
    }

    private static PlayerSkin skin(UUID owner) {
        if (owner == null) {
            return DefaultPlayerSkin.get(Util.NIL_UUID);
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }

    private static int scale(int rgb, float factor) {
        int r = Math.round((rgb >> 16 & 0xFF) * factor);
        int g = Math.round((rgb >> 8 & 0xFF) * factor);
        int b = Math.round((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerGhostEntity ghost) {
        return skinOf(ghost).texture();
    }
}
