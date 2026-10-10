package com.linc.spectralenchantments;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Cooldowns that belong to one stack, not to every item of its kind as vanilla's do: recalling one Animated diamond
 * sword (or using one pair of Branch Finding shears) leaves the others free. Kept on the stack as
 * {@link ModComponents#COOLDOWN}, synced to clients for the slot sweep and never saved.
 */
public final class StackCooldowns {
    /** Ready again at game time {@code until}, after {@code duration} ticks in all. */
    public record Cooldown(long until, int duration) {
        public static final StreamCodec<ByteBuf, Cooldown> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Cooldown::until,
                ByteBufCodecs.VAR_INT, Cooldown::duration,
                Cooldown::new);
    }

    private StackCooldowns() {}

    public static void start(ItemStack stack, Level level, int ticks) {
        if (ticks > 0 && !stack.isEmpty()) {
            stack.set(ModComponents.COOLDOWN.get(), new Cooldown(level.getGameTime() + ticks, ticks));
        }
    }

    /** Whether the stack is cooling down. A finished cooldown is taken off the stack (on the server) as it's noticed. */
    public static boolean isActive(ItemStack stack, Level level) {
        Cooldown cooldown = stack.get(ModComponents.COOLDOWN.get());
        if (cooldown == null) {
            return false;
        }
        if (level.getGameTime() < cooldown.until()) {
            return true;
        }
        if (!level.isClientSide) {
            stack.remove(ModComponents.COOLDOWN.get());
        }
        return false;
    }

    /** How much of the cooldown is left, from 1 just begun to 0 over, as vanilla's cooldown sweep shows it. */
    public static float remaining(ItemStack stack, Level level, float partialTick) {
        Cooldown cooldown = stack.get(ModComponents.COOLDOWN.get());
        if (cooldown == null || cooldown.duration() <= 0) {
            return 0f;
        }
        float left = cooldown.until() - (level.getGameTime() + partialTick);
        return Mth.clamp(left / cooldown.duration(), 0f, 1f);
    }
}
