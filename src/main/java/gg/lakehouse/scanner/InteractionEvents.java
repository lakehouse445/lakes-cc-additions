package gg.lakehouse.scanner;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Right-clicking a held printout with the pen or an engraved stamp in the
 * OTHER hand opens the editor instead of CC:T's read-only view, so tool
 * order doesn't matter.
 */
@Mod.EventBusSubscriber(modid = ScannerRegistry.MOD_ID)
public final class InteractionEvents {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack used = event.getItemStack();
        if (!PenItem.isPrintout(used)) return;

        InteractionHand other = event.getHand() == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack tool = event.getEntity().getItemInHand(other);

        boolean pen = tool.getItem() instanceof PenItem;
        boolean stamp = tool.getItem() instanceof StampItem
            && StampItem.getEmblem(tool) != null;
        if (!pen && !stamp) return;

        event.setCanceled(true); // suppress CC:T's printout view on both sides
        if (event.getSide().isClient()) {
            InteractionHand pageHand = event.getHand();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (pen) gg.lakehouse.scanner.client.PenClient.open(pageHand);
                else gg.lakehouse.scanner.client.PenClient.openStampPlacement(other, pageHand);
            });
        }
    }

    private InteractionEvents() {}
}
