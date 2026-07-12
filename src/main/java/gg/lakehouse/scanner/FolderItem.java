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
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.SlotAccess;
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

    /** Cursor holds the folder, right-click a document in a slot: grab it. */
    @Override
    public boolean overrideStackedOnOther(ItemStack folder, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        ItemStack target = slot.getItem();
        if (target.isEmpty() || !isDocument(target)) return false;
        int taken = insert(folder, target);
        if (taken > 0) {
            target.shrink(taken);
            slot.setChanged();
            player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.9f);
        }
        return taken > 0;
    }

    /** Cursor holds a document, right-click it onto the folder: file it. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack folder, ItemStack other, Slot slot,
                                            ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || other.isEmpty()) return false;
        if (!isDocument(other)) return false;
        int taken = insert(folder, other);
        if (taken > 0) {
            other.shrink(taken);
            player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.9f);
        }
        return taken > 0;
    }

    /** Insert as much of the stack as fits (merge, then empty slots). Returns amount taken. */
    private static int insert(ItemStack folder, ItemStack doc) {
        CompoundTag tag = folder.getOrCreateTag();
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);

        var slots = new ItemStack[SLOTS];
        for (int i = 0; i < items.size(); i++) {
            CompoundTag e = items.getCompound(i);
            slots[e.getByte("Slot")] = ItemStack.of(e);
        }

        int remaining = doc.getCount();
        for (int i = 0; i < SLOTS && remaining > 0; i++) { // merge
            if (slots[i] != null && ItemStack.isSameItemSameTags(slots[i], doc)
                && slots[i].getCount() < MAX_PER_SLOT) {
                int add = Math.min(remaining, MAX_PER_SLOT - slots[i].getCount());
                slots[i].grow(add);
                remaining -= add;
            }
        }
        for (int i = 0; i < SLOTS && remaining > 0; i++) { // empty slots
            if (slots[i] == null) {
                int add = Math.min(remaining, MAX_PER_SLOT);
                slots[i] = doc.copyWithCount(add);
                remaining -= add;
            }
        }

        int taken = doc.getCount() - remaining;
        if (taken > 0) {
            ListTag out = new ListTag();
            for (int i = 0; i < SLOTS; i++) {
                if (slots[i] != null && !slots[i].isEmpty()) {
                    CompoundTag e = new CompoundTag();
                    e.putByte("Slot", (byte) i);
                    slots[i].save(e);
                    out.add(e);
                }
            }
            tag.put("Items", out);
        }
        return taken;
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
