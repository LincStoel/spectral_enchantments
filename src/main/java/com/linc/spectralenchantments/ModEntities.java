package com.linc.spectralenchantments;

import com.linc.spectralenchantments.animated.AnimatedWeaponEntity;
import com.linc.spectralenchantments.catapult.CatapultedItem;
import com.linc.spectralenchantments.catapult.CatapultedWeapon;
import com.linc.spectralenchantments.explosive.BlazeBolt;
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

    public static final DeferredHolder<EntityType<?>, EntityType<AnimatedWeaponEntity>> ANIMATED_WEAPON =
            ENTITY_TYPES.register("animated_weapon", () -> EntityType.Builder.<AnimatedWeaponEntity>of(AnimatedWeaponEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build("animated_weapon"));

    public static final DeferredHolder<EntityType<?>, EntityType<CatapultedWeapon>> CATAPULTED_WEAPON =
            ENTITY_TYPES.register("catapulted_weapon", () -> EntityType.Builder.<CatapultedWeapon>of(CatapultedWeapon::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .eyeHeight(0.13f)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("catapulted_weapon"));

    public static final DeferredHolder<EntityType<?>, EntityType<CatapultedItem>> CATAPULTED_ITEM =
            ENTITY_TYPES.register("catapulted_item", () -> EntityType.Builder.<CatapultedItem>of(CatapultedItem::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("catapulted_item"));

    public static final DeferredHolder<EntityType<?>, EntityType<BlazeBolt>> BLAZE_BOLT =
            ENTITY_TYPES.register("blaze_bolt", () -> EntityType.Builder.<BlazeBolt>of(BlazeBolt::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .eyeHeight(0.13f)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("blaze_bolt"));

    private ModEntities() {}
}
