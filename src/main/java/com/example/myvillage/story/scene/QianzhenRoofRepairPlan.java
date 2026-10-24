package com.example.myvillage.story.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One reviewed pavilion patch; pure preflight/transaction logic, without world loading or save mutation. */
final class QianzhenRoofRepairPlan {
    record Pos(int x, int y, int z) {
        Pos add(Pos p) { return new Pos(Math.addExact(x,p.x),Math.addExact(y,p.y),Math.addExact(z,p.z)); }
        String coordinates() { return x+" "+y+" "+z; }
    }
    record Site(String id, String template, Pos origin, Pos size, String sha256) {}
    record Receipt(boolean complete, String id, String version, String dimension, String actualDimension,
                   Pos origin, String layoutId, String layoutVersion, Site site) {}
    record Target(Pos pos, String before, String after, String reason) {}
    record Guard(Pos pos, String state) {}
    record Change(Pos pos, String before, String after, String reason) {}
    interface World {
        boolean loaded(Pos pos);
        boolean matches(Pos pos, String state);
        boolean hasBlockEntity(Pos pos);
        boolean write(Pos pos, String state);
    }
    private final Map<String,Site> supported;
    private final Map<String,String> unsupported;
    private final List<Target> targets;
    private final List<Guard> guards;

    private QianzhenRoofRepairPlan(Map<String,Site> supported, Map<String,String> unsupported,
                                  List<Target> targets, List<Guard> guards) {
        this.supported=Map.copyOf(supported); this.unsupported=Map.copyOf(unsupported);
        this.targets=List.copyOf(targets); this.guards=List.copyOf(guards);
    }
    static QianzhenRoofRepairPlan parse(JsonObject root) {
        require(integer(root.get("schema"))==1 && text(root,"id").equals("qianzhen_sample_pavilion_supports_v1")
                && text(root,"scene_id").equals("qianzhen_chapter_1") && text(root,"site_id").equals("canal_gardens"), "屋架补丁身份非法");
        require(pos(root.getAsJsonObject("bounds").get("min")).equals(new Pos(31,0,7))
                && pos(root.getAsJsonObject("bounds").get("max")).equals(new Pos(44,10,15)), "屋架补丁范围非法");
        Map<String,Site> supported=new HashMap<>();
        for (JsonElement element:root.getAsJsonArray("supported")) {
            JsonObject row=element.getAsJsonObject(), s=row.getAsJsonObject("site");
            Site site=new Site(text(s,"id"),text(s,"template"),pos(s.get("origin")),pos(s.get("size")),text(s,"sha256"));
            require(site.id.equals("canal_gardens") && site.template.equals("myvillage:qianzhen/canal_gardens")
                    && site.origin.equals(new Pos(122,0,65)) && site.size.equals(new Pos(48,22,47))
                    && site.sha256.equals("f9c90f21a201cec9da1f05617b2c7a2f2323c78ecc9d453fc18d80331bc4dcb8"), "补丁来源地点身份非法");
            require(supported.put(text(row,"version"),site)==null,"重复补丁版本");
        }
        require(supported.keySet().equals(Set.of("1-f795e60913419e05","1-56da335c3dd1262f")),"补丁支持版本非法");
        Map<String,String> unsupported=new HashMap<>();
        for (JsonElement element:root.getAsJsonArray("unsupported")) {
            var row=element.getAsJsonObject(); require(unsupported.put(text(row,"version"),text(row,"reason"))==null,"重复不支持版本");
        }
        require(unsupported.keySet().equals(Set.of("1-93b7ddbc5c59392e")),"补丁拒绝版本非法");
        List<Target> targets=new ArrayList<>(); List<Guard> guards=new ArrayList<>(); Set<Pos> cells=new HashSet<>();
        for(JsonElement element:root.getAsJsonArray("targets")) {
            var row=element.getAsJsonObject(); Pos p=pos(row.get("pos")); String before=text(row,"before"),after=text(row,"after");
            require(inside(p) && cells.add(p) && p.x>=33 && p.x<=43 && p.y>=6 && p.y<=9 && p.z>=9 && p.z<=13
                    && before.equals("minecraft:air") && after.matches("minecraft:spruce_log\\[axis=[xyz]\\]"),"补丁目标不是有界空气→木支架");
            targets.add(new Target(p,before,after,text(row,"reason")));
        }
        require(targets.size()==45,"补丁目标数量非法");
        for(JsonElement element:root.getAsJsonArray("guards")) {
            var row=element.getAsJsonObject(); Pos p=pos(row.get("pos")); String state=text(row,"state");
            require(inside(p) && cells.add(p) && state.matches("minecraft:[a-z0-9_]+(?:\\[[a-z0-9_=,.-]+])?"),"保护格重复/越界/状态非法");
            guards.add(new Guard(p,state));
        }
        require(cells.size()==14*11*9,"亭子保护格覆盖不完整");
        return new QianzhenRoofRepairPlan(supported,unsupported,targets,guards);
    }
    private Site identity(Receipt receipt) {
        require(receipt.complete,"场景记录未完成，未写入任何方块");
        require(receipt.id.equals("qianzhen_chapter_1") && receipt.id.equals(receipt.layoutId)
                && receipt.version.equals(receipt.layoutVersion),"场景记录与冻结布局身份不符");
        require(!receipt.dimension.isBlank() && receipt.dimension.equals(receipt.actualDimension),"当前真实维度与场景记录不符");
        requireSupportedVersion(receipt.version);
        Site expected=supported.get(receipt.version);
        require(expected.equals(receipt.site),"冻结渠圃 origin/size/template/摘要与补丁来源不符");
        return expected;
    }
    void requireSupportedVersion(String version) {
        require(supported.containsKey(version),"unsupported："+version+"；"+unsupported.getOrDefault(version,"没有该精确版本的已证明补丁来源"));
    }
    List<Change> preview(Receipt receipt, World world) {
        Site site=identity(receipt); Pos origin=receipt.origin.add(site.origin);
        // Complete loaded-chunk pass precedes every block read; never generate or request a chunk.
        for(Guard g:guards) require(world.loaded(origin.add(g.pos)),"保护格区块未加载 @ "+origin.add(g.pos).coordinates());
        for(Target t:targets) require(world.loaded(origin.add(t.pos)),"目标区块未加载 @ "+origin.add(t.pos).coordinates());
        for(Guard g:guards) {
            Pos p=origin.add(g.pos); require(world.matches(p,g.state),"保护格已改建或未知差异 @ "+p.coordinates()+"；预期 "+g.state);
        }
        List<Change> changes=new ArrayList<>();
        for(Target t:targets) {
            Pos p=origin.add(t.pos);
            require(!world.hasBlockEntity(p),"目标含方块实体 @ "+p.coordinates());
            if(world.matches(p,t.after)) continue;
            require(world.matches(p,t.before),"目标已改建或未知差异 @ "+p.coordinates()+"；仅允许原空气或最终支架");
            changes.add(new Change(p,t.before,t.after,t.reason));
        }
        return List.copyOf(changes);
    }
    int apply(Receipt receipt, World world) {
        List<Change> changes=preview(receipt,world); // never reuse a prior preview result
        List<Change> attempted=new ArrayList<>();
        try {
            for(Change change:changes) {
                attempted.add(change); // a failing write may have changed the cell before throwing
                require(world.write(change.pos,change.after) && world.matches(change.pos,change.after),"支架写入失败 @ "+change.pos.coordinates());
            }
            require(preview(receipt,world).isEmpty(),"写入后完整复核失败");
            return changes.size();
        } catch(RuntimeException failure) {
            boolean restored=true;
            for(int i=attempted.size()-1;i>=0;i--) {
                Change change=attempted.get(i);
                try {
                    if(!world.matches(change.pos,change.before)) world.write(change.pos,change.before);
                    require(world.matches(change.pos,change.before),"回滚状态不符 @ "+change.pos.coordinates());
                } catch(RuntimeException rollback) { restored=false; failure.addSuppressed(rollback); }
            }
            throw new IllegalStateException(restored ? "修复异常，本次尝试写入的方块均已回滚；诊断见服务器日志"
                    : "修复异常且回滚未完整完成；请保留副本和服务器诊断",failure);
        }
    }
    private static boolean inside(Pos p) { return p.x>=31 && p.x<=44 && p.y>=0 && p.y<=10 && p.z>=7 && p.z<=15; }
    private static Pos pos(JsonElement value) {
        JsonArray a=value.getAsJsonArray(); require(a.size()==3,"坐标必须为三整数");
        return new Pos(integer(a.get(0)),integer(a.get(1)),integer(a.get(2)));
    }
    private static int integer(JsonElement value) {
        require(value!=null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),"坐标/版本不是整数");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch(ArithmeticException bad) { throw new IllegalArgumentException("整数越界",bad); }
    }
    private static String text(JsonObject raw,String key) {
        JsonElement value=raw.get(key);
        require(value!=null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),"缺少补丁文本："+key);
        String text=value.getAsString(); require(!text.isBlank() && text.length()<=512,"补丁文本非法"); return text;
    }
    private static void require(boolean condition,String reason) { if(!condition)throw new IllegalStateException(reason); }
}
