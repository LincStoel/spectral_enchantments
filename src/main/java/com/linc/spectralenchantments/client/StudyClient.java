package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.network.StudyPayload;
import com.linc.spectralenchantments.network.StudyReleasePayload;
import com.linc.spectralenchantments.study.EnchantmentStudy;
import com.linc.spectralenchantments.study.StudyMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Studying and copying enchantments at a lectern, as seen: the hands coming together (a {@link StudyArmPose} for
 * everyone, the held items drawn in first person), the purple glow and the amethyst rubbing while studying, the
 * writing jiggle while copying, runes streaming to the lectern, sparks and sounds, and for the player doing it the enchantment's name spelled out in
 * the enchanting table's runes in the action bar. The server says when each starts and ends ({@link StudyPayload}).
 */
public final class StudyClient {
    /** The enchanting table's rune font. */
    private static final ResourceLocation RUNES = ResourceLocation.withDefaultNamespace("alt");
    /** How long the hands take to come together, in ticks. */
    private static final float GATHER_TICKS = 8f;
    /** When the amethyst starts rubbing (studying), in ticks. */
    private static final float RUB_START_TICKS = 10f;
    private static final int GLOW_TINT = 0xB04CFF;
    private static final Vector3f PURPLE = new Vector3f(0.69f, 0.3f, 1f);

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static int ticks;
    /** Just finished: the held use keeps clicking the lectern until it is let go, and shouldn't open it. */
    private static boolean swallowing;

    private record Active(StudyMode mode, BlockPos lectern, int startTick, int duration, Holder<Enchantment> target) {}

    private StudyClient() {}

    public static void handle(StudyPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        boolean own = mc.player != null && payload.playerId() == mc.player.getId();
        switch (payload.kind()) {
            case START -> payload.enchantment().ifPresent(target -> ACTIVE.put(payload.playerId(),
                    new Active(payload.mode(), payload.lectern(), ticks, Math.max(1, payload.durationTicks()), target)));
            case STOP -> {
                if (ACTIVE.remove(payload.playerId()) != null && own) {
                    mc.gui.setOverlayMessage(Component.empty(), false);
                }
            }
            case SUCCESS, CURSED -> {
                ACTIVE.remove(payload.playerId());
                if (own) {
                    swallowing = true;
                    mc.gui.setOverlayMessage(payload.kind() == StudyPayload.Kind.CURSED
                            ? Component.translatable("message.spectral_enchantments." + payload.mode().getSerializedName() + ".failed")
                                    .withStyle(ChatFormatting.RED)
                            : payload.enchantment().map(StudyClient::writtenName).orElse(Component.empty()), false);
                }
            }
        }
    }

    private static Component writtenName(Holder<Enchantment> enchantment) {
        Component name = Enchantment.getFullname(enchantment, 1);
        return enchantment.is(EnchantmentTags.CURSE) ? name : name.copy().withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        swallowing = false;
    }

    /**
     * Takes the click on this side too, so the hand doesn't swing. The server decides whether anything starts; it
     * opens the lectern as usual if not.
     */
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || player != Minecraft.getInstance().player) {
            return;
        }
        if (ACTIVE.containsKey(player.getId()) || swallowing
                || EnchantmentStudy.attempted(player) != null && EnchantmentStudy.isLecternWithBook(event.getLevel().getBlockState(event.getPos()))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            ACTIVE.clear();
            return;
        }
        ticks++;
        boolean holding = mc.options.keyUse.isDown() && mc.screen == null;
        if (!holding) {
            swallowing = false;
        }
        Active own = ACTIVE.get(player.getId());
        if (own != null && !holding) {
            ACTIVE.remove(player.getId());
            mc.gui.setOverlayMessage(Component.empty(), false);
            PacketDistributor.sendToServer(StudyReleasePayload.INSTANCE);
        } else if (own != null) {
            spellRunes(mc, own);
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (Map.Entry<Integer, Active> entry : ACTIVE.entrySet()) {
            if (level.getEntity(entry.getKey()) instanceof Player studying) {
                ambience(level, studying, entry.getValue(), random);
            }
        }
    }

    /** The enchantment's name in runes, a letter at a time, done as the study or copy is. */
    private static void spellRunes(Minecraft mc, Active active) {
        String name = active.target.value().description().getString().toLowerCase(Locale.ROOT);
        float progress = Mth.clamp((ticks - active.startTick) / (float) active.duration, 0f, 1f);
        String shown = name.substring(0, Math.min(name.length(), Mth.ceil(name.length() * progress)));
        mc.gui.setOverlayMessage(Component.literal(shown).withStyle(style -> style.withFont(RUNES).withColor(ChatFormatting.LIGHT_PURPLE)), false);
    }

    /**
     * Enchanting table runes streaming from the item to the lectern's book; purple sparks and amethyst chimes while
     * studying, scribbling while copying.
     */
    private static void ambience(ClientLevel level, Player player, Active active, RandomSource random) {
        float elapsed = ticks - active.startTick;
        Vec3 hands = hands(player, 1f);
        // Enchanting runes fly from where they appear (x + velocity) to x.
        Vec3 book = Vec3.atCenterOf(active.lectern).add((random.nextDouble() - 0.5) * 0.4, 0.4, (random.nextDouble() - 0.5) * 0.4);
        Vec3 from = hands.add((random.nextDouble() - 0.5) * 0.25, (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.25);
        level.addParticle(ParticleTypes.ENCHANT, book.x, book.y, book.z, from.x - book.x, from.y - book.y, from.z - book.z);
        if (active.mode == StudyMode.STUDYING) {
            // Not in your own first-person view, where they would be right in front of the camera; the glow shows there.
            boolean ownView = player == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
            if (ticks % 2 == 0 && !ownView) {
                level.addParticle(new DustParticleOptions(PURPLE, 0.7f), hands.x + (random.nextDouble() - 0.5) * 0.3,
                        hands.y + (random.nextDouble() - 0.5) * 0.2, hands.z + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
            }
            if (elapsed >= RUB_START_TICKS && ticks % 5 == 0) {
                level.playLocalSound(hands.x, hands.y, hands.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f,
                        0.9f + random.nextFloat() * 0.4f, false);
            }
        } else {
            if (ticks % 12 == 0) {
                level.playLocalSound(hands.x, hands.y, hands.z, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.5f,
                        0.9f + random.nextFloat() * 0.2f, false);
            }
        }
    }

    /** About where a player's hands meet in front of them. */
    private static Vec3 hands(Player player, float partialTick) {
        return player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(0.5)).add(0, -0.4, 0);
    }

    private static float elapsed(Active active) {
        return ticks - active.startTick + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // ---- Third person ----

    /** Both arms take the study pose while a player studies or copies. */
    static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (ACTIVE.containsKey(event.getEntity().getId())) {
            HumanoidModel.ArmPose pose = StudyArmPose.STUDY.getValue();
            event.getRenderer().getModel().rightArmPose = pose;
            event.getRenderer().getModel().leftArmPose = pose;
        }
    }

    /**
     * Hands together in front of the chest, as though holding the item in both. Studying rubs the off hand across
     * it; copying jiggles the main hand as it writes.
     */
    static void poseArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        Active active = ACTIVE.get(entity.getId());
        if (active == null) {
            return;
        }
        float t = elapsed(active);
        float gather = smooth(t / GATHER_TICKS);
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float inward = arm == HumanoidArm.RIGHT ? -1f : 1f;
        boolean main = arm == entity.getMainArm();

        float x = -1.05f + model.head.xRot * 0.5f;
        float y = inward * 0.5f + model.head.yRot;
        float z = 0f;
        if (active.mode == StudyMode.STUDYING && !main) {
            float rub = smooth((t - RUB_START_TICKS) / 4f);
            y += 0.22f * Mth.sin(t * 1.3f) * rub;
            x += 0.06f * Mth.cos(t * 2.6f) * rub;
        } else if (active.mode == StudyMode.COPYING && main) {
            x += (0.07f * Mth.sin(t * 2.7f) + 0.035f * Mth.sin(t * 6.3f)) * gather;
            y += 0.09f * Mth.sin(t * 1.9f) * gather;
            z += 0.05f * Mth.sin(t * 4.4f) * gather;
        }
        part.xRot = Mth.lerp(gather, part.xRot, x);
        part.yRot = Mth.lerp(gather, part.yRot, y);
        part.zRot = Mth.lerp(gather, part.zRot, z);
    }

    // ---- First person ----

    /** Draws both held items coming together in front of the camera in place of the usual hands. */
    static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Active active = player == null ? null : ACTIVE.get(player.getId());
        if (active == null) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            drawHands(mc, player, active, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick());
        }
    }

    private static void drawHands(Minecraft mc, LocalPlayer player, Active active, PoseStack poseStack, MultiBufferSource buffers,
                                  int light, float partialTick) {
        float t = ticks - active.startTick + partialTick;
        float gather = smooth(t / GATHER_TICKS);
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        float side = rightHanded ? 1f : -1f;
        boolean studying = active.mode == StudyMode.STUDYING;
        ItemInHandRenderer renderer = mc.getEntityRenderDispatcher().getItemInHandRenderer();
        float glow = studying ? Mth.clamp(t / RUB_START_TICKS, 0f, 1f) * (0.3f + 0.1f * Mth.sin(t * 0.6f)) : 0f;

        // From where vanilla holds each item (side * 0.56, -0.52, -0.72) in toward the middle, a little further off so
        // the two don't fill the view; held as vanilla holds them, so flat items stay face on.
        ItemStack main = player.getMainHandItem();
        poseStack.pushPose();
        float x = Mth.lerp(gather, side * 0.56f, side * 0.30f);
        float y = Mth.lerp(gather, -0.52f, -0.50f);
        float z = Mth.lerp(gather, -0.72f, -0.80f);
        if (!studying) {
            x += side * (0.015f * Mth.sin(t * 2.7f) + 0.008f * Mth.sin(t * 6.3f)) * gather;
            y += 0.012f * Mth.sin(t * 3.9f) * gather;
        }
        poseStack.translate(x, y, z);
        if (!studying) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * 4f * Mth.sin(t * 4.4f) * gather));
        }
        ItemDisplayContext mainContext = rightHanded ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        renderer.renderItem(player, main, mainContext, !rightHanded, poseStack, buffers, light);
        drawGlow(mc, main, mainContext, !rightHanded, glow, poseStack, buffers);
        poseStack.popPose();

        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        // Studying brings the amethyst over to the blade and rubs it up and down along it; copying holds the other
        // hand's item steady beside the writing.
        float ox = Mth.lerp(gather, -side * 0.56f, studying ? side * 0.32f : -side * 0.30f);
        float oy = Mth.lerp(gather, -0.52f, studying ? -0.36f : -0.52f);
        float oz = Mth.lerp(gather, -0.72f, studying ? -1.00f : -0.80f);
        float tilt = 0f;
        if (studying) {
            float rub = smooth((t - RUB_START_TICKS) / 4f) * Mth.sin(t * 1.3f);
            ox -= side * 0.06f * rub;
            oy += 0.05f * rub;
            tilt = 10f * rub;
        }
        poseStack.translate(ox, oy, oz);
        if (studying) {
            // The off hand holds items side on; turn the amethyst to face the camera as it crosses over.
            poseStack.mulPose(Axis.YP.rotationDegrees(-side * 60f * gather));
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * tilt));
        ItemDisplayContext offContext = rightHanded ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        renderer.renderItem(player, off, offContext, rightHanded, poseStack, buffers, light);
        drawGlow(mc, off, offContext, rightHanded, glow, poseStack, buffers);
        poseStack.popPose();
    }

    /** A purple ghost of the item, a touch larger, over it. */
    private static void drawGlow(Minecraft mc, ItemStack stack, ItemDisplayContext context, boolean leftHand, float visibility,
                                 PoseStack poseStack, MultiBufferSource buffers) {
        if (visibility <= 0f || stack.isEmpty() || !(buffers instanceof MultiBufferSource.BufferSource bufferSource)) {
            return;
        }
        GhostTextures.Ghost ghost = GhostTextures.get(stack);
        if (ghost == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.scale(1.04f, 1.04f, 1.04f);
        GhostItemDraw.draw(mc, ghost, stack, visibility, GLOW_TINT, IrisCompat.shaderPackInUse(), context, leftHand, poseStack,
                bufferSource, 0);
        GhostItemDraw.endBatch(bufferSource, ghost);
        poseStack.popPose();
    }
}
