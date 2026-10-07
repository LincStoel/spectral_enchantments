package com.linc.spectralenchantments.animated;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.spectral.SpectralWeapons;
import java.util.Comparator;
import javax.annotation.Nullable;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared (client + server) rules for the Animated enchantment. */
public final class Animated {
    private Animated() {}

    public static boolean has(Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SpectralConfig.animatedEnabled() || SpectralWeapons.isExcluded(stack)) {
            return false;
        }
        return level.registryAccess().registry(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.getHolder(ModEnchantments.ANIMATED))
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0)
                .orElse(false);
    }

    /**
     * Whether an animated weapon will fight {@code entity}: hostile mobs only, and not neutral ones (endermen,
     * zombified piglins) unless they are already angry. Never players, armor stands or tamed pets.
     */
    public static boolean isValidTarget(LivingEntity entity) {
        if (!(entity instanceof Enemy) || entity instanceof Player || entity instanceof ArmorStand
                || !entity.isAlive() || entity.isInvulnerable() || !entity.isAttackable()) {
            return false;
        }
        if (entity instanceof NeutralMob neutral && !neutral.isAngry()) {
            return false;
        }
        return !(entity instanceof OwnableEntity pet && pet.getOwnerUUID() != null);
    }

    /** Within {@code radius} of {@code center} (plus a little slack, so it doesn't drop a target at the edge). */
    public static boolean inRange(Vec3 center, LivingEntity entity, double radius, double slack) {
        return entity.getBoundingBox().getCenter().distanceTo(center) - entity.getBbWidth() / 2 <= radius + slack;
    }

    /** Nothing solid between {@code from} and the entity's eyes or middle. */
    public static boolean canSee(Level level, Vec3 from, Entity entity, @Nullable Entity viewer) {
        return clear(level, from, entity.getEyePosition(), viewer) || clear(level, from, entity.getBoundingBox().getCenter(), viewer);
    }

    private static boolean clear(Level level, Vec3 from, Vec3 to, @Nullable Entity viewer) {
        ClipContext clip = viewer != null
                ? new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer)
                : new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        return level.clip(clip).getType() == HitResult.Type.MISS;
    }

    /** The nearest valid target within {@code radius} of {@code center} that can be seen from it, or null. */
    @Nullable
    public static LivingEntity findTarget(Level level, Vec3 center, double radius, @Nullable Entity viewer) {
        AABB box = new AABB(center, center).inflate(radius);
        return level.getEntitiesOfClass(LivingEntity.class, box,
                        entity -> isValidTarget(entity) && inRange(center, entity, radius, 0) && canSee(level, center, entity, viewer))
                .stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)))
                .orElse(null);
    }

    /** The armor stand's hand holding an Animated item, main hand first, or null. */
    @Nullable
    public static InteractionHand animatedHand(ArmorStand stand) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (has(stand.level(), stand.getItemInHand(hand))) {
                return hand;
            }
        }
        return null;
    }

    /** Where an armor stand looks for targets from: about its chest. */
    public static Vec3 standCenter(ArmorStand stand) {
        return stand.position().add(0, stand.getBbHeight() * 0.6, 0);
    }

    /**
     * The transform from the armor stand's feet (in world axes) to the grip of the item in {@code hand}, as the
     * armor stand renderer poses it, ready for the item to be drawn in the matching third-person hand context.
     * Mirrors LivingEntityRenderer, ArmorStandRenderer, ArmorStandModel and ItemInHandLayer.
     */
    public static Matrix4f handTransform(ArmorStand stand, InteractionHand hand, float partialTick) {
        boolean left = isLeftArm(stand, hand);
        float bodyYaw = Mth.rotLerp(partialTick, stand.yBodyRotO, stand.yBodyRot);
        float scale = stand.getScale();
        Rotations pose = left ? stand.getLeftArmPose() : stand.getRightArmPose();
        Matrix4f m = new Matrix4f();
        m.scale(scale);
        m.rotateY((180f - bodyYaw) * Mth.DEG_TO_RAD);
        float wobble = (float) (stand.level().getGameTime() - stand.lastHit) + partialTick;
        if (wobble < 5f) {
            m.rotateY(Mth.sin(wobble / 1.5f * Mth.PI) * 3f * Mth.DEG_TO_RAD);
        }
        m.scale(-1f, -1f, 1f);
        m.translate(0f, -1.501f, 0f);
        // The arm: pivot at the shoulder, rotated by its pose (ModelPart.translateAndRotate).
        m.translate((left ? 5f : -5f) / 16f, 2f / 16f, 0f);
        m.rotate(new Quaternionf().rotationZYX(pose.getZ() * Mth.DEG_TO_RAD, pose.getY() * Mth.DEG_TO_RAD, pose.getX() * Mth.DEG_TO_RAD));
        // ItemInHandLayer's hold.
        m.rotateX(-90f * Mth.DEG_TO_RAD);
        m.rotateY(180f * Mth.DEG_TO_RAD);
        m.translate((left ? -1f : 1f) / 16f, 0.125f, -0.625f);
        return m;
    }

    /** The armor stand's left arm holds its off hand item (armor stands are right-handed). */
    public static boolean isLeftArm(ArmorStand stand, InteractionHand hand) {
        boolean rightHanded = stand.getMainArm() == HumanoidArm.RIGHT;
        return (hand == InteractionHand.MAIN_HAND) != rightHanded;
    }

    /** World position of the item's grip in the armor stand's {@code hand}. */
    public static Vec3 handPosition(ArmorStand stand, InteractionHand hand, float partialTick) {
        Vector3f grip = handTransform(stand, hand, partialTick).transformPosition(new Vector3f());
        return stand.getPosition(partialTick).add(grip.x, grip.y, grip.z);
    }
}
