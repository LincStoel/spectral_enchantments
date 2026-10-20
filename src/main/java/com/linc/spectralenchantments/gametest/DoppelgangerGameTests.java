package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.doppelganger.Doppelganger;
import com.linc.spectralenchantments.doppelganger.DoppelgangerEntity;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for Doppelganger. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DoppelgangerGameTests {
    private static final BlockPos PLAYER_POS = new BlockPos(1, 2, 1);

    private DoppelgangerGameTests() {}

    @GameTest(template = "empty")
    public static void sneakingLeavesADoubleUntilStandingUp(GameTestHelper helper) {
        FakePlayer player = player(helper, enchanted(helper, Items.IRON_CHESTPLATE));
        player.setShiftKeyDown(true);
        Doppelganger.update(player);
        DoppelgangerEntity ghost = Doppelganger.ghostOf(player);
        helper.assertTrue(ghost != null && ghost.isAlive(), "Sneaking leaves a double behind");
        helper.assertTrue(ghost.position().distanceTo(player.position()) < 0.01, "The double appears where the player is");
        Doppelganger.update(player);
        helper.assertTrue(doubles(helper) == 1, "Staying crouched doesn't summon more doubles");
        player.setShiftKeyDown(false);
        Doppelganger.update(player);
        helper.assertTrue(ghost.isRemoved() && Doppelganger.ghostOf(player) == null, "Standing up takes the double away");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nothingWithoutTheEnchantment(GameTestHelper helper) {
        FakePlayer player = player(helper, new ItemStack(Items.IRON_CHESTPLATE));
        player.setShiftKeyDown(true);
        Doppelganger.update(player);
        helper.assertTrue(Doppelganger.ghostOf(player) == null, "A plain chestplate leaves no double");
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, Items.IRON_CHESTPLATE));
        player.setShiftKeyDown(false);
        Doppelganger.update(player);
        player.setShiftKeyDown(true);
        Doppelganger.update(player);
        helper.assertTrue(Doppelganger.ghostOf(player) == null, "An enchanted chestplate only works when worn");
        helper.assertTrue(doubles(helper) == 0, "No doubles were left behind");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void monstersAfterTheOwnerTurnOnTheDouble(GameTestHelper helper) {
        FakePlayer player = player(helper, enchanted(helper, Items.IRON_CHESTPLATE));
        Husk near = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 1));
        near.setTarget(player);
        Husk far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 1));
        far.moveTo(player.getX() + SpectralConfig.doppelgangerLureRadius() + 4, player.getY(), player.getZ());
        far.setTarget(player);
        Husk other = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));

        player.setShiftKeyDown(true);
        Doppelganger.update(player);
        DoppelgangerEntity ghost = Doppelganger.ghostOf(player);
        helper.assertTrue(ghost != null, "Sneaking leaves a double behind");
        helper.assertTrue(near.getTarget() == ghost, "A nearby monster chasing the owner turns on the double");
        helper.assertTrue(far.getTarget() == player, "A monster beyond the lure radius keeps chasing the owner");

        other.setTarget(player);
        helper.assertTrue(other.getTarget() == ghost, "A monster that picks the owner later goes for the double");
        helper.assertTrue(other.canAttack(ghost), "Monsters will attack the double");

        player.setShiftKeyDown(false);
        Doppelganger.update(player);
        other.setTarget(player);
        helper.assertTrue(other.getTarget() == player, "Once the double is gone the owner is fair game again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aMonsterTheOwnerHurtsTurnsOnTheOwner(GameTestHelper helper) {
        FakePlayer player = player(helper, enchanted(helper, Items.IRON_CHESTPLATE));
        Husk hit = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 1));
        hit.setTarget(player);
        Husk untouched = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));
        untouched.setTarget(player);
        Husk idle = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(1, 2, 4));

        player.setShiftKeyDown(true);
        Doppelganger.update(player);
        DoppelgangerEntity ghost = Doppelganger.ghostOf(player);
        helper.assertTrue(ghost != null && hit.getTarget() == ghost && untouched.getTarget() == ghost,
                "Both monsters turn on the double");

        hit.hurt(helper.getLevel().damageSources().playerAttack(player), 1f);
        helper.assertTrue(hit.getTarget() == player, "The monster the owner hurts turns on them");
        helper.assertTrue(untouched.getTarget() == ghost, "The others keep after the double");
        hit.setTarget(player);
        helper.assertTrue(hit.getTarget() == player, "The double doesn't win the hurt monster back");

        idle.hurt(helper.getLevel().damageSources().playerAttack(player), 1f);
        idle.setTarget(player);
        helper.assertTrue(idle.getTarget() == ghost, "A monster that wasn't after the double is still fooled by it");
        helper.assertTrue(Doppelganger.isImmune(hit) && !Doppelganger.isImmune(idle), "Only the monster after the double is immune");

        FakePlayer other = player(helper, enchanted(helper, Items.IRON_CHESTPLATE));
        hit.setTarget(other);
        other.setShiftKeyDown(true);
        Doppelganger.update(other);
        helper.assertTrue(Doppelganger.ghostOf(other) != null && hit.getTarget() == other,
                "A later double doesn't draw the immune monster off its target");
        hit.setTarget(other);
        helper.assertTrue(hit.getTarget() == other, "Nor does it catch the immune monster picking a target");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theDoubleCantBeHurt(GameTestHelper helper) {
        FakePlayer player = player(helper, enchanted(helper, Items.IRON_CHESTPLATE));
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 1));
        player.setShiftKeyDown(true);
        Doppelganger.update(player);
        DoppelgangerEntity ghost = Doppelganger.ghostOf(player);
        helper.assertTrue(ghost != null, "Sneaking leaves a double behind");
        helper.assertFalse(ghost.hurt(helper.getLevel().damageSources().mobAttack(husk), 100f), "Monsters can't hurt the double");
        helper.assertFalse(ghost.hurt(helper.getLevel().damageSources().playerAttack(player), 100f), "Players can't hurt the double");
        helper.assertTrue(ghost.isAlive(), "The double survives");
        helper.assertTrue(ghost.canBeSeenAsEnemy(), "Monsters still see the double as something to attack");
        helper.assertFalse(ghost.isPickable(), "Players' swings pass through the double");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exclusiveWithProtectionOnly(GameTestHelper helper) {
        Holder<Enchantment> doppelganger = holder(helper, ModEnchantments.DOPPELGANGER);
        helper.assertFalse(Enchantment.areCompatible(doppelganger, holder(helper, Enchantments.PROTECTION)),
                "Doppelganger can't share a chestplate with Protection");
        for (ResourceKey<Enchantment> other : List.of(Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION,
                Enchantments.PROJECTILE_PROTECTION, Enchantments.THORNS, Enchantments.UNBREAKING)) {
            helper.assertTrue(Enchantment.areCompatible(doppelganger, holder(helper, other)),
                    "Doppelganger goes with " + other.location());
        }
        helper.succeed();
    }

    private static int doubles(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(32);
        return helper.getLevel().getEntitiesOfClass(DoppelgangerEntity.class, area).size();
    }

    private static FakePlayer player(GameTestHelper helper, ItemStack chest) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "doppelganger_test"));
        BlockPos pos = helper.absolutePos(PLAYER_POS);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        player.setOnGround(true);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        return player;
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(holder(helper, ModEnchantments.DOPPELGANGER), 1);
        return stack;
    }
}
