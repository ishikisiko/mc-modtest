package com.example.myvillage.block.entity;

import com.example.myvillage.block.*;
import com.example.myvillage.forging.*;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-owned single infusion. GUI closure cannot cancel or duplicate a paid workpiece. */
public final class ForgingDingBlockEntity extends BlockEntity {
    private WorldInfusionJob job=new WorldInfusionJob();
    private ItemStack display=ItemStack.EMPTY;
    private int shownElapsed,shownDuration;
    private boolean shownRunning;
    private long shownTime;
    public ForgingDingBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.FORGING_DING.get(),pos,state);}
    public WorldInfusionJob job(){return job;}
    public boolean occupied(){return job.occupied();}
    private boolean actor(ServerPlayer p){return level!=null&&!level.isClientSide()&&!isRemoved()&&p.level()==level&&p.isAlive()&&!p.isSpectator()
            &&p.canInteractWithBlock(worldPosition,0)&&level.getBlockState(worldPosition).is(ModBlocks.FORGING_TABLE.get());}
    /** Prepares all state before committing inventory payment; called only by the current server menu. */
    public int begin(ServerPlayer player,WorkshopMenu menu){
        if(!actor(player)||player.containerMenu!=menu||!menu.ding())return 18;
        if(occupied())return 19;
        var input=menu.input().copy();int cap=EquipmentInfusion.qualityCap(player);
        var prepared=EquipmentInfusion.prepare(input,menu.trait(),cap);if(!prepared.valid())return prepared.reason();
        var source=SpiritStoneSources.open(player);var payment=ForgingCosts.reserve(source.copies(),prepared.costs());
        if(payment.isEmpty())return 4;
        int duration=WorkshopRules.get().operation_ticks().get("infuse");
        // Validate the complete frozen state before the actual transaction can mutate anything.
        var candidate=new WorldInfusionJob();candidate.begin(player.getUUID(),input,prepared,payment.get().consumed(),menu.trait(),cap,duration);
        if(!source.commit(payment.get().remaining()))return 4;
        job=candidate;
        menu.releaseInfusionInput();setChanged();setLit(true);sync();
        level.playSound(null,worldPosition,WorkshopSounds.get("infuse"),SoundSource.BLOCKS,
                ForgingDingVisuals.get().feedback().soundVolume(),ForgingDingVisuals.get().feedback().soundPitch());
        player.closeContainer();return 0;
    }
    /** An occupied ding is visible to everyone; only its owner (or an operator) takes/refunds it. */
    public void interact(ServerPlayer player){
        if(!actor(player)||!occupied())return;
        if(job.running()){message(player,"running");return;}
        if(!player.getUUID().equals(job.owner())&&!player.hasPermissions(2)){message(player,"owned");return;}
        var items=job.drain();setChanged();setLit(false);sync();
        for(var stack:items)player.getInventory().placeItemBackInInventory(stack);
        player.getInventory().setChanged();message(player,"taken");
    }
    private void message(ServerPlayer player,String name){player.displayClientMessage(Component.translatable("workshop.myvillage.world."+name),true);}
    public void dropContents(){
        if(level==null||level.isClientSide())return;
        var actual=job.drain();setChanged();
        for(var stack:actual)Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack);
    }
    public static void serverTick(Level level,BlockPos pos,BlockState state,ForgingDingBlockEntity block){
        if(!block.job.running())return;
        boolean finished=block.job.tick();block.setChanged();
        if(finished){block.setLit(false);block.sync();
            level.playSound(null,pos,WorkshopSounds.get("infuse"),SoundSource.BLOCKS,
                    ForgingDingVisuals.get().feedback().soundVolume(),ForgingDingVisuals.get().feedback().soundPitch());
        }else if(block.job.elapsed()%InfusionVisuals.get().syncTicks()==0)block.sync();
    }
    private void setLit(boolean value){
        if(level==null||!level.getBlockState(worldPosition).is(ModBlocks.FORGING_TABLE.get()))return;
        var state=level.getBlockState(worldPosition);if(state.getValue(ForgingTableBlock.LIT)!=value)level.setBlock(worldPosition,state.setValue(ForgingTableBlock.LIT,value),3);
    }
    private void sync(){if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide()&&occupied()){setLit(job.running());sync();}}
    public ItemStack displayItem(){return level!=null&&level.isClientSide()?display:job.display();}
    public boolean visuallyRunning(){return level!=null&&level.isClientSide()?shownRunning:job.running();}
    public float visualProgress(float partialTick){
        if(shownDuration<=0)return 1;
        double delta=shownRunning&&level!=null?Math.max(0,level.getGameTime()+partialTick-shownTime):0;
        return (float)Math.min(shownRunning?.999:1,Math.max(0,(shownElapsed+delta)/shownDuration));
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.put("Infusion",job.save(registries));}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);job.load(tag.getCompound("Infusion"),registries);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        var tag=new CompoundTag();if(!job.display().isEmpty())tag.put("Display",job.display().save(registries));
        tag.putInt("Elapsed",job.elapsed());tag.putInt("Duration",job.duration());tag.putBoolean("Running",job.running());tag.putLong("Time",level==null?0:level.getGameTime());return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){
        display=ItemStack.parseOptional(registries,tag.getCompound("Display"));shownElapsed=tag.getInt("Elapsed");shownDuration=tag.getInt("Duration");shownRunning=tag.getBoolean("Running");shownTime=tag.getLong("Time");
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onDataPacket(Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){handleUpdateTag(packet.getTag(),registries);}
}
