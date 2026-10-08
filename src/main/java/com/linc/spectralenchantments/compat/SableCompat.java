package com.linc.spectralenchantments.compat;

import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

/**
 * Optional Sable integration (off unless {@code catapulting.physicsBlocks} is set), by reflection so Sable stays a
 * soft dependency: a Catapulting bow fires a block as a Sable physics object, the heavier the slower (from an
 * arrow's speed for Sable's lightest blocks down to a thrown snowball's for its heaviest). Once it comes to rest it
 * sets itself down as an ordinary block where it lies; if it hits a creature on the way, it is left a loose physics
 * object.
 *
 * <p>Blocks with block entities (their contents can't be carried back) and blocks tagged
 * {@code spectral_enchantments:catapulting/no_physics} are fired the usual way.
 */
public final class SableCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final TagKey<Item> NO_PHYSICS = TagKey.create(Registries.ITEM, SpectralEnchantments.id("catapulting/no_physics"));
    /** Below this speed (blocks a second) for a few ticks running, a fired block has landed. */
    private static final double REST_SPEED = 0.6;
    private static final int REST_TICKS = 4;
    /** A fired block that never settles is set down after this long. */
    private static final int MAX_FLIGHT_TICKS = 400;
    /** Ticks after firing during which it can't hit its shooter. */
    private static final int SHOOTER_GRACE_TICKS = 10;
    /** Sable's masses: its "super light" blocks fly as fast as an arrow, its "super heavy" ones as a thrown snowball. */
    private static final double LIGHT_MASS = 0.25;
    private static final double HEAVY_MASS = 4.0;
    private static final float ARROW_SPEED = 3.0f;
    private static final float THROWN_SPEED = 1.5f;
    /** How many physics steps ahead to look for the ground, and how far a block reaches from its middle. */
    private static final double LOOKAHEAD_STEPS = 1.5;
    private static final double HALF_WIDTH = 0.6;
    /** Further than a fired block can move in a tick: a jump this big is Sable still settling its bounds, not flight. */
    private static final double MAX_TICK_TRAVEL = 16.0;

    /** For the GameTests: fire physics blocks whatever the config says. */
    public static boolean forceEnabled;

    private static boolean available;
    // Method handles rather than reflection: looking a method up reflectively makes Java resolve every method of its
    // class, and SubLevelContainer has client-only ones that can't be loaded on a dedicated server.
    private static MethodHandle assemble;
    private static MethodHandle boundingBox;
    private static MethodHandle rigidBody;
    private static MethodHandle addVelocity;
    private static MethodHandle logicalPose;
    private static MethodHandle posePosition;
    private static MethodHandle isRemoved;
    private static MethodHandle latestVelocity;
    private static MethodHandle getContainer;
    private static MethodHandle removeSubLevel;
    private static MethodHandle massTracker;
    private static MethodHandle mass;
    private static Object removedReason;

    /** A block in flight. */
    private static final class Flight {
        final Object subLevel;
        final ServerLevel level;
        final BlockState state;
        final ItemStack stack;
        final UUID shooter;
        int age;
        int still;
        /** Where its middle was last tick, to catch it passing through blocks between ticks. */
        Vec3 lastCenter;

        Flight(Object subLevel, ServerLevel level, BlockState state, ItemStack stack, UUID shooter, Vec3 center) {
            this.subLevel = subLevel;
            this.level = level;
            this.state = state;
            this.stack = stack;
            this.shooter = shooter;
            this.lastCenter = center;
        }
    }

    private static final List<Flight> FLIGHTS = new ArrayList<>();

    private SableCompat() {}

    public static void register() {
        if (!ModList.get().isLoaded("sable")) {
            return;
        }
        try {
            ClassLoader loader = SableCompat.class.getClassLoader();
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> helper = Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper", true, loader);
            Class<?> boxI = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3i", true, loader);
            Class<?> boxIc = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic", true, loader);
            Class<?> pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3d", true, loader);
            Class<?> serverSubLevel = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel", true, loader);
            Class<?> subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel", true, loader);
            Class<?> handle = Class.forName("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle", true, loader);
            Class<?> container = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", true, loader);
            Class<?> serverContainer = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", true, loader);
            Class<?> reason = Class.forName("dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason", true, loader);
            assemble = lookup.findStatic(helper, "assembleBlocks",
                    MethodType.methodType(serverSubLevel, ServerLevel.class, BlockPos.class, Iterable.class, boxIc));
            boundingBox = lookup.findConstructor(boxI, MethodType.methodType(void.class, BlockPos.class, BlockPos.class));
            rigidBody = lookup.findStatic(handle, "of", MethodType.methodType(handle, serverSubLevel));
            addVelocity = lookup.findVirtual(handle, "addLinearAndAngularVelocity",
                    MethodType.methodType(void.class, Vector3dc.class, Vector3dc.class));
            logicalPose = lookup.findVirtual(subLevel, "logicalPose", MethodType.methodType(pose));
            posePosition = lookup.findVirtual(pose, "position", MethodType.methodType(Vector3d.class));
            isRemoved = lookup.findVirtual(subLevel, "isRemoved", MethodType.methodType(boolean.class));
            latestVelocity = lookup.findGetter(serverSubLevel, "latestLinearVelocity", Vector3d.class);
            getContainer = lookup.findStatic(container, "getContainer", MethodType.methodType(serverContainer, ServerLevel.class));
            removeSubLevel = lookup.findVirtual(container, "removeSubLevel", MethodType.methodType(void.class, subLevel, reason));
            removedReason = lookup.findStaticGetter(reason, "REMOVED", reason).invoke();
            Class<?> massData = Class.forName("dev.ryanhcode.sable.api.physics.mass.MassData", true, loader);
            massTracker = lookup.findVirtual(serverSubLevel, "getMassTracker", MethodType.methodType(massData));
            mass = lookup.findVirtual(massData, "getMass", MethodType.methodType(double.class));
            available = true;
            NeoForge.EVENT_BUS.addListener(SableCompat::onLevelTick);
        } catch (Throwable e) {
            LOGGER.warn("Couldn't hook Sable; Catapulting will fire blocks the usual way", e);
        }
    }

    /** Whether {@code stack} would be fired as a physics object. */
    public static boolean firesAsPhysics(ItemStack stack) {
        return available && (forceEnabled || SpectralConfig.catapultingPhysicsBlocks()) && !stack.is(NO_PHYSICS)
                && stack.getItem() instanceof BlockItem block && !block.getBlock().defaultBlockState().hasBlockEntity();
    }

    /**
     * Fires {@code stack} (one block) as a physics object from in front of the player, heading {@code direction},
     * as fast as its weight allows at a draw of {@code power} (0-1). Returns false, having done nothing, if it can't be.
     */
    public static boolean fire(ServerPlayer player, ItemStack stack, Vec3 direction, float power) {
        if (!firesAsPhysics(stack)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(2.0)));
        if (!level.getBlockState(pos).isAir() || !player.mayInteract(level, pos)) {
            return false;
        }
        BlockState state = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
        try {
            level.setBlock(pos, state, 3);
            Object subLevel = assemble.invoke(level, pos, (Iterable<BlockPos>) List.of(pos), boundingBox.invoke(pos, pos));
            if (subLevel == null) {
                level.removeBlock(pos, false);
                return false;
            }
            Object handle = rigidBody.invoke(subLevel);
            // Sable's velocities are in blocks a second.
            Vec3 velocity = direction.normalize().scale(speedFor(subLevel) * power * 20);
            addVelocity.invoke(handle, new Vector3d(velocity.x, velocity.y, velocity.z), new Vector3d());
            FLIGHTS.add(new Flight(subLevel, level, state, stack.copyWithCount(1), player.getUUID(), Vec3.atCenterOf(pos)));
            return true;
        } catch (Throwable e) {
            LOGGER.warn("Sable couldn't launch a fired block; firing it the usual way", e);
            available = false;
            if (level.getBlockState(pos) == state) {
                level.removeBlock(pos, false);
            }
            return false;
        }
    }

    /** Blocks a tick at a full draw: an arrow's for the lightest, a snowball's for the heaviest, between on a log scale. */
    static float speedFor(Object subLevel) {
        double m;
        try {
            m = (double) mass.invoke(massTracker.invoke(subLevel));
        } catch (Throwable e) {
            m = 1.0;
        }
        if (!(m > 0) || Double.isInfinite(m)) {
            m = 1.0;
        }
        double t = Mth.clamp(Math.log(m / LIGHT_MASS) / Math.log(HEAVY_MASS / LIGHT_MASS), 0.0, 1.0);
        return (float) Mth.lerp(t, ARROW_SPEED, THROWN_SPEED);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || FLIGHTS.isEmpty()) {
            return;
        }
        for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
            Flight flight = it.next();
            if (flight.level != level) {
                continue;
            }
            try {
                if ((boolean) isRemoved.invoke(flight.subLevel)) {
                    // Gone from under us: leave the block's item where it was last seen rather than lose it.
                    level.addFreshEntity(new ItemEntity(level, flight.lastCenter.x, Math.max(flight.lastCenter.y, level.getMinBuildHeight() + 1),
                            flight.lastCenter.z, flight.stack));
                    it.remove();
                    continue;
                }
                flight.age++;
                Vec3 center = center(flight.subLevel);
                if (center.distanceTo(flight.lastCenter) > MAX_TICK_TRAVEL) {
                    // Not where it can be: Sable hasn't put it in the world yet.
                    continue;
                }
                // A single block, so its middle is where it pivots; this box holds it however it is turned.
                AABB box = new AABB(center, center).inflate(0.87);
                // Moving fast, the physics can carry it clean through the ground in a single step. So look ahead: if
                // anything solid lies between where it was and where its next step (and its own half-width) takes
                // it, it lands there now.
                Vector3d v = (Vector3d) latestVelocity.invoke(flight.subLevel);
                Vec3 step = new Vec3(v.x, v.y, v.z).scale(LOOKAHEAD_STEPS / 20.0);
                Vec3 ahead = center.add(step).add(step.lengthSqr() > 1.0E-6 ? step.normalize().scale(HALF_WIDTH) : Vec3.ZERO);
                BlockHitResult passed = clipWorld(level, flight.lastCenter, ahead);
                if (passed != null || center.y < level.getMinBuildHeight()) {
                    Vec3 at = passed != null ? Vec3.atCenterOf(passed.getBlockPos().relative(passed.getDirection())) : flight.lastCenter;
                    setDown(flight, level, at);
                    it.remove();
                    continue;
                }
                flight.lastCenter = center;
                if (hitsCreature(flight, level, box)) {
                    // Left loose: it's a physics object now.
                    it.remove();
                    continue;
                }
                double speed = ((Vector3d) latestVelocity.invoke(flight.subLevel)).length();
                flight.still = flight.age > 2 && speed < REST_SPEED ? flight.still + 1 : 0;
                if (flight.still >= REST_TICKS || flight.age > MAX_FLIGHT_TICKS) {
                    setDown(flight, level, flight.lastCenter);
                    it.remove();
                }
            } catch (Throwable e) {
                LOGGER.warn("Lost track of a fired Sable block", e);
                it.remove();
            }
        }
    }

    private static boolean hitsCreature(Flight flight, ServerLevel level, AABB box) {
        List<Entity> hit = level.getEntities((Entity) null, box.inflate(0.05), entity -> entity instanceof LivingEntity living
                && living.isAlive() && !living.isSpectator()
                && !(flight.age < SHOOTER_GRACE_TICKS && living.getUUID().equals(flight.shooter)));
        return !hit.isEmpty();
    }

    /** Swaps the resting physics object for the block, where it lies (or just above, or dropped if neither will do). */
    private static void setDown(Flight flight, ServerLevel level, Vec3 center) throws Throwable {
        removeSubLevel.invoke(getContainer.invoke(level), flight.subLevel, removedReason);
        BlockPos at = BlockPos.containing(center);
        try {
            for (BlockPos pos : List.of(at, at.above())) {
                if (level.isInWorldBounds(pos) && level.getBlockState(pos).canBeReplaced() && flight.state.canSurvive(level, pos)) {
                    level.setBlockAndUpdate(pos, flight.state);
                    level.playSound(null, pos, flight.state.getSoundType(level, pos, null).getPlaceSound(), SoundSource.BLOCKS, 1f, 0.8f);
                    return;
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Couldn't set a fired Sable block down at {}; dropping it", at, e);
        }
        // Never lost: dropped where it came to rest (or where it was last seen in the world).
        Vec3 drop = center.y < level.getMinBuildHeight() ? new Vec3(center.x, level.getMinBuildHeight() + 1, center.z) : center;
        level.addFreshEntity(new ItemEntity(level, drop.x, drop.y, drop.z, flight.stack));
    }

    /**
     * The first solid world block between {@code from} and {@code to}, or null. Not {@code level.clip}: Sable has that
     * hit its physics objects too (this one included), answering in the coordinates it stores them at.
     */
    private static BlockHitResult clipWorld(ServerLevel level, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, level, (world, pos) -> {
            BlockState state = world.getBlockState(pos);
            return state.getCollisionShape(world, pos).clip(from, to, pos);
        }, world -> null);
    }

    /** Where the fired block's middle is in the world (Sable's bounds are where it keeps the block, not where it is). */
    private static Vec3 center(Object subLevel) throws Throwable {
        Vector3d position = (Vector3d) posePosition.invoke(logicalPose.invoke(subLevel));
        return new Vec3(position.x, position.y, position.z);
    }
}
