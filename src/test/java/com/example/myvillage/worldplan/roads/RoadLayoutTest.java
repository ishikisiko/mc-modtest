package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoadLayoutTest {
    private static final String WORLD="a".repeat(64),RULES="b".repeat(64);
    private static RoadLayout.Route route(){return new RoadLayout.Route("nanjiang","ximo",new int[]{-20,35,4,35});}
    private static RoadLayout layout(RoadPlan.Cell... cells){var by=new TreeMap<Long,List<Integer>>();for(var c:cells)by.computeIfAbsent(c.chunk(),k->new ArrayList<>()).add(RoadLayout.pack(c));return new RoadLayout(WORLD,RULES,by.entrySet().stream().map(e->new RoadLayout.Slice(e.getKey(),e.getValue().stream().mapToInt(Integer::intValue).toArray())).toList(),List.of(route()));}
    @Test void packedColumnsRetainNegativeCoordinatesHalfStepsAndAllRailStates(){
        for(int feet:new int[]{-128,0,159,160,641})for(int mask=-1;mask<=255;mask++){
            if(mask>=0&&((mask>>>4)&~mask)!=0)continue;
            var c=new RoadPlan.Cell(-17,35,feet,false,mask<0?3:2,mask);if(mask<0)c=new RoadPlan.Cell(-17,35,feet,false,0);
            var l=layout(c);assertEquals(c,l.at(-17,35));assertEquals(List.of(c),l.slice(c.chunk()).cells());assertNull(l.at(-16,35));
        }
    }
    @Test void codecRoundTripRetainsDigestAndDefensivelyOwnsItsArrays(){
        var c=new RoadPlan.Cell(-17,35,173,false,2,85);int[] encoded={RoadLayout.pack(c)};var slice=new RoadLayout.Slice(c.chunk(),encoded);encoded[0]=0;
        var original=new RoadLayout(WORLD,RULES,List.of(slice),List.of(route()));JsonElement json=RoadLayout.CODEC.encodeStart(JsonOps.INSTANCE,original).getOrThrow();var read=RoadLayout.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();assertEquals(original.hash(),read.hash());assertEquals(c,read.at(c.x(),c.z()));assertEquals(original.routes().getFirst().points(),read.routes().getFirst().points());
    }
    @Test void nbtCacheUsesIntArraysAndRetainsSignedChunkCoordinates(){
        var c=new RoadPlan.Cell(-17,-35,159,false,2,85);var original=layout(c);
        var encoded=RoadLayout.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,original).getOrThrow();
        var tag=(net.minecraft.nbt.CompoundTag)encoded;
        assertInstanceOf(net.minecraft.nbt.IntArrayTag.class,tag.getList("chunks",10).getCompound(0).get("columns"));
        var read=RoadLayout.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,encoded).getOrThrow();
        assertEquals(original.hash(),read.hash());assertEquals(c,read.at(c.x(),c.z()));
    }
    @Test void cacheRetainsRelocatedHubAndRejectsAFalsePlatformRecord(){
        var rules=RoadRules.load();var hub=new RoadJunctionPlanner.Hub(-19,35,159,4,3);
        var columns=RoadJunctionPlanner.pad(hub,rules.junctions.cornerCut());var by=new TreeMap<Long,List<Integer>>();
        for(var c:columns)by.computeIfAbsent(c.chunk(),k->new ArrayList<>()).add(RoadLayout.pack(c));
        var slices=by.entrySet().stream().map(e->new RoadLayout.Slice(e.getKey(),e.getValue().stream().mapToInt(Integer::intValue).toArray())).toList();
        var layout=new RoadLayout(WORLD,RULES,slices,List.of(route()),List.of(hub));
        JsonObject json=RoadLayout.CODEC.encodeStart(JsonOps.INSTANCE,layout).getOrThrow().getAsJsonObject();
        var read=RoadLayout.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();assertEquals(List.of(hub),read.hubs());assertEquals(layout.hash(),read.hash());
        json.getAsJsonArray("hubs").get(0).getAsJsonObject().addProperty("x",hub.x()+1);
        assertTrue(RoadLayout.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
        assertThrows(IllegalArgumentException.class,()->new RoadLayout(WORLD,RULES,slices,List.of(route()),List.of(new RoadJunctionPlanner.Hub(hub.x(),hub.z(),hub.feet2()+1,hub.radius(),hub.branches()))));
    }
    @Test void corruptedCacheCannotSilentlyMoveOrDeleteRoadColumns(){
        var original=layout(new RoadPlan.Cell(-17,35,160,false));JsonObject json=RoadLayout.CODEC.encodeStart(JsonOps.INSTANCE,original).getOrThrow().getAsJsonObject();json.getAsJsonArray("chunks").get(0).getAsJsonObject().getAsJsonArray("columns").set(0,new JsonPrimitive(RoadLayout.pack(new RoadPlan.Cell(-17,35,162,false))));assertTrue(RoadLayout.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
        assertThrows(IllegalArgumentException.class,()->new RoadLayout.Slice(0,new int[]{0,0}));assertThrows(IllegalArgumentException.class,()->new RoadLayout.Slice(0,new int[]{-1}));assertThrows(IllegalArgumentException.class,()->RoadLayout.pack(new RoadPlan.Cell(0,0,160,true)));
    }
    @Test void nonRoadColumnsStayAbsentEvenInsideTheSameChunk(){var c=new RoadPlan.Cell(1,-1,160,false);var l=layout(c);assertNull(l.at(2,-1));assertNull(l.at(1,0));assertEquals(1,l.columns());}
    @Test void cacheWithWrongWorldOrIncompleteConnectivityIsRejected()throws Exception {
        WorldPlan world;try(var in=getClass().getResourceAsStream(WorldPlanRules.RESOURCE)){world=new WorldPlan(-6259944962606258298L,new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8)));}
        var rules=RoadRules.load();assertThrows(IllegalStateException.class,()->layout(new RoadPlan.Cell(1,1,160,false)).requireIdentity(world,rules));
        assertThrows(IllegalStateException.class,()->RoadLayout.empty(world,rules).requireIdentity(world,rules));
        var generation=new RoadGeneration(Optional.empty());generation.bind(world);assertTrue(generation.snapshot().isEmpty());assertEquals("idle",generation.progress().phase());generation.close();assertThrows(java.util.concurrent.CancellationException.class,generation::await);
    }
}
