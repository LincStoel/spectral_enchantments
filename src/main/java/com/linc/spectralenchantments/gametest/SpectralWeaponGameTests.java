package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.spectral.SpectralWeaponEntity;
import com.linc.spectralenchantments.spectral.SpectralWeaponManager;
import com.linc.spectralenchantments.spectral.SpectralWeapons;
import com.linc.spectralenchantments.spectral.WeaponProfile;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for Spectral Weapon. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpectralWeaponGameTests {
    private static final BlockPos PLAYER_POS = new BlockPos(4, 2, 1);

    private SpectralWeaponGameTests() {}

    @GameTest(template = "empty")
    public static void leftmostItemManifestsButNotTheMainHand(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.getInventory().setItem(0, enchanted(helper, Items.IRON_SWORD, 1));
        player.getInventory().setItem(2, enchanted(helper, Items.IRON_AXE, 1));
        player.getInventory().setItem(5, enchanted(helper, Items.DIAMOND_SWORD, 1));

        player.getInventory().selected = 1;
        SpectralWeapons.Selection selection = SpectralWeapons.select(player);
        helper.assertTrue(selection != null && selection.slot() == 0, "The leftmost hotbar item should manifest");

        player.getInventory().selected = 0;
        selection = SpectralWeapons.select(player);
        helper.assertTrue(selection != null && selection.slot() == 2, "The item in the main hand must not manifest; the next one should");

        player.getInventory().setItem(Inventory.SLOT_OFFHAND, enchanted(helper, Items.GOLDEN_SWORD, 1));
        selection = SpectralWeapons.select(player);
        helper.assertTrue(selection != null && selection.slot() == 2,
                "With a weapon in the main hand the off-hand weapon must not manifest (no dual-wielding a duplicate)");

        player.getInventory().selected = 1;
        selection = SpectralWeapons.select(player);
        helper.assertTrue(selection != null && selection.slot() == Inventory.SLOT_OFFHAND,
                "With an empty main hand the off hand, left of the hotbar, wins");

        player.getInventory().setItem(1, new ItemStack(Items.DIRT));
        selection = SpectralWeapons.select(player);
        helper.assertTrue(selection != null && selection.slot() == Inventory.SLOT_OFFHAND,
                "A non-weapon in the main hand doesn't block the off hand");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void attacksOnlyWhatTheOwnerHit(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack sword = enchanted(helper, Items.IRON_SWORD, 2);
        player.getInventory().setItem(0, sword);
        player.getInventory().selected = 4;
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 4));
        Spider spider = helper.spawnWithNoFreeWill(EntityType.SPIDER, new BlockPos(2, 2, 3));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(7, 2, 2));
        helper.onEachTick(() -> SpectralWeaponManager.tick(player));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth() && spider.getHealth() == spider.getMaxHealth(),
                    "Nothing is attacked until the owner hits something");
            helper.assertTrue(SpectralWeaponManager.ownerHit(player, husk), "Should lock on to what the owner hit");
            helper.assertFalse(SpectralWeaponManager.ownerHit(player, spider), "Stays locked on when the owner hits something else");
            helper.succeedWhen(() -> {
                helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "Husk not hit yet");
                helper.assertTrue(spider.getHealth() == spider.getMaxHealth(), "Only the locked-on target is attacked");
                helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "Bystanders must be left alone");
                helper.assertTrue(husk.getLastHurtByMob() == player, "The hit should be credited to the owner");
                helper.assertTrue(sword.getDamageValue() == 1, "One hit should cost 1 durability, used " + sword.getDamageValue());
            });
        });
    }

    @GameTest(template = "empty")
    public static void lockReleasesWhenTheTargetDies(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.getInventory().setItem(0, enchanted(helper, Items.IRON_SWORD, 2));
        player.getInventory().selected = 4;
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 4));
        Spider spider = helper.spawnWithNoFreeWill(EntityType.SPIDER, new BlockPos(2, 2, 3));
        helper.onEachTick(() -> SpectralWeaponManager.tick(player));

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(SpectralWeaponManager.ownerHit(player, husk), "Should lock on to the husk");
            husk.kill();
            helper.assertTrue(SpectralWeaponManager.ownerHit(player, spider), "Once its target is dead the weapon takes the next one");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void anvilOnlyItemsDealHalfAHeart(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack stick = enchanted(helper, Items.STICK, 1);
        helper.assertFalse(WeaponProfile.of(stick).full(), "A stick is not a melee weapon");
        player.getInventory().setItem(0, stick);
        player.getInventory().selected = 4;
        // A spider has no armor, so the hit lands at full value.
        Spider spider = helper.spawnWithNoFreeWill(EntityType.SPIDER, new BlockPos(4, 2, 3));
        helper.onEachTick(() -> SpectralWeaponManager.tick(player));

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(SpectralWeaponManager.ownerHit(player, spider), "Should lock on to the spider");
            helper.succeedWhen(() -> {
                helper.assertTrue(spider.getHealth() < spider.getMaxHealth(), "Spider not hit yet");
                helper.assertTrue(spider.getMaxHealth() - spider.getHealth() == WeaponProfile.WEAK_DAMAGE,
                        "Weak hit should deal half a heart, dealt " + (spider.getMaxHealth() - spider.getHealth()));
            });
        });
    }

    @GameTest(template = "empty")
    public static void wontLockOnOutsideTheRadius(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.getInventory().setItem(0, enchanted(helper, Items.IRON_SWORD, 1)); // 6 blocks (+2 slack)
        player.getInventory().selected = 4;
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 12));
        helper.onEachTick(() -> SpectralWeaponManager.tick(player));

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(SpectralWeaponManager.getActive(player) != null, "The spectral weapon should be out");
            helper.assertFalse(SpectralWeaponManager.ownerHit(player, husk), "A husk 11 blocks away is outside the level I radius");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void lockReleasesWhenTheTargetLeavesReach(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.getInventory().setItem(0, enchanted(helper, Items.IRON_SWORD, 1)); // 6 blocks (+2 slack)
        player.getInventory().selected = 4;
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 4));
        Spider spider = helper.spawnWithNoFreeWill(EntityType.SPIDER, new BlockPos(2, 2, 3));
        helper.onEachTick(() -> SpectralWeaponManager.tick(player));

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(SpectralWeaponManager.ownerHit(player, husk), "Should lock on to the husk");
            BlockPos far = helper.absolutePos(new BlockPos(4, 2, 14));
            husk.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        });
        helper.runAfterDelay(10, () -> {
            SpectralWeaponEntity weapon = SpectralWeaponManager.getActive(player);
            helper.assertTrue(weapon != null && weapon.getTarget() == null, "A target that leaves reach should be dropped");
            helper.assertTrue(SpectralWeaponManager.ownerHit(player, spider), "Free to lock on to the next thing hit");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void configLevelsMustRiseAboveOne(GameTestHelper helper) {
        IntPredicate any = value -> true;
        IntPredicate odd = value -> value % 2 == 1;
        helper.assertTrue(SpectralConfig.isValidLevels(List.of(6, 12, 16), 64, any), "Defaults are valid");
        helper.assertTrue(SpectralConfig.isValidLevels(List.of(2L, 3L, 4L), 64, any), "Longs from the TOML parser are fine");
        helper.assertFalse(SpectralConfig.isValidLevels(List.of(1, 12, 16), 64, any), "Each level must be greater than 1");
        helper.assertFalse(SpectralConfig.isValidLevels(List.of(6, 6, 16), 64, any), "Each level must exceed the one before");
        helper.assertFalse(SpectralConfig.isValidLevels(List.of(12, 6, 16), 64, any), "Levels must not decrease");
        helper.assertFalse(SpectralConfig.isValidLevels(List.of(6, 12), 64, any), "One value per level");
        helper.assertTrue(SpectralConfig.isValidLevels(List.of(3, 5, 9), 33, odd), "Odd areas are valid");
        helper.assertFalse(SpectralConfig.isValidLevels(List.of(3, 4, 7), 33, odd), "Areas must be odd");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sneakingClearsTheLock(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.getInventory().setItem(0, enchanted(helper, Items.IRON_SWORD, 3));
        player.getInventory().selected = 4;
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 3));
        helper.onEachTick(() -> SpectralWeaponManager.tick(player));

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(SpectralWeaponManager.ownerHit(player, husk), "Should lock on to the husk");
            player.setShiftKeyDown(true);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(SpectralWeaponManager.getActive(player) == null, "No spectral weapon while sneaking");
            player.setShiftKeyDown(false);
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(SpectralWeaponManager.getActive(player) != null, "The weapon returns after sneaking");
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "Sneaking should have cleared the lock");
            helper.assertTrue(SpectralWeaponManager.ownerHit(player, husk), "Free to lock on again");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void fakePlayersNeverSummonIt(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.getInventory().setItem(0, enchanted(helper, Items.IRON_SWORD, 3));
        player.getInventory().selected = 4;
        helper.onEachTick(() -> NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player)));

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(SpectralWeaponManager.getActive(player) == null, "Only real players get a spectral weapon");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void anvilPutsItOnAnyItem(GameTestHelper helper) {
        FakePlayer player = player(helper);
        Holder<Enchantment> holder = holder(helper);
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, 2));

        AnvilUpdateEvent dirt = new AnvilUpdateEvent(new ItemStack(Items.DIRT), book, "", 0, player);
        SpectralWeaponManager.onAnvilUpdate(dirt);
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(holder, dirt.getOutput()) == 2, "Dirt should take Spectral Weapon II");
        helper.assertTrue(dirt.getCost() > 0, "Should cost experience");

        AnvilUpdateEvent sword = new AnvilUpdateEvent(new ItemStack(Items.IRON_SWORD), book, "", 0, player);
        SpectralWeaponManager.onAnvilUpdate(sword);
        helper.assertTrue(sword.getOutput().isEmpty(), "Melee weapons are left to the vanilla anvil");
        helper.succeed();
    }

    /** Compat run only: Simply Swords' uniques never take the enchantment, from a book or the anvil. */
    @GameTest(template = "empty")
    public static void simplySwordsUniquesAreExcluded(GameTestHelper helper) {
        Item unique = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("simplyswords", "emberblade"));
        if (unique == Items.AIR) {
            helper.succeed();
            return;
        }
        Holder<Enchantment> holder = holder(helper);
        helper.assertFalse(new ItemStack(unique).supportsEnchantment(holder), "A Simply Swords unique must not support Spectral Weapon");
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, 1));
        AnvilUpdateEvent anvil = new AnvilUpdateEvent(new ItemStack(unique), book, "", 0, player(helper));
        SpectralWeaponManager.onAnvilUpdate(anvil);
        helper.assertTrue(anvil.getOutput().isEmpty(), "The anvil must not put Spectral Weapon on a unique");
        Item plain = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("simplyswords", "iron_longsword"));
        helper.assertTrue(plain == Items.AIR || new ItemStack(plain).supportsEnchantment(holder), "Ordinary Simply Swords weapons still take it");
        helper.succeed();
    }

    /** Compat run only: the Ars Nouveau enchanting apparatus recipes load. */
    @GameTest(template = "empty")
    public static void arsNouveauRecipesLoad(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ars_nouveau")) {
            helper.succeed();
            return;
        }
        for (String name : List.of("broad_breaker_1", "spectral_weapon_1")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(SpectralEnchantments.id("ars_nouveau/" + name)).isPresent(),
                    "Missing Ars Nouveau recipe " + name);
        }
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "spectral_weapon_test"));
        BlockPos stand = helper.absolutePos(PLAYER_POS);
        player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
        player.setOnGround(true);
        return player;
    }

    private static Holder<Enchantment> holder(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModEnchantments.SPECTRAL_WEAPON);
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(holder(helper), level);
        return stack;
    }
}
