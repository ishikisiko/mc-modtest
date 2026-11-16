package com.example.myvillage.courtyard;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Both leaves and the reserved swing cells share one server-owned open state. */
public abstract class CourtyardDoorBlock extends CourtyardAssemblyBlock {
    public static final BooleanProperty OPEN=BlockStateProperties.OPEN;

    protected CourtyardDoorBlock(BlockBehaviour.Properties properties){
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OPEN,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        super.createBlockStateDefinition(builder);builder.add(OPEN);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return variant(state).part(part(state),state.getValue(OPEN)).shape(state.getValue(FACING));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.isClientSide())return InteractionResult.SUCCESS;
        return setOpen(level,pos,state,player,!state.getValue(OPEN))?InteractionResult.CONSUME:InteractionResult.FAIL;
    }

    public boolean setOpen(Level level,BlockPos pos,BlockState clicked,Player player,boolean open){
        if(level.isClientSide()||player==null||!player.isAlive()||!clicked.is(this)||clicked.getValue(OPEN)==open)return false;
        var variant=variant(clicked);var facing=clicked.getValue(FACING);var root=root(pos,clicked);
        List<BlockPos> positions=new ArrayList<>();List<BlockState> before=new ArrayList<>(),after=new ArrayList<>();
        for(int part=0;part<variant.partCount();part++){
            var target=root.offset(variant.offset(part,facing));
            if(!level.isInWorldBounds(target)||!level.getWorldBorder().isWithinBounds(target)||!level.hasChunkAt(target)||!level.mayInteract(player,target))return false;
            var previous=level.getBlockState(target);
            if(!matches(previous,clicked,part)||previous.getValue(OPEN)!=clicked.getValue(OPEN))return false;
            var next=previous.setValue(OPEN,open);
            if(!level.isUnobstructed(next,target,CollisionContext.empty()))return false;
            positions.add(target);before.add(previous);after.add(next);
        }
        int written=0;boolean completed=false;beginEdit();
        try{
            for(int i=0;i<positions.size();i++){
                if(!level.setBlock(positions.get(i),after.get(i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))return false;
                written++;
            }
            completed=true;
        }finally{
            if(!completed)for(int i=written-1;i>=0;i--)level.setBlock(positions.get(i),before.get(i),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            endEdit();
        }
        for(var target:positions)level.updateNeighborsAt(target,this);
        level.playSound(null,root,open?SoundEvents.WOODEN_DOOR_OPEN:SoundEvents.WOODEN_DOOR_CLOSE,SoundSource.BLOCKS,1,1);
        level.gameEvent(open?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,root,GameEvent.Context.of(player,after.getFirst()));
        return true;
    }
}
