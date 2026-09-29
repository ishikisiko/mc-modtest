package com.example.myvillage.combat.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalDouble;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CombatStepServiceTest {
    @Test
    void choosesLongestCollisionAndSupportSafeDistance() {
        double chosen = CombatStepService.chooseSafeDistance(
                0.8,
                0.1,
                distance -> distance <= 0.5);

        assertEquals(0.5, chosen, 1.0E-9);
    }

    @Test
    void suppressesStepWhenEveryCandidateIsUnsafe() {
        assertEquals(
                0.0,
                CombatStepService.chooseSafeDistance(0.8, 0.1, ignored -> false),
                1.0E-9);
    }

    @Test
    void safeDistanceSearchStartsAtTheExactPlannedDistance() {
        assertEquals(0.25, CombatStepService.chooseSafeDistance(0.25, 0.1, ignored -> true), 1.0E-9);
        assertEquals(1.4, CombatStepService.chooseSafeDistance(1.4, 0.1, ignored -> true), 1.0E-9);
    }

    @Test
    void impulseCompensatesGroundDragSoTheSlideCoversThePlannedDistance() {
        assertEquals(0.454, CombatStepService.GROUND_DRAG_COMPENSATION, 1.0E-9);
        double speed = CombatStepService.impulseForDistance(1.4);
        double travelled = 0.0;
        double current = speed;
        for (int tick = 0; tick < 200; tick++) {
            travelled += current;
            current *= 0.6 * 0.91;
        }
        assertEquals(1.4, travelled, 1.0E-6);
    }

    @Test
    void magnetismUsesTheFullStepWithoutATarget() {
        assertEquals(1.4, CombatStepService.magnetizedDistance(1.4, OptionalDouble.empty(), 3.5, false), 1.0E-9);
        assertEquals(0.3, CombatStepService.magnetizedDistance(0.3, OptionalDouble.empty(), 3.0, true), 1.0E-9);
    }

    @Test
    void lightStepStaysPutWhenTheTargetIsAlreadyInRange() {
        assertEquals(0.0, CombatStepService.magnetizedDistance(0.3, OptionalDouble.of(2.0), 3.0, true), 1.0E-9);
        assertEquals(0.3, CombatStepService.magnetizedDistance(0.3, OptionalDouble.of(3.2), 3.0, true), 1.0E-9);
    }

    @Test
    void lungeStopsShortOfTheTargetEdge() {
        assertEquals(1.4, CombatStepService.magnetizedDistance(1.4, OptionalDouble.of(2.5), 3.5, false), 1.0E-9);
        assertEquals(0.4, CombatStepService.magnetizedDistance(1.4, OptionalDouble.of(1.0), 3.5, false), 1.0E-9);
        assertEquals(0.0, CombatStepService.magnetizedDistance(1.4, OptionalDouble.of(0.4), 3.5, false), 1.0E-9);
    }

    @Test
    void horizontalEdgeDistanceMeasuresToTheBoxSide() {
        AABB box = new AABB(-0.3, 0.0, 2.0, 0.3, 1.8, 2.6);
        assertEquals(2.0, CombatStepService.horizontalEdgeDistance(Vec3.ZERO, box), 1.0E-9);
        assertEquals(0.0, CombatStepService.horizontalEdgeDistance(new Vec3(0.0, 0.0, 2.3), box), 1.0E-9);
    }

    @Test
    void stepSweepOriginMovesToThePlannedEndAfterTheStepTick() {
        CombatHitResolver.StepSweep sweep = new CombatHitResolver.StepSweep(
                new Vec3(1.0, 64.0, 1.0), new Vec3(1.0, 64.0, 2.4), 6);
        assertEquals(new Vec3(1.0, 64.0, 1.0), sweep.originAt(5));
        assertEquals(new Vec3(1.0, 64.0, 1.0), sweep.originAt(6));
        assertEquals(new Vec3(1.0, 64.0, 2.4), sweep.originAt(7));
        assertEquals(new Vec3(1.0, 64.0, 2.4), sweep.originAt(9));
        assertEquals(1.4, sweep.distance(), 1.0E-9);
    }
}
