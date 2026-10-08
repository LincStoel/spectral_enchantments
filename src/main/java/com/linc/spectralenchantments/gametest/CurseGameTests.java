package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.ModEffects;
import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.compat.ArsNouveauCompat;
import com.linc.spectralenchantments.curse.Curses;
import com.linc.spectralenchantments.curse.Possession;
import com.linc.spectralenchantments.curse.PossessedWeaponEntity;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for hidden curses, the Cursed effect and Curse of Possession. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CurseGameTests {
    private static final BlockPos PLAYER_POS = new BlockPos(4, 2, 4);
    /** Chest menu slot of hotbar slot 0 (three rows of chest, then the main inventory, then the hotbar). */
    private static final int HOTBAR_0 = 54;

    private CurseGameTests() {}

    @GameTest(template = "empty")
    public static void cursesAreRevealedInTheInventory(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack cursed = enchanted(helper, Items.IRON_SWORD, Enchantments.BINDING_CURSE);
        ItemStack plain = enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS);
        helper.assertFalse(Curses.isRevealed(cursed), "A new cursed item hides its curse");
        player.getInventory().setItem(0, plain);
        Curses.reveal(player);
        helper.assertFalse(Curses.isRevealed(plain), "An item without curses has nothing to reveal");
        helper.assertFalse(player.hasEffect(ModEffects.CURSED), "No curse, no Cursed");

        player.getInventory().setItem(1, cursed);
        Curses.reveal(player);
        helper.assertTrue(Curses.isRevealed(cursed), "In the inventory its curse is revealed");
        helper.assertTrue(player.hasEffect(ModEffects.CURSED), "Revealing a curse makes the player Cursed");
        helper.assertTrue(player.getEffect(ModEffects.CURSED).getDuration() == 60 * 20, "Cursed lasts a minute by default");

        player.removeAllEffects();
        Curses.reveal(player);
        helper.assertFalse(player.hasEffect(ModEffects.CURSED), "Only the first time: an item is revealed once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cursedCantBeCuredWithMilk(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.addEffect(new MobEffectInstance(ModEffects.CURSED, 200));
        player.removeEffectsCuredBy(EffectCures.MILK);
        helper.assertTrue(player.hasEffect(ModEffects.CURSED), "Milk doesn't lift the curse");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lockedItemsStayOutOfContainers(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack cursed = enchanted(helper, Items.IRON_SWORD, Enchantments.VANISHING_CURSE);
        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        player.getInventory().setItem(0, cursed);
        player.getInventory().setItem(1, plain);
        player.addEffect(new MobEffectInstance(ModEffects.CURSED, 200));
        SimpleContainer chest = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), chest);

        menu.clicked(HOTBAR_0, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(chest.isEmpty(), "Shift-click can't move a locked item into a chest");
        helper.assertTrue(player.getInventory().getItem(0) == cursed, "The locked item stays put");

        menu.clicked(HOTBAR_0, 0, ClickType.PICKUP, player);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(chest.isEmpty(), "Clicking can't place a locked item in a chest");
        menu.clicked(HOTBAR_0, 0, ClickType.PICKUP, player);
        helper.assertTrue(player.getInventory().getItem(0).is(Items.IRON_SWORD) && menu.getCarried().isEmpty(),
                "...but it can be put back in the inventory");

        menu.clicked(0, 0, ClickType.SWAP, player);
        helper.assertTrue(chest.isEmpty(), "A number-key swap can't put it in a chest");

        menu.clicked(HOTBAR_0, 0, ClickType.THROW, player);
        helper.assertTrue(!player.getInventory().getItem(0).isEmpty(), "It can't be thrown out of the menu");

        menu.clicked(HOTBAR_0 + 1, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(chest.getItem(0) == plain || chest.getItem(0).is(Items.IRON_SWORD), "Uncursed items move freely");

        player.removeAllEffects();
        menu.clicked(HOTBAR_0, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Once the effect is gone the cursed item moves too");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lockedItemsCantBeTossed(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.getInventory().setItem(0, enchanted(helper, Items.IRON_SWORD, Enchantments.BINDING_CURSE));
        player.getInventory().selected = 0;
        player.addEffect(new MobEffectInstance(ModEffects.CURSED, 200));
        ItemStack tossed = player.getInventory().removeFromSelected(true);
        ItemEntity dropped = CommonHooks.onPlayerTossEvent(player, tossed, false);
        helper.assertTrue(dropped == null, "The toss is refused");
        helper.assertTrue(player.getInventory().getItem(0).is(Items.IRON_SWORD), "The item goes back where it was");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void possessedWeaponTearsFree(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        FakePlayer player = player(helper);
        ItemStack sword = enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.POSSESSION_CURSE);
        sword.setDamageValue(sword.getMaxDamage() - 600);
        player.getInventory().setItem(0, sword);
        Curses.reveal(player);

        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "The possessed weapon leaves the inventory");
        helper.assertFalse(player.hasEffect(ModEffects.CURSED), "Its curse is spent on possessing, not on Cursed");
        List<PossessedWeaponEntity> found = possessed(helper);
        helper.assertTrue(found.size() == 1, "One possessed weapon appears");
        PossessedWeaponEntity weapon = found.getFirst();
        helper.assertTrue(Math.abs(weapon.getMaxHealth() - 30f) < 0.01f, "600 durability left is 30 health, not " + weapon.getMaxHealth());
        helper.assertTrue(weapon.getHealth() == weapon.getMaxHealth(), "It starts at full health");
        helper.assertTrue(weapon.getAttributeValue(Attributes.ARMOR) == 2.0, "Armored like a zombie");
        helper.assertTrue(weapon.getTarget() == player, "It goes for the player who freed it");
        helper.assertTrue(weapon.getWeapon() == sword, "It carries the real weapon");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void healthFollowsDurability(GameTestHelper helper) {
        ItemStack worn = new ItemStack(Items.IRON_SWORD);
        worn.setDamageValue(worn.getMaxDamage() - 10);
        helper.assertTrue(Possession.maxHealth(worn) == 6f, "Nearly broken weapons get the minimum health");
        ItemStack netherite = new ItemStack(Items.NETHERITE_SWORD);
        helper.assertTrue(Possession.maxHealth(netherite) == 40f, "Health is capped at a player's");
        helper.assertTrue(Possession.maxHealth(new ItemStack(Items.STONE)) == 6f, "Stone's health is its hardness, 1.5, times 4");
        helper.assertTrue(Possession.maxHealth(new ItemStack(Items.OBSIDIAN)) == 40f, "Hard blocks are capped too");
        helper.assertTrue(Possession.maxHealth(new ItemStack(Items.BEDROCK)) == 40f, "Unbreakable blocks get the cap");
        helper.assertTrue(Possession.maxHealth(new ItemStack(Items.STICK)) == 1f, "Items without durability have half a heart");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void possessedWeaponFights(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        FakePlayer player = player(helper);
        player.getInventory().setItem(0, enchanted(helper, Items.IRON_SWORD, ModEnchantments.POSSESSION_CURSE));
        Curses.reveal(player);
        PossessedWeaponEntity weapon = possessed(helper).getFirst();
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 7));
        weapon.setTarget(husk);
        helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "Husk not hit yet"));
    }

    @GameTest(template = "empty")
    public static void slainWeaponDropsFreedAndAnimated(GameTestHelper helper) {
        ItemStack sword = enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.POSSESSION_CURSE);
        sword.enchant(holder(helper, Enchantments.SHARPNESS), 3);
        ItemStack drop = Possession.slainDrop(helper.getLevel(), sword);
        helper.assertFalse(Curses.hasCurse(drop), "The drop's curses are lifted");
        helper.assertTrue(level(helper, drop, ModEnchantments.ANIMATED) == 1, "The drop is Animated");
        helper.assertTrue(level(helper, drop, Enchantments.SHARPNESS) == 3, "Its other enchantments stay");
        helper.assertTrue(drop.getMaxDamage() - drop.getDamageValue() == Math.round(drop.getMaxDamage() * 0.05),
                "It is left with 5% durability");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void dispelFreesTheWeaponUnharmed(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        FakePlayer player = player(helper);
        ItemStack sword = enchanted(helper, Items.IRON_SWORD, ModEnchantments.POSSESSION_CURSE);
        sword.setDamageValue(42);
        player.getInventory().setItem(0, sword);
        Curses.reveal(player);
        PossessedWeaponEntity weapon = possessed(helper).getFirst();

        helper.assertTrue(ArsNouveauCompat.onDispel(null, new EntityHitResult(weapon)), "Dispel takes hold of the weapon");
        helper.assertTrue(weapon.isRemoved(), "The possessed weapon is gone");
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, weapon.getBoundingBox().inflate(2));
        helper.assertTrue(items.size() == 1, "It drops the weapon");
        ItemStack freed = items.getFirst().getItem();
        helper.assertTrue(freed.is(Items.IRON_SWORD) && freed.getDamageValue() == 42, "As worn as it was");
        helper.assertFalse(Curses.hasCurse(freed), "Its curse is lifted");
        helper.assertTrue(level(helper, freed, ModEnchantments.ANIMATED) == 1, "And it is Animated");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void possessionIsATreasureCurse(GameTestHelper helper) {
        Holder<Enchantment> possession = holder(helper, ModEnchantments.POSSESSION_CURSE);
        helper.assertTrue(possession.is(EnchantmentTags.CURSE), "Curse of Possession is a curse");
        helper.assertTrue(possession.is(EnchantmentTags.TREASURE), "It is treasure by default");
        helper.assertFalse(possession.is(EnchantmentTags.NON_TREASURE), "It doesn't roll at the enchanting table");
        helper.succeed();
    }

    /** Compat run only: the Ars Nouveau enchanting apparatus recipe loads. */
    @GameTest(template = "empty")
    public static void possessionArsNouveauRecipeLoads(GameTestHelper helper) {
        if (ModList.get().isLoaded("ars_nouveau")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(SpectralEnchantments.id("ars_nouveau/possession_curse_1")).isPresent(),
                    "Missing Ars Nouveau recipe possession_curse_1");
        }
        helper.succeed();
    }

    private static List<PossessedWeaponEntity> possessed(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(PossessedWeaponEntity.class, new AABB(helper.absolutePos(PLAYER_POS)).inflate(6));
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "curse_test"));
        BlockPos pos = helper.absolutePos(PLAYER_POS);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        player.setOnGround(true);
        return player;
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static int level(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key) {
        return EnchantmentHelper.getItemEnchantmentLevel(holder(helper, key), stack);
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(holder(helper, key), 1);
        return stack;
    }
}
