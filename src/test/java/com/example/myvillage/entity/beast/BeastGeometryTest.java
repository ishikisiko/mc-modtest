package com.example.myvillage.entity.beast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class BeastGeometryTest {
    /** A 0.6 x 1.8 player-sized box standing on y = 64 with its feet centre at (x, z). */
    private static AABB player(double x, double z) {
        return new AABB(x - 0.3, 64.0, z - 0.3, x + 0.3, 65.8, z + 0.3);
    }

    @Test
    void yawConvention() {
        Vec3 south = BeastGeometry.forward(0.0F);
        assertEquals(0.0, south.x, 1.0E-9);
        assertEquals(1.0, south.z, 1.0E-9);
        Vec3 west = BeastGeometry.forward(90.0F);
        assertEquals(-1.0, west.x, 1.0E-9);
        assertEquals(0.0, west.z, 1.0E-9);
        assertEquals(0.0F, BeastGeometry.yawToward(0.0, 5.0), 1.0E-4F);
        assertEquals(90.0F, BeastGeometry.yawToward(-5.0, 0.0), 1.0E-4F);
        assertEquals(-90.0F, BeastGeometry.yawToward(5.0, 0.0), 1.0E-4F);
        assertEquals(180.0F, Math.abs(BeastGeometry.yawToward(0.0, -5.0)), 1.0E-4F);
        for (float yaw = -170.0F; yaw < 180.0F; yaw += 35.0F) {
            Vec3 forward = BeastGeometry.forward(yaw);
            assertEquals(yaw, BeastGeometry.yawToward(forward.x, forward.z), 1.0E-3F, "round trip " + yaw);
        }
        assertEquals(3.0, BeastGeometry.forwardDistance(0.0, 0.0, 0.0F, 1.0, 3.0), 1.0E-9);
        assertEquals(-3.0, BeastGeometry.forwardDistance(0.0, 0.0, 180.0F, 1.0, 3.0), 1.0E-9);
    }

    @Test
    void biteBoxReachesAheadOnly() {
        BeastMoveDefinition.HitBox bite = BeastTestData.bite().hit();
        // Facing south from the origin: forward 0.3..2.9, half width 0.7.
        assertTrue(BeastGeometry.hitOverlaps(bite, 0.0, 64.0, 0.0, 0.0F, player(0.0, 2.5)));
        assertTrue(BeastGeometry.hitOverlaps(bite, 0.0, 64.0, 0.0, 0.0F, player(0.0, 3.15)), "edge touches far end");
        assertFalse(BeastGeometry.hitOverlaps(bite, 0.0, 64.0, 0.0, 0.0F, player(0.0, 3.3)), "beyond reach");
        assertFalse(BeastGeometry.hitOverlaps(bite, 0.0, 64.0, 0.0, 0.0F, player(0.0, -1.0)), "behind");
        assertTrue(BeastGeometry.hitOverlaps(bite, 0.0, 64.0, 0.0, 0.0F, player(0.95, 1.5)), "side, inside");
        assertFalse(BeastGeometry.hitOverlaps(bite, 0.0, 64.0, 0.0, 0.0F, player(1.1, 1.5)), "side step clears it");
        // Facing west the same player to the south is now beside the beast.
        assertFalse(BeastGeometry.hitOverlaps(bite, 0.0, 64.0, 0.0, 90.0F, player(0.0, 2.5)));
        assertTrue(BeastGeometry.hitOverlaps(bite, 0.0, 64.0, 0.0, 90.0F, player(-2.5, 0.0)));
    }

    @Test
    void rotatedBoxIsExactNotItsBounds() {
        BeastMoveDefinition.HitBox bite = BeastTestData.bite().hit();
        float diagonal = BeastGeometry.yawToward(1.0, 1.0);
        double reach = 1.6 / Math.sqrt(2.0);
        assertTrue(BeastGeometry.hitOverlaps(bite, 0.0, 64.0, 0.0, diagonal, player(reach, reach)));
        // Inside the world-axis bounds of the turned box but off its local side.
        AABB bounds = BeastGeometry.hitBounds(bite, 0.0, 64.0, 0.0, diagonal);
        AABB corner = new AABB(2.1, 64.0, 0.0, 2.3, 65.8, 0.2);
        assertTrue(bounds.intersects(corner));
        assertFalse(BeastGeometry.hitOverlaps(bite, 0.0, 64.0, 0.0, diagonal, corner));
    }

    @Test
    void heightIsMeasuredFromTheFeet() {
        BeastMoveDefinition.HitBox pounce = BeastTestData.pounce().hit();
        // Height -0.3..1.8: a pouncing wolf at its 1.06-block peak still reaches a standing player.
        assertTrue(BeastGeometry.hitOverlaps(pounce, 0.0, 65.06, 0.0, 0.0F, player(0.0, 1.0)));
        assertFalse(BeastGeometry.hitOverlaps(pounce, 0.0, 66.2, 0.0, 0.0F, player(0.0, 1.0)), "too high");
        assertFalse(BeastGeometry.hitOverlaps(pounce, 0.0, 62.0, 0.0, 0.0F, player(0.0, 1.0)), "too low");
    }

    @Test
    void biteFromItsFarthestStartStillReachesWhereTheTargetStood() {
        BeastMoveDefinition bite = BeastTestData.bite();
        double start = bite.useRange().maximum();
        // By the first active tick the lunge has carried the wolf one tick of its forward speed.
        double travelled = bite.lunge().forwardMax();
        assertTrue(BeastGeometry.hitOverlaps(bite.hit(), 0.0, 64.0, travelled, 0.0F, player(0.0, start)),
                "a player standing at the far end of use_range is inside the box on the first active tick");
        assertFalse(BeastGeometry.hitOverlaps(bite.hit(), 0.0, 64.0, travelled, 0.0F, player(1.1, start)),
                "one sidestep out of the locked line clears it");
    }
}
