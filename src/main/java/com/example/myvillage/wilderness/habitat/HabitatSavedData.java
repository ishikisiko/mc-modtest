package com.example.myvillage.wilderness.habitat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class HabitatSavedData extends SavedData {
    public final HabitatState state;
    public HabitatSavedData(){state=new HabitatState();}
    private HabitatSavedData(CompoundTag tag){state=HabitatState.decode(tag.getString("payload"));}
    public static HabitatSavedData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(HabitatSavedData::new,(tag,registry)->new HabitatSavedData(tag)),"yuanxian_east_habitats");}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registry){tag.putString("payload",state.encode());return tag;}
}
