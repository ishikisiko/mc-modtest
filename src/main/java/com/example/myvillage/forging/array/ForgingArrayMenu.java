package com.example.myvillage.forging.array;

import com.example.myvillage.forging.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Vanilla button packets carry only a bounded action; identity and costs are server-owned. */
public final class ForgingArrayMenu extends AbstractContainerMenu {
    public static final int WIDTH=316,HEIGHT=248;
    private final Container inventory;private final ContainerData data;
    private final DataSlot cap;
    public ForgingArrayMenu(int id,Inventory player){this(id,player,new SimpleContainer(2),new SimpleContainerData(ForgingArrayBlockEntity.DATA_COUNT));}
    public ForgingArrayMenu(int id,Inventory player,Container inventory,ContainerData data){
        super(ForgingArrayContent.MENU.get(),id);this.inventory=inventory;this.data=data;
        checkContainerSize(inventory,2);checkContainerDataCount(data,ForgingArrayBlockEntity.DATA_COUNT);
        if(!player.player.level().isClientSide()&&!(inventory instanceof ForgingArrayBlockEntity))throw new IllegalArgumentException("Controller required");
        addSlot(new Slot(inventory,0,150,51){
            @Override public boolean mayPlace(ItemStack stack){return !active()&&WorkshopEquipment.craft(stack).isPresent();}
            @Override public boolean mayPickup(Player p){return !active();}
            @Override public int getMaxStackSize(){return 1;}
        });
        addSlot(new Slot(inventory,1,268,51){@Override public boolean mayPlace(ItemStack stack){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,144+col*18,166+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,144+col*18,224));
        addDataSlots(data);
        cap=player.player instanceof ServerPlayer server?new DataSlot(){public int get(){return EquipmentInfusion.qualityCap(server);}public void set(int value){}}:DataSlot.standalone();addDataSlot(cap);
    }
    public int progress(){return data.get(0);}public int duration(){return data.get(1);}public int status(){return data.get(2);}
    public boolean formed(){return data.get(3)!=0;}public boolean active(){return data.get(4)!=0;}
    public int traitIndex(){return data.get(5);}public int batchQuality(){return data.get(6);}public int qualityCap(){return cap.get();}
    public int mismatchIndex(){return data.get(7)-1;}public int mismatchCount(){return data.get(8);}
    public Direction facing(){var d=Direction.from3DDataValue(data.get(9));return d.getAxis().isHorizontal()?d:Direction.NORTH;}
    public BlockPos origin(){return new BlockPos((data.get(10)&65535)|(data.get(11)<<16),(data.get(12)&65535)|(data.get(13)<<16),(data.get(14)&65535)|(data.get(15)<<16));}
    public ItemStack input(){return inventory.getItem(0);}public ItemStack output(){return inventory.getItem(1);}
    public boolean isFor(ForgingArrayBlockEntity controller){return inventory==controller;}
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof ServerPlayer server)||!(inventory instanceof ForgingArrayBlockEntity root)||player.containerMenu!=this)return false;
        return button==0?root.start(server,this):button==1?root.cancel(server,this):button>=10&&button<10+ForgeMark.TRAITS.size()&&root.select(server,this,button-10);
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(active()&&slot==0)return;super.clicked(slot,button,type,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);
        if(!slot.hasItem()||!slot.mayPickup(player))return ItemStack.EMPTY;
        var stack=slot.getItem();var original=stack.copy();
        if(index<2){if(!moveItemStackTo(stack,2,38,true))return ItemStack.EMPTY;}
        else if(WorkshopEquipment.craft(stack).isPresent()) {if(active()||!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(index<29){if(!moveItemStackTo(stack,29,38,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,2,29,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);return original;
    }
}
