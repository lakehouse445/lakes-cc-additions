package gg.lakehouse.scanner;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Craft an engraved stamp with a blank stamp to copy the emblem. The
 * original engraved stamp is returned (like written book copying).
 */
public class StampCopyRecipe extends CustomRecipe {
    public StampCopyRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private static ItemStack findEngraved(CraftingContainer container) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.getItem() instanceof StampItem && StampItem.getEmblem(s) != null) {
                if (!found.isEmpty()) return ItemStack.EMPTY; // more than one
                found = s;
            }
        }
        return found;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int blank = 0, engraved = 0, other = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof StampItem) {
                if (StampItem.getEmblem(s) != null) engraved++;
                else blank++;
            } else other++;
        }
        return engraved == 1 && blank == 1 && other == 0;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        ItemStack engraved = findEngraved(container);
        if (engraved.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = new ItemStack(ScannerRegistry.STAMP.get());
        StampItem.setEmblem(copy, StampItem.getEmblem(engraved));
        return copy;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.getItem() instanceof StampItem && StampItem.getEmblem(s) != null) {
                remaining.set(i, s.copy()); // keep the original
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ScannerRegistry.STAMP_COPY.get();
    }
}
