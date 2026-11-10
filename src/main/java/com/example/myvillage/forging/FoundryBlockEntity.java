package com.example.myvillage.forging;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Common real inventory transport. Each workstation implements its own bounded server actions. */
public abstract class FoundryBlockEntity extends BlockEntity implements Container,MenuProvider {
    public static final int DATA_COUNT=12;
    protected NonNullList<ItemStack> items=NonNullList.withSize(4,ItemStack.EMPTY);
    protected int recipe,progress,duration,status,quality;
    protected ItemStack display=ItemStack.EMPTY;
    protected boolean loading;
    public final ContainerData data=new ContainerData(){
        public int get(int i){return switch(i){case 0->recipe;case 1->progress;case 2->duration;case 3->status;
            case 4->heat();case 5->fuel();case 6->active()?0:quality;case 7->active()?1:0;default->0;};}
        public void set(int i,int value){}public int getCount(){return DATA_COUNT;}
    };
    protected FoundryBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public abstract FoundryMenu.Kind kind();
    public abstract void serverTick();
    public abstract boolean action(ServerPlayer player,FoundryMenu menu,int button);
    public boolean active(){return false;}
    public int heat(){return level==null?0:ForgeHeat.get(items.get(0),level.getGameTime());}
    public int fuel(){return 0;}
    public boolean locked(int slot){return active()&&slot<2;}
    public ItemStack displayItem(){return level!=null&&level.isClientSide()?display:items.get(0);}
    protected boolean actor(ServerPlayer player,FoundryMenu menu){return level!=null&&!level.isClientSide()&&!isRemoved()
            &&player.level()==level&&player.isAlive()&&!player.isSpectator()&&player.containerMenu==menu&&menu.isFor(this)&&stillValid(player);}
    protected boolean refuse(int reason){status=reason;setChanged();return false;}
    public void sync(){if(level!=null&&!level.isClientSide()&&!isRemoved())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override public void setChanged(){super.setChanged();}
    public void dropContents(){if(level!=null&&!level.isClientSide()){Containers.dropContents(level,worldPosition,this);clearContent();}}
    @Override public int getContainerSize(){return 4;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<4?items.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){
        if(slot<0||slot>=4||locked(slot))return ItemStack.EMPTY;
        var result=ContainerHelper.removeItem(items,slot,count);if(!result.isEmpty()){setChanged();sync();}return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,Integer.MAX_VALUE);}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=4||locked(slot))return;items.set(slot,stack);stack.limitSize(slot==1?64:1);setChanged();sync();}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot<2&&!locked(slot);}
    @Override public boolean stillValid(Player player){return Container.stillValidBlockEntity(this,player);}
    @Override public void clearContent(){items=NonNullList.withSize(4,ItemStack.EMPTY);progress=duration=status=0;setChanged();}
    @Override public Component getDisplayName(){return Component.translatable("foundry.myvillage."+switch(kind()){case PREPARATION->"preparation";case KILN->"kiln";case ASSEMBLY->"assembly";});}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new FoundryMenu(id,inventory,this,data,kind());}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);ContainerHelper.saveAllItems(tag,items,registries);
        tag.putInt("Recipe",recipe);tag.putInt("Progress",progress);tag.putInt("Duration",duration);tag.putInt("Status",status);tag.putInt("Quality",quality);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);loading=true;
        try{items=NonNullList.withSize(4,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,registries);
            recipe=Math.clamp(tag.getInt("Recipe"),0,WorkshopRules.get().crafts().size()-1);progress=tag.getInt("Progress");duration=tag.getInt("Duration");status=tag.getInt("Status");quality=tag.getInt("Quality");}
        finally{loading=false;}
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        var tag=new CompoundTag();if(!items.get(0).isEmpty())tag.put("Display",items.get(0).save(registries));return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){display=ItemStack.parseOptional(registries,tag.getCompound("Display"));}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onDataPacket(Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){handleUpdateTag(packet.getTag(),registries);}
}
