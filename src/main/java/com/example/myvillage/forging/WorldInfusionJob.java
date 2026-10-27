package com.example.myvillage.forging;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** One real workpiece and exact escrow. The prospective result is never a second owned item. */
public final class WorldInfusionJob {
    private ItemStack input=ItemStack.EMPTY,output=ItemStack.EMPTY,frozen=ItemStack.EMPTY;
    private List<ItemStack> reserved=new ArrayList<>();
    private UUID owner;
    private int elapsed,duration,quality;
    private String trait="";
    private boolean invalid;
    public boolean occupied(){return !input.isEmpty()||!output.isEmpty()||!reserved.isEmpty();}
    public boolean running(){return occupied()&&!invalid&&!input.isEmpty()&&!frozen.isEmpty();}
    public boolean invalid(){return invalid;}
    public UUID owner(){return owner;}
    public int elapsed(){return elapsed;}
    public int duration(){return duration;}
    public ItemStack display(){return (!output.isEmpty()?output:input).copy();}
    public ItemStack input(){return input.copy();}
    public ItemStack output(){return output.copy();}
    public List<ItemStack> escrow(){return reserved.stream().map(ItemStack::copy).toList();}
    public void begin(UUID owner,ItemStack input,EquipmentInfusion.Preparation prepared,List<ItemStack> escrow,String trait,int quality,int duration){
        if(occupied()||owner==null||!prepared.valid()||duration<1||duration>30000||!escrowMatches(escrow,prepared.costs()))
            throw new IllegalArgumentException("Invalid infusion reservation");
        var verified=EquipmentInfusion.prepare(input,trait,quality);
        if(!verified.valid()||!ItemStack.matches(verified.result(),prepared.result()))throw new IllegalArgumentException("Infusion snapshot mismatch");
        this.owner=owner;this.input=input.copy();this.frozen=prepared.result().copy();this.reserved=new ArrayList<>(escrow.stream().map(ItemStack::copy).toList());
        this.trait=trait;this.quality=quality;this.duration=duration;elapsed=0;invalid=false;
    }
    public boolean tick(){
        if(!running())return false;
        elapsed++;
        if(elapsed<duration)return false;
        output=frozen;frozen=ItemStack.EMPTY;input=ItemStack.EMPTY;reserved.clear();return true;
    }
    /** Take or break drains ownership before items are handed to the world/player. */
    public List<ItemStack> drain(){
        List<ItemStack> actual=new ArrayList<>();
        if(!input.isEmpty())actual.add(input.copy());if(!output.isEmpty())actual.add(output.copy());
        reserved.forEach(s->actual.add(s.copy()));clear();return actual;
    }
    private void clear(){input=output=frozen=ItemStack.EMPTY;reserved=new ArrayList<>();owner=null;elapsed=duration=quality=0;trait="";invalid=false;}
    private static boolean escrowMatches(List<ItemStack> escrow,Map<String,Integer> costs){
        return ForgingCosts.plan(escrow,costs).filter(rest->rest.stream().allMatch(ItemStack::isEmpty)).isPresent();
    }
    public CompoundTag save(HolderLookup.Provider registries){
        var tag=new CompoundTag();tag.putInt("Version",1);
        if(owner!=null)tag.putUUID("Owner",owner);
        if(!input.isEmpty())tag.put("Input",input.save(registries));if(!output.isEmpty())tag.put("Output",output.save(registries));
        if(!frozen.isEmpty())tag.put("Frozen",frozen.save(registries));
        var list=new ListTag();reserved.forEach(s->list.add(s.save(registries)));tag.put("Reserved",list);
        tag.putInt("Elapsed",elapsed);tag.putInt("Duration",duration);tag.putInt("Quality",quality);tag.putString("Trait",trait);tag.putBoolean("Invalid",invalid);return tag;
    }
    public void load(CompoundTag tag,HolderLookup.Provider registries){
        clear();input=ItemStack.parseOptional(registries,tag.getCompound("Input"));output=ItemStack.parseOptional(registries,tag.getCompound("Output"));
        frozen=ItemStack.parseOptional(registries,tag.getCompound("Frozen"));
        for(var row:tag.getList("Reserved",Tag.TAG_COMPOUND)){var s=ItemStack.parseOptional(registries,(CompoundTag)row);if(!s.isEmpty())reserved.add(s);}
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;elapsed=tag.getInt("Elapsed");duration=tag.getInt("Duration");quality=tag.getInt("Quality");trait=tag.getString("Trait");
        if(!occupied()){clear();return;}
        invalid=tag.getBoolean("Invalid")||tag.getInt("Version")!=1||owner==null;
        if(!input.isEmpty()||!reserved.isEmpty()||!frozen.isEmpty()){
            var prepared=EquipmentInfusion.prepare(input,trait,quality);
            invalid|=!output.isEmpty()||duration<1||duration>30000||elapsed<0||elapsed>=duration||!prepared.valid()
                    ||!ItemStack.matches(prepared.result(),frozen)||!escrowMatches(reserved,prepared.costs());
        }
        if(invalid)frozen=ItemStack.EMPTY; // Keep real input/escrow available for refund, never synthesize a result.
    }
}
