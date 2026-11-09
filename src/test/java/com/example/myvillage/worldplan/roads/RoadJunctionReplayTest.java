package com.example.myvillage.worldplan.roads;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;

/** 重放完整寻路后保留下来的真实输入；失败不会以缩路幅或改土方上限放行。 */
class RoadJunctionReplayTest {
    @Test @EnabledIfSystemProperty(named="myvillage.road.junction.replay",matches=".+")
    void capturedNetworkStillKeepsEveryHubAndOriginalCarriageColumn()throws Exception {
        JsonObject input;
        try(var stream=new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("myvillage.road.junction.replay"))))){
            input=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var routes=List.of(new Gson().fromJson(input.get("routes"),RoadPlan[].class));
        Map<Long,Integer> heights=new HashMap<>();
        for(var value:input.getAsJsonArray("heights")){var p=value.getAsJsonArray();heights.put(RoadPlan.Cell.key(p.get(0).getAsInt(),p.get(1).getAsInt()),p.get(2).getAsInt());}
        var rules=RoadRules.load();var result=RoadJunctionPlanner.prepare(routes,List.of(),rules,(x,z)->Objects.requireNonNull(heights.get(RoadPlan.Cell.key(x,z)),"缺少已记录的地形列"));
        Map<Long,RoadPlan.Cell> body=new HashMap<>();
        for(var route:result.routes())for(var c:route.cells())if(c.band()<2)body.put(c.key(),c);
        for(var route:routes)for(var c:route.cells())if(c.band()<2)assertNotNull(body.get(c.key()),"原通行面不能丢失");
        for(var hub:result.hubs())for(var c:RoadJunctionPlanner.pad(hub,rules.junctions.cornerCut()))assertEquals(hub.feet2(),body.get(c.key()).feet2());
        for(var c:body.values())for(var d:new int[][]{{1,0},{0,1}}){var n=body.get(RoadPlan.Cell.key(c.x()+d[0],c.z()+d[1]));if(n!=null)assertTrue(Math.abs(n.feet2()-c.feet2())<=1);}
        var report=Map.of("seed",input.get("seed").getAsLong(),"hubs",result.hubs(),"carriage_columns",body.size());
        Path out=Path.of("build/reports/road-junction-replay-result.json");Files.createDirectories(out.getParent());Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
