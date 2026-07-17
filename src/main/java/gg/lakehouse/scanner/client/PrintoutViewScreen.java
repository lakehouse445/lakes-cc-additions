package gg.lakehouse.scanner.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.Tesselator;
import dan200.computercraft.client.gui.PrintoutScreen;
import dan200.computercraft.client.render.PrintoutRenderer;
import dan200.computercraft.shared.media.PrintoutMenu;
import gg.lakehouse.scanner.DrawingData;
import gg.lakehouse.scanner.ScannerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static dan200.computercraft.client.render.PrintoutRenderer.X_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.X_TEXT_MARGIN;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_TEXT_MARGIN;

/**
 * Replaces CC:T's printout viewer. CC:T's screen keeps the current page in
 * a server-synced DataSlot and applies every incoming value unconditionally,
 * so paging faster than the round trip makes stale echoes snap the view back
 * ("page lagbacks"). This screen is client-authoritative: page turns still
 * notify the server (so the menu state stays valid for everyone else) but
 * server echoes never move the visible page. It also draws pen/stamp ink
 * layers natively rather than through the reflection overlay.
 */
@Mod.EventBusSubscriber(modid = ScannerRegistry.MOD_ID, value = Dist.CLIENT)
public final class PrintoutViewScreen extends AbstractContainerScreen<PrintoutMenu> {
    private static final int LINES = 21;

    private int page = 0;
    /** Until the player turns a page themselves, follow the server's page. */
    private boolean pageTouched = false;

    public PrintoutViewScreen(PrintoutMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = X_SIZE;
        imageHeight = Y_SIZE;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof PrintoutScreen cct) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            event.setNewScreen(new PrintoutViewScreen(
                cct.getMenu(), player.getInventory(), cct.getTitle()));
        }
    }

    // ---------------------------------------------------------------- paging

    private int pageCount() {
        CompoundTag tag = menu.getPrintout().getTag();
        return tag != null && tag.contains("Pages") ? Math.max(1, tag.getInt("Pages")) : 1;
    }

    private int clamp(int p) {
        return Math.max(0, Math.min(pageCount() - 1, p));
    }

    @Override
    protected void containerTick() {
        page = clamp(pageTouched ? page : menu.getPage());
    }

    private void setPage(int p) {
        pageTouched = true;
        p = clamp(p);
        if (p == page) return;
        page = p;
        var gameMode = Minecraft.getInstance().gameMode;
        if (gameMode != null) {
            gameMode.handleInventoryButtonClick(menu.containerId, PrintoutMenu.PAGE_BUTTON_OFFSET + p);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_RIGHT || keyCode == InputConstants.KEY_DOWN) {
            setPage(page + 1);
            return true;
        }
        if (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_UP) {
            setPage(page - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta < 0) setPage(page + 1);
        else if (delta > 0) setPage(page - 1);
        return true;
    }

    // ---------------------------------------------------------------- render

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ItemStack stack = menu.getPrintout();
        CompoundTag tag = stack.getTag();
        int pages = pageCount();
        boolean book = stack.getDescriptionId().contains("printed_book");

        String[] text = new String[pages * LINES];
        String[] colours = new String[pages * LINES];
        for (int i = 0; i < text.length; i++) {
            text[i] = pad(tag != null ? tag.getString("Text" + i) : "", ' ');
            colours[i] = pad(tag != null ? tag.getString("Color" + i) : "", 'f');
        }

        var buffers = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        PrintoutRenderer.drawBorder(g.pose(), buffers, leftPos, topPos, 0, page, pages, book,
            LightTexture.FULL_BRIGHT);
        PrintoutRenderer.drawText(g.pose(), buffers, leftPos + X_TEXT_MARGIN, topPos + Y_TEXT_MARGIN,
            page * LINES, LightTexture.FULL_BRIGHT, text, colours);
        buffers.endBatch();

        drawInk(g, DrawingData.get(stack, page));
    }

    private void drawInk(GuiGraphics g, byte[] layer) {
        if (layer == null) return;
        int ox = leftPos, oy = topPos;
        for (int y = 0; y < DrawingData.HEIGHT; y++) {
            int runStart = -1, runInk = 0;
            for (int x = 0; x <= DrawingData.WIDTH; x++) {
                int v = x < DrawingData.WIDTH ? DrawingData.getPixel(layer, x, y) : 0;
                if (v != runInk) {
                    if (runInk != 0) {
                        g.fill(ox + runStart, oy + y, ox + x, oy + y + 1, DrawingData.COLOURS[runInk]);
                    }
                    runStart = x;
                    runInk = v;
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The page is drawn at z <= 0 (drawBorder puts the paper at -0.0005 and
        // the leaf edges below that), so the background gradient must sit behind
        // it or its depth pass swallows the whole page, leaving a see-through
        // sheet with the world-space held printout ghosting through.
        g.pose().pushPose();
        g.pose().translate(0, 0, -1);
        renderBackground(g);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // no inventory labels on a book
    }

    private static String pad(String s, char fill) {
        if (s.length() >= 25) return s.substring(0, 25);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 25) sb.append(fill);
        return sb.toString();
    }
}
