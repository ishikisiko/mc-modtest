package com.example.myvillage.worldplan;

import java.nio.*;

/** 蓝图整地面：高度、施工权重和地表索引共同冻结，预留框外围不强行削平。 */
public final class SectTerrain {
    private final int width,depth,margin;
    private final short[] heights;
    private final byte[] influence,surfaces;
    public SectTerrain(int width,int depth,int margin,int maxY,byte[] raw){
        if(raw.length!=width*depth*4)throw new IllegalArgumentException("宗门地形场长度不符");
        this.width=width;this.depth=depth;this.margin=margin;
        heights=new short[width*depth];influence=new byte[heights.length];surfaces=new byte[heights.length];
        var data=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<heights.length;i++){
            heights[i]=data.getShort();influence[i]=data.get();surfaces[i]=data.get();
            if(heights[i]<0||heights[i]>maxY)throw new IllegalArgumentException("宗门地形高度越界");
        }
    }
    private int index(int x,int z){return Math.clamp(z,0,depth-1)*width+Math.clamp(x,0,width-1);}
    public int ground(int x,int z){return heights[index(x,z)];}
    public int surface(int x,int z){return Byte.toUnsignedInt(surfaces[index(x,z)]);}
    public double weight(int x,int z){
        int distance=Math.max(0,Math.max(Math.max(-x,x-width+1),Math.max(-z,z-depth+1)));
        return Byte.toUnsignedInt(influence[index(x,z)])/255.0*Math.max(0,1-distance/(double)margin);
    }
    public int firstAir(int baseY,int x,int z,int natural){
        return (int)Math.round(natural+(baseY+ground(x,z)+1-natural)*weight(x,z));
    }
}
