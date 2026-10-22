package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.index.EnchantingIndex;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

/**
 * Where one book sits within a book holder, for the Enchanting Index's glow, and whether it is still there. Knows the
 * chiseled bookshelf's six slots and Supplementaries' book pile (books stacked flat, a {@code books} count in its
 * block state); any other holder glows as a whole.
 */
final class BookSlotShapes {
    /** How far the glow stands out from what it covers, so it is never hidden in the surface itself. */
    private static final double RAISE = 0.01;
    /** The left and right edge of each column of a chiseled shelf's books, in pixels across the face. */
    private static final int[][] SHELF_COLUMNS = {{1, 6}, {6, 11}, {11, 15}};
    /** The bottom and top of the upper and lower row of a chiseled shelf's books, in pixels. */
    private static final int[][] SHELF_ROWS = {{9, 15}, {1, 7}};
    /** How far a chiseled shelf's books reach back into it from the face, so the whole book glows, not just its spine. */
    private static final double SHELF_BOOK_DEPTH = 4 / 16.0;
    /**
     * One of Supplementaries' flat books: 10 by 8 pixels, centred on the block, four pixels thick, before it is turned
     * about the block's middle (see {@link #pileAngle}).
     */
    private static final AABB PILE_BOOK = new AABB(0.5 - 5 / 16.0, 0, 0.5 - 4 / 16.0, 0.5 + 5 / 16.0, 4 / 16.0, 0.5 + 4 / 16.0);

    /**
     * The box (within the block, 0 to 1) around a book, turned {@code yaw} radians about the block's vertical
     * middle line.
     */
    record Shape(AABB box, float yaw) {}

    private BookSlotShapes() {}

    static Shape shape(BlockState state, BlockEntity blockEntity, int slot) {
        if (state.getBlock() instanceof ChiseledBookShelfBlock && slot >= 0 && slot < 6) {
            return new Shape(shelfSlot(state.getValue(HorizontalDirectionalBlock.FACING), slot), 0f);
        }
        if (pileBooks(state) != null && slot >= 0) {
            AABB book = PILE_BOOK.move(0, slot * 4 / 16.0, 0).inflate(RAISE);
            Float angle = pileAngle(blockEntity, slot);
            // Without the angle, a square around wherever the book may have turned.
            return angle != null ? new Shape(book, -angle)
                    : new Shape(new AABB(3 / 16.0, book.minY, 3 / 16.0, 13 / 16.0, book.maxY, 13 / 16.0), 0f);
        }
        return new Shape(new AABB(0, 0, 0, 1, 1, 1).inflate(RAISE), 0f);
    }

    /** Whether, as far as the client can tell, there is still a book in {@code slot}. */
    static boolean hasBook(BlockState state, int slot) {
        if (!state.is(EnchantingIndex.BOOK_HOLDERS)) {
            return false;
        }
        if (state.getBlock() instanceof ChiseledBookShelfBlock) {
            return slot >= 0 && slot < 6 && state.getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(slot));
        }
        IntegerProperty books = pileBooks(state);
        return books == null || slot < state.getValue(books);
    }

    private static IntegerProperty pileBooks(BlockState state) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty("books");
        return property instanceof IntegerProperty books ? books : null;
    }

    /**
     * A chiseled shelf's slot: a box around the books in it, from just in front of the face to
     * {@link #SHELF_BOOK_DEPTH} into the shelf. Columns run left to right as seen from the front, the same way vanilla
     * reads which slot a click hit.
     */
    private static AABB shelfSlot(Direction facing, int slot) {
        double u0 = SHELF_COLUMNS[slot % 3][0] / 16.0;
        double u1 = SHELF_COLUMNS[slot % 3][1] / 16.0;
        double y0 = SHELF_ROWS[slot / 3][0] / 16.0;
        double y1 = SHELF_ROWS[slot / 3][1] / 16.0;
        return switch (facing) {
            case NORTH -> new AABB(1 - u1, y0, -RAISE, 1 - u0, y1, SHELF_BOOK_DEPTH);
            case SOUTH -> new AABB(u0, y0, 1 - SHELF_BOOK_DEPTH, u1, y1, 1 + RAISE);
            case WEST -> new AABB(-RAISE, y0, u0, SHELF_BOOK_DEPTH, y1, u1);
            case EAST -> new AABB(1 - SHELF_BOOK_DEPTH, y0, 1 - u1, 1 + RAISE, y1, 1 - u0);
            default -> new AABB(0, 0, 0, 1, 1, 1).inflate(RAISE);
        };
    }

    // ---- Supplementaries ----

    /** Per block entity class: how to read a pile's book angles, or null if it has none. */
    private static final Map<Class<?>, PileAccess> PILE_ACCESS = new HashMap<>();

    private record PileAccess(Field visuals, Method books, Method angle) {}

    /**
     * The angle Supplementaries turns the book in {@code slot} by, in radians, read from its block entity
     * ({@code booksVisuals}, a list of books each with {@code getAngle()}) without depending on it; null if it can't
     * be read.
     */
    private static Float pileAngle(BlockEntity blockEntity, int slot) {
        if (blockEntity == null) {
            return null;
        }
        PileAccess access = PILE_ACCESS.computeIfAbsent(blockEntity.getClass(), BookSlotShapes::pileAccess);
        if (access == null) {
            return null;
        }
        try {
            List<?> books = (List<?>) access.books().invoke(access.visuals().get(blockEntity));
            return slot < books.size() ? (Float) access.angle().invoke(books.get(slot)) : null;
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    private static PileAccess pileAccess(Class<?> type) {
        try {
            Field visuals = type.getField("booksVisuals");
            Method books = visuals.getType().getMethod("books");
            Class<?> book = Class.forName(visuals.getType().getName().replace("$BooksList", "$BookVisualData"), false, type.getClassLoader());
            return new PileAccess(visuals, books, book.getMethod("getAngle"));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
