package com.example.myvillage.forging.array;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class ForgingArrayBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    private final ForgingArrayRules.Role role;
    public ForgingArrayBlock(ForgingArrayRules.Role role,Properties properties){
        super(properties);this.role=role;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LIT,false));
    }
    public ForgingArrayRules.Role role(){return role;}
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(p->new ForgingArrayBlock(role,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,LIT);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return role==ForgingArrayRules.Role.CONTROLLER?new ForgingArrayBlockEntity(p,s):null;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){
        return !l.isClientSide()&&t==ForgingArrayContent.CONTROLLER_ENTITY.get()?(world,p,state,entity)->((ForgingArrayBlockEntity)entity).serverTick():null;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,
                                                       Player player,InteractionHand hand,BlockHitResult hit){
        // Construction and repair must reach BlockItem.useOn instead of opening a component's menu.
        if(stack.getItem() instanceof BlockItem)return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack,state,level,pos,player,hand,hit);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer server){
            var controller=l.getBlockEntity(p) instanceof ForgingArrayBlockEntity root?root:ForgingArrayPattern.controller(l,p);
            if(controller!=null)server.openMenu(controller);
            else player.displayClientMessage(net.minecraft.network.chat.Component.literal("阵体尚未成形，请右键阵枢查看分层搭建图。"),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter view,BlockPos p,CollisionContext context){
        // Use the authored mesh's coarse solid volumes, sliced into the underlying component cells.
        var owner=ForgingArrayCollision.owner(view,p,role);
        return owner==null?Shapes.block():ForgingArrayCollision.shape(owner.getBlockPos(),owner.getBlockState().getValue(FACING),p);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){
        if(!state.is(next.getBlock())&&!level.isClientSide()&&level.getBlockEntity(pos) instanceof ForgingArrayBlockEntity root){
            Containers.dropContents(level,pos,root.dropSnapshot());root.clearContent();
        }
        super.onRemove(state,level,pos,next,moved);
    }
}
