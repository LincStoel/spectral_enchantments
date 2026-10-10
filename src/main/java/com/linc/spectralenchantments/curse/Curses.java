package com.linc.spectralenchantments.curse;

import com.linc.spectralenchantments.ModComponents;
import com.linc.spectralenchantments.ModEffects;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Hidden curses and the Cursed effect.
 *
 * <ul>
 *   <li>An item's curses stay out of its tooltip (see the client's CurseTooltips) until it has been in a survival
 *       player's inventory, when it is marked {@link ModComponents#CURSES_REVEALED}. Armor (anything worn) keeps
 *       them hidden until it is put on.</li>
 *   <li>Revealing a curse gives the player Cursed: while it lasts, no cursed item they carry can be dropped or put
 *       into another container (the menu clicks are refused by the container mixins, which ask
 *       {@link #mayPlace}).</li>
 *   <li>A revealed Curse of Possession takes the weapon away instead ({@link Possession}).</li>
 * </ul>
 */
public final class Curses {
    /** Blocks that take a held item on use (decorated pots, modded pedestals): a locked item can't go into them. */
    public static final TagKey<Block> REFUSES_ITEMS = TagKey.create(Registries.BLOCK, SpectralEnchantments.id("cursed/refuses_items"));

    /** Each player's inventory as last looked at, by stack identity, so unchanged slots are skipped. Server thread. */
    private static final Map<Player, ItemStack[]> SEEN = new WeakHashMap<>();
    /** How often every slot is looked at anyway, in ticks. */
    private static final int FULL_SCAN_INTERVAL = 20;

    /** The player whose menu click is being handled on this thread, for the container mixins. */
    private static final ThreadLocal<Player> CLICKING = new ThreadLocal<>();

    private Curses() {}

    // ---- Items ----

    /** Whether the item (not an enchanted book's stored enchantments) carries a curse. */
    public static boolean hasCurse(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(EnchantmentTags.CURSE)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRevealed(ItemStack stack) {
        return stack.has(ModComponents.CURSES_REVEALED.get());
    }

    /** A copy of {@code stack} with every curse removed. */
    public static ItemStack withoutCurses(ItemStack stack) {
        ItemStack copy = stack.copy();
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(copy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchantments.removeIf(enchantment -> enchantment.is(EnchantmentTags.CURSE));
        copy.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        return copy;
    }

    // ---- Players ----

    /** Survival and adventure players only: creative players see every curse and are never cursed. */
    public static boolean isAffected(Player player) {
        return !player.isCreative() && !player.isSpectator();
    }

    public static boolean isCursed(Player player) {
        return isAffected(player) && player.hasEffect(ModEffects.CURSED);
    }

    /** Whether the player may not let go of {@code stack}: they are Cursed and it carries a curse. */
    public static boolean isLocked(Player player, ItemStack stack) {
        return isCursed(player) && hasCurse(stack);
    }

    /** Whether {@code stack} may go into {@code slot}: anywhere in the player's own inventory, elsewhere only if not locked. */
    public static boolean mayPlace(Player player, Slot slot, ItemStack stack) {
        return slot.container instanceof Inventory inventory && inventory.player == player || !isLocked(player, stack);
    }

    /** Tells the player why their cursed item stays put. */
    public static void refuse(Player player) {
        player.displayClientMessage(Component.translatable("message.spectral_enchantments.cursed.locked")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    // ---- Menu clicks (called by the mixins, on both sides) ----

    public static void beginClick(Player player) {
        CLICKING.set(player);
    }

    public static void endClick() {
        CLICKING.remove();
    }

    @Nullable
    public static Player clickingPlayer() {
        return CLICKING.get();
    }

    // ---- Revealing ----

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            reveal(player);
        }
    }

    /**
     * Reveals the curses of every cursed item newly in the player's inventory, or for armor, newly worn. Possessed weapons leave; any other
     * newly revealed curse makes the player Cursed. Returns how many items were revealed.
     */
    public static int reveal(ServerPlayer player) {
        if (!isAffected(player)) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        ItemStack[] seen = SEEN.get(player);
        if (seen == null || seen.length != inventory.getContainerSize()) {
            seen = new ItemStack[inventory.getContainerSize()];
            SEEN.put(player, seen);
        }
        // Only slots holding a stack not seen there before, but every so often all of them: an item can be cursed
        // where it lies (a command, say), or wait out Peaceful.
        boolean everything = player.level().getGameTime() % FULL_SCAN_INTERVAL == 0;
        List<Integer> possessed = new ArrayList<>();
        int revealed = 0;
        boolean curse = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!everything && seen[slot] == stack) {
                continue;
            }
            seen[slot] = stack;
            if (stack.isEmpty() || isRevealed(stack) || !hasCurse(stack) || isArmor(player, stack) && !isArmorSlot(inventory, slot)) {
                continue;
            }
            boolean possession = Possession.has(player.level(), stack);
            if (possession && player.level().getDifficulty() == Difficulty.PEACEFUL) {
                // Left hidden and asleep, so it still tears free once the difficulty rises.
                continue;
            }
            stack.set(ModComponents.CURSES_REVEALED.get(), Unit.INSTANCE);
            revealed++;
            if (possession) {
                possessed.add(slot);
            } else {
                curse = true;
            }
        }
        for (int slot : possessed) {
            Possession.possess(player, slot);
        }
        int ticks = SpectralConfig.cursedTicks();
        if (curse && ticks > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.CURSED, ticks, 0, false, true, true));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE,
                    SoundSource.PLAYERS, 0.35f, 1.4f);
            player.displayClientMessage(Component.translatable("message.spectral_enchantments.cursed.start")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
        return revealed;
    }

    /** Whether {@code stack} is worn rather than held: armor, elytra, pumpkins, heads. */
    private static boolean isArmor(Player player, ItemStack stack) {
        return player.getEquipmentSlotForItem(stack).getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    private static boolean isArmorSlot(Inventory inventory, int slot) {
        return slot >= inventory.items.size() && slot < inventory.items.size() + inventory.armor.size();
    }

    // ---- Letting go ----

    /**
     * The drop key (handled first on the client, which never sends it) and anything else that tosses a locked item:
     * the toss is refused and the item put back, in the selected slot if it came from there.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        ItemEntity entity = event.getEntity();
        ItemStack stack = entity.getItem();
        if (player.level().isClientSide || !isLocked(player, stack)) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack back = stack.copy();
        if (inventory.getSelected().isEmpty()) {
            inventory.setItem(inventory.selected, back);
        } else if (!inventory.add(back)) {
            // No room to put it back: leave it on the ground for this player alone.
            entity.setTarget(player.getUUID());
            entity.setNoPickUpDelay();
            refuse(player);
            return;
        }
        event.setCanceled(true);
        player.containerMenu.broadcastChanges();
        refuse(player);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        refuseHandingOver(event, event.getEntity(), event.getHand(), event.getTarget());
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        refuseHandingOver(event, event.getEntity(), event.getHand(), event.getTarget());
    }

    /** A locked item can't be put into a block that holds items (see {@link #REFUSES_ITEMS}). */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!isLocked(player, event.getItemStack()) || !event.getLevel().getBlockState(event.getPos()).is(REFUSES_ITEMS)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (!player.level().isClientSide) {
            refuse(player);
        }
    }

    /**
     * Nor can it be tucked into an item that holds others (a bundle), by clicking one onto the other in a menu: that
     * goes through no slot, so the container mixins never see it.
     */
    @SubscribeEvent
    public static void onItemStackedOnOther(ItemStackedOnOtherEvent event) {
        Player player = event.getPlayer();
        if (isLocked(player, event.getCarriedItem()) || isLocked(player, event.getStackedOnItem())) {
            event.setCanceled(true);
        }
    }

    /** A locked item can't be hung in a frame or handed to an armor stand or allay. */
    private static void refuseHandingOver(PlayerInteractEvent event, Player player, InteractionHand hand, Entity target) {
        if (!(target instanceof ItemFrame || target instanceof ArmorStand || target instanceof Allay)
                || !isLocked(player, player.getItemInHand(hand))) {
            return;
        }
        ((ICancellableEvent) event).setCanceled(true);
        InteractionResult result = InteractionResult.FAIL;
        if (event instanceof PlayerInteractEvent.EntityInteract e) {
            e.setCancellationResult(result);
        } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific e) {
            e.setCancellationResult(result);
        }
        if (!player.level().isClientSide) {
            refuse(player);
        }
    }

    /** For the tooltip: each curse on the item with its level. */
    public static List<Object2IntMap.Entry<Holder<Enchantment>>> curses(ItemStack stack) {
        List<Object2IntMap.Entry<Holder<Enchantment>>> curses = new ArrayList<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            if (entry.getKey().is(EnchantmentTags.CURSE)) {
                curses.add(entry);
            }
        }
        return curses;
    }
}
