package gg.lakehouse.scanner;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

@Mod(ScannerRegistry.MOD_ID)
public class ScannerMod {
    /** CC:T registers this capability itself; the token just fetches the shared instance. */
    public static final Capability<IPeripheral> CAPABILITY_PERIPHERAL =
        CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation PERIPHERAL_ID =
        new ResourceLocation(ScannerRegistry.MOD_ID, "peripheral");

    public ScannerMod() {
        ScannerRegistry.register(FMLJavaModLoadingContext.get().getModEventBus());
        gg.lakehouse.scanner.network.ScannerNetwork.register();
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, ScannerMod::attachPeripherals);
    }

    private static void attachPeripherals(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof ScannerBlockEntity scanner) {
            PeripheralProvider.attach(event, scanner, ScannerPeripheral::new);
        }
    }

    /**
     * An ICapabilityProvider that lazily creates an IPeripheral when required.
     * (Boilerplate pattern recommended by CC:T's package docs for Forge 1.20.1.)
     */
    private static final class PeripheralProvider<O extends BlockEntity> implements ICapabilityProvider {
        private final O blockEntity;
        private final Function<O, IPeripheral> factory;
        private @Nullable LazyOptional<IPeripheral> peripheral;

        private PeripheralProvider(O blockEntity, Function<O, IPeripheral> factory) {
            this.blockEntity = blockEntity;
            this.factory = factory;
        }

        private static <O extends BlockEntity> void attach(
            AttachCapabilitiesEvent<BlockEntity> event, O blockEntity, Function<O, IPeripheral> factory
        ) {
            var provider = new PeripheralProvider<>(blockEntity, factory);
            event.addCapability(PERIPHERAL_ID, provider);
            event.addListener(provider::invalidate);
        }

        private void invalidate() {
            if (peripheral != null) peripheral.invalidate();
            peripheral = null;
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            if (capability != CAPABILITY_PERIPHERAL) return LazyOptional.empty();
            if (blockEntity.isRemoved()) return LazyOptional.empty();

            var peripheral = this.peripheral;
            if (peripheral == null) {
                this.peripheral = peripheral = LazyOptional.of(() -> factory.apply(blockEntity));
            }
            return peripheral.cast();
        }
    }
}
