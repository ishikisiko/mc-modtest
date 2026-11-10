package com.example.myvillage.alchemy;

import com.example.myvillage.item.SpiritStoneTags;
import com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

/** Three ingredient slots, fuel, and four extraction-only outputs. */
public final class AlchemyMenu extends AbstractContainerMenu {
    public static final int WIDTH=304,HEIGHT=222;
    public static final int[][] FURNACE_SLOTS={{116,38},{140,38},{164,38},{208,38},{248,38},{272,38},{248,62},{272,62}};
    private static final int PLAYER_START=8,PLAYER_END=44,HOTBAR_START=35;
    private final Container container;
    private final ContainerData data;
    private final java.util.List<DataSlot> learned=new java.util.ArrayList<>();
    private final DataSlot currentAptitude;
    public AlchemyMenu(int id,Inventory inventory){this(id,inventory,new SimpleContainer(AlchemyFurnaceBlockEntity.SLOT_COUNT),new SimpleContainerData(AlchemyFurnaceBlockEntity.DATA_COUNT));}
    public AlchemyMenu(int id,Inventory inventory,Container container,ContainerData data){
        super(AlchemyMenus.FURNACE.get(),id);checkContainerSize(container,8);checkContainerDataCount(data,8);
        if(!inventory.player.level().isClientSide()&&!(container instanceof AlchemyFurnaceBlockEntity))throw new IllegalArgumentException("A real furnace is required");
        this.container=container;this.data=data;
        for(int i=0;i<8;i++){final int index=i;addSlot(new Slot(container,i,FURNACE_SLOTS[i][0],FURNACE_SLOTS[i][1]){
            @Override public boolean mayPlace(ItemStack stack){return !inputLocked(index)&&accepts(index,stack);}
            @Override public boolean mayPickup(Player player){return !inputLocked(index);}
        });}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,70+col*18,140+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,70+col*18,198));
        addDataSlots(data);
        for(var recipe:AlchemyRules.get().recipes()){
            DataSlot known=inventory.player instanceof ServerPlayer p?new DataSlot(){
                public int get(){return AlchemyKnowledge.knows(p,recipe.id())?1:0;}public void set(int value){}
            }:DataSlot.standalone();learned.add(known);addDataSlot(known);
        }
        currentAptitude=inventory.player instanceof ServerPlayer p?new DataSlot(){
            public int get(){return com.example.myvillage.cultivation.CultivationService.craftingAptitudes(p).alchemy();}public void set(int value){}
        }:DataSlot.standalone();addDataSlot(currentAptitude);
    }
    private static boolean accepts(int index,ItemStack stack){
        return index>=0&&index<3?AlchemyRules.get().isHerb(AlchemyFurnaceBlockEntity.id(stack)):index==3&&SpiritStoneTags.isLowGrade(stack);
    }
    public int progress(){return data.get(0);}public int duration(){return data.get(1);}public int status(){return data.get(2);}
    public int aptitude(){return active()?data.get(3):currentAptitude.get();}
    public int failures(){return data.get(4);}public int successes(){return data.get(7);}
    public boolean outputsEmpty(){for(int i=4;i<8;i++)if(!container.getItem(i).isEmpty())return false;return true;}
    public boolean active(){return data.get(5)!=0;}
    private boolean inputLocked(int slot){return slot>=0&&slot<4&&active();}
    public boolean isFor(AlchemyFurnaceBlockEntity furnace){return container==furnace;}
    public int selectedRecipe(){return data.get(6)-1;}
    public boolean knows(int index){return index>=0&&index<learned.size()&&learned.get(index).get()==1;}
    @Override public boolean stillValid(Player player){return container.stillValid(player);}
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof ServerPlayer p)||player.containerMenu!=this||!(container instanceof AlchemyFurnaceBlockEntity furnace))return false;
        return button==0?furnace.start(p,this):furnace.select(p,this,button-1);
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(!inputLocked(slot))super.clicked(slot,button,type,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(player))return ItemStack.EMPTY;
        var stack=slot.getItem();var before=stack.copy();
        if(active()&&index>=PLAYER_START&&(accepts(0,stack)||accepts(3,stack)))return ItemStack.EMPTY;
        if(index<PLAYER_START){if(!moveItemStackTo(stack,PLAYER_START,PLAYER_END,true))return ItemStack.EMPTY;}
        else if(accepts(0,stack)){if(!moveItemStackTo(stack,0,3,false))return ItemStack.EMPTY;}
        else if(accepts(3,stack)){if(!moveItemStackTo(stack,3,4,false))return ItemStack.EMPTY;}
        else if(index<HOTBAR_START){if(!moveItemStackTo(stack,HOTBAR_START,PLAYER_END,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,PLAYER_START,HOTBAR_START,false))return ItemStack.EMPTY;
        if(stack.getCount()==before.getCount())return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return before;
    }
}
