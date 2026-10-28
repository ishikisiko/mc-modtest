package com.example.myvillage.client.immortal;

import com.example.myvillage.equipment.immortal.ImmortalEquipmentCatalogue;
import com.example.myvillage.equipment.immortal.ImmortalMeshData;
import com.example.myvillage.equipment.immortal.ImmortalMeshData.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Build a complete replacement, then publish once. A failed reload retains the previous snapshot. */
public final class ImmortalMeshResources implements ResourceManagerReloadListener {
    public static final ImmortalMeshResources INSTANCE = new ImmortalMeshResources();
    record Surface(RenderType solid, RenderType solidDouble, RenderType translucent, RenderType glow) { }
    record Fit(float sx,float sy,float sz,float tx,float ty,float tz) {
        Vec3 point(Vec3 p) {return new Vec3(p.x()*sx+tx,p.y()*sy+ty,p.z()*sz+tz);}
        Vec3 vector(Vec3 p) {return new Vec3(p.x()*sx,p.y()*sy,p.z()*sz);}
    }
    record Loaded(ImmortalMeshData data, Map<String,Surface> surfaces, Map<String,Fit> slimFits,
            Map<Part,Vec3> centers, Map<Part,Part> armMeshes) {
        Loaded(ImmortalMeshData data,Map<String,Surface> surfaces,Map<String,Fit> slimFits,Map<Part,Vec3> centers) {
            this(data,surfaces,slimFits,centers,ImmortalArmTessellation.parts(data));
        }
        Vec3 local(Profile profile,String bone,Vec3 author) {
            Vec3 origin=data.profiles().get("standard").boneOrigins().get(bone);
            Vec3 local=new Vec3(author.x()-origin.x(),24-author.y()-origin.y(),-author.z()-origin.z());
            return profile==data.profiles().get("slim")?slimFits.get(bone).point(local):local;
        }
        Vec3 vector(Profile profile,String bone,Vec3 author) {
            Vec3 local=new Vec3(author.x(),-author.y(),-author.z());
            return profile==data.profiles().get("slim")?slimFits.get(bone).vector(local):local;
        }
    }
    /** The exporter preserves vertex correspondence and applies an affine arm fit. Derive that
     * fit from the actual meshes, so Java never duplicates rig.json's tuning constants. */
    private static Map<String,Fit> fits(ImmortalMeshData data) {
        Map<String,Fit> result=new HashMap<>();
        var standard=data.profiles().get("standard");var slim=data.profiles().get("slim");
        Map<String,Part> slimParts=new HashMap<>();for(Part p:slim.parts())slimParts.put(p.id(),p);
        for(String bone:standard.boneOrigins().keySet()) {
            double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
            double[] hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            double[] targetLo=lo.clone(),targetHi=hi.clone();
            for(Part part:standard.parts())if(part.bone().equals(bone)) {
                Part target=slimParts.get(part.id());
                if(target==null||!target.bone().equals(bone)||!target.slot().equals(part.slot())||target.vertices().size()!=part.vertices().size())
                    throw new IllegalArgumentException("双身形组件无法对应："+part.id());
                for(int i=0;i<part.vertices().size();i++) {
                    Vertex a=part.vertices().get(i),b=target.vertices().get(i);
                    float[] av={a.x(),a.y(),a.z()},bv={b.x(),b.y(),b.z()};
                    for(int j=0;j<3;j++){lo[j]=Math.min(lo[j],av[j]);hi[j]=Math.max(hi[j],av[j]);targetLo[j]=Math.min(targetLo[j],bv[j]);targetHi[j]=Math.max(targetHi[j],bv[j]);}
                }
            }
            float[] scale={1,1,1},offset={0,0,0};
            for(int j=0;j<3;j++)if(Double.isFinite(lo[j])) {
                scale[j]=hi[j]-lo[j]>1e-6?(float)((targetHi[j]-targetLo[j])/(hi[j]-lo[j])):1;
                offset[j]=(float)(targetLo[j]-lo[j]*scale[j]);
            }
            Fit fit=new Fit(scale[0],scale[1],scale[2],offset[0],offset[1],offset[2]);
            for(Part part:standard.parts())if(part.bone().equals(bone)) {
                Part target=slimParts.get(part.id());
                for(int i=0;i<part.vertices().size();i++) {
                    Vertex a=part.vertices().get(i),b=target.vertices().get(i);Vec3 v=fit.point(new Vec3(a.x(),a.y(),a.z()));
                    if(Math.abs(v.x()-b.x())>1e-4||Math.abs(v.y()-b.y())>1e-4||Math.abs(v.z()-b.z())>1e-4)
                        throw new IllegalArgumentException("细臂网格并非 rig 仿射适配，动效无法对齐："+part.id());
                }
            }
            result.put(bone,fit);
        }
        return Map.copyOf(result);
    }
    private static Map<Part,Vec3> centers(ImmortalMeshData data) {
        Map<Part,Vec3> result=new IdentityHashMap<>();
        for(Profile profile:data.profiles().values())for(Part part:profile.parts()) {
            double x=0,y=0,z=0;for(Vertex v:part.vertices()){x+=v.x();y+=v.y();z+=v.z();}
            int count=part.vertices().size();result.put(part,new Vec3((float)(x/count),(float)(y/count),(float)(z/count)));
        }
        return Collections.unmodifiableMap(result);
    }
    private volatile Map<ResourceLocation, Loaded> snapshot = Map.of();
    Loaded get(ResourceLocation model) { return snapshot.get(model); }
    @Override public void onResourceManagerReload(ResourceManager manager) {
        Map<ResourceLocation,Loaded> next=new HashMap<>();
        for(var definition:ImmortalEquipmentCatalogue.armor()) {
            Loaded loaded=next.computeIfAbsent(definition.model(),location->{
                try(var stream=manager.getResourceOrThrow(location).open();
                    var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                    ImmortalMeshData data=ImmortalMeshData.parse(reader);
                    Map<String,Surface> surfaces=new HashMap<>();
                    data.materials().forEach((name,material)->{
                        ResourceLocation texture=ResourceLocation.parse(material.texture());
                        if(manager.getResource(texture).isEmpty()) throw new IllegalArgumentException("缺少仙衣材质 "+texture);
                        surfaces.put(name,new Surface(ImmortalRenderTypes.solid(texture,false),ImmortalRenderTypes.solid(texture,true),ImmortalRenderTypes.glass(texture),ImmortalRenderTypes.glow(texture)));
                    });
                    return new Loaded(data,Map.copyOf(surfaces),fits(data),centers(data));
                } catch(Exception error) { throw new IllegalStateException("加载仙衣网格失败 "+location,error); }
            });
            if(loaded.data().materials().values().stream().noneMatch(m->m.texture().equals(definition.texture().toString())))
                throw new IllegalArgumentException("仙衣目录贴图不属于该套材质 "+definition.id());
            if(!loaded.data().setId().equals(definition.setId()) || loaded.data().effects().level()!=definition.level())
                throw new IllegalArgumentException("仙衣身份或境界与目录不符 "+definition.id());
            for(var profile:loaded.data().profiles().values()) {
                if(profile.parts().stream().noneMatch(p->p.slot().equals(definition.type().getSlot().getName())))
                    throw new IllegalArgumentException("仙衣缺少槽位 "+definition.id());
            }
        }
        snapshot=Map.copyOf(next);
        org.slf4j.LoggerFactory.getLogger(ImmortalMeshResources.class).info("仙衣网格已原子加载：{} 套",next.size());
    }
}
