package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class WindStepRuntimeTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation NETHER = ResourceLocation.parse("minecraft:the_nether");

    @Test void firstObservationReconnectSkippedTickAndDimensionChangeNeverPretendToBeALanding() {
        var last = new WindStepRuntime.Landing(100, 64, OVERWORLD, true);
        assertFalse(WindStepRuntime.stableLanding(null, 101, 64, OVERWORLD, true));
        assertFalse(WindStepRuntime.stableLanding(last, 102, 64, OVERWORLD, true));
        assertFalse(WindStepRuntime.stableLanding(last, 101, 64, NETHER, true));
        assertFalse(WindStepRuntime.stableLanding(last, 101, 64, OVERWORLD, false));
        assertTrue(WindStepRuntime.stableLanding(last, 101, 64, OVERWORLD, true));
    }

    @Test void jumpingFallingAndStairHeightChangesDoNotCountAsStableLanding() {
        var last = new WindStepRuntime.Landing(100, 64, OVERWORLD, true);
        assertFalse(WindStepRuntime.stableLanding(last, 101, 64.2, OVERWORLD, true));
        assertFalse(WindStepRuntime.stableLanding(last, 101, 63.8, OVERWORLD, true));
        assertFalse(WindStepRuntime.stableLanding(last, 101, 64.5, OVERWORLD, true));
    }

    @Test void footProbeTouchesOnlyTheFloorUnderTheInsetFeetNotASideWallOrDeepBelow() {
        AABB body = new AABB(-.3, 64, -.3, .3, 65.8, .3);
        AABB probe = WindStepRuntime.footProbe(body);
        assertTrue(probe.intersects(new AABB(-1, 63, -1, 1, 64, 1)));
        assertFalse(probe.intersects(new AABB(.3, 63, -1, .35, 66, 1)));
        assertFalse(probe.intersects(new AABB(-1, 63, -1, 1, 63.9, 1)));
        assertEquals(64, probe.maxY);
        assertEquals(WindStepRuntime.FOOT_DEPTH, probe.getYsize(), 1.0E-9);
    }

    @Test void narrowRealEdgeSupportStillCountsButASideWallDoesNot() {
        AABB body = new AABB(-.3, 64, -.3, .3, 65.8, .3);
        AABB probe = WindStepRuntime.footProbe(body);
        assertTrue(probe.intersects(new AABB(.25, 63, -1, .35, 64, 1)));
        assertFalse(probe.intersects(new AABB(.3, 63, -1, .35, 66, 1)));
        assertFalse(probe.intersects(new AABB(-1, 63, -1, 1, 63.999, 1)));
    }

    @Test void anObservedAirToGroundTransitionRestoresImmediatelyForOneTickLandingsAndRepeatJumps() {
        var airborne = new WindStepRuntime.Landing(100, 64.4, OVERWORLD, false);
        assertTrue(WindStepRuntime.stableLanding(airborne, 101, 64, OVERWORLD, true));
        assertFalse(WindStepRuntime.stableLanding(airborne, 101, 64, OVERWORLD, false));
        // An authoritative air step can reach a supporting ledge during this same server tick.
        assertTrue(WindStepRuntime.stableLanding(airborne, 100, 64, OVERWORLD, true));
        assertFalse(WindStepRuntime.stableLanding(airborne, 102, 64, OVERWORLD, true));
    }
}
