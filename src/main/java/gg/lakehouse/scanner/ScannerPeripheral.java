package gg.lakehouse.scanner;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ScannerPeripheral implements IPeripheral {
    // CC:T printout constants (see PrintoutItem in CC:T source)
    private static final int LINES_PER_PAGE = 21;
    private static final int LINE_MAX_LENGTH = 25;

    /**
     * Fixed 16-colour palette for atlas tiles, so tiles from different maps
     * share one palette and can be composed on a single monitor.
     */
    private static final int[] ATLAS_PALETTE = {
        0x000000, // 1  void / unexplored
        0xFFFFFF, // 2  snow / white
        0x7FB238, // 3  grass
        0x007C00, // 4  foliage / trees
        0x4040FF, // 5  water
        0x3030B0, // 6  deep water
        0xF7E9A3, // 7  sand
        0x976D4D, // 8  dirt
        0x8F7748, // 9  wood / planks
        0x707070, // 10 stone
        0x969696, // 11 gravel / light gray
        0x4C4C4C, // 12 deepslate / dark gray
        0xD87F33, // 13 orange (terracotta, pumpkin)
        0x993333, // 14 red (netherrack, redstone)
        0xE5E533, // 15 yellow
        0xA0A0FF, // 16 ice
    };

    private final ScannerBlockEntity blockEntity;

    public ScannerPeripheral(ScannerBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "scanner";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ScannerPeripheral o && blockEntity == o.blockEntity;
    }

    @Override
    public void attach(IComputerAccess computer) {
        blockEntity.computers.add(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        blockEntity.computers.remove(computer);
    }

    // ------------------------------------------------------------------
    // Basic item inspection
    // ------------------------------------------------------------------

    @LuaFunction(mainThread = true)
    public final boolean hasItem() {
        return !blockEntity.getScannedItem().isEmpty();
    }

    @LuaFunction(mainThread = true)
    public final Object getItem() {
        ItemStack stack = blockEntity.getScannedItem();
        if (stack.isEmpty()) return null;

        Map<String, Object> out = new HashMap<>();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        out.put("name", id.toString());
        out.put("count", stack.getCount());
        out.put("displayName", stack.getHoverName().getString());
        if (stack.isDamageableItem()) {
            out.put("damage", stack.getDamageValue());
            out.put("maxDamage", stack.getMaxDamage());
        }
        return out;
    }

    /**
     * Scan ANY item and return its full NBT as a Lua table.
     * Compounds become string-keyed tables, lists become 1-indexed arrays.
     * Returns { name, count, nbt } where nbt may be nil for NBT-less items.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanNBT() throws LuaException {
        ItemStack stack = requireItem();
        Map<String, Object> out = new HashMap<>();
        out.put("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        out.put("count", stack.getCount());
        CompoundTag tag = stack.getTag();
        if (tag != null) out.put("nbt", tagToLua(tag));
        return out;
    }

    /**
     * A deterministic SHA-256 fingerprint of the item's id + NBT.
     * Two items with identical type and data produce identical fingerprints;
     * useful for verifying document/contract authenticity from Lua.
     * (Count is deliberately excluded.)
     */
    @LuaFunction(mainThread = true)
    public final String fingerprint() throws LuaException {
        ItemStack stack = requireItem();
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        CompoundTag tag = stack.getTag();
        String payload = id + "|" + (tag == null ? "" : tag.toString());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new LuaException("Hashing failed: " + e.getMessage());
        }
    }

    /**
     * Scan a container item's contents without opening it (shulker boxes and
     * anything else storing BlockEntityTag.Items). Customs approved.
     * Returns a list of { slot, name, count, hasNbt, nested } where nested
     * means the slot holds another container with contents.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> scanContainer() throws LuaException {
        ItemStack stack = requireItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        CompoundTag tag = stack.getTag();
        CompoundTag bet = tag != null && tag.contains("BlockEntityTag")
            ? tag.getCompound("BlockEntityTag") : null;

        if (bet == null || !bet.contains("Items")) {
            // An empty shulker box is a valid (empty) container; anything else isn't one
            if (id.getPath().endsWith("shulker_box")) return new ArrayList<>();
            throw new LuaException("Item has no container contents");
        }

        ListTag items = bet.getList("Items", Tag.TAG_COMPOUND);
        List<Map<String, Object>> out = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            Map<String, Object> entry = new HashMap<>();
            entry.put("slot", item.getByte("Slot") + 1);
            entry.put("name", item.getString("id"));
            entry.put("count", (int) item.getByte("Count"));
            CompoundTag itemTag = item.contains("tag") ? item.getCompound("tag") : null;
            entry.put("hasNbt", itemTag != null);
            entry.put("nested", itemTag != null
                && itemTag.contains("BlockEntityTag")
                && itemTag.getCompound("BlockEntityTag").contains("Items"));
            out.add(entry);
        }
        return out;
    }

    /**
     * SHA-256 of an arbitrary string, as lowercase hex. General-purpose
     * hashing for Lua: signatures, keyed document seals, citizen numbers.
     * (Pure CPU -- safe off the main thread.)
     */
    @LuaFunction
    public final String sha256(String text) throws LuaException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new LuaException("Hashing failed: " + e.getMessage());
        }
    }

    /**
     * List a folder's contents: { slot, name, count, type, title?, fingerprint }
     * per occupied slot. type is "page"/"pages"/"book"/"paper". Fingerprints
     * enable mass archiving and duplicate detection without opening anything.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> scanFolder() throws LuaException {
        ItemStack stack = requireItem();
        if (!(stack.getItem() instanceof FolderItem)) throw new LuaException("Item is not a folder");

        CompoundTag tag = stack.getTag();
        ListTag items = tag != null ? tag.getList("Items", Tag.TAG_COMPOUND) : new ListTag();
        List<Map<String, Object>> out = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            ItemStack doc = ItemStack.of(entry);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(doc.getItem());
            Map<String, Object> e = new HashMap<>();
            e.put("slot", entry.getByte("Slot") + 1);
            e.put("name", id.toString());
            e.put("count", doc.getCount());
            String type = switch (id.getPath()) {
                case "printed_page" -> "page";
                case "printed_pages" -> "pages";
                case "printed_book" -> "book";
                case "paper" -> "paper";
                default -> id.getPath();
            };
            e.put("type", type);
            CompoundTag docTag = doc.getTag();
            if (docTag != null && docTag.contains("Title")) e.put("title", docTag.getString("Title"));
            String payload = id + "|" + (docTag == null ? "" : docTag.toString());
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(64);
                for (byte b : hash) sb.append(String.format("%02x", b));
                e.put("fingerprint", sb.toString());
            } catch (Exception ignored) { }
            out.add(e);
        }
        return out;
    }

    /** Remove N plain paper from a folder's contents, or error without changing it. */
    private static void consumeFolderPaper(ItemStack folder, int needed) throws LuaException {
        CompoundTag tag = folder.getOrCreateTag();
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);

        int available = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = ItemStack.of(items.getCompound(i));
            if (s.is(net.minecraft.world.item.Items.PAPER)) available += s.getCount();
        }
        if (available < needed) {
            throw new LuaException("Not enough paper in the folder (need " + needed
                + ", found " + available + ")");
        }

        ListTag out = new ListTag();
        int remaining = needed;
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            ItemStack s = ItemStack.of(entry);
            if (remaining > 0 && s.is(net.minecraft.world.item.Items.PAPER)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
                if (s.isEmpty()) continue;
                CompoundTag e = new CompoundTag();
                e.putByte("Slot", entry.getByte("Slot"));
                s.save(e);
                out.add(e);
            } else {
                out.add(entry);
            }
        }
        tag.put("Items", out);
    }

    /** File a document into the first free slot of a folder's NBT. */
    private static void insertIntoFolder(ItemStack folder, ItemStack doc) throws LuaException {
        CompoundTag tag = folder.getOrCreateTag();
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        boolean[] used = new boolean[FolderItem.SLOTS];
        for (int i = 0; i < items.size(); i++) used[items.getCompound(i).getByte("Slot")] = true;
        int free = -1;
        for (int i = 0; i < FolderItem.SLOTS; i++) if (!used[i]) { free = i; break; }
        if (free < 0) throw new LuaException("Folder is full");
        CompoundTag entry = new CompoundTag();
        entry.putByte("Slot", (byte) free);
        doc.save(entry);
        items.add(entry);
        tag.put("Items", items);
    }

    /**
     * Count drawn ink pixels inside a rectangle of a printout's drawing
     * layer. Coordinates are 0-based page pixels (the text area starts at
     * 13, 11; a character cell is 6x9). Returns { total, dark, red, blue }.
     * Optional page (default 1) and folder slot to reach into a folder.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> countInk(int x, int y, int w, int h,
                                              Optional<Integer> page,
                                              Optional<Integer> folderSlot) throws LuaException {
        ItemStack stack = resolveDocument(folderSlot);
        if (!stack.hasTag()) return inkResult(0, 0, 0);
        int pageIndex = page.orElse(1) - 1;
        byte[] layer = DrawingData.get(stack, pageIndex);
        if (layer == null) return inkResult(0, 0, 0);

        int dark = 0, red = 0, blue = 0;
        int x1 = Math.max(0, x), y1 = Math.max(0, y);
        int x2 = Math.min(DrawingData.WIDTH, x + w), y2 = Math.min(DrawingData.HEIGHT, y + h);
        for (int py = y1; py < y2; py++) {
            for (int px = x1; px < x2; px++) {
                switch (DrawingData.getPixel(layer, px, py)) {
                    case 1 -> dark++;
                    case 2 -> red++;
                    case 3 -> blue++;
                }
            }
        }
        return inkResult(dark, red, blue);
    }

    private static Map<String, Object> inkResult(int dark, int red, int blue) {
        Map<String, Object> out = new HashMap<>();
        out.put("total", dark + red + blue);
        out.put("dark", dark);
        out.put("red", red);
        out.put("blue", blue);
        return out;
    }

    /**
     * Read the emblem off a stamp in the scanner: { size, rows } with rows
     * as strings of 0-3, same encoding as drawing data. Lets a computer
     * verify a seal is THE seal, not just that ink exists.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanStamp() throws LuaException {
        ItemStack stack = requireItem();
        if (!(stack.getItem() instanceof gg.lakehouse.scanner.StampItem)) {
            throw new LuaException("Item is not a stamp");
        }
        byte[] emblem = gg.lakehouse.scanner.StampItem.getEmblem(stack);
        if (emblem == null) throw new LuaException("Stamp has no emblem");

        Map<Integer, String> rows = new HashMap<>();
        StringBuilder row = new StringBuilder(gg.lakehouse.scanner.StampItem.SIZE);
        for (int y = 0; y < gg.lakehouse.scanner.StampItem.SIZE; y++) {
            row.setLength(0);
            for (int x = 0; x < gg.lakehouse.scanner.StampItem.SIZE; x++) {
                row.append((char) ('0' + gg.lakehouse.scanner.StampItem.pixel(emblem, x, y)));
            }
            rows.put(y + 1, row.toString());
        }
        Map<String, Object> out = new HashMap<>();
        out.put("size", gg.lakehouse.scanner.StampItem.SIZE);
        out.put("rows", rows);
        return out;
    }

    // ------------------------------------------------------------------
    // Printout scanning
    // ------------------------------------------------------------------

    /**
     * Scan a CC:T printed page / pages / book. Returns blit-ready lines:
     * { type, title, pageCount, width, height,
     *   pages = { { text = {21 lines}, color = {21 blit strings} }, ... } }
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanPrintout(Optional<Integer> folderSlot) throws LuaException {
        return printoutToTable(resolveDocument(folderSlot));
    }

    /** Resolve the target: the slot item, or a document inside a folder in the slot. */
    private ItemStack resolveDocument(Optional<Integer> folderSlot) throws LuaException {
        ItemStack stack = requireItem();
        if (folderSlot.isEmpty()) return stack;
        if (!(stack.getItem() instanceof FolderItem)) {
            throw new LuaException("Slot index given but the item is not a folder");
        }
        int slot = folderSlot.get();
        if (slot < 1 || slot > FolderItem.SLOTS) throw new LuaException("Folder slot out of range (1-27)");
        CompoundTag tag = stack.getTag();
        ListTag items = tag != null ? tag.getList("Items", Tag.TAG_COMPOUND) : new ListTag();
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            if (entry.getByte("Slot") == slot - 1) return ItemStack.of(entry);
        }
        throw new LuaException("Folder slot " + slot + " is empty");
    }

    private Map<String, Object> printoutToTable(ItemStack stack) throws LuaException {
        
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"computercraft".equals(id.getNamespace()) || !id.getPath().startsWith("printed_")) {
            throw new LuaException("Item is not a printout");
        }
        String type = switch (id.getPath()) {
            case "printed_page" -> "page";
            case "printed_pages" -> "pages";
            case "printed_book" -> "book";
            default -> id.getPath();
        };

        CompoundTag nbt = stack.getTag();
        String title = nbt != null && nbt.contains("Title") ? nbt.getString("Title") : "";
        int pageCount = nbt != null && nbt.contains("Pages") ? nbt.getInt("Pages") : 1;

        List<Map<String, Object>> pages = new ArrayList<>(pageCount);
        for (int page = 0; page < pageCount; page++) {
            Map<Integer, String> text = new HashMap<>();
            Map<Integer, String> color = new HashMap<>();
            for (int line = 0; line < LINES_PER_PAGE; line++) {
                int i = page * LINES_PER_PAGE + line;
                text.put(line + 1, padLine(nbt != null ? nbt.getString("Text" + i) : ""));
                color.put(line + 1, padColour(nbt != null ? nbt.getString("Color" + i) : ""));
            }
            Map<String, Object> pageTable = new HashMap<>();
            pageTable.put("text", text);
            pageTable.put("color", color);
            byte[] layer = DrawingData.get(stack, page);
            if (layer != null) {
                Map<Integer, String> drawing = new HashMap<>();
                StringBuilder row = new StringBuilder(DrawingData.WIDTH);
                for (int y = 0; y < DrawingData.HEIGHT; y++) {
                    row.setLength(0);
                    for (int x = 0; x < DrawingData.WIDTH; x++) {
                        row.append((char) ('0' + DrawingData.getPixel(layer, x, y)));
                    }
                    drawing.put(y + 1, row.toString());
                }
                pageTable.put("drawing", drawing);
            }
            pages.add(pageTable);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("type", type);
        out.put("title", title);
        out.put("pageCount", pageCount);
        out.put("pages", pages);
        out.put("width", LINE_MAX_LENGTH);
        out.put("height", LINES_PER_PAGE);
        return out;
    }

    /**
     * CREATE a printout item in the scanner's (empty) slot.
     * pages: list of { text = { up to 21 strings, <=25 chars },
     *                  color = { matching blit colour strings } }
     * (the exact shape scanPrintout returns -- scan + create = photocopier).
     * Supports per-character colours (how greyscale photo prints work).
     * Does not consume paper or ink.
     */
    @LuaFunction(mainThread = true)
    public final void createPrintout(Map<?, ?> pagesArg, Optional<String> title) throws LuaException {
        ItemStack existing = blockEntity.getScannedItem();
        if (!existing.isEmpty() && !(existing.getItem() instanceof FolderItem)
            && !existing.is(net.minecraft.world.item.Items.PAPER)) {
            throw new LuaException("Scanner slot must be empty (or hold a folder to file into)");
        }
        if (existing.is(net.minecraft.world.item.Items.PAPER)
            && !gg.lakehouse.scanner.ScannerConfig.REQUIRE_PRINTOUT_MATERIALS.get()) {
            throw new LuaException("Scanner slot must be empty (or hold a folder to file into)");
        }
        List<Map<?, ?>> pages = new ArrayList<>();
        for (int i = 1; pagesArg.get((double) i) instanceof Map<?, ?> page; i++) pages.add(page);
        if (pages.isEmpty()) throw new LuaException("No pages given");
        if (pages.size() > 16) throw new LuaException("Too many pages (max 16)");

        String itemName = pages.size() == 1 ? "printed_page" : "printed_pages";
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(
            new ResourceLocation("computercraft", itemName));

        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("Title", title.orElse(""));
        tag.putInt("Pages", pages.size());
        for (int p = 0; p < pages.size(); p++) {
            Object textObj = pages.get(p).get("text");
            Object colorObj = pages.get(p).get("color");
            if (!(textObj instanceof Map<?, ?> text)) throw new LuaException("Page " + (p + 1) + " has no text table");
            Map<?, ?> color = colorObj instanceof Map<?, ?> c ? c : java.util.Collections.emptyMap();
            for (int l = 0; l < LINES_PER_PAGE; l++) {
                Object textLine = text.get((double) (l + 1));
                Object colorLine = color.get((double) (l + 1));
                tag.putString("Text" + (p * LINES_PER_PAGE + l),
                    padLine(textLine instanceof String s ? s : ""));
                tag.putString("Color" + (p * LINES_PER_PAGE + l),
                    padColour(colorLine instanceof String s ? s : ""));
            }
            if (pages.get(p).get("drawing") instanceof Map<?, ?> drawing) {
                byte[] layer = new byte[DrawingData.BYTES];
                boolean any = false;
                for (int y = 0; y < DrawingData.HEIGHT; y++) {
                    Object rowObj = drawing.get((double) (y + 1));
                    if (!(rowObj instanceof String row)) continue;
                    for (int x = 0; x < Math.min(row.length(), DrawingData.WIDTH); x++) {
                        int v = row.charAt(x) - '0';
                        if (v >= 1 && v <= 3) {
                            DrawingData.setPixel(layer, x, y, v);
                            any = true;
                        }
                    }
                }
                if (any) tag.putByteArray(DrawingData.key(p), layer);
            }
        }
        ItemStack inSlot = blockEntity.getScannedItem();
        boolean needPaper = gg.lakehouse.scanner.ScannerConfig.REQUIRE_PRINTOUT_MATERIALS.get();

        if (inSlot.getItem() instanceof FolderItem) {
            if (needPaper) consumeFolderPaper(inSlot, pages.size());
            insertIntoFolder(inSlot, stack);
            blockEntity.setScannedItem(inSlot); // re-set to fire events + persist
        } else if (needPaper) {
            if (!inSlot.is(net.minecraft.world.item.Items.PAPER)
                || inSlot.getCount() != pages.size()) {
                throw new LuaException("Printing requires paper: a folder containing "
                    + pages.size() + "+ paper, or an exact stack of "
                    + pages.size() + " paper in the slot");
            }
            blockEntity.setScannedItem(stack);
        } else {
            blockEntity.setScannedItem(stack);
        }
        if (blockEntity.getLevel() != null) {
            blockEntity.getLevel().playSound(null, blockEntity.getBlockPos(),
                net.minecraft.sounds.SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.0f);
        }
    }

    /**
     * Scan a vanilla book: book & quill (writable_book) or signed written_book.
     * Returns { type = "writable"|"written", pageCount, pages = {strings},
     *           title?, author?, generation? }.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanBook() throws LuaException {
        ItemStack stack = requireItem();
        CompoundTag nbt = stack.getTag();

        Map<String, Object> out = new HashMap<>();
        Map<Integer, String> pages = new HashMap<>();

        if (stack.is(Items.WRITABLE_BOOK)) {
            out.put("type", "writable");
            if (nbt != null) {
                var list = nbt.getList("pages", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) pages.put(i + 1, list.getString(i));
            }
        } else if (stack.is(Items.WRITTEN_BOOK)) {
            out.put("type", "written");
            if (nbt != null) {
                out.put("title", nbt.getString("title"));
                out.put("author", nbt.getString("author"));
                out.put("generation", nbt.getInt("generation"));
                var list = nbt.getList("pages", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    String raw = list.getString(i);
                    // Written book pages are JSON text components
                    String plain;
                    try {
                        Component component = Component.Serializer.fromJson(raw);
                        plain = component != null ? component.getString() : raw;
                    } catch (Exception e) {
                        plain = raw;
                    }
                    pages.put(i + 1, plain);
                }
            }
        } else {
            throw new LuaException("Item is not a book and quill or written book");
        }

        out.put("pages", pages);
        out.put("pageCount", pages.size());
        return out;
    }

    // ------------------------------------------------------------------
    // Map scanning
    // ------------------------------------------------------------------

    /** Map metadata and decorations (banners, frames, player markers). */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanMap() throws LuaException {
        MapItemSavedData data = getMapData();

        Map<String, Object> out = new HashMap<>();
        out.put("centerX", data.centerX);
        out.put("centerZ", data.centerZ);
        out.put("scale", data.scale);
        out.put("locked", data.locked);
        out.put("dimension", data.dimension.location().toString());

        List<Map<String, Object>> decorations = new ArrayList<>();
        data.getDecorations().forEach(dec -> {
            Map<String, Object> d = new HashMap<>();
            d.put("type", dec.getType().name().toLowerCase());
            d.put("x", (int) dec.getX());   // -128..127 map space
            d.put("y", (int) dec.getY());
            d.put("rotation", (int) dec.getRot());
            if (dec.getName() != null) d.put("name", dec.getName().getString());
            decorations.add(d);
        });
        out.put("decorations", decorations);
        return out;
    }

    /**
     * Raw 128x128 pixel data: 128 binary strings of vanilla packed colour
     * bytes (colourId * 4 + brightness). Row 1 is north, byte 1 is west.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanMapPixels() throws LuaException {
        MapItemSavedData data = getMapData();

        Map<Integer, String> rows = new HashMap<>();
        byte[] colors = data.colors;
        for (int z = 0; z < 128; z++) {
            byte[] row = new byte[128];
            System.arraycopy(colors, z * 128, row, 0, 128);
            rows.put(z + 1, new String(row, StandardCharsets.ISO_8859_1));
        }

        Map<String, Object> out = new HashMap<>();
        out.put("width", 128);
        out.put("height", 128);
        out.put("rows", rows);
        return out;
    }

    /**
     * Monitor-ready scan: per-map popularity quantisation (best colours for
     * THIS map). Not suitable for multi-map atlases; use scanMapTile for that.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanMapForMonitor() throws LuaException {
        MapItemSavedData data = getMapData();
        int[] rgb = resolveRgb(data.colors);

        // Popularity quantisation: the 16 most common colours in this map
        Map<Integer, Integer> counts = new HashMap<>();
        for (int c : rgb) counts.merge(c, 1, Integer::sum);
        List<Integer> palette = counts.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(16)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        while (palette.size() < 16) palette.add(0x000000);

        return blitResult(rgb, 128, 128, palette);
    }

    /**
     * Atlas tile scan: quantises against a FIXED shared palette so tiles from
     * different maps compose seamlessly on one monitor. Includes grid info:
     * gridX/gridZ are the map's tile coordinates in the vanilla map grid at
     * its scale, so adjacent maps get adjacent grid coords automatically.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanMapTile() throws LuaException {
        MapItemSavedData data = getMapData();

        List<Integer> palette = new ArrayList<>(16);
        for (int c : ATLAS_PALETTE) palette.add(c);

        Map<String, Object> out = blitResult(resolveRgb(data.colors), 128, 128, palette);

        int blocksPerMap = 128 * (1 << data.scale);
        out.put("scale", data.scale);
        out.put("centerX", data.centerX);
        out.put("centerZ", data.centerZ);
        out.put("gridX", Math.floorDiv(data.centerX + 64, blocksPerMap));
        out.put("gridZ", Math.floorDiv(data.centerZ + 64, blocksPerMap));
        out.put("dimension", data.dimension.location().toString());
        return out;
    }

    // ------------------------------------------------------------------
    // Exposure mod photo scanning (soft dependency)
    // ------------------------------------------------------------------

    private static boolean exposureLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("exposure");
    }

    private ItemStack requirePhotograph() throws LuaException {
        if (!exposureLoaded()) throw new LuaException("The Exposure mod is not installed");
        if (blockEntity.getLevel() == null) throw new LuaException("Scanner has no level");
        ItemStack stack = requireItem();
        if (!ExposurePhotoScanner.isPhotograph(stack)) {
            throw new LuaException("Item is not an Exposure photograph");
        }
        return stack;
    }

    /**
     * Scan an Exposure photograph's metadata: dimensions, film type,
     * photographer, camera settings. Optional index selects a photo
     * from a stacked_photographs item (1-based, default 1).
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanPhoto(Optional<Integer> index) throws LuaException {
        return ExposurePhotoScanner.scanMeta(requirePhotograph(), index.orElse(1), blockEntity.getLevel());
    }

    /**
     * Monitor-ready photo scan: best-16-colour quantised palette + blit rows,
     * same shape as scanMapForMonitor (but photo-sized, typically 320x320).
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanPhotoForMonitor(Optional<Integer> index) throws LuaException {
        return ExposurePhotoScanner.scanForMonitor(requirePhotograph(), index.orElse(1), blockEntity.getLevel());
    }

    /**
     * Printer-ready photo scan: dithered 1-bit teletext characters that fit
     * a single printed page. Feed the lines straight to a printer.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> scanPhotoForPrint(Optional<Integer> index, Optional<Map<?, ?>> options) throws LuaException {
        return ExposurePhotoScanner.scanForPrint(requirePhotograph(), index.orElse(1),
            blockEntity.getLevel(), options.orElse(java.util.Collections.emptyMap()));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private ItemStack requireItem() throws LuaException {
        ItemStack stack = blockEntity.getScannedItem();
        if (stack.isEmpty()) throw new LuaException("Nothing to scan");
        return stack;
    }

    private MapItemSavedData getMapData() throws LuaException {
        ItemStack stack = requireItem();
        if (!(stack.getItem() instanceof MapItem)) throw new LuaException("Item is not a filled map");
        if (blockEntity.getLevel() == null) throw new LuaException("Scanner has no level");

        MapItemSavedData data = MapItem.getSavedData(stack, blockEntity.getLevel());
        if (data == null) throw new LuaException("Map data is not available (unexplored or foreign map?)");
        return data;
    }

    /** Recursively convert an NBT tag to Lua-convertible objects. */
    private static Object tagToLua(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            Map<String, Object> out = new HashMap<>();
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child != null) out.put(key, tagToLua(child));
            }
            return out;
        }
        if (tag instanceof CollectionTag<?> list) {
            Map<Integer, Object> out = new HashMap<>();
            for (int i = 0; i < list.size(); i++) out.put(i + 1, tagToLua(list.get(i)));
            return out;
        }
        if (tag instanceof NumericTag numeric) {
            // Preserve integer-ness where possible
            double d = numeric.getAsDouble();
            long l = numeric.getAsLong();
            return d == l ? (Object) l : (Object) d;
        }
        if (tag instanceof StringTag string) {
            return string.getAsString();
        }
        return tag.toString();
    }

    /** Resolve packed map colour bytes to an array of 0xRRGGBB. (Also used for Exposure photos.) */
    static int[] resolveRgb(byte[] colors) {
        int[] rgb = new int[colors.length];
        for (int i = 0; i < colors.length; i++) rgb[i] = packedToRgb(colors[i]);
        return rgb;
    }

    /** Quantise pixels against a palette and build the standard blit result table. */
    static Map<String, Object> blitResult(int[] rgb, int width, int height, List<Integer> palette) {
        char[] blitChars = "0123456789abcdef".toCharArray();
        Map<Integer, Character> nearestCache = new HashMap<>();
        Map<Integer, String> rows = new HashMap<>();
        StringBuilder sb = new StringBuilder(width);
        for (int z = 0; z < height; z++) {
            sb.setLength(0);
            for (int x = 0; x < width; x++) {
                int c = rgb[x + z * width];
                Character ch = nearestCache.get(c);
                if (ch == null) {
                    int best = 0;
                    long bestDist = Long.MAX_VALUE;
                    for (int p = 0; p < 16; p++) {
                        long d = rgbDistance(c, palette.get(p));
                        if (d < bestDist) { bestDist = d; best = p; }
                    }
                    ch = blitChars[best];
                    nearestCache.put(c, ch);
                }
                sb.append(ch);
            }
            rows.put(z + 1, sb.toString());
        }

        Map<Integer, Integer> paletteTable = new HashMap<>();
        for (int p = 0; p < 16; p++) paletteTable.put(p + 1, palette.get(p));

        Map<String, Object> out = new HashMap<>();
        out.put("width", width);
        out.put("height", height);
        out.put("palette", paletteTable);
        out.put("rows", rows);
        return out;
    }

    /**
     * Convert a vanilla packed map colour byte (colourId * 4 + brightness) to 0xRRGGBB.
     * Brightness multipliers per vanilla: LOW 180, NORMAL 220, HIGH 255, LOWEST 135.
     */
    private static int packedToRgb(byte packed) {
        int id = (packed & 0xFF) >> 2;
        int brightness = packed & 3;
        MapColor color = MapColor.byId(id);
        if (color == MapColor.NONE) return 0x000000;

        int mult = switch (brightness) {
            case 0 -> 180;
            case 1 -> 220;
            case 2 -> 255;
            default -> 135;
        };
        int base = color.col; // 0xRRGGBB
        int r = ((base >> 16) & 0xFF) * mult / 255;
        int g = ((base >> 8) & 0xFF) * mult / 255;
        int b = (base & 0xFF) * mult / 255;
        return (r << 16) | (g << 8) | b;
    }

    private static long rgbDistance(int a, int b) {
        long dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        long dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        long db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    /** Pad/trim a text line to exactly 25 chars so term.blit is happy. */
    private static String padLine(String s) {
        if (s == null) s = "";
        if (s.length() > LINE_MAX_LENGTH) return s.substring(0, LINE_MAX_LENGTH);
        return s + " ".repeat(LINE_MAX_LENGTH - s.length());
    }

    /** Pad/trim a colour line to 25 chars, defaulting to black text ("f"). */
    private static String padColour(String s) {
        if (s == null) s = "";
        if (s.length() > LINE_MAX_LENGTH) return s.substring(0, LINE_MAX_LENGTH);
        return s + "f".repeat(LINE_MAX_LENGTH - s.length());
    }
}
