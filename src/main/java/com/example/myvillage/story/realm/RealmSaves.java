package com.example.myvillage.story.realm;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

/** Durable directory of private dimensions. A missing/corrupt record never resets an existing realm. */
public final class RealmSaves extends SavedData {
    private static final String NAME="myvillage_qianzhen_realms";
    public record Location(String dimension,double x,double y,double z,float yaw,float pitch) {
        public Location {
            if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    ||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>29_999_980||Math.abs(z)>29_999_980
                    ||y < -2048||y>2048||!Float.isFinite(yaw)||!Float.isFinite(pitch))throw new IllegalArgumentException("Invalid realm location");
        }
        CompoundTag save() {
            var t=new CompoundTag();t.putString("dimension",dimension);t.putDouble("x",x);t.putDouble("y",y);t.putDouble("z",z);
            t.putFloat("yaw",yaw);t.putFloat("pitch",pitch);return t;
        }
        static Location load(CompoundTag t) {
            if(!t.contains("dimension",Tag.TAG_STRING))throw new IllegalStateException("Missing realm dimension");
            for(var k:new String[]{"x","y","z"})if(!t.contains(k,Tag.TAG_DOUBLE))throw new IllegalStateException("Missing realm coordinate");
            for(var k:new String[]{"yaw","pitch"})if(!t.contains(k,Tag.TAG_FLOAT))throw new IllegalStateException("Missing realm rotation");
            return new Location(t.getString("dimension"),t.getDouble("x"),t.getDouble("y"),t.getDouble("z"),t.getFloat("yaw"),t.getFloat("pitch"));
        }
    }
    public record Entry(UUID owner,String region,Location outside,Location inside) {
        public Entry {
            com.example.myvillage.sim.life.LifePlace.requireId(region);
            if(owner==null||outside==null||RealmIdentity.reserved(outside.dimension())
                    ||inside!=null&&!inside.dimension().equals(RealmIdentity.dimension(owner)))throw new IllegalArgumentException("Invalid realm ownership/return");
        }
    }
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    public static RealmSaves get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(()->{
            if(Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("data/"+NAME+".dat")))throw new IllegalStateException("Unreadable private realm directory; restore backup");
            return new RealmSaves();
        },RealmSaves::load),NAME);
    }
    public Map<UUID,Entry> entries(){return Map.copyOf(entries);}
    public Entry get(UUID owner){return entries.get(owner);}
    public void put(Entry entry){entries.put(entry.owner(),entry);setDirty();}
    public static RealmSaves load(CompoundTag t,HolderLookup.Provider registries) {
        if(!t.contains("schema",Tag.TAG_INT)||t.getInt("schema")!=1||!t.contains("realms",Tag.TAG_LIST))throw new IllegalStateException("Invalid realm directory schema");
        var result=new RealmSaves();var list=t.getList("realms",Tag.TAG_COMPOUND);
        if(list.size()!=((ListTag)t.get("realms")).size())throw new IllegalStateException("Invalid realm records");
        for(var raw:list){var e=(CompoundTag)raw;
            if(!e.hasUUID("owner")||!e.contains("region",Tag.TAG_STRING)||!e.contains("outside",Tag.TAG_COMPOUND))throw new IllegalStateException("Incomplete realm record");
            var entry=new Entry(e.getUUID("owner"),e.getString("region"),Location.load(e.getCompound("outside")),
                    e.contains("inside")?Location.load(e.getCompound("inside")):null);
            if(result.entries.putIfAbsent(entry.owner(),entry)!=null)throw new IllegalStateException("Duplicate realm owner");
        }return result;
    }
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider registries){
        t.putInt("schema",1);var list=new ListTag();
        for(var entry:entries.values()){
            var e=new CompoundTag();e.putUUID("owner",entry.owner());e.putString("region",entry.region());e.put("outside",entry.outside().save());
            if(entry.inside()!=null)e.put("inside",entry.inside().save());list.add(e);
        }t.put("realms",list);return t;
    }
    public void checkpoint(MinecraftServer server){
        try {
            IOUtilities.waitUntilIOWorkerComplete();var folder=server.getWorldPath(LevelResource.ROOT).resolve("data");Files.createDirectories(folder);
            var root=new CompoundTag();root.put("data",save(new CompoundTag(),server.registryAccess()));NbtUtils.addCurrentDataVersion(root);
            IOUtilities.writeNbtCompressed(root,folder.resolve(NAME+".dat"));setDirty(false);
        }catch(IOException failure){throw new IllegalStateException("无法保存个人秘境归属；操作已停止",failure);}
    }
}
