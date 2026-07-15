package gg.lakehouse.scanner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A wired network cable disguised as any full block. Right-click with a
 * full opaque block to apply camouflage (consumes one); sneak-punch to
 * strip it (camouflage is not returned). Joins CC:T wired networks like a
 * cable; connect CC cables or wired modems to any face.
 */
public class CamoCableBlock extends BaseEntityBlock {
    public static final BooleanProperty DISGUISED = BooleanProperty.create("disguised");
    /** Per-side connection flags, drives the cable arms in the blockstate model. */
    public static final Map<Direction, BooleanProperty> CONNECTIONS = PipeBlock.PROPERTY_BY_DIRECTION;

    // Same dimensions as CC:T's cable: 4px core with 3px arms to each face.
    private static final VoxelShape CORE = box(6, 6, 6, 10, 10, 10);
    private static final VoxelShape[] ARMS = { // indexed by Direction.get3DDataValue()
        box(6, 0, 6, 10, 6, 10),   // down
        box(6, 10, 6, 10, 16, 10), // up
        box(6, 6, 0, 10, 10, 6),   // north
        box(6, 6, 10, 10, 10, 16), // south
        box(0, 6, 6, 6, 10, 10),   // west
        box(10, 6, 6, 16, 10, 10)  // east
    };
    private static final VoxelShape[] SHAPE_CACHE = new VoxelShape[64];

    public CamoCableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(DISGUISED, false);
        for (BooleanProperty side : CONNECTIONS.values()) state = state.setValue(side, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DISGUISED);
        CONNECTIONS.values().forEach(builder::add);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(DISGUISED)) return Shapes.block();
        int key = 0;
        for (Direction dir : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(dir))) key |= 1 << dir.get3DDataValue();
        }
        VoxelShape shape = SHAPE_CACHE[key];
        if (shape == null) {
            shape = CORE;
            for (Direction dir : Direction.values()) {
                if ((key & 1 << dir.get3DDataValue()) != 0) shape = Shapes.or(shape, ARMS[dir.get3DDataValue()]);
            }
            SHAPE_CACHE[key] = shape;
        }
        return shape;
    }

    /** True when the neighbour on this side exposes a wired element towards us. */
    private static boolean connectsTo(BlockGetter level, BlockPos pos, Direction dir) {
        BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
        return neighbor != null
            && neighbor.getCapability(CamoCableBlockEntity.WIRED_ELEMENT, dir.getOpposite()).isPresent();
    }

    private static BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(dir), connectsTo(level, pos, dir));
        }
        return state;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Undisguised: the CC:T cable multipart model. Disguised: CamoCableModel
        // swaps in the camouflage block's real quads at chunk bake.
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof BlockItem blockItem)) return InteractionResult.PASS;
        BlockState camo = blockItem.getBlock().defaultBlockState();
        if (!camo.isSolidRender(level, pos) || camo.getBlock() instanceof BaseEntityBlock) {
            return InteractionResult.PASS; // full opaque cubes only, no block entities
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CamoCableBlockEntity cable) {
            cable.setCamo(camo);
            level.setBlock(pos, state.setValue(DISGUISED, true), 3);
            if (!player.getAbilities().instabuild) held.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player.isShiftKeyDown()
            && level.getBlockEntity(pos) instanceof CamoCableBlockEntity cable) {
            cable.setCamo(null);
            level.setBlock(pos, state.setValue(DISGUISED, false), 3);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CamoCableBlockEntity cable) {
            cable.connectionsChanged();
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (level.isClientSide) return;
        BlockState updated = withConnections(level, pos, state);
        if (updated != state) level.setBlock(pos, updated, 3);
        if (level.getBlockEntity(pos) instanceof CamoCableBlockEntity cable) {
            cable.connectionsChanged();
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
            && level.getBlockEntity(pos) instanceof CamoCableBlockEntity cable) {
            cable.destroy();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CamoCableBlockEntity(pos, state);
    }

    public static BlockEntityType<CamoCableBlockEntity> createBlockEntityType(Block block) {
        return BlockEntityType.Builder.of(CamoCableBlockEntity::new, block).build(null);
    }
}
