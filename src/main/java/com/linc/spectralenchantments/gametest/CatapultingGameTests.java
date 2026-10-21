package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.ModEffects;
import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.animated.AnimatedWeaponEntity;
import com.linc.spectralenchantments.catapult.CatapultedItem;
import com.linc.spectralenchantments.catapult.CatapultedWeapon;
import com.linc.spectralenchantments.catapult.Catapulting;
import com.linc.spectralenchantments.compat.SableCompat;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for Catapulting. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CatapultingGameTests {
    /** The shooter stands here looking straight down at a stone floor at y=1. */
    private static final BlockPos SHOOTER = new BlockPos(4, 6, 4);
    private static final BlockPos LANDING = new BlockPos(4, 2, 4);

    private CatapultingGameTests() {}

    @GameTest(template = "empty")
    public static void sortsWhatTheOffHandHolds(GameTestHelper helper) {
        FakePlayer player = shooter(helper, Items.STONE);
        ItemStack bow = player.getMainHandItem();
        helper.assertTrue(kind(player, Items.IRON_SWORD) == Catapulting.Kind.WEAPON, "Swords are weapons");
        helper.assertTrue(kind(player, Items.DIAMOND_PICKAXE) == Catapulting.Kind.WEAPON, "Tools are weapons");
        helper.assertTrue(kind(player, Items.TRIDENT) == Catapulting.Kind.WEAPON, "Tridents are weapons");
        helper.assertTrue(kind(player, Items.SNOWBALL) == Catapulting.Kind.THROWABLE, "Snowballs are thrown");
        helper.assertTrue(kind(player, Items.ENDER_PEARL) == Catapulting.Kind.THROWABLE, "Ender pearls are thrown");
        helper.assertTrue(kind(player, Items.DIRT) == Catapulting.Kind.BLOCK, "Dirt is a block");
        helper.assertTrue(kind(player, Items.DIAMOND) == Catapulting.Kind.ITEM, "Diamonds are just items");
        helper.assertTrue(kind(player, Items.ARROW) == null, "Arrows are shot as arrows");
        helper.assertTrue(kind(player, Items.SHIELD) == null, "Shields stay in the off hand");
        helper.assertTrue(kind(player, Items.TOTEM_OF_UNDYING) == null, "Totems stay in the off hand");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(Catapulting.kindFor(player, player.getMainHandItem()) == null, "A plain bow fires nothing from the off hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertTrue(Catapulting.kindFor(player, bow) == null, "An empty off hand leaves the bow as it was");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void offHandItemIsOfferedAsAmmo(GameTestHelper helper) {
        FakePlayer player = shooter(helper, Items.IRON_SWORD);
        helper.assertTrue(player.getProjectile(player.getMainHandItem()) == player.getOffhandItem(),
                "The bow should draw with the off-hand weapon and no arrows");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void weaponAboutToBreakIsNotFired(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(sword.getMaxDamage() - 1);
        FakePlayer player = shooter(helper, sword);
        helper.assertTrue(Catapulting.kindFor(player, player.getMainHandItem()) == null, "Firing would break the sword");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void weaponSticksWhereItLands(GameTestHelper helper) {
        floor(helper);
        FakePlayer player = shooter(helper, Items.IRON_SWORD);
        ItemStack bow = player.getMainHandItem();
        helper.assertTrue(Catapulting.fire(player, bow, 1f), "The sword should be fired");
        helper.assertTrue(player.getOffhandItem().isEmpty(), "The sword leaves the off hand");
        helper.assertTrue(bow.getDamageValue() == 2, "The bow loses 2 durability, not " + bow.getDamageValue());

        helper.succeedWhen(() -> {
            List<CatapultedWeapon> weapons = helper.getLevel().getEntitiesOfClass(CatapultedWeapon.class, area(helper));
            helper.assertTrue(weapons.size() == 1, "Expected one fired weapon, found " + weapons.size());
            CatapultedWeapon weapon = weapons.getFirst();
            helper.assertTrue(weapon.isStuck(), "The sword should stick in the ground");
            helper.assertFalse(weapon.isReturning(), "A plain sword stays where it lands");
            helper.assertTrue(weapon.getPickupItemStackOrigin().is(Items.IRON_SWORD), "It can be picked up as the sword");
            helper.assertTrue(weapon.getPickupItemStackOrigin().getDamageValue() == 1, "The sword loses 1 durability");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void animatedWeaponGuardsWhereItLands(GameTestHelper helper) {
        floor(helper);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(holder(helper, ModEnchantments.ANIMATED), 1);
        FakePlayer player = shooter(helper, sword);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(CatapultedWeapon.class, area(helper)).isEmpty(),
                    "The fired sword should have landed");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(AnimatedWeaponEntity.class, area(helper))
                    .stream().anyMatch(AnimatedWeaponEntity::isThrown), "The Animated sword should stand guard where it landed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void loyalWeaponComesBack(GameTestHelper helper) {
        floor(helper);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(holder(helper, Enchantments.LOYALTY), 3);
        FakePlayer player = shooter(helper, sword);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getEntitiesOfClass(CatapultedWeapon.class, area(helper))
                .stream().anyMatch(CatapultedWeapon::isReturning), "The Loyalty sword should head home"));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blockIsPlacedWhereItLands(GameTestHelper helper) {
        floor(helper);
        FakePlayer player = shooter(helper, new ItemStack(Items.OAK_PLANKS, 2));
        helper.assertTrue(Catapulting.fire(player, player.getMainHandItem(), 1f), "The planks should be fired");
        helper.assertTrue(player.getOffhandItem().getCount() == 1, "One plank is fired at a time");
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.OAK_PLANKS, LANDING);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(CatapultedItem.class, area(helper)).isEmpty(), "The projectile is gone");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blockPloughsThroughAMob(GameTestHelper helper) {
        floor(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, LANDING); // unarmored, unlike husks
        FakePlayer player = shooter(helper, Items.OAK_PLANKS);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.runAfterDelay(20, () -> {
            float expected = 2f; // a common item: one heart
            helper.assertTrue(pig.getHealth() <= pig.getMaxHealth() - expected + 0.01f,
                    "The pig should take one heart, health is " + pig.getHealth());
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(CatapultedItem.class, area(helper)).isEmpty(), "The block flew on and landed");
            boolean placed = helper.getLevel().getBlockState(helper.absolutePos(LANDING)).is(Blocks.OAK_PLANKS);
            boolean dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).stream()
                    .anyMatch(item -> item.getItem().is(Items.OAK_PLANKS));
            helper.assertTrue(placed || dropped, "The block isn't destroyed: it is placed, or dropped beside the pig");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void anvilHitsLikeAFallingAnvil(GameTestHelper helper) {
        floor(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, LANDING);
        FakePlayer player = shooter(helper, Items.ANVIL);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.runAfterDelay(20, () -> {
            // Fired from 4 blocks up: about as if it had fallen that far, 2 points a block.
            helper.assertTrue(pig.getHealth() <= pig.getMaxHealth() - 4f, "The anvil should hurt like a falling one, health is " + pig.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void snowballLeavesSnow(GameTestHelper helper) {
        floor(helper);
        FakePlayer player = shooter(helper, Items.SNOWBALL);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.SNOW, LANDING));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fireChargeLightsAFire(GameTestHelper helper) {
        floor(helper);
        FakePlayer player = shooter(helper, Items.FIRE_CHARGE);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.FIRE, LANDING);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).isEmpty(), "The fire charge is used up");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void spawnEggHatches(GameTestHelper helper) {
        floor(helper);
        FakePlayer player = shooter(helper, Items.CHICKEN_SPAWN_EGG);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.succeedWhen(() -> helper.assertEntityPresent(EntityType.CHICKEN));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void waterBucketPoursAndStaysInHand(GameTestHelper helper) {
        floor(helper);
        FakePlayer player = shooter(helper, Items.WATER_BUCKET);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.assertTrue(player.getOffhandItem().is(Items.BUCKET), "The empty bucket stays in the off hand");
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.WATER, LANDING));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fishBucketFlingsTheFish(GameTestHelper helper) {
        floor(helper);
        FakePlayer player = shooter(helper, Items.COD_BUCKET);
        helper.assertTrue(Catapulting.fire(player, player.getMainHandItem(), 1f), "The cod should be fired");
        helper.assertTrue(player.getOffhandItem().is(Items.BUCKET), "The empty bucket stays in the off hand");
        helper.assertEntityPresent(EntityType.COD);
        helper.runAfterDelay(20, () -> {
            helper.assertBlockNotPresent(Blocks.WATER, LANDING);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void armorIsPutOn(GameTestHelper helper) {
        floor(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, LANDING);
        zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        FakePlayer player = shooter(helper, Items.IRON_HELMET);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.succeedWhen(() -> helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "The zombie should be wearing the helmet"));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void foodIsEaten(GameTestHelper helper) {
        floor(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, LANDING);
        FakePlayer player = shooter(helper, Items.CARROT);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.isInLove(), "A pig fed a carrot falls in love");
            helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "Food isn't thrown hard");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void shearsShear(GameTestHelper helper) {
        floor(helper);
        Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, LANDING);
        FakePlayer player = shooter(helper, Items.SHEARS);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.succeedWhen(() -> {
            helper.assertTrue(sheep.isSheared(), "The sheep should be shorn");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).stream()
                    .anyMatch(item -> item.getItem().is(Items.SHEARS)), "The shears drop");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fireworksFlyStraight(GameTestHelper helper) {
        FakePlayer player = shooter(helper, new ItemStack(Items.FIREWORK_ROCKET, 2));
        helper.assertTrue(Catapulting.fire(player, player.getMainHandItem(), 1f), "The rocket should be fired");
        helper.assertTrue(player.getOffhandItem().getCount() == 1, "One rocket at a time");
        List<FireworkRocketEntity> rockets = helper.getLevel().getEntitiesOfClass(FireworkRocketEntity.class, area(helper).inflate(4));
        helper.assertTrue(rockets.size() == 1 && rockets.getFirst().isShotAtAngle(), "It flies like a crossbow's rocket");
        helper.succeed();
    }

    /** Compat run only: with Sable, a block can fly as a physics object and sets itself down where it lands. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void physicsBlockSetsItselfDown(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sable")) {
            helper.succeed();
            return;
        }
        floor(helper);
        SableCompat.forceEnabled = true;
        FakePlayer player = shooter(helper, Items.OAK_PLANKS);
        boolean fired;
        try {
            // A full draw straight down at a one-block floor: fast enough to pass through it between physics ticks.
            fired = Catapulting.fire(player, player.getMainHandItem(), 1f);
        } finally {
            SableCompat.forceEnabled = false;
        }
        helper.assertTrue(fired, "The planks should be fired");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(CatapultedItem.class, area(helper)).isEmpty(),
                "Fired as a physics object, not a projectile");
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(LANDING)).is(Blocks.OAK_PLANKS)
                || helper.getLevel().getBlockState(helper.absolutePos(LANDING.above())).is(Blocks.OAK_PLANKS),
                "The block should set itself down where it lands"));
    }

    @GameTest(template = "empty")
    public static void sortsTheNewKinds(GameTestHelper helper) {
        FakePlayer player = shooter(helper, Items.STONE);
        helper.assertTrue(kind(player, Items.FIREWORK_ROCKET) == Catapulting.Kind.FIREWORK, "Rockets fly as rockets");
        helper.assertTrue(kind(player, Items.SALMON_BUCKET) == Catapulting.Kind.MOB_BUCKET, "Fish buckets fling the fish");
        helper.assertTrue(kind(player, Items.FISHING_ROD) == Catapulting.Kind.THROWABLE, "Fishing rods cast their lure");
        helper.assertTrue(kind(player, Items.POWDER_SNOW_BUCKET) == Catapulting.Kind.ITEM, "Powder snow is poured, not placed as a block");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void itemsDealDamageByRarity(GameTestHelper helper) {
        floor(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, LANDING); // unarmored, unlike husks
        ItemStack uncommon = new ItemStack(Items.DIAMOND);
        uncommon.set(DataComponents.RARITY, Rarity.UNCOMMON); // two hearts
        FakePlayer player = shooter(helper, uncommon);
        Catapulting.fire(player, player.getMainHandItem(), 1f);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(pig.getHealth() <= pig.getMaxHealth() - 4f + 0.01f,
                    "The pig should take two hearts, health is " + pig.getHealth());
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).stream()
                    .anyMatch(item -> item.getItem().is(Items.DIAMOND)), "An item that wasn't used pops out where it hit");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void throwablesAreThrownAtBowSpeed(GameTestHelper helper) {
        FakePlayer player = shooter(helper, new ItemStack(Items.SNOWBALL, 4));
        ItemStack bow = player.getMainHandItem();
        helper.assertTrue(Catapulting.fire(player, bow, 1f), "The snowball should be thrown");
        helper.assertTrue(player.getOffhandItem().getCount() == 3, "Throwing uses up a snowball");
        List<Snowball> snowballs = helper.getLevel().getEntitiesOfClass(Snowball.class, area(helper).inflate(4));
        helper.assertTrue(snowballs.size() == 1, "Expected one snowball, found " + snowballs.size());
        helper.assertTrue(snowballs.getFirst().getDeltaMovement().length() > 2.5, "The snowball flies at bow speed");
        helper.assertTrue(bow.getDamageValue() == 2, "The bow loses 2 durability");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void treasureOnlyByDefault(GameTestHelper helper) {
        Holder<Enchantment> catapulting = holder(helper, ModEnchantments.CATAPULTING);
        helper.assertTrue(catapulting.is(EnchantmentTags.TREASURE), "Catapulting is treasure by default");
        helper.assertFalse(catapulting.is(EnchantmentTags.NON_TREASURE), "Catapulting doesn't roll at the enchanting table");
        helper.assertTrue(catapulting.value().isSupportedItem(new ItemStack(Items.BOW)), "Catapulting goes on bows");
        helper.assertFalse(catapulting.value().isSupportedItem(new ItemStack(Items.CROSSBOW)), "Catapulting doesn't go on crossbows");
        helper.succeed();
    }

    /** Compat run only: the Ars Nouveau enchanting apparatus recipe loads. */
    @GameTest(template = "empty")
    public static void catapultingArsNouveauRecipeLoads(GameTestHelper helper) {
        if (ModList.get().isLoaded("ars_nouveau")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(SpectralEnchantments.id("ars_nouveau/catapulting_1")).isPresent(),
                    "Missing Ars Nouveau recipe catapulting_1");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lockedCursedItemIsNotFired(GameTestHelper helper) {
        ItemStack cursed = new ItemStack(Items.IRON_SWORD);
        cursed.enchant(holder(helper, Enchantments.VANISHING_CURSE), 1);
        FakePlayer player = shooter(helper, cursed);
        helper.assertTrue(Catapulting.kindFor(player, player.getMainHandItem()) == Catapulting.Kind.WEAPON, "Not Cursed: it's fired");
        player.addEffect(new MobEffectInstance(ModEffects.CURSED, 200));
        helper.assertTrue(Catapulting.kindFor(player, player.getMainHandItem()) == null, "While Cursed it stays in hand");
        helper.succeed();
    }

    /** With nobody to answer for it (the shooter gone mid-flight), a fired bucket changes nothing: it just drops. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void lavaWithoutAShooterIsNotPoured(GameTestHelper helper) {
        floor(helper);
        CatapultedItem item = dropWithoutShooter(helper, Items.LAVA_BUCKET);
        helper.succeedWhen(() -> {
            helper.assertTrue(item.isRemoved(), "Not landed yet");
            helper.assertBlockNotPresent(Blocks.LAVA, LANDING);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).stream()
                    .anyMatch(drop -> drop.getItem().is(Items.LAVA_BUCKET)), "The lava bucket drops instead");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void crystalWithoutAShooterDoesNotExplode(GameTestHelper helper) {
        floor(helper);
        CatapultedItem item = dropWithoutShooter(helper, Items.END_CRYSTAL);
        helper.succeedWhen(() -> {
            helper.assertTrue(item.isRemoved(), "Not landed yet");
            helper.assertBlockPresent(Blocks.STONE, LANDING.below());
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).stream()
                    .anyMatch(drop -> drop.getItem().is(Items.END_CRYSTAL)), "The crystal drops instead");
        });
    }

    /** {@code item} falling onto the landing spot, fired by a zombie (so no player shot it). */
    private static CatapultedItem dropWithoutShooter(GameTestHelper helper, Item item) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        CatapultedItem projectile = new CatapultedItem(helper.getLevel(), zombie, new ItemStack(item), 1f);
        BlockPos above = helper.absolutePos(LANDING.above(4));
        projectile.setPos(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
        projectile.setDeltaMovement(0, -1.5, 0);
        helper.getLevel().addFreshEntity(projectile);
        return projectile;
    }

    private static Catapulting.Kind kind(FakePlayer player, Item item) {
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));
        return Catapulting.kindFor(player, player.getMainHandItem());
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static AABB area(GameTestHelper helper) {
        return new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(9, 12, 9);
    }

    private static FakePlayer shooter(GameTestHelper helper, Item offhand) {
        return shooter(helper, new ItemStack(offhand));
    }

    /** A survival player above the floor, looking straight down, a Catapulting bow in hand and {@code offhand}. */
    private static FakePlayer shooter(GameTestHelper helper, ItemStack offhand) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "catapult_test"));
        BlockPos pos = helper.absolutePos(SHOOTER);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 90f);
        ItemStack bow = new ItemStack(Items.BOW);
        bow.enchant(holder(helper, ModEnchantments.CATAPULTING), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        return player;
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}
