package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.ModEntities;
import com.linc.spectralenchantments.explosive.BlazeBolt;
import com.linc.spectralenchantments.explosive.ExplosiveBolts;
import com.linc.spectralenchantments.explosive.WaterBolt;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for Explosive Bolts. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExplosiveBoltsGameTests {
    private static final BlockPos SHOOTER = new BlockPos(4, 6, 4);

    private ExplosiveBoltsGameTests() {}

    @GameTest(template = "empty")
    public static void loadsExplosiveAmmunition(GameTestHelper helper) {
        FakePlayer player = shooter(helper, 90f);
        ItemStack crossbow = player.getMainHandItem();
        player.getInventory().setItem(5, new ItemStack(Items.BLAZE_ROD));
        helper.assertTrue(player.getProjectile(crossbow).is(Items.BLAZE_ROD), "Blaze rods load as ammunition");

        player.getInventory().setItem(2, new ItemStack(Items.ARROW));
        helper.assertTrue(player.getProjectile(crossbow).is(Items.ARROW), "Ammunition is taken in inventory order");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GHAST_TEAR));
        helper.assertTrue(player.getProjectile(crossbow).is(Items.GHAST_TEAR), "Ammunition in hand comes first");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
        player.getInventory().setItem(2, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertTrue(player.getProjectile(player.getMainHandItem()).isEmpty(), "A plain crossbow can't load blaze rods");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireChargeFiresASmallFireball(GameTestHelper helper) {
        firesAs(helper, Items.FIRE_CHARGE, SmallFireball.class);
    }

    @GameTest(template = "empty")
    public static void ghastTearFiresAGhastFireball(GameTestHelper helper) {
        firesAs(helper, Items.GHAST_TEAR, LargeFireball.class);
    }

    @GameTest(template = "empty")
    public static void blazeRodFiresABlazeBolt(GameTestHelper helper) {
        firesAs(helper, Items.BLAZE_ROD, BlazeBolt.class);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blazeBoltSpraysFire(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Husk near = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(6, 2, 4));
        Husk far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(0, 2, 0));
        FakePlayer player = shooter(helper, 90f);
        shoot(player, Items.BLAZE_ROD);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.FIRE, new BlockPos(4, 2, 4));
            helper.assertBlockPresent(Blocks.FIRE, new BlockPos(3, 2, 4));
            helper.assertBlockNotPresent(Blocks.FIRE, new BlockPos(0, 2, 8));
            helper.assertTrue(near.isOnFire(), "A husk within the spray burns");
            helper.assertFalse(far.isOnFire(), "A husk out of reach doesn't");
        });
    }

    @GameTest(template = "empty")
    public static void notTreasureByDefault(GameTestHelper helper) {
        Holder<Enchantment> bolts = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.EXPLOSIVE_BOLTS);
        helper.assertTrue(bolts.is(EnchantmentTags.NON_TREASURE), "Explosive Bolts rolls at the enchanting table");
        helper.assertFalse(bolts.is(EnchantmentTags.TREASURE), "Explosive Bolts isn't treasure by default");
        helper.assertTrue(bolts.value().isSupportedItem(new ItemStack(Items.CROSSBOW)), "Explosive Bolts goes on crossbows");
        helper.assertFalse(bolts.value().isSupportedItem(new ItemStack(Items.BOW)), "Explosive Bolts doesn't go on bows");
        helper.succeed();
    }

    /** Compat run only: the Ars Nouveau enchanting apparatus recipe loads. */
    @GameTest(template = "empty")
    public static void explosiveBoltsArsNouveauRecipeLoads(GameTestHelper helper) {
        if (ModList.get().isLoaded("ars_nouveau")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(SpectralEnchantments.id("ars_nouveau/explosive_bolts_1")).isPresent(),
                    "Missing Ars Nouveau recipe explosive_bolts_1");
        }
        helper.succeed();
    }

    /** Shooting the loaded ammunition (upwards, out of the way) makes {@code expected} and no arrow. */
    @GameTest(template = "empty")
    public static void sortsWaterAndPotions(GameTestHelper helper) {
        helper.assertTrue(ExplosiveBolts.behavior(PotionContents.createItemStack(Items.POTION, Potions.WATER)) == ExplosiveBolts.Behavior.WATER_BURST,
                "Water bottles burst");
        helper.assertTrue(ExplosiveBolts.behavior(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS)) == null,
                "Drinkable potions aren't ammunition");
        helper.assertTrue(ExplosiveBolts.behavior(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING)) == ExplosiveBolts.Behavior.THROWN_POTION,
                "Splash potions are thrown");
        helper.assertTrue(ExplosiveBolts.behavior(PotionContents.createItemStack(Items.LINGERING_POTION, Potions.HARMING)) == ExplosiveBolts.Behavior.THROWN_POTION,
                "Lingering potions are thrown");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void splashPotionIsThrown(GameTestHelper helper) {
        FakePlayer player = shooter(helper, -90f);
        ItemStack crossbow = player.getMainHandItem();
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING)));
        ((CrossbowItem) crossbow.getItem()).performShooting(player.level(), player, InteractionHand.MAIN_HAND, crossbow, 3.15f, 0f, null);
        AABB around = new AABB(player.blockPosition()).inflate(8);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ThrownPotion.class, around).size() == 1, "Expected a thrown potion");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(AbstractArrow.class, around).isEmpty(), "No arrow should be fired");
        helper.getLevel().getEntitiesOfClass(ThrownPotion.class, around).forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void waterBottleFiresAWaterBolt(GameTestHelper helper) {
        FakePlayer player = shooter(helper, -90f);
        ItemStack crossbow = player.getMainHandItem();
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(PotionContents.createItemStack(Items.POTION, Potions.WATER)));
        ((CrossbowItem) crossbow.getItem()).performShooting(player.level(), player, InteractionHand.MAIN_HAND, crossbow, 3.15f, 0f, null);
        AABB around = new AABB(player.blockPosition()).inflate(8);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(WaterBolt.class, around).size() == 1, "Expected a water bolt");
        helper.getLevel().getEntitiesOfClass(WaterBolt.class, around).forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void waterBoltDousesFires(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.NETHERRACK);
            }
        }
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.FIRE);
        helper.setBlock(new BlockPos(7, 2, 4), Blocks.FIRE); // 3 blocks away
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.CAMPFIRE);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 4));
        husk.igniteForSeconds(10f);
        WaterBolt bolt = ModEntities.WATER_BOLT.get().create(helper.getLevel());
        bolt.burst(Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        helper.assertBlockNotPresent(Blocks.FIRE, new BlockPos(4, 2, 4));
        helper.assertBlockNotPresent(Blocks.FIRE, new BlockPos(7, 2, 4));
        helper.assertFalse(helper.getBlockState(new BlockPos(4, 2, 1)).getValue(CampfireBlock.LIT), "The campfire is put out");
        helper.assertFalse(husk.isOnFire(), "Burning creatures are put out");
        helper.succeed();
    }

    private static void firesAs(GameTestHelper helper, Item ammo, Class<? extends Entity> expected) {
        FakePlayer player = shooter(helper, -90f);
        shoot(player, ammo);
        AABB around = new AABB(player.blockPosition()).inflate(8);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(expected, around).size() == 1,
                "Expected a " + expected.getSimpleName() + " from " + ammo);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(AbstractArrow.class, around, arrow -> !(arrow instanceof BlazeBolt)).isEmpty(),
                "No arrow should be fired");
        helper.getLevel().getEntitiesOfClass(expected, around).forEach(Entity::discard);
        helper.succeed();
    }

    /** Loads {@code ammo} into the player's crossbow and fires it, with no spread. */
    private static void shoot(FakePlayer player, Item ammo) {
        ItemStack crossbow = player.getMainHandItem();
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(ammo)));
        ((CrossbowItem) crossbow.getItem()).performShooting(player.level(), player, InteractionHand.MAIN_HAND, crossbow, 3.15f, 0f, null);
    }

    private static FakePlayer shooter(GameTestHelper helper, float pitch) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "explosive_test"));
        BlockPos pos = helper.absolutePos(SHOOTER);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, pitch);
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.EXPLOSIVE_BOLTS), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
        return player;
    }
}
