package com.example.myvillage.manor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TownSurfaceAssetsTest {
    private static byte[] bytes(String path) throws Exception {
        try(var in=TownSurfaceAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in,path);return in.readAllBytes();
        }
    }
    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(new String(bytes(path),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    @Test void everyTownMaterialClosesItemTextureLootAndDirectionalStateResources() throws Exception {
        int total=0,directional=0;
        var language=json("/assets/myvillage/lang/zh_cn.json");
        for(var element:json("/data/myvillage/lord_manors/catalog.json").getAsJsonArray("materials")) {
            var material=element.getAsJsonObject();if(!material.has("pattern"))continue;
            var id=material.get("id").getAsString();var root="/assets/myvillage/";
            assertEquals(material.get("name").getAsString(),language.get("block.myvillage."+id).getAsString());
            assertEquals("myvillage:block/"+id,json(root+"models/item/"+id+".json").get("parent").getAsString());
            var model=json(root+"models/block/"+id+".json");
            assertEquals("minecraft:solid",model.get("render_type").getAsString());
            var texture=model.getAsJsonObject("textures").get("all").getAsString().substring("myvillage:".length());
            var png=bytes(root+"textures/"+texture+".png");
            assertTrue(png.length>100,id);assertEquals(0x89,png[0]&255);assertEquals('P',png[1]);
            var variants=json(root+"blockstates/"+id+".json").getAsJsonObject("variants");
            if(material.get("directional").getAsBoolean()) {
                int angle=0;
                for(var facing:new String[]{"north","east","south","west"}) {
                    var state=variants.getAsJsonObject("facing="+facing);
                    assertNotNull(state,id+" "+facing);assertEquals(angle,state.get("y").getAsInt());angle+=90;
                }
                assertEquals(4,variants.size());directional++;
            }else assertEquals(1,variants.size());
            var loot=json("/data/myvillage/loot_table/blocks/"+id+".json");
            assertEquals("myvillage:"+id,loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
            total++;
        }
        assertEquals(23,total);assertTrue(directional>0);
    }

    @Test void everyDefaultRegionalCityActuallyUsesItsDeclaredStreetSurfaces() throws Exception {
        var names=new HashSet<String>();
        for(var region:new String[]{"beiyuan","nanjiang","lingyue","ximo","donghai","donghuang","moyu"}) {
            var city=json("/data/myvillage/capitals/v4/capital_"+region+"/city.json");
            var paving=city.getAsJsonObject("paving");assertNotNull(paving,region);
            names.add(paving.get("profile").getAsString());
            var counts=paving.getAsJsonObject("actual_surface_counts");assertTrue(counts.size()>1,region);
            int cells=0;
            for(var entry:counts.entrySet())cells+=entry.getValue().getAsInt();
            assertEquals(city.getAsJsonObject("blueprint").getAsJsonObject("construction_audit").get("road_columns").getAsInt(),cells);
            assertTrue(counts.keySet().stream().anyMatch(k->k.startsWith("myvillage:manor_town_")),region);
        }
        assertEquals(7,names.size());
    }
}
