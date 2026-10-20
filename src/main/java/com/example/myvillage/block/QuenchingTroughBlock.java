package com.example.myvillage.block;

import com.example.myvillage.block.entity.QuenchingTroughBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** 一桶容量；水保存在方块状态，展示器坯不成为物品库存。 */
public final class QuenchingTroughBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    public static final MapCodec<QuenchingTroughBlock> CODEC = simpleCodec(QuenchingTroughBlock::new);
    public QuenchingTroughBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(FILLED,false));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,FILLED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new QuenchingTroughBlockEntity(pos,state); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return state.getValue(FACING).getAxis()==Direction.Axis.Z
                ? Shapes.or(box(0,0,2,16,3,14),box(0,3,2,2,11,14),box(14,3,2,16,11,14),box(2,3,2,14,11,4),box(2,3,12,14,11,14))
                : Shapes.or(box(2,0,0,14,3,16),box(2,3,0,14,11,2),box(2,3,14,14,11,16),box(2,3,2,4,11,14),box(12,3,2,14,11,14));
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,
                                                       Player player,InteractionHand hand,BlockHitResult hit) {
        if(!stack.is(Items.WATER_BUCKET)&&!stack.is(Items.BUCKET)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(player.isSpectator()) return ItemInteractionResult.FAIL;
        if(level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if(!(level.getBlockEntity(pos) instanceof QuenchingTroughBlockEntity trough)) return ItemInteractionResult.FAIL;
        if(!trough.available()) {
            player.displayClientMessage(Component.translatable("workshop.myvillage.trough.busy"),true);
            return ItemInteractionResult.CONSUME;
        }
        boolean fill=stack.is(Items.WATER_BUCKET);
        if(fill==state.getValue(FILLED)) return ItemInteractionResult.CONSUME;
        level.setBlock(pos,state.setValue(FILLED,fill),Block.UPDATE_ALL);
        player.setItemInHand(hand,ItemUtils.createFilledResult(stack,player,new ItemStack(fill?Items.BUCKET:Items.WATER_BUCKET)));
        level.playSound(null,pos,fill?SoundEvents.BUCKET_EMPTY:SoundEvents.BUCKET_FILL,SoundSource.BLOCKS,1,1);
        return ItemInteractionResult.CONSUME;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide()) player.displayClientMessage(Component.translatable(state.getValue(FILLED)
                ?"workshop.myvillage.trough.ready":"workshop.myvillage.trough.empty"),true);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
