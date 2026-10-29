package com.example.myvillage.streetfurniture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 实际占用两格或四格的独立设施；只有编号 0 的根格掉落一件物品。 */
public abstract class StreetFurnitureBlock extends Block implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private static final ThreadLocal<Integer> EDIT_DEPTH=ThreadLocal.withInitial(()->0);
    public abstract String furnitureId();
    public abstract IntegerProperty partProperty();
    public StreetFurnitureCatalog.Family family(){return StreetFurnitureCatalog.get(furnitureId());}
    public int part(BlockState state){return state.getValue(partProperty());}
    public BlockState state(int part,Direction facing,boolean water){family().part(part);return defaultBlockState().setValue(partProperty(),part).setValue(FACING,facing).setValue(WATERLOGGED,water);}

    protected StreetFurnitureBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(WATERLOGGED,false).setValue(partProperty(),0));}
    public static BlockBehaviour.Properties properties(String id){
        var f=StreetFurnitureCatalog.get(id);
        return BlockBehaviour.Properties.of().mapColor(id.equals("rune_lamp")?MapColor.METAL:MapColor.WOOD).strength(f.strength()).noOcclusion()
                .sound(f.sound().equals("metal")?SoundType.LANTERN:SoundType.WOOD).pushReaction(PushReaction.BLOCK)
                .lightLevel(state->{var b=(StreetFurnitureBlock)state.getBlock();return b.family().part(b.part(state)).light();});
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,WATERLOGGED,partProperty());}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return family().part(part(state)).outline(state.getValue(FACING));}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return family().part(part(state)).collision(state.getValue(FACING));}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return mirror==Mirror.NONE?state:state.setValue(FACING,mirror.mirror(state.getValue(FACING))).setValue(partProperty(),family().mirroredPart(part(state)));}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult target,LevelReader level,BlockPos pos,Player player){return new ItemStack(asItem());}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    public static void beginEdit(){EDIT_DEPTH.set(EDIT_DEPTH.get()+1);}
    public static void endEdit(){int value=EDIT_DEPTH.get()-1;if(value==0)EDIT_DEPTH.remove();else EDIT_DEPTH.set(value);}
    public BlockPos root(BlockPos pos,BlockState state){return pos.subtract(family().offset(part(state),state.getValue(FACING)));}
    public boolean matches(BlockState candidate,BlockState original,int index){return candidate.is(this)&&candidate.getValue(FACING)==original.getValue(FACING)&&part(candidate)==index;}

    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos){
        if(state.getValue(WATERLOGGED))level.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        if(EDIT_DEPTH.get()==0&&direction==Direction.DOWN&&family().part(part(state)).offset().getY()==0
                &&!neighbor.isFaceSturdy(level,neighborPos,Direction.UP))return state.getFluidState().createLegacyBlock();
        return super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }

    private void removeOthers(Level level,BlockPos pos,BlockState original,boolean dropRoot){
        var family=family();BlockPos root=root(pos,original);Direction facing=original.getValue(FACING);beginEdit();
        try{
            if(part(original)!=0&&level.hasChunkAt(root)&&matches(level.getBlockState(root),original,0)){
                if(dropRoot)level.destroyBlock(root,true);
                else level.setBlock(root,level.getBlockState(root).getFluidState().createLegacyBlock(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            }
            for(int i=1;i<family.parts().size();i++){
                BlockPos target=root.offset(family.offset(i,facing));
                if(!target.equals(pos)&&level.hasChunkAt(target)&&matches(level.getBlockState(target),original,i))
                    level.setBlock(target,level.getBlockState(target).getFluidState().createLegacyBlock(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
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
