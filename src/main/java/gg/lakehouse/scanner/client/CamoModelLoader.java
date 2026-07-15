package gg.lakehouse.scanner.client;

import gg.lakehouse.scanner.ScannerRegistry;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wraps the camo cable's baked models (all blockstate variants + the item)
 * in CamoCableModel after models bake.
 */
@Mod.EventBusSubscriber(modid = ScannerRegistry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CamoModelLoader {

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (var entry : models.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!id.getNamespace().equals(ScannerRegistry.MOD_ID)) continue;
            if (!id.getPath().equals("camo_cable")) continue;
            BakedModel original = entry.getValue();
            if (!(original instanceof CamoCableModel)) {
                entry.setValue(new CamoCableModel(original));
            }
        }
    }

    private CamoModelLoader() {}
}
