package com.linc.spectralenchantments.compat;

import com.linc.spectralenchantments.curse.PossessedWeaponEntity;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Optional Ars Nouveau integration, by reflection so Ars Nouveau stays a soft dependency: Dispel cast on a
 * possessed weapon drives the curse out ({@link PossessedWeaponEntity#dispel}). Listens to Ars Nouveau's
 * {@code DispelEvent.Pre}, cancelling it so the spell does nothing else to the weapon.
 */
public final class ArsNouveauCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DISPEL_EVENT = "com.hollingsworth.arsnouveau.api.event.DispelEvent";

    private ArsNouveauCompat() {}

    public static void register() {
        if (!ModList.get().isLoaded("ars_nouveau")) {
            return;
        }
        try {
            Field hit = Class.forName(DISPEL_EVENT).getField("rayTraceResult");
            listen(Class.forName(DISPEL_EVENT + "$Pre").asSubclass(Event.class), hit);
        } catch (ReflectiveOperationException | ClassCastException e) {
            LOGGER.warn("Couldn't hook Ars Nouveau's Dispel; it won't free possessed weapons", e);
        }
    }

    private static <T extends Event> void listen(Class<T> type, Field hit) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, event -> {
            try {
                onDispel(event, hit.get(event));
            } catch (IllegalAccessException e) {
                LOGGER.warn("Couldn't read Ars Nouveau's DispelEvent", e);
            }
        });
    }

    /** Frees the possessed weapon the spell hit, if it hit one. Public for the GameTests. */
    public static boolean onDispel(Event event, Object hitResult) {
        if (hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof PossessedWeaponEntity weapon
                && !weapon.level().isClientSide && weapon.isAlive()) {
            weapon.dispel();
            if (event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
            return true;
        }
        return false;
    }
}
