package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 新建独立世界的生成读回；没有修改玩家世界或替代映射行走。 */
@EventBusSubscriber(modid="myvillage")
public final class RoadHubWorldgenEvidence {
    private static boolean done;
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void write(Path path,Object value)throws Exception {Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(value));}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event)throws Exception {
        String folder=System.getenv("MYVILLAGE_ROAD_HUB_OUT");if(folder==null||done)return;
        var out=Path.of(folder);Files.createDirectories(out);var level=event.getServer().overworld();done=true;
        var result=new LinkedHashMap<String,Object>();var selected=List.of("complete_network","generated_junctions","donghuang_real_gate");var completed=new ArrayList<String>();
        result.put("selected",selected);result.put("completed",completed);result.put("mapped_walk","not_verified");result.put("owner_acceptance","not_verified");
        try {
            var generator=WorldPlanRuntime.generator(level).orElseThrow();var world=generator.plan();var layout=generator.roads().await();var rules=generator.roads().rules();
            layout.requireIdentity(world,rules);check(world.cities().size()==8,"八城数量不符");
            check(layout.routes().size()==world.cities().size()-1+world.sectSites().size(),"官道或宗门支路缺失");completed.add("complete_network");
            var centers=RoadJunctionPlanner.centers(layout.routes().stream().map(RoadLayout.Route::points).toList(),rules.junctions.mergeDistance());
            check(!centers.isEmpty(),"实际路网没有枢纽");var samples=new ArrayList<Map<String,Object>>();int columns=0;
            for(var center:centers.stream().limit(3).toList()) {
                var at=layout.at(center.x(),center.z());check(at!=null,"枢纽中心缺少通行面");
                var hub=new RoadJunctionPlanner.Hub(center.x(),center.z(),at.feet2(),rules.junctions.minimumRadius(),3);
                for(var c:RoadJunctionPlanner.pad(hub,rules.junctions.cornerCut())) {
                    var actual=layout.at(c.x(),c.z());check(actual!=null&&actual.band()<2&&actual.feet2()==at.feet2(),"枢纽中心没有统一标高");
                    level.getChunk(c.x()>>4,c.z()>>4);RoadPaver.verify(level,actual,rules.clearance);
                    check(level.getBlockState(new BlockPos(c.x(),actual.floorY(),c.z())).equals(RoadPaver.surface(world,rules,actual)),"实际枢纽路面不符");columns++;
                }
                samples.add(Map.of("x",center.x(),"y",at.feet2()/2.0,"z",center.z()));
            }
            completed.add("generated_junctions");
            var city=world.capitalOf("donghuang").orElseThrow();
            JsonObject manifest;try(var stream=RoadHubWorldgenEvidence.class.getResourceAsStream("/data/myvillage/capitals/v4/capital_donghuang/city.json")) {
                manifest=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
            JsonObject approach=null;for(var row:manifest.getAsJsonArray("access_paths"))if(row.getAsJsonObject().get("building_id").getAsString().equals("lord_manor"))approach=row.getAsJsonObject();
            check(approach!=null&&approach.has("feet2_points"),"缺少真实府门通路");int treads=0;
            var positions=new ArrayList<JsonElement>();approach.getAsJsonArray("surface_columns").forEach(positions::add);approach.getAsJsonArray("curb_columns").forEach(positions::add);
            for(var row:positions) {
                var p=row.getAsJsonArray();int x=city.x()+p.get(0).getAsInt(),z=city.z()+p.get(2).getAsInt();double feet=city.originY()+p.get(1).getAsInt()/2.0;
                level.getChunk(x>>4,z>>4);
                check(level.getBlockCollisions(null,new AABB(x+.2,feet-.02,z+.2,x+.8,feet,z+.8)).iterator().hasNext(),"府前踏步缺少真实碰撞支撑 "+x+","+feet+","+z);
                check(level.noCollision(null,new AABB(x+.2,feet+.001,z+.2,x+.8,feet+3-.001,z+.8)),"府前踏步的身体或头部被挡 "+x+","+feet+","+z);treads++;
            }
            completed.add("donghuang_real_gate");result.put("pass",true);result.put("failed",List.of());result.put("unrun",List.of());
            result.put("seed",world.seed());result.put("world_hash",world.fingerprint());result.put("roads_hash",rules.fingerprint);result.put("layout_hash",layout.hash());
            result.put("routes",layout.routes().size());result.put("hubs",centers.size());result.put("sample_hubs",samples);result.put("checked_hub_columns",columns);result.put("checked_manor_treads",treads);
            var portal=approach.getAsJsonArray("portal");result.put("manor",Map.of("x",city.x()+portal.get(0).getAsInt(),"y",city.originY()+portal.get(1).getAsInt(),"z",city.z()+portal.get(2).getAsInt()));
        } catch(Throwable error) {
            result.put("pass",false);result.put("failed",List.of(error.toString()));result.put("unrun",selected.stream().filter(s->!completed.contains(s)).toList());error.printStackTrace();
        }
        write(out.resolve("server-result.json"),result);
        if(System.getenv("MYVILLAGE_ROAD_HUB_WAIT_CLIENT")==null)event.getServer().halt(false);
    }
}
