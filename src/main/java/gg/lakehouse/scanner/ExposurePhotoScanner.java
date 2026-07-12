package gg.lakehouse.scanner;

import dan200.computercraft.api.lua.LuaException;
import io.github.mortuusars.exposure.ExposureServer;
import io.github.mortuusars.exposure.data.ColorPalette;
import io.github.mortuusars.exposure.data.ColorPalettes;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import io.github.mortuusars.exposure.world.item.StackedPhotographsItem;
import io.github.mortuusars.exposure.world.item.util.ItemAndStack;
import io.github.mortuusars.exposure.world.level.storage.ExposureData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposure mod integration, targeting the 1.20.1-1.9-backport line
 * (Exposure 1.9.x for MC 1.20.1 -- the backported 2.0 rewrite).
 * Exposures live in ExposureRepository flat files; pixels are indices into
 * a datapack ColorPalette (256 ARGB colours, index 255 transparent);
 * photograph items carry a Frame (identifier + film type + photographer).
 *
 * Loaded ONLY when Exposure is installed (guarded in ScannerPeripheral).
 */
final class ExposurePhotoScanner {
    private static final int PAGE_COLS = 25;
    private static final int PAGE_LINES = 21;

    static boolean isPhotograph(ItemStack stack) {
        return stack.getItem() instanceof PhotographItem
            || stack.getItem() instanceof StackedPhotographsItem;
    }

    private static Frame getFrame(ItemStack stack, int index) throws LuaException {
        if (stack.getItem() instanceof StackedPhotographsItem stacked) {
            List<ItemAndStack<PhotographItem>> photos = stacked.getPhotographs(stack);
            if (photos.isEmpty()) throw new LuaException("Photograph stack is empty");
            if (index < 1 || index > photos.size()) {
                throw new LuaException("Photo index out of range (stack has " + photos.size() + ")");
            }
            var photo = photos.get(index - 1);
            return photo.getItem().getFrame(photo.getItemStack());
        }
        if (stack.getItem() instanceof PhotographItem photograph) {
            return photograph.getFrame(stack);
        }
        throw new LuaException("Item is not an Exposure photograph");
    }

    private static ExposureData getData(Frame frame) throws LuaException {
        if (frame.identifier().isEmpty()) throw new LuaException("Photograph has no exposure data");
        String id = frame.identifier().getId().orElse(null);
        if (id == null) {
            throw new LuaException("Photograph uses a file texture; there is no pixel data to scan");
        }
        return ExposureServer.exposureRepository().load(id).getData()
            .orElseThrow(() -> new LuaException("Exposure data not found on server"));
    }

    private static int[] resolveRgb(Level level, ExposureData data, int transparentAs) {
        ColorPalette palette = ColorPalettes.get(level.registryAccess(), data.getPaletteId()).value();
        int[] colors = palette.colors();
        byte[] pixels = data.getPixels();
        int[] rgb = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int argb = colors[pixels[i] & 0xFF];
            rgb[i] = (argb >>> 24) == 0 ? transparentAs : (argb & 0xFFFFFF);
        }
        return rgb;
    }

    static Map<String, Object> scanMeta(ItemStack stack, int index, Level level) throws LuaException {
        Frame frame = getFrame(stack, index);
        ExposureData data = getData(frame);

        Map<String, Object> out = new HashMap<>();
        out.put("id", frame.identifier().getId().orElse(""));
        out.put("width", data.getWidth());
        out.put("height", data.getHeight());
        out.put("type", frame.type().getSerializedName());
        out.put("photographer", frame.photographer().name());
        out.put("palette", data.getPaletteId().toString());
        out.put("timestamp", data.getTag().unixTimestamp());
        out.put("wasPrinted", data.getTag().wasPrinted());
        if (stack.getItem() instanceof StackedPhotographsItem stacked) {
            out.put("stackSize", stacked.getPhotographs(stack).size());
        }
        return out;
    }

    static Map<String, Object> scanForMonitor(ItemStack stack, int index, Level level) throws LuaException {
        ExposureData data = getData(getFrame(stack, index));
        int[] rgb = resolveRgb(level, data, 0x000000);

        Map<Integer, Integer> counts = new HashMap<>();
        for (int c : rgb) counts.merge(c, 1, Integer::sum);
        List<Integer> palette = counts.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(16)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        while (palette.size() < 16) palette.add(0x000000);

        return ScannerPeripheral.blitResult(rgb, data.getWidth(), data.getHeight(), palette);
    }

    /**
     * Prepress pipeline: downscale -> grey -> auto-contrast -> brightness/
     * contrast -> unsharp -> multi-tone dither -> teletext pages w/ colours.
     * Options: pagesWide/pagesTall (1-6), autoContrast(true), brightness(0),
     * contrast(1.0), sharpen(0.6), dither(atkinson|floyd|threshold),
     * threshold(128), invert(false), tones(2-4).
     * Returns { width, height, pagesWide, pagesTall,
     *           pages = { { x, y, cols, lineCount, lines, colors } } }
     */
    static Map<String, Object> scanForPrint(ItemStack stack, int index, Level level,
                                            Map<?, ?> opts) throws LuaException {
        Frame frame = getFrame(stack, index);
        ExposureData data = getData(frame);
        int srcW = data.getWidth(), srcH = data.getHeight();
        if (srcW == 0 || srcH == 0) throw new LuaException("Photo is empty");
        int[] rgb = resolveRgb(level, data, 0xFFFFFF);

        int pagesWide = clampInt(optNum(opts, "pagesWide", 1), 1, 6);
        int pagesTall = clampInt(optNum(opts, "pagesTall", 1), 1, 6);
        boolean autoContrast = optBool(opts, "autoContrast", true);
        double brightness = optNum(opts, "brightness", 0);
        double contrast = optNum(opts, "contrast", 1.0);
        double sharpen = optNum(opts, "sharpen", 0.6);
        String ditherMode = optStr(opts, "dither", "atkinson");
        double threshold = optNum(opts, "threshold", 128);
        boolean invert = optBool(opts, "invert", false);
        int tones = clampInt(optNum(opts, "tones", 2), 2, 4);

        int maxW = PAGE_COLS * 2 * pagesWide;
        int maxH = PAGE_LINES * 3 * pagesTall;
        double scale = Math.min((double) maxW / srcW, (double) maxH / srcH);
        int w = Math.max(2, (int) Math.floor(srcW * scale));
        int h = Math.max(3, (int) Math.floor(srcH * scale));
        w -= w % 2;
        h -= h % 3;

        // Box-average downscale to greyscale
        double[] grey = new double[w * h];
        for (int y = 0; y < h; y++) {
            int sy0 = y * srcH / h, sy1 = Math.max(sy0 + 1, (y + 1) * srcH / h);
            for (int x = 0; x < w; x++) {
                int sx0 = x * srcW / w, sx1 = Math.max(sx0 + 1, (x + 1) * srcW / w);
                double sum = 0;
                int n = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    for (int sx = sx0; sx < sx1; sx++) {
                        int c = rgb[sx + sy * srcW];
                        sum += 0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF);
                        n++;
                    }
                }
                grey[x + y * w] = sum / n;
            }
        }

        // Auto-contrast: stretch 2nd..98th percentile
        if (autoContrast) {
            double[] sorted = grey.clone();
            java.util.Arrays.sort(sorted);
            double lo = sorted[(int) (sorted.length * 0.02)];
            double hi = sorted[(int) (sorted.length * 0.98) - 1];
            if (hi - lo > 1) {
                for (int i = 0; i < grey.length; i++) grey[i] = (grey[i] - lo) * 255.0 / (hi - lo);
            }
        }

        if (brightness != 0 || contrast != 1.0) {
            for (int i = 0; i < grey.length; i++) grey[i] = (grey[i] - 128) * contrast + 128 + brightness;
        }

        // Unsharp mask
        if (sharpen > 0) {
            double[] blur = new double[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double sum = 0;
                    int n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                                sum += grey[nx + ny * w];
                                n++;
                            }
                        }
                    }
                    blur[x + y * w] = sum / n;
                }
            }
            for (int i = 0; i < grey.length; i++) grey[i] = grey[i] + sharpen * (grey[i] - blur[i]);
        }

        for (int i = 0; i < grey.length; i++) {
            double g = Math.max(0, Math.min(255, grey[i]));
            grey[i] = invert ? 255 - g : g;
        }

        // Multi-tone dither. Tone 0 = darkest ink ... tones-1 = paper.
        double step = 255.0 / (tones - 1);
        int[] tone = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = x + y * w;
                boolean deadPixel = (x % 2 == 1) && (y % 3 == 2);
                double old = grey[i];
                double val;
                if (deadPixel) {
                    tone[i] = tones - 1;
                    val = 255;
                } else if (tones == 2) {
                    tone[i] = old < threshold ? 0 : 1;
                    val = tone[i] == 0 ? 0 : 255;
                } else {
                    int t = (int) Math.round(Math.max(0, Math.min(255, old)) / step);
                    tone[i] = t;
                    val = t * step;
                }
                double err = old - val;

                switch (ditherMode) {
                    case "floyd" -> {
                        if (x + 1 < w) grey[i + 1] += err * 7 / 16;
                        if (y + 1 < h) {
                            if (x > 0) grey[i + w - 1] += err * 3 / 16;
                            grey[i + w] += err * 5 / 16;
                            if (x + 1 < w) grey[i + w + 1] += err * 1 / 16;
                        }
                    }
                    case "threshold" -> { }
                    default -> { // atkinson
                        double e = err / 8;
                        if (x + 1 < w) grey[i + 1] += e;
                        if (x + 2 < w) grey[i + 2] += e;
                        if (y + 1 < h) {
                            if (x > 0) grey[i + w - 1] += e;
                            grey[i + w] += e;
                            if (x + 1 < w) grey[i + w + 1] += e;
                        }
                        if (y + 2 < h) grey[i + 2 * w] += e;
                    }
                }
            }
        }

        char[] toneColour = switch (tones) {
            case 3 -> new char[]{'f', '7'};
            case 4 -> new char[]{'f', '7', '8'};
            default -> new char[]{'f'};
        };
        int white = tones - 1;

        int totalCols = w / 2, totalLines = h / 3;
        List<Map<String, Object>> pages = new ArrayList<>();
        for (int py = 0; py < pagesTall; py++) {
            for (int px = 0; px < pagesWide; px++) {
                int col0 = px * PAGE_COLS, line0 = py * PAGE_LINES;
                if (col0 >= totalCols || line0 >= totalLines) continue;
                int cols = Math.min(PAGE_COLS, totalCols - col0);
                int lineCount = Math.min(PAGE_LINES, totalLines - line0);

                Map<Integer, String> lineTable = new HashMap<>();
                Map<Integer, String> colourTable = new HashMap<>();
                byte[] line = new byte[cols];
                char[] colourLine = new char[cols];
                for (int cy = 0; cy < lineCount; cy++) {
                    for (int cx = 0; cx < cols; cx++) {
                        int gx = (col0 + cx) * 2, gy = (line0 + cy) * 3;
                        int bits = 0, toneSum = 0, inkCount = 0;
                        int[] cell = {
                            gx + gy * w, gx + 1 + gy * w,
                            gx + (gy + 1) * w, gx + 1 + (gy + 1) * w,
                            gx + (gy + 2) * w
                        };
                        int[] bit = {1, 2, 4, 8, 16};
                        for (int s = 0; s < 5; s++) {
                            int t = tone[cell[s]];
                            if (t < white) {
                                bits |= bit[s];
                                toneSum += t;
                                inkCount++;
                            }
                        }
                        line[cx] = (byte) (128 + bits);
                        int avgTone = inkCount == 0 ? 0
                            : Math.min(toneColour.length - 1, Math.round((float) toneSum / inkCount));
                        colourLine[cx] = toneColour[avgTone];
                    }
                    lineTable.put(cy + 1, new String(line, 0, cols, StandardCharsets.ISO_8859_1));
                    colourTable.put(cy + 1, new String(colourLine, 0, cols));
                }

                Map<String, Object> page = new HashMap<>();
                page.put("x", px + 1);
                page.put("y", py + 1);
                page.put("cols", cols);
                page.put("lineCount", lineCount);
                page.put("lines", lineTable);
                page.put("colors", colourTable);
                pages.add(page);
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("width", w);
        out.put("height", h);
        out.put("pagesWide", pagesWide);
        out.put("pagesTall", pagesTall);
        out.put("pages", pages);
        return out;
    }

    private static double optNum(Map<?, ?> opts, String key, double def) {
        Object v = opts.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    private static int clampInt(double v, int min, int max) {
        return Math.max(min, Math.min(max, (int) v));
    }

    private static boolean optBool(Map<?, ?> opts, String key, boolean def) {
        Object v = opts.get(key);
        return v instanceof Boolean b ? b : def;
    }

    private static String optStr(Map<?, ?> opts, String key, String def) {
        Object v = opts.get(key);
        return v instanceof String s ? s : def;
    }

    private ExposurePhotoScanner() {}
}
