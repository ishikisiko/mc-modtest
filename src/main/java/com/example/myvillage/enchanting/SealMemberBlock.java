package com.example.myvillage.enchanting;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A formed position records its original material and the direction and offset to its sole controller. */
final class SealMemberBlock extends Block {
    private static final IntegerProperty CELL = IntegerProperty.create("cell", 0, EnchantingStructure.SEAL.size() - 1);

    SealMemberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CELL, 0)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CELL, BlockStateProperties.HORIZONTAL_FACING);
    }

    static BlockState part(int index, Direction facing) {
        return EnchantingContent.MEMBER.get().defaultBlockState().setValue(CELL, index)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    private static EnchantingStructure.Cell cell(BlockState state) {
        return EnchantingStructure.SEAL.get(state.getValue(CELL));
    }

    private static BlockPos owner(BlockPos pos, BlockState state) {
        var cell = cell(state);
        return EnchantingStructure.offset(pos, state.getValue(BlockStateProperties.HORIZONTAL_FACING),
                -cell.x(), -cell.y(), -cell.z());
    }

    private static EnchantingDevice controller(ServerLevel level, BlockPos origin, BlockState state) {
        return level.getBlockEntity(origin) instanceof EnchantingDevice device && device.seal()
                && device.front() == state.getValue(BlockStateProperties.HORIZONTAL_FACING) && device.formed() ? device : null;
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var cell = cell(state);
        if (cell.part().equals("minecraft:deepslate_brick_slab")) return box(0, 0, 0, 16, 6, 16);
        if (cell.y() < 0) return Shapes.block();
        return box(1, 0, 1, 15, cell.y() == 0 ? 12 : 4, 15);
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var cell = cell(state);
        if (cell.y() > 0) return Shapes.empty();
        if (cell.y() == -1 && cell.part().equals("seal_tile")) return box(0, 0, 0, 16, 25, 16);
        return getShape(state, level, pos, context);
    }

    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(EnchantingStructure.rawState(cell(state)).getBlock());
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return EnchantingStructure.rawState(cell(state)).getDrops(params);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (level instanceof ServerLevel server && !old.is(this)) schedule(server, pos);
    }

    private void schedule(ServerLevel level, BlockPos pos) {
        level.scheduleTick(pos, this, 80 + Math.floorMod(pos.hashCode(), 37));
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var origin = owner(pos, state);
        if (level.isLoaded(origin) && controller(level, origin, state) == null) {
            var raw = Block.pushEntitiesUp(state, EnchantingStructure.rawState(cell(state)), level, pos);
            level.setBlock(pos, raw, Block.UPDATE_ALL);
        } else schedule(level, pos);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (level instanceof ServerLevel server && !next.is(this)) {
            var origin = owner(pos, state);
            if (server.isLoaded(origin)) {
                var device = controller(server, origin, state);
                if (device != null) device.unform(pos);
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }

}
