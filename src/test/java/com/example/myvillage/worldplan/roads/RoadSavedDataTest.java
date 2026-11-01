package com.example.myvillage.worldplan.roads;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RoadSavedDataTest {
    static RoadPlan route(String from,String to,List<RoadPlan.Cell> cells){return new RoadPlan(from,to,"world","rules",5,Map.of(from,3,to,5),List.of(),List.of(new RoadAStar.Point(-1,-1),new RoadAStar.Point(2,2)),cells);}
    @Test void chunkPackingMatchesMinecraftForAsymmetricNegativeCoordinates(){var c=new RoadPlan.Cell(-17,35,160,false);var p=new net.minecraft.world.level.ChunkPos(c.chunk());assertEquals(-2,p.x);assertEquals(2,p.z);}
    @Test void restartPreservesUnorderedProgressAndExplicitPause(){
        var d=new RoadSavedData();var cells=List.of(new RoadPlan.Cell(-1,-1,171,true),new RoadPlan.Cell(2,2,173,false,2,85),new RoadPlan.Cell(80,2,172,false));
        var p=route("nanjiang","ximo",cells);d.stage(List.of(p),cells);long last=d.job.writeChunks.get(1).getKey();d.job.markWritten(last);d.manualPause=true;d.phase="paused";
        var loaded=RoadSavedData.load(d.save(new CompoundTag(),null),null);
        assertEquals(p,loaded.selected().plan);assertEquals("paused",loaded.phase);assertTrue(loaded.manualPause);assertEquals(Set.of(last),loaded.job.completed);assertEquals(cells,loaded.job.target);
    }
    @Test void automaticJobResumesAndBlockedChunkIsRetained(){
        var d=new RoadSavedData();var cells=List.of(new RoadPlan.Cell(2,2,172,false),new RoadPlan.Cell(80,2,172,false));d.stage(List.of(route("nanjiang","ximo",cells)),cells);
        long key=d.job.writeChunks.get(0).getKey();d.job.blocked.put(key,"箱子");var loaded=RoadSavedData.load(d.save(new CompoundTag(),null),null);
        assertEquals("building",loaded.phase);assertEquals("箱子",loaded.job.blocked.get(key));assertTrue(loaded.job.completed.isEmpty());
    }
    @Test void batchCommitCompletesAllRoutesTogether(){
        var d=new RoadSavedData();var a=new RoadPlan.Cell(2,2,172,false);var b=new RoadPlan.Cell(80,2,172,false);var p=route("nanjiang","ximo",List.of(a));var q=route("ximo","zhongzhou",List.of(b));
        d.stage(List.of(p,q),List.of(a,b));assertThrows(IllegalStateException.class,d::completeJob);
        for(var c:d.job.writeChunks)d.job.markWritten(c.getKey());d.completeJob();
        var loaded=RoadSavedData.load(d.save(new CompoundTag(),null),null);assertEquals("complete",loaded.phase);assertEquals(2,loaded.completedPlans().size());assertNull(loaded.job);assertEquals(List.of(a,b),loaded.committed);
    }
    @Test void truncatedFootprintAndUnknownProgressAreRejected(){
        var d=new RoadSavedData();var badTag=d.save(new CompoundTag(),null);badTag.putIntArray("committed",new int[]{1,2,3});assertThrows(IllegalStateException.class,()->RoadSavedData.load(badTag,null));
        var cells=List.of(new RoadPlan.Cell(2,2,172,false));d.stage(List.of(route("nanjiang","ximo",cells)),cells);var tag=d.save(new CompoundTag(),null);tag.getCompound("job").putLongArray("completed",new long[]{999});final var corrupt=tag;assertThrows(IllegalStateException.class,()->RoadSavedData.load(corrupt,null));
    }
}
