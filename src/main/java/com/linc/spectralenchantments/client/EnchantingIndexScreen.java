package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.ModEffects;
import com.linc.spectralenchantments.SpectralEnchantments;
import com.linc.spectralenchantments.index.EnchantingIndex;
import com.linc.spectralenchantments.network.EnchantingIndexRenamePayload;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The Enchanting Index's page: every enchanted book found, one row per kind with how many copies, under a search line
 * and a row of type filters, on a single sepia parchment page. Clicking a row closes the page and makes those books
 * glow on their shelves; right-clicking one renames every copy.
 */
final class EnchantingIndexScreen extends Screen {
    private static final ResourceLocation PAGE = sprite("page");
    private static final ResourceLocation DIVIDER = sprite("divider");
    private static final ResourceLocation TILE = sprite("filter_tile");
    private static final ResourceLocation TILE_HOVER = sprite("filter_tile_hover");
    private static final ResourceLocation TILE_SELECTED = sprite("filter_tile_selected");
    private static final ResourceLocation CURSE_ROW = sprite("curse_row");
    private static final ResourceLocation SCROLL_THUMB = sprite("scroll_thumb");

    private static final int INK = 0xFF3B2614;
    private static final int FADED_INK = 0xFF7A5C3A;
    private static final int HOVER_WASH = 0x305A3A1E;
    private static final int EDIT_WASH = 0x40FFF4D8;

    private static final int PAGE_WIDTH = 212;
    private static final int PAGE_MAX_HEIGHT = 256;
    private static final int DIVIDER_WIDTH = 120;
    private static final int DIVIDER_HEIGHT = 5;
    private static final int MARGIN = 16;
    private static final int LIST_WIDTH = 174;
    private static final int ROW_HEIGHT = 18;
    private static final int TILE_SIZE = 20;
    private static final int TILE_GAP = 2;
    private static final int TILES_PER_ROW = 8;
    private static final int SCROLL_WIDTH = 5;
    private static final int MIN_THUMB = 12;

    /** The type filters, in button order. Each but the last two tests an enchantment against its icon's item. */
    private enum Filter {
        SWORD(Items.DIAMOND_SWORD), AXE(Items.DIAMOND_AXE), PICKAXE(Items.DIAMOND_PICKAXE), SHOVEL(Items.DIAMOND_SHOVEL),
        HOE(Items.DIAMOND_HOE), BOW(Items.BOW), CROSSBOW(Items.CROSSBOW), TRIDENT(Items.TRIDENT),
        MACE(Items.MACE), FISHING_ROD(Items.FISHING_ROD), HELMET(Items.DIAMOND_HELMET), CHESTPLATE(Items.DIAMOND_CHESTPLATE),
        LEGGINGS(Items.DIAMOND_LEGGINGS), BOOTS(Items.DIAMOND_BOOTS),
        /** Enchantments that go on every one of the above, like Mending and Unbreaking. */
        GENERAL(Items.DIRT),
        /** Books carrying a curse. */
        CURSE(null);

        final ItemStack icon;

        Filter(Item item) {
            this.icon = item == null ? ItemStack.EMPTY : new ItemStack(item);
        }

        int bit() {
            return 1 << ordinal();
        }

        boolean isItemType() {
            return this != GENERAL && this != CURSE;
        }

        Component label() {
            return Component.translatable("gui.spectral_enchantments.enchanting_index.filter." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static final int ALL_ITEM_TYPES = (1 << Filter.GENERAL.ordinal()) - 1;

    /** One kind of book, as listed. */
    private static final class Row {
        ItemStack book;
        final List<EnchantingIndex.Location> locations;
        String label;
        boolean named;
        String search;
        int filters;
        boolean cursed;

        Row(ItemStack book, List<EnchantingIndex.Location> locations) {
            this.book = book;
            this.locations = new ArrayList<>(locations);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<Row> shown = new ArrayList<>();
    private final boolean truncated;
    /** Which item types each enchantment goes on (as filter bits), worked out once per enchantment. */
    private final Map<Holder<Enchantment>, Integer> enchantmentFilters = new HashMap<>();
    private int books;
    private int selectedFilters;
    private String query = "";
    private int scroll;
    private boolean draggingScroll;

    private EditBox searchBox;
    private EditBox renameBox;
    private Row renaming;

    private int left;
    private int top;
    private int pageHeight;
    private int listLeft;
    private int listTop;
    private int visibleRows;

    EnchantingIndexScreen(List<EnchantingIndex.Entry> entries, boolean truncated) {
        super(Component.translatable("gui.spectral_enchantments.enchanting_index.title"));
        this.truncated = truncated;
        for (EnchantingIndex.Entry entry : entries) {
            Row row = new Row(entry.book(), entry.locations());
            describe(row);
            rows.add(row);
            books += entry.locations().size();
        }
        sortRows();
    }

    private static ResourceLocation sprite(String name) {
        return SpectralEnchantments.id("enchanting_index/" + name);
    }

    /** Sepia tooltips while the page is open, dark enough for the usual tooltip colors to read on. */
    static void onTooltipColor(RenderTooltipEvent.Color event) {
        if (Minecraft.getInstance().screen instanceof EnchantingIndexScreen) {
            event.setBackground(0xF02A1C10);
            event.setBorderStart(0xFF9A7A48);
            event.setBorderEnd(0xFF5A4022);
        }
    }

    // ---- Layout ----

    @Override
    protected void init() {
        pageHeight = Math.min(PAGE_MAX_HEIGHT, height - 16);
        left = (width - PAGE_WIDTH) / 2;
        top = (height - pageHeight) / 2;
        listLeft = left + MARGIN;
        listTop = top + 104;
        // Rows stop short of the footer, which sits inside the page's ruled frame.
        visibleRows = Math.max(1, (top + pageHeight - 26 - listTop) / ROW_HEIGHT);

        int seekWidth = font.width(Component.translatable("gui.spectral_enchantments.enchanting_index.search"));
        searchBox = new EditBox(font, listLeft + seekWidth + 4, top + 35, LIST_WIDTH - seekWidth - 4, 10,
                Component.translatable("gui.spectral_enchantments.enchanting_index.search"));
        searchBox.setBordered(false);
        searchBox.setTextColor(INK);
        searchBox.setTextShadow(false);
        searchBox.setMaxLength(50);
        searchBox.setValue(query);
        searchBox.setResponder(value -> {
            query = value;
            refilter();
        });
        addWidget(searchBox);
        setInitialFocus(searchBox);
        renameBox = null;
        renaming = null;
        refilter();
    }

    private int listBottom() {
        return listTop + visibleRows * ROW_HEIGHT;
    }

    private int tileX(int index) {
        return listLeft + (index % TILES_PER_ROW) * (TILE_SIZE + TILE_GAP);
    }

    private int tileY(int index) {
        return top + 52 + (index / TILES_PER_ROW) * (TILE_SIZE + TILE_GAP);
    }

    private int scrollX() {
        return listLeft + LIST_WIDTH + 3;
    }

    private int maxScroll() {
        return Math.max(0, shown.size() - visibleRows);
    }

    // ---- Rows ----

    /** Works out a row's label, what the search matches and which filters it passes. */
    private void describe(Row row) {
        ItemEnchantments stored = row.book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        List<Object2IntMap.Entry<Holder<Enchantment>>> enchantments = new ArrayList<>(stored.entrySet());
        // Real enchantments ahead of curses, so a cursed Sharpness book reads as Sharpness.
        enchantments.sort(Comparator.<Object2IntMap.Entry<Holder<Enchantment>>, Boolean>comparing(entry -> entry.getKey().is(EnchantmentTags.CURSE))
                .thenComparing(entry -> entry.getKey().value().description().getString()));
        StringBuilder search = new StringBuilder();
        row.filters = 0;
        row.cursed = false;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments) {
            search.append(entry.getKey().value().description().getString()).append('\n');
            row.filters |= filtersFor(entry.getKey());
            row.cursed |= entry.getKey().is(EnchantmentTags.CURSE);
        }
        row.named = row.book.has(DataComponents.CUSTOM_NAME);
        if (row.named) {
            row.label = row.book.getHoverName().getString();
        } else if (enchantments.isEmpty()) {
            row.label = row.book.getHoverName().getString();
        } else {
            Object2IntMap.Entry<Holder<Enchantment>> first = enchantments.getFirst();
            row.label = Enchantment.getFullname(first.getKey(), first.getIntValue()).getString();
            if (enchantments.size() > 1) {
                row.label += " " + Component.translatable("gui.spectral_enchantments.enchanting_index.more", enchantments.size() - 1).getString();
            }
        }
        search.append(row.label);
        row.search = search.toString().toLowerCase(Locale.ROOT);
    }

    private int filtersFor(Holder<Enchantment> enchantment) {
        return enchantmentFilters.computeIfAbsent(enchantment, holder -> {
            int bits = 0;
            for (Filter filter : Filter.values()) {
                if (filter.isItemType() && holder.value().isSupportedItem(filter.icon)) {
                    bits |= filter.bit();
                }
            }
            if (bits == ALL_ITEM_TYPES) {
                bits |= Filter.GENERAL.bit();
            }
            if (holder.is(EnchantmentTags.CURSE)) {
                bits |= Filter.CURSE.bit();
            }
            return bits;
        });
    }

    private void sortRows() {
        rows.sort(Comparator.comparing((Row row) -> row.label.toLowerCase(Locale.ROOT))
                .thenComparing(row -> -row.locations.size()));
    }

    /** Rebuilds the shown rows from the search and the filters (any selected filter will do). */
    private void refilter() {
        shown.clear();
        String needle = query.strip().toLowerCase(Locale.ROOT);
        for (Row row : rows) {
            if ((selectedFilters == 0 || (row.filters & selectedFilters) != 0)
                    && (needle.isEmpty() || row.search.contains(needle))) {
                shown.add(row);
            }
        }
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listLeft || mouseX >= listLeft + LIST_WIDTH || mouseY < listTop || mouseY >= listBottom()) {
            return -1;
        }
        int index = scroll + (int) ((mouseY - listTop) / ROW_HEIGHT);
        return index < shown.size() ? index : -1;
    }

    private int tileAt(double mouseX, double mouseY) {
        for (Filter filter : Filter.values()) {
            int x = tileX(filter.ordinal());
            int y = tileY(filter.ordinal());
            if (mouseX >= x && mouseX < x + TILE_SIZE && mouseY >= y && mouseY < y + TILE_SIZE) {
                return filter.ordinal();
            }
        }
        return -1;
    }

    // ---- Drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(PAGE, left, top, PAGE_WIDTH, pageHeight);

        graphics.drawString(font, title, left + (PAGE_WIDTH - font.width(title)) / 2, top + 12, INK, false);
        graphics.blitSprite(DIVIDER, left + (PAGE_WIDTH - DIVIDER_WIDTH) / 2, top + 23, DIVIDER_WIDTH, DIVIDER_HEIGHT);

        Component seek = Component.translatable("gui.spectral_enchantments.enchanting_index.search");
        graphics.drawString(font, seek, listLeft, top + 35, FADED_INK, false);
        graphics.hLine(searchBox.getX() - 1, listLeft + LIST_WIDTH - 1, top + 46, FADED_INK);
        if (searchBox.getValue().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.spectral_enchantments.enchanting_index.search_hint")
                    .withStyle(ChatFormatting.ITALIC), searchBox.getX(), top + 35, FADED_INK & 0x99FFFFFF, false);
        }
        searchBox.render(graphics, mouseX, mouseY, partialTick);

        int hoveredTile = renaming == null ? tileAt(mouseX, mouseY) : -1;
        for (Filter filter : Filter.values()) {
            int x = tileX(filter.ordinal());
            int y = tileY(filter.ordinal());
            boolean selected = (selectedFilters & filter.bit()) != 0;
            graphics.blitSprite(selected ? TILE_SELECTED : hoveredTile == filter.ordinal() ? TILE_HOVER : TILE, x, y, TILE_SIZE, TILE_SIZE);
            if (filter == Filter.CURSE) {
                graphics.blit(x + 2, y + 2, 0, 16, 16, minecraft.getMobEffectTextures().get(ModEffects.CURSED));
            } else {
                graphics.renderItem(filter.icon, x + 2, y + 2);
            }
        }
        graphics.blitSprite(DIVIDER, left + (PAGE_WIDTH - DIVIDER_WIDTH) / 2, top + 96, DIVIDER_WIDTH, DIVIDER_HEIGHT);

        int hoveredRow = renaming == null && !draggingScroll ? rowAt(mouseX, mouseY) : -1;
        renderRows(graphics, hoveredRow);
        renderScrollBar(graphics);
        renderFooter(graphics);

        if (renameBox != null) {
            renameBox.render(graphics, mouseX, mouseY, partialTick);
        }
        if (hoveredRow >= 0) {
            renderRowTooltip(graphics, shown.get(hoveredRow), mouseX, mouseY);
        } else if (hoveredTile >= 0) {
            graphics.renderTooltip(font, Filter.values()[hoveredTile].label(), mouseX, mouseY);
        }
    }

    private void renderRows(GuiGraphics graphics, int hoveredRow) {
        if (shown.isEmpty()) {
            Component empty = Component.translatable(rows.isEmpty() ? "gui.spectral_enchantments.enchanting_index.empty"
                    : "gui.spectral_enchantments.enchanting_index.no_match").withStyle(ChatFormatting.ITALIC);
            graphics.drawString(font, empty, left + (PAGE_WIDTH - font.width(empty)) / 2, listTop + 8, FADED_INK, false);
            return;
        }
        graphics.enableScissor(listLeft, listTop, listLeft + LIST_WIDTH, listBottom());
        for (int i = 0; i < visibleRows && scroll + i < shown.size(); i++) {
            Row row = shown.get(scroll + i);
            int y = listTop + i * ROW_HEIGHT;
            if (row.cursed) {
                graphics.blitSprite(CURSE_ROW, listLeft, y, LIST_WIDTH, ROW_HEIGHT);
            }
            if (row == renaming) {
                graphics.fill(listLeft, y, listLeft + LIST_WIDTH, y + ROW_HEIGHT, EDIT_WASH);
            } else if (scroll + i == hoveredRow) {
                graphics.fill(listLeft, y, listLeft + LIST_WIDTH, y + ROW_HEIGHT, HOVER_WASH);
            }
            graphics.renderItem(row.book, listLeft + 1, y + 1);

            String count = "×" + row.locations.size();
            int countX = listLeft + LIST_WIDTH - 2 - font.width(count);
            graphics.drawString(font, count, countX, y + 5, FADED_INK, false);
            if (row == renaming) {
                graphics.hLine(listLeft + 19, countX - 4, y + 14, FADED_INK);
                continue;
            }
            int labelX = listLeft + 20;
            int room = countX - 6 - labelX;
            String label = row.label;
            if (font.width(label) > room) {
                label = font.plainSubstrByWidth(label, room - font.width("…")) + "…";
            }
            Component text = row.named ? Component.literal(label).withStyle(ChatFormatting.ITALIC) : Component.literal(label);
            int labelEnd = labelX + font.width(text);
            graphics.drawString(font, text, labelX, y + 5, INK, false);
            // Dotted leaders from the title to the count, as in an old catalogue.
            for (int x = labelEnd + 3; x < countX - 3; x += 3) {
                graphics.fill(x, y + 12, x + 1, y + 13, FADED_INK);
            }
        }
        graphics.disableScissor();
    }

    private void renderScrollBar(GuiGraphics graphics) {
        int max = maxScroll();
        if (max == 0) {
            return;
        }
        int x = scrollX();
        int trackHeight = listBottom() - listTop;
        graphics.vLine(x + SCROLL_WIDTH / 2, listTop - 1, listBottom(), FADED_INK);
        int thumbHeight = Math.max(MIN_THUMB, trackHeight * visibleRows / shown.size());
        int thumbY = listTop + (trackHeight - thumbHeight) * scroll / max;
        graphics.blitSprite(SCROLL_THUMB, x, thumbY, SCROLL_WIDTH, thumbHeight);
    }

    private void renderFooter(GuiGraphics graphics) {
        Component footer = truncated
                ? Component.translatable("gui.spectral_enchantments.enchanting_index.truncated", EnchantingIndex.MAX_BOOKS)
                : Component.translatable("gui.spectral_enchantments.enchanting_index.footer", books, rows.size());
        graphics.drawString(font, footer, left + (PAGE_WIDTH - font.width(footer)) / 2, top + pageHeight - 23, FADED_INK, false);
    }

    private void renderRowTooltip(GuiGraphics graphics, Row row, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, row.book));
        int copies = row.locations.size();
        lines.add(Component.translatable(copies == 1 ? "gui.spectral_enchantments.enchanting_index.shelved.one"
                : "gui.spectral_enchantments.enchanting_index.shelved.many", copies).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("gui.spectral_enchantments.enchanting_index.hint").withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY, row.book);
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (renameBox != null) {
            if (renameBox.isMouseOver(mouseX, mouseY)) {
                return renameBox.mouseClicked(mouseX, mouseY, button);
            }
            stopRenaming();
            return true;
        }
        int tile = tileAt(mouseX, mouseY);
        if (tile >= 0 && button == 0) {
            selectedFilters ^= Filter.values()[tile].bit();
            refilter();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            return true;
        }
        int x = scrollX();
        if (button == 0 && maxScroll() > 0 && mouseX >= x - 1 && mouseX < x + SCROLL_WIDTH + 1
                && mouseY >= listTop && mouseY < listBottom()) {
            draggingScroll = true;
            scrollTo(mouseY);
            return true;
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0) {
            Row row = shown.get(index);
            if (button == 0) {
                pick(row);
                return true;
            }
            if (button == 1) {
                startRenaming(row, index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroll) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (renameBox != null) {
            return true;
        }
        scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    private void scrollTo(double mouseY) {
        int trackHeight = listBottom() - listTop;
        int thumbHeight = Math.max(MIN_THUMB, trackHeight * visibleRows / Math.max(1, shown.size()));
        double fraction = (mouseY - listTop - thumbHeight / 2.0) / Math.max(1, trackHeight - thumbHeight);
        scroll = Math.clamp(Math.round(fraction * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renameBox != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishRenaming();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                stopRenaming();
            } else {
                renameBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Closes the page and lights up every copy of the row's book. */
    private void pick(Row row) {
        EnchantingIndexClient.mark(row.locations);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f));
        if (minecraft.player != null) {
            int copies = row.locations.size();
            minecraft.player.displayClientMessage(Component.translatable(copies == 1
                    ? "gui.spectral_enchantments.enchanting_index.marked.one"
                    : "gui.spectral_enchantments.enchanting_index.marked.many", copies), true);
        }
        onClose();
    }

    // ---- Renaming ----

    private void startRenaming(Row row, int index) {
        int y = listTop + (index - scroll) * ROW_HEIGHT;
        String count = "×" + row.locations.size();
        int x = listLeft + 20;
        int countX = listLeft + LIST_WIDTH - 2 - font.width(count);
        renameBox = new EditBox(font, x, y + 5, countX - 4 - x, 10, Component.translatable("gui.spectral_enchantments.enchanting_index.rename"));
        renameBox.setBordered(false);
        renameBox.setTextColor(INK);
        renameBox.setTextShadow(false);
        renameBox.setMaxLength(EnchantingIndex.MAX_NAME_LENGTH);
        renameBox.setValue(row.named ? row.label : "");
        renaming = row;
        addWidget(renameBox);
        setFocused(renameBox);
        renameBox.setFocused(true);
    }

    private void stopRenaming() {
        if (renameBox != null) {
            removeWidget(renameBox);
        }
        renameBox = null;
        renaming = null;
        setFocused(searchBox);
    }

    /**
     * Sends the new name for every copy in the row and updates the page at once without waiting on the server; a
     * blank name takes the custom name off. The row joins any other that now holds the very same book.
     */
    private void finishRenaming() {
        Row row = renaming;
        String name = renameBox.getValue().strip();
        stopRenaming();
        if (row == null || name.equals(row.named ? row.label : "")) {
            return;
        }
        PacketDistributor.sendToServer(new EnchantingIndexRenamePayload(List.copyOf(row.locations), row.book, name));
        ItemStack renamed = row.book.copy();
        if (name.isEmpty()) {
            renamed.remove(DataComponents.CUSTOM_NAME);
        } else {
            renamed.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        row.book = renamed;
        describe(row);
        for (Row other : rows) {
            if (other != row && ItemStack.isSameItemSameComponents(other.book, row.book)) {
                other.locations.addAll(row.locations);
                rows.remove(row);
                break;
            }
        }
        sortRows();
        refilter();
    }
}
