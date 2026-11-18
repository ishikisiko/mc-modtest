package com.example.myvillage.housing;

import com.example.myvillage.estate.EstateBounds;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

public record HousingPlot(String id,String name,EstateBounds bounds,int floor,BlockPos steward,BlockPos entry) {
    public int width(){return (int)(bounds.maxX()-bounds.minX());}
    public int depth(){return (int)(bounds.maxZ()-bounds.minZ());}
    public int minX(){return (int)bounds.minX();}
    public int minZ(){return (int)bounds.minZ();}
    public static HousingPlot read(JsonObject root,int width,int height,int depth){
        String id=root.get("id").getAsString(),name=root.get("name").getAsString();var a=root.getAsJsonArray("bounds");
        if(!id.matches("[a-z_]{1,64}")||name.length()>64||a.size()!=4)throw new IllegalArgumentException("宅地身份错误");
        var bounds=new EstateBounds(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt());
        int w=(int)(bounds.maxX()-bounds.minX()),d=(int)(bounds.maxZ()-bounds.minZ()),floor=root.get("floor_y").getAsInt();
        if(bounds.minX()<0||bounds.minZ()<0||bounds.maxX()>width||bounds.maxZ()>depth||!((w==64&&d==72)||(w==72&&d==64))||floor<3||floor>=height-32)throw new IllegalArgumentException("宅地尺寸或标高错误");
        BlockPos steward=point(root,"steward"),entry=point(root,"entry");
        for(var p:new BlockPos[]{steward,entry})if(p.getX()<0||p.getX()>=width||p.getZ()<0||p.getZ()>=depth||p.getY()<1||p.getY()>=height-2||bounds.overlaps(p.getX()+.5,p.getZ()+.5,.3))throw new IllegalArgumentException("宅地执事和路口须在公共退界带");
        return new HousingPlot(id,name,bounds,floor,steward,entry);
    }
    private static BlockPos point(JsonObject root,String key){var a=root.getAsJsonArray(key);if(a.size()!=3)throw new IllegalArgumentException("宅地坐标错误");return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
}
