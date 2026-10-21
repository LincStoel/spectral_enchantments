package com.linc.spectralenchantments.gametest;

import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.boots.HighJump;
import com.linc.spectralenchantments.boots.JumpPhysics;
import com.linc.spectralenchantments.boots.Springy;
import com.linc.spectralenchantments.network.HighJumpPayload;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side checks for the boot enchantments, High Jump and Springy (their motion is the client's, so only the
 * rules and arithmetic are checked here). Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(SpectralEnchantments.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootsGameTests {
    private static final BlockPos PLAYER_POS = new BlockPos(1, 2, 1);
    private static final double CLOSE = 0.05;

    private BootsGameTests() {}

    @GameTest(template = "empty")
    public static void springyAndFeatherFallingDontMix(GameTestHelper helper) {
        helper.assertFalse(Enchantment.areCompatible(holder(helper, ModEnchantments.SPRINGY), holder(helper, Enchantments.FEATHER_FALLING)),
                "Springy and Feather Falling are incompatible");
        helper.assertTrue(Enchantment.areCompatible(holder(helper, ModEnchantments.SPRINGY), holder(helper, ModEnchantments.HIGH_JUMP)),
                "Springy and High Jump go together");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void springyBouncesOnlyLongUnsneakingFalls(GameTestHelper helper) {
        FakePlayer player = player(helper, boots(helper, ModEnchantments.SPRINGY, 1));
        helper.assertTrue(Springy.bounces(player, 10f, 1f), "A long fall bounces");
        helper.assertFalse(Springy.bounces(player, 3f, 1f), "A fall of 3 blocks doesn't");
        helper.assertFalse(Springy.bounces(player, 10f, 0f), "Nor does landing where falls do no damage (a slime block)");
        player.setShiftKeyDown(true);
        helper.assertFalse(Springy.bounces(player, 10f, 1f), "Sneaking lands normally");
        player.setShiftKeyDown(false);
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        helper.assertFalse(Springy.bounces(player, 10f, 1f), "Plain boots don't bounce");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void springyCancelsTheFallDamage(GameTestHelper helper) {
        FakePlayer player = player(helper, boots(helper, ModEnchantments.SPRINGY, 1));
        helper.assertTrue(NeoForge.EVENT_BUS.post(new LivingFallEvent(player, 10f, 1f)).isCanceled(), "A bounce takes no damage");
        player.setPos(player.getX(), player.getY() + 1, player.getZ());
        helper.setBlock(PLAYER_POS, Blocks.SLIME_BLOCK);
        helper.assertFalse(Springy.bounces(player, 10f, 1f), "A slime block bounces on its own, even when the multiplier is lost (creative)");
        player.setPos(player.getX(), player.getY() - 1, player.getZ());
        player.setShiftKeyDown(true);
        helper.assertFalse(NeoForge.EVENT_BUS.post(new LivingFallEvent(player, 10f, 1f)).isCanceled(), "A sneaking landing does");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void springyBounceReturnsMostAndTipsForward(GameTestHelper helper) {
        Vec3 straight = Springy.bounce(new Vec3(0, -1.2, 0), 10, 0.75);
        helper.assertTrue(Math.abs(straight.x) < 1e-9 && Math.abs(straight.z) < 1e-9, "Straight down bounces straight up");
        double height = JumpPhysics.heightFor(straight.y);
        helper.assertTrue(Math.abs(height - 7.5) < CLOSE, "A 10 block fall bounces 7.5 blocks, got " + height);
        double timed = JumpPhysics.heightFor(Springy.bounce(new Vec3(0, -1.2, 0), 10, 0.95).y);
        helper.assertTrue(Math.abs(timed - 9.5) < CLOSE, "or 9.5 with a timed jump, got " + timed);
        Vec3 angled = Springy.bounce(new Vec3(0.6, -1.2, 0), 10, 0.75);
        helper.assertTrue(angled.x > 0 && Math.abs(angled.z) < 1e-9, "An angled landing carries on the way it was going");
        helper.assertTrue(angled.y < straight.y, "and rises less");
        helper.assertTrue(Math.abs(angled.length() - straight.length()) < 1e-6, "for the same bounce");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void highJumpChargesLikeAHorse(GameTestHelper helper) {
        helper.assertTrue(Math.abs(JumpPhysics.VANILLA_JUMP_HEIGHT - 1.25) < CLOSE, "A plain jump rises about 1.25 blocks");
        helper.assertTrue(HighJump.power(HighJump.chargeScale(10)) == 1f, "Half a second charges a full leap");
        helper.assertTrue(HighJump.power(HighJump.chargeScale(60)) < 1f, "but sags if held too long");
        helper.assertTrue(HighJump.power(HighJump.chargeScale(1000)) > 0.7f, "though never far");
        helper.assertTrue(HighJump.power(0f) == 0.4f, "An empty charge still leaps a little");
        for (int level = 1; level <= 3; level++) {
            double height = JumpPhysics.heightFor(JumpPhysics.speedFor(HighJump.height(level, 1f)));
            helper.assertTrue(Math.abs(height - (level + 1)) < CLOSE, "A full leap at level " + level + " rises " + (level + 1) + ", got " + height);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void highJumpForgivesTheLeapOnLanding(GameTestHelper helper) {
        FakePlayer player = player(helper, boots(helper, ModEnchantments.HIGH_JUMP, 3));
        HighJump.handle(player, new HighJumpPayload(1f));
        LivingFallEvent landing = NeoForge.EVENT_BUS.post(new LivingFallEvent(player, 4f, 1f));
        helper.assertTrue(Math.abs(landing.getDistance() - JumpPhysics.VANILLA_JUMP_HEIGHT) < CLOSE,
                "A full leap lands as a plain jump would, got " + landing.getDistance());
        LivingFallEvent next = NeoForge.EVENT_BUS.post(new LivingFallEvent(player, 4f, 1f));
        helper.assertTrue(next.getDistance() == 4f, "Only the leap's own landing is forgiven");
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper, ItemStack feet) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "boots_test"));
        BlockPos pos = helper.absolutePos(PLAYER_POS);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        player.setOnGround(true);
        player.setShiftKeyDown(false);
        player.setItemSlot(EquipmentSlot.FEET, feet);
        return player;
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack boots(GameTestHelper helper, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(Items.IRON_BOOTS);
        stack.enchant(holder(helper, key), level);
        return stack;
    }
}
