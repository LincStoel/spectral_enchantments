package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.StackCooldowns;
import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.animated.Animated;
import com.linc.spectralenchantments.animated.AnimatedManager;
import com.linc.spectralenchantments.animated.AnimatedWeaponEntity;
import com.linc.spectralenchantments.spectral.WeaponProfile;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for Animated. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimatedGameTests {
    private static final BlockPos STAND_POS = new BlockPos(4, 2, 1);

    private AnimatedGameTests() {}

    @GameTest(template = "empty")
    public static void onlyHostileMobsAreTargets(GameTestHelper helper) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 2));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 2, 2));
        EnderMan enderman = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(6, 2, 2));
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, STAND_POS);
        helper.assertTrue(Animated.isValidTarget(husk), "Hostile mobs are fought");
        helper.assertFalse(Animated.isValidTarget(cow), "Passive mobs are left alone");
        helper.assertFalse(Animated.isValidTarget(enderman), "A calm neutral mob is left alone");
        helper.assertFalse(Animated.isValidTarget(stand), "Armor stands are never targets");
        helper.assertFalse(Animated.isValidTarget(player(helper, new BlockPos(1, 2, 1))), "Players are never targets");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void standSendsItsWeaponAfterHostiles(GameTestHelper helper) {
        ArmorStand stand = armedStand(helper, Items.IRON_SWORD);
        ItemStack sword = stand.getItemInHand(InteractionHand.MAIN_HAND);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 5));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 3));

        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "Husk not hit yet");
            helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "Passive mobs must be left alone");
            helper.assertTrue(husk.getLastHurtByMob() == null, "A stand's hit is nobody's");
            helper.assertTrue(sword.getDamageValue() >= 1, "Hits should wear down the stand's sword");
            helper.assertTrue(stand.getItemInHand(InteractionHand.MAIN_HAND) == sword, "The sword never leaves the stand");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void standKillsDropNoExperience(GameTestHelper helper) {
        armedStand(helper, Items.DIAMOND_SWORD);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 5));
        husk.setHealth(1f);

        helper.succeedWhen(() -> {
            helper.assertTrue(husk.isDeadOrDying(), "Husk not killed yet");
            AABB around = new AABB(husk.blockPosition()).inflate(4);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around).isEmpty(),
                    "A kill that isn't a player's drops no experience");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void weaponReturnsWhenNothingIsLeft(GameTestHelper helper) {
        ArmorStand stand = armedStand(helper, Items.IRON_SWORD);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 5));

        helper.runAfterDelay(25, () -> {
            helper.assertTrue(AnimatedManager.getGuard(stand) != null, "The weapon should be out after the husk");
            husk.discard();
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(AnimatedManager.getGuard(stand) == null, "With nothing left to fight the weapon should be home");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void standNeedsLineOfSight(GameTestHelper helper) {
        ArmorStand stand = armedStand(helper, Items.IRON_SWORD);
        for (int x = 0; x < 9; x++) {
            for (int y = 1; y < 6; y++) {
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }
        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 5));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(AnimatedManager.getGuard(stand) == null, "A husk behind a wall should go unnoticed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void takingTheItemCallsTheWeaponOff(GameTestHelper helper) {
        ArmorStand stand = armedStand(helper, Items.IRON_SWORD);
        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 7));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(AnimatedManager.getGuard(stand) != null, "The weapon should be out");
            stand.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        });
        helper.runAfterDelay(28, () -> {
            helper.assertTrue(AnimatedManager.getGuard(stand) == null, "With the item gone from the stand there is nothing to fly");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void fightsAtNormalSpeed(GameTestHelper helper) {
        // An iron sword swings 1.6 times a second: a full charge every 12.5 ticks.
        helper.assertTrue(WeaponProfile.of(new ItemStack(Items.IRON_SWORD), 1.0).intervalTicks() == Math.round(20.0 / 1.6),
                "Animated weapons attack at the wielder's speed");
        helper.assertTrue(WeaponProfile.of(new ItemStack(Items.IRON_SWORD), 2.0).intervalTicks() == Math.round(2 * 20.0 / 1.6),
                "Companions attack at half speed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void deployedKillsArePlayerKills(GameTestHelper helper) {
        FakePlayer player = player(helper, STAND_POS);
        ItemStack sword = enchanted(helper, Items.IRON_SWORD);
        player.getInventory().setItem(0, sword);
        player.getInventory().selected = 0;
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 5));
        AnimatedManager.deploy(player, InteractionHand.MAIN_HAND, Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));

        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "Husk not hit yet");
            helper.assertTrue(husk.getLastHurtByMob() == player, "A deployed weapon's hits are the player's");
            helper.assertTrue(sword.getDamageValue() >= 1, "Hits should wear down the deployed sword");
            helper.assertTrue(player.getInventory().getItem(0) == sword, "The sword never leaves the inventory");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deployedWeaponOnlyFightsNearItsSpot(GameTestHelper helper) {
        FakePlayer player = player(helper, STAND_POS);
        player.getInventory().setItem(0, enchanted(helper, Items.IRON_SWORD));
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 12));
        AnimatedManager.deploy(player, InteractionHand.MAIN_HAND, Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 2))));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "A husk 10 blocks from the spot is out of the 4 block radius");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void movingTheItemRecallsTheWeapon(GameTestHelper helper) {
        FakePlayer player = player(helper, STAND_POS);
        ItemStack sword = enchanted(helper, Items.IRON_SWORD);
        player.getInventory().setItem(0, sword);
        AnimatedWeaponEntity weapon = AnimatedManager.deploy(player, InteractionHand.MAIN_HAND,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));

        helper.runAfterDelay(5, () -> {
            player.getInventory().setItem(3, sword);
            player.getInventory().setItem(0, ItemStack.EMPTY);
        });
        helper.runAfterDelay(7, () -> {
            helper.assertFalse(weapon.isReturning(), "Moving the item within the hotbar keeps it deployed");
            player.getInventory().setItem(20, sword);
            player.getInventory().setItem(3, ItemStack.EMPTY);
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(weapon.isReturning(), "Moving the item out of the hotbar calls the weapon back");
            helper.assertTrue(AnimatedManager.getDeployed(player) == null, "It is no longer deployed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void strayingTooFarRecallsTheWeapon(GameTestHelper helper) {
        FakePlayer player = player(helper, STAND_POS);
        player.getInventory().setItem(Inventory.SLOT_OFFHAND, enchanted(helper, Items.IRON_SWORD));
        AnimatedWeaponEntity weapon = AnimatedManager.deploy(player, InteractionHand.OFF_HAND,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(weapon.isReturning(), "Still deployed while the player is near");
            player.setPos(player.getX(), player.getY(), player.getZ() + 20);
        });
        helper.runAfterDelay(7, () -> {
            helper.assertTrue(weapon.isReturning(), "Straying beyond the recall distance calls the weapon back");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void deploymentRunsOut(GameTestHelper helper) {
        FakePlayer player = player(helper, STAND_POS);
        player.getInventory().setItem(0, enchanted(helper, Items.IRON_SWORD));
        AnimatedWeaponEntity weapon = AnimatedManager.deploy(player, InteractionHand.MAIN_HAND,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        helper.runAfterDelay(290, () -> helper.assertFalse(weapon.isReturning(), "Still out before 15 seconds"));
        helper.runAfterDelay(305, () -> {
            helper.assertTrue(weapon.isReturning() || weapon.isRemoved(), "Comes back after 15 seconds");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void recalledWeaponCantBeSwungAtOnce(GameTestHelper helper) {
        FakePlayer player = player(helper, STAND_POS);
        ItemStack sword = enchanted(helper, Items.IRON_SWORD);
        player.getInventory().setItem(0, sword);
        player.getInventory().selected = 0;
        AnimatedWeaponEntity weapon = AnimatedManager.deploy(player, InteractionHand.MAIN_HAND,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        helper.runAfterDelay(3, () -> {
            weapon.recall();
            helper.assertTrue(StackCooldowns.isActive(player.getMainHandItem(), player.level()), "A recalled weapon cools down");
            Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, STAND_POS.east());
            helper.assertTrue(net.neoforged.neoforge.common.CommonHooks.onPlayerAttackTarget(player, husk) == false,
                    "It can't be swung while it cools down");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void mendingMendsFromItsKills(GameTestHelper helper) {
        ArmorStand stand = armedStand(helper, Items.DIAMOND_SWORD);
        ItemStack sword = stand.getItemInHand(InteractionHand.MAIN_HAND);
        sword.enchant(holder(helper, net.minecraft.world.item.enchantment.Enchantments.MENDING), 1);
        sword.setDamageValue(500);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 5));
        husk.setHealth(1f);
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.isDeadOrDying(), "Husk not killed yet");
            helper.assertTrue(sword.getDamageValue() < 499, "The kill's experience should mend the sword, damage is " + sword.getDamageValue());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void thrownTridentGuardsWhereItLands(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        FakePlayer player = player(helper, new BlockPos(4, 6, 4));
        ItemStack trident = enchanted(helper, Items.TRIDENT);
        net.minecraft.world.entity.projectile.ThrownTrident thrown =
                new net.minecraft.world.entity.projectile.ThrownTrident(helper.getLevel(), player, trident);
        thrown.shootFromRotation(player, 90f, 0f, 0f, 2.5f, 0f);
        helper.getLevel().addFreshEntity(thrown);
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown.isRemoved(), "The trident should land");
            AnimatedWeaponEntity guard = helper.getLevel().getEntitiesOfClass(AnimatedWeaponEntity.class,
                    new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(9, 9, 9)).stream()
                    .filter(AnimatedWeaponEntity::isThrown).findFirst().orElse(null);
            helper.assertTrue(guard != null, "The trident should stand guard where it landed");
            helper.assertTrue(guard.getWeaponItem().is(Items.TRIDENT), "It holds the trident itself");
        });
    }

    /** Its owner gone, a thrown weapon waits where it is, saved with the world, rather than dropping (and despawning). */
    @GameTest(template = "empty", timeoutTicks = 260)
    public static void thrownWeaponWaitsForItsOwner(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        FakePlayer player = player(helper, new BlockPos(4, 6, 4));
        net.minecraft.world.entity.projectile.ThrownTrident thrown =
                new net.minecraft.world.entity.projectile.ThrownTrident(helper.getLevel(), player, enchanted(helper, Items.TRIDENT));
        thrown.shootFromRotation(player, 90f, 0f, 0f, 2.5f, 0f);
        helper.getLevel().addFreshEntity(thrown);
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(9, 9, 9);
        helper.runAfterDelay(20, () -> {
            AnimatedWeaponEntity guard = helper.getLevel().getEntitiesOfClass(AnimatedWeaponEntity.class, area).stream()
                    .filter(AnimatedWeaponEntity::isThrown).findFirst().orElse(null);
            helper.assertTrue(guard != null, "The trident should stand guard where it landed");
            player.discard(); // logged out
            helper.runAfterDelay(200, () -> {
                helper.assertTrue(!guard.isRemoved(), "Its owner away, it still waits");
                helper.assertTrue(guard.shouldBeSaved(), "...saved with the world");
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).isEmpty(),
                        "It isn't dropped as an item, which would despawn");
                guard.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void exclusiveWithLoyalty(GameTestHelper helper) {
        helper.assertFalse(Enchantment.areCompatible(holder(helper, ModEnchantments.ANIMATED),
                holder(helper, net.minecraft.world.item.enchantment.Enchantments.LOYALTY)), "Animated and Loyalty are mutually exclusive");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exclusiveWithFarMiner(GameTestHelper helper) {
        Holder<Enchantment> animated = holder(helper, ModEnchantments.ANIMATED);
        helper.assertFalse(Enchantment.areCompatible(animated, holder(helper, ModEnchantments.FAR_MINER)),
                "Animated and Far Miner are mutually exclusive");
        helper.assertTrue(Enchantment.areCompatible(animated, holder(helper, ModEnchantments.GATHER)),
                "Others still combine");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void treasureOnlyByDefault(GameTestHelper helper) {
        Holder<Enchantment> animated = holder(helper, ModEnchantments.ANIMATED);
        helper.assertTrue(animated.is(EnchantmentTags.TREASURE), "Animated should be treasure");
        helper.assertFalse(animated.is(EnchantmentTags.NON_TREASURE), "Animated must not roll at the enchanting table");
        helper.assertTrue(animated.is(EnchantmentTags.TRADEABLE), "Animated should be tradeable, like Mending");
        helper.succeed();
    }

    /** Compat run only: the Ars Nouveau enchanting apparatus recipe loads. */
    @GameTest(template = "empty")
    public static void arsNouveauRecipeLoads(GameTestHelper helper) {
        if (ModList.get().isLoaded("ars_nouveau")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(SpectralEnchantments.id("ars_nouveau/animated_1")).isPresent(),
                    "Missing Ars Nouveau recipe animated_1");
        }
        helper.succeed();
    }

    private static ArmorStand armedStand(GameTestHelper helper, Item item) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, STAND_POS);
        stand.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, item));
        return stand;
    }

    private static FakePlayer player(GameTestHelper helper, BlockPos at) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "animated_test"));
        BlockPos pos = helper.absolutePos(at);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        player.setOnGround(true);
        return player;
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(holder(helper, ModEnchantments.ANIMATED), 1);
        return stack;
    }
}
