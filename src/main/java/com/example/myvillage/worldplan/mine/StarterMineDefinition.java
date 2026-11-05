package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.capital.CapitalGameBundle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** 矿场的唯一资源定义。读取有界作者输出，不访问世界或区块。 */
public final class StarterMineDefinition {
    public record Cell(int x,int y,int z,int palette) {}
    public record Point(int x,int y,int z) {}
    public record Landmark(String id,String name,Point position) {}
    public record Placement(int minimumRoadDistance,int maximumRoadDistance,int sampleSpacing,List<Integer> connectorLengths,
                            int pathWidth,int earthwork,int minimumLandY,int surfaceSpread,int terrainAdjustment,
                            int attempts,int minimumY,int maximumY) {
        public Placement {connectorLengths=List.copyOf(connectorLengths);}
    }
    public final String resource,hash,name;
    public final int width,height,depth,groundY,soilDepth,clearance,foundationDepth,minBlockY,maxBlockY;
    public final Point entry,port;
    public final Placement placement;
    public final StarterMineCaveRules caves;
    public final RegionalMineRules regional;
    public final List<Cell> cells;
    public final List<String> palette;
    public final Map<Integer,String> entities;
    public final Map<String,String> surfaces;
    public final List<Landmark> landmarks;
    public final List<Point> trees;
    private final short[] terrain;
    private final byte[] weights;

    public StarterMineDefinition(JsonObject reference){
        if(reference==null)throw new IllegalArgumentException("缺少初始城矿场定义");
        resource=text(reference,"resource");hash=text(reference,"sha256");
        byte[] raw=CapitalGameBundle.readResource(resource,2*1024*1024);
        CapitalGameBundle.requireHash(raw,hash,resource);
        var r=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();
        integer(r,"schema",1,1);if(!text(r,"id").equals("starter_mine"))throw new IllegalArgumentException("矿场身份不正确");
        name=text(r,"display_name");var size=r.getAsJsonArray("size");
        if(size==null||size.size()!=3)throw new IllegalArgumentException("矿场尺寸不正确");
        width=bounded(size.get(0).getAsInt(),16,128);height=bounded(size.get(1).getAsInt(),16,128);depth=bounded(size.get(2).getAsInt(),16,128);
        groundY=integer(r,"ground_y",8,height-8);entry=point(r,"entry");port=point(r,"road_port");
        if(port.z!=0||port.y!=groundY||port.x<4||port.x>=width-4)throw new IllegalArgumentException("矿场接路口必须在正面边缘");
        var t=r.getAsJsonObject("terrain");soilDepth=integer(t,"soil_depth",1,8);clearance=integer(t,"clearance",3,8);foundationDepth=integer(t,"foundation_depth",1,8);
        terrain=new short[width*depth];weights=new byte[terrain.length];
        try(var in=new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text(r,"terrain_gzip"))))){
            byte[] data=in.readNBytes(terrain.length*3+1);if(data.length!=terrain.length*3)throw new IllegalArgumentException("矿场地形数据长度不匹配");
            var input=new DataInputStream(new ByteArrayInputStream(data));
            for(int i=0;i<terrain.length;i++){terrain[i]=(short)bounded(input.readShort(),1,height-8);weights[i]=input.readByte();}
        }catch(java.io.IOException e){throw new IllegalArgumentException("矿场地形无法解码",e);}
        if(weight(port.x,port.z)!=255||top(port.x,port.z)!=groundY)throw new IllegalArgumentException("矿场出口没有完整地面");
        var p=r.getAsJsonObject("placement");List<Integer> lengths=new ArrayList<>();
        for(var v:p.getAsJsonArray("connector_lengths")){int n=bounded(v.getAsInt(),12,128);if(lengths.contains(n))throw new IllegalArgumentException("矿场接路长度重复");lengths.add(n);}
        if(lengths.isEmpty()||lengths.size()>8||!text(p,"on_candidate_exhaustion").equals("fail_whole_plan"))throw new IllegalArgumentException("矿场选址规则不完整");
        placement=new Placement(integer(p,"minimum_road_distance",16,512),integer(p,"maximum_road_distance",128,1536),integer(p,"road_sample_spacing",4,64),lengths,
            integer(p,"connector_width",3,5),integer(p,"maximum_connector_earthwork",1,16),integer(p,"minimum_land_y",32,128),integer(p,"maximum_surface_spread",1,48),
            integer(p,"maximum_terrain_adjustment",1,48),integer(p,"maximum_candidates",8,2048),integer(p,"minimum_y",-64,0),integer(p,"maximum_y_exclusive",256,320));
        if(placement.pathWidth%2==0||placement.maximumRoadDistance<=placement.minimumRoadDistance)throw new IllegalArgumentException("矿场距离或道路宽度不合法");
        Map<String,String> materials=new HashMap<>();for(String key:List.of("stone","dirt","grass","path"))materials.put(key,text(r.getAsJsonObject("surface_materials"),key));surfaces=Map.copyOf(materials);
        byte[] compressed=CapitalGameBundle.readResource(text(r,"blocks_resource"),4*1024*1024);
        CapitalGameBundle.requireHash(compressed,text(r,"blocks_sha256"),"矿场方块");
        JsonObject body;
        try(var in=new GZIPInputStream(new ByteArrayInputStream(compressed))){
            byte[] data=in.readNBytes(16*1024*1024+1);if(data.length>16*1024*1024)throw new IllegalArgumentException("矿场方块数据超过预算");
            body=JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException e){throw new IllegalArgumentException("矿场方块无法解码",e);}
        integer(body,"schema",1,1);List<String> states=new ArrayList<>();
        for(var v:body.getAsJsonArray("palette")){String state=v.getAsString();if(!state.matches("minecraft:[a-z0-9_]+(?:\\[[a-z0-9_=,]+])?"))throw new IllegalArgumentException("矿场只能使用已注册原版方块");states.add(state);}
        if(states.isEmpty()||states.size()>128||new HashSet<>(states).size()!=states.size())throw new IllegalArgumentException("矿场方块表无效");palette=List.copyOf(states);
        List<Cell> parsed=new ArrayList<>();Set<Integer> occupied=new HashSet<>();int low=height,high=0;
        for(var v:body.getAsJsonArray("cells")){
            var a=v.getAsJsonArray();if(a.size()!=4)throw new IllegalArgumentException("矿场方块行长度无效");
            var cell=new Cell(bounded(a.get(0).getAsInt(),0,width-1),bounded(a.get(1).getAsInt(),0,height-1),bounded(a.get(2).getAsInt(),0,depth-1),bounded(a.get(3).getAsInt(),0,palette.size()-1));
            if(!occupied.add(key(cell.x,cell.y,cell.z)))throw new IllegalArgumentException("矿场方块重复");parsed.add(cell);low=Math.min(low,cell.y);high=Math.max(high,cell.y);
        }
        if(parsed.isEmpty()||parsed.size()>width*height*depth)throw new IllegalArgumentException("矿场方块数量无效");cells=List.copyOf(parsed);minBlockY=low;maxBlockY=high;
        Map<Integer,String> blockEntities=new HashMap<>();
        for(var v:body.getAsJsonArray("block_entities")){var item=v.getAsJsonObject();var at=point(item,"pos");int key=key(at.x,at.y,at.z);if(!occupied.contains(key)||blockEntities.putIfAbsent(key,item.getAsJsonObject("nbt").toString())!=null)throw new IllegalArgumentException("矿场方块实体没有唯一所属方块");}
        entities=Map.copyOf(blockEntities);
        List<Landmark> goals=new ArrayList<>();Set<String> goalIds=new HashSet<>();
        for(var v:r.getAsJsonArray("landmarks")){var item=v.getAsJsonObject();String id=text(item,"id");if(!goalIds.add(id))throw new IllegalArgumentException("矿场目标重复");goals.add(new Landmark(id,text(item,"name"),point(item,"pos")));}landmarks=List.copyOf(goals);
        List<Point> plants=new ArrayList<>();for(var v:r.getAsJsonArray("trees")){JsonObject wrapper=new JsonObject();wrapper.add("pos",v);plants.add(point(wrapper,"pos"));}trees=List.copyOf(plants);
        if(landmarks.isEmpty()||trees.isEmpty())throw new IllegalArgumentException("矿场必须有入口和树木");
        caves=new StarterMineCaveRules(r.getAsJsonObject("underground"),this);
        regional=new RegionalMineRules(r.getAsJsonObject("regional_entries"),this);
        if(minBlockY<groundY-1)throw new IllegalArgumentException("地面模板不得携带固定地下洞室");
    }
    public int key(int x,int y,int z){return (z*height+y)*width+x;}
    public int top(int x,int z){return terrain[z*width+x];}
    public int weight(int x,int z){return Byte.toUnsignedInt(weights[z*width+x]);}
    public int surface(int originY,int x,int z,int natural){int weight=weight(x,z);return natural+(int)Math.round((originY+top(x,z)-natural)*weight/255.0);}
    private Point point(JsonObject r,String name){var p=r.getAsJsonArray(name);if(p==null||p.size()!=3)throw new IllegalArgumentException("矿场坐标无效："+name);return new Point(bounded(p.get(0).getAsInt(),0,width-1),bounded(p.get(1).getAsInt(),0,height-1),bounded(p.get(2).getAsInt(),0,depth-1));}
    static int bounded(int n,int lo,int hi){if(n<lo||n>hi)throw new IllegalArgumentException("矿场数值越界："+n);return n;}
    static int integer(JsonObject r,String key,int lo,int hi){
        var value=r==null?null:r.get(key);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("矿场缺少整数："+key);
        double number=value.getAsDouble();if(!Double.isFinite(number)||number!=Math.rint(number)||number<lo||number>hi)throw new IllegalArgumentException("矿场整数越界："+key);
        return (int)number;
    }
    static String text(JsonObject r,String key){
        var value=r==null?null:r.get(key);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().isBlank())throw new IllegalArgumentException("矿场缺少文本："+key);
        return value.getAsString();
    }
}
