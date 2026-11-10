package com.example.myvillage.forging;

import com.example.myvillage.block.ModBlockEntities;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class AssemblyBlockEntity extends FoundryBlockEntity {
    private ItemStack snapshot=ItemStack.EMPTY,material=ItemStack.EMPTY,frozen=ItemStack.EMPTY,returned=ItemStack.EMPTY;
    private UUID owner;
    private boolean invalid;
    public AssemblyBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.ASSEMBLY_TABLE.get(),pos,state);}
    @Override public FoundryMenu.Kind kind(){return FoundryMenu.Kind.ASSEMBLY;}
    @Override public boolean active(){return owner!=null||invalid;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return !active()&&(slot==0&&(WorkshopItems.valid(stack)||WorkshopEquipment.craft(stack).isPresent())
            ||slot==1&&(stack.getItem() instanceof FoundryMaterialItem p&&!p.flame()||ForgingIngredients.matches(FoundryRules.get().assembly().armor_material(),stack)));}
    @Override public boolean action(ServerPlayer player,FoundryMenu menu,int button){
        if(!actor(player,menu))return false;
        if(button==1&&active()&&(owner==null||owner.equals(player.getUUID())||player.hasPermissions(2))){reset();setChanged();return true;}
        if(button!=0||active())return false;
        if(!items.get(2).isEmpty()||!items.get(3).isEmpty())return refuse(5);
        var check=AssemblyPlan.prepare(items.get(0),items.get(1),1);
        if(!check.valid())return refuse(check.reason());
        boolean newItem=WorkshopItems.valid(items.get(0));
        int outcome=newItem?ForgingCompletion.roll(player,items.get(0).get(com.example.myvillage.item.ModDataComponents.WORKPIECE.get()).recipe()):items.get(0).get(com.example.myvillage.item.ModDataComponents.FORGE_MARK.get()).quality();
        var plan=AssemblyPlan.prepare(items.get(0),items.get(1),outcome);
        snapshot=items.get(0).copy();material=items.get(1).copy();frozen=plan.result();returned=plan.returned();
        quality=outcome;owner=player.getUUID();progress=0;duration=FoundryRules.get().assembly().ticks();status=6;invalid=false;setChanged();return true;
    }
    private void reset(){snapshot=material=frozen=returned=ItemStack.EMPTY;invalid=false;owner=null;progress=duration=0;status=0;}
    @Override public void serverTick(){
        if(!active())return;
        if(invalid||!ItemStack.matches(snapshot,items.get(0))||!ItemStack.matches(material,items.get(1))){invalid=true;status=7;return;}
        if(!items.get(2).isEmpty()||!items.get(3).isEmpty()){status=5;return;}
        progress++;setChanged();if(progress<duration)return;
        items.set(0,ItemStack.EMPTY);items.get(1).shrink(1);items.set(2,frozen);items.set(3,returned);
        var actor=owner==null?null:((net.minecraft.server.level.ServerLevel)level).getServer().getPlayerList().getPlayer(owner);
        if(!frozen.isEmpty()&&com.example.myvillage.aptitude.AptitudeRuntime.nearStation(actor,level,worldPosition))com.example.myvillage.aptitude.AptitudeRuntime.equipmentCompleted(actor,frozen,false);
        if(quality==0&&actor!=null)actor.displayClientMessage(net.minecraft.network.chat.Component.literal("炼器失败，材料已消耗。"),false);
        reset();status=8;setChanged();sync();level.playSound(null,worldPosition,SoundEvents.SMITHING_TABLE_USE,SoundSource.BLOCKS,.7f,1);
    }
    @Override public void clearContent(){reset();super.clearContent();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.putInt("OutcomeVersion",1);if(owner!=null)tag.putUUID("Owner",owner);tag.putBoolean("Invalid",invalid);
        for(var entry:java.util.Map.of("Snapshot",snapshot,"Material",material,"Frozen",frozen,"Returned",returned).entrySet())
            if(!entry.getValue().isEmpty())tag.put(entry.getKey(),entry.getValue().save(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
        snapshot=ItemStack.parseOptional(registries,tag.getCompound("Snapshot"));material=ItemStack.parseOptional(registries,tag.getCompound("Material"));
        frozen=ItemStack.parseOptional(registries,tag.getCompound("Frozen"));returned=ItemStack.parseOptional(registries,tag.getCompound("Returned"));invalid=tag.getBoolean("Invalid");
        if(owner!=null||!frozen.isEmpty()||!snapshot.isEmpty()||invalid){
            var plan=AssemblyPlan.prepare(snapshot,material,quality);
            invalid=invalid||tag.getInt("OutcomeVersion")!=1||!tag.contains("Quality",net.minecraft.nbt.Tag.TAG_INT)||quality<0||quality>4||owner==null||duration!=FoundryRules.get().assembly().ticks()||progress<0||progress>=duration
                    ||!ItemStack.matches(snapshot,items.get(0))||!ItemStack.matches(material,items.get(1))||!plan.valid()
                    ||!ItemStack.matches(plan.result(),frozen)||!ItemStack.matches(plan.returned(),returned);
            if(invalid){frozen=returned=ItemStack.EMPTY;status=7;}
        }
    }
}
