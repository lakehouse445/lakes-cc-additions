package gg.lakehouse.scanner;

import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ScannerBlockEntity extends BlockEntity implements Container, MenuProvider {
    private ItemStack scannedItem = ItemStack.EMPTY;

    /** Computers attached to this scanner (thread-safe: attach/detach happen off-thread). */
    final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    public ScannerBlockEntity(BlockPos pos, BlockState state) {
        super(ScannerRegistry.SCANNER_BE.get(), pos, state);
    }

    public ItemStack getScannedItem() {
        return scannedItem;
    }

    public void setScannedItem(ItemStack stack) {
        this.scannedItem = stack;
        setChanged();
        fireScannerEvent();
    }

    /** Queue a "scanner_biometric" event: a player scanned their hand. */
    public void fireBiometricEvent(Player player) {
        for (IComputerAccess computer : computers) {
            try {
                computer.queueEvent("scanner_biometric", computer.getAttachmentName(),
                    player.getName().getString(), player.getStringUUID());
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Queue a "scanner" event on every attached computer when contents change. */
    private void fireScannerEvent() {
        boolean hasItem = !scannedItem.isEmpty();
        for (IComputerAccess computer : computers) {
            try {
                computer.queueEvent("scanner", computer.getAttachmentName(), hasItem);
            } catch (RuntimeException ignored) {
                // Computer detached between iteration and queue; harmless.
            }
        }
    }

    // ------------------------------------------------------------------
    // Container (gives us the GUI slot AND free hopper automation)
    // ------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return scannedItem.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? scannedItem : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || scannedItem.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = scannedItem.split(amount);
        setChanged();
        fireScannerEvent();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = scannedItem;
        scannedItem = ItemStack.EMPTY;
        fireScannerEvent();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 0) setScannedItem(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        setScannedItem(ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------
    // MenuProvider (the disk-drive style GUI)
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.lakescanner.scanner");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ScannerMenu(id, playerInventory, this);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        scannedItem = tag.contains("ScannedItem")
            ? ItemStack.of(tag.getCompound("ScannedItem"))
            : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!scannedItem.isEmpty()) {
            tag.put("ScannedItem", scannedItem.save(new CompoundTag()));
        }
    }
}
