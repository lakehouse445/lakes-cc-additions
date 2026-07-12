package gg.lakehouse.scanner;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The hand-drawn ink layer on a printed page: 172x209 pixels (the full page,
 * margins included; the text area begins at pixel 13,11), 2 bits per pixel.
 * 0 = nothing, 1 = dark ink, 2 = red ink, 3 = blue ink.
 * Stored per page as a byte array under "LakeDrawing<page>" on the item.
 */
public final class DrawingData {
    public static final int WIDTH = 172;   // full page width, margins drawable
    public static final int HEIGHT = 209;  // full page height
    public static final int BYTES = (WIDTH * HEIGHT + 3) / 4; // 2bpp

    /** ARGB colours for ink values 1..3 (index 0 unused). */
    public static final int[] COLOURS = { 0, 0xFF141414, 0xFFB02E26, 0xFF3C44AA };

    private DrawingData() {}

    public static String key(int page) {
        return "LakeDrawing" + page;
    }

    @Nullable
    public static byte[] get(ItemStack stack, int page) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key(page))) return null;
        byte[] data = tag.getByteArray(key(page));
        return data.length == BYTES ? data : null;
    }

    /** Writes the layer; removes the tag entirely if the layer is blank. */
    public static void set(ItemStack stack, int page, byte @Nullable [] data) {
        boolean blank = data == null;
        if (!blank) {
            blank = true;
            for (byte b : data) if (b != 0) { blank = false; break; }
        }
        if (blank) {
            if (stack.getTag() != null) stack.getTag().remove(key(page));
        } else {
            stack.getOrCreateTag().putByteArray(key(page), data);
        }
    }

    public static int getPixel(byte[] data, int x, int y) {
        int i = y * WIDTH + x;
        return (data[i >> 2] >> ((i & 3) * 2)) & 3;
    }

    public static void setPixel(byte[] data, int x, int y, int value) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) return;
        int i = y * WIDTH + x;
        int shift = (i & 3) * 2;
        data[i >> 2] = (byte) ((data[i >> 2] & ~(3 << shift)) | ((value & 3) << shift));
    }
}
