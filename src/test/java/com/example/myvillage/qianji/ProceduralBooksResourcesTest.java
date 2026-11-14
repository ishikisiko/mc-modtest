package com.example.myvillage.qianji;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceduralBooksResourcesTest {
    private JsonObject read(String path)throws Exception{
        try(var in=getClass().getResourceAsStream(path)){
            assertNotNull(in,path);return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void packagedBookGeometryContainsOnlyRegionsAndSharedAlgorithmParameters()throws Exception {
        var catalog=read("/data/myvillage/qianji/components/catalog.json");
        int procedural=0;var rules=ProceduralBooks.rules();
        for(var entry:catalog.getAsJsonArray("banks")){
            var bank=entry.getAsJsonObject();int shape=0;
            for(var value:bank.getAsJsonArray("profiles")){
                var profile=value.getAsJsonObject();List<ProceduralBooks.Region> expected=new ArrayList<>();
                for(var box:profile.getAsJsonArray("boxes")){
                    var b=box.getAsJsonArray();String role=b.get(6).getAsString();assertFalse(rules.roles().contains(role),"逐本书几何不能回到目录");
                    if(role.equals(rules.markerRole())){
                        var a=new JsonArray();for(int i:new int[]{0,1,2,3,4,5,7,8})a.add(b.get(i));
                        expected.add(ProceduralBooks.Region.fromJson(a));
                    }
                }
                if(!expected.isEmpty()){
                    var model=read("/assets/myvillage/models/block/"+bank.get("id").getAsString()+"/shape_"+shape+".json");
                    assertEquals("myvillage:procedural_books",model.get("loader").getAsString());
                    assertTrue(model.has("base_model"));
                    assertEquals(expected,model.getAsJsonArray("book_regions").asList().stream().map(v->ProceduralBooks.Region.fromJson(v.getAsJsonArray())).toList());
                    for(var region:expected)for(int seed=0;seed<rules.patterns();seed++)for(var b:ProceduralBooks.generate(region,seed,rules)){
                        assertTrue(b.x0()>=region.x0()&&b.x1()<=region.x1());
                        assertTrue(b.y0()>=region.y0()&&b.y1()<=region.y1());
                        assertTrue(b.z0()>=region.z0()&&b.z1()<=region.z1());
                    }
                    procedural++;
                }
                shape++;
            }
        }
        assertTrue(procedural>0,"必须覆盖实际总舵书架");
        assertTrue(catalog.getAsJsonArray("materials").asList().stream().map(JsonElement::getAsJsonObject)
                .noneMatch(v->v.get("role").getAsString().equals(rules.markerRole())),"排列区域不能注册成独立材料方块");
    }
}
