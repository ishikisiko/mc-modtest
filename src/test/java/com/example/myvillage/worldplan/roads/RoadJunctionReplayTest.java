package com.example.myvillage.worldplan.roads;

import com.google.gson.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;

/** 重放完整寻路后保留下来的真实输入；失败不会以缩路幅或改土方上限放行。 */
class RoadJunctionReplayTest {
    static java.util.stream.Stream<Path> capturedInputs(){return Arrays.stream(System.getProperty("myvillage.road.junction.replay").split(",")).map(Path::of);}
    @ParameterizedTest @MethodSource("capturedInputs") @EnabledIfSystemProperty(named="myvillage.road.junction.replay",matches=".+")
    void capturedNetworkStillKeepsEveryHubAndOriginalCarriageColumn(Path captured)throws Exception {
        JsonObject input;
        try(var stream=new GZIPInputStream(Files.newInputStream(captured))){
            input=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var routes=List.of(new Gson().fromJson(input.get("routes"),RoadPlan[].class));
        Map<Long,Integer> heights=new HashMap<>();
        for(var value:input.getAsJsonArray("heights")){var p=value.getAsJsonArray();heights.put(RoadPlan.Cell.key(p.get(0).getAsInt(),p.get(1).getAsInt()),p.get(2).getAsInt());}
        com.example.myvillage.worldplan.WorldPlan world;
        try(var stream=getClass().getResourceAsStream("/data/myvillage/worldplan/frozen_v11.json")){
            world=new com.example.myvillage.worldplan.WorldPlan(input.get("seed").getAsLong(),new com.example.myvillage.worldplan.WorldPlanRules(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));
        }
        var rules=RoadRules.load();var permission=RoadJunctionPlanner.permission(world,rules);
        var result=RoadJunctionPlanner.prepare(routes,List.of(),rules,(x,z)->{var h=heights.get(RoadPlan.Cell.key(x,z));return h!=null?h:Math.max(world.rules().seaLevel+1,world.surfaceY(x,z));},permission);
        for(var route:result.routes())for(var c:route.cells())if(!c.existing()&&!heights.containsKey(c.key()))assertTrue(permission.permits(route,c.x(),c.z()),"新平台和外缘必须逐列取得原路线的写入权限");
        Map<Long,RoadPlan.Cell> body=new HashMap<>(),gates=new HashMap<>();
        for(var route:result.routes())for(var c:route.cells())if(c.existing())gates.put(c.key(),c);else if(c.band()<2)body.put(c.key(),c);
        for(var route:routes)for(var c:route.cells())if(c.existing())assertEquals(c,gates.get(c.key()),"原只读门阶的所有列必须保持不变");else if(c.band()<2)assertNotNull(body.get(c.key()),"原通行面不能丢失");
        for(var hub:result.hubs())for(var c:RoadJunctionPlanner.pad(hub,rules.junctions.cornerCut()))assertEquals(hub.feet2(),body.get(c.key()).feet2());
        // 城内原阶梯沿用真实楼梯构件；半砖坡度约束覆盖所有新路列及其只读门阶接缝。
        for(var c:body.values())for(var d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){long key=RoadPlan.Cell.key(c.x()+d[0],c.z()+d[1]);var n=body.getOrDefault(key,gates.get(key));if(n!=null)assertTrue(Math.abs(n.feet2()-c.feet2())<=1,c+" -> "+n);}
        var network=new RoadNetworkPlanner.Result(result.routes(),RoadNetworkGeometry.merge(world,rules,result.routes(),List.of()),result.hubs());
        var layout=RoadLayout.from(world,rules,network);layout.requireIdentity(world,rules);
        var encoded=RoadLayout.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,layout).getOrThrow();
        var read=RoadLayout.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow();read.requireIdentity(world,rules);assertEquals(layout.hash(),read.hash());assertEquals(result.hubs(),read.hubs());
        var report=Map.of("seed",input.get("seed").getAsLong(),"hubs",result.hubs(),"carriage_columns",body.size(),"layout_hash",layout.hash());
        Path out=Path.of("build/reports/road-junction-replay-"+input.get("seed").getAsLong()+".json");Files.createDirectories(out.getParent());Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
