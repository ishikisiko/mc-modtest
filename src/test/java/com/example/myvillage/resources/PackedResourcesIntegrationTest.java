package com.example.myvillage.resources;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedResourcesIntegrationTest {
    @Test void everyActualModelStructureAndLinkedResourceRestoresOriginalBytes()throws Exception{
        int models=0,structures=0,linked=0;
        try(var store=PackedResourceStore.classpath()){
            for(var entry:store.entries().values()){
                assertNull(getClass().getResourceAsStream("/"+entry.path()),"不能同时打包展开副本："+entry.path());
                try(var in=store.open(entry.path())){
                    assertNotNull(in,entry.path());byte[] value=in.readAllBytes();
                    assertEquals(entry.bytes(),value.length,entry.path());assertEquals(entry.sha256(),PackedResourceStore.sha(value),entry.path());
                    if(entry.path().contains("/models/block/")&&entry.path().contains("/shape_")){
                        var json=JsonParser.parseString(new String(value,StandardCharsets.UTF_8)).getAsJsonObject();assertTrue(json.has("parent"),entry.path());models++;
                    }else if(entry.path().endsWith(".nbt")){assertEquals(0x1f,value[0]&255);assertEquals(0x8b,value[1]&255);structures++;}
                    else linked++;
                }
            }
            int expected=0,banks=0;
            for(String path:new String[]{"/data/myvillage/lord_manors/catalog.json","/data/myvillage/qianji/components/catalog.json"}){
                try(var in=BuiltinResources.open(path)){
                    var catalog=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                    for(var bank:catalog.getAsJsonArray("banks")){banks++;expected+=bank.getAsJsonObject().getAsJsonArray("profiles").size();}
                }
            }
            assertEquals(expected,models);assertEquals(banks*3,linked);assertEquals(8371,structures);
            assertTrue(store.stats().get("cached_bytes")<=33554432);assertTrue(store.stats().get("cached_groups")<=256);
        }
    }
}
