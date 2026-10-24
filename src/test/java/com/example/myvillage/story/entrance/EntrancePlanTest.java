package com.example.myvillage.story.entrance;

import com.google.gson.JsonParser;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntrancePlanTest {
    static final Path RULES=Path.of("src/main/resources/data/myvillage/story/qianzhen_entrance.json");
    static EntranceRules rules()throws Exception {try(var r=Files.newBufferedReader(RULES)){return EntranceRules.read(r);}}
    static EntrancePlan plan(EntranceRules r,int x,int z,EntranceRules.Point offset) {
        return EntrancePlan.create(r,x,79,z,offset,0,-60,238,159);
    }
    @Test void fullRoadIsContiguousAndLeavesTheSpawnBufferUntouched()throws Exception {
        var r=rules();
        for(var offset:r.candidates()) {
            var p=plan(r,-100,-200,offset);
            for(var c:p.roadCells(r.roadHalfWidth()+1))assertTrue(Math.max(Math.abs(c.x()+100),Math.abs(c.z()+200))>r.spawnBuffer());
            var last=p.route().getLast();assertEquals(p.originX()+r.roadAnchor().x(),last.x());assertEquals(p.originZ()+r.roadAnchor().z(),last.z());
            for(int i=1;i<p.route().size();i++) {
                var a=p.route().get(i-1);var b=p.route().get(i);assertEquals(1,Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z()));
            }
            assertTrue(p.chunks(r).size()<=r.maxChunks());
        }
    }
    @Test void newTownAndNegativeChunksAreFiniteAndIndependentOfVisitOrder()throws Exception {
        var r=rules();var p=plan(r,-500,-500,r.candidates().getFirst());
        assertEquals(p,plan(r,-500,-500,r.candidates().getFirst()));
        Set<EntrancePlan.Cell> expected=p.chunks(r);
        for(var c:p.roadCells(r.roadHalfWidth()+1))assertTrue(expected.contains(new EntrancePlan.Cell(Math.floorDiv(c.x(),16),Math.floorDiv(c.z(),16))));
        assertThrows(UnsupportedOperationException.class,()->p.route().clear());
    }
    @Test void laneIndicatorDoesNotCoverBirthAreaWholeTownOrHighPlayerHouses()throws Exception {
        var r=rules();var p=plan(r,0,0,r.candidates().getFirst());var middle=p.route().get(p.route().size()/2);
        assertTrue(p.onRoad(middle.x(),80,middle.z(),r));assertFalse(p.onRoad(0,80,0,r));
        assertFalse(p.onRoad(p.scene().maxX(),80,p.scene().maxZ(),r));
        assertFalse(p.onRoad(middle.x(),90,middle.z(),r));assertFalse(p.onRoad(middle.x(),70,middle.z(),r));
    }
    @Test void malformedFractionalOrUnboundedRulesAndRoadBudgetsFailBeforeWorldAccess()throws Exception {
        String json=Files.readString(RULES);var o=JsonParser.parseString(json).getAsJsonObject();
        o.addProperty("max_chunks",1);var narrow=EntranceRules.read(new StringReader(o.toString()));
        assertThrows(IllegalArgumentException.class,()->plan(narrow,0,0,narrow.candidates().getFirst()));
        o=JsonParser.parseString(json).getAsJsonObject();o.addProperty("road_half_width",1.5);String fractional=o.toString();
        assertThrows(IllegalArgumentException.class,()->EntranceRules.read(new StringReader(fractional)));
        o=JsonParser.parseString(json).getAsJsonObject();o.addProperty("max_block_writes",10_000_000);String big=o.toString();
        assertThrows(IllegalArgumentException.class,()->EntranceRules.read(new StringReader(big)));
        var r=rules();assertThrows(IllegalArgumentException.class,()->plan(r,0,0,new EntranceRules.Point(8,0)));
    }
}
