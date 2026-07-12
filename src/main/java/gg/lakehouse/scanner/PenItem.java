package gg.lakehouse.scanner;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * Hold a printed page in one hand and use the pen with the other to draw
 * on the page -- freehand ink at font-pixel resolution, rendered above the
 * printed characters.
 */
public class PenItem extends Item {
    public PenItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    public static boolean isPrintout(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "computercraft".equals(id.getNamespace()) && id.getPath().startsWith("printed_");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        if (!isPrintout(player.getItemInHand(other))) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("item.lakescanner.pen.needs_page"), true);
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        if (level.isClientSide) {
            InteractionHand pageHand = other;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> gg.lakehouse.scanner.client.PenClient.open(pageHand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lakescanner.pen.tooltip")
            .withStyle(ChatFormatting.GRAY));
    }
}
