package com.example.myvillage.alchemy.array;

import com.example.myvillage.alchemy.*;
import com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.crafting.*;
import com.example.myvillage.item.SpiritStoneTags;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sole inventory owner. Authorization survives logout; every batch retains its frozen decision. */
public final class AlchemyArrayBlockEntity extends BlockEntity implements Container,MenuProvider {
    public static final int SLOT_COUNT=7,DATA_COUNT=18;
    private NonNullList<ItemStack> items=NonNullList.withSize(SLOT_COUNT,ItemStack.EMPTY);
    private NonNullList<ItemStack> reserved=NonNullList.withSize(3,ItemStack.EMPTY);
    private AlchemyBatch batch;
    private String selectedRecipe="";
    private UUID authorizer,identity=UUID.randomUUID();
    private int score,cap,progress,status,duration;
    private boolean enabled,formed;
    private int mismatchIndex=-1,mismatchCount;
    public final ContainerData data=new ContainerData(){
        public int get(int i){return switch(i){
            case 0->progress;case 1->duration;case 2->status;case 3->formed?1:0;case 4->recipeIndex()+1;
            case 5->active()?1:0;case 6->enabled?1:0;case 7->score;case 8->cap;case 9->mismatchIndex+1;case 10->mismatchCount;
            case 11->getBlockState().getValue(AlchemyArrayBlock.FACING).get3DDataValue();
            case 12->worldPosition.getX()&65535;case 13->worldPosition.getX()>>>16;
            case 14->worldPosition.getY()&65535;case 15->worldPosition.getY()>>>16;
            case 16->worldPosition.getZ()&65535;case 17->worldPosition.getZ()>>>16;default->0;
        };}
        public void set(int i,int v){} public int getCount(){return DATA_COUNT;}
    };
    public AlchemyArrayBlockEntity(BlockPos pos,BlockState state){super(AlchemyArrayContent.CONTROLLER_ENTITY.get(),pos,state);}
    public boolean active(){return batch!=null;} public boolean enabled(){return enabled;} public boolean formed(){return formed;}
    public boolean running(){return getBlockState().getValue(AlchemyArrayBlock.LIT);}
    public int progress(){return progress;} public int duration(){return duration;} public int status(){return status;}
    public int qualityCap(){return cap;} public String selectedRecipe(){return selectedRecipe;} public UUID authorizer(){return authorizer;}
    public int mismatchIndex(){return mismatchIndex;} public int mismatchCount(){return mismatchCount;}
    public UUID identity(){return identity;}
    private int recipeIndex(){var recipes=AlchemyRules.get().recipes();for(int i=0;i<recipes.size();i++)if(recipes.get(i).id().equals(selectedRecipe))return i;return -1;}
    private AlchemyRules.Recipe recipe(){return AlchemyRules.get().recipe(selectedRecipe).orElse(null);}
    public AlchemyArrayPattern.Check freshCheck(){
        var result=level==null?new AlchemyArrayPattern.Check(false,-1,1):AlchemyArrayPattern.check(level,worldPosition,getBlockState().getValue(AlchemyArrayBlock.FACING));
        boolean changed=formed!=result.formed();
        formed=result.formed();mismatchIndex=result.mismatchIndex();mismatchCount=result.mismatchCount();
        if(changed&&level!=null&&!level.isClientSide()){
            AlchemyArrayPattern.invalidatePorts(level,worldPosition,getBlockState().getValue(AlchemyArrayBlock.FACING));
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        }
        return result;
    }
    private boolean validActor(ServerPlayer p,AlchemyArrayMenu menu){return level!=null&&!level.isClientSide()&&p.level()==level&&p.isAlive()&&!p.isSpectator()&&p.containerMenu==menu&&menu.isFor(this)&&stillValid(p);}
    private boolean awakened(ServerPlayer p){return CultivationService.craftingAptitudes(p).initialized()&&CultivationService.getProfile(p).spiritualRoot().isPresent();}
    public boolean select(ServerPlayer p,AlchemyArrayMenu menu,int index){
        var recipes=AlchemyRules.get().recipes();
        if(!validActor(p,menu))return false;
        if(!awakened(p)){p.displayClientMessage(Component.literal("请先完成灵根觉醒，再使用丹炉阵。"),true);return false;}
        if(active()||enabled||index<0||index>=recipes.size()||!AlchemyKnowledge.knows(p,recipes.get(index).id()))return false;
        var r=recipes.get(index);var payment=com.example.myvillage.spiritstone.SpiritStoneSources.open(p);
        var plan=AlchemyAutofill.plan(payment.copies(),List.of(items.get(0),items.get(1),items.get(2)),
                List.of(r.ingredients().get(0),r.ingredients().get(1),AlchemyRules.FUEL),List.of(1,1,r.fuelCount()),36);
        if(plan.isEmpty()||!payment.commit(plan.get().inventory()))return false;
        for(int i=0;i<3;i++)items.set(i,plan.get().inputs().get(i));p.getInventory().setChanged();
        selectedRecipe=recipes.get(index).id();authorizer=null;score=cap=0;progress=0;setChanged();menu.broadcastChanges();return true;
    }
    public boolean enable(ServerPlayer p,AlchemyArrayMenu menu){
        if(!validActor(p,menu)||!awakened(p)||active()||enabled||recipe()==null||!AlchemyKnowledge.knows(p,selectedRecipe)||!freshCheck().formed()||!ingredients(recipe())||!fuel(recipe()))return false;
        CraftingAptitudes apt=CultivationService.craftingAptitudes(p);
        authorizer=p.getUUID();score=apt.alchemy();cap=apt.qualityCap(CraftingAptitudes.Profession.ALCHEMY,CraftingRules.get());
        enabled=true;beginBatch();setChanged();menu.broadcastChanges();return true;
    }
    public boolean stop(ServerPlayer p,AlchemyArrayMenu menu){
        if(!validActor(p,menu))return false;enabled=false;setChanged();menu.broadcastChanges();return true;
    }
    private boolean ingredients(AlchemyRules.Recipe r){return r!=null&&r.ingredients().get(0).equals(AlchemyFurnaceBlockEntity.id(items.get(0)))&&r.ingredients().get(1).equals(AlchemyFurnaceBlockEntity.id(items.get(1)));}
    private boolean fuel(AlchemyRules.Recipe r){return r!=null&&SpiritStoneTags.isLowGrade(items.get(2))&&items.get(2).getCount()>=r.fuelCount();}
    private void beginBatch(){
        var r=recipe();if(!enabled||active()||authorizer==null||cap<1||cap>4||!ingredients(r)||!fuel(r))return;
        var rules=AlchemyRules.get();batch=new AlchemyBatch(authorizer,r.id(),score,cap,rules.rollQuality(cap,level.random.nextInt(rules.qualityWeightTotal(cap))));
        for(int i=0;i<3;i++)reserved.set(i,items.get(i).copyWithCount(i==2?r.fuelCount():1));
        progress=0;duration=r.durationTicks();setChanged();
    }
    private boolean reservedPresent(){return AlchemyArrayTransactions.reservedPresent(items,reserved);}
    private ItemStack result(){var r=recipe();if(r==null||batch==null)return ItemStack.EMPTY;ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.output())),r.outputCount());PillQuality.apply(stack,batch.quality());return stack;}
    /** Copies form a complete output transaction, including component equality and split stacks. */
    private List<ItemStack> outputPlan(ItemStack result){return AlchemyArrayTransactions.outputPlan(items.subList(3,SLOT_COUNT),result,getMaxStackSize());}
    public static void serverTick(Level level,BlockPos pos,BlockState state,AlchemyArrayBlockEntity array){array.tick();}
    private void tick(){
        boolean wasFormed=formed;freshCheck();var r=recipe();duration=r==null?0:r.durationTicks();
        if(!formed)status=6;
        else {
            if(!active()&&enabled)beginBatch();
            if(active()) {
                if(r==null||!r.id().equals(batch.recipe())||!reservedPresent())status=1;
                else {
                    var plan=outputPlan(result());
                    if(plan==null)status=3;
                    else {
                        status=enabled?4:7;progress++;setChanged();
                        if(progress>=duration){
                            for(int i=0;i<3;i++)items.get(i).shrink(reserved.get(i).getCount());
                            for(int i=3;i<SLOT_COUNT;i++)items.set(i,plan.get(i-3));
                            batch=null;reserved=NonNullList.withSize(3,ItemStack.EMPTY);progress=0;setChanged();
                        }
                    }
                }
            } else status=r==null?0:!ingredients(r)?1:!fuel(r)?2:5;
        }
        boolean lit=formed&&active()&&(status==4||status==7);
        BlockState state=getBlockState();boolean changedLight=state.getValue(AlchemyArrayBlock.LIT)!=lit;
        if(changedLight)level.setBlock(worldPosition,state.setValue(AlchemyArrayBlock.LIT,lit),3);
        // Presentation is never authoritative; do not load the vent's chunk to update it.
        for(var c:AlchemyArrayRules.get().cells())if(c.role()==AlchemyArrayRules.Role.VENT){
            BlockPos vent=AlchemyArrayPattern.worldPos(worldPosition,state.getValue(AlchemyArrayBlock.FACING),c);
            if(level.hasChunkAt(vent)){BlockState v=level.getBlockState(vent);if(v.getBlock() instanceof AlchemyArrayBlock b&&b.role()==AlchemyArrayRules.Role.VENT&&v.getValue(AlchemyArrayBlock.LIT)!=lit)level.setBlock(vent,v.setValue(AlchemyArrayBlock.LIT,lit),3);}
        }
        if(wasFormed!=formed||changedLight)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    public boolean accepts(int slot,ItemStack stack){
        var r=recipe();if(stack.isEmpty()||r==null||slot<0||slot>2)return false;
        if(active()&&!ItemStack.isSameItemSameComponents(reserved.get(slot),stack))return false;
        return slot<2?r.ingredients().get(slot).equals(AlchemyFurnaceBlockEntity.id(stack)):SpiritStoneTags.isLowGrade(stack);
    }
    /** Called only after a port has freshly revalidated structure and ownership. */
    public ItemStack insertPort(int slot,ItemStack stack,boolean simulate){
        if(!accepts(slot,stack))return stack.copy();ItemStack current=items.get(slot);
        if(!current.isEmpty()&&!ItemStack.isSameItemSameComponents(current,stack))return stack.copy();
        int n=Math.min(stack.getCount(),Math.min(getMaxStackSize(),stack.getMaxStackSize())-current.getCount());
        if(n<=0)return stack.copy();ItemStack remainder=stack.copy();remainder.shrink(n);
        if(!simulate){if(current.isEmpty())items.set(slot,stack.copyWithCount(n));else current.grow(n);setChanged();}
        return remainder;
    }
    public ItemStack extractPort(int slot,int amount,boolean simulate){
        if(slot<3||slot>=SLOT_COUNT||amount<=0)return ItemStack.EMPTY;ItemStack current=items.get(slot);if(current.isEmpty())return ItemStack.EMPTY;
        ItemStack result=current.copyWithCount(Math.min(amount,current.getCount()));if(!simulate){current.shrink(result.getCount());setChanged();}return result;
    }
    public SimpleContainer dropSnapshot(){SimpleContainer out=new SimpleContainer(SLOT_COUNT);for(int i=0;i<SLOT_COUNT;i++)out.setItem(i,items.get(i).copy());return out;}
    @Override public int getContainerSize(){return SLOT_COUNT;} @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<SLOT_COUNT?items.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot<0||slot>=SLOT_COUNT||(active()&&slot<3))return ItemStack.EMPTY;ItemStack out=ContainerHelper.removeItem(items,slot,count);if(!out.isEmpty())setChanged();return out;}
    @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,Integer.MAX_VALUE);}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=SLOT_COUNT||(active()&&slot<3))return;items.set(slot,stack);stack.limitSize(Math.min(getMaxStackSize(),stack.getMaxStackSize()));setChanged();}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return !active()&&accepts(slot,stack);}
    @Override public boolean stillValid(Player p){return Container.stillValidBlockEntity(this,p);}
    @Override public void clearContent(){items=NonNullList.withSize(SLOT_COUNT,ItemStack.EMPTY);reserved=NonNullList.withSize(3,ItemStack.EMPTY);batch=null;enabled=false;progress=0;setChanged();}
    @Override public Component getDisplayName(){return Component.literal("青铜丹炉阵");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player p){return new AlchemyArrayMenu(id,inventory,this,data);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);ContainerHelper.saveAllItems(tag,items,registries);tag.putInt("Version",1);tag.putUUID("Identity",identity);
        tag.putString("SelectedRecipe",selectedRecipe);tag.putBoolean("Enabled",enabled);tag.putInt("Score",score);tag.putInt("Cap",cap);tag.putInt("Progress",progress);
        if(authorizer!=null)tag.putUUID("Authorizer",authorizer);if(batch!=null){tag.put("Batch",batch.save());CompoundTag inputs=new CompoundTag();ContainerHelper.saveAllItems(inputs,reserved,registries);tag.put("Reserved",inputs);}
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);items=NonNullList.withSize(SLOT_COUNT,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,registries);
        batch=null;reserved=NonNullList.withSize(3,ItemStack.EMPTY);progress=0;enabled=false;authorizer=null;score=cap=0;
        identity=tag.hasUUID("Identity")?tag.getUUID("Identity"):UUID.randomUUID();selectedRecipe=tag.getString("SelectedRecipe");
        var r=recipe();if(r==null){selectedRecipe="";return;}duration=r.durationTicks();
        if(tag.contains("Version",Tag.TAG_INT)&&tag.getInt("Version")==1&&tag.hasUUID("Authorizer")&&tag.contains("Score",Tag.TAG_INT)&&tag.contains("Cap",Tag.TAG_INT)
                &&tag.getInt("Score")>=0&&tag.getInt("Score")<=100&&tag.getInt("Cap")>=1&&tag.getInt("Cap")<=4){authorizer=tag.getUUID("Authorizer");score=tag.getInt("Score");cap=tag.getInt("Cap");enabled=tag.getBoolean("Enabled");}
        if(tag.contains("Batch",Tag.TAG_COMPOUND)) {
            var restored=AlchemyBatch.read(tag.getCompound("Batch")).orElse(null);ContainerHelper.loadAllItems(tag.getCompound("Reserved"),reserved,registries);
            if(restored!=null&&restored.recipe().equals(selectedRecipe)&&restored.brewer().equals(authorizer)&&restored.score()==score&&restored.cap()==cap
                    &&tag.contains("Progress",Tag.TAG_INT)&&tag.getInt("Progress")>=0&&tag.getInt("Progress")<duration
                    &&reserved.get(0).getCount()==1&&reserved.get(1).getCount()==1&&reserved.get(2).getCount()==r.fuelCount()&&ingredients(r)&&fuel(r)&&reservedPresent()){
                batch=restored;progress=tag.getInt("Progress");
            } else {enabled=false;reserved=NonNullList.withSize(3,ItemStack.EMPTY);}
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();tag.putBoolean("Formed",formed);return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){formed=tag.getBoolean("Formed");}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){handleUpdateTag(packet.getTag(),registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide())AlchemyArrayPattern.invalidatePorts(level,worldPosition,getBlockState().getValue(AlchemyArrayBlock.FACING));}
    @Override public void onChunkUnloaded(){if(level!=null&&!level.isClientSide())AlchemyArrayPattern.invalidatePorts(level,worldPosition,getBlockState().getValue(AlchemyArrayBlock.FACING));super.onChunkUnloaded();}
}
