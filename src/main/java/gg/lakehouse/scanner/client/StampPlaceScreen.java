package gg.lakehouse.scanner.client;

import dan200.computercraft.client.render.PrintoutRenderer;
import gg.lakehouse.scanner.DrawingData;
import gg.lakehouse.scanner.StampItem;
import gg.lakehouse.scanner.network.SaveDrawingPacket;
import gg.lakehouse.scanner.network.ScannerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import static dan200.computercraft.client.render.PrintoutRenderer.X_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.X_TEXT_MARGIN;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_TEXT_MARGIN;

/**
 * Stamp placement: the page renders, a ghost of the emblem follows the
 * cursor, click to commit. Shift-click stamps and stays open.
 */
public class StampPlaceScreen extends Screen {
    private static final int LINES = 21;

    private final InteractionHand pageHand;
    private final byte[] emblem;
    private final String[] text;
    private final String[] colours;
    private final int pageCount;
    private final boolean isBook;
    private final byte[][] layers;

    private int page = 0;
    private int left, top;
    private Button prevButton, nextButton;

    public StampPlaceScreen(InteractionHand stampHand, InteractionHand pageHand) {
        super(Component.translatable("item.lakescanner.stamp"));
        this.pageHand = pageHand;
        var player = Minecraft.getInstance().player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getItemInHand(pageHand);
        byte[] e = player == null ? null : StampItem.getEmblem(player.getItemInHand(stampHand));
        this.emblem = e != null ? e : new byte[StampItem.BYTES];

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
        for (int p = 0; p < pageCount; p++) {
            byte[] existing = DrawingData.get(stack, p);
            layers[p] = existing != null ? existing.clone() : new byte[DrawingData.BYTES];
        }
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
        if (pageCount > 1) {
            prevButton = addRenderableWidget(Button.builder(Component.literal("<"),
                b -> { if (page > 0) page--; refresh(); })
                .bounds(left - 24, top + Y_SIZE / 2 - 10, 20, 20).build());
            nextButton = addRenderableWidget(Button.builder(Component.literal(">"),
                b -> { if (page < pageCount - 1) page++; refresh(); })
                .bounds(left + X_SIZE + 4, top + Y_SIZE / 2 - 10, 20, 20).build());
            refresh();
        }
    }

    private void refresh() {
        if (prevButton != null) {
            prevButton.active = page > 0;
            nextButton.active = page < pageCount - 1;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.pose().pushPose();
        g.pose().translate(0, 0, -1);
        renderBackground(g);
        g.pose().popPose();

        var buffers = net.minecraft.client.renderer.MultiBufferSource.immediate(
            com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder());
        PrintoutRenderer.drawBorder(g.pose(), buffers, left, top, 0, page, pageCount, isBook,
            LightTexture.FULL_BRIGHT);
        PrintoutRenderer.drawText(g.pose(), buffers, left + X_TEXT_MARGIN, top + Y_TEXT_MARGIN,
            page * LINES, LightTexture.FULL_BRIGHT, text, colours);
        buffers.endBatch();

        // existing ink
        for (int y = 0; y < DrawingData.HEIGHT; y++) {
            for (int x = 0; x < DrawingData.WIDTH; x++) {
                int v = DrawingData.getPixel(layers[page], x, y);
                if (v != 0) g.fill(left + x, top + y, left + x + 1, top + y + 1,
                    DrawingData.COLOURS[v]);
            }
        }

        // ghost emblem following the cursor (centred), half alpha
        int gx = mouseX - StampItem.SIZE / 2, gy = mouseY - StampItem.SIZE / 2;
        for (int y = 0; y < StampItem.SIZE; y++) {
            for (int x = 0; x < StampItem.SIZE; x++) {
                int v = StampItem.pixel(emblem, x, y);
                if (v != 0) g.fill(gx + x, gy + y, gx + x + 1, gy + y + 1,
                    (DrawingData.COLOURS[v] & 0x00FFFFFF) | 0x80000000);
            }
        }

        g.drawCenteredString(font, Component.translatable("item.lakescanner.stamp.hint"),
            width / 2, top + Y_SIZE + (isBook ? 16 : 8), 0xFF8D8A63);
        if (pageCount > 1) {
            g.drawCenteredString(font, (page + 1) + " / " + pageCount,
                left + X_SIZE / 2, top - 12, 0xFFFFFF);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        int ox = (int) mouseX - StampItem.SIZE / 2 - left;
        int oy = (int) mouseY - StampItem.SIZE / 2 - top;
        boolean any = false;
        for (int y = 0; y < StampItem.SIZE; y++) {
            for (int x = 0; x < StampItem.SIZE; x++) {
                int v = StampItem.pixel(emblem, x, y);
                if (v != 0) {
                    int px = ox + x, py = oy + y;
                    if (px >= 0 && px < DrawingData.WIDTH && py >= 0 && py < DrawingData.HEIGHT) {
                        DrawingData.setPixel(layers[page], px, py, v);
                        any = true;
                    }
                }
            }
        }
        if (!any) return true;

        ScannerNetwork.CHANNEL.sendToServer(new SaveDrawingPacket(pageHand, page, layers[page]));
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0f));
        if (!hasShiftDown()) onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
