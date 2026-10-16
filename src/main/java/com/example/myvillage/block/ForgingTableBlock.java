package com.example.myvillage.block;

import com.example.myvillage.forging.ForgingDingVisuals;
import com.example.myvillage.forging.ForgingMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Iron/copper ding on the original registry ID; inventories stay private to each viewer. */
public final class ForgingTableBlock extends Block {
    public static final MapCodec<ForgingTableBlock> CODEC=simpleCodec(ForgingTableBlock::new);
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public ForgingTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH).setValue(LIT,false));
    }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,LIT); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return ForgingDingVisuals.get().shape(state.getValue(FACING));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(player instanceof ServerPlayer p)p.openMenu(new SimpleMenuProvider(
                (id,inventory,viewer)->new ForgingMenu(id,inventory,ContainerLevelAccess.create(level,pos)),
                Component.translatable("block.myvillage.forging_table")));
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    /** Called only after the server commits a successful forge or repair. This never makes items. */
    public static void completed(ServerLevel level,BlockPos pos) {
        BlockState state=level.getBlockState(pos);
        if (!(state.getBlock() instanceof ForgingTableBlock block)) return;
        var visuals=ForgingDingVisuals.get();var feedback=visuals.feedback();
        if (!state.getValue(LIT)) {
            level.setBlock(pos,state.setValue(LIT,true),Block.UPDATE_ALL);
            level.scheduleTick(pos,block,feedback.durationTicks());
        }
        level.sendParticles(ParticleTypes.LAVA,pos.getX()+visuals.hearthX(),pos.getY()+visuals.hearthY(),pos.getZ()+visuals.hearthZ(),
                feedback.sparkCount(),feedback.sparkSpread(),feedback.sparkSpread(),feedback.sparkSpread(),feedback.sparkSpeed());
        level.playSound(null,pos,SoundEvents.ANVIL_USE,SoundSource.BLOCKS,feedback.soundVolume(),feedback.soundPitch());
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved) {
        super.onPlace(state,level,pos,old,moved);
        if (!level.isClientSide() && state.getValue(LIT)) level.scheduleTick(pos,this,ForgingDingVisuals.get().feedback().durationTicks());
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if (state.getValue(LIT)) level.setBlock(pos,state.setValue(LIT,false),Block.UPDATE_ALL);
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random) {
        var v=ForgingDingVisuals.get();var f=v.feedback();
        if (!state.getValue(LIT) || random.nextFloat()>=f.ambientProbability()) return;
        double x=pos.getX()+v.hearthX()+(random.nextDouble()-.5)*f.sparkSpread();
        double y=pos.getY()+v.hearthY();
        double z=pos.getZ()+v.hearthZ()+(random.nextDouble()-.5)*f.sparkSpread();
        level.addParticle(ParticleTypes.SMALL_FLAME,x,y,z,0,f.sparkSpeed(),0);
        level.addParticle(ParticleTypes.SMOKE,x,y,z,0,f.sparkSpeed(),0);
    }
}
