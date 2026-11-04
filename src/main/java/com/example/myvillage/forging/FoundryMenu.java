package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Fixed menu kinds prevent sending preparation/assembly actions to the wrong workstation. */
public final class FoundryMenu extends AbstractContainerMenu {
    public enum Kind {PREPARATION,KILN,ASSEMBLY}
    public static final int WIDTH=300,HEIGHT=232;
    private final Container container;
    private final Inventory inventory;
    private final ContainerData data;
    private final Kind kind;
    private final List<DataSlot> known=new ArrayList<>();
    private final DataSlot skill,cap;
    public FoundryMenu(int id,Inventory inventory,Kind kind){this(id,inventory,new SimpleContainer(4),new SimpleContainerData(FoundryBlockEntity.DATA_COUNT),kind);}
    public FoundryMenu(int id,Inventory player,Container container,ContainerData data,Kind kind){
        super(type(kind),id);this.container=container;this.inventory=player;this.data=data;this.kind=kind;
        checkContainerSize(container,4);checkContainerDataCount(data,FoundryBlockEntity.DATA_COUNT);
        int[][] positions={{32,92},{96,92},{202,92},{258,92}};
        for(int i=0;i<4;i++){
            final int slot=i;addSlot(new Slot(container,i,positions[i][0],positions[i][1]){
                @Override public boolean mayPlace(ItemStack stack){return accepts(slot,stack);}
                @Override public boolean mayPickup(Player viewer){return !active()||slot>=2;}
                @Override public int getMaxStackSize(){return slot==1?64:1;}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,69+col*18,151+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,69+col*18,209));
        addDataSlots(data);
        for(var craft:WorkshopRules.get().crafts()){
            DataSlot slot=player.player instanceof ServerPlayer p?new DataSlot(){public int get(){return !craft.requires_pattern()||ForgingKnowledge.knows(p,craft.recipe())?1:0;}public void set(int value){}}:DataSlot.standalone();
            known.add(slot);addDataSlot(slot);
        }
        skill=player.player instanceof ServerPlayer p?new DataSlot(){public int get(){return ForgingSkill.level(p);}public void set(int value){}}:DataSlot.standalone();addDataSlot(skill);
        cap=player.player instanceof ServerPlayer p?new DataSlot(){public int get(){return ForgingCompletion.quality(p);}public void set(int value){}}:DataSlot.standalone();addDataSlot(cap);
    }
    private static MenuType<?> type(Kind kind){return switch(kind){case PREPARATION->ForgingMenus.PREPARATION.get();case KILN->ForgingMenus.KILN.get();case ASSEMBLY->ForgingMenus.ASSEMBLY.get();};}
    public Kind kind(){return kind;}public int recipeIndex(){return data.get(0);}public int progress(){return data.get(1);}public int duration(){return data.get(2);}
    public int status(){return data.get(3);}public int heat(){return data.get(4);}public int fuel(){return data.get(5);}public int quality(){return cap.get();}
    public int skill(){return skill.get();}public boolean active(){return data.get(7)!=0;}
    public ItemStack input(){return container.getItem(0);}public ItemStack material(){return container.getItem(1);}public ItemStack output(){return container.getItem(2);}
    public boolean knows(int index){return index>=0&&index<known.size()&&known.get(index).get()!=0;}
    public boolean isFor(FoundryBlockEntity block){return container==block;}
    private boolean accepts(int slot,ItemStack stack){
        if(slot>=2||active())return false;
        if(container instanceof FoundryBlockEntity block&&!block.canPlaceItem(slot,stack))return false;
        if(slot==0)return kind!=Kind.KILN?WorkshopItems.valid(stack)||kind==Kind.ASSEMBLY&&WorkshopEquipment.craft(stack).isPresent():WorkshopItems.valid(stack)||WorkshopEquipment.craft(stack).isPresent();
        return switch(kind){case PREPARATION->false;case KILN->stack.is(net.minecraft.tags.ItemTags.COALS)||ForgingIngredients.matches("#myvillage:forging_fuels",stack);
            case ASSEMBLY->stack.getItem() instanceof FoundryMaterialItem h&&!h.flame()||stack.is(net.minecraft.world.item.Items.LEATHER);};
    }
    @Override public boolean stillValid(Player player){return player==inventory.player&&player.isAlive()&&!player.isSpectator()&&container.stillValid(player);}
    @Override public boolean clickMenuButton(Player player,int button){return player instanceof ServerPlayer p&&container instanceof FoundryBlockEntity block&&stillValid(player)&&player.containerMenu==this&&block.action(p,this,button);}
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(active()&&slot>=0&&slot<2)return;super.clicked(slot,button,type,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);
        if(!slot.hasItem()||!slot.mayPickup(player))return ItemStack.EMPTY;
        var stack=slot.getItem();var before=stack.copy();
        if(index<4){if(!moveItemStackTo(stack,4,40,true))return ItemStack.EMPTY;}
        else if(accepts(0,stack)){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(accepts(1,stack)){if(!moveItemStackTo(stack,1,2,false))return ItemStack.EMPTY;}
        else if(index<31){if(!moveItemStackTo(stack,31,40,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,4,31,false))return ItemStack.EMPTY;
        if(stack.getCount()==before.getCount())return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return before;
    }
}
