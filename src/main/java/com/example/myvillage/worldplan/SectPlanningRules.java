package com.example.myvillage.worldplan;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import static com.example.myvillage.worldplan.WorldPlanRules.*;

/** 由正式宗门蓝图冻结；选址、地形与实际施工使用同一套尺寸和高度。 */
public final class SectPlanningRules {
    public final int slots, radius, attempts, buffer, spacing, minSurface, maxSurface, minY, maxY;
    public final int width, height, depth, margin, maxBlockY, entryX, entryY, entryZ;
    public final String hash;
    private final short[] terrain;
    public SectPlanningRules(JsonObject r) {
        slots=integer(r,"slots_per_region",1,32);radius=integer(r,"placement_radius",512,5000);
        attempts=integer(r,"placement_attempts",16,8192);buffer=integer(r,"structure_buffer",16,256);
        spacing=integer(r,"gate_spacing",128,2048);
        minSurface=integer(r,"minimum_surface",64,200);maxSurface=integer(r,"maximum_surface",minSurface,240);
        minY=integer(r,"minimum_y",-64,0);maxY=integer(r,"maximum_y_exclusive",256,320);
        if(!text(r,"on_candidate_exhaustion").equals("fail_whole_plan"))throw new IllegalArgumentException("宗门地块不足必须拒绝世界规划");
        width=integer(r,"width",16,512);height=integer(r,"height",16,256);depth=integer(r,"depth",16,512);
        margin=integer(r,"blend_margin",1,128);maxBlockY=integer(r,"max_y",1,height-1);
        entryX=integer(r,"entry_x",0,width-1);entryY=integer(r,"entry_y",0,height-1);entryZ=integer(r,"entry_z",0,depth-1);
        hash=text(r,"blueprint_sha256");if(!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("宗门蓝图摘要无效");
        try(var input=new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text(r,"terrain_gzip"))))) {
            byte[] bytes=input.readNBytes(width*depth*2+1);
            if(bytes.length!=width*depth*2)throw new IllegalArgumentException("宗门高度场长度无效");
            var values=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);terrain=new short[width*depth];
            for(int i=0;i<terrain.length;i++){terrain[i]=values.getShort();if(terrain[i]<0||terrain[i]>maxBlockY)throw new IllegalArgumentException("宗门高度场越界");}
        }catch(java.io.IOException e){throw new IllegalArgumentException("宗门高度场无效",e);}
    }
    public int ground(int x,int z){return terrain[Math.clamp(z,0,depth-1)*width+Math.clamp(x,0,width-1)];}
}
