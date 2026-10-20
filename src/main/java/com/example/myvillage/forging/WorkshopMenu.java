package com.example.myvillage.forging;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.cultivation.CultivationAttachments;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.cultivation.crafting.CraftingRules;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Server-owned operations. Only contact commits a strike; every other unpaid action is cancellable. */
public final class WorkshopMenu extends AbstractContainerMenu {
    public static final int WIDTH=316,HEIGHT=234;
    public static final int START=0,HEAT=1,STRIKE=2,QUENCH=3,TEMPER=4,GRIND=5,ASSEMBLE=6,INFUSE=7,REPAIR=8,COPPER=9;
    public static final int RECIPE_BASE=20,TRAIT_BASE=40,PATH_BASE=50,TREATMENT_BASE=60;
    private static final List<String> ACTIONS=List.of("start","heat","strike","quench","temper","grind","assemble","infuse","repair");
    private final Inventory inventory;
    private final ContainerLevelAccess access;
    private final SimpleContainer work=new SimpleContainer(2);
    // recipe, trait, training direction, treatment, heat, action, remaining, committed hits, status, quality ceiling
    private final ContainerData data=new SimpleContainerData(10);
    private final List<DataSlot> known=new ArrayList<>();
    private ItemStack observed=ItemStack.EMPTY,scheduled=ItemStack.EMPTY;
    private long lastTick=Long.MIN_VALUE;
    public WorkshopMenu(int id,Inventory inventory){this(id,inventory,ContainerLevelAccess.NULL);}
    public WorkshopMenu(int id,Inventory inventory,ContainerLevelAccess access){
        super(ForgingMenus.WORKSHOP.get(),id);this.inventory=inventory;this.access=access;
        if(!inventory.player.level().isClientSide()&&access==ContainerLevelAccess.NULL)throw new IllegalArgumentException("Placed workshop required");
        data.set(2,1);data.set(5,-1);
        addSlot(new Slot(work,0,94,104){
            @Override public boolean mayPlace(ItemStack stack){return !busy()&&(WorkshopItems.valid(stack)||WorkshopEquipment.craft(stack).isPresent());}
            @Override public boolean mayPickup(Player player){return !busy();}
            @Override public int getMaxStackSize(){return 1;}
        });
        addSlot(new Slot(work,1,188,104){@Override public boolean mayPlace(ItemStack stack){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,144+col*18,151+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,144+col*18,209));
        addDataSlots(data);
        for(var craft:WorkshopRules.get().crafts()){
            var slot=inventory.player instanceof ServerPlayer server?new DataSlot(){
                @Override public int get(){return !craft.requires_pattern()||ForgingKnowledge.knows(server,craft.recipe())?1:0;}
                @Override public void set(int value){}
            }:DataSlot.standalone();known.add(slot);addDataSlot(slot);
        }
    }
    public ItemStack input(){return work.getItem(0);}public ItemStack output(){return work.getItem(1);}
    public int recipeIndex(){return data.get(0);}public int traitIndex(){return data.get(1);}public int direction(){return data.get(2);}
    public int treatment(){return data.get(3);}public int heat(){return data.get(4);}public int action(){return data.get(5);}
    public int remaining(){return data.get(6);}public int hitSequence(){return data.get(7);}public int status(){return data.get(8);}
    public int qualityCap(){return data.get(9);}public boolean busy(){return action()>=0;}
    public boolean knows(int index){return index>=0&&index<known.size()&&known.get(index).get()==1;}
    public WorkshopRules.Craft craft(){return WorkshopRules.get().crafts().get(recipeIndex());}
    public ForgingRules.Recipe recipe(){return ForgingRules.get().recipe(craft().recipe()).orElseThrow();}
    public String trait(){return ForgeMark.TRAITS.get(traitIndex());}
    public Workpiece piece(){return WorkshopItems.valid(input())?input().get(ModDataComponents.WORKPIECE.get()):null;}
    public TemperRecord temperRecord(){return WorkshopEquipment.record(input());}
    public int available(){return input().isEmpty()||piece()!=null?0:WorkshopRules.get().available(temperRecord(),craft());}
    public boolean trainingDirection(int direction){return direction==1||direction==2||(direction==3&&recipe().traits().contains("channeling")&&input().has(ModDataComponents.FORGE_MARK.get()));}
    public static String actionName(int action){return action>=0&&action<ACTIONS.size()?ACTIONS.get(action):"idle";}
    public int duration(int action){return action==STRIKE?WorkshopRules.get().strike_ticks():WorkshopRules.get().operation_ticks().getOrDefault(actionName(action),1);}
    @Override public boolean stillValid(Player player){return player==inventory.player&&(stillValid(access,player,ModBlocks.FORGING_TABLE.get())||stillValid(access,player,ModBlocks.FORGING_ANVIL.get()));}
    private boolean authorized(Player player){return player==inventory.player&&player.containerMenu==this&&player.isAlive()&&!player.isSpectator()&&stillValid(player);}
    private boolean reject(int status){data.set(8,status);return false;}
    /** Stable reason codes are used by the native UI; inventory costs are checked again at commitment. */
    public int reason(int action){
        if(!output().isEmpty())return 1;
        if(action==START)return !input().isEmpty()?9:!knows(recipeIndex())?8:0;
        if(input().isEmpty())return 9;
        var piece=piece();boolean equipment=WorkshopEquipment.craft(input()).isPresent();
        if(action==HEAT)return piece!=null&&piece.stage()<2||equipment?0:2;
        if(action==STRIKE){
            if(piece!=null){if(piece.stage()>1||piece.blows()>=WorkshopRules.get().forming_strikes())return 2;}
            else if(equipment){if(!trainingDirection(direction()))return 11;if(temperRecord().strikes()>=craft().max_strikes())return 6;if(available()==0)return 5;}
            else return 9;
            return heat()>0?0:3;
        }
        if(action==QUENCH)return piece!=null&&piece.stage()==1&&piece.blows()==WorkshopRules.get().forming_strikes()?(heat()>0?0:3):2;
        if(action==TEMPER)return piece!=null&&piece.stage()==2?0:2;
        if(action==GRIND)return piece!=null&&piece.stage()==3?0:2;
        if(action==ASSEMBLE)return piece!=null&&piece.stage()==4?0:2;
        if(action==INFUSE){if(!equipment||input().has(ModDataComponents.FORGE_MARK.get()))return 2;if(qualityCap()==0)return 7;return recipe().traits().contains(trait())?0:11;}
        if(action==REPAIR)return equipment&&input().isDamaged()?0:2;
        return 11;
    }
    public Map<String,Integer> costs(int action){
        var rules=WorkshopRules.get();return switch(action){
            case START->rules.blankCosts(recipe());case HEAT,TEMPER->rules.fuel_cost();case QUENCH->rules.quench_cost();
            case GRIND->rules.grind_cost();case STRIKE->piece()==null?rules.training_cost():Map.of();
            case INFUSE->rules.infusionCosts(recipe(),trait());case REPAIR->ForgingRules.get().repairCosts();default->Map.of();
        };
    }
    public int nextAction(){
        if(input().isEmpty())return START;var p=piece();
        if(p==null)return available()>0?(heat()>0?STRIKE:HEAT):INFUSE;
        if(p.stage()<2){if(heat()==0)return HEAT;return p.blows()<WorkshopRules.get().forming_strikes()?STRIKE:QUENCH;}
        return switch(p.stage()){case 2->TEMPER;case 3->GRIND;default->ASSEMBLE;};
    }
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof ServerPlayer server)||!authorized(player))return false;
        if(busy())return reject(10);
        if(button==COPPER){server.openMenu(new SimpleMenuProvider((id,inv,p)->new CopperInfusionMenu(id,inv,access),Component.translatable("screen.myvillage.infusion.title")));return true;}
        if(button>=RECIPE_BASE&&button<RECIPE_BASE+known.size()){
            if(!input().isEmpty())return reject(9);data.set(0,button-RECIPE_BASE);normalizeSelection();data.set(8,0);return true;
        }
        if(button>=TRAIT_BASE&&button<TRAIT_BASE+ForgeMark.TRAITS.size()){
            if(!recipe().traits().contains(ForgeMark.TRAITS.get(button-TRAIT_BASE)))return reject(11);data.set(1,button-TRAIT_BASE);return true;
        }
        if(button>=PATH_BASE+1&&button<=PATH_BASE+3){if(!trainingDirection(button-PATH_BASE))return reject(11);data.set(2,button-PATH_BASE);return true;}
        if(button>=TREATMENT_BASE&&button<=TREATMENT_BASE+2){if(piece()!=null&&piece().stage()>=2)return reject(2);data.set(3,button-TREATMENT_BASE);return true;}
        if(button<START||button>REPAIR)return false;
        int reason=reason(button);if(reason!=0)return reject(reason);
        if(button==START)return commit(server,button);
        if(!canPay(player,costs(button)))return reject(4);
        scheduled=input().copy();data.set(5,button);data.set(6,duration(button));data.set(8,0);return true;
    }
    private static boolean canPay(Player player,Map<String,Integer> costs){return costs.isEmpty()||ForgingCosts.plan(SpiritStoneSources.open(player).copies(),costs).isPresent();}
    private void normalizeSelection(){
        if(!recipe().traits().contains(trait()))data.set(1,ForgeMark.TRAITS.indexOf(recipe().traits().getFirst()));
        if(!trainingDirection(direction()))data.set(2,1);
    }
    private boolean commit(ServerPlayer player,int operation){
        int reason=reason(operation);if(reason!=0)return reject(reason);
        var rules=WorkshopRules.get();ItemStack result=input().copy();boolean toOutput=false;
        var piece=piece();var costs=costs(operation);
        switch(operation){
            case START->result=WorkshopItems.blank(new Workpiece(recipe().id(),0,0,0));
            case HEAT->{}
            case STRIKE->{
                if(piece!=null)result=WorkshopItems.blank(piece.strike(rules));
                else{result.set(ModDataComponents.TEMPER.get(),temperRecord().strike(direction(),craft(),rules));WorkshopEquipment.refresh(result);}
            }
            case QUENCH->result=WorkshopItems.blank(piece.next(treatment()));
            case TEMPER,GRIND->result=WorkshopItems.blank(piece.next(piece.treatment()));
            case ASSEMBLE->{
                result=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe().output())));
                result.set(ModDataComponents.TEMPER.get(),new TemperRecord(0,0,0,0,piece.treatment()));WorkshopEquipment.refresh(result);toOutput=true;
            }
            case INFUSE->{
                // The aptitude ceiling is read at commitment. A completed item's quality never rerolls.
                ForgedEquipment.apply(result,new ForgeMark(trait(),qualityCap()));
                if(result.has(ModDataComponents.TEMPER.get()))WorkshopEquipment.refresh(result);toOutput=true;
            }
            case REPAIR->{result.setDamageValue(Math.max(0,result.getDamageValue()-ForgedEquipment.repairAmount(result)));toOutput=true;}
            default->{return false;}
        }
        if(!costs.isEmpty()){
            var source=SpiritStoneSources.open(player);var plan=ForgingCosts.plan(source.copies(),costs);
            if(plan.isEmpty()||!source.commit(plan.get()))return reject(4);
        }
        if(toOutput){work.setItem(0,ItemStack.EMPTY);work.setItem(1,result);data.set(4,0);}
        else work.setItem(0,result);
        if(operation==HEAT)data.set(4,rules.heat_ticks());
        if(operation==QUENCH){data.set(4,0);rules.quench_returns().forEach((id,n)->{
            var remainder=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)),n);
            if(!player.getInventory().add(remainder))player.drop(remainder,false);
        });}
        if(operation==STRIKE)data.set(7,(hitSequence()+1)%30000);
        observed=input().copy();scheduled=input().copy();inventory.setChanged();
        data.set(8,operation==STRIKE?13:14);
        if(operation!=START)feedback(player,actionName(operation));
        return true;
    }
    private void feedback(ServerPlayer player,String operation){
        access.execute((level,pos)->{
            var rules=WorkshopRules.get();float pitch=1+(level.random.nextFloat()*2-1)*rules.sound_pitch_jitter();
            level.playSound(null,pos,WorkshopSounds.get(operation),SoundSource.BLOCKS,rules.sound_volume(),pitch);
            if(level instanceof ServerLevel server)server.sendParticles(operation.equals("quench")?ParticleTypes.CLOUD:
                    operation.equals("infuse")?ParticleTypes.ENCHANT:ParticleTypes.CRIT,
                    pos.getX()+0.5,pos.getY()+1.05,pos.getZ()+0.5,4,0.12,0.08,0.12,0.03);
        });
    }
    @Override public void broadcastChanges(){
        if(inventory.player instanceof ServerPlayer player){
            long now=player.level().getGameTime();
            if(now!=lastTick){lastTick=now;
                var apt=player.getData(CultivationAttachments.CRAFTING.get());
                data.set(9,apt.initialized()&&CultivationService.getProfile(player).awakened()?apt.qualityCap(CraftingAptitudes.Profession.FORGING,CraftingRules.get()):0);
                if(!ItemStack.matches(observed,input())){
                    data.set(4,0);data.set(5,-1);data.set(6,0);observed=input().copy();
                    String recipe=piece()!=null?piece().recipe():WorkshopEquipment.craft(input()).map(WorkshopRules.Craft::recipe).orElse("");
                    for(int i=0;i<WorkshopRules.get().crafts().size();i++)if(WorkshopRules.get().crafts().get(i).recipe().equals(recipe))data.set(0,i);
                    normalizeSelection();
                }
                if(heat()>0)data.set(4,heat()-1);
                if(busy()){
                    if(!authorized(player)||!ItemStack.matches(scheduled,input())){data.set(5,-1);data.set(6,0);}
                    else{
                        int operation=action(),remaining=remaining()-1;data.set(6,remaining);
                        int contact=operation==STRIKE?duration(operation)-WorkshopRules.get().contact_tick():0;
                        if(remaining==contact&&!commit(player,operation)){data.set(5,-1);data.set(6,0);}
                        if(remaining<=0)data.set(5,-1);
                    }
                }
            }
        }
        super.broadcastChanges();
    }
    @Override public void removed(Player player){data.set(5,-1);data.set(4,0);super.removed(player);if(!player.level().isClientSide())clearContainer(player,work);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size()||(index==0&&busy()))return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var original=stack.copy();
        if(index<2){if(!moveItemStackTo(stack,2,38,true))return ItemStack.EMPTY;}
        else if(!busy()&&(WorkshopItems.valid(stack)||WorkshopEquipment.craft(stack).isPresent())){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(index<29){if(!moveItemStackTo(stack,29,38,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,2,29,false))return ItemStack.EMPTY;
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return original;
    }
}
