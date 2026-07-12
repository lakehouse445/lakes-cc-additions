package gg.lakehouse.scanner.client;

import gg.lakehouse.scanner.DrawingData;
import gg.lakehouse.scanner.StampItem;
import gg.lakehouse.scanner.network.SaveEmblemPacket;
import gg.lakehouse.scanner.network.ScannerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The emblem editor: a zoomed 32x32 pixel grid with a four-swatch palette
 * (blank, dark, red, blue), mirror symmetry, per-stroke undo, and a live
 * 1:1 preview on a paper chip.
 */
public class StampEditorScreen extends Screen {
    private static final int ZOOM = 6;
    private static final int GRID = StampItem.SIZE * ZOOM;

    private static final int PANEL = 0xF01A1A1E;
    private static final int PANEL_EDGE = 0xFF3A3A44;
    private static final int GOLD = 0xFFE3E087;
    private static final int PAPER = 0xFFECEADE;
    private static final int CHECKER_A = 0xFF232328;
    private static final int CHECKER_B = 0xFF2B2B31;

    private final InteractionHand stampHand;
    private final byte[] emblem;
    private final Deque<byte[]> undoStack = new ArrayDeque<>();

    private int ink = 1;
    private boolean mirror = false;
    private boolean dirty = false;
    private Button mirrorButton;

    private int panelX, panelY, gridX, gridY;

    public StampEditorScreen(InteractionHand stampHand) {
        super(Component.translatable("item.lakescanner.stamp"));
        this.stampHand = stampHand;
        var player = Minecraft.getInstance().player;
        byte[] existing = player == null ? null
            : StampItem.getEmblem(player.getItemInHand(stampHand));
        this.emblem = existing != null ? existing.clone() : new byte[StampItem.BYTES];
    }

    @Override
    protected void init() {
        int panelW = GRID + 24 + 64;
        int panelH = GRID + 58;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        gridX = panelX + 12;
        gridY = panelY + 24;

        int bx = gridX + GRID + 12;
        mirrorButton = addRenderableWidget(Button.builder(Component.literal("Mirror"),
            b -> { mirror = !mirror; refresh(); })
            .bounds(bx, gridY + 44, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> undo())
            .bounds(bx, gridY + 68, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
                pushUndo();
                java.util.Arrays.fill(emblem, (byte) 0);
                dirty = true;
            })
            .bounds(bx, gridY + 92, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(bx, gridY + GRID - 20, 52, 20).build());
        refresh();
    }

    private void refresh() {
        mirrorButton.setMessage(Component.literal(mirror ? "Mirror *" : "Mirror"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int panelW = GRID + 24 + 64;
        int panelH = GRID + 58;
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_EDGE);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        g.drawString(font, title, panelX + 12, panelY + 9, GOLD, false);

        // canvas: checkerboard for transparency, then pixels, then grid lines
        for (int y = 0; y < StampItem.SIZE; y++) {
            for (int x = 0; x < StampItem.SIZE; x++) {
                int px = gridX + x * ZOOM, py = gridY + y * ZOOM;
                g.fill(px, py, px + ZOOM, py + ZOOM,
                    ((x + y) & 1) == 0 ? CHECKER_A : CHECKER_B);
                int v = StampItem.pixel(emblem, x, y);
                if (v != 0) g.fill(px, py, px + ZOOM, py + ZOOM, DrawingData.COLOURS[v]);
            }
        }
        if (mirror) {
            int mid = gridX + GRID / 2;
            g.fill(mid, gridY, mid + 1, gridY + GRID, 0x50E3E087);
        }

        // palette: ink-on-paper chips in a 2x2 grid (blank chip = eraser)
        int sx = gridX + GRID + 12;
        for (int i = 0; i < 4; i++) {
            int cx = sx + (i % 2) * 20;
            int cy = gridY + (i / 2) * 20;
            boolean sel = ink == i;
            g.fill(cx - 2, cy - 2, cx + 16, cy + 16, sel ? GOLD : PANEL_EDGE);
            g.fill(cx, cy, cx + 14, cy + 14, PAPER);
            if (i != 0) g.fill(cx + 4, cy + 4, cx + 10, cy + 10, DrawingData.COLOURS[i]);
        }

        // 1:1 preview on a paper chip
        int pvx = sx, pvy = gridY + GRID - 60;
        g.drawString(font, "1:1", pvx, pvy - 10, 0xFF8D8A63, false);
        g.fill(pvx - 3, pvy - 3, pvx + StampItem.SIZE + 3, pvy + StampItem.SIZE + 3, PAPER);
        for (int y = 0; y < StampItem.SIZE; y++) {
            for (int x = 0; x < StampItem.SIZE; x++) {
                int v = StampItem.pixel(emblem, x, y);
                if (v != 0) g.fill(pvx + x, pvy + y, pvx + x + 1, pvy + y + 1,
                    DrawingData.COLOURS[v]);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void paintAt(double mouseX, double mouseY) {
        int x = ((int) mouseX - gridX) / ZOOM;
        int y = ((int) mouseY - gridY) / ZOOM;
        if (mouseX < gridX || x < 0 || x >= StampItem.SIZE || y < 0 || y >= StampItem.SIZE) return;
        StampItem.setPixel(emblem, x, y, ink);
        if (mirror) StampItem.setPixel(emblem, StampItem.SIZE - 1 - x, y, ink);
        dirty = true;
    }

    private boolean paletteClick(double mouseX, double mouseY) {
        int sx = gridX + GRID + 12;
        for (int i = 0; i < 4; i++) {
            int cx = sx + (i % 2) * 20;
            int cy = gridY + (i / 2) * 20;
            if (mouseX >= cx - 2 && mouseX <= cx + 16 && mouseY >= cy - 2 && mouseY <= cy + 16) {
                ink = i;
                return true;
            }
        }
        return false;
    }

    private void pushUndo() {
        undoStack.push(emblem.clone());
        while (undoStack.size() > 24) undoStack.removeLast();
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            System.arraycopy(undoStack.pop(), 0, emblem, 0, emblem.length);
            dirty = true;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (paletteClick(mouseX, mouseY)) return true;
        if (button == 0 || button == 1) {
            pushUndo();
            int keep = ink;
            if (button == 1) ink = 0;
            paintAt(mouseX, mouseY);
            ink = keep;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 || button == 1) {
            int keep = ink;
            if (button == 1) ink = 0;
            paintAt(mouseX, mouseY);
            ink = keep;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_Z && (modifiers & 2) != 0) {
            undo();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (dirty) {
            boolean blank = true;
            for (byte b : emblem) if (b != 0) { blank = false; break; }
            ScannerNetwork.CHANNEL.sendToServer(new SaveEmblemPacket(
                stampHand, blank ? new byte[0] : emblem));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
