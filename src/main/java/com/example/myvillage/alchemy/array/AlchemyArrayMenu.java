package com.example.myvillage.alchemy.array;

import com.example.myvillage.alchemy.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Private knowledge DataSlots and bounded vanilla button intents; no authored client state. */
public final class AlchemyArrayMenu extends AbstractContainerMenu {
    public static final int WIDTH=368,HEIGHT=250;
    public static final int[][] MACHINE_SLOTS={{154,38},{178,38},{238,38},{276,76},{294,76},{312,76},{330,76},{202,38}};
    private static final int PLAYER_START=8,PLAYER_END=44,HOTBAR_START=35;
    private final Container container; private final ContainerData data;
    private final List<DataSlot> learned=new ArrayList<>();
    public AlchemyArrayMenu(int id,Inventory inventory){this(id,inventory,new SimpleContainer(8),new SimpleContainerData(AlchemyArrayBlockEntity.DATA_COUNT));}
    public AlchemyArrayMenu(int id,Inventory inventory,Container container,ContainerData data){
        super(AlchemyArrayContent.MENU.get(),id);checkContainerSize(container,8);checkContainerDataCount(data,AlchemyArrayBlockEntity.DATA_COUNT);
        if(!inventory.player.level().isClientSide()&&!(container instanceof AlchemyArrayBlockEntity))throw new IllegalArgumentException("Array requires server controller");
        this.container=container;this.data=data;
        for(int i=0;i<8;i++){
            final int index=i;addSlot(new Slot(container,i,MACHINE_SLOTS[i][0],MACHINE_SLOTS[i][1]){
                @Override public boolean mayPlace(ItemStack stack){return (index<3||index==7)&&!active()&&accepts(index,stack);}
                @Override public boolean mayPickup(Player player){return (index>=3&&index<7)||!active();}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,100+col*18,168+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,100+col*18,226));
        addDataSlots(data);
        for(var recipe:AlchemyRules.get().recipes()){
            DataSlot known=inventory.player instanceof ServerPlayer server?new DataSlot(){public int get(){return AlchemyKnowledge.knows(server,recipe.id())?1:0;}public void set(int value){}}:DataSlot.standalone();
            learned.add(known);addDataSlot(known);
        }
    }
    private boolean accepts(int slot,ItemStack stack){
        int selected=selectedRecipe();if(selected<0||selected>=AlchemyRules.get().recipes().size()||slot<0||(slot>2&&slot!=7))return false;
        var recipe=AlchemyRules.get().recipes().get(selected);
        return slot==2?com.example.myvillage.item.SpiritStoneTags.isLowGrade(stack):(slot==7?recipe.thirdIngredient():recipe.ingredients().get(slot)).equals(com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity.id(stack));
    }
    public int progress(){return data.get(0);} public int duration(){return data.get(1);} public int status(){return data.get(2);}
    public boolean formed(){return data.get(3)!=0;} public int selectedRecipe(){return data.get(4)-1;}
    public boolean active(){return data.get(5)!=0;} public boolean enabled(){return data.get(6)!=0;}
    public int aptitude(){return data.get(7);} public int qualityCap(){return data.get(8);}
    public int mismatchIndex(){return data.get(9)-1;} public int mismatchCount(){return data.get(10);}
    public Direction facing(){Direction direction=Direction.from3DDataValue(data.get(11));return direction.getAxis().isHorizontal()?direction:Direction.NORTH;}
    public BlockPos controllerPos(){return new BlockPos((data.get(12)&65535)|(data.get(13)<<16),(data.get(14)&65535)|(data.get(15)<<16),(data.get(16)&65535)|(data.get(17)<<16));}
    public boolean knows(int i){return i>=0&&i<learned.size()&&learned.get(i).get()==1;}
    public boolean isFor(AlchemyArrayBlockEntity root){return container==root;}
    @Override public boolean stillValid(Player player){return container.stillValid(player);}
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof ServerPlayer server)||player.containerMenu!=this||!(container instanceof AlchemyArrayBlockEntity root))return false;
        return button==0?root.enable(server,this):button==1?root.stop(server,this):root.select(server,this,button-2);
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(active()&&slot>=0&&(slot<3||slot==7))return;super.clicked(slot,button,type,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(player))return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        if(index<PLAYER_START){if(!moveItemStackTo(stack,PLAYER_START,PLAYER_END,true))return ItemStack.EMPTY;}
        else {
            int dest=-1;for(int i:new int[]{0,1,2,7})if(accepts(i,stack)){dest=i;break;}
            if(dest>=0){if(active()||!moveItemStackTo(stack,dest,dest+1,false))return ItemStack.EMPTY;}
            else if(index<HOTBAR_START){if(!moveItemStackTo(stack,HOTBAR_START,PLAYER_END,false))return ItemStack.EMPTY;}
            else if(!moveItemStackTo(stack,PLAYER_START,HOTBAR_START,false))return ItemStack.EMPTY;
        }
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);return original;
    }
}
