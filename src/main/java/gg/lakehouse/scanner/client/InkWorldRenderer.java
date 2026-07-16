package gg.lakehouse.scanner.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dan200.computercraft.client.render.ItemMapLikeRenderer;
import dan200.computercraft.client.render.PrintoutRenderer;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import gg.lakehouse.scanner.DrawingData;
import gg.lakehouse.scanner.PenItem;
import gg.lakehouse.scanner.ScannerRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderItemInFrameEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static dan200.computercraft.client.render.PrintoutRenderer.COVER_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.X_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.X_TEXT_MARGIN;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_TEXT_MARGIN;
import static dan200.computercraft.client.render.PrintoutRenderer.offsetAt;

/**
 * Printout rendering in hand and in item frames.
 *
 * Single pages: CC:T renders the page itself (cancelling the event); we
 * listen at LOW priority with receiveCanceled and add the ink layer with
 * the same transforms. Extends CC:T's ItemMapLikeRenderer so all
 * first-person hand mathematics (side/centre hold, swing, equip) are
 * inherited, not copied.
 *
 * Multi-page printouts and books: we take the event over entirely (HIGHEST
 * priority, cancel before CC:T sees it) and draw border, text and ink
 * ourselves with the z axis spread out -- see Z_SPREAD.
 */
@Mod.EventBusSubscriber(modid = ScannerRegistry.MOD_ID, value = Dist.CLIENT)
public final class InkWorldRenderer extends ItemMapLikeRenderer {
    private static final int LINES = 21;

    /**
     * Multiplies CC:T's internal z-offsets when we draw a printout in hand.
     * drawBorder spaces the page leaves 0.001 apart, which after the 0.42
     * item scale is ~2e-6 blocks: below depth-buffer precision (worse under
     * shaders, which compress hand depth), so thick books z-fight. 16x lifts
     * the spacing above precision while staying visually imperceptible
     * (under a millimetre across the whole stack).
     */
    private static final float Z_SPREAD = 16f;

    private static final InkWorldRenderer INSTANCE = new InkWorldRenderer();

    /** True while the HIGHEST-priority pass draws the whole printout. */
    private boolean fullDraw = false;

    private InkWorldRenderer() {}

    /** Books/multi-page printouts: draw everything ourselves with spread z. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHandEarly(RenderHandEvent event) {
        if (event.isCanceled()) return;
        ItemStack stack = event.getItemStack();
        if (!PenItem.isPrintout(stack) || pageCount(stack) <= 1) return;
        event.setCanceled(true);
        INSTANCE.fullDraw = true;
        try {
            INSTANCE.renderItemFirstPerson(
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                event.getHand(), event.getInterpolatedPitch(), event.getEquipProgress(),
                event.getSwingProgress(), stack);
        } finally {
            INSTANCE.fullDraw = false;
        }
    }

    /** Single pages: CC:T drew the page (and cancelled); add ink on top. */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onRenderHand(RenderHandEvent event) {
        if (!event.isCanceled()) return; // nobody rendered a printout
        ItemStack stack = event.getItemStack();
        if (pageCount(stack) > 1) return; // the whole-book pass already drew ink
        if (!hasInk(stack)) return;
        INSTANCE.renderItemFirstPerson(
            event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
            event.getHand(), event.getInterpolatedPitch(), event.getEquipProgress(),
            event.getSwingProgress(), stack);
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onRenderInFrame(RenderItemInFrameEvent event) {
        if (!event.isCanceled()) return;
        ItemStack stack = event.getItemStack();
        if (!hasInk(stack)) return;

        // CC:T's frame handler mutates the event PoseStack WITHOUT push/pop
        // (the vanilla frame renderer pops around the whole event), so by the
        // time we run, the stack is already in final page space. Just draw.
        var transform = event.getPoseStack();
        transform.pushPose();
        int light = event.getItemFrameEntity().getType()
            == net.minecraft.world.entity.EntityType.GLOW_ITEM_FRAME
            ? 0xf000d2 : event.getPackedLight();
        emitInkQuads(transform, event.getMultiBufferSource(),
            gg.lakehouse.scanner.DrawingData.get(stack, 0), light);
        transform.popPose();
    }

    /** Called by the inherited hand math with the item-space transform ready. */
    @Override
    protected void renderItem(PoseStack transform, MultiBufferSource render, ItemStack stack, int light) {
        // Mirror PrintoutItemRenderer.renderItem's pre-transform
        transform.mulPose(Axis.XP.rotationDegrees(180f));
        transform.scale(0.42f, 0.42f, -0.42f);
        transform.translate(-0.5f, -0.48f, 0.0f);

        // Mirror drawPrintout's normalisation
        int pages = pageCount(stack);
        boolean book = stack.getDescriptionId().contains("printed_book");
        double width = X_SIZE + (book ? 0 : offsetAt(pages - 1));
        double height = Y_SIZE;
        double visualWidth = width, visualHeight = height;
        if (book) {
            visualWidth += 2 * COVER_SIZE + 2 * offsetAt(pages);
            visualHeight += 2 * COVER_SIZE;
        }
        double max = Math.max(visualHeight, visualWidth);
        float scale = (float) (1.0 / max);
        transform.scale(scale, scale, scale);
        transform.translate((max - width) / 2.0, (max - height) / 2.0, 0.0);

        if (fullDraw) {
            transform.scale(1f, 1f, Z_SPREAD);
            drawWholePrintout(transform, render, stack, pages, book, light);
        } else {
            emitInkQuads(transform, render, DrawingData.get(stack, 0), light);
        }
    }

    /** Border, page-0 text and ink -- what CC:T's drawPrintout would draw. */
    private static void drawWholePrintout(PoseStack transform, MultiBufferSource render,
                                          ItemStack stack, int pages, boolean book, int light) {
        CompoundTag tag = stack.getTag();
        String[] text = new String[pages * LINES];
        String[] colours = new String[pages * LINES];
        for (int i = 0; i < text.length; i++) {
            text[i] = pad(tag != null ? tag.getString("Text" + i) : "", ' ');
            colours[i] = pad(tag != null ? tag.getString("Color" + i) : "", 'f');
        }
        PrintoutRenderer.drawBorder(transform, render, 0, 0, 0, 0, pages, book, light);
        PrintoutRenderer.drawText(transform, render, X_TEXT_MARGIN, Y_TEXT_MARGIN, 0, light, text, colours);
        emitInkQuads(transform, render, DrawingData.get(stack, 0), light);
    }

    private static int pageCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("Pages") ? Math.max(1, tag.getInt("Pages")) : 1;
    }

    private static boolean hasInk(ItemStack stack) {
        return PenItem.isPrintout(stack) && DrawingData.get(stack, 0) != null;
    }

    private static String pad(String s, char fill) {
        if (s.length() >= 25) return s.substring(0, 25);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 25) sb.append(fill);
        return sb.toString();
    }

    private static void emitInkQuads(PoseStack transform, MultiBufferSource render,
                                     byte[] layer, int light) {
        if (layer == null) return;

        var buffer = render.getBuffer(dan200.computercraft.client.render.RenderTypes.PRINTOUT_TEXT);
        var emitter = FixedWidthFontRenderer.toVertexConsumer(transform, buffer);
        for (int y = 0; y < DrawingData.HEIGHT; y++) {
            int runStart = -1, runInk = 0;
            for (int x = 0; x <= DrawingData.WIDTH; x++) {
                int v = x < DrawingData.WIDTH ? DrawingData.getPixel(layer, x, y) : 0;
                if (v != runInk) {
                    if (runInk != 0) {
                        FixedWidthFontRenderer.drawQuad(emitter, runStart, y, 0.001f,
                            x - runStart, 1, DrawingData.COLOURS[runInk], light);
                    }
                    runStart = x;
                    runInk = v;
                }
            }
        }
    }
}
