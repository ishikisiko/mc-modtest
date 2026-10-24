package com.example.myvillage.regiongen;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BayTerracesGuardTest {
    @Test void guardProtectsOnlyItsCoreAndReleasesOnExceptionalExit() {
        var core=new BayTerracesGuard.Reservation(-100,52,200,83,383);
        var inside=new BlockPos(-30,76,250);
        assertTrue(BayTerracesGuard.allowsOre(inside));
        assertThrows(IllegalStateException.class,()->{
            try(var scope=BayTerracesGuard.reserve(null,List.of(core))) {
                assertFalse(BayTerracesGuard.allowsOre(inside));
                assertTrue(BayTerracesGuard.allowsOre(new BlockPos(-30,51,250)));
                assertTrue(BayTerracesGuard.allowsOre(new BlockPos(-101,76,250)));
                assertTrue(BayTerracesGuard.allowsOre(new BlockPos(-30,76,384)));
                throw new IllegalStateException("fixture");
            }
        });
        assertTrue(BayTerracesGuard.allowsOre(inside));
    }

    @Test void nestedDecorationRestoresOuterReservation() {
        var core=new BayTerracesGuard.Reservation(0,50,0,183,183);var at=new BlockPos(10,70,10);
        try(var outer=BayTerracesGuard.reserve(null,List.of(core))) {
            assertFalse(BayTerracesGuard.allowsOre(at));
            try(var inner=BayTerracesGuard.reserve(null,List.of())) { assertTrue(BayTerracesGuard.allowsOre(at)); }
            assertFalse(BayTerracesGuard.allowsOre(at));
        }
        assertTrue(BayTerracesGuard.allowsOre(at));
    }
}
