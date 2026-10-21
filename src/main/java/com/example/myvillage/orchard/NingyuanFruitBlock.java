package com.example.myvillage.orchard;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class NingyuanFruitBlock extends Block implements BonemealableBlock {
    public static final IntegerProperty AGE=IntegerProperty.create("age",0,2);
    public static final BooleanProperty ENRICHED=BooleanProperty.create("enriched");
    public static final MapCodec<NingyuanFruitBlock> CODEC=simpleCodec(NingyuanFruitBlock::new);
    public NingyuanFruitBlock(Properties properties) {
        super(properties);registerDefaultState(stateDefinition.any().setValue(AGE,0).setValue(ENRICHED,false));
    }
    @Override public MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(AGE,ENRICHED); }
    private static FruitCycle cycle(BlockState state) { return new FruitCycle(state.getValue(AGE),state.getValue(ENRICHED)); }
    private static BlockState withCycle(BlockState state,FruitCycle c) { return state.setValue(AGE,c.age()).setValue(ENRICHED,c.enriched()); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return OrchardGeometry.fruit(state.getValue(AGE)); }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        BlockState support=level.getBlockState(pos.above());
        return support.is(OrchardContent.LEAVES.get()) && support.getValue(LeavesBlock.DISTANCE)<7 && !support.getValue(LeavesBlock.WATERLOGGED);
    }
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState other,LevelAccessor level,BlockPos pos,BlockPos otherPos) {
        return direction==Direction.UP && !canSurvive(state,level,pos)?Blocks.AIR.defaultBlockState():super.updateShape(state,direction,other,level,pos,otherPos);
    }
    @Override public boolean isRandomlyTicking(BlockState state) { return state.getValue(AGE)<2; }
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        var rules=OrchardRules.get();
        if(level.isAreaLoaded(pos,1) && canSurvive(state,level,pos) && level.getMaxLocalRawBrightness(pos)>=rules.minimumLight()
                && net.neoforged.neoforge.common.CommonHooks.canCropGrow(level,pos,state,random.nextInt(rules.fruitChanceTicks())==0)) {
            level.setBlock(pos,withCycle(state,cycle(state).grow()),2);
            net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(level,pos,state);
        }
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(stack.is(OrchardContent.FERTILIZER.get())) {
            if(!cycle(state).canEnrich()) {
                if(!level.isClientSide) player.displayClientMessage(Component.literal("培元肥仅用于未施肥的花芽，一份供这一轮结出上品果。"),true);
                return ItemInteractionResult.FAIL;
            }
            if(!level.isClientSide) {
                level.setBlock(pos,withCycle(state,cycle(state).enrich()),3);stack.consume(1,player);
                level.playSound(null,pos,SoundEvents.BONE_MEAL_USE,SoundSource.BLOCKS,1,1);
                level.gameEvent(GameEvent.BLOCK_CHANGE,pos,GameEvent.Context.of(player,state));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return stack.is(Items.BONE_MEAL) && !cycle(state).ripe()?ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                :super.useItemOn(stack,state,level,pos,player,hand,hit);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        var c=cycle(state);
        if(!c.ripe()) return InteractionResult.PASS;
        if(!level.isClientSide) {
            var rules=OrchardRules.get();
            var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(c.enriched()?rules.highItem():rules.lowItem()));
            level.setBlock(pos,withCycle(state,c.afterHarvest()),3);
            popResource(level,pos,new ItemStack(item,rules.harvestCount()));
            level.playSound(null,pos,SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,SoundSource.BLOCKS,1,1);
            level.gameEvent(GameEvent.BLOCK_CHANGE,pos,GameEvent.Context.of(player,state));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public boolean isValidBonemealTarget(LevelReader level,BlockPos pos,BlockState state) { return cycle(state).age()<2 && canSurvive(state,level,pos); }
    @Override public boolean isBonemealSuccess(Level level,RandomSource random,BlockPos pos,BlockState state) { return true; }
    @Override public void performBonemeal(ServerLevel level,RandomSource random,BlockPos pos,BlockState state) {
        if(isValidBonemealTarget(level,pos,state)) level.setBlock(pos,withCycle(state,cycle(state).grow()),2);
    }
}
