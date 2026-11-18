package com.example.myvillage.housing;

import com.example.myvillage.resources.BuiltinResources;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public final class HousingTemplates {
    public record Info(String id,String name,String role,String description,int width,int height,int depth,BlockPos entry,String resource,String hash){}
    public record Column(int x,int z,int bottom,int top){}
    public record Cell(BlockPos pos,BlockState state){}
    public record Template(Info info,Map<BlockPos,BlockState> blocks,List<List<Cell>> units,List<Column> columns){}
    private static final Map<String,Template> CACHE=new HashMap<>();
    private static final class Holder {static final List<Info> ALL=readCatalog();}
    public static List<Info> all(){return Holder.ALL;}
    public static List<Info> players(){return all().stream().filter(i->i.role().equals("player")).toList();}
    public static Info info(String id){return all().stream().filter(i->i.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("未知住宅模板"));}
    public static Template get(String id){return CACHE.computeIfAbsent(id,key->load(info(key)));}
    public static BlockPos rotate(BlockPos pos,int width,int depth,int turn){
        return switch(turn){case 0->pos;case 1->new BlockPos(depth-1-pos.getZ(),pos.getY(),pos.getX());
            case 2->new BlockPos(width-1-pos.getX(),pos.getY(),depth-1-pos.getZ());
            case 3->new BlockPos(pos.getZ(),pos.getY(),width-1-pos.getX());default->throw new IllegalArgumentException("无效朝向");};
    }
    public static Rotation rotation(int turn){return switch(turn){case 0->Rotation.NONE;case 1->Rotation.CLOCKWISE_90;case 2->Rotation.CLOCKWISE_180;case 3->Rotation.COUNTERCLOCKWISE_90;default->throw new IllegalArgumentException("无效朝向");};}
    private static byte[] read(String resource,int limit){
        if(!resource.matches("yuanxian:housing/[a-z0-9_/]+\\.json"))throw new IllegalArgumentException("住宅资源路径无效");
        try(var stream=BuiltinResources.open("data/"+resource.replace(':','/'))){
            byte[] bytes=stream.readNBytes(limit+1);if(bytes.length>limit)throw new IOException("住宅资源过大");return bytes;
        }catch(IOException e){throw new IllegalStateException("无法载入住宅资源",e);}
    }
    private static List<Info> readCatalog(){
        var root=JsonParser.parseString(new String(read("yuanxian:housing/catalog.json",128*1024),StandardCharsets.UTF_8)).getAsJsonObject();
        if(root.get("format").getAsInt()!=1)throw new IllegalArgumentException("住宅目录版本错误");
        var result=new ArrayList<Info>();
        for(var element:root.getAsJsonArray("houses")){
            var h=element.getAsJsonObject();String id=h.get("id").getAsString(),role=h.get("role").getAsString();var size=h.getAsJsonArray("size");
            int w=size.get(0).getAsInt(),height=size.get(1).getAsInt(),d=size.get(2).getAsInt();
            if(!id.matches("[a-z_]{1,48}")||!Set.of("npc","player").contains(role)||w<16||w>72||d<16||d>72||height<2||height>64)throw new IllegalArgumentException("住宅目录范围错误");
            result.add(new Info(id,h.get("name").getAsString(),role,h.get("description").getAsString(),w,height,d,
                point(h.getAsJsonArray("entry")),h.get("resource").getAsString(),h.get("sha256").getAsString()));
        }
        if(result.size()!=12||result.stream().map(Info::id).distinct().count()!=12||result.stream().filter(i->i.role().equals("player")).count()!=6)throw new IllegalArgumentException("住宅角色目录不完整");
        return List.copyOf(result);
    }
    private static Template load(Info info){
        byte[] raw=read(info.resource(),8*1024*1024);
        try{if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)).equals(info.hash()))throw new IllegalArgumentException("住宅模板摘要不符");}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        var root=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();
        List<BlockState> palette=new ArrayList<>();
        for(var entry:root.getAsJsonArray("palette")){
            try{palette.add(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),entry.getAsString(),false).blockState());}
            catch(Exception e){throw new IllegalArgumentException("住宅包含未注册方块："+entry,e);}
        }
        Map<BlockPos,BlockState> blocks=new HashMap<>();
        for(var entry:root.getAsJsonArray("blocks")){
            var a=entry.getAsJsonArray();var p=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
            if(p.getX()<0||p.getX()>=info.width()||p.getZ()<0||p.getZ()>=info.depth()||p.getY()<-2||p.getY()>=64)throw new IllegalArgumentException("住宅方块越界");
            if(blocks.put(p,palette.get(a.get(3).getAsInt()))!=null)throw new IllegalArgumentException("住宅坐标重复");
        }
        if(blocks.size()>100000)throw new IllegalArgumentException("住宅方块过多");
        List<Column> columns=new ArrayList<>();List<List<Cell>> units=new ArrayList<>();Set<BlockPos> touched=new HashSet<>();
        for(var entry:root.getAsJsonArray("columns")){
            var a=entry.getAsJsonArray();var c=new Column(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt());
            if(c.x()<0||c.x()>=info.width()||c.z()<0||c.z()>=info.depth()||c.bottom()!=-2||c.top()<0||c.top()>64)throw new IllegalArgumentException("施工列越界");
            columns.add(c);List<Cell> clear=new ArrayList<>();
            for(int y=c.top()-1;y>=0;y--){var p=new BlockPos(c.x(),y,c.z());if(!blocks.containsKey(p))clear.add(new Cell(p,Blocks.AIR.defaultBlockState()));}
            if(!clear.isEmpty())units.add(List.copyOf(clear));
        }
        for(var unit:root.getAsJsonArray("units")){
            List<Cell> cells=new ArrayList<>();
            for(var entry:unit.getAsJsonArray()){
                var p=point(entry.getAsJsonArray());var state=blocks.get(p);
                if(state==null||!touched.add(p))throw new IllegalArgumentException("住宅施工单元重复或不存在");
                cells.add(new Cell(p,state));
            }
            if(cells.isEmpty()||cells.size()>125)throw new IllegalArgumentException("住宅施工单元过大");
            units.add(List.copyOf(cells));
        }
        if(!touched.equals(blocks.keySet()))throw new IllegalArgumentException("住宅施工单元不完整");
        return new Template(info,Map.copyOf(blocks),List.copyOf(units),List.copyOf(columns));
    }
    private static BlockPos point(JsonArray a){if(a.size()!=3)throw new IllegalArgumentException("住宅坐标格式错误");return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private HousingTemplates(){}
}
