package com.example.myvillage.worldplan;

import com.google.gson.JsonObject;
import java.util.List;

/** Frozen siting inputs. Geometry dimensions are compiled from the owned settlement asset. */
public final class SettlementRules {
    public final String layout, layoutHash;
    public final int attempts, searchRadius, maximumSites, structureBuffer;
    public final int size, margin, inset, baseY, height, anchorLevel, maxRelief, minAboveSea, maxSurface, salt;
    public final boolean allowWalled;
    private final int[] sampleOffsets;
    public final List<String> biomes;

    public SettlementRules(JsonObject root) {
        if (root == null) throw new IllegalArgumentException("Missing frozen settlement rules");
        layout = root.get("layout").getAsString();
        if (!layout.equals("bay_terraces_v1")) throw new IllegalArgumentException("Unsupported settlement layout");
        attempts = integer(root,"attempts_per_region",1,512);
        searchRadius = integer(root,"search_radius",128,2000);
        maximumSites = integer(root,"maximum_sites",1,8);
        structureBuffer = integer(root,"structure_buffer",0,256);
        if (!root.get("allow_walled").isJsonPrimitive() || !root.get("allow_walled").getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Expected allow_walled boolean");
        allowWalled = root.get("allow_walled").getAsBoolean();
        JsonObject asset = root.getAsJsonObject("asset"), siting = asset.getAsJsonObject("worldgen");
        if (!layout.equals(asset.get("id").getAsString())) throw new IllegalArgumentException("Settlement asset identity mismatch");
        layoutHash = asset.get("layout_hash").getAsString();
        if (!layoutHash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid settlement layout hash");
        size = integer(asset,"size",16,224); baseY = integer(asset,"base_y",0,128); height = integer(asset,"height",1,160);
        margin = integer(siting,"margin",0,64);
        inset = integer(siting,"blend_width",margin+16,size/2)-margin;
        if (size%16!=0 || margin%16!=0 || size+2*margin>256) throw new IllegalArgumentException("Settlement chunk bounds");
        anchorLevel = integer(siting,"anchor_level",baseY,baseY+height-1);
        maxRelief = integer(siting,"max_relief",1,64); minAboveSea = integer(siting,"min_above_sea",0,32);
        maxSurface = integer(siting,"max_surface",anchorLevel,240); salt = integer(siting,"salt",0,Integer.MAX_VALUE);
        var offsets=siting.getAsJsonArray("sample_offsets");
        if(offsets.size()!=3)throw new IllegalArgumentException("Settlement sample count");
        sampleOffsets=new int[3];
        for(int i=0;i<3;i++) {
            var value=offsets.get(i); double d=value.getAsDouble();
            if(d!=Math.rint(d)||d< -size/2||d>size/2)throw new IllegalArgumentException("Settlement sample offset");
            sampleOffsets[i]=(int)d;
        }
        var names=new java.util.ArrayList<String>();
        for(var value:siting.getAsJsonArray("biomes")) {
            String id=value.getAsString();if(!id.matches("minecraft:[a-z0-9_]+"))throw new IllegalArgumentException("Settlement biome");
            names.add(id);
        }
        if(names.isEmpty()||names.size()>32)throw new IllegalArgumentException("Settlement biome count");
        biomes=List.copyOf(names);
    }
    public int[] sampleOffsets(){return sampleOffsets.clone();}
    private static int integer(JsonObject root,String name,int lo,int hi) {
        var value=root.get(name);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Settlement field "+name);
        double d=value.getAsDouble();
        if(!Double.isFinite(d)||d!=Math.rint(d)||d<lo||d>hi)throw new IllegalArgumentException("Settlement bounds "+name);
        return (int)d;
    }
}
