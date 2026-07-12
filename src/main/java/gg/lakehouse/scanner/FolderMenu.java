package gg.lakehouse.scanner;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Single-chest layout, documents only, 16 per slot, saved into the folder item's NBT. */
public class FolderMenu extends AbstractContainerMenu {
    private final ItemStack folderStack;
    private final SimpleContainer container;

    /** Client constructor (contents are synced by vanilla). */
    public FolderMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ItemStack.EMPTY);
    }

    public FolderMenu(int id, Inventory playerInventory, ItemStack folderStack) {
        super(ScannerRegistry.FOLDER_MENU.get(), id);
        this.folderStack = folderStack;
        this.container = new StackContainer(folderStack);

        // Folder contents: 9x3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new DocumentSlot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new PlayerSlot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return folderStack.isEmpty() // client
            || player.getMainHandItem() == folderStack
            || player.getOffhandItem() == folderStack;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack current = slot.getItem();
        ItemStack original = current.copy();

        if (index < FolderItem.SLOTS) {
            if (!moveItemStackTo(current, FolderItem.SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!FolderItem.isDocument(current)
                || !moveItemStackTo(current, 0, FolderItem.SLOTS, false)) return ItemStack.EMPTY;
        }

        if (current.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    /** Contents slot: documents only. Max stack comes from the container (16). */
    private static class DocumentSlot extends Slot {
        DocumentSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return FolderItem.isDocument(stack);
        }
    }

    /** Player slot that refuses to let you move the open folder itself (no folderception dupes). */
    private class PlayerSlot extends Slot {
        PlayerSlot(Inventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return getItem() != folderStack;
        }
    }

    /** 27 slots persisted into the folder ItemStack's NBT on every change. */
    private static class StackContainer extends SimpleContainer {
        private final ItemStack stack;

        StackContainer(ItemStack stack) {
            super(FolderItem.SLOTS);
            this.stack = stack;
            if (!stack.isEmpty() && stack.getTag() != null) {
                var list = net.minecraft.core.NonNullList.withSize(FolderItem.SLOTS, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(stack.getTag(), list);
                for (int i = 0; i < FolderItem.SLOTS; i++) setItem(i, list.get(i));
            }
        }

        @Override
        public int getMaxStackSize() {
            return FolderItem.MAX_PER_SLOT;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (stack.isEmpty()) return;
            var list = net.minecraft.core.NonNullList.withSize(FolderItem.SLOTS, ItemStack.EMPTY);
            for (int i = 0; i < FolderItem.SLOTS; i++) list.set(i, getItem(i));
            CompoundTag tag = new CompoundTag();
            ContainerHelper.saveAllItems(tag, list, false);
            if (tag.contains("Items")) {
                stack.getOrCreateTag().put("Items", tag.getList("Items", 10));
            } else if (stack.getTag() != null) {
                stack.getTag().remove("Items");
            }
        }
    }
}
