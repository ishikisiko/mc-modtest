package com.example.myvillage.forging;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Dimension-local provenance. No eviction: replacing a block or restarting cannot mint experience. */
public final class WorkshopHarvestLedger extends SavedData {
    private final LongOpenHashSet worked=new LongOpenHashSet();
    private final LongOpenHashSet placedLogs=new LongOpenHashSet();
    public WorkshopHarvestLedger(){}
    static WorkshopHarvestLedger load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new WorkshopHarvestLedger();data.worked.addAll(new LongOpenHashSet(tag.getLongArray("worked")));
        data.placedLogs.addAll(new LongOpenHashSet(tag.getLongArray("placed_logs")));return data;
    }
    public static WorkshopHarvestLedger get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(new Factory<>(WorkshopHarvestLedger::new,WorkshopHarvestLedger::load),"yuanxian_workshop_harvest");
    }
    public boolean claim(BlockPos pos){if(!worked.add(pos.asLong()))return false;setDirty();return true;}
    boolean playerPlacedLog(BlockPos pos){return placedLogs.contains(pos.asLong());}
    void markPlacedLog(BlockPos pos){if(placedLogs.add(pos.asLong()))setDirty();}
    void forgetPlacedLog(BlockPos pos){if(placedLogs.remove(pos.asLong()))setDirty();}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        tag.putLongArray("worked",worked.toLongArray());
        tag.putLongArray("placed_logs",placedLogs.toLongArray());
        return tag;
    }
}
