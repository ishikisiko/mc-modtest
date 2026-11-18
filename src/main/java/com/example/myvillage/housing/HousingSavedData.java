package com.example.myvillage.housing;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** 房契与施工进度一起保存；支付只发生在状态开始时。 */
public final class HousingSavedData extends SavedData {
    public static final class Title {
        public final UUID owner;public final String ownerName;
        public String house="",status="empty",error="";public int turn,offsetX,offsetZ,cursor;
        public Title(UUID owner,String name){this.owner=owner;ownerName=name;}
    }
    public final Map<String,Title> titles=new LinkedHashMap<>();
    public final Map<String,Set<Long>> edits=new LinkedHashMap<>();
    public static HousingSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(HousingSavedData::new,HousingSavedData::load),"yuanxian_housing");}
    static HousingSavedData load(CompoundTag root,HolderLookup.Provider registries){
        if(root.getInt("format")!=1)throw new IllegalArgumentException("宅地档案格式错误");
        var data=new HousingSavedData();var list=root.getList("titles",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            var row=list.getCompound(i);var title=new Title(row.getUUID("owner"),row.getString("owner_name"));
            title.house=row.getString("house");title.status=row.getString("status");title.error=row.getString("error");title.turn=row.getInt("turn");
            title.offsetX=row.getInt("offset_x");title.offsetZ=row.getInt("offset_z");title.cursor=row.getInt("cursor");
            if(!Set.of("empty","building","built","blocked").contains(title.status)||title.turn<0||title.turn>3||title.cursor<0||title.offsetX<0||title.offsetZ<0)throw new IllegalArgumentException("施工档案状态错误");
            if(data.titles.put(row.getString("id"),title)!=null)throw new IllegalArgumentException("重复宅地房契");
        }
        var changes=root.getList("edits",Tag.TAG_COMPOUND);
        for(int i=0;i<changes.size();i++){
            var row=changes.getCompound(i);Set<Long> positions=new HashSet<>();for(long p:row.getLongArray("positions"))positions.add(p);
            data.edits.put(row.getString("id"),positions);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("format",1);var list=new ListTag();
        titles.forEach((id,t)->{var row=new CompoundTag();row.putString("id",id);row.putUUID("owner",t.owner);row.putString("owner_name",t.ownerName);
            row.putString("house",t.house);row.putString("status",t.status);row.putString("error",t.error);row.putInt("turn",t.turn);
            row.putInt("offset_x",t.offsetX);row.putInt("offset_z",t.offsetZ);row.putInt("cursor",t.cursor);list.add(row);});
        root.put("titles",list);var changes=new ListTag();
        edits.forEach((id,positions)->{var row=new CompoundTag();row.putString("id",id);row.putLongArray("positions",positions.stream().mapToLong(Long::longValue).sorted().toArray());changes.add(row);});
        root.put("edits",changes);return root;
    }
}
