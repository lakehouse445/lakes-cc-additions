package gg.lakehouse.scanner.network;

import gg.lakehouse.scanner.ScannerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ScannerNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(ScannerRegistry.MOD_ID, "main"),
        () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, SaveDrawingPacket.class,
            SaveDrawingPacket::encode, SaveDrawingPacket::decode, SaveDrawingPacket::handle);
        CHANNEL.registerMessage(1, SaveEmblemPacket.class,
            SaveEmblemPacket::encode, SaveEmblemPacket::decode, SaveEmblemPacket::handle);
    }

    private ScannerNetwork() {}
}
