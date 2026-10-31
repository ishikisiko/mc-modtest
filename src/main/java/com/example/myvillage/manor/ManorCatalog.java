package com.example.myvillage.manor;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;

/** 模型与碰撞共同消费七府生成目录；不依赖客户端类型。 */
public final class ManorCatalog {
    public record Material(String id,String name,String role,boolean collides,boolean opaque,String sound) { }
    public static final class Profile {
        private final List<double[]> outline,collision,occlusion;
        private final VoxelShape[][] cache=new VoxelShape[3][4];
        private final Map<String,List<double[]>> translucent;
        private final Map<String,VoxelShape[]> translucentCache=new HashMap<>();
        Profile(List<double[]> outline,List<double[]> collision,List<double[]> occlusion,Map<String,List<double[]>> translucent) {
            if(outline.isEmpty()||outline.size()>128)throw new IllegalArgumentException("七府雕构复杂度越界");
            this.outline=outline;this.collision=same(outline,collision)?outline:collision;this.occlusion=same(outline,occlusion)?outline:occlusion;
            this.translucent=Map.copyOf(translucent);
            translucent.keySet().forEach(key->translucentCache.put(key,new VoxelShape[4]));
        }
        private static boolean same(List<double[]> a,List<double[]> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!Arrays.equals(a.get(i),b.get(i)))return false;return true;}
        VoxelShape shape(int kind,Direction facing) {
            if((kind==1&&collision==outline)||(kind==2&&occlusion==outline))return shape(0,facing);
            int turn=turn(facing);var found=cache[kind][turn];if(found!=null)return found;
            var boxes=kind==0?outline:kind==1?collision:occlusion;VoxelShape result=Shapes.empty();
            for(double[] source:boxes) {
                double[] b=source;
                for(int i=0;i<turn;i++)b=new double[]{16-b[5],b[1],b[0],16-b[2],b[4],b[3]};
                result=Shapes.or(result,Block.box(b[0],b[1],b[2],b[3],b[4],b[5]));
            }
            return cache[kind][turn]=result.optimize();
        }
        public Set<String> translucentMaterials(){return translucent.keySet();}
        public synchronized VoxelShape translucentShape(String material,Direction facing) {
            var boxes=translucent.get(material);if(boxes==null)return Shapes.empty();
            int turn=turn(facing);var cached=translucentCache.get(material);
            if(cached[turn]!=null)return cached[turn];
            VoxelShape result=Shapes.empty();
            for(double[] source:boxes){
                double[] b=source;
                for(int i=0;i<turn;i++)b=new double[]{16-b[5],b[1],b[0],16-b[2],b[4],b[3]};
                result=Shapes.or(result,Block.box(b[0],b[1],b[2],b[3],b[4],b[5]));
            }
            return cached[turn]=result.optimize();
        }
    }
    public record Bank(String id,String name,List<Profile> profiles) { public Profile profile(int shape){return profiles.get(shape);} }
    public record Manor(String city,String name,int width,int height,int depth,int blocks,int[] sourceMin) { }
    public record Gallery(String dimension,int spacing,int columns,int floorY,int blocksPerTick,double cameraScale) { }
    public final List<Material> materials;
    public final List<Bank> banks;
    public final List<Manor> manors;
    public final Gallery gallery;
    public final float strength,resistance;
    private ManorCatalog(List<Material> materials,List<Bank> banks,List<Manor> manors,Gallery gallery,float strength,float resistance) {
        this.materials=List.copyOf(materials);this.banks=List.copyOf(banks);this.manors=List.copyOf(manors);this.gallery=gallery;this.strength=strength;this.resistance=resistance;
    }
    private static final class Holder {static final ManorCatalog DATA=load();}
    public static ManorCatalog get(){return Holder.DATA;}
    public Manor manor(String city){return manors.stream().filter(m->m.city().equals(city)).findFirst().orElseThrow(()->new IllegalArgumentException("未知府邸："+city));}
    static int turn(Direction facing){return switch(facing){case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException("需要水平朝向");};}
    private static List<double[]> boxes(JsonArray entries) {
        List<double[]> result=new ArrayList<>();
        for(var entry:entries) {
            var a=entry.getAsJsonArray();if(a.size()<6)throw new IllegalArgumentException("七府碰撞长度错误");double[] b=new double[6];
            for(int i=0;i<6;i++)b[i]=a.get(i).getAsDouble();
            for(int i=0;i<3;i++)if(!Double.isFinite(b[i]+b[i+3])||b[i]<0||b[i]>=b[i+3]||b[i+3]>16)throw new IllegalArgumentException("七府碰撞超出单格");
            result.add(b);
        }
        return List.copyOf(result);
    }
    private static ManorCatalog load() {
        String resource="/data/myvillage/lord_manors/catalog.json";
        try(var stream=ManorCatalog.class.getResourceAsStream(resource)) {
            if(stream==null)throw new IllegalStateException("Missing "+resource);
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("七府目录格式错误");
            List<Material> materials=new ArrayList<>();List<Bank> banks=new ArrayList<>();List<Manor> manors=new ArrayList<>();Set<String> ids=new HashSet<>();
            for(var e:root.getAsJsonArray("materials")) {
                var m=e.getAsJsonObject();String id=m.get("id").getAsString();if(!ids.add(id))throw new IllegalArgumentException("七府物品重复");
                materials.add(new Material(id,m.get("name").getAsString(),m.get("role").getAsString(),m.get("collides").getAsBoolean(),m.get("opaque").getAsBoolean(),m.get("sound").getAsString()));
            }
            for(var e:root.getAsJsonArray("banks")) {
                var b=e.getAsJsonObject();String id=b.get("id").getAsString();if(!ids.add(id))throw new IllegalArgumentException("七府雕构重复");List<Profile> profiles=new ArrayList<>();
                for(var pe:b.getAsJsonArray("profiles")){
                    var p=pe.getAsJsonObject();Map<String,List<double[]>> translucent=new HashMap<>();
                    for(var entry:p.getAsJsonObject("translucent").entrySet())translucent.put(entry.getKey(),boxes(entry.getValue().getAsJsonArray()));
                    profiles.add(new Profile(boxes(p.getAsJsonArray("boxes")),boxes(p.getAsJsonArray("collision")),boxes(p.getAsJsonArray("occlusion")),translucent));
                }
                if(profiles.size()!=32)throw new IllegalArgumentException("七府雕构状态不齐全");banks.add(new Bank(id,b.get("name").getAsString(),List.copyOf(profiles)));
            }
            for(var e:root.getAsJsonArray("manors")) {
                var m=e.getAsJsonObject();var size=m.getAsJsonArray("size");var min=m.getAsJsonArray("source_min");
                int w=size.get(0).getAsInt(),h=size.get(1).getAsInt(),d=size.get(2).getAsInt();if(w<1||w>512||h<1||h>256||d<1||d>512)throw new IllegalArgumentException("七府体量越界");
                manors.add(new Manor(m.get("city").getAsString(),m.get("name").getAsString(),w,h,d,m.get("blocks").getAsInt(),new int[]{min.get(0).getAsInt(),min.get(1).getAsInt(),min.get(2).getAsInt()}));
            }
            var g=root.getAsJsonObject("gallery");var gallery=new Gallery(g.get("dimension").getAsString(),g.get("spacing").getAsInt(),g.get("columns").getAsInt(),g.get("floor_y").getAsInt(),g.get("blocks_per_tick").getAsInt(),g.get("camera_scale").getAsDouble());
            if(manors.size()!=7||banks.size()>2048||materials.size()>512||gallery.spacing()<256||gallery.columns()<1||gallery.columns()>8||gallery.blocksPerTick()<1||gallery.blocksPerTick()>16384)throw new IllegalArgumentException("七府目录规模错误");
            return new ManorCatalog(materials,banks,manors,gallery,root.get("strength").getAsFloat(),root.get("explosion_resistance").getAsFloat());
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private ManorCatalog(){throw new AssertionError();}
}
