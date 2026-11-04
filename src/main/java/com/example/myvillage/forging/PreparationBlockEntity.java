package com.example.myvillage.forging;

import com.example.myvillage.block.*;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Ordinary ding prepares blanks or finishes a paid standard item; it never exposes manual forging controls. */
public final class PreparationBlockEntity extends FoundryBlockEntity {
    private List<ItemStack> reserved=new ArrayList<>();
    private ItemStack snapshot=ItemStack.EMPTY,frozen=ItemStack.EMPTY;
    private UUID owner;
    private boolean paidRaw,invalid;
    public PreparationBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.FORGING_DING.get(),pos,state);}
    @Override public FoundryMenu.Kind kind(){return FoundryMenu.Kind.PREPARATION;}
    @Override public boolean active(){return !frozen.isEmpty()||!reserved.isEmpty()||invalid;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0&&!active()&&WorkshopItems.valid(stack);}
    public WorkshopRules.Craft selected(){return WorkshopRules.get().crafts().get(recipe);}
    private static void add(Map<String,Integer> costs,String item,int amount){if(amount>0)costs.merge(item,amount,Math::addExact);}
    public static Map<String,Integer> automaticCosts(Workpiece piece,boolean raw){
        var rules=WorkshopRules.get();var craft=rules.craft(piece.recipe());var recipe=ForgingRules.get().recipe(piece.recipe()).orElseThrow();
        var costs=new TreeMap<String,Integer>();if(raw)costs.putAll(rules.blankCosts(recipe));
        if(piece.stage()<2){rules.fuel_cost().forEach((key,n)->add(costs,key,n));add(costs,"minecraft:water_bucket",1);}
        if(piece.stage()<3)rules.fuel_cost().forEach((key,n)->add(costs,key,n));
        if(piece.stage()<4)rules.grind_cost().forEach((key,n)->add(costs,key,n));
        if(craft.armor())add(costs,FoundryRules.get().assembly().armor_material(),1);else add(costs,"minecraft:stick",2);
        return Collections.unmodifiableMap(costs);
    }
    public static ItemStack standard(ItemStack input,int quality){
        if(!WorkshopItems.valid(input))throw new IllegalArgumentException("Invalid automatic input");
        var piece=input.get(ModDataComponents.WORKPIECE.get());var rules=WorkshopRules.get();
        var ready=new Workpiece(piece.recipe(),4,rules.formingPoints(piece.recipe()),piece.stage()<2?(rules.craft(piece.recipe()).armor()?2:0):piece.treatment());
        return WorkshopEquipment.complete(WorkshopItems.advance(input,ready),quality,"wood");
    }
    @Override public boolean action(ServerPlayer player,FoundryMenu menu,int button){
        if(!actor(player,menu))return false;
        if(button>=20&&button<20+WorkshopRules.get().crafts().size()){
            if(active()||!items.get(0).isEmpty())return refuse(1);
            recipe=button-20;status=0;setChanged();return true;
        }
        if(button==3&&active()){
            if(owner!=null&&!owner.equals(player.getUUID())&&!player.hasPermissions(2))return refuse(7);
            var refund=drainReserved();if(paidRaw)items.set(0,ItemStack.EMPTY);reset();setChanged();sync();
            refund.forEach(stack->player.getInventory().placeItemBackInInventory(stack));return true;
        }
        if(button==2&&!active()){
            player.openMenu(new SimpleMenuProvider((id,inv,p)->new CopperInfusionMenu(id,inv,ContainerLevelAccess.create(level,worldPosition)),net.minecraft.network.chat.Component.translatable("screen.myvillage.infusion.title")));return true;
        }
        if(button!=0&&button!=1||active())return false;
        if(!items.get(2).isEmpty()||!items.get(3).isEmpty())return refuse(2);
        if(!items.get(0).isEmpty()){
            if(!WorkshopItems.valid(items.get(0)))return refuse(3);
            recipe=WorkshopRules.get().crafts().indexOf(WorkshopRules.get().craft(items.get(0).get(ModDataComponents.WORKPIECE.get()).recipe()));
        }
        var craft=selected();if(craft.requires_pattern()&&!ForgingKnowledge.knows(player,craft.recipe()))return refuse(4);
        boolean raw=items.get(0).isEmpty();if(button==0&&!raw)return refuse(1);
        var blank=raw?WorkshopItems.blank(new Workpiece(craft.recipe(),0,0,craft.armor()?2:0)):items.get(0).copy();
        var piece=blank.get(ModDataComponents.WORKPIECE.get());
        Map<String,Integer> costs=button==0?WorkshopRules.get().blankCosts(ForgingRules.get().recipe(craft.recipe()).orElseThrow()):automaticCosts(piece,raw);
        int quality=ForgingCompletion.quality(player);ItemStack result=button==1?standard(blank,quality):blank;
        var source=SpiritStoneSources.open(player);var payment=ForgingCosts.reserve(source.copies(),costs);
        if(payment.isEmpty()||!source.commit(payment.get().remaining()))return refuse(5);
        if(button==0){items.set(0,blank);status=6;setChanged();sync();return true;}
        items.set(0,blank);snapshot=blank.copy();frozen=result;reserved=new ArrayList<>(payment.get().consumed());owner=player.getUUID();paidRaw=raw;invalid=false;
        this.quality=quality;progress=0;duration=Math.min(30000,Math.max(20,(craft.forming_points()-piece.points())*FoundryRules.get().automatic_ticks_per_point()
                +WorkshopRules.get().operation_ticks().values().stream().mapToInt(Integer::intValue).sum()));status=8;setChanged();sync();return true;
    }
    private List<ItemStack> drainReserved(){var actual=reserved;reserved=new ArrayList<>();return actual;}
    private void reset(){snapshot=frozen=ItemStack.EMPTY;owner=null;invalid=false;paidRaw=false;progress=duration=0;status=0;}
    @Override public void serverTick(){
        if(!active())return;
        if(invalid||!ItemStack.matches(snapshot,items.get(0))){invalid=true;status=9;return;}
        if(!items.get(2).isEmpty()||!items.get(3).isEmpty()){status=2;return;}
        progress++;status=8;setChanged();
        if(progress>=duration){
            boolean bucket=automaticCosts(snapshot.get(ModDataComponents.WORKPIECE.get()),paidRaw).containsKey("minecraft:water_bucket");
            items.set(0,ItemStack.EMPTY);items.set(2,frozen);if(bucket)items.set(3,new ItemStack(Items.BUCKET));
            reserved=new ArrayList<>();reset();status=10;setChanged();sync();
            level.playSound(null,worldPosition,SoundEvents.ANVIL_USE,SoundSource.BLOCKS,.6f,1.2f);
        }
        var state=getBlockState();boolean lit=active()&&!invalid;
        if(state.getValue(ForgingTableBlock.LIT)!=lit)level.setBlock(worldPosition,state.setValue(ForgingTableBlock.LIT,lit),3);
        if(level.getGameTime()%20==0)sync();
    }
    @Override public void dropContents(){
        if(level==null||level.isClientSide())return;
        var refund=drainReserved();if(paidRaw&&active())items.set(0,ItemStack.EMPTY);
        reset();super.dropContents();for(var stack:refund)Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack);
    }
    @Override public void clearContent(){reserved=new ArrayList<>();reset();super.clearContent();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);if(owner!=null)tag.putUUID("Owner",owner);tag.putBoolean("PaidRaw",paidRaw);tag.putBoolean("Invalid",invalid);
        if(!snapshot.isEmpty())tag.put("Snapshot",snapshot.save(registries));if(!frozen.isEmpty())tag.put("Frozen",frozen.save(registries));
        var escrow=new ListTag();for(var item:reserved)if(!item.isEmpty())escrow.add(item.save(registries));tag.put("Reserved",escrow);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;paidRaw=tag.getBoolean("PaidRaw");invalid=tag.getBoolean("Invalid");
        snapshot=ItemStack.parseOptional(registries,tag.getCompound("Snapshot"));frozen=ItemStack.parseOptional(registries,tag.getCompound("Frozen"));
        reserved=new ArrayList<>();for(var entry:tag.getList("Reserved",Tag.TAG_COMPOUND)){var item=ItemStack.parseOptional(registries,(CompoundTag)entry);if(!item.isEmpty())reserved.add(item);}
        if(active()||!snapshot.isEmpty()){
            try{
                var expected=standard(snapshot,quality);var costs=automaticCosts(snapshot.get(ModDataComponents.WORKPIECE.get()),paidRaw);
                invalid=invalid||owner==null||duration<1||duration>30000||progress<0||progress>=duration||!ItemStack.matches(snapshot,items.get(0))
                        ||!ItemStack.matches(expected,frozen)||ForgingCosts.plan(reserved,costs).filter(rest->rest.stream().allMatch(ItemStack::isEmpty)).isEmpty();
            }catch(RuntimeException e){invalid=true;}
            if(invalid){frozen=ItemStack.EMPTY;status=9;}
        }
    }
}
