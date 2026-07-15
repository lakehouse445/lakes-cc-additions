package gg.lakehouse.scanner.client;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelProperty;

/** Carries the camouflage BlockState from the block entity to the baked model. */
public final class CamoModelData {
    public static final ModelProperty<BlockState> CAMO = new ModelProperty<>();
    private CamoModelData() {}
}
