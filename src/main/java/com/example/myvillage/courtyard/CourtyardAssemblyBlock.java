package com.example.myvillage.courtyard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.*;

/** A whole furnishing occupies real cells; only its root owns the one-item drop. */
public abstract class CourtyardAssemblyBlock extends Block implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private static final ThreadLocal<Integer> EDIT_DEPTH=ThreadLocal.withInitial(()->0);
    public abstract String familyId();
    public abstract IntegerProperty variantProperty();
    public abstract IntegerProperty partProperty();
    public CourtyardCatalog.Family family(){return CourtyardCatalog.get(familyId());}
    public CourtyardCatalog.Variant variant(BlockState state){return family().variant(state.getValue(variantProperty()));}
    public int part(BlockState state){return partProperty()==null?0:state.getValue(partProperty());}
    public BlockState state(int variant,int part,Direction facing,boolean water){
        BlockState state=defaultBlockState().setValue(variantProperty(),variant).setValue(FACING,facing).setValue(WATERLOGGED,water);
        return partProperty()==null?state:state.setValue(partProperty(),part);
    }
    protected CourtyardAssemblyBlock(BlockBehaviour.Properties properties){
        super(properties);
        var state=stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(WATERLOGGED,false).setValue(variantProperty(),0);
        registerDefaultState(partProperty()==null?state:state.setValue(partProperty(),0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,WATERLOGGED,variantProperty());if(partProperty()!=null)b.add(partProperty());}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return variant(state).part(part(state)).shape(state.getValue(FACING));}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){
        if(mirror==Mirror.NONE)return state;
        var result=state.setValue(FACING,mirror.mirror(state.getValue(FACING)));
        // Whole parts reverse across local X. All authored patterns are symmetric across X.
        return partProperty()==null?result:result.setValue(partProperty(),variant(state).mirroredPart(part(state)));
    }
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult target,LevelReader level,BlockPos pos,Player player){return CourtyardAssemblyItem.stack(asItem(),state.getValue(variantProperty()));}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override public boolean canPlaceLiquid(Player player,BlockGetter level,BlockPos pos,BlockState state,Fluid fluid){return !variant(state).part(part(state)).full()&&SimpleWaterloggedBlock.super.canPlaceLiquid(player,level,pos,state,fluid);}
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState other,LevelAccessor level,BlockPos pos,BlockPos otherPos){
        if(state.getValue(WATERLOGGED))level.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        return super.updateShape(state,direction,other,level,pos,otherPos);
    }
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    public static void beginEdit(){EDIT_DEPTH.set(EDIT_DEPTH.get()+1);}
    public static void endEdit(){int n=EDIT_DEPTH.get()-1;if(n==0)EDIT_DEPTH.remove();else EDIT_DEPTH.set(n);}
    public BlockPos root(BlockPos pos,BlockState state){return pos.subtract(variant(state).offset(part(state),state.getValue(FACING)));}
    public boolean matches(BlockState candidate,BlockState original,int part){return candidate.is(this)&&candidate.getValue(variantProperty()).equals(original.getValue(variantProperty()))&&candidate.getValue(FACING)==original.getValue(FACING)&&part(candidate)==part;}

    private void removeOthers(Level level,BlockPos pos,BlockState original,boolean dropRoot){
        var v=variant(original);int currentPart=part(original);if(!v.validPart(currentPart))return;
        BlockPos root=root(pos,original);Direction facing=original.getValue(FACING);
        beginEdit();
        try {
            if(currentPart!=0 && level.hasChunkAt(root)&&matches(level.getBlockState(root),original,0)){
                if(dropRoot)level.destroyBlock(root,true);
                else level.setBlock(root,level.getBlockState(root).getFluidState().createLegacyBlock(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            }
            for(int i=1;i<v.partCount();i++){
                BlockPos target=root.offset(v.offset(i,facing));
                if(!target.equals(pos)&&level.hasChunkAt(target)&&matches(level.getBlockState(target),original,i)){
                    var old=level.getBlockState(target);
                    level.setBlock(target,old.getFluidState().createLegacyBlock(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }finally{endEdit();}
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){
        if(!level.isClientSide()&&!level.restoringBlockSnapshots&&EDIT_DEPTH.get()==0&&!next.is(this))removeOthers(level,pos,state,true);
        super.onRemove(state,level,pos,next,moved);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide()&&player.isCreative()&&EDIT_DEPTH.get()==0)removeOthers(level,pos,state,false);
        return super.playerWillDestroy(level,pos,state,player);
    }
}
