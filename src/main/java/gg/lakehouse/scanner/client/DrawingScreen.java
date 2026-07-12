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

import static dan200.computercraft.client.render.PrintoutRenderer.X_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.X_TEXT_MARGIN;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_TEXT_MARGIN;

/**
 * The pen editor: renders the real page (via CC:T's printout renderer) with
 * the ink layer above it. Left-drag draws, right-drag erases. Saved layers
 * are sent to the server per page on Done.
 */
public class DrawingScreen extends Screen {
    private static final int LINES = 21;
    private static final String[] INK_NAMES = { "", "Dark", "Red", "Blue" };

    private final InteractionHand pageHand;
    private final ItemStack stack;
    private final String[] text;
    private final String[] colours;
    private final int pageCount;
    private final boolean isBook;

    private final byte[][] layers;
    private final boolean[] dirty;

    private int page = 0;
    private int ink = 1;       // 1 dark, 2 red, 3 blue
    private int brush = 1;     // 1 or 2 px
    private boolean erasing = false;
    private int lastX = -1, lastY = -1;
    private final java.util.List<java.util.Deque<byte[]>> undoStacks = new java.util.ArrayList<>();
    private static final int UNDO_LIMIT = 24;

    private int left, top;
    private Button inkButton, brushButton, eraseButton, prevButton, nextButton;

    public DrawingScreen(InteractionHand pageHand) {
        super(Component.translatable("item.lakescanner.pen"));
        this.pageHand = pageHand;
        this.stack = minecraftPlayerStack(pageHand);

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
            undoStacks.add(new java.util.ArrayDeque<>());
        }
    }

    private static ItemStack minecraftPlayerStack(InteractionHand hand) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
    }

    private static String pad(String s, char fill) {
        if (s.length() >= 25) return s.substring(0, 25);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 25) sb.append(fill);
        return sb.toString();
    }

    @Override
    protected void init() {
        left = (width - X_SIZE) / 2;
        top = (height - Y_SIZE) / 2 - 8;
        int by = top + Y_SIZE + (isBook ? 16 : 8);
        int[] widths = { 58, 34, 46, 44, 44 };
        int gap = 5;
        int totalW = gap * (widths.length - 1);
        for (int w : widths) totalW += w;
        int bx = (width - totalW) / 2;

        inkButton = addRenderableWidget(Button.builder(Component.literal("Ink: Dark"),
            b -> { ink = ink % 3 + 1; erasing = false; refreshLabels(); })
            .bounds(bx, by, widths[0], 20).build());
        bx += widths[0] + gap;
        brushButton = addRenderableWidget(Button.builder(Component.literal("1px"),
            b -> { brush = brush == 1 ? 2 : 1; refreshLabels(); })
            .bounds(bx, by, widths[1], 20).build());
        bx += widths[1] + gap;
        eraseButton = addRenderableWidget(Button.builder(Component.literal("Erase"),
            b -> { erasing = !erasing; refreshLabels(); })
            .bounds(bx, by, widths[2], 20).build());
        bx += widths[2] + gap;
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> undo())
            .bounds(bx, by, widths[3], 20).build());
        bx += widths[3] + gap;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(bx, by, widths[4], 20).build());

        if (pageCount > 1) {
            prevButton = addRenderableWidget(Button.builder(Component.literal("<"),
                b -> { if (page > 0) page--; refreshLabels(); })
                .bounds(left - 24, top + Y_SIZE / 2 - 10, 20, 20).build());
            nextButton = addRenderableWidget(Button.builder(Component.literal(">"),
                b -> { if (page < pageCount - 1) page++; refreshLabels(); })
                .bounds(left + X_SIZE + 4, top + Y_SIZE / 2 - 10, 20, 20).build());
        }
        refreshLabels();
    }

    private void refreshLabels() {
        inkButton.setMessage(Component.literal("Ink: " + INK_NAMES[ink]));
        brushButton.setMessage(Component.literal(brush + "px"));
        eraseButton.setMessage(Component.literal(erasing ? "Drawing?" : "Erase"));
        if (prevButton != null) {
            prevButton.active = page > 0;
            nextButton.active = page < pageCount - 1;
        }
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background goes behind the page (CC:T does the same in PrintoutScreen)
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, -1);
        renderBackground(graphics);
        graphics.pose().popPose();

        var buffers = net.minecraft.client.renderer.MultiBufferSource.immediate(
            com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder());
        PrintoutRenderer.drawBorder(graphics.pose(), buffers, left, top, 0, page, pageCount, isBook,
            LightTexture.FULL_BRIGHT);
        PrintoutRenderer.drawText(graphics.pose(), buffers, left + X_TEXT_MARGIN, top + Y_TEXT_MARGIN,
            page * LINES, LightTexture.FULL_BRIGHT, text, colours);
        buffers.endBatch();

        drawInk(graphics, layers[page], left, top);

        if (pageCount > 1) {
            graphics.drawCenteredString(font, (page + 1) + " / " + pageCount,
                left + X_SIZE / 2, top - 12, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Horizontal run-length rendering of the ink layer. */
    private static void drawInk(GuiGraphics graphics, byte[] layer, int ox, int oy) {
        for (int y = 0; y < DrawingData.HEIGHT; y++) {
            int runStart = -1, runInk = 0;
            for (int x = 0; x <= DrawingData.WIDTH; x++) {
                int v = x < DrawingData.WIDTH ? DrawingData.getPixel(layer, x, y) : 0;
                if (v != runInk) {
                    if (runInk != 0) {
                        graphics.fill(ox + runStart, oy + y, ox + x, oy + y + 1,
                            DrawingData.COLOURS[runInk]);
                    }
                    runStart = x;
                    runInk = v;
                }
            }
        }
    }

    // ---------------------------------------------------------------- input

    private void paint(double mouseX, double mouseY, boolean erase) {
        int cx = (int) Math.floor(mouseX) - left;
        int cy = (int) Math.floor(mouseY) - top;
        if (cx < 0 || cx >= DrawingData.WIDTH || cy < 0 || cy >= DrawingData.HEIGHT) {
            lastX = -1;
            return;
        }
        int value = erase ? 0 : ink;

        if (lastX >= 0) {
            // Bresenham from the previous sample so fast strokes stay connected
            int x0 = lastX, y0 = lastY, x1 = cx, y1 = cy;
            int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
            while (true) {
                stamp(x0, y0, value);
                if (x0 == x1 && y0 == y1) break;
                int e2 = 2 * err;
                if (e2 >= dy) { err += dy; x0 += sx; }
                if (e2 <= dx) { err += dx; y0 += sy; }
            }
        } else {
            stamp(cx, cy, value);
        }
        lastX = cx;
        lastY = cy;
        dirty[page] = true;
    }

    private void stamp(int x, int y, int value) {
        for (int dy = 0; dy < brush; dy++) {
            for (int dx = 0; dx < brush; dx++) {
                DrawingData.setPixel(layers[page], x + dx, y + dy, value);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 || button == 1) {
            pushUndo();
            lastX = -1;
            paint(mouseX, mouseY, erasing || button == 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 || button == 1) {
            paint(mouseX, mouseY, erasing || button == 1);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        lastX = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void pushUndo() {
        var stack = undoStacks.get(page);
        stack.push(layers[page].clone());
        while (stack.size() > UNDO_LIMIT) stack.removeLast();
    }

    private void undo() {
        var stack = undoStacks.get(page);
        if (!stack.isEmpty()) {
            layers[page] = stack.pop();
            dirty[page] = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_Z && (modifiers & 2) != 0) { // Ctrl+Z
            undo();
            return true;
        }
        if (keyCode == InputConstants.KEY_E) {
            onClose();
            return true;
        }
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
    public boolean isPauseScreen() {
        return false;
    }
}
