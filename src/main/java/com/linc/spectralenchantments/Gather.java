package com.linc.spectralenchantments;

import java.util.Collection;
import java.util.Iterator;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Gather: drops from blocks broken (or creatures killed) with the enchanted item go straight into the player's
 * inventory. Whatever does not fit drops as normal. Low priority, so other mods' changes to the drops come first.
 */
public final class Gather {
    private Gather() {}

    public static boolean has(Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SpectralConfig.gatherEnabled()) {
            return false;
        }
        return level.registryAccess().registry(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.getHolder(ModEnchantments.GATHER))
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0)
                .orElse(false);
    }

    /** Covers ordinary mining, Broad Breaker's extra blocks and Far Miner: all pass the tool used. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!event.isCanceled() && event.getBreaker() instanceof ServerPlayer player && has(event.getLevel(), event.getTool())) {
            gather(player, event.getDrops());
        }
    }

    /** The weapon is the held item for melee, the launcher for arrows, the trident itself, or a Spectral Weapon's item. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!event.isCanceled() && event.getSource().getEntity() instanceof ServerPlayer player
                && has(player.level(), event.getSource().getWeaponItem())) {
            gather(player, event.getDrops());
        }
    }

    private static void gather(ServerPlayer player, Collection<ItemEntity> drops) {
        if (player instanceof FakePlayer || !player.isAlive()) {
            return;
        }
        boolean any = false;
        Iterator<ItemEntity> it = drops.iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            ItemStack stack = drop.getItem();
            int before = stack.getCount();
            Item item = stack.getItem(); // an emptied stack reports air
            player.getInventory().add(stack);
            int taken = before - stack.getCount();
            if (taken <= 0) {
                continue;
            }
            any = true;
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
