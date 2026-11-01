package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;
import static org.junit.jupiter.api.Assertions.*;

class RoadGradeAvoidanceTest {
    private static RoadRules rules;
    @BeforeAll static void prepare(){rules=RoadRules.load();}
    private static RoadPlan route(List<Cell> cells){return RoadSavedDataTest.route("nanjiang","ximo",cells);}
    @Test void ownerFenceInOuterGradingCanBeKeptWithoutNarrowingTheCarriage()throws Exception {
        WorldPlan world;try(var in=getClass().getResourceAsStream(WorldPlanRules.RESOURCE)){assertNotNull(in);world=new WorldPlan(-6259944962606258298L,new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8)));}
        assertEquals("87fc5fd86a4f5d7d9753b84b051d3e90a899be4c981b9d6dfae804ddac772dbf",world.fingerprint());
        assertEquals(73,world.surfaceY(-3790,5295));
        var line=RoadGeometry.densify(List.of(new RoadAStar.Point(-3779,5293),new RoadAStar.Point(-3788,5284)));
        Map<Long,Cell> body=new LinkedHashMap<>();for(var p:line)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){var c=new Cell(p.x()+dx,p.z()+dz,147,false);body.put(c.key(),c);}
        var styled=RoadOfficialStyle.apply(List.copyOf(body.values()),rules,world.rules().seaLevel,world::surfaceY,(x,z)->true);
        var before=RoadNetworkGeometry.index(styled);long fence=Cell.key(-3790,5295);var outer=before.get(fence);
        assertNotNull(outer);assertEquals(3,outer.band());assertTrue(RoadPaver.low(world,rules,outer)<=70);assertTrue(RoadPaver.high(world,rules,outer)>=70);
        var job=new RoadSavedData.Job(Set.of("nanjiang--ximo"),List.of(),styled);
        assertEquals(1,job.trimGrades(outer.chunk(),c->c.key()==fence));assertFalse(job.after.containsKey(fence));
        for(var c:styled)if(c.key()!=fence)assertEquals(c,job.after.get(c.key()));
        for(var p:line)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){var c=job.after.get(Cell.key(p.x()+dx,p.z()+dz));assertNotNull(c);assertTrue(c.band()<2);assertEquals(147,c.feet2());}
        assertTrue(job.chunk(outer.chunk()).stream().noneMatch(c->c.key()==fence));
        assertTrue(job.writeChunks.stream().flatMap(e->e.getValue().stream()).noneMatch(c->c.after().key()==fence));
    }
    @Test void carriageCurbsRailsReadOnlyExitsAndCommittedGradesCannotBeTrimmed() {
        var cells=List.of(new Cell(0,0,160,false,0),new Cell(1,0,160,false,1),new Cell(2,0,161,false,2,0),new Cell(3,0,160,true),new Cell(4,0,160,false,3),new Cell(5,0,160,false,3));
        var old=cells.get(4);var job=new RoadSavedData.Job(Set.of("a--b"),List.of(old),cells);var visited=new ArrayList<Long>();
        assertEquals(1,job.trimGrades(old.chunk(),c->{visited.add(c.key());return true;}));assertEquals(List.of(cells.get(5).key()),visited);
        for(var c:cells.subList(0,5))assertEquals(c,job.after.get(c.key()));
    }
    @Test void writtenGradeIsNeverRemoved() {
        var c=new Cell(0,0,160,false,3);var job=new RoadSavedData.Job(Set.of("a--b"),List.of(),List.of(c));job.markWritten(c.chunk());
        assertEquals(0,job.trimGrades(c.chunk(),ignored->{fail("已建道路不能重新裁剪");return true;}));assertEquals(c,job.after.get(c.key()));
    }
    @Test void emptyGradingChunkAndPartialProgressSurviveRestartAndFinalCommit() {
        var grade=new Cell(0,0,160,false,3);var body=new Cell(80,0,160,false);var d=new RoadSavedData();d.stage(List.of(route(List.of(grade,body))),List.of(grade,body));
        assertEquals(1,d.job.trimGrades(grade.chunk(),c->true));assertTrue(d.job.chunk(grade.chunk()).isEmpty());d.job.markWritten(grade.chunk());d.manualPause=true;d.phase="paused";
        var loaded=RoadSavedData.load(d.save(new CompoundTag(),null),null);assertTrue(loaded.manualPause);assertEquals("paused",loaded.phase);
        assertEquals(Set.of(grade.chunk()),loaded.job.completed);assertEquals(Set.of(grade.key()),loaded.job.omittedGrades());assertTrue(loaded.job.chunk(grade.chunk()).isEmpty());assertTrue(loaded.materialized().isEmpty());
        assertEquals(List.of(body),loaded.resolved(loaded.selected()).cells());loaded.job.markWritten(body.chunk());loaded.completeJob();
        var done=RoadSavedData.load(loaded.save(new CompoundTag(),null),null);assertNull(done.job);assertEquals(List.of(body),done.committed);assertEquals(List.of(body),done.completedPlans().getFirst().cells());
    }
    @Test void corruptOmissionsCannotHideARequiredRoadColumnOrDuplicateProgress() {
        var body=new Cell(0,0,160,false);var grade=new Cell(1,0,160,false,3);var d=new RoadSavedData();d.stage(List.of(route(List.of(body,grade))),List.of(body,grade));
        for(long[] omitted:List.of(new long[]{body.key()},new long[]{grade.key(),grade.key()},new long[]{Cell.key(99,99)})){
            var tag=d.save(new CompoundTag(),null);tag.getCompound("job").putLongArray("omitted_grades",omitted);assertThrows(IllegalStateException.class,()->RoadSavedData.load(tag,null));
        }
    }
    @Test void obstacleScanFailureLeavesTheWholePlannedFootprintIntact() {
        var a=new Cell(0,0,160,false,3);var b=new Cell(1,0,160,false,3);var job=new RoadSavedData.Job(Set.of("a--b"),List.of(),List.of(a,b));var calls=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->job.trimGrades(a.chunk(),c->{if(calls.incrementAndGet()==2)throw new IllegalStateException("读取失败");return true;}));
        assertTrue(job.omittedGrades().isEmpty());assertEquals(List.of(a,b),List.copyOf(job.after.values()));assertEquals(List.of(a,b),job.chunk(a.chunk()));
    }
}
