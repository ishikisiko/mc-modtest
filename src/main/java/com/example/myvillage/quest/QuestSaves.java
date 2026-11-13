package com.example.myvillage.quest;

import java.nio.file.Files;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

/** Dedicated durable instance directory; unrelated personal realms are never touched. */
public final class QuestSaves extends SavedData {
    private static final String NAME="myvillage_expeditions";
    public final QuestState state;
    public QuestSaves(){state=new QuestState();}
    private QuestSaves(QuestState state){this.state=state;}
    public static QuestSaves get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(()->{
            if(Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("data/"+NAME+".dat")))throw new IllegalStateException("远征记录无法读取，请保留现场恢复备份");
            return new QuestSaves();
        },(tag,registries)->{
            if(!tag.contains("payload",Tag.TAG_STRING))throw new IllegalStateException("远征记录缺失");
            return new QuestSaves(QuestState.decode(tag.getString("payload")));
        }),NAME);
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){state.validate();tag.putString("payload",state.encode());return tag;}
    public void checkpoint(MinecraftServer server){
        try{
            IOUtilities.waitUntilIOWorkerComplete();var folder=server.getWorldPath(LevelResource.ROOT).resolve("data");Files.createDirectories(folder);
            var root=new CompoundTag();root.put("data",save(new CompoundTag(),server.registryAccess()));NbtUtils.addCurrentDataVersion(root);
            IOUtilities.writeNbtCompressed(root,folder.resolve(NAME+".dat"));setDirty(false);
        }catch(Exception error){throw new IllegalStateException("远征记录未能保存，已停止操作",error);}
    }
}
