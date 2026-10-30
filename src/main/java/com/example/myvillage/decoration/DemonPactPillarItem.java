package com.example.myvillage.decoration;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;

/** The server checks every cell before writing anything or consuming the item. */
public final class DemonPactPillarItem extends BlockItem {
    private final DemonPactPillarBlock pillar;
    public DemonPactPillarItem(DemonPactPillarBlock pillar,Properties properties) { super(pillar,properties);this.pillar=pillar; }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.myvillage.demon_pact_pillar").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.myvillage.demon_pact_pillar.anchor").withStyle(ChatFormatting.GRAY));
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
        var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();
        if(player==null||context.getClickedFace()!=Direction.UP||!context.canPlace()||!pillar.isEnabled(level.enabledFeatures()))return InteractionResult.FAIL;
        var shape=DemonPactPillarShape.get();var facing=context.getHorizontalDirection().getOpposite();BlockPos root=context.getClickedPos();
        List<BlockPos> positions=new ArrayList<>();List<BlockState> before=new ArrayList<>(),after=new ArrayList<>();
        for(int i=0;i<shape.count();i++) {
            BlockPos pos=root.offset(shape.offset(i,facing));
            if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.hasChunkAt(pos)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,Direction.UP,stack))return InteractionResult.FAIL;
            var old=level.getBlockState(pos);var local=BlockPlaceContext.at(context,pos,Direction.UP);
            if(!old.canBeReplaced(local)||!old.getFluidState().isEmpty()||level.getBlockEntity(pos)!=null)return InteractionResult.FAIL;
            if(shape.part(i).offset().getY()==0&&!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))return InteractionResult.FAIL;
            var state=pillar.state(i,facing);
            if(!level.isUnobstructed(state,pos,CollisionContext.of(player)))return InteractionResult.FAIL;
            positions.add(pos);before.add(old);after.add(state);
        }
        if(level.isClientSide())return InteractionResult.SUCCESS;
        boolean completed=false;int written=0;DemonPactPillarBlock.beginEdit();
        try {
            for(int i=0;i<positions.size();i++) {
                if(!level.setBlock(positions.get(i),after.get(i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))return InteractionResult.FAIL;
                written++;
            }
            completed=true;
        } finally {
            if(!completed)for(int i=written-1;i>=0;i--)level.setBlock(positions.get(i),before.get(i),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            DemonPactPillarBlock.endEdit();
        }
        for(BlockPos pos:positions)level.updateNeighborsAt(pos,pillar);
        var state=after.getFirst();var sound=state.getSoundType(level,root,player);
        level.playSound(null,root,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE,root,GameEvent.Context.of(player,state));
        if(player instanceof ServerPlayer server)CriteriaTriggers.PLACED_BLOCK.trigger(server,root,stack);
        stack.consume(1,player);return InteractionResult.CONSUME;
    }
}
