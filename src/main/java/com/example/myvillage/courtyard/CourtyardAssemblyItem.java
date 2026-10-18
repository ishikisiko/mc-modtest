package com.example.myvillage.courtyard;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Server validates the full footprint before any write or consumption. */
public final class CourtyardAssemblyItem extends BlockItem {
    private final CourtyardAssemblyBlock assembly;
    public CourtyardAssemblyItem(CourtyardAssemblyBlock block,Properties properties){
        super(block,properties.component(DataComponents.BLOCK_STATE,new BlockItemStateProperties(Map.of("variant","0")))
                .component(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(1)));assembly=block;
    }
    public CourtyardAssemblyBlock assembly(){return assembly;}
    public int variant(ItemStack stack){
        try{int index=Integer.parseInt(stack.getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).properties().getOrDefault("variant","0"));return index>=0&&index<assembly.family().variants().size()?index:0;}
        catch(NumberFormatException e){return 0;}
    }
    public static ItemStack stack(Item item,int variant){var result=new ItemStack(item);setVariant(result,variant);return result;}
    public static void setVariant(ItemStack stack,int variant){
        if(!(stack.getItem() instanceof CourtyardAssemblyItem item)||variant<0||variant>=item.assembly.family().variants().size())throw new IllegalArgumentException("Invalid held building variant");
        stack.set(DataComponents.BLOCK_STATE,new BlockItemStateProperties(Map.of("variant",Integer.toString(variant))));stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(variant+1));
    }
    public static boolean accepts(ItemStack stack,ResourceLocation expected,int variant){
        return stack.getItem() instanceof CourtyardAssemblyItem item&&BuiltInRegistries.ITEM.getKey(item).equals(expected)&&variant>=0&&variant<item.assembly.family().variants().size();
    }
    public static boolean select(ServerPlayer player,InteractionHand hand,ResourceLocation expected,int variant){
        if(!player.isAlive()||player.isRemoved())return false;
        var stack=player.getItemInHand(hand);
        if(!accepts(stack,expected,variant))return false;
        var item=(CourtyardAssemblyItem)stack.getItem();
        if(item.variant(stack)!=variant){setVariant(stack,variant);player.getInventory().setChanged();player.containerMenu.broadcastChanges();}
        return true;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        int selected=variant(stack);var v=assembly.family().variant(selected);
        lines.add(Component.translatable("variant.myvillage."+assembly.familyId()+"."+selected).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.myvillage.courtyard.size",v.width(),v.height(),v.depth()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.myvillage.courtyard.select").withStyle(ChatFormatting.GRAY));
    }
    @Override public InteractionResult place(BlockPlaceContext context){
        var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();
        if(player==null||player.isShiftKeyDown()||!context.canPlace()||!assembly.isEnabled(level.enabledFeatures()))return InteractionResult.FAIL;
        int selected=variant(stack);var v=assembly.family().variant(selected);var facing=context.getHorizontalDirection().getOpposite();BlockPos root=context.getClickedPos();
        List<BlockPos> positions=new ArrayList<>();List<BlockState> before=new ArrayList<>(),after=new ArrayList<>();
        for(int i=0;i<v.partCount();i++){
            BlockPos pos=root.offset(v.offset(i,facing));
            if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.hasChunkAt(pos)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack))return InteractionResult.FAIL;
            var old=level.getBlockState(pos);var local=BlockPlaceContext.at(context,pos,context.getClickedFace());
            if(!old.canBeReplaced(local)||level.getBlockEntity(pos)!=null)return InteractionResult.FAIL;
            boolean water=!v.part(i).full()&&level.getFluidState(pos).getType()==Fluids.WATER;
            var state=assembly.state(selected,i,facing,water);
            if(!level.isUnobstructed(state,pos,CollisionContext.of(player)))return InteractionResult.FAIL;
            positions.add(pos);before.add(old);after.add(state);
        }
        if(level.isClientSide())return InteractionResult.SUCCESS;
        boolean completed=false;int written=0;CourtyardAssemblyBlock.beginEdit();
        try{
            for(int i=0;i<positions.size();i++){
                if(!level.setBlock(positions.get(i),after.get(i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))return InteractionResult.FAIL;
                written++;
            }
            completed=true;
        }finally{
            if(!completed)for(int i=written-1;i>=0;i--)level.setBlock(positions.get(i),before.get(i),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            CourtyardAssemblyBlock.endEdit();
        }
        for(BlockPos pos:positions)level.updateNeighborsAt(pos,assembly);
        var rootState=after.getFirst();var sound=rootState.getSoundType(level,root,player);
        level.playSound(null,root,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE,root,GameEvent.Context.of(player,rootState));
        if(player instanceof ServerPlayer server)CriteriaTriggers.PLACED_BLOCK.trigger(server,root,stack);
        stack.consume(1,player);return InteractionResult.CONSUME;
    }
}
