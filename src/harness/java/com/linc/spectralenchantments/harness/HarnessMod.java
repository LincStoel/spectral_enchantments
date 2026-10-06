package com.linc.spectralenchantments.harness;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.lang.reflect.Method;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Dev-only compat harness. Server commands, run over RCON by tools/compat:
 * <ul>
 *   <li>{@code /bbh do <players> <action> [arg]} forwards an instruction to bot clients (see HarnessClient)</li>
 *   <li>{@code /bbh arsbreak <player> <pos>} breaks a block exactly like an Ars Nouveau spell cast by that player</li>
 * </ul>
 */
@Mod(HarnessMod.MOD_ID)
public final class HarnessMod {
    public static final String MOD_ID = "spectral_enchantments_harness";

    public HarnessMod(IEventBus modBus) {
        modBus.addListener(HarnessNet::register);
        NeoForge.EVENT_BUS.addListener(HarnessMod::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bbh").requires(s -> s.hasPermission(2))
                .then(Commands.literal("do")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .executes(ctx -> instruct(ctx.getArgument("action", String.class), "",
                                                EntityArgument.getPlayers(ctx, "players")))
                                        .then(Commands.argument("arg", StringArgumentType.greedyString())
                                                .executes(ctx -> instruct(ctx.getArgument("action", String.class),
                                                        StringArgumentType.getString(ctx, "arg"), EntityArgument.getPlayers(ctx, "players")))))))
                .then(Commands.literal("trace")
                        .then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(ctx -> {
                                    ServerTrace.enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
                                    return 1;
                                })))
                .then(Commands.literal("arsbreak")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> {
                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                            boolean broken = arsBreak(player, pos);
                                            ctx.getSource().sendSuccess(() -> Component.literal("arsbreak " + broken), false);
                                            return broken ? 1 : 0;
                                        })))));
    }

    private static int instruct(String action, String arg, java.util.Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, new HarnessNet.Instruct(action, arg));
        }
        return players.size();
    }

    /** Calls Ars Nouveau's BlockUtil.destroyBlockSafely, the path its Break effect uses, with the player as caster. */
    private static boolean arsBreak(ServerPlayer player, BlockPos pos) {
        try {
            Class<?> blockUtil = Class.forName("com.hollingsworth.arsnouveau.api.util.BlockUtil");
            Method destroy = blockUtil.getMethod("destroyBlockSafely", Level.class, BlockPos.class, boolean.class, LivingEntity.class);
            return (boolean) destroy.invoke(null, player.level(), pos, true, player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Ars Nouveau BlockUtil unavailable", e);
        }
    }
}
