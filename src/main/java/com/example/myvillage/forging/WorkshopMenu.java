package com.example.myvillage.forging;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.block.entity.ForgingAnvilBlockEntity;
import com.example.myvillage.block.entity.QuenchingTroughBlockEntity;
import net.minecraft.core.Direction;
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
    private final SimpleContainer work;
    private final ForgingAnvilBlockEntity anvil;
    // recipe, trait, training direction, treatment, heat, action, remaining, committed hits, status, quality ceiling
    private final ContainerData data=new SimpleContainerData(12);
    private final List<DataSlot> known=new ArrayList<>();
    private ItemStack observed=ItemStack.EMPTY,scheduled=ItemStack.EMPTY;
    private QuenchingTroughBlockEntity trough;
    private long lastTick=Long.MIN_VALUE;
    public WorkshopMenu(int id,Inventory inventory){this(id,inventory,ContainerLevelAccess.NULL);}
    public WorkshopMenu(int id,Inventory inventory,ContainerLevelAccess access){
        super(ForgingMenus.WORKSHOP.get(),id);this.inventory=inventory;this.access=access;
        if(!inventory.player.level().isClientSide()&&access==ContainerLevelAccess.NULL)throw new IllegalArgumentException("Placed workshop required");
        anvil=inventory.player instanceof ServerPlayer?access.evaluate((level,pos)->
                level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity found?Optional.of(found):Optional.<ForgingAnvilBlockEntity>empty(),Optional.<ForgingAnvilBlockEntity>empty()).orElse(null):null;
        work=anvil==null?new SimpleContainer(2):anvil.contents();
        data.set(2,1);data.set(5,-1);
        if(anvil!=null){
            data.set(10,2);
            var state=anvil.storage();data.set(0,state.recipe());data.set(1,state.trait());data.set(2,state.direction());data.set(3,state.treatment());
            data.set(4,anvil.heat());data.set(7,anvil.hits()%30000);observed=input().copy();
            if(piece()!=null&&piece().stage()>=2)data.set(3,piece().treatment());
            normalizeSelection();
        }
        addSlot(new Slot(work,0,94,104){
            @Override public boolean mayPlace(ItemStack stack){return !shared()&&!busy()&&(WorkshopItems.valid(stack)||WorkshopEquipment.craft(stack).isPresent());}
            @Override public boolean mayPickup(Player player){return !shared()&&!busy();}
            @Override public int getMaxStackSize(){return 1;}
        });
        addSlot(new Slot(work,1,188,104){@Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player player){return !shared();}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,144+col*18,151+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,144+col*18,209));
        addDataSlots(data);
        for(var craft:WorkshopRules.get().crafts()){
            var slot=inventory.player instanceof ServerPlayer server?new DataSlot(){
                @Override public int get(){return !craft.requires_pattern()||ForgingKnowledge.knows(server,craft.recipe())?1:0;}
                @Override public void set(int value){}
            }:DataSlot.standalone();known.add(slot);addDataSlot(slot);
        }
        if(anvil!=null&&inventory.player instanceof ServerPlayer server)anvil.bind(this,server);
    }
    public ItemStack input(){return work.getItem(0);}public ItemStack output(){return work.getItem(1);}
    public int recipeIndex(){return data.get(0);}public int traitIndex(){return data.get(1);}public int direction(){return data.get(2);}
    public int treatment(){return data.get(3);}public int heat(){return anvil==null?data.get(4):anvil.heat();}public int action(){return data.get(5);}
    private void setHeat(int ticks){data.set(4,ticks);if(anvil!=null)anvil.setHeat(ticks);}
    public int remaining(){return data.get(6);}public int hitSequence(){return data.get(7);}public int status(){return data.get(8);}
    public int qualityCap(){return data.get(9);}public boolean ding(){return data.get(10)==1;}public boolean busy(){return action()>=0;}
    public boolean shared(){return anvil!=null||data.get(10)==2;}
    public boolean knows(int index){return index>=0&&index<known.size()&&known.get(index).get()==1;}
    public WorkshopRules.Craft craft(){return WorkshopRules.get().crafts().get(recipeIndex());}
    public ForgingRules.Recipe recipe(){return ForgingRules.get().recipe(craft().recipe()).orElseThrow();}
    public String trait(){return ForgeMark.TRAITS.get(traitIndex());}
    public Workpiece piece(){return WorkshopItems.valid(input())?input().get(ModDataComponents.WORKPIECE.get()):null;}
    public TemperRecord temperRecord(){return WorkshopEquipment.record(input());}
    public int available(){return input().isEmpty()||piece()!=null?0:WorkshopRules.get().available(temperRecord(),craft());}
    public boolean trainingDirection(int direction){return craft().training(direction)&&(direction!=3||(recipe().traits().contains("channeling")&&input().has(ModDataComponents.FORGE_MARK.get())));}
    public static String actionName(int action){return action>=0&&action<ACTIONS.size()?ACTIONS.get(action):"idle";}
    public int duration(int action){return action==STRIKE?WorkshopRules.get().strike_ticks():WorkshopRules.get().operation_ticks().getOrDefault(actionName(action),1);}
    @Override public boolean stillValid(Player player){return player==inventory.player&&(stillValid(access,player,ModBlocks.FORGING_TABLE.get())||
            (stillValid(access,player,ModBlocks.FORGING_ANVIL.get())&&access.evaluate((level,pos)->
                    level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity anvil&&anvil.owns(this),false)));}
    private boolean authorized(Player player){return player==inventory.player&&player.containerMenu==this&&player.isAlive()&&!player.isSpectator()&&stillValid(player);}
    private boolean reject(int status){data.set(8,status);return false;}
    /** Stable reason codes are used by the native UI; inventory costs are checked again at commitment. */
    public boolean quenchingAt(QuenchingTroughBlockEntity target) {
        return trough==target&&action()==QUENCH&&inventory.player.isAlive()&&inventory.player.containerMenu==this&&stillValid(inventory.player);
    }
    private QuenchingTroughBlockEntity findTrough() {
        // ContainerLevelAccess 不接受回调返回 null；缺槽也必须返回非空容器。
        return access.evaluate((level,pos)->{
            for(var direction:Direction.Plane.HORIZONTAL) {
                var target=pos.relative(direction);
                if(level.hasChunkAt(target)&&level.getBlockEntity(target) instanceof QuenchingTroughBlockEntity found
                        &&found.filled()&&(found.owns(this)||found.available())) return Optional.of(found);
            }
            return Optional.<QuenchingTroughBlockEntity>empty();
        },Optional.<QuenchingTroughBlockEntity>empty()).orElse(null);
    }
    private void releaseTrough() { if(trough!=null){trough.release(this);trough=null;} }
    public int reason(int action){
        if(action==STRIKE)return 17;
        if(!output().isEmpty())return 1;
        if(action==START)return !input().isEmpty()?9:!knows(recipeIndex())?8:0;
        if(input().isEmpty())return 9;
        var piece=piece();boolean equipment=WorkshopEquipment.craft(input()).isPresent();
        if(action==HEAT){
            if(piece!=null&&piece.stage()<2)return 0;
            if(!equipment)return 2;
            if(temperRecord().strikes()>=craft().max_strikes())return 6;
            return available()>0?0:5;
        }
        if(action==QUENCH&&!craft().treatment(treatment()))return 11;
        if(action==QUENCH)return piece!=null&&piece.stage()==1&&piece.blows()==WorkshopRules.get().forming_strikes()?(heat()>0?(inventory.player.level().isClientSide()?data.get(11):findTrough()!=null?0:16):3):2;
        if(action==TEMPER)return piece!=null&&piece.stage()==2?0:2;
        if(action==GRIND)return piece!=null&&piece.stage()==3?0:2;
        if(action==ASSEMBLE)return piece!=null&&piece.stage()==4?0:2;
        if(action==INFUSE){if(!ding())return 15;if(!equipment||input().has(ModDataComponents.FORGE_MARK.get()))return 2;if(qualityCap()==0)return 7;return recipe().traits().contains(trait())?0:11;}
        if(action==REPAIR)return equipment&&input().isDamaged()?0:2;
        return 11;
    }
    public Map<String,Integer> costs(int action){
        var rules=WorkshopRules.get();return switch(action){
            case START->rules.blankCosts(recipe());case HEAT,TEMPER->rules.fuel_cost();
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
        if(button==STRIKE)return reject(17);
        if(busy())return reject(10);
        if(button==COPPER){if(!ding())return reject(15);server.openMenu(new SimpleMenuProvider((id,inv,p)->new CopperInfusionMenu(id,inv,access),Component.translatable("screen.myvillage.infusion.title")));return true;}
        if(button>=RECIPE_BASE&&button<RECIPE_BASE+known.size()){
            if(!input().isEmpty())return reject(9);data.set(0,button-RECIPE_BASE);normalizeSelection();data.set(8,0);return true;
        }
        if(button>=TRAIT_BASE&&button<TRAIT_BASE+ForgeMark.TRAITS.size()){
            if(!recipe().traits().contains(ForgeMark.TRAITS.get(button-TRAIT_BASE)))return reject(11);data.set(1,button-TRAIT_BASE);return true;
        }
        if(button>=PATH_BASE+1&&button<=PATH_BASE+3){if(!trainingDirection(button-PATH_BASE))return reject(11);data.set(2,button-PATH_BASE);return true;}
        if(button>=TREATMENT_BASE&&button<=TREATMENT_BASE+2){if(!craft().treatment(button-TREATMENT_BASE))return reject(11);if(piece()!=null&&piece().stage()>=2)return reject(2);data.set(3,button-TREATMENT_BASE);return true;}
        if(button<START||button>REPAIR)return false;
        int reason=reason(button);if(reason!=0)return reject(reason);
        if(button==START)return commit(server,button);
        if(!canPay(player,costs(button)))return reject(4);
        if(button==QUENCH){
            trough=findTrough();
            if(trough==null||!trough.reserve(this)){trough=null;return reject(16);}
        }
        scheduled=input().copy();data.set(5,button);data.set(6,duration(button));data.set(8,0);publishDisplay();return true;
    }
    private static boolean canPay(Player player,Map<String,Integer> costs){return costs.isEmpty()||ForgingCosts.plan(SpiritStoneSources.open(player).copies(),costs).isPresent();}
    private void normalizeSelection(){
        if(!recipe().traits().contains(trait()))data.set(1,ForgeMark.TRAITS.indexOf(recipe().traits().getFirst()));
        if(!trainingDirection(direction()))data.set(2,craft().armor()?2:1);
        if(!craft().treatment(treatment())&&(piece()==null||piece().stage()<2))data.set(3,2);
    }
    private boolean commit(ServerPlayer player,int operation){
        int reason=reason(operation);if(reason!=0)return reject(reason);
        var rules=WorkshopRules.get();ItemStack result=input().copy();boolean toOutput=false;
        var piece=piece();var costs=costs(operation);
        switch(operation){
            case START->result=WorkshopItems.blank(new Workpiece(recipe().id(),0,0,0));
            case HEAT->{}
            case QUENCH->result=WorkshopItems.blank(piece.next(treatment()));
            case TEMPER,GRIND->result=WorkshopItems.blank(piece.next(piece.treatment()));
            case ASSEMBLE->{
                result=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe().output())));
                result.set(ModDataComponents.TEMPER.get(),new TemperRecord(0,0,0,0,piece.treatment()));WorkshopEquipment.refresh(result);toOutput=true;
            }
            case INFUSE->{
                // The aptitude ceiling is read at commitment. A completed item's quality never rerolls.
                var prepared=EquipmentInfusion.prepare(input(),trait(),qualityCap());
                if(!prepared.valid())return reject(prepared.reason());
                result=prepared.result();toOutput=true;
            }
            case REPAIR->{result.setDamageValue(Math.max(0,result.getDamageValue()-ForgedEquipment.repairAmount(result)));toOutput=true;}
            default->{return false;}
        }
        if(operation==QUENCH&&(trough==null||!trough.consume(this)))return reject(16);
        if(!costs.isEmpty()){
            var source=SpiritStoneSources.open(player);var plan=ForgingCosts.plan(source.copies(),costs);
            if(plan.isEmpty()||!source.commit(plan.get()))return reject(4);
        }
        if(toOutput){work.setItem(0,ItemStack.EMPTY);work.setItem(1,result);setHeat(0);}
        else work.setItem(0,result);
        if(operation==HEAT)setHeat(rules.heat_ticks());
        if(operation==QUENCH)setHeat(0);
        if(operation==STRIKE)data.set(7,(hitSequence()+1)%30000);
        observed=input().copy();scheduled=input().copy();inventory.setChanged();
        data.set(8,operation==STRIKE?13:14);
        if(operation!=START&&operation!=QUENCH)feedback(player,actionName(operation));
        publishDisplay();
        return true;
    }
    private void publishDisplay(){access.execute((level,pos)->{
        if(level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity anvil)anvil.publish(this);
    });}
    private void feedback(ServerPlayer player,String operation){
        access.execute((level,pos)->{
            var rules=WorkshopRules.get();float pitch=1+(level.random.nextFloat()*2-1)*rules.sound_pitch_jitter();
            var location=operation.equals("quench")&&trough!=null?trough.getBlockPos():pos;
            level.playSound(null,location,WorkshopSounds.get(operation),SoundSource.BLOCKS,rules.sound_volume(),pitch);
            var feedback=ForgingDingVisuals.get().feedback();
            if(level instanceof ServerLevel server)server.sendParticles(operation.equals("quench")?ParticleTypes.CLOUD:
                    operation.equals("infuse")?ParticleTypes.ENCHANT:ParticleTypes.CRIT,
                    location.getX()+0.5,location.getY()+.75,location.getZ()+0.5,feedback.sparkCount(),feedback.sparkSpread(),feedback.sparkSpread(),feedback.sparkSpread(),feedback.sparkSpeed());
        });
    }
    @Override public void broadcastChanges(){
        if(inventory.player instanceof ServerPlayer player){
            long now=player.level().getGameTime();
            if(now!=lastTick){lastTick=now;
                data.set(10,access.evaluate((level,pos)->level.getBlockState(pos).is(ModBlocks.FORGING_TABLE.get())?1:
                        level.getBlockState(pos).is(ModBlocks.FORGING_ANVIL.get())?2:0,0));
                data.set(9,EquipmentInfusion.qualityCap(player));
                if(!ItemStack.matches(observed,input())){
                    setHeat(0);data.set(5,-1);data.set(6,0);observed=input().copy();
                    String recipe=piece()!=null?piece().recipe():WorkshopEquipment.craft(input()).map(WorkshopRules.Craft::recipe).orElse("");
                    for(int i=0;i<WorkshopRules.get().crafts().size();i++)if(WorkshopRules.get().crafts().get(i).recipe().equals(recipe))data.set(0,i);
                    if(piece()!=null&&piece().stage()>=2)data.set(3,piece().treatment());
                    normalizeSelection();
                }
                data.set(11,findTrough()!=null?0:16);
                if(anvil==null){if(heat()>0&&action()!=QUENCH)data.set(4,heat()-1);}
                else{
                    // Keep the existing quench rule: the checked heat is held during immersion.
                    if(action()==QUENCH&&data.get(4)>0)anvil.setHeat(data.get(4));
                    data.set(4,anvil.heat());data.set(7,anvil.hits()%30000);
                }
                if(busy()){
                    if(!authorized(player)||!ItemStack.matches(scheduled,input())){data.set(5,-1);data.set(6,0);}
                    else{
                        int operation=action(),remaining=remaining()-1;data.set(6,remaining);
                        if(operation==QUENCH&&trough!=null&&trough.owns(this)&&trough.filled()
                                &&remaining==duration(QUENCH)*3/4)feedback(player,"quench");
                        int contact=operation==STRIKE?duration(operation)-WorkshopRules.get().contact_tick():0;
                        if(remaining==contact&&!commit(player,operation)){data.set(5,-1);data.set(6,0);}
                        if(remaining<=0)data.set(5,-1);
                    }
                }
            }
        }
        if(inventory.player instanceof ServerPlayer){if(action()!=QUENCH)releaseTrough();publishDisplay();}
        super.broadcastChanges();
    }
    @Override public void removed(Player player){
        releaseTrough();data.set(5,-1);data.set(4,0);super.removed(player);
        if(!player.level().isClientSide()){
            access.execute((level,pos)->{if(level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity anvil)anvil.release(this);});
            if(anvil==null)clearContainer(player,work);
        }
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size()||(index==0&&busy()))return ItemStack.EMPTY;
        if(shared()&&index<2)return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var original=stack.copy();
        if(index<2){if(!moveItemStackTo(stack,2,38,true))return ItemStack.EMPTY;}
        else if(!shared()&&!busy()&&(WorkshopItems.valid(stack)||WorkshopEquipment.craft(stack).isPresent())){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(index<29){if(!moveItemStackTo(stack,29,38,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,2,29,false))return ItemStack.EMPTY;
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return original;
    }
}
