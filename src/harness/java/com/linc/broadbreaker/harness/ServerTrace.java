package com.linc.broadbreaker.harness;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

/** Server-side event trace for the compat harness ({@code /bbh trace on|off}); lines go to the server log as [bbh-trace]. */
@EventBusSubscriber(modid = HarnessMod.MOD_ID)
public final class ServerTrace {
    static boolean enabled;
    private static final Logger LOG = LogUtils.getLogger();

    private ServerTrace() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void leftClickFirst(PlayerInteractEvent.LeftClickBlock event) {
        if (enabled && event.getEntity() instanceof ServerPlayer) {
            LOG.info("[bbh-trace] leftclick-first {} {} {} canceled={}", event.getEntity().getName().getString(), event.getAction(),
                    event.getPos().toShortString(), event.isCanceled());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void leftClickLast(PlayerInteractEvent.LeftClickBlock event) {
        if (enabled && event.getEntity() instanceof ServerPlayer) {
            LOG.info("[bbh-trace] leftclick-last {} {} {} canceled={} useItem={}", event.getEntity().getName().getString(), event.getAction(),
                    event.getPos().toShortString(), event.isCanceled(), event.getUseItem());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void breakEvent(BlockEvent.BreakEvent event) {
        if (enabled) {
            LOG.info("[bbh-trace] break {} {} {} canceled={}", event.getPlayer().getName().getString(), event.getPos().toShortString(),
                    event.getState(), event.isCanceled());
        }
    }
}
