package com.linc.spectralenchantments.animated;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.compat.BetterCombatCompat;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    /** Weapons that are thrown (tridents, spears): they wander off as far as a thrown weapon may, however they were left. */
    public static final TagKey<Item> THROWN_WEAPONS = TagKey.create(Registries.ITEM, SpectralEnchantments.id("animated/thrown_weapons"));

    /** The melee weapons Animated applies to normally; anything else can only get it from an anvil and fights weakly. */
    public static final TagKey<Item> MELEE_WEAPONS = TagKey.create(Registries.ITEM, SpectralEnchantments.id("melee_weapons"));
    /** Items that never take the enchantment (Simply Swords' uniques, by default), even through the anvil. */
    public static final TagKey<Item> EXCLUDED_ITEMS = TagKey.create(Registries.ITEM, SpectralEnchantments.id("enchantable/animated_excluded"));

    /** How far past its search radius a weapon will chase (and lock on to) its owner's target. */
    public static final double LEASH_SLACK = 2.0;

    /** Hotbar size; slots 0-8 of the inventory. */
    private static final int HOTBAR_SLOTS = Inventory.getSelectionSize();

    /** Rest pose of a weapon in the hotbar: at the player's left at about shoulder height, a little behind. */
    private static final double REST_SIDE = 0.85;
    private static final double REST_HEIGHT = 1.25;
    private static final double REST_BACK = 0.15;

    private Animated() {}

    /**
     * The hotbar item that fights beside its player.
     *
     * @param slot  inventory slot ({@link Inventory#SLOT_OFFHAND} for the off hand)
     */
    public record Selection(int slot, ItemStack stack) {}

    /** How far the owner may stray before the weapon flies back: further for thrown weapons. */
    public static int recallDistance(ItemStack stack, boolean thrown) {
        return thrown || stack.is(THROWN_WEAPONS) ? SpectralConfig.animatedThrownRecallDistance() : SpectralConfig.animatedRecallDistance();
    }

    public static boolean has(Level level, ItemStack stack) {
        return stack != null && !stack.isEmpty() && !isExcluded(stack) && ModEnchantments.has(level, ModEnchantments.ANIMATED, stack);
    }

    public static boolean isExcluded(ItemStack stack) {
        return stack.is(EXCLUDED_ITEMS);
    }

    public static boolean isMeleeWeapon(ItemStack stack) {
        return stack.is(MELEE_WEAPONS);
    }

    /** Anything Better Combat would let the player swing, or a melee weapon. */
    public static boolean isWieldedWeapon(ItemStack stack) {
        return !stack.isEmpty() && (isMeleeWeapon(stack) || BetterCombatCompat.isWeapon(stack));
    }

    /** How far from its owner a weapon in the hotbar fights, in blocks. */
    public static double companionRange() {
        return SpectralConfig.animatedCompanionRange();
    }

    /**
     * The leftmost Animated item as the player sees it: the off-hand slot sits left of the hotbar (right of it for
     * left-handed players), then hotbar slots 0-8. The item in the main hand never fights beside the player; it's
     * being wielded. Nor does the off-hand item while another weapon is in the main hand, so it can't be
     * dual-wielded and fight on its own at once. Items {@code busy} (already out as a guard) are skipped.
     */
    @Nullable
    public static Selection select(Player player, Predicate<ItemStack> busy) {
        Inventory inventory = player.getInventory();
        boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;
        boolean offhandAllowed = !isWieldedWeapon(player.getMainHandItem());
        if (!leftHanded && offhandAllowed) {
            Selection offhand = candidate(player, Inventory.SLOT_OFFHAND, busy);
            if (offhand != null) {
                return offhand;
            }
        }
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            if (slot == inventory.selected) {
                continue;
            }
            Selection hotbar = candidate(player, slot, busy);
            if (hotbar != null) {
                return hotbar;
            }
        }
        return leftHanded && offhandAllowed ? candidate(player, Inventory.SLOT_OFFHAND, busy) : null;
    }

    @Nullable
    public static Selection select(Player player) {
        return select(player, stack -> false);
    }

    @Nullable
    private static Selection candidate(Player player, int slot, Predicate<ItemStack> busy) {
        ItemStack stack = player.getInventory().getItem(slot);
        return has(player.level(), stack) && !busy.test(stack) ? new Selection(slot, stack) : null;
    }

    /**
     * Whether a weapon at its owner's side may lock on to something the owner hit. It fights whatever the owner
     * picks, but never players, the owner's own pets, or armor stands.
     */
    public static boolean canLockOn(Player owner, LivingEntity entity) {
        if (entity == owner || entity instanceof Player || entity instanceof ArmorStand
                || !entity.isAlive() || entity.isInvulnerable() || !entity.isAttackable()) {
            return false;
        }
        return !(entity instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID()));
    }

    /** Where a weapon floats when it isn't fighting, at the owner's left. {@code tuck} (0-1) pulls it in while sprinting. */
    public static Vec3 restAnchor(Player owner, float partialTick, float tuck) {
        return restAnchor(owner, owner.getPosition(partialTick), Mth.rotLerp(partialTick, owner.yBodyRotO, owner.yBodyRot), 0, tuck);
    }

    /**
     * The rest spot relative to {@code feet} and facing {@code yawDegrees}, rather than the owner's body:
     * in first person it hangs off the camera, so it holds still in view as the body lags behind the head.
     *
     * @param forward extra distance ahead
     */
    public static Vec3 restAnchor(Player owner, Vec3 feet, float yawDegrees, double forward, float tuck) {
        float yaw = yawDegrees * Mth.DEG_TO_RAD;
        double forwardX = -Mth.sin(yaw);
        double forwardZ = Mth.cos(yaw);
        // The player's right, looking along forward.
        double rightX = -forwardZ;
        double rightZ = forwardX;
        double side = -(REST_SIDE - 0.15 * tuck);
        double back = REST_BACK + 0.25 * tuck - forward;
        double height = owner.getBbHeight() / 1.8 * REST_HEIGHT;
        return feet.add(rightX * side - forwardX * back, height, rightZ * side - forwardZ * back);
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
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> isValidTarget(entity) && inRange(center, entity, radius, 0));
        // Nearest first, so the sight lines (the costly part) stop at the first one in view.
        candidates.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
        for (LivingEntity entity : candidates) {
            if (canSee(level, center, entity, viewer)) {
                return entity;
            }
        }
        return null;
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
