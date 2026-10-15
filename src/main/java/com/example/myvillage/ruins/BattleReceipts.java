package com.example.myvillage.ruins;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Global receipts survive an unloaded cache, dimension changes and normal save/restart. */
public final class BattleReceipts extends SavedData {
    private final Map<UUID,String> receipts=new HashMap<>();
    public static BattleReceipts get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(
        new Factory<>(BattleReceipts::new,BattleReceipts::load),"myvillage_battle_receipts");}
    private static BattleReceipts load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new BattleReceipts();
        for(var t:tag.getList("Guards",Tag.TAG_COMPOUND)) { var v=(CompoundTag)t;
            if(v.hasUUID("Id") && Set.of("alive","dead","retry").contains(v.getString("Status"))) data.receipts.put(v.getUUID("Id"),v.getString("Status")); }
        return data;
    }
    public void register(UUID id){receipts.put(id,"alive");setDirty();}
    public String status(UUID id){return receipts.getOrDefault(id,"unknown");}
    public void forget(UUID id){if(receipts.remove(id)!=null)setDirty();}
    public static void death(LivingDeathEvent event){
        if(event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel server))return;
        var data=get(server); UUID id=event.getEntity().getUUID();
        if(data.receipts.containsKey(id)){data.receipts.put(id,"dead");data.setDirty();}
    }
    public static void leave(EntityLeaveLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel server) || server.getDifficulty()!=Difficulty.PEACEFUL
            || event.getEntity().getRemovalReason()!=Entity.RemovalReason.DISCARDED)return;
        var data=get(server); UUID id=event.getEntity().getUUID();
        if("alive".equals(data.receipts.get(id))){data.receipts.put(id,"retry");data.setDirty();}
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var entries=new ListTag(); receipts.forEach((id,status)->{var v=new CompoundTag();v.putUUID("Id",id);v.putString("Status",status);entries.add(v);});
        tag.put("Guards",entries);return tag;
    }
}
