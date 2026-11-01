package com.example.myvillage.worldplan.roads;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** 青石护栏：半砖支撑下移半格，施工连接冻结，玩家放拆时更新邻栏。 */
public final class RoadGuardrailBlock extends Block {
    public static final MapCodec<RoadGuardrailBlock> CODEC=simpleCodec(RoadGuardrailBlock::new);
    public static final IntegerProperty MASK=IntegerProperty.create("mask",0,255);
    public static final BooleanProperty LOWERED=BooleanProperty.create("lowered"),PLANNED=BooleanProperty.create("planned");
    private static final Direction[] DIRS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static final ThreadLocal<Boolean> WRITING=ThreadLocal.withInitial(()->false);
    private final RoadGuardrailRules rules;
    private final VoxelShape[] outline=new VoxelShape[512],collision=new VoxelShape[512];
    public RoadGuardrailBlock(Properties p){this(p,RoadGuardrailRules.load());}
    public RoadGuardrailBlock(Properties p,RoadGuardrailRules rules){super(p);this.rules=rules;registerDefaultState(stateDefinition.any().setValue(MASK,0).setValue(LOWERED,false).setValue(PLANNED,false));}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(MASK,LOWERED,PLANNED);}
    public static final class Placement implements AutoCloseable {
        private final boolean previous=WRITING.get();
        private Placement(){WRITING.set(true);}
        @Override public void close(){WRITING.set(previous);}
    }
    public static Placement placing(){return new Placement();}
    private synchronized VoxelShape shape(BlockState state,boolean collide) {
        int key=state.getValue(MASK)+(state.getValue(LOWERED)?256:0);var cache=collide?collision:outline;if(cache[key]!=null)return cache[key];
        VoxelShape result=Shapes.empty();for(double[] b:rules.boxes(state.getValue(MASK),state.getValue(LOWERED),collide))result=Shapes.or(result,Block.box(b[0],b[1],b[2],b[3],b[4],b[5]));
        return cache[key]=result.optimize();
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shape(s,false);}
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shape(s,true);}
    private static boolean lowered(BlockState below){return below.getBlock() instanceof SlabBlock&&below.getValue(SlabBlock.TYPE)==SlabType.BOTTOM;}
    @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){var below=l.getBlockState(p.below());return below.getBlock() instanceof SlabBlock||below.isFaceSturdy(l,p.below(),Direction.UP);}
    private static int base2(BlockState s,BlockPos p){return p.getY()*2-(s.getValue(LOWERED)?1:0);}
    private static int connections(BlockState self,BlockGetter world,BlockPos pos) {
        int mask=0,base=base2(self,pos);
        for(int i=0;i<4;i++)for(int dy:new int[]{0,-1,1}){
            BlockPos p=pos.relative(DIRS[i]).above(dy);BlockState n=world.getBlockState(p);if(!(n.getBlock() instanceof RoadGuardrailBlock))continue;
            int delta=base2(n,p)-base;if(Math.abs(delta)>2)continue;mask|=1<<i;if(delta>1)mask|=16<<i;break;
        }return mask;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){var state=defaultBlockState().setValue(LOWERED,lowered(c.getLevel().getBlockState(c.getClickedPos().below())));return state.setValue(MASK,connections(state,c.getLevel(),c.getClickedPos()));}
    private static void refreshAround(Level world,BlockPos center) {
        if(world.isClientSide||WRITING.get())return;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=1;dy++){
            if(Math.abs(dx)+Math.abs(dz)>1)continue;BlockPos p=center.offset(dx,dy,dz);BlockState state=world.getBlockState(p);if(!(state.getBlock() instanceof RoadGuardrailBlock))continue;
            BlockState next=state.setValue(MASK,connections(state,world,p));if(next!=state)world.setBlock(p,next,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        }
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){if(!state.getValue(PLANNED)&&!old.is(this))refreshAround(level,pos);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){super.onRemove(state,level,pos,next,moving);if(!next.is(this))refreshAround(level,pos);}
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other) {
        if(direction==Direction.DOWN&&!canSurvive(state,level,pos))return Blocks.AIR.defaultBlockState();
        if(WRITING.get()||state.getValue(PLANNED))return state;
        var next=state.setValue(LOWERED,lowered(level.getBlockState(pos.below())));return next.setValue(MASK,connections(next,level,pos));
    }
    private static int index(Direction d){return switch(d){case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException();};}
    @Override protected BlockState rotate(BlockState s,Rotation r){int old=s.getValue(MASK),mask=0;for(int i=0;i<4;i++){int j=index(r.rotate(DIRS[i]));if((old&(1<<i))!=0)mask|=1<<j;if((old&(16<<i))!=0)mask|=16<<j;}return s.setValue(MASK,mask);}
    @Override protected BlockState mirror(BlockState s,Mirror m){int old=s.getValue(MASK),mask=0;for(int i=0;i<4;i++){Direction d=DIRS[i];if(m==Mirror.LEFT_RIGHT&&d.getAxis()==Direction.Axis.Z||m==Mirror.FRONT_BACK&&d.getAxis()==Direction.Axis.X)d=d.getOpposite();int j=index(d);if((old&(1<<i))!=0)mask|=1<<j;if((old&(16<<i))!=0)mask|=16<<j;}return s.setValue(MASK,mask);}
}
