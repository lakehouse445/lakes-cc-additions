package gg.lakehouse.scanner.client;

import com.mojang.blaze3d.platform.InputConstants;
import dan200.computercraft.client.render.PrintoutRenderer;
import gg.lakehouse.scanner.DrawingData;
import gg.lakehouse.scanner.network.SaveDrawingPacket;
import gg.lakehouse.scanner.network.ScannerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static dan200.computercraft.client.render.PrintoutRenderer.X_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.X_TEXT_MARGIN;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_TEXT_MARGIN;

/**
 * The pen editor. The page sits in a fixed-size viewport; zoom (scroll,
 * 1-4x, anchored on the cursor) and pan (middle-drag) happen inside it, so
 * the layout never moves. Tools: draw (with lazy-brush stabilizer), line
 * (also Shift while drawing), erase. Undo/redo per page.
 */
public class DrawingScreen extends Screen {
    private static final int LINES = 21;
    private static final int PANEL = 0xF01A1A1E;
    private static final int PANEL_EDGE = 0xFF3A3A44;
    private static final int GOLD = 0xFFE3E087;
    private static final int DIM = 0xFF8D8A63;
    private static final int PAPER = 0xFFECEADE;

    private static final int PAD = 12;
    private static final int SIDEBAR_W = 72;
    private static final int BTN_W = 34, BTN_H = 20, BTN_GAP = 4, ROW = 24;
    private static final int VW = X_SIZE + 4, VH = Y_SIZE + 4; // viewport (2px inset)

    private static final int TOOL_DRAW = 0, TOOL_LINE = 1, TOOL_ERASE = 2;
    private static final double[] STAB_RADII = { 0, 2.5, 5.0 };
    private static final String[] STAB_NAMES = { "Stab: Off", "Stab: Med", "Stab: Hi" };

    private final InteractionHand pageHand;
    private final String[] text;
    private final String[] colours;
    private final int pageCount;
    private final boolean isBook;
    private final byte[][] layers;
    private final boolean[] dirty;
    private final List<Deque<byte[]>> undoStacks = new ArrayList<>();
    private final List<Deque<byte[]>> redoStacks = new ArrayList<>();

    private int page = 0;
    private int ink = 1;
    private int tool = TOOL_DRAW;
    private int brush = 1;
    private int stab = 1;

    private int zoom = 1;
    private double panX = 0, panY = 0;

    private boolean stroking = false;
    private boolean strokeErase = false;
    private boolean strokeLine = false;
    private double tipX, tipY;          // stabilized pen tip (canvas)
    private int anchorX, anchorY;       // line anchor (canvas)
    private int curX, curY;             // current canvas position
    private int hoverX = -1, hoverY = -1;

    private int panelX, panelY, vx, vy;
    private Button drawBtn, lineBtn, eraseBtn, brushBtn, stabBtn, prevBtn, nextBtn;

    public DrawingScreen(InteractionHand pageHand) {
        super(Component.translatable("item.lakescanner.pen"));
        this.pageHand = pageHand;
        ItemStack stack = stack();
        CompoundTag tag = stack.getTag();
        this.pageCount = tag != null && tag.contains("Pages") ? Math.max(1, tag.getInt("Pages")) : 1;
        this.isBook = stack.getDescriptionId().contains("printed_book");
        this.text = new String[pageCount * LINES];
        this.colours = new String[pageCount * LINES];
        for (int i = 0; i < pageCount * LINES; i++) {
            text[i] = pad(tag != null ? tag.getString("Text" + i) : "", ' ');
            colours[i] = pad(tag != null ? tag.getString("Color" + i) : "", 'f');
        }
        this.layers = new byte[pageCount][];
        this.dirty = new boolean[pageCount];
        for (int p = 0; p < pageCount; p++) {
            byte[] existing = DrawingData.get(stack, p);
            layers[p] = existing != null ? existing.clone() : new byte[DrawingData.BYTES];
            undoStacks.add(new ArrayDeque<>());
            redoStacks.add(new ArrayDeque<>());
        }
    }

    private ItemStack stack() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getItemInHand(pageHand);
    }

    private static String pad(String s, char fill) {
        if (s.length() >= 25) return s.substring(0, 25);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 25) sb.append(fill);
        return sb.toString();
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        int groupW = VW + PAD + SIDEBAR_W;
        panelX = (width - groupW) / 2;
        panelY = Math.max(2, (height - (VH + 24)) / 2);
        vx = panelX;
        vy = panelY;

        int sx = vx + VW + PAD;
        int y = vy + 48; // below swatches

        drawBtn = addRenderableWidget(Button.builder(Component.literal("Draw"),
            b -> { tool = TOOL_DRAW; refresh(); }).bounds(sx, y, BTN_W, BTN_H).build());
        lineBtn = addRenderableWidget(Button.builder(Component.literal("Line"),
            b -> { tool = TOOL_LINE; refresh(); }).bounds(sx + BTN_W + BTN_GAP, y, BTN_W, BTN_H).build());
        y += ROW;
        eraseBtn = addRenderableWidget(Button.builder(Component.literal("Erase"),
            b -> { tool = TOOL_ERASE; refresh(); }).bounds(sx, y, BTN_W, BTN_H).build());
        brushBtn = addRenderableWidget(Button.builder(Component.literal("1px"),
            b -> { brush = brush == 1 ? 2 : 1; refresh(); }).bounds(sx + BTN_W + BTN_GAP, y, BTN_W, BTN_H).build());
        y += ROW;
        stabBtn = addRenderableWidget(Button.builder(Component.literal(STAB_NAMES[stab]),
            b -> { stab = (stab + 1) % 3; refresh(); }).bounds(sx, y, BTN_W * 2 + BTN_GAP, BTN_H).build());
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> undo())
            .bounds(sx, y, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("Redo"), b -> redo())
            .bounds(sx + BTN_W + BTN_GAP, y, BTN_W, BTN_H).build());
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(sx, vy + VH - BTN_H, BTN_W * 2 + BTN_GAP, BTN_H).build());

        if (pageCount > 1) {
            prevBtn = addRenderableWidget(Button.builder(Component.literal("<"),
                b -> { if (page > 0) page--; refresh(); })
                .bounds(vx, vy + VH + 2, 20, 20).build());
            nextBtn = addRenderableWidget(Button.builder(Component.literal(">"),
                b -> { if (page < pageCount - 1) page++; refresh(); })
                .bounds(vx + VW - 20, vy + VH + 2, 20, 20).build());
        }
        refresh();
    }

    private void refresh() {
        drawBtn.active = tool != TOOL_DRAW;
        lineBtn.active = tool != TOOL_LINE;
        eraseBtn.active = tool != TOOL_ERASE;
        brushBtn.setMessage(Component.literal(brush + "px"));
        stabBtn.setMessage(Component.literal(STAB_NAMES[stab]));
        if (prevBtn != null) {
            prevBtn.active = page > 0;
            nextBtn.active = page < pageCount - 1;
        }
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.pose().pushPose();
        g.pose().translate(0, 0, -1);
        renderBackground(g);
        g.pose().popPose();

        // page content (scissored, zoomed, panned) -- no chrome, just the paper
        g.enableScissor(vx, vy, vx + VW, vy + VH);
        g.pose().pushPose();
        g.pose().translate(vx + 2 - panX * zoom, vy + 2 - panY * zoom, 0);
        g.pose().scale(zoom, zoom, 1);

        var buffers = net.minecraft.client.renderer.MultiBufferSource.immediate(
            com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder());
        PrintoutRenderer.drawBorder(g.pose(), buffers, 0, 0, 0, page, pageCount, isBook,
            LightTexture.FULL_BRIGHT);
        PrintoutRenderer.drawText(g.pose(), buffers, X_TEXT_MARGIN, Y_TEXT_MARGIN,
            page * LINES, LightTexture.FULL_BRIGHT, text, colours);
        buffers.endBatch();

        drawLayer(g, layers[page]);
        if (stroking && strokeLine) {
            ShapeUtil.line(anchorX, anchorY, curX, curY, (x, y2) ->
                stampPreview(g, x, y2));
        }
        g.pose().popPose();
        g.disableScissor();

        // coordinates + zoom, above the page where the eye already is
        String status;
        if (hoverX >= 0) {
            int col = (hoverX - X_TEXT_MARGIN) / 6, row = (hoverY - Y_TEXT_MARGIN) / 9;
            boolean inText = hoverX >= X_TEXT_MARGIN && hoverY >= Y_TEXT_MARGIN
                && col >= 0 && col < 25 && row >= 0 && row < LINES;
            status = inText
                ? String.format("col %d, row %d  (px %d,%d)   %dx", col, row, hoverX, hoverY, zoom)
                : String.format("margin  (px %d,%d)   %dx", hoverX, hoverY, zoom);
        } else {
            status = String.format("scroll: zoom   middle-drag: pan   %dx", zoom);
        }
        g.drawCenteredString(font, status, vx + VW / 2, vy - 12, DIM);
        if (pageCount > 1) {
            g.drawCenteredString(font, (page + 1) + " / " + pageCount,
                vx + VW / 2, vy + VH + 8, 0xFFFFFFFF);
        }

        drawSwatches(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawLayer(GuiGraphics g, byte[] layer) {
        for (int y = 0; y < DrawingData.HEIGHT; y++) {
            int runStart = -1, runInk = 0;
            for (int x = 0; x <= DrawingData.WIDTH; x++) {
                int v = x < DrawingData.WIDTH ? DrawingData.getPixel(layer, x, y) : 0;
                if (v != runInk) {
                    if (runInk != 0) g.fill(runStart, y, x, y + 1, DrawingData.COLOURS[runInk]);
                    runStart = x;
                    runInk = v;
                }
            }
        }
    }

    private void stampPreview(GuiGraphics g, int x, int y) {
        for (int dy = 0; dy < brush; dy++) {
            for (int dx = 0; dx < brush; dx++) {
                g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1,
                    (DrawingData.COLOURS[ink] & 0x00FFFFFF) | 0xA0000000);
            }
        }
    }

    private void drawSwatches(GuiGraphics g) {
        int sx = vx + VW + PAD;
        for (int i = 0; i < 4; i++) {
            int cx = sx + (i % 2) * 20;
            int cy = vy + (i / 2) * 20;
            boolean sel = i == 0 ? tool == TOOL_ERASE : (ink == i && tool != TOOL_ERASE);
            g.fill(cx - 2, cy - 2, cx + 16, cy + 16, sel ? GOLD : PANEL_EDGE);
            g.fill(cx, cy, cx + 14, cy + 14, PAPER);
            if (i != 0) g.fill(cx + 4, cy + 4, cx + 10, cy + 10, DrawingData.COLOURS[i]);
        }
    }

    // ---------------------------------------------------------------- input

    private double canvasX(double mx) { return (mx - (vx + 2)) / zoom + panX; }
    private double canvasY(double my) { return (my - (vy + 2)) / zoom + panY; }
    private boolean inViewport(double mx, double my) {
        return mx >= vx && mx < vx + VW && my >= vy && my < vy + VH;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (inViewport(mx, my)) {
            hoverX = (int) Math.floor(canvasX(mx));
            hoverY = (int) Math.floor(canvasY(my));
            if (hoverX < 0 || hoverX >= DrawingData.WIDTH
                || hoverY < 0 || hoverY >= DrawingData.HEIGHT) hoverX = hoverY = -1;
        } else {
            hoverX = hoverY = -1;
        }
    }

    private boolean swatchClick(double mx, double my) {
        int sx = vx + VW + PAD;
        for (int i = 0; i < 4; i++) {
            int cx = sx + (i % 2) * 20;
            int cy = vy + (i / 2) * 20;
            if (mx >= cx - 2 && mx <= cx + 16 && my >= cy - 2 && my <= cy + 16) {
                if (i == 0) tool = TOOL_ERASE;
                else { ink = i; if (tool == TOOL_ERASE) tool = TOOL_DRAW; }
                refresh();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (swatchClick(mx, my)) return true;
        if (!inViewport(mx, my)) return false;
        if (button == 2) return true; // pan handled in drag

        if (button == 0 || button == 1) {
            int cx = clampX(canvasX(mx)), cy = clampY(canvasY(my));
            pushUndo();
            stroking = true;
            strokeErase = tool == TOOL_ERASE || button == 1;
            strokeLine = !strokeErase && (tool == TOOL_LINE || hasShiftDown());
            tipX = cx; tipY = cy;
            anchorX = cx; anchorY = cy;
            curX = cx; curY = cy;
            if (!strokeLine) stamp(cx, cy, strokeErase ? 0 : ink);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (button == 2) {
            panX = clampPanX(panX - dragX / zoom);
            panY = clampPanY(panY - dragY / zoom);
            return true;
        }
        if (!stroking || (button != 0 && button != 1)) {
            return super.mouseDragged(mx, my, button, dragX, dragY);
        }
        double txd = canvasX(mx), tyd = canvasY(my);
        curX = clampX(txd); curY = clampY(tyd);

        if (strokeLine) return true; // preview only; committed on release

        // lazy-brush stabilizer
        double r = STAB_RADII[stab];
        double dx = txd - tipX, dy = tyd - tipY;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d > r && d > 0) {
            double nx = tipX + dx / d * (d - r), ny = tipY + dy / d * (d - r);
            int value = strokeErase ? 0 : ink;
            ShapeUtil.line((int) Math.floor(tipX), (int) Math.floor(tipY),
                (int) Math.floor(nx), (int) Math.floor(ny), (x, y) -> stamp(x, y, value));
            tipX = nx; tipY = ny;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (stroking && (button == 0 || button == 1)) {
            if (strokeLine) {
                int value = strokeErase ? 0 : ink;
                ShapeUtil.line(anchorX, anchorY, curX, curY, (x, y) -> stamp(x, y, value));
            }
            stroking = false;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!inViewport(mx, my)) return super.mouseScrolled(mx, my, delta);
        int newZoom = Math.max(1, Math.min(4, zoom + (delta > 0 ? 1 : -1)));
        if (newZoom != zoom) {
            panX = clampPanXFor(panX + (mx - (vx + 2)) * (1.0 / zoom - 1.0 / newZoom), newZoom);
            panY = clampPanYFor(panY + (my - (vy + 2)) * (1.0 / zoom - 1.0 / newZoom), newZoom);
            zoom = newZoom;
        }
        return true;
    }

    private int clampX(double v) { return Math.max(0, Math.min(DrawingData.WIDTH - 1, (int) Math.floor(v))); }
    private int clampY(double v) { return Math.max(0, Math.min(DrawingData.HEIGHT - 1, (int) Math.floor(v))); }
    private double clampPanX(double v) { return clampPanXFor(v, zoom); }
    private double clampPanY(double v) { return clampPanYFor(v, zoom); }
    private double clampPanXFor(double v, int z) {
        return Math.max(0, Math.min(Math.max(0, DrawingData.WIDTH - (VW - 4.0) / z), v));
    }
    private double clampPanYFor(double v, int z) {
        return Math.max(0, Math.min(Math.max(0, DrawingData.HEIGHT - (VH - 4.0) / z), v));
    }

    private void stamp(int x, int y, int value) {
        for (int dy = 0; dy < brush; dy++) {
            for (int dx = 0; dx < brush; dx++) {
                DrawingData.setPixel(layers[page], x + dx, y + dy, value);
            }
        }
        dirty[page] = true;
    }

    // ---------------------------------------------------------------- history

    private void pushUndo() {
        undoStacks.get(page).push(layers[page].clone());
        while (undoStacks.get(page).size() > 24) undoStacks.get(page).removeLast();
        redoStacks.get(page).clear();
    }

    private void undo() {
        var u = undoStacks.get(page);
        if (!u.isEmpty()) {
            redoStacks.get(page).push(layers[page].clone());
            layers[page] = u.pop();
            dirty[page] = true;
        }
    }

    private void redo() {
        var r = redoStacks.get(page);
        if (!r.isEmpty()) {
            undoStacks.get(page).push(layers[page].clone());
            layers[page] = r.pop();
            dirty[page] = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & 2) != 0;
        if (ctrl && keyCode == InputConstants.KEY_Z) { undo(); return true; }
        if (ctrl && keyCode == InputConstants.KEY_Y) { redo(); return true; }
        if (keyCode == InputConstants.KEY_E) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        for (int p = 0; p < pageCount; p++) {
            if (dirty[p]) {
                boolean blank = true;
                for (byte b : layers[p]) if (b != 0) { blank = false; break; }
                ScannerNetwork.CHANNEL.sendToServer(new SaveDrawingPacket(
                    pageHand, p, blank ? new byte[0] : layers[p]));
            }
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
