package com.example.myvillage.worldplan.roads;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoadPaverTest {
    @Test void structureFoundationsBelowDeclaredHeightStillProtectTheirColumns() {
        var box=new BoundingBox(6224,86,-1536,6238,95,-1525);
        var foundation=new BlockPos(6232,84,-1525);
        assertFalse(box.isInside(foundation));assertTrue(RoadPaver.coversColumn(box,foundation));
        assertFalse(RoadPaver.coversColumn(box,new BlockPos(6239,84,-1525)));
    }
}
