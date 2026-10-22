package com.linc.spectralenchantments.index;

import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.network.EnchantingIndexPayload;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Enchanting Index's work on the server: finding the shelves ({@link #BOOK_HOLDERS}: chiseled bookshelves and
 * Supplementaries' book piles by default) around a player or joined to one shelf, listing the enchanted books in them,
 * and renaming those books from the index's page. Books are read through {@link Container}, which both blocks'
 * block entities are, so no other mod is needed to compile or run.
 */
public final class EnchantingIndex {
    /** Blocks whose books the index lists. Their block entities must be {@link Container}s. */
    public static final TagKey<Block> BOOK_HOLDERS = TagKey.create(Registries.BLOCK, SpectralEnchantments.id("enchanting_index/book_holders"));
    /** The most books (copies, not kinds) one use lists, nearest shelves first. Keeps the packet well under 1 MiB. */
    public static final int MAX_BOOKS = 4096;
    /** The anvil's limit. */
    public static final int MAX_NAME_LENGTH = 50;

    /** Where a book is: the block holding it and its slot there. */
    public record Location(BlockPos pos, int slot) {
        public static final StreamCodec<ByteBuf, Location> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Location::pos,
                ByteBufCodecs.VAR_INT, Location::slot,
                Location::new);
    }

    /** One kind of book (one copy, as a stack of 1) and where each copy of it is. */
    public record Entry(ItemStack book, List<Location> locations) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Entry::book,
                Location.STREAM_CODEC.apply(ByteBufCodecs.list()), Entry::locations,
                Entry::new);
    }

    /** What a use of the index found: every kind of book, and whether {@link #MAX_BOOKS} cut the list short. */
    public record Catalogue(List<Entry> entries, boolean truncated) {}

    /** Each player's last listing, so a rename can only touch books the index showed them. Server thread. */
    private static final Map<ServerPlayer, Listing> LISTINGS = new WeakHashMap<>();

    private record Listing(ResourceKey<Level> dimension, Set<Location> locations) {}

    private EnchantingIndex() {}

    public static boolean isEnchantedBook(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    /**
     * Every book holder within {@code radius} blocks of {@code center} on each axis (a cube), nearest first. Only the
     * block entities of loaded chunks are looked at, never each block in the cube.
     */
    public static List<BlockPos> holdersAround(Level level, BlockPos center, int radius) {
        List<BlockPos> found = new ArrayList<>();
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = center.getY() - radius;
        int maxY = center.getY() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                            && pos.getZ() >= minZ && pos.getZ() <= maxZ
                            && blockEntity instanceof Container && blockEntity.getBlockState().is(BOOK_HOLDERS)) {
                        found.add(pos);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return found;
    }

    /**
     * The book holder at {@code start} and every one joined to it through others, nearest (in steps) first, at most
     * {@code max}. Holders touching at an edge or corner count as joined, since shelves are often stacked offset.
     * Unloaded chunks are left alone.
     */
    public static List<BlockPos> holdersJoined(Level level, BlockPos start, int max) {
        List<BlockPos> found = new ArrayList<>();
        if (!level.getBlockState(start).is(BOOK_HOLDERS)) {
            return found;
        }
        LongSet visited = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start.immutable());
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && found.size() < max) {
            BlockPos pos = queue.poll();
            found.add(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        next.setWithOffset(pos, dx, dy, dz);
                        if (visited.add(next.asLong()) && level.isLoaded(next) && level.getBlockState(next).is(BOOK_HOLDERS)) {
                            queue.add(next.immutable());
                        }
                    }
                }
            }
        }
        return found;
    }

    /** The enchanted books in {@code holders}, one entry per kind, in the order first met; at most {@link #MAX_BOOKS}. */
    public static Catalogue catalogue(Level level, List<BlockPos> holders) {
        Map<ItemStack, List<Location>> kinds = new Object2ObjectLinkedOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
        int books = 0;
        for (BlockPos pos : holders) {
            if (!(level.getBlockEntity(pos) instanceof Container container)) {
                continue;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!isEnchantedBook(stack)) {
                    continue;
                }
                if (books == MAX_BOOKS) {
                    return new Catalogue(entries(kinds), true);
                }
                books++;
                kinds.computeIfAbsent(stack.copyWithCount(1), key -> new ArrayList<>()).add(new Location(pos, slot));
            }
        }
        return new Catalogue(entries(kinds), false);
    }

    private static List<Entry> entries(Map<ItemStack, List<Location>> kinds) {
        List<Entry> entries = new ArrayList<>(kinds.size());
        kinds.forEach((book, locations) -> entries.add(new Entry(book, locations)));
        return entries;
    }

    /** Lists the books in {@code holders} and opens the index's page on the player's screen. */
    public static void open(ServerPlayer player, List<BlockPos> holders) {
        Catalogue catalogue = catalogue(player.level(), holders);
        Set<Location> locations = new HashSet<>();
        for (Entry entry : catalogue.entries()) {
            locations.addAll(entry.locations());
        }
        LISTINGS.put(player, new Listing(player.level().dimension(), locations));
        PacketDistributor.sendToPlayer(player, new EnchantingIndexPayload(catalogue.entries(), catalogue.truncated()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 1f, 1f);
    }

    /**
     * Renames each book at {@code locations} that the player's last listing showed and that is still {@code book};
     * a blank name takes the custom name off. Free, unlike the anvil. Returns how many books were renamed.
     */
    public static int rename(ServerPlayer player, List<Location> locations, ItemStack book, String name) {
        Listing listing = LISTINGS.get(player);
        if (listing == null || listing.dimension() != player.level().dimension()) {
            return 0;
        }
        String filtered = StringUtil.filterText(name).strip();
        if (filtered.length() > MAX_NAME_LENGTH) {
            filtered = filtered.substring(0, MAX_NAME_LENGTH);
        }
        Level level = player.level();
        int renamed = 0;
        for (Location location : locations) {
            if (!listing.locations().contains(location) || !level.isLoaded(location.pos())
                    || !(level.getBlockEntity(location.pos()) instanceof Container container)
                    || location.slot() < 0 || location.slot() >= container.getContainerSize()) {
                continue;
            }
            ItemStack current = container.getItem(location.slot());
            if (!ItemStack.isSameItemSameComponents(current, book)) {
                continue;
            }
            // In place rather than through setItem, which would also move a chiseled shelf's comparator output.
            if (filtered.isEmpty()) {
                current.remove(DataComponents.CUSTOM_NAME);
            } else {
                current.set(DataComponents.CUSTOM_NAME, Component.literal(filtered));
            }
            container.setChanged();
            renamed++;
        }
        return renamed;
    }
}
