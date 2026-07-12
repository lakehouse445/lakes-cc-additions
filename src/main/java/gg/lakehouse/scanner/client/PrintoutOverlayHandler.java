package gg.lakehouse.scanner.client;

import dan200.computercraft.client.gui.PrintoutScreen;
import gg.lakehouse.scanner.DrawingData;
import gg.lakehouse.scanner.ScannerRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * Draws saved ink layers on top of CC:T's own printout view screen, so
 * drawn pages look drawn for everyone, not just in the pen editor.
 * Reads PrintoutScreen's private page counter reflectively (pinned to
 * CC:T 1.119.0; the field is called "page").
 */
@Mod.EventBusSubscriber(modid = ScannerRegistry.MOD_ID, value = Dist.CLIENT)
public final class PrintoutOverlayHandler {
    private static Field pageField;
    private static boolean reflectionFailed = false;

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof PrintoutScreen screen) || reflectionFailed) return;

        ItemStack stack = screen.getMenu().getSlot(0).getItem();
        if (stack.isEmpty()) return;

        int page;
        try {
            if (pageField == null) {
                pageField = PrintoutScreen.class.getDeclaredField("page");
                pageField.setAccessible(true);
            }
            page = pageField.getInt(screen);
        } catch (ReflectiveOperationException e) {
            reflectionFailed = true; // never spam; drawings still work in the editor
            return;
        }

        byte[] layer = DrawingData.get(stack, page);
        if (layer == null) return;

        int ox = screen.getGuiLeft();
        int oy = screen.getGuiTop();
        var graphics = event.getGuiGraphics();
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

    private PrintoutOverlayHandler() {}
}
