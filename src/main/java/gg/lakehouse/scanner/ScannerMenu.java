package gg.lakehouse.scanner;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Disk-drive style menu: one item slot at (80, 35) plus the player inventory.
 * Slot layout mirrors CC:T's DiskDriveMenu so we can reuse its GUI texture.
 */
public class ScannerMenu extends AbstractContainerMenu {
    private final Container container;

    /** Client-side constructor (the container is synced by vanilla). */
    public ScannerMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(1));
    }

    public ScannerMenu(int id, Inventory playerInventory, Container container) {
        super(ScannerRegistry.SCANNER_MENU.get(), id);
        this.container = container;
        container.startOpen(playerInventory.player);

        // The scanner bed
        addSlot(new Slot(container, 0, 80, 35));

        // Player inventory
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new Slot(playerInventory, x + y * 9 + 9, 8 + x * 18, 84 + y * 18));
            }
        }
        // Hotbar
        for (int x = 0; x < 9; x++) {
            addSlot(new Slot(playerInventory, x, 8 + x * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack current = slot.getItem();
        ItemStack original = current.copy();

        if (index == 0) {
            // Scanner slot -> player inventory
            if (!moveItemStackTo(current, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            // Player inventory -> scanner slot
            if (!moveItemStackTo(current, 0, 1, false)) return ItemStack.EMPTY;
        }

        if (current.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
