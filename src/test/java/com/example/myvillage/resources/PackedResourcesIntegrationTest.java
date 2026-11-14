package com.example.myvillage.resources;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedResourcesIntegrationTest {
    @Test void everyActualModelStructureAndLinkedResourceRestoresOriginalBytes()throws Exception{
        int models=0,structures=0,linked=0,otherModels=0,expected=0,banks=0;
        Set<String> linkedPaths=new HashSet<>();
        for(String path:new String[]{"/data/myvillage/lord_manors/catalog.json","/data/myvillage/qianji/components/catalog.json"}){
            try(var in=BuiltinResources.open(path)){
                var catalog=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                for(var row:catalog.getAsJsonArray("banks")){
                    var bank=row.getAsJsonObject();banks++;expected+=bank.getAsJsonArray("profiles").size();String id=bank.get("id").getAsString();
                    linkedPaths.add("assets/myvillage/blockstates/"+id+".json");linkedPaths.add("assets/myvillage/models/item/"+id+".json");linkedPaths.add("data/myvillage/loot_table/blocks/"+id+".json");
                }
            }
        }
        try(var store=PackedResourceStore.classpath()){
            for(var entry:store.entries().values()){
                assertNull(getClass().getResourceAsStream("/"+entry.path()),"不能同时打包展开副本："+entry.path());
                try(var in=store.open(entry.path())){
                    assertNotNull(in,entry.path());byte[] value=in.readAllBytes();
                    assertEquals(entry.bytes(),value.length,entry.path());assertEquals(entry.sha256(),PackedResourceStore.sha(value),entry.path());
                    if(entry.path().contains("/models/block/")&&entry.path().contains("/shape_")){
                        var json=JsonParser.parseString(new String(value,StandardCharsets.UTF_8)).getAsJsonObject();assertTrue(json.has("parent"),entry.path());models++;
                    }else if(entry.path().endsWith(".nbt")){assertEquals(0x1f,value[0]&255);assertEquals(0x8b,value[1]&255);structures++;}
                    else if(linkedPaths.contains(entry.path()))linked++;
                    else{
                        assertTrue(entry.path().startsWith("assets/myvillage/models/"),entry.path());
                        assertTrue(entry.path().endsWith(".json")||entry.path().endsWith(".obj")||entry.path().endsWith(".mtl"),entry.path());otherModels++;
                    }
                }
            }
            assertEquals(expected,models);assertEquals(banks*3,linked);assertEquals(8371,structures);
            assertEquals(2688,otherModels);
            assertTrue(store.stats().get("cached_bytes")<=33554432);assertTrue(store.stats().get("cached_groups")<=256);
        }
    }
}
