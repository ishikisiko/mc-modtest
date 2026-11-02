package com.example.myvillage.sect;
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
class SectCourtyardTest {
    @Test void actualAnchorAndFootprintTranslateTheCompiledGeometry(){
        var d=SectBlueprint.current();BlockPos anchor=new BlockPos(-1234,80,3456),base=SectGenerator.baseFor(anchor);
        assertEquals(anchor,base.offset(d.entry));assertEquals(base,SectCourtyard.base(anchor));
        var footprint=SectCourtyard.footprint(anchor);
        assertEquals(d.width,footprint.maxX()-footprint.minX()+1);assertEquals(d.depth,footprint.maxZ()-footprint.minZ()+1);
        assertEquals(0,footprint.distanceTo(anchor.getX(),anchor.getZ()));
    }
    @Test void everyBodyCellComesFromTheCompiledActivityAreas(){
        var d=SectBlueprint.current();var anchor=new BlockPos(100,80,-40);var cells=SectCourtyard.cells(1,anchor,d.id.toString());
        assertEquals(d.courtyard.size(),cells.size());assertEquals(cells.size(),new HashSet<>(cells).size());
        for(int i=0;i<cells.size();i++) assertEquals(d.world(anchor,d.courtyard.get(i)),cells.get(i));
        assertEquals(d.world(anchor,d.steward),SectCourtyard.stewardCell(anchor));
        assertTrue(cells.contains(SectCourtyard.stewardCell(anchor)));
    }
    @Test void invalidLayoutIsNotSilentlyReplaced(){assertThrows(IllegalArgumentException.class,()->SectCourtyard.cells(1,BlockPos.ZERO,"none"));}
    @Test void lowWorldsAreLiftedWithoutMovingTheirGateColumn(){
        var d=SectBlueprint.current();BlockPos fitted=d.fitAnchor(new BlockPos(1,-60,2),-64,320);
        assertEquals(1,fitted.getX());assertEquals(2,fitted.getZ());assertEquals(-63,d.base(fitted).getY());
        assertThrows(IllegalArgumentException.class,()->d.fitAnchor(new BlockPos(1,319,2),-64,320));
    }
}
