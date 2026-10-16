package com.example.myvillage.forging;

import com.example.myvillage.block.ForgingTableBlock;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Private batch. Closing returns the paid copper, so no shared job or invisible block inventory exists. */
public final class CopperInfusionMenu extends AbstractContainerMenu {
    public static final int START=0,PAUSE=1,BACK=2;
    private final Inventory inventory;
    private final ContainerLevelAccess access;
    private final SimpleContainer work=new SimpleContainer(2);
    private final ContainerData data=new SimpleContainerData(2); // running, status
    private long nextPulse;
    public CopperInfusionMenu(int id,Inventory inventory){this(id,inventory,ContainerLevelAccess.NULL);}
    public CopperInfusionMenu(int id,Inventory inventory,ContainerLevelAccess access){
        super(ForgingMenus.INFUSION.get(),id);this.inventory=inventory;this.access=access;
        if(!inventory.player.level().isClientSide()&&access==ContainerLevelAccess.NULL)throw new IllegalArgumentException("Placed ding required");
        addSlot(new Slot(work,0,88,56){
            @Override public boolean mayPlace(ItemStack stack){return ForgingIngredients.infusionInput(stack);}
            @Override public int getMaxStackSize(){return InfusionRules.get().maxBatch();}
        });
        addSlot(new Slot(work,1,200,56){@Override public boolean mayPlace(ItemStack stack){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,70+col*18,156+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,70+col*18,214));
        addDataSlots(data);
    }
    public boolean running(){return data.get(0)==1;}
    public int status(){return data.get(1);}
    public int paidQi(){return work.getItem(0).getOrDefault(ModDataComponents.COPPER_INFUSION.get(),new CopperInfusion(0)).paidQi();}
    public int count(){return work.getItem(0).getCount();}
    public boolean ready(){return ForgingIngredients.infusionInput(work.getItem(0))&&count()<=InfusionRules.get().maxBatch()&&work.getItem(1).isEmpty();}
    private boolean authorized(Player p){return p==inventory.player&&p.containerMenu==this&&p.isAlive()&&!p.isSpectator()&&stillValid(p);}
    @Override public boolean stillValid(Player p){return p==inventory.player&&stillValid(access,p,ModBlocks.FORGING_TABLE.get());}
    private void pause(int status){data.set(0,0);data.set(1,status);}
    @Override public boolean clickMenuButton(Player p,int button){
        if(!(p instanceof ServerPlayer server)||!authorized(p))return false;
        if(button==BACK){
            server.openMenu(new SimpleMenuProvider((id,inv,viewer)->new ForgingMenu(id,inv,access),Component.translatable("block.myvillage.forging_table")));
            return true;
        }
        if(button==PAUSE){pause(2);return true;}
        if(button!=START||running())return false;
        if(!ready()){pause(3);return false;}
        if(!CultivationService.getProfile(server).awakened()){pause(4);return false;}
        data.set(0,1);data.set(1,1);nextPulse=server.level().getGameTime()+InfusionRules.get().pulseTicks();return true;
    }
    @Override public void broadcastChanges(){
        if(inventory.player instanceof ServerPlayer player&&running()){
            if(!authorized(player)||!ready())pause(3);
            else if(!CultivationService.getProfile(player).awakened())pause(4);
            else if(player.level().getGameTime()>=nextPulse){
                nextPulse=player.level().getGameTime()+InfusionRules.get().pulseTicks();
                var plan=InfusionRules.get().plan(count(),paidQi(),CultivationService.getProfile(player).currentSpiritualPower());
                if(plan.isEmpty()){pause(5);}
                else {
                    var pulse=plan.get();
                    ItemStack result=pulse.complete()?new ItemStack(ModItems.CHITONG_INGOT.get(),count()):work.getItem(0).copy();
                    if(!pulse.complete())result.set(ModDataComponents.COPPER_INFUSION.get(),new CopperInfusion(pulse.paidQi()));
                    if(CultivationService.spendSpiritualPower(player,pulse.cost())){
                        if(pulse.complete()){work.setItem(0,ItemStack.EMPTY);work.setItem(1,result);pause(6);}
                        else work.setItem(0,result);
                        access.execute((level,pos)->ForgingTableBlock.infusing((net.minecraft.server.level.ServerLevel)level,pos,pulse.complete()));
                    }else pause(5);
                }
            }
        }
        super.broadcastChanges();
    }
    @Override public void removed(Player p){pause(2);super.removed(p);if(!p.level().isClientSide())clearContainer(p,work);}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        if(index<2){if(!moveItemStackTo(stack,2,38,true))return ItemStack.EMPTY;}
        else if(ForgingIngredients.infusionInput(stack)){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(index<29){if(!moveItemStackTo(stack,29,38,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,2,29,false))return ItemStack.EMPTY;
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return original;
    }
}
