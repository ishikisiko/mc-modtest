package com.example.myvillage.client.cultivation.panel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechniqueGridLayoutTest {
    @Test void allViewportWidthsKeepCellsInsideAndRowsDisjoint() {
        for(int width:new int[]{1,40,120,239,265,399,407})for(int count:new int[]{0,1,7,139}){
            var grid=TechniqueGridLayout.of(width,count);
            assertTrue(grid.columns()>=1&&grid.columns()<=4);
            for(int i=0;i<count;i++){
                assertTrue(grid.x(i)>=0);
                assertTrue(grid.x(i)+grid.cellWidth()<=width);
                assertTrue(grid.y(i)+TechniqueGridLayout.HEIGHT<=grid.height());
                if(i>=grid.columns())assertTrue(grid.y(i)>=grid.y(i-grid.columns())+TechniqueGridLayout.HEIGHT);
            }
            if(count==0)assertEquals(0,grid.height());
        }
    }
    @Test void narrowAndWideLayoutsHavePredictableHitCoordinates() {
        var narrow=TechniqueGridLayout.of(239,7);
        assertEquals(3,narrow.columns());assertEquals(0,narrow.x(3));assertEquals(64,narrow.y(3));
        var wide=TechniqueGridLayout.of(399,7);
        assertEquals(4,wide.columns());assertEquals(124,wide.height());
        assertThrows(IllegalArgumentException.class,()->TechniqueGridLayout.of(0,1));
    }
}
