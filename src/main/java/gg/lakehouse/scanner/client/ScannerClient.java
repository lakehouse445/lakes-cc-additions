package gg.lakehouse.scanner.client;

import gg.lakehouse.scanner.FolderItem;
import gg.lakehouse.scanner.ScannerRegistry;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ScannerRegistry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ScannerClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ScannerRegistry.SCANNER_MENU.get(), ScannerScreen::new);
            MenuScreens.register(ScannerRegistry.FOLDER_MENU.get(), FolderScreen::new);
            ItemProperties.register(ScannerRegistry.FOLDER.get(),
                new ResourceLocation("lakescanner", "filled"),
                (stack, level, entity, seed) -> FolderItem.hasContents(stack) ? 1.0f : 0.0f);
        });
    }

    private ScannerClient() {}
}
