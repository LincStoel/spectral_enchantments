package com.linc.spectralenchantments.projectile;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** An arrow-like projectile drawn as an item flying point first (see ItemArrowRenderer). The item is synced to clients. */
public abstract class ItemArrow extends AbstractArrow implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(ItemArrow.class, EntityDataSerializers.ITEM_STACK);

    protected ItemArrow(EntityType<? extends ItemArrow> type, Level level) {
        super(type, level);
    }

    protected ItemArrow(EntityType<? extends ItemArrow> type, LivingEntity owner, Level level, ItemStack pickup, ItemStack weapon) {
        super(type, owner, level, pickup, weapon);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ITEM, ItemStack.EMPTY);
    }

    protected void setItem(ItemStack stack) {
        entityData.set(ITEM, stack.copyWithCount(1));
    }

    @Override
    public ItemStack getItem() {
        return entityData.get(ITEM);
    }
}
