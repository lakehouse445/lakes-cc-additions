package gg.lakehouse.scanner;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A rubber stamp with a customisable 32x32 emblem (same four ink states as
 * the pen). Use it alone to edit the emblem; use it while holding a printed
 * page in the other hand to stamp the emblem onto the page.
 */
public class StampItem extends Item {
    public static final int SIZE = 32;
    public static final int BYTES = SIZE * SIZE / 4; // 2bpp
    public static final String EMBLEM_KEY = "Emblem";

    public StampItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static byte @Nullable [] getEmblem(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(EMBLEM_KEY)) return null;
        byte[] data = tag.getByteArray(EMBLEM_KEY);
        return data.length == BYTES ? data : null;
    }

    public static void setEmblem(ItemStack stack, byte @Nullable [] data) {
        boolean blank = data == null;
        if (!blank) {
            blank = true;
            for (byte b : data) if (b != 0) { blank = false; break; }
        }
        if (blank) {
            if (stack.getTag() != null) stack.getTag().remove(EMBLEM_KEY);
        } else {
            stack.getOrCreateTag().putByteArray(EMBLEM_KEY, data);
        }
    }

    public static int pixel(byte[] data, int x, int y) {
        int i = y * SIZE + x;
        return (data[i >> 2] >> ((i & 3) * 2)) & 3;
    }

    public static void setPixel(byte[] data, int x, int y, int value) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) return;
        int i = y * SIZE + x;
        int shift = (i & 3) * 2;
        data[i >> 2] = (byte) ((data[i >> 2] & ~(3 << shift)) | ((value & 3) << shift));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean placing = PenItem.isPrintout(player.getItemInHand(other));

        if (placing && getEmblem(player.getItemInHand(hand)) == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("item.lakescanner.stamp.blank"), true);
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        if (level.isClientSide) {
            InteractionHand stampHand = hand;
            InteractionHand pageHand = other;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (placing) gg.lakehouse.scanner.client.PenClient.openStampPlacement(stampHand, pageHand);
                else gg.lakehouse.scanner.client.PenClient.openStampEditor(stampHand);
            });
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lakescanner.stamp.tooltip")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getEmblem(stack) != null
                ? "item.lakescanner.stamp.engraved" : "item.lakescanner.stamp.blank_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
