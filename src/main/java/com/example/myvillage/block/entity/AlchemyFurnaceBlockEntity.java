package com.example.myvillage.block.entity;

import com.example.myvillage.alchemy.*;
import com.example.myvillage.alchemy.array.AlchemyArrayTransactions;
import com.example.myvillage.block.AlchemyFurnaceBlock;
import com.example.myvillage.block.ModBlockEntities;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.item.SpiritStoneTags;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Three herb slots, one fuel slot and four result slots. Each batch is one server transaction. */
public final class AlchemyFurnaceBlockEntity extends BlockEntity implements Container,MenuProvider {
    public static final int SLOT_COUNT=8,OUTPUT_START=4,OUTPUT_END=8,DATA_COUNT=8;
    private NonNullList<ItemStack> items=NonNullList.withSize(SLOT_COUNT,ItemStack.EMPTY);
    private final NonNullList<ItemStack> identities=NonNullList.withSize(4,ItemStack.EMPTY);
    private final BrewingProgress progress=new BrewingProgress();
    private AlchemyBatch batch;
    private String selectedRecipe="";
    private int duration,status,lastScore,lastSuccess,lastFailures;
    private boolean completed;
    public final ContainerData data=new ContainerData(){
        public int get(int i){return switch(i){
            case 0->progress.ticks();case 1->duration;case 2->status;
            case 3->batch==null?lastScore:batch.score();case 4->lastFailures;
            case 5->active()?1:0;case 6->recipeIndex();case 7->lastSuccess;default->0;
        };}
        public void set(int i,int value){}public int getCount(){return DATA_COUNT;}
    };
    public AlchemyFurnaceBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.ALCHEMY_FURNACE.get(),pos,state);}
    public static String id(ItemStack stack){return stack.isEmpty()?"":net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    public boolean active(){return batch!=null;}
    public static boolean outputSlot(int slot){return slot>=OUTPUT_START&&slot<OUTPUT_END;}
    private int recipeIndex(){
        var recipes=AlchemyRules.get().recipes();
        for(int i=0;i<recipes.size();i++)if(recipes.get(i).id().equals(selectedRecipe))return i+1;return 0;
    }
    private AlchemyRules.Recipe matching(){
        return AlchemyRules.get().recipe(selectedRecipe).filter(r->r.matchesStacks(java.util.List.of(items.get(0),items.get(1),items.get(2)))).orElse(null);
    }
    private boolean actor(ServerPlayer p,AlchemyMenu menu){
        return level!=null&&!level.isClientSide()&&p.level()==level&&p.isAlive()&&!p.isSpectator()
            &&p.containerMenu==menu&&menu.isFor(this)&&stillValid(p);
    }
    public boolean select(ServerPlayer player,AlchemyMenu menu,int index){
        var recipes=AlchemyRules.get().recipes();
        if(!actor(player,menu)||active()||index<0||index>=recipes.size())return false;
        var recipe=recipes.get(index);if(!AlchemyKnowledge.knows(player,recipe.id()))return false;
        var payment=com.example.myvillage.spiritstone.SpiritStoneSources.open(player);
        var plan=AlchemyAutofill.plan(payment.copies(),java.util.List.of(items.get(0),items.get(1),items.get(3),items.get(2)),
            recipe.refillItems(),recipe.refillCounts(),36);
        if(plan.isEmpty()){player.displayClientMessage(Component.translatable("message.myvillage.alchemy.inventory_full"),true);return false;}
        var transfer=plan.orElseThrow();if(!payment.commit(transfer.inventory()))return false;
        items.set(0,transfer.inputs().get(0));items.set(1,transfer.inputs().get(1));
        items.set(3,transfer.inputs().get(2));items.set(2,transfer.inputs().get(3));
        selectedRecipe=recipe.id();completed=false;captureInputChanges();super.setChanged();
        player.getInventory().setChanged();updateIdleStatus();menu.broadcastChanges();return true;
    }
    private boolean outputEmpty(){for(int i=OUTPUT_START;i<OUTPUT_END;i++)if(!items.get(i).isEmpty())return false;return true;}
    private boolean hasFuel(AlchemyRules.Recipe r){return r!=null&&SpiritStoneTags.isLowGrade(items.get(3))&&items.get(3).getCount()>=r.fuelCount();}
    private void updateIdleStatus(){
        var r=matching();duration=AlchemyRules.get().recipe(selectedRecipe).map(AlchemyRules.Recipe::durationTicks).orElse(0);
        status=completed?(lastSuccess==0?7:6):!outputEmpty()?3:r==null?(selectedRecipe.isEmpty()?0:1):!hasFuel(r)?2:5;
    }
    private boolean captureInputChanges(){
        boolean changed=false;for(int i=0;i<4;i++){if(!ItemStack.matches(identities.get(i),items.get(i)))changed=true;identities.set(i,items.get(i).copy());}return changed;
    }
    private void clearBatch(){batch=null;progress.reset();}
    public boolean start(ServerPlayer player,AlchemyMenu menu){
        var r=matching();var aptitude=CultivationService.craftingAptitudes(player);boolean valid=actor(player,menu);
        if(valid&&!active()&&!AlchemyKnowledge.knows(player,selectedRecipe)){
            player.displayClientMessage(Component.translatable("message.myvillage.alchemy.formula_unknown"),true);return false;
        }
        var rejection=AlchemyBatch.rejection(valid,active(),aptitude.initialized()&&CultivationService.getProfile(player).awakened(),r!=null,hasFuel(r),outputEmpty());
        if(rejection!=null){player.displayClientMessage(Component.translatable(rejection.messageKey()),true);return false;}
        if(!r.eligible(aptitude.alchemy())){player.displayClientMessage(Component.literal("炼丹资质不足：此方要求 "+r.requiredAptitude()+" 点"),true);return false;}
        batch=AlchemyBatch.create(player.getUUID(),r,aptitude.alchemy(),
            com.example.myvillage.cultivation.crafting.CraftingTalents.modifier(player),level.random::nextGaussian);
        completed=false;lastSuccess=lastFailures=0;lastScore=aptitude.alchemy();
        progress.restore(r.id(),0,r.durationTicks());duration=r.durationTicks();status=4;
        captureInputChanges();super.setChanged();menu.broadcastChanges();return true;
    }
    public static void serverTick(Level level,BlockPos pos,BlockState state,AlchemyFurnaceBlockEntity furnace){furnace.tick(level,pos,state);}
    private void tick(Level level,BlockPos pos,BlockState state){
        int previous=progress.ticks();boolean wasActive=active(),changed=captureInputChanges();
        if(changed){if(active())clearBatch();completed=false;}
        var r=matching();if(active()&&(r==null||!r.acceptsBatch(batch)||!hasFuel(r)))clearBatch();
        duration=r==null?0:r.durationTicks();boolean finished=false;
        if(!active())updateIdleStatus();
        else{
            var results=AlchemyOutputs.of(r,batch);
            var plan=AlchemyArrayTransactions.outputPlan(items.subList(OUTPUT_START,OUTPUT_END),results,getMaxStackSize());
            status=plan==null?3:4;
            finished=progress.advance(r.id(),duration,false,plan!=null);
            if(finished){
                for(int i=0;i<3;i++)items.get(i).shrink(r.count(i));items.get(3).shrink(r.fuelCount());
                for(int i=OUTPUT_START;i<OUTPUT_END;i++)items.set(i,plan.get(i-OUTPUT_START));
                lastSuccess=batch.successfulCount();lastFailures=batch.failures();lastScore=batch.score();
                var brewer=((net.minecraft.server.level.ServerLevel)level).getServer().getPlayerList().getPlayer(batch.brewer());
                if(lastSuccess>0&&com.example.myvillage.aptitude.AptitudeRuntime.nearStation(brewer,level,worldPosition))
                    com.example.myvillage.aptitude.AptitudeRuntime.crafted(brewer,"alchemy",r.id(),batch.bestQuality(),results.getLast());
                if(brewer!=null)brewer.displayClientMessage(Component.literal("本炉炼成 "+lastSuccess+" 颗，失败 "+lastFailures+" 颗。"),false);
                clearBatch();captureInputChanges();completed=true;status=lastSuccess==0?7:6;
            }
        }
        boolean lit=active()&&status==4;
        if(state.getValue(AlchemyFurnaceBlock.LIT)!=lit)level.setBlock(pos,state.setValue(AlchemyFurnaceBlock.LIT,lit),3);
        if(finished||changed||previous!=progress.ticks()||wasActive!=active())super.setChanged();
    }
    @Override public int getContainerSize(){return SLOT_COUNT;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return items.get(slot);}
    @Override public ItemStack removeItem(int slot,int count){
        if(active()&&!outputSlot(slot))return ItemStack.EMPTY;
        var result=ContainerHelper.removeItem(items,slot,count);if(!result.isEmpty())inputChanged();return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot){
        if(active()&&!outputSlot(slot))return ItemStack.EMPTY;
        var result=ContainerHelper.takeItem(items,slot);inputChanged();return result;
    }
    @Override public void setItem(int slot,ItemStack stack){
        if(active()&&!outputSlot(slot))return;items.set(slot,stack);
        stack.limitSize(Math.min(getMaxStackSize(),stack.getMaxStackSize()));inputChanged();
    }
    private void inputChanged(){if(captureInputChanges()){clearBatch();completed=false;}super.setChanged();}
    @Override public void setChanged(){inputChanged();}
    @Override public boolean stillValid(Player player){return Container.stillValidBlockEntity(this,player);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){
        if(active())return false;
        return slot>=0&&slot<3?AlchemyRules.get().isHerb(id(stack)):slot==3&&SpiritStoneTags.isLowGrade(stack);
    }
    @Override public void clearContent(){items.clear();clearBatch();completed=false;captureInputChanges();super.setChanged();}
    @Override public Component getDisplayName(){return Component.translatable("container.myvillage.alchemy_furnace");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new AlchemyMenu(id,inventory,this,data);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.putInt("InventoryVersion",2);ContainerHelper.saveAllItems(tag,items,registries);
        tag.putString("SelectedRecipe",selectedRecipe);tag.putInt("Progress",progress.ticks());
        tag.putInt("LastScore",lastScore);tag.putInt("Succeeded",lastSuccess);tag.putInt("Failed",lastFailures);tag.putBoolean("Completed",completed);
        if(batch!=null)tag.put("Batch",batch.save());
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        items=NonNullList.withSize(SLOT_COUNT,ItemStack.EMPTY);clearBatch();completed=false;
        if(tag.getInt("InventoryVersion")!=2){captureInputChanges();return;}
        ContainerHelper.loadAllItems(tag,items,registries);captureInputChanges();selectedRecipe=tag.getString("SelectedRecipe");
        lastScore=Math.max(0,tag.getInt("LastScore"));lastSuccess=Math.clamp(tag.getInt("Succeeded"),0,16);lastFailures=Math.clamp(tag.getInt("Failed"),0,16);completed=tag.getBoolean("Completed");
        var r=matching();var restored=tag.contains("Batch",Tag.TAG_COMPOUND)?AlchemyBatch.read(tag.getCompound("Batch")).orElse(null):null;
        if(restored!=null&&r!=null&&hasFuel(r)&&r.acceptsBatch(restored)&&tag.contains("Progress",Tag.TAG_INT)
                &&tag.getInt("Progress")>=0&&tag.getInt("Progress")<r.durationTicks()){
            batch=restored;completed=false;progress.restore(r.id(),tag.getInt("Progress"),r.durationTicks());
        }
        duration=r==null?0:r.durationTicks();if(active())status=4;else updateIdleStatus();
    }
}
