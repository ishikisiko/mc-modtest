package com.example.myvillage.qianji;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 书册排列使用整数 1/16 格和位置种子；不保存逐本书的模型或逐格方块实体。 */
public final class ProceduralBooks {
    public record Rules(String markerRole,List<Integer> widths,List<Integer> heights,List<String> roles,int gap,int patterns,int cachedVariants) {
        public Rules {
            widths=List.copyOf(widths);heights=List.copyOf(heights);roles=List.copyOf(roles);
            if(markerRole==null||!markerRole.matches("[a-z0-9_]+")||widths.isEmpty()||heights.isEmpty()||roles.isEmpty()||widths.size()>16||heights.size()>16||roles.size()>8
                    ||widths.stream().anyMatch(v->v<1||v>16)||heights.stream().anyMatch(v->v<1||v>16)
                    ||roles.stream().anyMatch(v->!v.matches("[a-z0-9_]+"))||gap<0||gap>8||patterns<1||patterns>64
                    ||cachedVariants<1||cachedVariants>16)throw new IllegalArgumentException("书册生成参数越界");
        }
        public static Rules fromJson(JsonObject json) {
            if(json.get("schema").getAsInt()!=1)throw new IllegalArgumentException("书册生成参数格式错误");
            return new Rules(json.get("marker_role").getAsString(),integers(json.getAsJsonArray("widths")),integers(json.getAsJsonArray("heights")),
                    json.getAsJsonArray("book_roles").asList().stream().map(JsonElement::getAsString).toList(),
                    json.get("gap").getAsInt(),json.get("patterns").getAsInt(),json.get("cached_variants_per_model").getAsInt());
        }
        private static List<Integer> integers(JsonArray values){return values.asList().stream().map(JsonElement::getAsInt).toList();}
    }
    public record Region(int x0,int y0,int z0,int x1,int y1,int z1,int facing,int rowY) {
        public Region {
            if(x0<0||y0<0||z0<0||x1>16||y1>16||z1>16||x0>=x1||y0>=y1||z0>=z1||facing<0||facing>3||rowY< -16||rowY>16)
                throw new IllegalArgumentException("书册排列区域越界");
        }
        public static Region fromJson(JsonArray a){
            if(a.size()!=8)throw new IllegalArgumentException("书册排列区域长度错误");
            return new Region(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt(),
                    a.get(4).getAsInt(),a.get(5).getAsInt(),a.get(6).getAsInt(),a.get(7).getAsInt());
        }
    }
    public record Book(int x0,int y0,int z0,int x1,int y1,int z1,String role) {}
    private static final class Holder {
        static final Rules RULES=read();
        static Rules read(){
            try(var in=ProceduralBooks.class.getResourceAsStream("/data/myvillage/qianji/components/bookcases.json")){
                if(in==null)throw new IllegalStateException("缺少书册生成参数");
                return Rules.fromJson(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
        }
    }
    public static Rules rules(){return Holder.RULES;}
    public static int mix(int value){value^=value>>>16;value*=0x7feb352d;value^=value>>>15;value*=0x846ca68b;return value^(value>>>16);}
    public static int pattern(int horizontal,int y,int rowY,Rules rules){
        return Integer.remainderUnsigned(mix((horizontal*73428767)^((y*16+rowY)*438289)),rules.patterns());
    }
    public static List<Book> generate(Region r,int pattern,Rules rules){
        if(pattern<0||pattern>=rules.patterns())throw new IllegalArgumentException("书册种子越界");
        boolean alongX=r.facing()==0||r.facing()==2;int low=alongX?r.x0():r.z0(),end=alongX?r.x1():r.z1(),index=0;
        List<Book> result=new ArrayList<>();
        while(low<end){
            int value=mix(pattern+index*0x9e3779b9);
            int width=rules.widths().get(Integer.remainderUnsigned(value,rules.widths().size()));
            int height=rules.heights().get((value>>>8)%rules.heights().size());
            String role=rules.roles().get((value>>>16)%rules.roles().size());
            int y0=Math.max(r.y0(),r.rowY()),y1=Math.min(r.y1(),r.rowY()+height),high=Math.min(low+width,end);
            if(y1>y0)result.add(new Book(alongX?low:r.x0(),y0,alongX?r.z0():low,alongX?high:r.x1(),y1,alongX?r.z1():high,role));
            low+=width+rules.gap();index++;
        }
        return List.copyOf(result);
    }
    private ProceduralBooks(){}
}
