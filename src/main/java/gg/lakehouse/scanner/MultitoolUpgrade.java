package gg.lakehouse.scanner;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.AbstractPocketUpgrade;
import dan200.computercraft.api.pocket.IPocketAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Pocket upgrade combining an ender modem and a speaker.
 * Craft the multitool item, then craft it above any pocket computer
 * (CC:T's standard upgrade recipe) to get a "Deluxe Pocket Computer".
 */
public class MultitoolUpgrade extends AbstractPocketUpgrade {
    public MultitoolUpgrade(ResourceLocation id, ItemStack stack) {
        super(id, "upgrade.lakescanner.multitool.adjective", stack);
    }

    @Nullable
    @Override
    public IPeripheral createPeripheral(IPocketAccess access) {
        return new MultitoolPeripheral(access);
    }

    @Override
    public void update(IPocketAccess access, @Nullable IPeripheral peripheral) {
        if (peripheral instanceof MultitoolPeripheral multitool) multitool.serverTick();
    }
}
