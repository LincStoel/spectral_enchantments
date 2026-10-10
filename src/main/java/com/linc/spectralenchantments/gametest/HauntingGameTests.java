package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.ModEffects;
import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.curse.Curses;
import com.linc.spectralenchantments.curse.Haunting;
import com.linc.spectralenchantments.curse.HauntingGhostEntity;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side checks for the Curse of Haunting and armor curses staying hidden until worn. Run with
 * {@code ./gradlew runGameTestServer}. In batches of their own, so no other test's monsters draw the ghost off.
 */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HauntingGameTests {
    private static final BlockPos PLAYER_POS = new BlockPos(24, 2, 24);
    /** Long enough for any wait before the ghost appears. */
    private static final int FOREVER = Integer.MAX_VALUE / 2;

    private HauntingGameTests() {}

    @GameTest(template = "haunting_arena", batch = "haunting")
    public static void armorCursesShowOnlyOnceWorn(GameTestHelper helper) {
        FakePlayer player = player(helper, ItemStack.EMPTY);
        ItemStack helmet = enchanted(helper, Items.IRON_HELMET, Enchantments.BINDING_CURSE);
        ItemStack sword = enchanted(helper, Items.IRON_SWORD, Enchantments.VANISHING_CURSE);
        player.getInventory().setItem(0, helmet);
        Curses.reveal(player);
        helper.assertFalse(Curses.isRevealed(helmet), "Cursed armor carried in the inventory keeps its curse hidden");
        helper.assertFalse(player.hasEffect(ModEffects.CURSED), "Carrying it doesn't make the player Cursed");

        player.getInventory().setItem(1, sword);
        Curses.reveal(player);
        helper.assertTrue(Curses.isRevealed(sword), "Other cursed items are still revealed in the inventory");
        player.removeAllEffects();

        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        Curses.reveal(player);
        helper.assertTrue(Curses.isRevealed(helmet), "Putting it on reveals the curse");
        helper.assertTrue(player.hasEffect(ModEffects.CURSED), "...and makes the player Cursed");
        helper.succeed();
    }

    @GameTest(template = "haunting_arena", batch = "haunting")
    public static void theGhostAppearsAndStares(GameTestHelper helper) {
        FakePlayer player = player(helper, haunted(helper, Items.IRON_BOOTS));
        Haunting.update(player, 0);
        HauntingGhostEntity ghost = Haunting.ghostOf(player);
        helper.assertTrue(ghost != null, "Putting on haunted armor brings the ghost out at once");
        double distance = ghost.distanceTo(player);
        helper.assertTrue(distance >= SpectralConfig.hauntingStareMinDistance() - 0.01
                && distance <= SpectralConfig.hauntingStareMaxDistance() + 0.01, "It appears a way off: " + distance);
        helper.assertTrue(player.getUUID().equals(ghost.ownerId()), "It's the player's own ghost");
        helper.assertFalse(ghost.canBeSeenAsEnemy(), "Monsters pay it no mind");

        helper.runAfterDelay(2, () -> {
            float yaw = (float) Math.toDegrees(Math.atan2(player.getZ() - ghost.getZ(), player.getX() - ghost.getX())) - 90f;
            helper.assertTrue(Math.abs(Mth.wrapDegrees(ghost.getYRot() - yaw)) < 1f, "It stares at the player");
            helper.assertTrue(ghost.isAlive(), "It stays to stare");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            Haunting.update(player, 0);
            helper.assertTrue(ghost.isRemoved() && Haunting.timer(player) == -1, "Taking the armor off ends the haunting");
            helper.succeed();
        });
    }

    @GameTest(template = "haunting_arena", batch = "haunting")
    public static void itAppearsAtOnceEvenWhenWalledIn(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(PLAYER_POS.offset(-1, -1, -1), PLAYER_POS.offset(1, 2, 1))) {
            if (pos.getX() != PLAYER_POS.getX() || pos.getZ() != PLAYER_POS.getZ() || pos.getY() < PLAYER_POS.getY()
                    || pos.getY() > PLAYER_POS.getY() + 1) {
                helper.setBlock(pos, Blocks.STONE);
            }
        }
        FakePlayer player = player(helper, haunted(helper, Items.IRON_HELMET));
        Haunting.update(player, 0);
        helper.assertTrue(Haunting.ghostOf(player) != null, "With nowhere it could stare from, it still comes out at once");
        helper.succeed();
    }

    @GameTest(template = "haunting_arena", batch = "haunting")
    public static void approachingMakesItFlee(GameTestHelper helper) {
        FakePlayer player = player(helper, haunted(helper, Items.IRON_CHESTPLATE));
        Haunting.update(player, FOREVER);
        HauntingGhostEntity ghost = Haunting.ghostOf(player);
        helper.assertTrue(ghost != null, "The ghost appears");
        player.moveTo(ghost.getX() + 2, ghost.getY(), ghost.getZ());
        double start = ghost.distanceTo(player);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(ghost.isFleeing(), "Coming close sends it fleeing");
            helper.assertTrue(ghost.distanceTo(player) > start, "It runs away from the player");
        });
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(ghost.isRemoved(), "After a second it's gone");
            Haunting.update(player, 0);
            helper.assertTrue(Haunting.ghostOf(player) == null, "It doesn't come straight back");
            helper.assertTrue(Haunting.timer(player) >= SpectralConfig.hauntingAppearMinTicks(), "It comes back after the usual wait");
            helper.succeed();
        });
    }

    @GameTest(template = "haunting_arena", batch = "haunting")
    public static void strikingItBanishesItForAWhile(GameTestHelper helper) {
        FakePlayer player = player(helper, haunted(helper, Items.IRON_LEGGINGS));
        Haunting.update(player, FOREVER);
        HauntingGhostEntity ghost = Haunting.ghostOf(player);
        helper.assertTrue(ghost != null, "The ghost appears");
        helper.assertTrue(ghost.isPickable() && ghost.isAttackable(), "Players can swing at it");
        helper.assertFalse(ghost.hurt(helper.getLevel().damageSources().playerAttack(player), 1f), "It takes no damage");
        helper.assertTrue(ghost.isRemoved(), "A player's blow makes it vanish");
        Haunting.update(player, 0);
        int timer = Haunting.timer(player);
        helper.assertTrue(timer >= SpectralConfig.hauntingBanishMinTicks() && timer <= SpectralConfig.hauntingBanishMaxTicks(),
                "It comes back 10 to 20 seconds later: " + timer);
        Haunting.update(player, timer);
        helper.assertTrue(Haunting.ghostOf(player) != null, "...and then it's back");
        helper.succeed();
    }

    @GameTest(template = "haunting_arena", batch = "haunting")
    public static void itRaisesItsArmsLikeAnEvoker(GameTestHelper helper) {
        FakePlayer player = player(helper, haunted(helper, Items.IRON_HELMET));
        Haunting.update(player, FOREVER);
        HauntingGhostEntity ghost = Haunting.ghostOf(player);
        helper.assertTrue(ghost != null, "The ghost appears");
        helper.assertFalse(ghost.isCasting(), "It doesn't start out casting");
        ghost.startCasting();
        helper.assertTrue(ghost.isCasting(), "It raises its arms");
        helper.runAfterDelay(HauntingGhostEntity.CAST_TICKS + 2, () -> {
            helper.assertFalse(ghost.isCasting(), "...and lowers them again after a couple of seconds");
            helper.assertTrue(ghost.isAlive(), "...still staring");
            helper.succeed();
        });
    }

    @GameTest(template = "haunting_arena", batch = "haunting")
    public static void nothingInCreativeOrWithoutTheCurse(GameTestHelper helper) {
        FakePlayer plain = player(helper, new ItemStack(Items.IRON_HELMET));
        Haunting.update(plain, FOREVER);
        helper.assertTrue(Haunting.ghostOf(plain) == null && Haunting.timer(plain) == -1, "Plain armor isn't haunted");

        FakePlayer held = player(helper, ItemStack.EMPTY);
        held.setItemSlot(EquipmentSlot.MAINHAND, haunted(helper, Items.IRON_HELMET));
        Haunting.update(held, FOREVER);
        helper.assertTrue(Haunting.ghostOf(held) == null, "Haunted armor only haunts when worn");

        FakePlayer creative = player(helper, haunted(helper, Items.IRON_HELMET));
        creative.setGameMode(GameType.CREATIVE);
        Haunting.update(creative, FOREVER);
        helper.assertTrue(Haunting.ghostOf(creative) == null, "Creative players aren't haunted");
        helper.assertTrue(holder(helper, ModEnchantments.HAUNTING_CURSE).is(EnchantmentTags.CURSE), "It's a curse");
        helper.succeed();
    }

    @GameTest(template = "haunting_arena", batch = "haunting_monsters", timeoutTicks = 200)
    public static void itStrengthensMonstersNearby(GameTestHelper helper) {
        FakePlayer player = player(helper, haunted(helper, Items.IRON_HELMET));
        Haunting.update(player, FOREVER);
        HauntingGhostEntity ghost = Haunting.ghostOf(player);
        helper.assertTrue(ghost != null, "The ghost appears");
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(28, 2, 24));
        helper.succeedWhen(() -> {
            helper.assertTrue(ghost.focus() == husk, "With a monster about, the ghost goes to it");
            helper.assertTrue(ghost.distanceTo(husk) <= SpectralConfig.hauntingStrengthRadius(), "It floats near the monster");
            helper.assertTrue(husk.hasEffect(MobEffects.DAMAGE_BOOST), "The monster is strengthened");
            helper.assertFalse(ghost.isFleeing(), "Being close to the player while with monsters doesn't scare it off");
        });
    }

    @GameTest(template = "haunting_arena", batch = "haunting_monsters")
    public static void aMobsBlowBringsTheGhostAtOnce(GameTestHelper helper) {
        FakePlayer player = player(helper, haunted(helper, Items.IRON_CHESTPLATE));
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(24, 2, 29));
        Haunting.update(player, 0);
        HauntingGhostEntity first = Haunting.ghostOf(player);
        helper.assertTrue(first != null, "The ghost comes out as the armor goes on");
        first.vanish(-1);
        Haunting.update(player, 0);
        helper.assertTrue(Haunting.ghostOf(player) == null, "Once it has gone, no ghost until the next one is due");
        HauntingGhostEntity ghost = Haunting.provoke(player, husk);
        helper.assertTrue(ghost != null && Haunting.ghostOf(player) == ghost, "A mob's blow brings the ghost out at once");
        helper.assertTrue(ghost.distanceTo(husk) < 3, "It appears by the mob that struck");
        helper.assertTrue(Haunting.provoke(player, husk) == null, "Only one ghost at a time");

        helper.runAfterDelay(25, () -> {
            helper.assertTrue(ghost.isAlive() && !ghost.isFleeing(), "Close to the player but with a monster, it stays");
            helper.assertTrue(ghost.focus() == husk, "It goes to the monster");
            ghost.hurt(helper.getLevel().damageSources().playerAttack(player), 1f);
            Haunting.update(player, 0);
            helper.assertTrue(Haunting.provoke(player, husk) == null, "A banished ghost doesn't come back early for a blow");
            helper.succeed();
        });
    }

    @GameTest(template = "haunting_arena", batch = "haunting_monsters", timeoutTicks = 200)
    public static void aMonstersGhostStaysByIt(GameTestHelper helper) {
        helper.assertTrue(holder(helper, ModEnchantments.HAUNTING_CURSE).is(EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT),
                "Monsters can spawn wearing the curse");
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(24, 2, 24));
        Husk friend = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(27, 2, 24));
        Husk far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(24, 2, 40));
        husk.setItemSlot(EquipmentSlot.HEAD, haunted(helper, Items.IRON_HELMET));
        helper.succeedWhen(() -> {
            HauntingGhostEntity ghost = Haunting.ghostOf(husk);
            helper.assertTrue(ghost != null, "Putting the armor on brings out the monster's ghost");
            helper.assertTrue(Math.abs(ghost.getBbHeight() - 1.8f) < 0.01, "It's a player-sized ghost (drawn as Steve)");
            helper.assertTrue(ghost.distanceTo(husk) < 3, "It stays by the monster");
            helper.assertFalse(ghost.canBeSeenAsEnemy(), "Monsters treat it as one of their own");
            helper.assertTrue(husk.hasEffect(MobEffects.DAMAGE_BOOST), "Its monster is strengthened");
            helper.assertTrue(friend.hasEffect(MobEffects.DAMAGE_BOOST), "...and so are monsters near it");
            helper.assertFalse(far.hasEffect(MobEffects.DAMAGE_BOOST), "...but not those far off");

            FakePlayer player = player(helper, ItemStack.EMPTY);
            ghost.hurt(helper.getLevel().damageSources().playerAttack(player), 1f);
            helper.assertTrue(ghost.isAlive() && ghost.isHidden() && !ghost.isPickable(), "Struck, it fades out but stays");
            husk.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        });
    }

    @GameTest(template = "haunting_arena", batch = "haunting_monsters", timeoutTicks = 200)
    public static void aMonstersGhostLeavesWithTheArmor(GameTestHelper helper) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(24, 2, 24));
        husk.setItemSlot(EquipmentSlot.FEET, haunted(helper, Items.IRON_BOOTS));
        helper.runAfterDelay(5, () -> {
            HauntingGhostEntity ghost = Haunting.ghostOf(husk);
            helper.assertTrue(ghost != null, "The monster's ghost is out");
            husk.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(ghost.isRemoved() && Haunting.ghostOf(husk) == null, "Without the armor the ghost goes");
                helper.succeed();
            });
        });
    }

    private static FakePlayer player(GameTestHelper helper, ItemStack armor) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "haunting_test"));
        BlockPos pos = helper.absolutePos(PLAYER_POS);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        player.setOnGround(true);
        player.setGameMode(GameType.SURVIVAL);
        if (!armor.isEmpty()) {
            player.setItemSlot(player.getEquipmentSlotForItem(armor), armor);
        }
        return player;
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack haunted(GameTestHelper helper, Item item) {
        return enchanted(helper, item, ModEnchantments.HAUNTING_CURSE);
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> enchantment) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(holder(helper, enchantment), 1);
        return stack;
    }
}
