package com.example.myvillage.forging.array;

import com.example.myvillage.forging.*;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Shared persistent single batch. Only this controller owns real items and escrow. */
public final class ForgingArrayBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    public static final int DATA_COUNT=16;
    // Status: idle, refining, incomplete, output blocked, completed, no equipment,
    // not awakened, insufficient materials, already active, wrong trait, denied, recovery required.
    private NonNullList<ItemStack> items=NonNullList.withSize(2,ItemStack.EMPTY);
    private List<ItemStack> reserved=new ArrayList<>();
    private ItemStack frozen=ItemStack.EMPTY,snapshot=ItemStack.EMPTY,display=ItemStack.EMPTY;
    private UUID authorizer;
    private int progress,duration,quality,trait,status,mismatch=-1,missing;
    private volatile boolean formed;
    private boolean invalidBatch;
    public final ContainerData data=new ContainerData(){
        public int get(int i){return switch(i){
            case 0->progress;case 1->duration;case 2->status;case 3->formed?1:0;case 4->locked()?1:0;
            case 5->trait;case 6->quality;case 7->mismatch+1;case 8->missing;
            case 9->getBlockState().getValue(ForgingArrayBlock.FACING).get3DDataValue();
            case 10->worldPosition.getX()&65535;case 11->worldPosition.getX()>>>16;
            case 12->worldPosition.getY()&65535;case 13->worldPosition.getY()>>>16;
            case 14->worldPosition.getZ()&65535;case 15->worldPosition.getZ()>>>16;default->0;
        };}
        public void set(int i,int value){}public int getCount(){return DATA_COUNT;}
    };
    public ForgingArrayBlockEntity(BlockPos pos,BlockState state){super(ForgingArrayContent.CONTROLLER_ENTITY.get(),pos,state);}
    public boolean formed(){return formed;}public boolean active(){return !frozen.isEmpty();}
    public boolean locked(){return active()||invalidBatch||!reserved.isEmpty();}
    public boolean running(){return getBlockState().getValue(ForgingArrayBlock.LIT);}
    public int progress(){return progress;}public int duration(){return duration;}public int status(){return status;}
    public int quality(){return quality;}public int trait(){return trait;}public UUID authorizer(){return authorizer;}
    public ItemStack displayItem(){return level!=null&&level.isClientSide()?display:items.get(0);}
    public ForgingArrayPattern.Check freshCheck(){
        var check=ForgingArrayPattern.check(level,worldPosition,getBlockState().getValue(ForgingArrayBlock.FACING));
        boolean changed=formed!=check.formed();formed=check.formed();mismatch=check.mismatchIndex();missing=check.mismatchCount();
        if(changed)sync();return check;
    }
    private boolean actor(ServerPlayer player,ForgingArrayMenu menu){return level!=null&&!level.isClientSide()&&player.level()==level
            &&player.isAlive()&&!player.isSpectator()&&player.containerMenu==menu&&menu.isFor(this)&&stillValid(player);}
    private boolean reject(int code){status=code;setChanged();return false;}
    public boolean select(ServerPlayer player,ForgingArrayMenu menu,int index){
        if(!actor(player,menu)||locked()||index<0||index>=ForgeMark.TRAITS.size())return false;
        trait=index;status=0;setChanged();return true;
    }
    public boolean start(ServerPlayer player,ForgingArrayMenu menu){
        if(!actor(player,menu))return false;
        if(locked())return reject(8);
        if(!freshCheck().formed())return reject(2);
        if(!items.get(1).isEmpty())return reject(3);
        int cap=EquipmentInfusion.qualityCap(player);
        var prepared=EquipmentInfusion.prepare(items.get(0),ForgeMark.TRAITS.get(trait),cap);
        if(!prepared.valid())return reject(prepared.reason()==7?6:prepared.reason()==11?9:5);
        var access=SpiritStoneSources.open(player);
        var payment=ForgingCosts.reserve(access.copies(),prepared.costs());
        if(payment.isEmpty())return reject(7);
        // Build every saved value before the only inventory commit.
        var result=prepared.result().copy();var source=items.get(0).copy();
        var escrow=payment.get().consumed().stream().map(ItemStack::copy).toList();
        if(!access.commit(payment.get().remaining()))return reject(7);
        snapshot=source;frozen=result;reserved=new ArrayList<>(escrow);authorizer=player.getUUID();quality=cap;
        duration=ForgingArrayRules.get().duration();progress=0;status=1;invalidBatch=false;
        setChanged();sync();
        level.playSound(null,worldPosition,SoundEvents.BEACON_ACTIVATE,SoundSource.BLOCKS,.6f,.8f);
        return true;
    }
    public boolean cancel(ServerPlayer player,ForgingArrayMenu menu){
        if(!actor(player,menu)||!locked())return false;
        if(authorizer!=null&&!authorizer.equals(player.getUUID())&&!player.hasPermissions(2))return reject(10);
        var refund=reserved;reserved=new ArrayList<>();frozen=ItemStack.EMPTY;snapshot=ItemStack.EMPTY;
        invalidBatch=false;progress=duration=quality=0;authorizer=null;status=0;
        setChanged();sync();
        // Vanilla returns or drops overflow; never mint generic replacements for tagged/ledger materials.
        for(var stack:refund)player.getInventory().placeItemBackInInventory(stack.copy());
        return true;
    }
    public void serverTick(){
        if(level==null||level.isClientSide())return;
        freshCheck();
        boolean advancing=false;
        if(locked()){
            if(invalidBatch||!ItemStack.matches(snapshot,items.get(0))){status=11;invalidBatch=true;}
            else if(!formed)status=2;
            else if(!items.get(1).isEmpty())status=3;
            else {
                status=1;progress++;advancing=true;setChanged();
                if(progress>=duration){
                    items.set(0,ItemStack.EMPTY);items.set(1,frozen);frozen=ItemStack.EMPTY;snapshot=ItemStack.EMPTY;
                    reserved=new ArrayList<>();progress=duration;status=4;advancing=false;
                    level.playSound(null,worldPosition,SoundEvents.ANVIL_USE,SoundSource.BLOCKS,.5f,1.25f);sync();
                }
            }
        }
        var state=getBlockState();
        if(state.getValue(ForgingArrayBlock.LIT)!=advancing){level.setBlock(worldPosition,state.setValue(ForgingArrayBlock.LIT,advancing),3);sync();}
    }
    private void sync(){if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    public SimpleContainer dropSnapshot(){
        var out=new SimpleContainer(2+reserved.size());out.setItem(0,items.get(0).copy());out.setItem(1,items.get(1).copy());
        for(int i=0;i<reserved.size();i++)out.setItem(i+2,reserved.get(i).copy());return out;
    }
    @Override public int getContainerSize(){return 2;}@Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int i){return i>=0&&i<2?items.get(i):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int i,int count){
        if(i<0||i>=2||(i==0&&locked()))return ItemStack.EMPTY;
        var removed=ContainerHelper.removeItem(items,i,count);if(!removed.isEmpty()){setChanged();sync();}return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int i){return removeItem(i,Integer.MAX_VALUE);}
    @Override public void setItem(int i,ItemStack stack){
        if(i<0||i>=2||(i==0&&locked()))return;
        items.set(i,stack);stack.limitSize(1);if(i==0){progress=duration=quality=0;status=0;}setChanged();sync();
    }
    @Override public boolean canPlaceItem(int i,ItemStack stack){return i==0&&!locked()&&WorkshopEquipment.craft(stack).isPresent();}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean stillValid(Player player){return Container.stillValidBlockEntity(this,player);}
    @Override public void clearContent(){items=NonNullList.withSize(2,ItemStack.EMPTY);reserved=new ArrayList<>();frozen=snapshot=ItemStack.EMPTY;invalidBatch=false;authorizer=null;progress=duration=quality=0;setChanged();}
    @Override public int[] getSlotsForFace(Direction side){return new int[0];}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return false;}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return false;}
    @Override public Component getDisplayName(){return Component.literal("玄枢炼器鼎");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new ForgingArrayMenu(id,inventory,this,data);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);ContainerHelper.saveAllItems(tag,items,registries);tag.putInt("Version",1);
        tag.putInt("Trait",trait);tag.putInt("Quality",quality);tag.putInt("Progress",progress);tag.putInt("Duration",duration);
        tag.putBoolean("InvalidBatch",invalidBatch);if(authorizer!=null)tag.putUUID("Authorizer",authorizer);
        if(!frozen.isEmpty())tag.put("Frozen",frozen.save(registries));if(!snapshot.isEmpty())tag.put("Snapshot",snapshot.save(registries));
        var escrow=new ListTag();for(var stack:reserved)if(!stack.isEmpty())escrow.add(stack.save(registries));tag.put("Reserved",escrow);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);items=NonNullList.withSize(2,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,registries);
        trait=Math.max(0,Math.min(ForgeMark.TRAITS.size()-1,tag.getInt("Trait")));quality=tag.getInt("Quality");
        progress=tag.getInt("Progress");duration=tag.getInt("Duration");authorizer=tag.hasUUID("Authorizer")?tag.getUUID("Authorizer"):null;
        frozen=ItemStack.parseOptional(registries,tag.getCompound("Frozen"));snapshot=ItemStack.parseOptional(registries,tag.getCompound("Snapshot"));
        reserved=new ArrayList<>();for(var entry:tag.getList("Reserved",Tag.TAG_COMPOUND)){
            var stack=ItemStack.parseOptional(registries,(CompoundTag)entry);if(!stack.isEmpty())reserved.add(stack);
        }
        invalidBatch=tag.getBoolean("InvalidBatch");formed=false;status=0;
        if(!frozen.isEmpty()||!reserved.isEmpty()||invalidBatch){
            var expected=EquipmentInfusion.prepare(snapshot,ForgeMark.TRAITS.get(trait),quality);
            invalidBatch=invalidBatch||tag.getInt("Version")!=1||authorizer==null||duration<=0||duration>30000||progress<0||progress>=duration
                    ||!ItemStack.matches(snapshot,items.get(0))||!expected.valid()||!ItemStack.matches(expected.result(),frozen);
            if(invalidBatch){frozen=ItemStack.EMPTY;status=11;}
        } else {progress=duration=quality=0;snapshot=ItemStack.EMPTY;}
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        var tag=new CompoundTag();tag.putBoolean("Formed",formed);
        if(!items.get(0).isEmpty())tag.put("Display",items.get(0).save(registries));return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){
        boolean before=formed;formed=tag.getBoolean("Formed");display=ItemStack.parseOptional(registries,tag.getCompound("Display"));
        if(before!=formed)refreshMeshes();
    }
    private void refreshMeshes(){
        if(level==null||!level.isClientSide())return;
        for(var c:ForgingArrayRules.get().cells()){
            if(c.role()==ForgingArrayRules.Role.AIR)continue;var pos=ForgingArrayPattern.worldPos(worldPosition,getBlockState().getValue(ForgingArrayBlock.FACING),c);
            if(!level.hasChunkAt(pos))continue;var state=level.getBlockState(pos);level.sendBlockUpdated(pos,state,state,2);
        }
    }
    @Override public void setRemoved(){if(level!=null&&level.isClientSide()){formed=false;refreshMeshes();}super.setRemoved();}
    @Override public void onChunkUnloaded(){if(level!=null&&level.isClientSide()){formed=false;refreshMeshes();}super.onChunkUnloaded();}
    @Override public void onLoad(){super.onLoad();refreshMeshes();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){handleUpdateTag(packet.getTag(),registries);}
}
