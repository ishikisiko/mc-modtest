package com.example.myvillage.block;

import com.example.myvillage.forging.WorkshopMenu;
import com.example.myvillage.block.entity.ForgingAnvilBlockEntity;
import com.example.myvillage.item.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.BlockHitResult;

/** Right-click transfers the real workpiece or strikes it. Sneak-use keeps the other workshop operations. */
public final class ForgingAnvilBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
    public static final MapCodec<ForgingAnvilBlock> CODEC=simpleCodec(ForgingAnvilBlock::new);
    public ForgingAnvilBlock(Properties props){super(props);registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH).setValue(LIT,false));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,LIT);}
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
        return new ForgingAnvilBlockEntity(pos,state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide()||type!=ModBlockEntities.FORGING_ANVIL.get()?null:
                (world,pos,current,entity)->ForgingAnvilBlockEntity.serverTick(world,pos,current,(ForgingAnvilBlockEntity)entity);
    }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,
            BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.or(Block.box(0,0,0,16,12,16),Block.box(2,12,3,14,16,13));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,
            Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.isEmpty()||player.isShiftKeyDown())return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(hand!=InteractionHand.MAIN_HAND||player.isSpectator())return ItemInteractionResult.FAIL;
        if(level.isClientSide())return ItemInteractionResult.SUCCESS;
        if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity anvil){
            if(stack.is(ModItems.FORGING_HAMMER.get()))anvil.strike(server);
            else anvil.place(server,hand);
        }
        return ItemInteractionResult.CONSUME;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player.isSpectator()||!player.isAlive())return InteractionResult.FAIL;
        if(player instanceof ServerPlayer server){
            if (!(level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity anvil) || !anvil.available()) {
                server.displayClientMessage(Component.translatable("workshop.myvillage.occupied"),true);
                return InteractionResult.CONSUME;
            }
            if(!player.isShiftKeyDown()){
                anvil.take(server);
                return InteractionResult.CONSUME;
            }
            server.openMenu(new SimpleMenuProvider(
                (id,inventory,viewer)->new WorkshopMenu(id,inventory,ContainerLevelAccess.create(level,pos)),
                Component.translatable("workshop.myvillage.title")));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){
        if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity anvil)anvil.dropContents();
        super.onRemove(state,level,pos,next,moved);
    }
}
