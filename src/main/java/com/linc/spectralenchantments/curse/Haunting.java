package com.linc.spectralenchantments.curse;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.SpectralConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server rules for the Curse of Haunting.
 *
 * <ul>
 *   <li>While a player wears armor with the curse, as soon as they put it on and every so often after, a
 *       {@link HauntingGhostEntity} of them appears
 *       somewhere in view, a little way off, to stare at them; and at once whenever a mob hurts them (unless they
 *       have just banished it). Checked once a second per player; finding a spot costs a few dozen collision checks
 *       and ray casts, once per appearance.</li>
 *   <li>A monster wearing it always has its ghost beside it. The ghost is sent out when the monster puts the armor
 *       on (which includes spawning in it, or being loaded with it), or picks a target without its ghost, so nothing
 *       is checked per tick.</li>
 * </ul>
 */
public final class Haunting {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<Mob, HauntingGhostEntity> MONSTER_GHOSTS = new WeakHashMap<>();
    /** How often each player is checked, in ticks. */
    private static final int CHECK_INTERVAL = 20;
    /** When no spot in view can be found, how long until the next try, in ticks. */
    private static final int RETRY_TICKS = 100;
    private static final int SPOT_ATTEMPTS = 12;
    private static final int[] HEIGHTS = {0, 1, -1, 2, -2, 3, -3};

    private Haunting() {}

    private static final class State {
        @Nullable
        HauntingGhostEntity ghost;
        /** Ticks until the ghost (next) appears. */
        int timer;
        /** Whether the wait is a banishment (the player struck the ghost), which a mob's blow doesn't cut short. */
        boolean banished;
    }

    /** The first armor piece the entity wears with the curse, or empty if it is not haunted. */
    public static ItemStack cursedArmor(LivingEntity entity) {
        if (entity instanceof Player player && !Curses.isAffected(player)) {
            return ItemStack.EMPTY;
        }
        Holder<Enchantment> curse = ModEnchantments.holder(entity.level().registryAccess(), ModEnchantments.HAUNTING_CURSE);
        if (curse == null || curse.is(ModEnchantments.DISABLED)) {
            return ItemStack.EMPTY;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(curse, stack) > 0) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** The player's ghost, if it is out. */
    @Nullable
    public static HauntingGhostEntity ghostOf(Player player) {
        State state = STATES.get(player.getUUID());
        return state == null || state.ghost == null || state.ghost.isRemoved() ? null : state.ghost;
    }

    /** Ticks until the player's ghost next appears, or -1 if they aren't haunted. */
    public static int timer(Player player) {
        State state = STATES.get(player.getUUID());
        return state == null ? -1 : state.timer;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && !(player instanceof FakePlayer)
                && (player.tickCount + player.getId()) % CHECK_INTERVAL == 0) {
            update(player, CHECK_INTERVAL);
        }
    }

    /** Counts {@code elapsed} ticks off the player's haunting, sending the ghost out when it's time. */
    public static void update(Player player, int elapsed) {
        State state = STATES.get(player.getUUID());
        ItemStack armor = cursedArmor(player);
        if (armor.isEmpty()) {
            if (state != null) {
                if (state.ghost != null) {
                    state.ghost.discard();
                }
                STATES.remove(player.getUUID());
            }
            return;
        }
        boolean first = state == null;
        if (first) {
            // The ghost shows itself the moment the armor goes on.
            state = new State();
            STATES.put(player.getUUID(), state);
        }
        if (state.ghost != null) {
            if (!state.ghost.isRemoved()) {
                return;
            }
            int delay = state.ghost.returnDelay();
            state.banished = delay >= 0;
            state.timer = state.banished ? delay : nextAppearance(player.getRandom());
            state.ghost = null;
        }
        state.timer -= elapsed;
        if (state.timer <= 0 && player.level() instanceof ServerLevel level) {
            state.ghost = first ? appearAnyway(level, player, armor) : appear(level, player, armor);
            state.timer = state.ghost == null ? RETRY_TICKS : 0;
            state.banished = false;
        }
    }

    /**
     * A mob hurt the player: unless their ghost is out already or banished, it appears at once, just behind
     * {@code attacker}. Returns the new ghost, if any.
     */
    @Nullable
    public static HauntingGhostEntity provoke(Player player, Mob attacker) {
        update(player, 0);
        State state = STATES.get(player.getUUID());
        if (state == null || state.ghost != null || state.banished || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 away = attacker.position().subtract(player.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(attacker.getBbWidth() / 2 + 1.2);
        HauntingGhostEntity ghost = HauntingGhostEntity.create(player, cursedArmor(player), attacker.position().add(away).add(0, 0.4, 0));
        if (level.addFreshEntity(ghost)) {
            state.ghost = ghost;
            state.timer = 0;
            return ghost;
        }
        return null;
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
                && event.getSource().getEntity() instanceof Mob attacker && event.getNewDamage() > 0) {
            provoke(player, attacker);
        }
    }

    // ---- Monsters ----

    /** The monster's ghost, if it has one out. */
    @Nullable
    public static HauntingGhostEntity ghostOf(Mob mob) {
        HauntingGhostEntity ghost = MONSTER_GHOSTS.get(mob);
        return ghost == null || ghost.isRemoved() ? null : ghost;
    }

    /** Sends out the monster's ghost if it wears the curse and its ghost isn't out. Returns its ghost, if any. */
    @Nullable
    public static HauntingGhostEntity summon(Mob mob) {
        HauntingGhostEntity ghost = ghostOf(mob);
        if (ghost != null || !mob.isAlive() || !(mob.level() instanceof ServerLevel level) || !SpectralConfig.hauntingMonsterGhosts()) {
            return ghost;
        }
        ItemStack armor = cursedArmor(mob);
        if (armor.isEmpty()) {
            return null;
        }
        ghost = HauntingGhostEntity.create(mob, armor);
        if (!level.addFreshEntity(ghost)) {
            return null;
        }
        MONSTER_GHOSTS.put(mob, ghost);
        return ghost;
    }

    /** The ghost is gone (or unloaded): let go of it, and of its monster. */
    static void forget(Mob mob, HauntingGhostEntity ghost) {
        MONSTER_GHOSTS.remove(mob, ghost);
    }

    /** Putting the armor on brings the ghost out then and there, for players and monsters alike. */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getSlot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR
                || event.getTo().isEmpty()) {
            return;
        }
        if (entity instanceof Mob mob) {
            summon(mob);
        } else if (entity instanceof Player player && !(player instanceof FakePlayer)) {
            update(player, 0);
        }
    }

    /** Picks up a monster whose ghost was lost (unloaded with its chunk, say) as it goes after something. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide && event.getNewAboutToBeSetTarget() != null
                && ghostOf(mob) == null) {
            summon(mob);
        }
    }

    /** Sends the player's ghost out somewhere it can see them from, or returns null if there's no such spot. */
    @Nullable
    public static HauntingGhostEntity appear(ServerLevel level, Player player, ItemStack armor) {
        Vec3 spot = findSpot(level, player);
        if (spot == null) {
            return null;
        }
        HauntingGhostEntity ghost = HauntingGhostEntity.create(player, armor, spot);
        return level.addFreshEntity(ghost) ? ghost : null;
    }

    /**
     * As {@link #appear}, but where no good spot can be found it appears straight ahead of the player instead, as far
     * off as they can see (up to the stare distance).
     */
    @Nullable
    private static HauntingGhostEntity appearAnyway(ServerLevel level, Player player, ItemStack armor) {
        HauntingGhostEntity ghost = appear(level, player, armor);
        if (ghost != null) {
            return ghost;
        }
        Vec3 ahead = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(ahead.scale(SpectralConfig.hauntingStareMaxDistance()));
        double distance = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
                .getLocation().distanceTo(eye);
        ghost = HauntingGhostEntity.create(player, armor, player.position().add(ahead.scale(Math.max(1, distance - 0.5))));
        return level.addFreshEntity(ghost) ? ghost : null;
    }

    /**
     * A spot between the stare distances from the player, in loaded chunks, where the ghost fits and can see the
     * player's eyes; null if a few random tries find none.
     */
    @Nullable
    private static Vec3 findSpot(ServerLevel level, Player player) {
        RandomSource random = player.getRandom();
        double min = SpectralConfig.hauntingStareMinDistance();
        double max = SpectralConfig.hauntingStareMaxDistance();
        Vec3 eye = player.getEyePosition();
        var dimensions = ModEntities.HAUNTING_GHOST.get().getDimensions();
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double distance = min + random.nextDouble() * (max - min);
            double x = player.getX() + Mth.cos(angle) * distance;
            double z = player.getZ() + Mth.sin(angle) * distance;
            for (int dy : HEIGHTS) {
                double y = Math.floor(player.getY()) + dy;
                if (!level.hasChunkAt(BlockPos.containing(x, y, z))) {
                    break;
                }
                AABB box = dimensions.makeBoundingBox(x, y, z);
                if (!level.noCollision(box) || level.containsAnyLiquid(box)) {
                    continue;
                }
                Vec3 ghostEye = new Vec3(x, y + dimensions.eyeHeight(), z);
                if (level.clip(new ClipContext(ghostEye, eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
                        .getType() == HitResult.Type.MISS) {
                    return new Vec3(x, y, z);
                }
            }
        }
        return null;
    }

    private static int nextAppearance(RandomSource random) {
        int min = SpectralConfig.hauntingAppearMinTicks();
        return min + random.nextInt(SpectralConfig.hauntingAppearMaxTicks() - min + 1);
    }

    /** Monsters' arrows and fireballs pass through the ghost; players' strike it. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof HauntingGhostEntity
                && !(event.getProjectile().getOwner() instanceof Player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        State state = STATES.remove(event.getEntity().getUUID());
        if (state != null && state.ghost != null) {
            state.ghost.discard();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        MONSTER_GHOSTS.clear();
    }
}
