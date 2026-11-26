package com.example.myvillage.region.runtime;

/** 创建世界时确定的完整宗门地块；与命簿宗门的归属绑定分开。 */
public record SectSite(String id, String regionId, int x, int baseY, int z,
                       int gateX, int gateY, int gateZ, int width, int depth, int margin,
                       String blueprintId,String blueprintHash,String tier,
                       int openingRank, boolean foundingOnly, String parentSiteId) {
    public SectSite(String id,String regionId,int x,int baseY,int z,int gateX,int gateY,int gateZ,
                    int width,int depth,int margin,String blueprintId,String blueprintHash,String tier) {
        this(id,regionId,x,baseY,z,gateX,gateY,gateZ,width,depth,margin,blueprintId,blueprintHash,tier,-1,false,"");
    }

    public boolean intersects(int minX,int minZ,int maxX,int maxZ,int buffer) {
        int m=margin+buffer;
        return minX<x+width+m && maxX>=x-m && minZ<z+depth+m && maxZ>=z-m;
    }
}
