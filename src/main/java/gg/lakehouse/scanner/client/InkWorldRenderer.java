package gg.lakehouse.scanner.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dan200.computercraft.client.render.ItemMapLikeRenderer;
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

import java.lang.reflect.Method;

import static dan200.computercraft.client.render.PrintoutRenderer.COVER_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.X_SIZE;
import static dan200.computercraft.client.render.PrintoutRenderer.Y_SIZE;

/**
 * Draws ink layers over printouts rendered in hand and in item frames.
 * CC:T cancels vanilla rendering and draws the page itself; we listen at
 * LOW priority with receiveCanceled and draw the ink with the same
 * transforms. Extends CC:T's ItemMapLikeRenderer so all first-person hand
 * mathematics (side/centre hold, swing, equip) are inherited, not copied.
 */
@Mod.EventBusSubscriber(modid = ScannerRegistry.MOD_ID, value = Dist.CLIENT)
public final class InkWorldRenderer extends ItemMapLikeRenderer {
    private static final InkWorldRenderer INSTANCE = new InkWorldRenderer();
    private static Method offsetAt;
    private static boolean offsetAtFailed = false;

    private InkWorldRenderer() {}

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onRenderHand(RenderHandEvent event) {
        if (!event.isCanceled()) return; // CC:T didn't render a printout
        ItemStack stack = event.getItemStack();
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
        drawInkOnPage(transform, render, stack, light);
    }

    private static boolean hasInk(ItemStack stack) {
        return PenItem.isPrintout(stack) && DrawingData.get(stack, 0) != null;
    }

    /** Mirror drawPrintout's normalisation, then emit the page-0 ink quads. */
    private static void drawInkOnPage(PoseStack transform, MultiBufferSource render,
                                      ItemStack stack, int light) {
        CompoundTag tag = stack.getTag();
        int pages = tag != null && tag.contains("Pages") ? Math.max(1, tag.getInt("Pages")) : 1;
        boolean book = stack.getDescriptionId().contains("printed_book");

        double width = X_SIZE + (book ? 0 : pageOffset(pages - 1));
        double height = Y_SIZE;
        double visualWidth = width, visualHeight = height;
        if (book) {
            visualWidth += 2 * COVER_SIZE + 2 * pageOffset(pages);
            visualHeight += 2 * COVER_SIZE;
        }
        double max = Math.max(visualHeight, visualWidth);
        float scale = (float) (1.0 / max);
        transform.scale(scale, scale, scale);
        transform.translate((max - width) / 2.0, (max - height) / 2.0, 0.0);

        emitInkQuads(transform, render, DrawingData.get(stack, 0), light);
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

    /** PrintoutRenderer.offsetAt reflectively (package-private); 0 on failure. */
    private static double pageOffset(int page) {
        if (offsetAtFailed) return 0;
        try {
            if (offsetAt == null) {
                offsetAt = dan200.computercraft.client.render.PrintoutRenderer.class
                    .getDeclaredMethod("offsetAt", int.class);
                offsetAt.setAccessible(true);
            }
            return ((Number) offsetAt.invoke(null, page)).doubleValue();
        } catch (ReflectiveOperationException e) {
            offsetAtFailed = true;
            return 0;
        }
    }
}
