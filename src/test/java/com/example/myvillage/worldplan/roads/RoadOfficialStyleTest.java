package com.example.myvillage.worldplan.roads;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

class RoadOfficialStyleTest {
    private List<Cell> carriage(){List<Cell> cells=new ArrayList<>();for(int z=0;z<36;z++)for(int x=-2;x<=2;x++)cells.add(new Cell(x,z,161,false));return cells;}
    @Test void fullFiveWideCarriageIsNeverReplacedByRaisedCurbs() {
        var original=carriage();var styled=RoadOfficialStyle.apply(original,RoadRules.load(),63,(x,z)->80,(x,z)->true);
        var by=new HashMap<Long,Cell>();styled.forEach(c->assertNull(by.put(c.key(),c)));
        for(Cell c:original){var after=by.get(c.key());assertNotNull(after);assertEquals(c.feet2(),after.feet2());assertTrue(after.band()<2);}
        assertEquals(0,by.get(Cell.key(0,18)).band());assertEquals(1,by.get(Cell.key(2,18)).band());
        assertEquals(2,by.get(Cell.key(3,18)).band());assertEquals(162,by.get(Cell.key(3,18)).feet2());
        assertEquals(3,by.get(Cell.key(7,18)).band());assertEquals(160,by.get(Cell.key(7,18)).feet2());
        assertTrue(styled.stream().filter(c->c.band()==3).allMatch(c->c.feet2()%2==0));
    }
    @Test void protectedSideColumnsTrimOnlyAdornments() {
        var styled=RoadOfficialStyle.apply(carriage(),RoadRules.load(),63,(x,z)->80,(x,z)->x<3&&z>=0);
        assertEquals(180,styled.stream().filter(c->c.band()<2).count());
        assertTrue(styled.stream().noneMatch(c->c.x()>=3||c.z()<0));
    }
    @Test void curbAndGradingRangeRemainBounded() {
        var rules=RoadRules.load();var styled=RoadOfficialStyle.apply(carriage(),rules,63,(x,z)->80,(x,z)->true);
        int r=rules.style.curbWidth()+rules.style.gradeWidth();
        assertTrue(styled.stream().allMatch(c->c.x()>=-2-r&&c.x()<=2+r&&c.z()>=-r&&c.z()<36+r));
    }
    @Test void raisedPavingStillJoinsBothUnchangedEnds() {
        var g=RoadGeometry.build(List.of(new RoadAStar.Point(0,0),new RoadAStar.Point(0,36)),5,3,160,160,(x,z)->80,(x,z)->true,8,10000,List.of(),1);
        var cells=new HashMap<Long,Cell>();g.cells().forEach(c->cells.put(c.key(),c));
        assertEquals(160,cells.get(Cell.key(0,0)).feet2());assertEquals(160,cells.get(Cell.key(0,36)).feet2());
        assertEquals(161,cells.get(Cell.key(0,18)).feet2());
    }
    @Test void shallowWaterRetainsCurbSupportWithoutGradingTheWaterBody() {
        var rules=RoadRules.load();var styled=RoadOfficialStyle.apply(carriage(),rules,80,(x,z)->x>=3?79:80,(x,z)->true);
        assertTrue(styled.stream().anyMatch(c->c.x()==3&&c.z()==18&&c.band()==2));
        assertTrue(styled.stream().noneMatch(c->c.x()>3&&c.band()==3));
        var deep=RoadOfficialStyle.apply(carriage(),rules,80,(x,z)->x>=3?80-rules.maxWaterDepth-1:80,(x,z)->true);
        assertTrue(deep.stream().noneMatch(c->c.x()==3&&c.z()==18));
    }
}
