package com.example.myvillage.estate;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World property titles are separate from the WorldSim population ledger. */
public final class EstateSavedData extends SavedData {
    public final EstateTitles titles = new EstateTitles();
    public static EstateSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(EstateSavedData::new,
                EstateSavedData::load), "myvillage_estates");
    }
    static EstateSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != 1) throw new IllegalArgumentException("房契格式无效");
        var result = new EstateSavedData();
        var list = tag.getList("titles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var row = list.getCompound(i);
            result.titles.restore(row.getString("id"), new EstateTitles.Title(row.getUUID("owner"), row.getString("name")));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        titles.snapshot().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e -> {
            var row = new CompoundTag(); row.putString("id", e.getKey()); row.putUUID("owner", e.getValue().owner());
            row.putString("name", e.getValue().name()); list.add(row);
        });
        tag.putInt("format", 1); tag.put("titles", list); return tag;
    }
}
