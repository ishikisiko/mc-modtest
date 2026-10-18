package com.example.myvillage.client.atlas;

import com.example.myvillage.worldplan.atlas.AtlasInkPainter;
import com.example.myvillage.worldplan.atlas.WorldAtlas;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Render-thread texture lifetime; CPU-only painting happens on a background worker. */
public final class AtlasTextures {
    public record Textures(ResourceLocation paper,ResourceLocation map,List<ResourceLocation> names,ResourceLocation scale) {}
    private record Paint(BufferedImage paper,BufferedImage map,List<BufferedImage> names,BufferedImage scale) {}
    private static CompletableFuture<Paint> painting;
    private static Textures textures;
    private static String key="";
    private static boolean failed;
    private AtlasTextures() {}
    public static Textures get(WorldAtlas atlas,boolean chinese){
        String wanted=atlas.fingerprint()+":"+chinese;
        if(!key.equals(wanted)){
            clear();key=wanted;
            painting=CompletableFuture.supplyAsync(()->{
                var painter=new AtlasInkPainter();var names=new ArrayList<BufferedImage>();
                names.add(painter.label(chinese?"外域":"Outer lands"));
                for(var r:atlas.regions())names.add(painter.label(r.name()));
                names.add(painter.label(chinese?"异界":"Other dimension"));
                return new Paint(painter.paper(chinese),painter.map(atlas),List.copyOf(names),painter.scaleLabel(atlas.radius(),chinese));
            });
        }
        if(textures==null&&!failed&&painting.isDone()){
            try{Paint p=painting.join();var names=new ArrayList<ResourceLocation>();
                // Own every uploaded texture immediately, even if a later upload fails.
                List<ResourceLocation> uploaded=new ArrayList<>();
                try{var paper=upload("paper",p.paper());uploaded.add(paper);var map=upload("map",p.map());uploaded.add(map);
                    for(int i=0;i<p.names().size();i++){var name=upload("name_"+i,p.names().get(i));uploaded.add(name);names.add(name);}
                    var scale=upload("scale",p.scale());uploaded.add(scale);
                    textures=new Textures(paper,map,List.copyOf(names),scale);
                    org.slf4j.LoggerFactory.getLogger(AtlasTextures.class).info("ATLAS_READY plan={} regions={}",atlas.fingerprint(),atlas.regions().size());
                }catch(RuntimeException e){for(var location:uploaded)Minecraft.getInstance().getTextureManager().release(location);throw e;}
            }catch(RuntimeException e){failed=true;org.slf4j.LoggerFactory.getLogger(AtlasTextures.class).error("Atlas painting failed",e);}
        }return textures;
    }
    public static boolean failed(){return failed;}
    public static void clear(){
        if(textures!=null){var manager=Minecraft.getInstance().getTextureManager();manager.release(textures.paper());manager.release(textures.map());manager.release(textures.scale());textures.names().forEach(manager::release);}
        if(painting!=null)painting.cancel(true);
        textures=null;painting=null;key="";failed=false;
    }
    private static ResourceLocation upload(String name,BufferedImage source){
        NativeImage image=new NativeImage(source.getWidth(),source.getHeight(),false);
        for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++){
            int c=source.getRGB(x,y);image.setPixelRGBA(x,y,(c&0xff00ff00)|((c>>>16)&255)|((c&255)<<16));
        }
        ResourceLocation id=ResourceLocation.fromNamespaceAndPath("myvillage","atlas/dynamic/"+name);
        DynamicTexture texture=new DynamicTexture(image);texture.setFilter(true,false);
        Minecraft.getInstance().getTextureManager().register(id,texture);return id;
    }
}
