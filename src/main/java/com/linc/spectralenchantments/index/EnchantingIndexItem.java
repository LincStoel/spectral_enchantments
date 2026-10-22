package com.linc.spectralenchantments.index;

import com.linc.spectralenchantments.SpectralConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Enchanting Index. Used in the air (or on any other block) it lists the enchanted books on every shelf within
 * {@link SpectralConfig#enchantingIndexRadius()} blocks; used on a shelf it lists those on that shelf and every shelf
 * joined to it, however far the library runs (up to {@link SpectralConfig#enchantingIndexMaxConnected()}). Either
 * way it then cools down for a moment. Picking a book from the list makes it glow on its shelf, in the cover's dye
 * colour. It can also be set on a lectern and used from there; see {@link IndexLectern}.
 */
public class EnchantingIndexItem extends Item {
    /** The cover's colour when undyed: the vanilla book's leather. It's dyed like leather armour (#minecraft:dyeable). */
    public static final int DEFAULT_COLOR = 0xFF654B17;

    public EnchantingIndexItem(Properties properties) {
        super(properties);
    }

    /** Lets a water cauldron wash the dye off, as it does leather armour. The map isn't thread-safe, so this runs from enqueueWork. */
    public static void registerCauldronWashing(Item item) {
        CauldronInteraction.WATER.map().put(item, CauldronInteraction.DYED_ITEM);
    }

    /** Before the block's own use, so a chiseled shelf never takes the click as taking a book out. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !SpectralConfig.enchantingIndexEnabled()
                || !level.getBlockState(context.getClickedPos()).is(EnchantingIndex.BOOK_HOLDERS)) {
            return InteractionResult.PASS;
        }
        // This runs ahead of vanilla's cooldown check, so it has to make its own.
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            EnchantingIndex.open(serverPlayer, EnchantingIndex.holdersJoined(level, context.getClickedPos(),
                    SpectralConfig.enchantingIndexMaxConnected()), stack);
            startCooldown(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SpectralConfig.enchantingIndexEnabled()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            EnchantingIndex.open(serverPlayer, EnchantingIndex.holdersAround(level, player.blockPosition(),
                    SpectralConfig.enchantingIndexRadius()), stack);
            startCooldown(player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    void startCooldown(Player player) {
        int cooldown = SpectralConfig.enchantingIndexCooldownTicks();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(this, cooldown);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.spectral_enchantments.enchanting_index.desc").withStyle(ChatFormatting.GRAY));
    }
}
