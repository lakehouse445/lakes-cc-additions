package gg.lakehouse.scanner.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public final class PenClient {
    public static void open(InteractionHand pageHand) {
        Minecraft.getInstance().setScreen(new DrawingScreen(pageHand));
    }

    public static void openStampEditor(InteractionHand stampHand) {
        Minecraft.getInstance().setScreen(new StampEditorScreen(stampHand));
    }

    public static void openStampPlacement(InteractionHand stampHand, InteractionHand pageHand) {
        Minecraft.getInstance().setScreen(new StampPlaceScreen(stampHand, pageHand));
    }

    private PenClient() {}
}
