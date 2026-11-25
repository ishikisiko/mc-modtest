package com.example.myvillage.worldplan;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.example.myvillage.resources.BuiltinResources;
import java.io.*;
import java.nio.*;
import java.security.*;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** 蓝图整地面：高度、施工权重和地表索引共同冻结，预留框外围不强行削平。 */
public final class SectTerrain {
    private final int width,depth,margin;
    private final int x0,z0,fieldWidth,fieldDepth;
    private final short[] heights;
    private final byte[] influence,surfaces;
    private final Cavern cavern;

    public static byte[] verifiedResource(String id,String hash,int limit){
        if(!id.matches("yuanxian:sect_blueprints/[a-z0-9_]+/[a-z_.]+")||!hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("宗门资源引用无效："+id);
        try(var in=BuiltinResources.open("data/"+id.replace(':','/'))){
            if(in==null)throw new FileNotFoundException(id);
            byte[] bytes=in.readNBytes(limit+1);
            if(bytes.length>limit||!hash.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
                throw new IllegalArgumentException("宗门资源容量或摘要不符："+id);
            return bytes;
        }catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException("无法读取宗门资源："+id,e);}
    }

    public static SectTerrain read(int width,int depth,int margin,int maxY,JsonObject manifest){
        JsonObject field=manifest.getAsJsonObject("terrain");
        byte[] packed=verifiedResource(field.get("resource").getAsString(),field.get("sha256").getAsString(),8*1024*1024);
        try(var in=new GZIPInputStream(new ByteArrayInputStream(packed))){
            return new SectTerrain(width,depth,margin,maxY,field.getAsJsonArray("bounds"),
                in.readNBytes(width*depth*4+1),manifest.getAsJsonObject("cavern"));
        }catch(IOException e){throw new IllegalStateException("宗门地形场无效",e);}
    }

    public SectTerrain(int width,int depth,int margin,int maxY,JsonArray bounds,byte[] raw,JsonObject cavity){
        var area=integers(bounds,4);
        x0=area[0];z0=area[1];fieldWidth=area[2];fieldDepth=area[3];
        if(x0<0||z0<0||fieldWidth<1||fieldDepth<1||fieldWidth>width-x0||fieldDepth>depth-z0
                ||raw.length!=fieldWidth*fieldDepth*4)throw new IllegalArgumentException("宗门地形场范围或长度不符");
        this.width=width;this.depth=depth;this.margin=margin;
        cavern=cavity==null?null:new Cavern(cavity,width,depth,maxY);
        heights=new short[fieldWidth*fieldDepth];influence=new byte[heights.length];surfaces=new byte[heights.length];
        var data=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<heights.length;i++){
            heights[i]=data.getShort();influence[i]=data.get();surfaces[i]=data.get();
            if(heights[i]<0||heights[i]>maxY)throw new IllegalArgumentException("宗门地形高度越界");
        }
    }
    private int index(int x,int z){
        int cx=Math.clamp(x,0,width-1)-x0,cz=Math.clamp(z,0,depth-1)-z0;
        return cx<0||cx>=fieldWidth||cz<0||cz>=fieldDepth?-1:cz*fieldWidth+cx;
    }
    public int ground(int x,int z){int i=index(x,z);return i<0?0:heights[i];}
    public int surface(int x,int z){int i=index(x,z);return i<0?0:Byte.toUnsignedInt(surfaces[i]);}
    public double weight(int x,int z){
        int i=index(x,z);if(i<0)return 0;
        int distance=Math.max(0,Math.max(Math.max(-x,x-width+1),Math.max(-z,z-depth+1)));
        return Byte.toUnsignedInt(influence[i])/255.0*Math.max(0,1-distance/(double)margin);
    }
    public int firstAir(int baseY,int x,int z,int natural){
        int floor=(int)Math.round(natural+(baseY+ground(x,z)+1-natural)*weight(x,z));
        return cavern==null?floor:baseY+cavern.surface(x,z,floor-baseY);
    }
    public boolean hasCavern(){return cavern!=null;}
    public int surveySamples(){return cavern==null?1:cavern.surveySamples;}
    public boolean affects(int x,int z){return weight(x,z)>0||(cavern!=null&&cavern.contains(x,z));}
    public int carveTop(){return cavern==null?0:cavern.crest;}
    public int carveBottom(){return cavern==null?0:cavern.bottom;}
    public boolean carves(int x,int y,int z){
        return cavern!=null&&(weight(x,z)==0||y>ground(x,z))&&cavern.air(x,y,z);
    }
    public boolean reserves(int x,int y,int z,int natural){
        if(weight(x,z)==0)return false;
        if(cavern==null)return true;
        int sky=firstAir(0,x,z,natural);
        return sky<cavern.vegetatedRoof||y<sky;
    }

    private static int[] integers(JsonArray row,int size){
        if(row==null||row.size()!=size)throw new IllegalArgumentException("宗门地形参数维数错误");
        int[] out=new int[size];for(int i=0;i<size;i++)out[i]=row.get(i).getAsInt();
        return out;
    }
    private static int[][] rows(JsonArray array,int size){
        if(array==null||array.size()>16)throw new IllegalArgumentException("宗门洞室参数超出范围");
        int[][] out=new int[array.size()][];
        for(int i=0;i<out.length;i++)out[i]=integers(array.get(i).getAsJsonArray(),size);
        return out;
    }
    private static double square(double x){return x*x;}

    private static final class Cavern {
        private final int cx,cz,rx,rz,crest,bottom,vegetatedRoof,surveySamples;
        private final int[][] rooms,tunnels,openings;
        private Cavern(JsonObject spec,int width,int depth,int maxY){
            int[] shape=integers(spec.getAsJsonArray("shell"),4);
            cx=shape[0];cz=shape[1];rx=shape[2];rz=shape[3];
            crest=spec.get("crest_y").getAsInt();bottom=spec.get("floor_y").getAsInt();
            vegetatedRoof=spec.get("vegetated_roof_y").getAsInt();
            surveySamples=spec.get("survey_samples").getAsInt();
            if(rx<1||rz<1||cx-rx<0||cz-rz<0||cx+rx>=width||cz+rz>=depth
                    ||bottom<0||bottom>=crest||crest>maxY||vegetatedRoof<=bottom||vegetatedRoof>crest
                    ||surveySamples<1||surveySamples>128)
                throw new IllegalArgumentException("宗门洞室山体超出蓝图");
            rooms=rows(spec.getAsJsonArray("chambers"),6);
            tunnels=rows(spec.getAsJsonArray("tunnels"),6);
            openings=rows(spec.getAsJsonArray("openings"),5);
            for(int[] room:rooms)if(room[3]<1||room[4]<1||room[5]<1)
                throw new IllegalArgumentException("宗门洞室半径无效");
            for(int[] cut:openings)if(cut[2]<1||cut[3]<1||cut[4]<bottom||cut[4]>=crest)
                throw new IllegalArgumentException("宗门敞口尺寸无效");
            for(int[] box:tunnels)if(box[0]<0||box[1]<bottom||box[2]<0||box[3]>=width||box[4]>crest
                    ||box[5]>=depth||box[0]>box[3]||box[1]>box[4]||box[2]>box[5])
                throw new IllegalArgumentException("宗门通道超出蓝图");
        }
        private double radius(int x,int z){return square((x-cx)/(double)rx)+square((z-cz)/(double)rz);}
        private boolean contains(int x,int z){return radius(x,z)<1;}
        private int surface(int x,int z,int floor){
            double r=radius(x,z);if(r>=1)return floor;
            double top=floor+Math.max(0,crest+1-floor)*StrictMath.sqrt(1-r);
            for(int[] cut:openings){
                double d=StrictMath.sqrt(square((x-cut[0])/(double)cut[2])+square((z-cut[1])/(double)cut[3]));
                double weight=Math.clamp((1-d)*5,0,1);
                top-=Math.max(0,top-Math.max(floor,cut[4]+1))*weight;
            }
            return (int)Math.round(top);
        }
        private boolean air(int x,int y,int z){
            if(y<bottom||y>crest)return false;
            for(int[] box:tunnels)if(x>=box[0]&&y>=box[1]&&z>=box[2]&&x<=box[3]&&y<=box[4]&&z<=box[5])return true;
            if(!contains(x,z))return false;
            for(int[] room:rooms)if(square((x-room[0])/(double)room[3])+square((y-room[1])/(double)room[4])
                    +square((z-room[2])/(double)room[5])<1)return true;
            return false;
        }
    }
}
