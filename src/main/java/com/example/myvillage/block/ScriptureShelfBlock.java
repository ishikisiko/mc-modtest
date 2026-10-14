package com.example.myvillage.block;

import com.example.myvillage.block.entity.ScriptureShelfBlockEntity;
import com.example.myvillage.sim.runtime.player.ScriptureHall;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 经架: a directional two-block-high furnishing with one owning-sect block entity at its lower
 * root. Both halves open the same server-authoritative scripture hall. Old cube saves decode as
 * north/lower and acquire their upper half without replacing the root or its saved sect owner.
 */
public final class ScriptureShelfBlock extends Block implements EntityBlock {
    public static final MapCodec<ScriptureShelfBlock> CODEC = simpleCodec(ScriptureShelfBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LOWER_NS = Block.box(0.4, 0, 2.4, 15.6, 16, 13.6);
    private static final VoxelShape UPPER_NS = Block.box(0.4, 0, 2.4, 15.6, 11.52, 13.6);
    private static final VoxelShape LOWER_EW = Block.box(2.4, 0, 0.4, 13.6, 16, 15.6);
    private static final VoxelShape UPPER_EW = Block.box(2.4, 0, 0.4, 13.6, 11.52, 15.6);

    public ScriptureShelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        return state.getValue(FACING).getAxis() == Direction.Axis.Z
                ? (lower ? LOWER_NS : UPPER_NS) : (lower ? LOWER_EW : UPPER_EW);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).isAir()) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.canSurvive(level, pos) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        ensureUpper(level, pos);
    }

    /** Returns the root, including an old unpaired lower root; rejects orphan upper halves. */
    @Nullable
    public static BlockPos rootPosition(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ScriptureShelfBlock)) {
            return null;
        }
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return pos;
        }
        BlockPos root = pos.below();
        return isHalf(level.getBlockState(root), state.getBlock(), DoubleBlockHalf.LOWER) ? root : null;
    }

    private static boolean isHalf(BlockState state, Block block, DoubleBlockHalf half) {
        return state.is(block) && state.getValue(HALF) == half;
    }

    /** The upper repair is allowed only in air or in this root's existing upper half. */
    @Nullable
    public static BlockState upperRepairState(BlockState root, BlockState upper) {
        if (!(root.getBlock() instanceof ScriptureShelfBlock) || root.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        return upper.isAir() || isHalf(upper, root.getBlock(), DoubleBlockHalf.UPPER)
                ? root.setValue(HALF, DoubleBlockHalf.UPPER) : null;
    }

    /** Never replaces the root/BE, an unrelated upper block, or another lower shelf. */
    public static boolean ensureUpper(Level level, BlockPos root) {
        if (root.getY() >= level.getMaxBuildHeight() - 1) {
            return false;
        }
        BlockState current = level.getBlockState(root.above());
        BlockState repair = upperRepairState(level.getBlockState(root), current);
        if (repair == null) {
            return false;
        }
        return repair == current || level.setBlock(root.above(), repair, Block.UPDATE_ALL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return isHalf(below, this, DoubleBlockHalf.LOWER) && below.getValue(FACING) == state.getValue(FACING);
        }
        return !(below.getBlock() instanceof ScriptureShelfBlock)
                && below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER && direction == Direction.DOWN
                && isHalf(neighborState, this, DoubleBlockHalf.LOWER)) {
            return state.setValue(FACING, neighborState.getValue(FACING));
        }
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        // Missing uppers are legal during old-save migration. Actual upper removal is handled
        // in onRemove, so neighbor changes cannot discard a legacy root and its saved ownership.
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (isHalf(level.getBlockState(pos.above()), this, DoubleBlockHalf.UPPER)) {
                    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            } else if (isHalf(level.getBlockState(pos.below()), this, DoubleBlockHalf.LOWER)) {
                // Only the root's loot table yields an item. It is gone before this half's
                // own drop is evaluated, so harvesting either half yields exactly one item.
                level.destroyBlock(pos.below(), true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER
                && isHalf(level.getBlockState(pos.below()), this, DoubleBlockHalf.LOWER)) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new ScriptureShelfBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        // A loaded old root has no new onPlace event. This server-only, bounded repair keeps
        // its existing block entity/NBT and never changes the world-simulation ledger.
        return (world, pos, current, entity) -> {
            if (entity instanceof ScriptureShelfBlockEntity && world.getGameTime() % 20 == 0) {
                ensureUpper(world, pos);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            BlockPos root = rootPosition(level, pos);
            if (root != null) {
                ensureUpper(level, root);
                ScriptureHall.open(serverPlayer, serverLevel, root);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
