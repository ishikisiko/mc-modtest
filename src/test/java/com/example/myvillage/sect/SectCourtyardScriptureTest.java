package com.example.myvillage.sect;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
class SectCourtyardScriptureTest {
    @Test void shelfCoordinatesAndFacingComeFromTheSameBlueprint(){
        var d=SectBlueprint.current();var anchor=new BlockPos(333,75,-211);
        var sites=SectCourtyard.scriptureShelfSites(42,anchor,d.id.toString());
        assertEquals(d.shelves.size(),sites.size());assertFalse(sites.isEmpty());
        for(int i=0;i<sites.size();i++){
            assertEquals(d.world(anchor,d.shelves.get(i).position()),sites.get(i));
            assertTrue(d.shelves.get(i).facing().getAxis().isHorizontal());
            assertTrue(d.shelves.get(i).position().distManhattan(d.shelves.get(i).reader())<=3);
        }
    }
}
