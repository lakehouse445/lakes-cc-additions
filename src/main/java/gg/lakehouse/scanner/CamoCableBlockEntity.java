package gg.lakehouse.scanner;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the camouflage state and participates in CC:T wired networks as
 * an element (a full-block cable, in effect).
 */
public class CamoCableBlockEntity extends BlockEntity {
    public static final Capability<WiredElement> WIRED_ELEMENT =
        CapabilityManager.get(new CapabilityToken<>() {});

    @Nullable
    private BlockState camo;

    private final WiredElement element = new WiredElement() {
        @Override
        public Level getLevel() {
            return CamoCableBlockEntity.this.level;
        }

        @Override
        public net.minecraft.world.phys.Vec3 getPosition() {
            BlockPos pos = getBlockPos();
            return new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }

        @Override
        public String getSenderID() {
            return "camo_cable";
        }

        @Override
        public WiredNode getNode() {
            return node;
        }
    };

    private final WiredNode node = ComputerCraftAPI.createWiredNodeForElement(element);
    private final LazyOptional<WiredElement> elementCap = LazyOptional.of(() -> element);

    public CamoCableBlockEntity(BlockPos pos, BlockState state) {
        super(ScannerRegistry.CAMO_CABLE_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- camo

    @Nullable
    public BlockState getCamo() {
        return camo;
    }

    public void setCamo(@Nullable BlockState state) {
        this.camo = state;
        setChanged();
        if (level != null) {
            if (level.isClientSide) requestModelDataUpdate();
            // On the server this broadcasts the change; on the client it marks the
            // section for rebuild unconditionally. (setBlocksDirty must NOT be used
            // here: with old == new it fails the requiresRender check and the
            // rebuild is dropped, leaving the camo invisible until some later
            // neighbour update rebuilds the section.)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public @org.jetbrains.annotations.NotNull ModelData getModelData() {
        return ModelData.builder()
            .with(gg.lakehouse.scanner.client.CamoModelData.CAMO, camo)
            .build();
    }

    // ---------------------------------------------------------------- network

    /** Connect our node to adjacent wired elements (cables, modems, other camo cables). */
    public void connectionsChanged() {
        if (level == null || level.isClientSide) return;
        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbor == null) continue;
            neighbor.getCapability(WIRED_ELEMENT, dir.getOpposite()).ifPresent(other ->
                node.connectTo(other.getNode()));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) connectionsChanged();
    }

    public void destroy() {
        node.remove();
        elementCap.invalidate();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == WIRED_ELEMENT) return elementCap.cast();
        return super.getCapability(cap, side);
    }

    // ---------------------------------------------------------------- nbt/sync

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (camo != null) tag.put("Camo", NbtUtils.writeBlockState(camo));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        camo = tag.contains("Camo")
            ? NbtUtils.readBlockState(level != null
                ? level.holderLookup(net.minecraft.core.registries.Registries.BLOCK)
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Camo"))
            : null;
        if (camo != null && camo.isAir()) camo = null;
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            // sendBlockUpdated, not setBlocksDirty: see setCamo.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
