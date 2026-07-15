package gg.lakehouse.scanner;

import dan200.computercraft.api.pocket.PocketUpgradeSerialiser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ScannerRegistry {
    public static final String MOD_ID = "lakescanner";

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<PocketUpgradeSerialiser<?>> POCKET_UPGRADES =
        DeferredRegister.create(PocketUpgradeSerialiser.registryId(), MOD_ID);

    public static final RegistryObject<Block> SCANNER = BLOCKS.register("scanner",
        () -> new ScannerBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0f)
            .sound(SoundType.METAL)));

    public static final RegistryObject<Item> SCANNER_ITEM = ITEMS.register("scanner",
        () -> new BlockItem(SCANNER.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, @Nullable Level level,
                                        List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
                tooltip.add(net.minecraft.network.chat.Component
                    .translatable("item.lakescanner.scanner.tooltip").withStyle(ChatFormatting.GRAY));
                tooltip.add(net.minecraft.network.chat.Component
                    .translatable("item.lakescanner.scanner.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
            }
        });

    public static final RegistryObject<BlockEntityType<ScannerBlockEntity>> SCANNER_BE =
        BLOCK_ENTITIES.register("scanner",
            () -> BlockEntityType.Builder.of(ScannerBlockEntity::new, SCANNER.get()).build(null));

    public static final RegistryObject<Item> POCKET_MULTITOOL = ITEMS.register("pocket_multitool",
        () -> new Item(new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, @Nullable Level level,
                                        List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
                tooltip.add(net.minecraft.network.chat.Component
                    .translatable("item.lakescanner.pocket_multitool.tooltip").withStyle(ChatFormatting.GRAY));
            }
        });

    public static final RegistryObject<PocketUpgradeSerialiser<MultitoolUpgrade>> MULTITOOL_UPGRADE =
        POCKET_UPGRADES.register("multitool",
            () -> PocketUpgradeSerialiser.simpleWithCustomItem(MultitoolUpgrade::new));

    public static final RegistryObject<Item> FOLDER = ITEMS.register("folder",
        () -> new FolderItem(new Item.Properties()));

    public static final RegistryObject<MenuType<FolderMenu>> FOLDER_MENU =
        MENUS.register("folder",
            () -> new MenuType<>(FolderMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<Item> PEN = ITEMS.register("pen",
        () -> new PenItem(new Item.Properties()));

    public static final net.minecraftforge.registries.DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        net.minecraftforge.registries.DeferredRegister.create(
            net.minecraftforge.registries.ForgeRegistries.RECIPE_SERIALIZERS, MOD_ID);

    public static final RegistryObject<RecipeSerializer<StampCopyRecipe>> STAMP_COPY =
        RECIPE_SERIALIZERS.register("stamp_copy",
            () -> new SimpleCraftingRecipeSerializer<>(StampCopyRecipe::new));

    public static final RegistryObject<net.minecraft.world.level.block.Block> CAMO_CABLE =
        BLOCKS.register("camo_cable", () -> new CamoCableBlock(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .strength(1.0f).sound(net.minecraft.world.level.block.SoundType.WOOL)));

    public static final RegistryObject<Item> CAMO_CABLE_ITEM = ITEMS.register("camo_cable",
        () -> new BlockItem(CAMO_CABLE.get(), new Item.Properties()));

    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<CamoCableBlockEntity>> CAMO_CABLE_BE =
        BLOCK_ENTITIES.register("camo_cable",
            () -> CamoCableBlock.createBlockEntityType(CAMO_CABLE.get()));

        public static final RegistryObject<Item> STAMP = ITEMS.register("stamp",
        () -> new StampItem(new Item.Properties()));

    public static final RegistryObject<MenuType<ScannerMenu>> SCANNER_MENU =
        MENUS.register("scanner",
            () -> new MenuType<>(ScannerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<CreativeModeTab> SCANNER_TAB =
        CREATIVE_TABS.register("scanner_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lakescanner"))
            .icon(() -> new ItemStack(SCANNER_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(SCANNER_ITEM.get());
                output.accept(POCKET_MULTITOOL.get());
                output.accept(FOLDER.get());
                output.accept(PEN.get());
                output.accept(STAMP.get());
                output.accept(CAMO_CABLE_ITEM.get());
                output.accept(createDeluxePocket());
            })
            .build());

    /**
     * A ready-made Deluxe Pocket Computer: the advanced pocket computer item
     * with our multitool upgrade pre-installed. CC:T stores the installed
     * upgrade as a plain NBT string ("Upgrade"), so no CC:T internals needed.
     */
    private static ItemStack createDeluxePocket() {
        Item pocket = BuiltInRegistries.ITEM.get(
            new ResourceLocation("computercraft", "pocket_computer_advanced"));
        ItemStack stack = new ItemStack(pocket);
        stack.getOrCreateTag().putString("Upgrade", "lakescanner:multitool");
        return stack;
    }

    public static void register(IEventBus modBus) {
        RECIPE_SERIALIZERS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        CREATIVE_TABS.register(modBus);
        POCKET_UPGRADES.register(modBus);
    }

    private ScannerRegistry() {}
}
