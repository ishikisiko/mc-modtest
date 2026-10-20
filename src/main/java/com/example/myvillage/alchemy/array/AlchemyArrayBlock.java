package com.example.myvillage.alchemy.array;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class AlchemyArrayBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    private final AlchemyArrayRules.Role role;
    public AlchemyArrayBlock(AlchemyArrayRules.Role role,Properties properties) {
        super(properties);this.role=role;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LIT,false));
    }
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(p->new AlchemyArrayBlock(role,p));}
    public AlchemyArrayRules.Role role(){return role;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,LIT);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) {
        return role==AlchemyArrayRules.Role.VENT?Shapes.or(Shapes.block(),box(3,16,3,13,22,13)):Shapes.block();
    }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return switch(role){
        case CONTROLLER->new AlchemyArrayBlockEntity(p,s);case INPUT,OUTPUT,FUEL->new AlchemyArrayPortBlockEntity(p,s);default->null;};}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t) {
        return !l.isClientSide()&&t==AlchemyArrayContent.CONTROLLER_ENTITY.get()?(world,p,state,entity)->AlchemyArrayBlockEntity.serverTick(world,p,state,(AlchemyArrayBlockEntity)entity):null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit) {
        if(role==AlchemyArrayRules.Role.CONTROLLER&&player instanceof ServerPlayer server&&l.getBlockEntity(p) instanceof AlchemyArrayBlockEntity array)server.openMenu(array);
        return role==AlchemyArrayRules.Role.CONTROLLER?InteractionResult.sidedSuccess(l.isClientSide()):InteractionResult.PASS;
    }
    @Override protected void onRemove(BlockState state,Level l,BlockPos p,BlockState next,boolean moved) {
        if(!state.is(next.getBlock())&&!l.isClientSide()&&l.getBlockEntity(p) instanceof AlchemyArrayBlockEntity array){
            AlchemyArrayPattern.invalidatePorts(l,p,state.getValue(FACING));
            Containers.dropContents(l,p,array.dropSnapshot());array.clearContent();l.updateNeighbourForOutputSignal(p,this);
        }
        super.onRemove(state,l,p,next,moved);
    }
}
