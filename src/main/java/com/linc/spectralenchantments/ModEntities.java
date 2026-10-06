package com.linc.spectralenchantments;

import com.linc.spectralenchantments.spectral.SpectralWeaponEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SpectralEnchantments.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpectralWeaponEntity>> SPECTRAL_WEAPON =
            ENTITY_TYPES.register("spectral_weapon", () -> EntityType.Builder.<SpectralWeaponEntity>of(SpectralWeaponEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build("spectral_weapon"));

    private ModEntities() {}
}
