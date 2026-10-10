package com.linc.spectralenchantments;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Gather: drops from blocks broken (or creatures killed) with the enchanted item go straight into the player's
 * inventory, and their experience to the player. Whatever does not fit drops as normal. Low priority, so other mods'
 * changes to the drops come first.
 */
public final class Gather {
    private Gather() {}

    public static boolean has(Level level, ItemStack stack) {
        return ModEnchantments.has(level, ModEnchantments.GATHER, stack);
    }

    /** Covers ordinary mining, Broad Breaker's extra blocks and Far Miner: all pass the tool used. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!event.isCanceled() && event.getBreaker() instanceof ServerPlayer player && has(event.getLevel(), event.getTool())) {
            gather(player, event.getDrops());
            int xp = event.getDroppedExperience();
            if (xp > 0 && canGather(player)) {
                event.setDroppedExperience(0);
                giveExperience(player, xp);
            }
        }
    }

    /** The weapon is the held item for melee, the launcher for arrows, the trident itself, or an Animated weapon's item. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!event.isCanceled() && event.getSource().getEntity() instanceof ServerPlayer player
                && has(player.level(), event.getSource().getWeaponItem())) {
            gather(player, event.getDrops());
        }
    }

    /** Creatures just killed with a Gather item, to who killed them: their experience drops a moment later. */
    private static final Map<LivingEntity, ServerPlayer> KILLS = new WeakHashMap<>();

    /** The death's source still names the weapon here; by the time experience drops, it no longer does. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && has(player.level(), event.getSource().getWeaponItem())) {
            KILLS.put(event.getEntity(), player);
        }
    }

    /**
     * A kill's experience goes to the player too. Low priority, so an Animated weapon with Mending (which mends from
     * its own kill's experience first, see AnimatedManager) takes its share before this takes the rest.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        ServerPlayer killer = KILLS.remove(event.getEntity());
        int xp = event.getDroppedExperience();
        if (!event.isCanceled() && xp > 0 && killer != null && event.getAttackingPlayer() == killer && canGather(killer)) {
            event.setDroppedExperience(0);
            giveExperience(killer, xp);
        }
    }

    private static boolean canGather(ServerPlayer player) {
        return !(player instanceof FakePlayer) && player.isAlive();
    }

    /**
     * As orbs right at the player, which they pick up at once the usual way: so Mending, other mods' pickup hooks and
     * the experience sound all behave just as if they had walked over to them.
     */
    private static void giveExperience(ServerPlayer player, int xp) {
        ExperienceOrb.award(player.serverLevel(), player.position(), xp);
    }

    /**
     * Into the inventory as if each drop had been walked over: other mods' pickup hooks (backpack pickup upgrades,
     * void filters) see each one first and may take it, or refuse it.
     */
    private static void gather(ServerPlayer player, Collection<ItemEntity> drops) {
        if (!canGather(player)) {
            return;
        }
        boolean any = false;
        Iterator<ItemEntity> it = drops.iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            if (EventHooks.fireItemPickupPre(drop, player).canPickup() == TriState.FALSE) {
                continue;
            }
            // A pickup hook may have taken some or all of it already.
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                it.remove();
                any = true;
                continue;
            }
            ItemStack before = stack.copy();
            Item item = stack.getItem(); // an emptied stack reports air
            player.getInventory().add(stack);
            int taken = before.getCount() - stack.getCount();
            if (taken <= 0) {
                continue;
            }
            any = true;
            EventHooks.fireItemPickupPost(drop, player, before.copyWithCount(taken));
            player.awardStat(Stats.ITEM_PICKED_UP.get(item), taken);
            if (stack.isEmpty()) {
                it.remove();
            } else {
                drop.setItem(stack);
            }
        }
        if (any) {
            // The same sound a picked-up item makes.
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.2f, ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7f + 1f) * 2f);
            player.containerMenu.broadcastChanges();
        }
    }
}
