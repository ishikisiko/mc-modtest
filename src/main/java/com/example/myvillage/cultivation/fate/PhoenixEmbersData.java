package com.example.myvillage.cultivation.fate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.saveddata.SavedData;

/** 仅保存进行中的余烬及其完整玩家快照；原玩家文件仍由 PlayerList 正常保存。 */
public final class PhoenixEmbersData extends SavedData {
    public static final class Ember {
        final UUID owner;
        final String name;
        String dimension;
        PhoenixEmberClock clock;
        CompoundTag snapshot;
        String causeType;
        UUID attacker;
        transient ServerPlayer player;
        transient DamageSource source;
        transient boolean offline,dead;
        transient long movementTick=Long.MIN_VALUE;
        transient double movementThisTick;
        transient net.minecraft.world.level.ChunkPos ticket;
        Ember(UUID owner,String name,String dimension,PhoenixEmberClock clock,CompoundTag snapshot,String causeType,UUID attacker) {
            this.owner=owner;this.name=name;this.dimension=dimension;this.clock=clock;this.snapshot=snapshot.copy();
            this.causeType=causeType;this.attacker=attacker;
        }
        void capture() {
            if(player!=null){snapshot=player.saveWithoutId(new CompoundTag());dimension=player.level().dimension().location().toString();}
        }
    }
    final Map<UUID,Ember> embers=new HashMap<>();
    public static PhoenixEmbersData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PhoenixEmbersData::new,PhoenixEmbersData::load),"myvillage_phoenix_embers");
    }
    public static PhoenixEmbersData load(CompoundTag tag,HolderLookup.Provider registries) {
        if(tag.getInt("schema")!=1)throw new IllegalStateException("无法读取凤凰余烬格式");
        var data=new PhoenixEmbersData();var stored=tag.getCompound("embers");
        if(stored.getAllKeys().size()>SpiritBodyRules.get().phoenix().maximumOfflineEmbers())throw new IllegalStateException("保存的凤凰余烬超出容量");
        for(String key:stored.getAllKeys()) {
            var n=stored.getCompound(key);UUID id=UUID.fromString(key);
            if(!n.contains("player",Tag.TAG_COMPOUND)||!n.hasUUID("owner")||!id.equals(n.getUUID("owner")))throw new IllegalStateException("凤凰玩家快照损坏");
            var clock=new PhoenixEmberClock(n.getDouble("maximum_health"),n.getDouble("maximum_durability"),n.getDouble("durability"),
                    n.getInt("duration"),n.getInt("remaining"),n.getInt("protection"),n.getDouble("movement"));
            var ember=new Ember(id,n.getString("name"),n.getString("dimension"),clock,n.getCompound("player"),n.getString("cause"),n.hasUUID("attacker")?n.getUUID("attacker"):null);
            ember.offline=true;data.embers.put(id,ember);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("schema",1);var stored=new CompoundTag();
        embers.forEach((id,e)->{
            e.capture();var n=new CompoundTag();n.putUUID("owner",id);n.putString("name",e.name);n.putString("dimension",e.dimension);
            n.putDouble("maximum_health",e.clock.maximumHealth());n.putDouble("maximum_durability",e.clock.maximumDurability());
            n.putDouble("durability",e.clock.durability());n.putInt("duration",e.clock.duration());n.putInt("remaining",e.clock.remaining());
            n.putInt("protection",e.clock.protectionRemaining());n.putDouble("movement",e.clock.movementUsed());
            n.put("player",e.snapshot.copy());n.putString("cause",e.causeType);if(e.attacker!=null)n.putUUID("attacker",e.attacker);
            stored.put(id.toString(),n);
        });tag.put("embers",stored);return tag;
    }
}
