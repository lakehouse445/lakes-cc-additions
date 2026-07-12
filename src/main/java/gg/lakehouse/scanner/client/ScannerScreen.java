package gg.lakehouse.scanner.client;

import gg.lakehouse.scanner.ScannerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Reuses CC:T's disk drive GUI texture (loaded from CC:T's jar at runtime).
 * Our slot sits at the same (80, 35) the disk drive uses, so it lines up.
 */
public class ScannerScreen extends AbstractContainerScreen<ScannerMenu> {
    private static final ResourceLocation BACKGROUND =
        new ResourceLocation("computercraft", "textures/gui/disk_drive.png");

    public ScannerScreen(ScannerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
