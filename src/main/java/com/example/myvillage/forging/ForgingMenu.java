package com.example.myvillage.forging;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.cultivation.CultivationAttachments;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.cultivation.crafting.CraftingRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Private workbench session. Button intents are bounded; only the server commits costs/results. */
public final class ForgingMenu extends AbstractContainerMenu {
    public static final int WIDTH=304, HEIGHT=238;
    public static final int FORGE=0, REPAIR=1, RECIPE_BASE=10, TRAIT_BASE=30;
    private final Inventory inventory;
    private final ContainerLevelAccess access;
    private final SimpleContainer work=new SimpleContainer(2);
    private final ContainerData data=new SimpleContainerData(3); // recipe index, trait index, last result
    private final List<DataSlot> learned=new ArrayList<>();
    private final DataSlot cap;
    public ForgingMenu(int id,Inventory inventory){this(id,inventory,ContainerLevelAccess.NULL);}
    public ForgingMenu(int id,Inventory inventory,ContainerLevelAccess access){
        super(ForgingMenus.TABLE.get(),id);this.inventory=inventory;this.access=access;
        if(!inventory.player.level().isClientSide() && access==ContainerLevelAccess.NULL)
            throw new IllegalArgumentException("Server forging requires a placed table");
        addSlot(new Slot(work,0,244,96){@Override public boolean mayPlace(ItemStack s){return ForgedEquipment.supported(s);}});
        addSlot(new Slot(work,1,276,96){@Override public boolean mayPlace(ItemStack s){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,70+col*18,156+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,70+col*18,214));
        addDataSlots(data);
        for(var r:ForgingRules.get().recipes()){
            DataSlot known=inventory.player instanceof ServerPlayer p?new DataSlot(){
                public int get(){return ForgingKnowledge.knows(p,r.id())?1:0;} public void set(int v){}
            }:DataSlot.standalone();learned.add(known);addDataSlot(known);
        }
        cap=inventory.player instanceof ServerPlayer p?new DataSlot(){
            public int get(){var a=p.getData(CultivationAttachments.CRAFTING.get());return a.initialized()?a.qualityCap(CraftingAptitudes.Profession.FORGING,CraftingRules.get()):0;}
            public void set(int v){}
        }:DataSlot.standalone();addDataSlot(cap);
    }
    public int recipeIndex(){return data.get(0);}
    public int traitIndex(){return data.get(1);}
    public int status(){return data.get(2);}
    public int qualityCap(){return cap.get();}
    public boolean knows(int i){return i>=0&&i<learned.size()&&learned.get(i).get()==1;}
    public ForgingRules.Recipe recipe(){return ForgingRules.get().recipes().get(recipeIndex());}
    public String trait(){return ForgeMark.TRAITS.get(traitIndex());}
    public boolean canForge(){return knows(recipeIndex())&&qualityCap()>0&&work.isEmpty()&&recipe().traits().contains(trait());}
    public boolean canRepair(){return !work.getItem(0).isEmpty()&&work.getItem(0).isDamaged()&&work.getItem(1).isEmpty();}
    @Override public boolean stillValid(Player p){return p==inventory.player&&stillValid(access,p,ModBlocks.FORGING_TABLE.get());}
    private boolean reject(int status){data.set(2,status);broadcastChanges();return false;}
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof ServerPlayer server)||player.containerMenu!=this||!player.isAlive()||player.isSpectator()||!stillValid(player))return false;
        var rules=ForgingRules.get();
        if(button>=RECIPE_BASE&&button<RECIPE_BASE+rules.recipes().size()){
            int i=button-RECIPE_BASE;if(!knows(i))return reject(1);
            data.set(0,i);if(!recipe().traits().contains(trait()))data.set(1,ForgeMark.TRAITS.indexOf(recipe().traits().getFirst()));
            data.set(2,0);broadcastChanges();return true;
        }
        if(button>=TRAIT_BASE&&button<TRAIT_BASE+ForgeMark.TRAITS.size()){
            String t=ForgeMark.TRAITS.get(button-TRAIT_BASE);if(!recipe().traits().contains(t))return reject(6);
            data.set(1,button-TRAIT_BASE);data.set(2,0);broadcastChanges();return true;
        }
        if(button!=FORGE&&button!=REPAIR)return false;
        if(!work.getItem(1).isEmpty())return reject(3);
        ItemStack result;Map<String,Integer> costs;
        if(button==FORGE){
            if(!knows(recipeIndex()))return reject(1);
            if(qualityCap()==0)return reject(2);
            if(!work.getItem(0).isEmpty()||!recipe().traits().contains(trait()))return reject(6);
            costs=rules.costs(recipe(),trait());
        }else{
            if(!ForgedEquipment.supported(work.getItem(0))||!work.getItem(0).isDamaged())return reject(5);
            costs=rules.repairCosts();
        }
        var plan=ForgingCosts.plan(inventory.items,costs);if(plan.isEmpty())return reject(4);
        if(button==FORGE){
            int quality=rules.roll(qualityCap(),server.getRandom().nextInt(rules.total(qualityCap())));
            result=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe().output())));
            ForgedEquipment.apply(result,new ForgeMark(trait(),quality));
        }else{
            result=work.getItem(0).copy();result.setDamageValue(Math.max(0,result.getDamageValue()-ForgedEquipment.repairAmount(result)));
        }
        // All preconditions and result creation finish before this single server-thread transaction.
        for(int i=0;i<inventory.items.size();i++)inventory.items.set(i,plan.get().get(i));
        if(button==REPAIR)work.setItem(0,ItemStack.EMPTY);
        work.setItem(1,result);inventory.setChanged();data.set(2,button==FORGE?7:8);broadcastChanges();
        access.execute((level,pos)->level.playSound(null,pos,net.minecraft.sounds.SoundEvents.ANVIL_USE,net.minecraft.sounds.SoundSource.BLOCKS,0.6F,1F));
        return true;
    }
    @Override public void removed(Player p){super.removed(p);if(!p.level().isClientSide())clearContainer(p,work);}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        if(index<2){if(!moveItemStackTo(stack,2,38,true))return ItemStack.EMPTY;}
        else if(ForgedEquipment.supported(stack)){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(index<29){if(!moveItemStackTo(stack,29,38,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,2,29,false))return ItemStack.EMPTY;
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return original;
    }
}
