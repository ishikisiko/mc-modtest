package com.example.myvillage.combat.runtime;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WindStepCollisionTest {
    private static final AABB BODY = new AABB(-.3, 64, -.3, .3, 65.8, .3);
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);

    @Test void openAirNeedsNoDestinationFloor() {
        assertEquals(4.5, CombatStepService.chooseAirDistance(BODY, FORWARD, 4.5, sweep -> true), 1.0E-9);
    }

    @Test void thinWallBeforeTheClearDestinationCannotBeSkipped() {
        AABB thinWall = new AABB(-1, 64, 2.01, 1, 67, 2.02);
        double safe = CombatStepService.chooseAirDistance(BODY, FORWARD, 4.5, sweep -> !sweep.intersects(thinWall));
        assertEquals(1.7, safe, 1.0E-9);
        assertFalse(BODY.expandTowards(FORWARD.scale(safe)).intersects(thinWall));
    }

    @Test void bodyWidthAndHeadHeightBlockCornersAndOverhangsThatACenterRayWouldMiss() {
        AABB corner = new AABB(.25, 64, 2.01, 1, 67, 2.02);
        AABB overhang = new AABB(-1, 65.5, 2.01, 1, 67, 2.02);
        assertEquals(1.7, CombatStepService.chooseAirDistance(BODY, FORWARD, 4.5,
                sweep -> !sweep.intersects(corner)), 1.0E-9);
        assertEquals(1.7, CombatStepService.chooseAirDistance(BODY, FORWARD, 4.5,
                sweep -> !sweep.intersects(overhang)), 1.0E-9);
    }

    @Test void wallAgainstTheBodyRefusesWithoutAnEndpointJumpAndUnloadedPathIsAlsoRefused() {
        AABB wall = new AABB(-1, 64, .31, 1, 67, .33);
        assertEquals(0, CombatStepService.chooseAirDistance(BODY, FORWARD, 4.5,
                sweep -> !sweep.intersects(wall)));
        assertEquals(0, CombatStepService.chooseAirDistance(BODY, FORWARD, 4.5, sweep -> false));
    }

    @Test void stairsAndSlabHeightCollidersStillStopTheAirPath() {
        AABB stair = new AABB(-1, 63.5, 2.01, 1, 64.5, 2.51);
        assertEquals(1.7, CombatStepService.chooseAirDistance(BODY, FORWARD, 4.5,
                sweep -> !sweep.intersects(stair)), 1.0E-9);
    }
}
