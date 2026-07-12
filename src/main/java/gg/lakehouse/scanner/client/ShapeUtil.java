package gg.lakehouse.scanner.client;

/** Pixel rasterizers shared by the pen and stamp editors. */
final class ShapeUtil {
    interface PixelConsumer { void accept(int x, int y); }

    static void line(int x0, int y0, int x1, int y1, PixelConsumer out) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            out.accept(x0, y0);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    static void rect(int x0, int y0, int x1, int y1, boolean fill, PixelConsumer out) {
        int lo_x = Math.min(x0, x1), hi_x = Math.max(x0, x1);
        int lo_y = Math.min(y0, y1), hi_y = Math.max(y0, y1);
        for (int y = lo_y; y <= hi_y; y++) {
            for (int x = lo_x; x <= hi_x; x++) {
                if (fill || y == lo_y || y == hi_y || x == lo_x || x == hi_x) out.accept(x, y);
            }
        }
    }

    /** Ellipse in the drag bounding box. Outline is drawn where the implicit
     *  function crosses zero between neighbouring pixels. */
    static void ellipse(int x0, int y0, int x1, int y1, boolean fill, PixelConsumer out) {
        int lo_x = Math.min(x0, x1), hi_x = Math.max(x0, x1);
        int lo_y = Math.min(y0, y1), hi_y = Math.max(y0, y1);
        double cx = (lo_x + hi_x) / 2.0, cy = (lo_y + hi_y) / 2.0;
        double rx = Math.max(0.5, (hi_x - lo_x) / 2.0), ry = Math.max(0.5, (hi_y - lo_y) / 2.0);
        for (int y = lo_y; y <= hi_y; y++) {
            for (int x = lo_x; x <= hi_x; x++) {
                double v = sq((x - cx) / rx) + sq((y - cy) / ry);
                if (v > 1) continue;
                if (fill) { out.accept(x, y); continue; }
                boolean edge = sq((x - cx + 1) / rx) + sq((y - cy) / ry) > 1
                    || sq((x - cx - 1) / rx) + sq((y - cy) / ry) > 1
                    || sq((x - cx) / rx) + sq((y - cy + 1) / ry) > 1
                    || sq((x - cx) / rx) + sq((y - cy - 1) / ry) > 1;
                if (edge) out.accept(x, y);
            }
        }
    }

    private static double sq(double v) { return v * v; }

    private ShapeUtil() {}
}
