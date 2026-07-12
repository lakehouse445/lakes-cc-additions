package gg.lakehouse.scanner.network;

import gg.lakehouse.scanner.DrawingData;
import gg.lakehouse.scanner.PenItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: save one page's ink layer onto the held printout. */
public record SaveDrawingPacket(InteractionHand pageHand, int page, byte[] data) {

    public static void encode(SaveDrawingPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.pageHand);
        buf.writeVarInt(msg.page);
        buf.writeByteArray(msg.data);
    }

    public static SaveDrawingPacket decode(FriendlyByteBuf buf) {
        return new SaveDrawingPacket(
            buf.readEnum(InteractionHand.class),
            buf.readVarInt(),
            buf.readByteArray(DrawingData.BYTES + 16));
    }

    public static void handle(SaveDrawingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.page < 0 || msg.page >= 16) return;
            if (msg.data.length != 0 && msg.data.length != DrawingData.BYTES) return;

            ItemStack page = player.getItemInHand(msg.pageHand);
            InteractionHand other = msg.pageHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack pen = player.getItemInHand(other);

            if (!PenItem.isPrintout(page)) return;
            if (!(pen.getItem() instanceof PenItem)
                && !(pen.getItem() instanceof gg.lakehouse.scanner.StampItem)) return;

            DrawingData.set(page, msg.page, msg.data.length == 0 ? null : msg.data);
        });
        ctx.get().setPacketHandled(true);
    }
}
