package com.linc.spectralenchantments;

import com.linc.spectralenchantments.animated.AnimatedWeaponEntity;
import com.linc.spectralenchantments.catapult.CatapultedItem;
import com.linc.spectralenchantments.catapult.CatapultedWeapon;
import com.linc.spectralenchantments.curse.HauntingGhostEntity;
import com.linc.spectralenchantments.curse.PossessedWeaponEntity;
import com.linc.spectralenchantments.doppelganger.DoppelgangerEntity;
import com.linc.spectralenchantments.explosive.BlazeBolt;
import com.linc.spectralenchantments.explosive.WaterBolt;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SpectralEnchantments.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<AnimatedWeaponEntity>> ANIMATED_WEAPON =
            ENTITY_TYPES.register("animated_weapon", () -> EntityType.Builder.<AnimatedWeaponEntity>of(AnimatedWeaponEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSummon()
                    .fireImmune()
                    .build("animated_weapon"));

    public static final DeferredHolder<EntityType<?>, EntityType<CatapultedWeapon>> CATAPULTED_WEAPON =
            ENTITY_TYPES.register("catapulted_weapon", () -> EntityType.Builder.<CatapultedWeapon>of(CatapultedWeapon::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .eyeHeight(0.13f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("catapulted_weapon"));

    public static final DeferredHolder<EntityType<?>, EntityType<CatapultedItem>> CATAPULTED_ITEM =
            ENTITY_TYPES.register("catapulted_item", () -> EntityType.Builder.<CatapultedItem>of(CatapultedItem::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build("catapulted_item"));

    public static final DeferredHolder<EntityType<?>, EntityType<BlazeBolt>> BLAZE_BOLT =
            ENTITY_TYPES.register("blaze_bolt", () -> EntityType.Builder.<BlazeBolt>of(BlazeBolt::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .eyeHeight(0.13f)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("blaze_bolt"));

    public static final DeferredHolder<EntityType<?>, EntityType<WaterBolt>> WATER_BOLT =
            ENTITY_TYPES.register("water_bolt", () -> EntityType.Builder.<WaterBolt>of(WaterBolt::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .eyeHeight(0.13f)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("water_bolt"));

    public static final DeferredHolder<EntityType<?>, EntityType<PossessedWeaponEntity>> POSSESSED_WEAPON =
            ENTITY_TYPES.register("possessed_weapon", () -> EntityType.Builder.<PossessedWeaponEntity>of(PossessedWeaponEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 0.9f)
                    .eyeHeight(0.45f)
                    .clientTrackingRange(8)
                    .build("possessed_weapon"));

    public static final DeferredHolder<EntityType<?>, EntityType<DoppelgangerEntity>> DOPPELGANGER =
            ENTITY_TYPES.register("doppelganger", () -> EntityType.Builder.<DoppelgangerEntity>of(DoppelgangerEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build("doppelganger"));

    public static final DeferredHolder<EntityType<?>, EntityType<HauntingGhostEntity>> HAUNTING_GHOST =
            ENTITY_TYPES.register("haunting_ghost", () -> EntityType.Builder.<HauntingGhostEntity>of(HauntingGhostEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build("haunting_ghost"));

    private ModEntities() {}
}
