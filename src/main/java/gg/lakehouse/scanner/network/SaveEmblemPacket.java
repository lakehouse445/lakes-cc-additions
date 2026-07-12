package gg.lakehouse.scanner.network;

import gg.lakehouse.scanner.StampItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: save the edited emblem onto the held stamp. */
public record SaveEmblemPacket(InteractionHand stampHand, byte[] data) {

    public static void encode(SaveEmblemPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.stampHand);
        buf.writeByteArray(msg.data);
    }

    public static SaveEmblemPacket decode(FriendlyByteBuf buf) {
        return new SaveEmblemPacket(
            buf.readEnum(InteractionHand.class),
            buf.readByteArray(StampItem.BYTES + 16));
    }

    public static void handle(SaveEmblemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.data.length != 0 && msg.data.length != StampItem.BYTES) return;
            ItemStack stamp = player.getItemInHand(msg.stampHand);
            if (!(stamp.getItem() instanceof StampItem)) return;
            StampItem.setEmblem(stamp, msg.data.length == 0 ? null : msg.data);
        });
        ctx.get().setPacketHandled(true);
    }
}
