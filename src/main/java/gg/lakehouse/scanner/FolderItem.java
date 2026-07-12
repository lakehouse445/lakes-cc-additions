package gg.lakehouse.scanner;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A document folder: 27 slots, documents only (CC printouts + plain paper),
 * contents stack to 16 per slot. A portable shulker box for paperwork.
 */
public class FolderItem extends Item {
    public static final int SLOTS = 27;
    public static final int MAX_PER_SLOT = 16;

    public FolderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** What a folder accepts: CC printouts and plain paper. */
    public static boolean isDocument(ItemStack stack) {
        if (stack.is(Items.PAPER)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "computercraft".equals(id.getNamespace()) && id.getPath().startsWith("printed_");
    }

    public static boolean hasContents(ItemStack folder) {
        CompoundTag tag = folder.getTag();
        return tag != null && !tag.getList("Items", Tag.TAG_COMPOUND).isEmpty();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack folder = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new FolderMenu(id, inv, folder),
                folder.getHoverName()));
        }
        return InteractionResultHolder.sidedSuccess(folder, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lakescanner.folder.tooltip")
            .withStyle(ChatFormatting.GRAY));

        CompoundTag tag = stack.getTag();
        ListTag items = tag != null ? tag.getList("Items", Tag.TAG_COMPOUND) : new ListTag();
        if (items.isEmpty()) {
            tooltip.add(Component.translatable("item.lakescanner.folder.empty")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        int shown = 0, total = 0;
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int count = entry.getByte("Count");
            total += count;
            if (shown < 5) {
                String id = entry.getString("id");
                String label;
                if (id.equals("minecraft:paper")) {
                    label = "Paper x" + count;
                } else {
                    CompoundTag itemTag = entry.getCompound("tag");
                    String title = itemTag.getString("Title");
                    label = title.isEmpty() ? "(untitled)" : title;
                    if (count > 1) label += " x" + count;
                }
                tooltip.add(Component.literal("· " + label).withStyle(ChatFormatting.GRAY));
                shown++;
            }
        }
        if (items.size() > shown) {
            tooltip.add(Component.literal("+ " + (items.size() - shown) + " more...")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.literal(total + " document(s)").withStyle(ChatFormatting.DARK_GRAY));
    }
}
