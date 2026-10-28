package com.example.myvillage.equipment.immortal;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;

/** Immutable, bounded version-one wearing mesh. Contains no client or rendering types. */
public record ImmortalMeshData(String setId, Map<String, Material> materials,
        Map<String, Profile> profiles, Effects effects) {
    private static final Set<String> BONES = Set.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
    private static final Set<String> SLOTS = Set.of("head", "chest", "legs", "feet");
    public record Vec3(float x, float y, float z) { }
    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) { }
    public record Material(String texture, float opacity) { }
    public record Part(String id, String slot, String bone, String material, String side, List<Vertex> vertices, List<Integer> indices) { }
    public record Profile(Map<String, Vec3> boneOrigins, List<Part> parts) { }
    public record Motion(List<String> targets, Vec3 pivot, String axis, float angle, float period, Vec3 lift) { }
    public record Glow(List<String> targets, int color, float period, float min, float max) { }
    public record Flow(String slot, String bone, List<Vec3> path, int count, float size, int color,
            float period, float opacity, String shape, float trail) { }
    public record Arc(String slot, String bone, List<Vec3> points, int color, float period,
            float duration, float phase, float opacity) { }
    public record Effects(int level, String title, String description, List<Motion> motions,
            List<Glow> glows, List<Flow> flows, List<Arc> arcs) { }

    public static ImmortalMeshData parse(Reader reader) {
        try { return parseRoot(JsonParser.parseReader(reader).getAsJsonObject()); }
        catch(IllegalArgumentException error) {throw error;}
        catch(RuntimeException error) {throw new IllegalArgumentException("仙衣网格格式错误",error);}
    }
    private static ImmortalMeshData parseRoot(JsonObject root) {
        require(integer(root, "version", 1, 1) == 1, "版本错误");
        String setId = text(root, "set_id");
        require(setId.matches("[a-z0-9_]+"), "套装身份错误");
        Map<String, Material> materials = new LinkedHashMap<>();
        JsonObject rawMaterials = object(root, "materials");
        require(rawMaterials.size() > 0 && rawMaterials.size() <= 128, "材质数量越界");
        rawMaterials.entrySet().forEach(e -> {
            JsonObject m = e.getValue().getAsJsonObject();
            String texture = text(m, "texture");
            require(texture.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png") && !texture.contains(".."), "材质路径错误");
            materials.put(e.getKey(), new Material(texture, number(m, "opacity", 0, 1)));
        });
        Map<String, Profile> profiles = new LinkedHashMap<>();
        JsonObject rawProfiles = object(root, "profiles");
        for (String name : List.of("standard", "slim")) {
            JsonObject p = object(rawProfiles, name);
            Map<String, Vec3> origins = new LinkedHashMap<>();
            JsonObject rawOrigins = object(p, "bone_origins");
            require(rawOrigins.keySet().equals(BONES), "骨骼原点必须完整且合法");
            for (String bone : BONES) origins.put(bone, vec(rawOrigins.get(bone)));
            List<Part> parts = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            long verticesTotal = 0;
            for (JsonElement el : array(p, "parts", 4096)) {
                JsonObject part = el.getAsJsonObject();
                String id = text(part, "id"), slot = text(part, "slot"), bone = text(part, "bone"), material = text(part, "material");
                require(ids.add(id), "重复组件 " + id);
                require(SLOTS.contains(slot) && BONES.contains(bone), "槽位或骨骼错误 " + id);
                require(switch(slot) {case "head"->bone.equals("head");case "chest"->Set.of("body","right_arm","left_arm").contains(bone);case "legs"->Set.of("body","right_leg","left_leg").contains(bone);case "feet"->Set.of("right_leg","left_leg").contains(bone);default->false;}, "槽位不能绑定此骨骼 " + id);
                String side=part.has("side")?text(part,"side"):"double";
                require(Set.of("front","double").contains(side),"未知面向 " + id);
                require(materials.containsKey(material), "未知材质 " + material);
                List<Vertex> vertices = new ArrayList<>();
                JsonArray rawVertices = array(part, "vertices", 262144);
                verticesTotal += rawVertices.size();
                require(verticesTotal <= 1_000_000, "网格顶点总数越界");
                for (JsonElement v : rawVertices) {
                    JsonArray a = v.getAsJsonArray(); require(a.size() == 8, "顶点必须有八个值");
                    float x = scalar(a.get(0), -512, 512), y = scalar(a.get(1), -512, 512), z = scalar(a.get(2), -512, 512);
                    float u = scalar(a.get(3), 0, 1), w = scalar(a.get(4), 0, 1);
                    float nx = scalar(a.get(5), -1, 1), ny = scalar(a.get(6), -1, 1), nz = scalar(a.get(7), -1, 1);
                    float length = nx*nx + ny*ny + nz*nz;
                    require(length > .98f && length < 1.02f, "法线未单位化");
                    vertices.add(new Vertex(x,y,z,u,w,nx,ny,nz));
                }
                List<Integer> indices = new ArrayList<>();
                JsonArray rawIndices = array(part, "indices", 1572864);
                require(rawIndices.size() > 0 && rawIndices.size()%3 == 0, "三角索引错误");
                for (JsonElement i : rawIndices) indices.add(integral(i, 0, vertices.size()-1));
                for(int i=0;i<indices.size();i+=3) {
                    Vertex a=vertices.get(indices.get(i)), b=vertices.get(indices.get(i+1)), c=vertices.get(indices.get(i+2));
                    double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
                    double cx=uy*vz-uz*vy,cy=uz*vx-ux*vz,cz=ux*vy-uy*vx;
                    require(cx*cx+cy*cy+cz*cz>1e-14,"退化三角面 " + id);
                }
                parts.add(new Part(id,slot,bone,material,side,List.copyOf(vertices),List.copyOf(indices)));
            }
            require(!parts.isEmpty(), "空身形网格");
            profiles.put(name,new Profile(Map.copyOf(origins),List.copyOf(parts)));
        }
        Effects effects = effects(object(root,"effects"), object(root,"effect_bindings"), profiles);
        return new ImmortalMeshData(setId,Map.copyOf(materials),Map.copyOf(profiles),effects);
    }

    private static Effects effects(JsonObject e, JsonObject bindings, Map<String, Profile> profiles) {
        List<Motion> motions = new ArrayList<>(); List<Glow> glows = new ArrayList<>();
        List<Flow> flows = new ArrayList<>(); List<Arc> arcs = new ArrayList<>();
        Set<String> movingTargets=new HashSet<>();
        int level=integer(e,"level",1,5);
        for (JsonElement el : array(e,"motions",3)) {
            JsonObject m = el.getAsJsonObject(); List<String> targets = targets(m,profiles,true);
            for(String target:targets) require(movingTargets.add(target),"动作目标重叠 "+target);
            String axis = text(m,"axis"); require(Set.of("x","y","z").contains(axis),"动作轴错误");
            Vec3 lift=m.has("lift")?vec(m.get("lift")):new Vec3(0,0,0);
            require(Math.abs(lift.x())<=.75f&&Math.abs(lift.y())<=.75f&&Math.abs(lift.z())<=.75f,"动作升降幅度越界");
            motions.add(new Motion(targets,vec(m.get("pivot")),axis,number(m,"angle",-8,8),number(m,"period",4,30),lift));
        }
        for (JsonElement el : array(e,"glows",4)) {
            JsonObject g=el.getAsJsonObject(); float min=number(g,"min",0,.5f), max=number(g,"max",0,.5f);
            require(min<=max,"光效上下限错误");
            glows.add(new Glow(targets(g,profiles,false),color(g),number(g,"period",3,30),min,max));
        }
        JsonArray fs=array(e,"flows",64), fb=array(bindings,"flows",64);
        require(fs.size()==fb.size(),"流光绑定数量错误");
        for(int i=0;i<fs.size();i++) {
            JsonObject f=fs.get(i).getAsJsonObject(); String slot=slot(f), bone=binding(fb.get(i),slot,profiles);
            String shape=text(f,"shape"); require(Set.of("spark","star","seal","leaf").contains(shape),"流光形状错误");
            flows.add(new Flow(slot,bone,path(f,"path"),integer(f,"count",1,16),number(f,"size",.05f,.7f),color(f),
                    number(f,"period",2,30),number(f,"opacity",0,.85f),shape,f.has("trail")?number(f,"trail",0,.15f):0));
        }
        JsonArray as=array(e,"arcs",3), ab=array(bindings,"arcs",3);
        require(as.size()==ab.size(),"弧光绑定数量错误");
        for(int i=0;i<as.size();i++) {
            JsonObject a=as.get(i).getAsJsonObject(); String slot=slot(a),bone=binding(ab.get(i),slot,profiles);
            float period=number(a,"period",3,30), duration=number(a,"duration",.2f,15);
            require(duration<=period*.5f,"弧光时长超过半周期");
            arcs.add(new Arc(slot,bone,path(a,"points"),color(a),period,duration,number(a,"phase",0,1),number(a,"opacity",0,.85f)));
        }
        require(flows.stream().mapToInt(Flow::count).sum()<=new int[]{6,12,18,26,32}[level-1],"境界粒子预算越界");
        return new Effects(level,text(e,"title"),text(e,"description"),List.copyOf(motions),List.copyOf(glows),List.copyOf(flows),List.copyOf(arcs));
    }
    private static String slot(JsonObject o) {String s=text(o,"role"); require(SLOTS.contains(s),"效果槽位错误");return s;}
    private static String binding(JsonElement e,String slot,Map<String,Profile> profiles) {
        String bone=e.getAsString(); require(BONES.contains(bone),"效果骨骼错误");
        for(Profile p:profiles.values()) require(p.parts.stream().anyMatch(part->part.slot.equals(slot)&&part.bone.equals(bone)),"效果绑定未穿戴组件");
        return bone;
    }
    private static List<String> targets(JsonObject o,Map<String,Profile> profiles,boolean oneBone) {
        List<String> result=new ArrayList<>();
        for(JsonElement e:array(o,"targets",128)) {String id=e.getAsString();require(!result.contains(id),"重复动态目标");result.add(id);}
        require(!result.isEmpty(),"缺动态目标");
        for(Profile p:profiles.values()) {
            Set<String> bones=new HashSet<>(), slots=new HashSet<>();
            for(String id:result) {
                Part target=p.parts.stream().filter(part->part.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("未知动态目标 "+id));
                bones.add(target.bone);slots.add(target.slot);
            }
            require(!oneBone || bones.size()==1 && slots.size()==1,"动作跨骨骼或槽位");
        }
        return List.copyOf(result);
    }
    private static List<Vec3> path(JsonObject o,String key) {
        List<Vec3> result=new ArrayList<>();for(JsonElement e:array(o,key,128)) result.add(vec(e));
        require(result.size()>=2,"路径点不足");return List.copyOf(result);
    }
    private static int color(JsonObject o) {String s=text(o,"color");require(s.matches("#[0-9a-fA-F]{6}"),"颜色错误");return Integer.parseInt(s.substring(1),16);}
    private static Vec3 vec(JsonElement e) {JsonArray a=e.getAsJsonArray();require(a.size()==3,"向量长度错误");return new Vec3(scalar(a.get(0),-512,512),scalar(a.get(1),-512,512),scalar(a.get(2),-512,512));}
    private static String text(JsonObject o,String key) {require(o.has(key)&&o.get(key).isJsonPrimitive()&&o.get(key).getAsJsonPrimitive().isString(),"缺字符串 "+key);String s=o.get(key).getAsString();require(!s.isBlank()&&s.length()<=2048,"字符串越界 "+key);return s;}
    private static JsonObject object(JsonObject o,String key) {require(o.has(key)&&o.get(key).isJsonObject(),"缺对象 "+key);return o.getAsJsonObject(key);}
    private static JsonArray array(JsonObject o,String key,int max) {require(o.has(key)&&o.get(key).isJsonArray(),"缺数组 "+key);JsonArray a=o.getAsJsonArray(key);require(a.size()<=max,"数组越界 "+key);return a;}
    private static float number(JsonObject o,String key,float min,float max) {require(o.has(key),"缺数值 "+key);return scalar(o.get(key),min,max);}
    private static float scalar(JsonElement e,float min,float max) {require(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber(),"非法数值");double d=e.getAsDouble();require(Double.isFinite(d)&&d>=min&&d<=max,"数值越界");return (float)d;}
    private static int integer(JsonObject o,String key,int min,int max) {require(o.has(key),"缺整数 "+key);return integral(o.get(key),min,max);}
    private static int integral(JsonElement e,int min,int max) {require(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber(),"非法整数");double d=e.getAsDouble();require(Double.isFinite(d)&&d>=min&&d<=max&&d==Math.rint(d),"整数越界");return (int)d;}
    private static void require(boolean ok,String message) {if(!ok)throw new IllegalArgumentException(message);}
}
