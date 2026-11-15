package com.example.myvillage.resources;

import com.example.myvillage.MyVillageMod;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.Nullable;

/** 提供当前资源的标准逻辑路径，保持原版模型烘焙、结构读取和覆盖顺序。 */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class SharedResourcePack extends AbstractPackResources {
    private final PackedResourceStore store;
    private final PackType type;
    public SharedResourcePack(PackLocationInfo location,PackType type,PackedResourceStore store){super(location);this.type=type;this.store=store;}
    private static SharedResourcePack create(PackLocationInfo location,PackType type){
        try{return new SharedResourcePack(location,type,PackedResourceStore.classpath());}
        catch(IOException error){throw new UncheckedIOException("无法打开当前共享构件资源",error);}
    }
    @SubscribeEvent public static void find(AddPackFindersEvent event){
        PackType type=event.getPackType();
        event.addRepositorySource(consumer->{
            var location=new PackLocationInfo("yuanxian/shared_resources",Component.literal("修仙大陆共享构件资源"),PackSource.BUILT_IN,Optional.empty());
            Pack pack=Pack.readMetaAndCreate(location,new Pack.ResourcesSupplier(){
                @Override public PackResources openPrimary(PackLocationInfo info){return create(info,type);}
                @Override public PackResources openFull(PackLocationInfo info,Pack.Metadata metadata){return create(info,type);}
            },type,new PackSelectionConfig(true,Pack.Position.BOTTOM,true));
            if(pack==null)throw new IllegalStateException("共享构件资源包元数据错误");
            consumer.accept(pack.hidden());
        });
    }
    @Override public @Nullable IoSupplier<InputStream> getRootResource(String... path){
        if(path.length!=1||!path[0].equals("pack.mcmeta"))return null;
        String text="{\"pack\":{\"pack_format\":"+SharedConstants.getCurrentVersion().getPackVersion(type)+",\"description\":\"修仙大陆共享构件资源\"}}";
        byte[] value=text.getBytes(StandardCharsets.UTF_8);return()->new ByteArrayInputStream(value);
    }
    @Override public @Nullable IoSupplier<InputStream> getResource(PackType requested,ResourceLocation name){
        if(requested!=type)return null;
        String path=type.getDirectory()+"/"+name.getNamespace()+"/"+name.getPath();
        return store.contains(path)?()->store.open(path):null;
    }
    @Override public void listResources(PackType requested,String namespace,String prefix,ResourceOutput output){
        if(requested!=type)return;
        String root=type.getDirectory()+"/"+namespace+"/",start=prefix.isEmpty()?root:root+prefix+"/";
        for(String path:store.entries().keySet())if(path.startsWith(start)){
            var id=ResourceLocation.fromNamespaceAndPath(namespace,path.substring(root.length()));output.accept(id,()->store.open(path));
        }
    }
    @Override public Set<String> getNamespaces(PackType requested){return requested==type?Set.of("yuanxian"):Set.of();}
    @Override public boolean isHidden(){return true;}
    @Override public void close(){store.close();}
}
