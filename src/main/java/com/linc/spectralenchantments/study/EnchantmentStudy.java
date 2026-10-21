package com.linc.spectralenchantments.study;

import com.linc.spectralenchantments.ModAttachments;
import com.linc.spectralenchantments.ModEnchantments;
import com.linc.spectralenchantments.SpectralConfig;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.curse.Curses;
import com.linc.spectralenchantments.network.StudyPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Studying and copying enchantments at a lectern (server side).
 *
 * <ul>
 *   <li>Studying: an enchanted item used with a {@link #CATALYST} (amethyst) in the off hand. Item and amethyst are
 *       destroyed.</li>
 *   <li>Copying: an enchanted book used. It is kept.</li>
 * </ul>
 *
 * <p>Either is done at a lectern holding a blank book and quill, which becomes an enchanted book of one of the
 * enchantments, at level I, and floats up off the lectern ({@link #FLOAT_TICKS}) before dropping.
 *
 * <p>Use must be held throughout. Vanilla repeats a held use on a block every 4 ticks, and each repeat keeps the
 * session alive; one that goes {@link #HOLD_TIMEOUT_TICKS} without one was let go of. Only the item's own
 * enchantments count (its {@code enchantments} component), never ones other mods add as it is used, such as Quark's
 * Fortune on gold tools. Enchantments switched off, or in {@link #UNSTUDYABLE} / {@link #UNCOPYABLE}, are left out.
 * Which enchantment comes out, and whether a curse comes out instead, follows the player's {@link
 * ModAttachments#STUDY_SEED}, so letting go and trying again gives the same result.
 */
public final class EnchantmentStudy {
    /** What studying is rubbed with: amethyst shards. */
    public static final TagKey<Item> CATALYST = TagKey.create(Registries.ITEM, SpectralEnchantments.id("study/catalyst"));
    public static final TagKey<Enchantment> UNSTUDYABLE = TagKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("study/unstudyable"));
    /** Treasure enchantments (Mending and the like) by default. */
    public static final TagKey<Enchantment> UNCOPYABLE = TagKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("study/uncopyable"));
    /** The curses a failed study or copy can write: every curse by default. */
    public static final TagKey<Enchantment> CURSES = TagKey.create(Registries.ENCHANTMENT, SpectralEnchantments.id("study/curses"));
    static final int HOLD_TIMEOUT_TICKS = 10;
    /** How long a written book floats up off the lectern before it drops, in ticks. */
    static final int FLOAT_TICKS = 40;
    /** Starting rise of a written book, slowed by item drag: about 2.5 blocks over {@link #FLOAT_TICKS}. */
    private static final double FLOAT_SPEED = 0.09;
    private static final Vector3f PURPLE = new Vector3f(0.69f, 0.3f, 1f);

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /**
     * Players who just finished at a lectern and may still be holding use on it: their repeats are swallowed until
     * they let go, so the lectern doesn't open (or start another study) under them.
     */
    private static final Map<UUID, Spent> SPENT = new HashMap<>();
    /** Written books floating up off their lecterns. */
    private static final Map<ItemEntity, Floating> FLOATING = new HashMap<>();
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0f, 0.02f), 1.3f);
    /** Dark red falling dust (nether wart's map colour). */
    private static final BlockParticleOption BLOOD_DRIP = new BlockParticleOption(ParticleTypes.FALLING_DUST,
            Blocks.NETHER_WART_BLOCK.defaultBlockState());

    private EnchantmentStudy() {}

    /** What the enchantment rolled for a study or copy is, and the curse written instead if it fails. */
    public record Roll(Holder<Enchantment> target, Optional<Holder<Enchantment>> curse) {
        public Holder<Enchantment> written() {
            return curse.orElse(target);
        }
    }

    private static final class Session {
        final ServerPlayer player;
        final ServerLevel level;
        final StudyMode mode;
        final BlockPos lectern;
        /** The main hand and the lectern's book as they were at the start; either changing stops the session. */
        final ItemStack held;
        final ItemStack book;
        final Roll roll;
        final int xpCost;
        final int startTick;
        final int duration;
        int lastHeld;

        Session(ServerPlayer player, StudyMode mode, BlockPos lectern, ItemStack held, ItemStack book, Roll roll, int xpCost,
                int now, int duration) {
            this.player = player;
            this.level = player.serverLevel();
            this.mode = mode;
            this.lectern = lectern.immutable();
            this.held = held.copy();
            this.book = book.copy();
            this.roll = roll;
            this.xpCost = xpCost;
            this.startTick = now;
            this.duration = duration;
            this.lastHeld = now;
        }
    }

    private record Spent(BlockPos lectern, int lastHeld) {}

    /** A written book floating up until the server tick {@code dropTick}; a cursed one rains blood as it goes. */
    private record Floating(int dropTick, boolean cursed) {}

    /** What the player's hands would do on a lectern with a book on it, or null. Used on both sides. */
    @Nullable
    public static StudyMode attempted(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(Items.ENCHANTED_BOOK)) {
            return StudyMode.COPYING;
        }
        if (!main.isEmpty() && player.getOffhandItem().is(CATALYST)) {
            return StudyMode.STUDYING;
        }
        return null;
    }

    /** A lectern that holds a book, which is what either workflow needs. */
    public static boolean isLecternWithBook(BlockState state) {
        return state.getBlock() instanceof LecternBlock && state.getValue(LecternBlock.HAS_BOOK);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = event.getPos();
        UUID id = player.getUUID();
        int now = player.server.getTickCount();

        Session session = SESSIONS.get(id);
        if (session != null) {
            if (session.lectern.equals(pos)) {
                session.lastHeld = now;
                claim(event);
                return;
            }
            SESSIONS.remove(id);
            send(session, StudyPayload.Kind.STOP, Optional.empty());
        }
        Spent spent = SPENT.remove(id);
        if (spent != null && spent.lectern.equals(pos) && now - spent.lastHeld <= HOLD_TIMEOUT_TICKS) {
            SPENT.put(id, new Spent(spent.lectern, now));
            claim(event);
            return;
        }

        Level level = event.getLevel();
        StudyMode mode = attempted(player);
        if (mode == null || player.isSpectator() || !isLecternWithBook(level.getBlockState(pos))
                || !level.mayInteract(player, pos) || !(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) {
            return;
        }
        ItemStack book = lectern.getBook();
        ItemStack held = player.getMainHandItem();
        if (!(mode == StudyMode.STUDYING ? SpectralConfig.studyingEnabled() : SpectralConfig.copyingEnabled())
                || !book.is(Items.WRITABLE_BOOK)) {
            return;
        }
        claim(event);
        if (!isBlank(book)) {
            refuse(player, Component.translatable("message.spectral_enchantments." + mode.getSerializedName() + ".not_blank"));
            return;
        }
        ItemEnchantments enchantments;
        if (mode == StudyMode.STUDYING) {
            if (Curses.isLocked(player, held)) {
                Curses.refuse(player);
                return;
            }
            // The component itself, not getAllEnchantments: what other mods add at use time (Quark's gold-tool
            // Fortune) isn't on the item to study.
            enchantments = held.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        } else {
            enchantments = held.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        }
        if (enchantments.isEmpty()) {
            refuse(player, Component.translatable("message.spectral_enchantments." + mode.getSerializedName() + ".nothing"));
            return;
        }

        List<Holder<Enchantment>> allowed = new ArrayList<>();
        List<Holder<Enchantment>> forbidden = new ArrayList<>();
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            (isForbidden(enchantment, mode) ? forbidden : allowed).add(enchantment);
        }
        if (allowed.isEmpty()) {
            refuse(player, forbiddenMessage(mode, sorted(forbidden)));
            return;
        }
        for (Session other : SESSIONS.values()) {
            if (other.lectern.equals(pos) && other.level == level) {
                refuse(player, Component.translatable("message.spectral_enchantments.study.busy"));
                return;
            }
        }

        double curseChance = mode == StudyMode.STUDYING ? SpectralConfig.studyingCurseChance(player.experienceLevel)
                : SpectralConfig.copyingCurseChance(player.experienceLevel);
        Roll roll = roll(seed(player), mode, allowed, curses(level), curseChance);
        int cost = player.getAbilities().instabuild ? 0 : xpCost(roll.target(),
                mode == StudyMode.STUDYING ? SpectralConfig.studyingXpCost() : SpectralConfig.copyingXpCost(),
                SpectralConfig.studyTreasureMultiplier());
        if (totalExperience(player) < cost) {
            refuse(player, Component.translatable("message.spectral_enchantments." + mode.getSerializedName() + ".not_enough_xp", cost));
            return;
        }
        int duration = mode == StudyMode.STUDYING ? SpectralConfig.studyingDurationTicks() : SpectralConfig.copyingDurationTicks();
        Session started = new Session(player, mode, pos, held, book, roll, cost, now, duration);
        SESSIONS.put(id, started);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                StudyPayload.start(player.getId(), mode, started.lectern, duration, roll.target()));
    }

    /** The player let go of use: stop their study or copy now. */
    public static void release(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            send(session, StudyPayload.Kind.STOP, Optional.empty());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!FLOATING.isEmpty()) {
            int now = event.getServer().getTickCount();
            FLOATING.entrySet().removeIf(entry -> {
                ItemEntity item = entry.getKey();
                if (item.isRemoved()) {
                    return true;
                }
                if (now >= entry.getValue().dropTick()) {
                    item.setNoGravity(false);
                    return true;
                }
                if (entry.getValue().cursed() && item.level() instanceof ServerLevel level) {
                    level.sendParticles(BLOOD, item.getX(), item.getY() + 0.2, item.getZ(), 3, 0.15, 0.1, 0.15, 0);
                    level.sendParticles(BLOOD_DRIP, item.getX(), item.getY(), item.getZ(), 2, 0.12, 0.05, 0.12, 0);
                }
                return false;
            });
        }
        if (!SPENT.isEmpty()) {
            int now = event.getServer().getTickCount();
            SPENT.values().removeIf(spent -> now - spent.lastHeld > HOLD_TIMEOUT_TICKS);
        }
        if (SESSIONS.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        List<Session> finished = new ArrayList<>();
        for (Iterator<Session> it = SESSIONS.values().iterator(); it.hasNext(); ) {
            Session session = it.next();
            if (!stillGoing(session, now)) {
                it.remove();
                send(session, StudyPayload.Kind.STOP, Optional.empty());
            } else if (now - session.startTick >= session.duration) {
                it.remove();
                finished.add(session);
            }
        }
        for (Session session : finished) {
            finish(session, now);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        SPENT.clear();
        FLOATING.clear();
    }

    /** Whether the player is still at it: holding use, in reach, and nothing in their hands or on the lectern changed. */
    private static boolean stillGoing(Session session, int now) {
        ServerPlayer player = session.player;
        if (player.isRemoved() || !player.isAlive() || player.isSpectator() || player.level() != session.level
                || now - session.lastHeld > HOLD_TIMEOUT_TICKS || !session.level.isLoaded(session.lectern)
                || !player.canInteractWithBlock(session.lectern, 1.0)) {
            return false;
        }
        if (!(session.level.getBlockEntity(session.lectern) instanceof LecternBlockEntity lectern)
                || !ItemStack.isSameItemSameComponents(lectern.getBook(), session.book)
                || !ItemStack.isSameItemSameComponents(player.getMainHandItem(), session.held)) {
            return false;
        }
        return session.mode != StudyMode.STUDYING || player.getOffhandItem().is(CATALYST);
    }

    private static void finish(Session session, int now) {
        ServerPlayer player = session.player;
        if (totalExperience(player) < session.xpCost) {
            send(session, StudyPayload.Kind.STOP, Optional.empty());
            refuse(player, Component.translatable("message.spectral_enchantments." + session.mode.getSerializedName() + ".not_enough_xp",
                    session.xpCost));
            return;
        }
        if (session.mode == StudyMode.STUDYING) {
            player.getMainHandItem().shrink(1);
            player.getOffhandItem().shrink(1);
        }
        ServerLevel level = session.level;
        ((LecternBlockEntity) level.getBlockEntity(session.lectern)).clearContent();
        LecternBlock.resetBookState(player, level, session.lectern, level.getBlockState(session.lectern), false);
        boolean cursed = session.roll.curse().isPresent();
        floatUp(level, session.lectern, EnchantedBookItem.createForEnchantment(new EnchantmentInstance(session.roll.written(), 1)), cursed);
        if (cursed) {
            backlash(player, session.lectern);
        }
        if (session.xpCost > 0) {
            player.giveExperiencePoints(-session.xpCost);
        }
        player.setData(ModAttachments.STUDY_SEED, newSeed(player.getRandom()));
        effects(session);
        SPENT.put(player.getUUID(), new Spent(session.lectern, now));
        send(session, session.roll.curse().isPresent() ? StudyPayload.Kind.CURSED : StudyPayload.Kind.SUCCESS,
                Optional.of(session.roll.written()));
    }

    /**
     * The written book rises slowly off the lectern, then drops. Without gravity an item's own drag slows it, the same
     * on both sides, so nothing needs sending while it floats. It can't be picked up until it starts to fall.
     */
    private static void floatUp(ServerLevel level, BlockPos lectern, ItemStack book, boolean cursed) {
        ItemEntity item = new ItemEntity(level, lectern.getX() + 0.5, lectern.getY() + 1.0, lectern.getZ() + 0.5, book, 0, FLOAT_SPEED, 0);
        item.setNoGravity(true);
        item.setPickUpDelay(FLOAT_TICKS);
        level.addFreshEntity(item);
        FLOATING.put(item, new Floating(level.getServer().getTickCount() + FLOAT_TICKS, cursed));
        if (cursed) {
            level.sendParticles(BLOOD, item.getX(), item.getY(), item.getZ(), 40, 0.35, 0.3, 0.35, 0);
            level.sendParticles(BLOOD_DRIP, item.getX(), item.getY() + 0.6, item.getZ(), 30, 0.5, 0.5, 0.5, 0);
        }
    }

    /** A curse lashes out at whoever wrote it: half a heart, and a shove away from the lectern. */
    private static void backlash(ServerPlayer player, BlockPos lectern) {
        player.hurt(player.damageSources().magic(), 1f);
        double dx = lectern.getX() + 0.5 - player.getX();
        double dz = lectern.getZ() + 0.5 - player.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            dx = -player.getLookAngle().x;
            dz = -player.getLookAngle().z;
        }
        player.knockback(0.5, dx, dz);
        player.hurtMarked = true;
    }

    /** A puff of purple sparks where the hands were, and enchanting sparks settling on the lectern. */
    private static void effects(Session session) {
        ServerPlayer player = session.player;
        ServerLevel level = session.level;
        Vec3 hands = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.45, 0);
        level.sendParticles(new DustParticleOptions(PURPLE, 1.2f), hands.x, hands.y, hands.z, 24, 0.18, 0.18, 0.18, 0.02);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, hands.x, hands.y, hands.z, 16, 0.15, 0.15, 0.15, 0.04);
        Vec3 book = Vec3.atCenterOf(session.lectern).add(0, 0.6, 0);
        level.sendParticles(ParticleTypes.ENCHANT, book.x, book.y, book.z, 30, 0.3, 0.2, 0.3, 0.6);
        if (session.mode == StudyMode.STUDYING) {
            level.playSound(null, hands.x, hands.y, hands.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 1.2f);
        } else {
            level.playSound(null, hands.x, hands.y, hands.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        }
        level.playSound(null, book.x, book.y, book.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, 1f);
        if (session.roll.curse().isPresent()) {
            level.playSound(null, book.x, book.y, book.z, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1f, 0.6f);
        }
    }

    // ---- Rules (public for the game tests) ----

    /** Whether {@code enchantment} may not be written by {@code mode}: switched off, or tagged so. */
    public static boolean isForbidden(Holder<Enchantment> enchantment, StudyMode mode) {
        return enchantment.is(ModEnchantments.DISABLED) || enchantment.is(mode == StudyMode.STUDYING ? UNSTUDYABLE : UNCOPYABLE);
    }

    /**
     * Picks one of {@code allowed}, and with {@code curseChance} one of {@code curses} to write instead. The same
     * seed, mode and enchantments always give the same roll.
     */
    public static Roll roll(int seed, StudyMode mode, List<Holder<Enchantment>> allowed, List<Holder<Enchantment>> curses,
                            double curseChance) {
        List<Holder<Enchantment>> pool = sorted(allowed);
        long hash = mode.ordinal();
        for (Holder<Enchantment> enchantment : pool) {
            hash = hash * 31 + name(enchantment).hashCode();
        }
        RandomSource random = RandomSource.create((long) seed << 32 ^ hash);
        Holder<Enchantment> target = pool.get(random.nextInt(pool.size()));
        Optional<Holder<Enchantment>> curse = Optional.empty();
        if (!curses.isEmpty() && random.nextDouble() < curseChance) {
            List<Holder<Enchantment>> sortedCurses = sorted(curses);
            curse = Optional.of(sortedCurses.get(random.nextInt(sortedCurses.size())));
        }
        return new Roll(target, curse);
    }

    /** From {@code max} at level 0 in a straight line to {@code min} at {@code minLevel}, and {@code min} above it. */
    public static double curseChance(int level, double max, double min, int minLevel) {
        return Mth.clampedLerp(max, min, level / (double) Math.max(1, minLevel));
    }

    /** {@code base} experience points, times {@code treasureMultiplier} for treasure enchantments other than curses. */
    public static int xpCost(Holder<Enchantment> enchantment, int base, double treasureMultiplier) {
        boolean treasure = enchantment.is(EnchantmentTags.TREASURE) && !enchantment.is(EnchantmentTags.CURSE);
        return treasure ? (int) Math.round(base * treasureMultiplier) : base;
    }

    /** All the experience points the player has, levels included. */
    public static long totalExperience(Player player) {
        double level = player.experienceLevel;
        double points;
        if (level <= 16) {
            points = level * level + 6 * level;
        } else if (level <= 31) {
            points = 2.5 * level * level - 40.5 * level + 360;
        } else {
            points = 4.5 * level * level - 162.5 * level + 2220;
        }
        return Math.round(points) + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    /** A book and quill with nothing written in it. */
    public static boolean isBlank(ItemStack book) {
        WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
        return content == null || content.pages().stream().allMatch(page -> page.raw().isEmpty());
    }

    /** "Mending is too powerful to be copied", or "Mending and Frost Walker are ...". */
    public static Component forbiddenMessage(StudyMode mode, List<Holder<Enchantment>> forbidden) {
        String key = "message.spectral_enchantments." + mode.getSerializedName() + ".forbidden";
        List<Component> names = forbidden.stream().map(enchantment -> enchantment.value().description()).toList();
        if (names.size() == 1) {
            return Component.translatable(key + ".one", names.get(0));
        }
        return Component.translatable(key + ".many", ComponentUtils.formatList(names.subList(0, names.size() - 1), Component.literal(", ")),
                names.get(names.size() - 1));
    }

    /** The curses a failed study or copy can write in {@code level}: {@link #CURSES}, less any switched off. */
    public static List<Holder<Enchantment>> curses(Level level) {
        List<Holder<Enchantment>> curses = new ArrayList<>();
        for (Holder<Enchantment> curse : level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getTagOrEmpty(CURSES)) {
            if (!curse.is(ModEnchantments.DISABLED)) {
                curses.add(curse);
            }
        }
        return curses;
    }

    // ---- Helpers ----

    private static int seed(ServerPlayer player) {
        int seed = player.getData(ModAttachments.STUDY_SEED);
        if (seed == 0) {
            seed = newSeed(player.getRandom());
            player.setData(ModAttachments.STUDY_SEED, seed);
        }
        return seed;
    }

    private static int newSeed(RandomSource random) {
        return random.nextInt() | 1; // never 0, which means "not picked yet"
    }

    private static List<Holder<Enchantment>> sorted(List<Holder<Enchantment>> enchantments) {
        List<Holder<Enchantment>> sorted = new ArrayList<>(enchantments);
        sorted.sort(Comparator.comparing(EnchantmentStudy::name));
        return sorted;
    }

    private static String name(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(key -> key.location().toString()).orElse("");
    }

    /** Takes the interaction: no lectern screen, no item use, no arm swing. */
    private static void claim(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    private static void refuse(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    private static void send(Session session, StudyPayload.Kind kind, Optional<Holder<Enchantment>> written) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(session.player,
                StudyPayload.end(session.player.getId(), kind, session.mode, session.lectern, written));
    }
}
