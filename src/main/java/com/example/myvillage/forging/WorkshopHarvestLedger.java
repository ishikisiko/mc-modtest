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
    public WorkshopHarvestLedger(){}
    static WorkshopHarvestLedger load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new WorkshopHarvestLedger();data.worked.addAll(new LongOpenHashSet(tag.getLongArray("worked")));return data;
    }
    public static WorkshopHarvestLedger get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(new Factory<>(WorkshopHarvestLedger::new,WorkshopHarvestLedger::load),"myvillage_workshop_harvest");
    }
    public boolean claim(BlockPos pos){if(!worked.add(pos.asLong()))return false;setDirty();return true;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){tag.putLongArray("worked",worked.toLongArray());return tag;}
}
