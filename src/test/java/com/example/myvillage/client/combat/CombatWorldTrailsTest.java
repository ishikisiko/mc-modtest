package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.definition.HitboxSample;
import com.example.myvillage.combat.definition.WeaponDefinition;
import com.example.myvillage.combat.runtime.CombatGeometry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class CombatWorldTrailsTest {
    private static final double STREAK_OVERSHOOT = 1.15;
    private static final Vec3 ORIGIN = new Vec3(10.0, 64.0, -5.0);

    @Test
    void arcTrailKeepsItsRadiusBetweenSamples() {
        List<HitboxSample> samples = CombatTestData.basicSword().move(1).hitbox().samples();
        double radius = Math.hypot(samples.getFirst().endX(), samples.getFirst().endZ());
        for (float tick = samples.getFirst().actionTick(); tick <= samples.getLast().actionTick(); tick += 0.25F) {
            HitboxSample blade = CombatWorldTrails.blade(samples, tick);
            assertEquals(radius, Math.hypot(blade.endX(), blade.endZ()), 1.0E-6);
        }
    }

    @Test
    void trailClampsToTheAuthoredSamples() {
        List<HitboxSample> samples = CombatTestData.basicSword().move(3).hitbox().samples();
        assertSame(samples.getFirst(), CombatWorldTrails.blade(samples, 0.0F));
        assertSame(samples.getLast(), CombatWorldTrails.blade(samples, 30.0F));
        assertSame(samples.get(1), CombatWorldTrails.blade(samples, samples.get(1).actionTick()));
    }

    @Test
    void samplesSharingATickSpreadEvenlyThroughIt() {
        // One sample per tick sits on its tick (every sword move).
        for (AttackMoveDefinition move : CombatTestData.basicSword().moves()) {
            List<HitboxSample> samples = move.hitbox().samples();
            for (int index = 0; index < samples.size(); index++) {
                assertEquals(samples.get(index).actionTick(), CombatWorldTrails.sampleTime(samples, index), 0.0F);
            }
        }
        // n samples on one tick sit 1/n of a tick apart in list order, centred on the tick, and the
        // trail shows each one exactly at its time. Counts come from the loaded spear moves.
        int shared = 0;
        for (AttackMoveDefinition move : CombatTestData.basicSpear().moves()) {
            List<HitboxSample> samples = move.hitbox().samples();
            for (int index = 0; index < samples.size(); index++) {
                int tick = samples.get(index).actionTick();
                int count = move.hitbox().samplesAt(tick).size();
                int position = (int) samples.subList(0, index).stream().filter(sample -> sample.actionTick() == tick).count();
                float expected = tick + (position + 0.5F) / count - 0.5F;
                assertEquals(expected, CombatWorldTrails.sampleTime(samples, index), 1.0E-5F, move.id() + " sample " + index);
                assertSame(samples.get(index), CombatWorldTrails.blade(samples, expected), move.id() + " sample " + index);
                shared += count > 1 ? 1 : 0;
            }
        }
        assertTrue(shared > 0, "a spear move with several samples on one tick");
    }

    @Test
    void fallbackSizeHugsTheBodyInsteadOfTheHitboxReach() {
        CombatWorldTrails.TrailSize size = CombatWorldTrails.FALLBACK_SIZE;
        for (int moveIndex = 0; moveIndex < CombatTestData.basicSword().moves().size(); moveIndex++) {
            for (HitboxSample sample : CombatTestData.basicSword().move(moveIndex).hitbox().samples()) {
                HitboxSample drawn = CombatWorldTrails.drawnBlade(sample, 1.0, size);
                assertEquals(size.tipRadius(), tipRadius(drawn), 1.0E-6);
                assertEquals(size.trailLength(), tipRadius(drawn) - baseRadius(drawn), 1.0E-6);
                // Same direction as the server sample, so the trail still shows where the hit lands.
                double sampleLength = Math.sqrt(sample.endX() * sample.endX()
                        + Math.pow(sample.endY() - CombatWorldTrails.PIVOT_HEIGHT, 2)
                        + sample.endZ() * sample.endZ());
                assertEquals(sample.endX() / sampleLength, drawn.endX() / tipRadius(drawn), 1.0E-6);
                assertEquals(sample.endZ() / sampleLength, drawn.endZ() / tipRadius(drawn), 1.0E-6);
            }
        }
        assertEquals(size, CombatWorldTrails.trailSize(Optional.empty(), 0.8F));
    }

    @Test
    void trailLengthComesFromAWeaponOfTheMovesStyle() {
        var styles = CombatTestData.styles();
        var move = CombatTestData.basicSword().move(2).id();
        var qingfeng = CombatTestData.QINGFENG_SWORD;
        assertEquals(Optional.of(qingfeng), CombatWorldTrails.trailWeapon(styles, qingfeng, move));
        // START before the equipment update: the held item is not yet the weapon.
        assertEquals(Optional.of(qingfeng), CombatWorldTrails.trailWeapon(styles, null, move));
        assertEquals(Optional.of(qingfeng), CombatWorldTrails.trailWeapon(
                styles, ResourceLocation.withDefaultNamespace("stick"), move));
        assertEquals(Optional.empty(), CombatWorldTrails.trailWeapon(
                styles, qingfeng, ResourceLocation.fromNamespaceAndPath("myvillage", "unknown")));
        var spearMove = CombatTestData.basicSpear().move(1).id();
        assertEquals(Optional.of(CombatTestData.LINGXIAO_SPEAR),
                CombatWorldTrails.trailWeapon(styles, qingfeng, spearMove));
    }

    @Test
    void worldBladeFollowsTheMoveSamplesAndTheServerFacing() {
        List<HitboxSample> samples = CombatTestData.basicSword().move(1).hitbox().samples();
        float tick = samples.getFirst().actionTick();
        CombatWorldTrails.TrailSize size = new CombatWorldTrails.TrailSize(1.7, 1.0);
        HitboxSample drawn = CombatWorldTrails.drawnBlade(samples.getFirst(), 1.0, size);
        for (float yaw : new float[] {0.0F, 90.0F, -137.5F}) {
            CombatGeometry.WorldSample expected = CombatGeometry.transform(drawn, ORIGIN, yaw);
            CombatGeometry.WorldSample world = CombatWorldTrails.worldBlade(samples, tick, 1.0, size, ORIGIN, yaw);
            assertEquals(expected.start().x, world.start().x, 1.0E-9);
            assertEquals(expected.start().z, world.start().z, 1.0E-9);
            assertEquals(expected.end().x, world.end().x, 1.0E-9);
            assertEquals(expected.end().y, world.end().y, 1.0E-9);
            assertEquals(expected.end().z, world.end().z, 1.0E-9);
        }
        // Turning the server facing by 90 degrees turns the drawn blade with it.
        var north = CombatWorldTrails.worldBlade(samples, tick, 1.0, size, ORIGIN, 0.0F);
        var east = CombatWorldTrails.worldBlade(samples, tick, 1.0, size, ORIGIN, 90.0F);
        double northAngle = Math.atan2(north.end().x - ORIGIN.x, north.end().z - ORIGIN.z);
        double eastAngle = Math.atan2(east.end().x - ORIGIN.x, east.end().z - ORIGIN.z);
        assertEquals(Math.PI / 2.0, Math.abs(Math.IEEEremainder(northAngle - eastAngle, 2.0 * Math.PI)), 1.0E-9);
    }

    @Test
    void trailSizeComesFromTheWeaponGeometryAndScale() throws IOException {
        WeaponGeometry sword = FirstPersonSwingTest.geometry();
        CombatWorldTrails.TrailSize size = CombatWorldTrails.trailSize(Optional.of(sword), 0.8F);
        assertEquals(sword.trailLengthPixels() / 16.0 * 0.8, size.trailLength(), 1.0E-6);
        assertEquals(CombatWorldTrails.ARM_REACH + sword.gripToTrailTipPixels() / 16.0 * 0.8, size.tipRadius(), 1.0E-6);
        assertEquals(CombatWorldTrails.FALLBACK_SIZE, CombatWorldTrails.trailSize(Optional.of(sword), 0.0F));
        assertEquals(CombatWorldTrails.FALLBACK_SIZE, CombatWorldTrails.trailSize(Optional.of(sword), Float.NaN));
        // A bigger display scale draws a bigger trail; no fixed cap clips it.
        CombatWorldTrails.TrailSize doubled = CombatWorldTrails.trailSize(Optional.of(sword), 1.6F);
        assertEquals(2.0 * size.trailLength(), doubled.trailLength(), 1.0E-6);
        // A span longer than the drawn reach never draws its base behind the pivot.
        HitboxSample sample = CombatTestData.basicSword().move(1).hitbox().samples().getFirst();
        HitboxSample drawn = CombatWorldTrails.drawnBlade(sample, 1.0, new CombatWorldTrails.TrailSize(1.0, 3.0));
        assertEquals(0.0, baseRadius(drawn), 1.0E-9);
    }

    /**
     * The sword's world trail is the 0.28.0 trail: every move, every drawn frame, streaks included,
     * matches the code before the trail span and derived sizes existed (tip radius capped at 1.7,
     * blade clamped to 0.5..1.3, samples on their own ticks), and the dimensions are pinned.
     */
    @Test
    void swordWorldTrailIsUnchangedForEveryMove() throws IOException {
        WeaponDefinition weapon = CombatTestData.qingfeng();
        float scale = thirdPersonScale(weapon);
        assertEquals(0.8F, scale, 0.0F);
        WeaponGeometry sword = FirstPersonSwingTest.geometry();
        CombatWorldTrails.TrailSize size = CombatWorldTrails.trailSize(Optional.of(sword), scale);
        assertEquals(1.7, size.tipRadius(), 1.0E-6);
        assertEquals(0.795, size.trailLength(), 1.0E-6);
        assertEquals(legacyBladeLength(sword, scale), size.trailLength(), 1.0E-6);

        CombatStyleDefinition style = CombatTestData.basicSword();
        assertEquals(5, style.moves().size());
        for (AttackMoveDefinition move : style.moves()) {
            List<HitboxSample> samples = move.hitbox().samples();
            float first = samples.getFirst().actionTick() - 0.5F;
            float last = samples.getLast().actionTick() + 0.5F;
            for (float tick = first; tick <= last + 1.0E-4F; tick += CombatWorldTrails.TRAIL_TICKS / CombatWorldTrails.SEGMENTS) {
                for (double tipScale : new double[] {1.0, STREAK_OVERSHOOT}) {
                    for (float yaw : new float[] {0.0F, 73.0F}) {
                        CombatGeometry.WorldSample now = CombatWorldTrails.worldBlade(
                                samples, tick, tipScale, size, ORIGIN, yaw);
                        CombatGeometry.WorldSample before = CombatGeometry.transform(
                                legacyDrawnBlade(legacyBlade(samples, tick), tipScale, legacyBladeLength(sword, scale)),
                                ORIGIN, yaw);
                        String where = move.id() + " tick " + tick + " scale " + tipScale;
                        assertEquals(0.0, now.start().distanceTo(before.start()), 1.0E-6, where);
                        assertEquals(0.0, now.end().distanceTo(before.end()), 1.0E-6, where);
                    }
                }
                // Pinned dimensions: every sword sample reaches past 1.7, so each drawn frame is the
                // same 0.795-block span ending 1.7 blocks from the pivot (1.955 for a streak).
                HitboxSample drawn = CombatWorldTrails.drawnBlade(CombatWorldTrails.blade(samples, tick), 1.0, size);
                assertEquals(1.7, tipRadius(drawn), 1.0E-6, move.id() + " tick " + tick);
                assertEquals(0.905, baseRadius(drawn), 1.0E-6, move.id() + " tick " + tick);
                HitboxSample streak = CombatWorldTrails.drawnBlade(
                        CombatWorldTrails.blade(samples, tick), STREAK_OVERSHOOT, size);
                assertEquals(1.955, tipRadius(streak), 1.0E-6, move.id() + " tick " + tick);
            }
        }
    }

    @Test
    void spearWorldTrailIsSizedFromItsOwnGeometry() throws IOException {
        WeaponDefinition weapon = CombatTestData.lingxiao();
        float scale = thirdPersonScale(weapon);
        WeaponGeometry spear = FirstPersonOffHandTest.spearGeometry();
        CombatWorldTrails.TrailSize size = CombatWorldTrails.trailSize(Optional.of(spear), scale);
        CombatWorldTrails.TrailSize sword = CombatWorldTrails.trailSize(
                Optional.of(FirstPersonSwingTest.geometry()), thirdPersonScale(CombatTestData.qingfeng()));
        // The arm plus grip to trail tip, and the trail span, at the spear model's third-person scale.
        assertEquals(CombatWorldTrails.ARM_REACH + spear.gripToTrailTipPixels() * scale / 16.0, size.tipRadius(), 1.0E-6);
        assertEquals(spear.trailLengthPixels() * scale / 16.0, size.trailLength(), 1.0E-6);
        assertTrue(size.tipRadius() > sword.tipRadius() + 0.8, "spear reaches " + size.tipRadius());
        assertTrue(size.trailLength() > sword.trailLength(), "spear span " + size.trailLength());

        // The rule, on every drawn frame of every shipped spear move (between samples too, streaks
        // included), whatever lengths the data chooses: the head is min(far-end length, tip radius),
        // never beyond the radius, the base is the span length inside the head, and a far end past
        // the sword's 1.7 is never drawn at that old cap.
        for (AttackMoveDefinition move : CombatTestData.basicSpear().moves()) {
            assertDrawnAtMinOfReachAndRadius(move.id().toString(), move.hitbox().samples(), size, sword);
        }
    }

    /**
     * A move whose far ends are shorter than the weapon's tip radius (built here, so the case is
     * covered whatever the shipped data does): the head follows the far end's own length where it
     * is shorter, and the base never passes behind the pivot.
     */
    @Test
    void shortFarEndsDrawTheHeadAtTheirOwnLength() throws IOException {
        CombatWorldTrails.TrailSize size = CombatWorldTrails.trailSize(
                Optional.of(FirstPersonOffHandTest.spearGeometry()), thirdPersonScale(CombatTestData.lingxiao()));
        CombatWorldTrails.TrailSize sword = CombatWorldTrails.trailSize(
                Optional.of(FirstPersonSwingTest.geometry()), thirdPersonScale(CombatTestData.qingfeng()));
        double pivot = CombatWorldTrails.PIVOT_HEIGHT;
        // Far ends 3.5, 2.2, 1.4 and 0.6 blocks from the pivot, rising; two share tick 6.
        List<HitboxSample> samples = List.of(
                farEnd(5, 0.0, pivot - 0.3, 3.5),
                farEnd(6, 0.4, pivot + 1.2, 2.2),
                farEnd(6, 0.3, pivot + 1.1, 1.4),
                farEnd(7, 0.1, pivot + 0.4, 0.6));
        double[] lengths = samples.stream().mapToDouble(CombatWorldTrailsTest::tipRadius).toArray();
        assertTrue(lengths[0] > size.tipRadius() && lengths[1] < size.tipRadius() && lengths[1] > sword.tipRadius()
                && lengths[2] < sword.tipRadius() && lengths[3] < size.trailLength(), java.util.Arrays.toString(lengths));
        assertDrawnAtMinOfReachAndRadius("synthetic", samples, size, sword);
        for (int index = 0; index < samples.size(); index++) {
            HitboxSample drawn = CombatWorldTrails.drawnBlade(samples.get(index), 1.0, size);
            double head = Math.min(lengths[index], size.tipRadius());
            assertEquals(head, tipRadius(drawn), 1.0E-6, "sample " + index);
            assertEquals(Math.max(0.0, head - size.trailLength()), baseRadius(drawn), 1.0E-6, "sample " + index);
        }
        assertEquals(0.0, baseRadius(CombatWorldTrails.drawnBlade(samples.get(3), 1.0, size)), 1.0E-9,
                "a far end shorter than the span puts the base on the pivot, not behind it");
    }

    /**
     * What the shipped spear cuts are authored for: through each cut's active window the drawn head
     * stays on the tip-radius sphere. A far end shorter than the radius pulls the head inward and
     * bends its path (the c40f7ee flick and smash, whose posed-head far ends of 2.0 to 2.1 blocks
     * gave 39 to 42 degree corners); lengthen such far ends to at least the radius instead.
     */
    @Test
    void shippedSpearCutsKeepTheTrailHeadOnTheTipRadius() throws IOException {
        CombatWorldTrails.TrailSize size = CombatWorldTrails.trailSize(
                Optional.of(FirstPersonOffHandTest.spearGeometry()), thirdPersonScale(CombatTestData.lingxiao()));
        int cuts = 0;
        for (AttackMoveDefinition move : CombatTestData.basicSpear().moves()) {
            if (WeaponTrailShape.streak(move.kind())) {
                continue;
            }
            cuts++;
            List<HitboxSample> samples = move.hitbox().samples();
            for (float tick = move.activeStartTick() - 0.5F; tick <= move.activeEndTick() + 0.5F + 1.0E-4F;
                    tick += CombatWorldTrails.TRAIL_TICKS / CombatWorldTrails.SEGMENTS) {
                HitboxSample far = CombatWorldTrails.blade(samples, tick);
                double head = tipRadius(CombatWorldTrails.drawnBlade(far, 1.0, size));
                assertEquals(size.tipRadius(), head, 1.0E-6, move.id() + " tick " + tick + ": the far end is "
                        + tipRadius(far) + " blocks from the trail pivot, shorter than the spear's tip radius "
                        + size.tipRadius() + ", so the trail head leaves the radius and its path bends; author"
                        + " the cut's far ends at least that far out");
            }
        }
        assertEquals(3, cuts, "the spear's three cuts");
    }

    /** One drawn frame per render step over the trail window: the drawn-size rule holds on each. */
    private static void assertDrawnAtMinOfReachAndRadius(
            String label,
            List<HitboxSample> samples,
            CombatWorldTrails.TrailSize size,
            CombatWorldTrails.TrailSize sword) {
        float first = CombatWorldTrails.sampleTime(samples, 0) - 0.5F;
        float last = CombatWorldTrails.sampleTime(samples, samples.size() - 1) + 0.5F;
        for (float tick = first; tick <= last + 1.0E-4F; tick += CombatWorldTrails.TRAIL_TICKS / CombatWorldTrails.SEGMENTS) {
            HitboxSample far = CombatWorldTrails.blade(samples, tick);
            double reach = tipRadius(far);
            HitboxSample drawn = CombatWorldTrails.drawnBlade(far, 1.0, size);
            String where = label + " tick " + tick + " far end " + reach;
            double head = Math.min(reach, size.tipRadius());
            assertEquals(head, tipRadius(drawn), 1.0E-6, where);
            assertTrue(tipRadius(drawn) <= size.tipRadius() + 1.0E-9, where);
            assertEquals(Math.max(0.0, head - size.trailLength()), baseRadius(drawn), 1.0E-6, where);
            HitboxSample streak = CombatWorldTrails.drawnBlade(far, STREAK_OVERSHOOT, size);
            assertEquals(head * STREAK_OVERSHOOT, tipRadius(streak), 1.0E-6, where);
            if (reach > sword.tipRadius() + 0.01) {
                assertTrue(tipRadius(drawn) > sword.tipRadius() + 0.01 - 1.0E-9, where + ": drawn at the old 1.7 cap");
            }
        }
    }

    /** A sample whose far end is {@code reach} blocks from the trail pivot, at side {@code x} and height {@code y}. */
    private static HitboxSample farEnd(int tick, double x, double y, double reach) {
        double up = y - CombatWorldTrails.PIVOT_HEIGHT;
        double z = Math.sqrt(reach * reach - x * x - up * up);
        return new HitboxSample(tick, 0.0, 1.05, 0.55, x, y, z, 0.2, 0.2);
    }

    /**
     * Every spear cut's trail head traces one smooth path in sample order through the cut's active
     * window. Read from the loaded data, not pinned:
     * <ul>
     *     <li>each active tick carries the same number of samples, so {@link CombatWorldTrails#sampleTime}
     *     spaces them evenly; uneven counts (3/4/2 at c40f7ee) spaced them 1/3, 1/4 and 1/2 of a tick
     *     apart and changed the drawn speed by themselves;</li>
     *     <li>the head never reverses: consecutive drawn steps turn by less than 90 degrees;</li>
     *     <li>where the head is moving (a step of at least a fifth of the cut's fastest), neighbouring
     *     drawn steps differ in speed by no more than the posed samples themselves do from one
     *     interval to the next (5% for the interpolation inside an interval): the trail adds no
     *     speed change of its own. The bound is the pose's motion, not a number fixed here.</li>
     * </ul>
     */
    @Test
    void spearCutsTraceASmoothOneWayPathInSampleOrder() throws IOException {
        CombatWorldTrails.TrailSize size = CombatWorldTrails.trailSize(
                Optional.of(FirstPersonOffHandTest.spearGeometry()), thirdPersonScale(CombatTestData.lingxiao()));
        float step = CombatWorldTrails.TRAIL_TICKS / CombatWorldTrails.SEGMENTS;
        int cuts = 0;
        for (AttackMoveDefinition move : CombatTestData.basicSpear().moves()) {
            if (WeaponTrailShape.streak(move.kind())) {
                continue;
            }
            cuts++;
            String id = move.id().toString();
            int perTick = move.hitbox().samplesAt(move.activeStartTick()).size();
            for (int tick = move.activeStartTick(); tick <= move.activeEndTick(); tick++) {
                assertEquals(perTick, move.hitbox().samplesAt(tick).size(), id + " tick " + tick
                        + ": every active tick needs the same number of samples, or the trail's speed changes"
                        + " at the tick boundary (sampleTime spaces n samples 1/n of a tick apart)");
            }
            List<HitboxSample> samples = move.hitbox().samples();
            // The posed samples' own speed per interval, in radians per tick.
            List<Double> poseSpeeds = new ArrayList<>();
            for (int index = 0; index + 1 < samples.size(); index++) {
                poseSpeeds.add(angle(direction(samples.get(index)), direction(samples.get(index + 1)))
                        / (CombatWorldTrails.sampleTime(samples, index + 1) - CombatWorldTrails.sampleTime(samples, index)));
            }
            double poseRatio = movingRatio(poseSpeeds);
            List<double[]> heads = new ArrayList<>();
            float start = CombatWorldTrails.sampleTime(samples, 0);
            float end = CombatWorldTrails.sampleTime(samples, samples.size() - 1);
            for (float tick = start; tick <= end + 1.0E-4F; tick += step) {
                HitboxSample drawn = CombatWorldTrails.drawnBlade(CombatWorldTrails.blade(samples, tick), 1.0, size);
                heads.add(new double[] {drawn.endX(), drawn.endY() - CombatWorldTrails.PIVOT_HEIGHT, drawn.endZ()});
            }
            List<Double> steps = new ArrayList<>();
            for (int index = 0; index + 1 < heads.size(); index++) {
                steps.add(angle(unit(heads.get(index)), unit(heads.get(index + 1))));
            }
            for (int index = 0; index + 2 < heads.size(); index++) {
                double[] first = difference(heads.get(index + 1), heads.get(index));
                double[] second = difference(heads.get(index + 2), heads.get(index + 1));
                if (length(first) > 1.0E-9 && length(second) > 1.0E-9) {
                    assertTrue(angle(unit(first), unit(second)) < Math.PI / 2.0,
                            id + " frame " + index + ": the trail head turns back");
                }
            }
            double frameRatio = movingRatio(steps);
            assertTrue(frameRatio <= Math.max(1.0, poseRatio) * 1.05, id + ": neighbouring drawn steps change speed "
                    + frameRatio + "x, the posed samples only " + poseRatio + "x");
        }
        assertTrue(cuts > 0, "the spear has cuts");
    }

    /**
     * The jump the shared-tick timeline replaced, on a level arc built here with three lines per
     * tick: the old per-tick lookup drew the arc with steps many times the average at each tick
     * boundary; the timeline draws it at one speed in sample order.
     */
    @Test
    void sharedTickSamplesNoLongerJumpAtTickBoundaries() {
        List<HitboxSample> samples = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            double angle = Math.toRadians(60.0 - 15.0 * index);
            samples.add(new HitboxSample(5 + index / 3, 0.0, 1.05, 0.45,
                    Math.sin(angle) * 3.5, 1.05, Math.cos(angle) * 3.5, 0.18, 0.34));
        }
        float step = CombatWorldTrails.TRAIL_TICKS / CombatWorldTrails.SEGMENTS;
        float start = CombatWorldTrails.sampleTime(samples, 0);
        float end = CombatWorldTrails.sampleTime(samples, samples.size() - 1);
        double mean = Math.toRadians(120.0) / ((end - start) / step);
        double largest = 0.0;
        double legacyLargest = 0.0;
        for (float tick = start + step; tick <= end + 1.0E-4F; tick += step) {
            double turn = heading(CombatWorldTrails.blade(samples, tick)) - heading(CombatWorldTrails.blade(samples, tick - step));
            assertTrue(turn < 0.0, "tick " + tick + " turns back");
            largest = Math.max(largest, -turn);
            legacyLargest = Math.max(legacyLargest,
                    Math.abs(heading(legacyBlade(samples, tick)) - heading(legacyBlade(samples, tick - step))));
        }
        assertTrue(largest <= mean * 1.01, "largest step " + Math.toDegrees(largest) + ", mean " + Math.toDegrees(mean));
        assertTrue(legacyLargest > 5.0 * mean, "legacy largest step " + Math.toDegrees(legacyLargest));
    }

    private static double movingRatio(List<Double> speeds) {
        double fastest = speeds.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double ratio = 1.0;
        for (int index = 0; index + 1 < speeds.size(); index++) {
            double first = speeds.get(index);
            double second = speeds.get(index + 1);
            if (first >= 0.2 * fastest && second >= 0.2 * fastest && first > 0.0 && second > 0.0) {
                ratio = Math.max(ratio, Math.max(first / second, second / first));
            }
        }
        return ratio;
    }

    private static double[] direction(HitboxSample sample) {
        return unit(new double[] {sample.endX(), sample.endY() - CombatWorldTrails.PIVOT_HEIGHT, sample.endZ()});
    }

    private static double[] difference(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static double[] unit(double[] v) {
        double length = length(v);
        return new double[] {v[0] / length, v[1] / length, v[2] / length};
    }

    private static double angle(double[] a, double[] b) {
        return Math.acos(Math.max(-1.0, Math.min(1.0, a[0] * b[0] + a[1] * b[1] + a[2] * b[2])));
    }

    private static double heading(HitboxSample sample) {
        return Math.atan2(sample.endX(), sample.endZ());
    }

    private static double tipRadius(HitboxSample drawn) {
        return Math.sqrt(drawn.endX() * drawn.endX()
                + Math.pow(drawn.endY() - CombatWorldTrails.PIVOT_HEIGHT, 2) + drawn.endZ() * drawn.endZ());
    }

    private static double baseRadius(HitboxSample drawn) {
        return Math.sqrt(drawn.startX() * drawn.startX()
                + Math.pow(drawn.startY() - CombatWorldTrails.PIVOT_HEIGHT, 2) + drawn.startZ() * drawn.startZ());
    }

    /** The weapon model's thirdperson_righthand scale along the blade, read like the runtime does. */
    private static float thirdPersonScale(WeaponDefinition weapon) throws IOException {
        JsonObject contract = JsonParser.parseString(
                Files.readString(CombatTestData.assetPath(weapon.geometry()))).getAsJsonObject();
        ResourceLocation model = ResourceLocation.parse(contract.get("model").getAsString());
        Path path = CombatTestData.RESOURCES.resolve(
                "assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json");
        JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        return json.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand")
                .getAsJsonArray("scale").get(1).getAsFloat();
    }

    // ---- The world trail as it was at 922d7a1, kept verbatim as the reference for the sword. ----

    private static double legacyBladeLength(WeaponGeometry sword, float thirdPersonScale) {
        double length = sword.headLengthPixels() / 16.0 * thirdPersonScale;
        return Math.max(0.5, Math.min(1.3, length));
    }

    private static HitboxSample legacyDrawnBlade(HitboxSample sample, double tipScale, double bladeLength) {
        double x = sample.endX();
        double y = sample.endY() - CombatWorldTrails.PIVOT_HEIGHT;
        double z = sample.endZ();
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-6) {
            return sample;
        }
        double tipRadius = Math.min(length, 1.7);
        double baseRadius = Math.max(0.0, tipRadius - bladeLength);
        double tip = tipRadius * tipScale / length;
        double base = baseRadius / length;
        return new HitboxSample(
                sample.actionTick(),
                x * base, CombatWorldTrails.PIVOT_HEIGHT + y * base, z * base,
                x * tip, CombatWorldTrails.PIVOT_HEIGHT + y * tip, z * tip,
                sample.horizontalRadius(),
                sample.verticalRadius());
    }

    private static HitboxSample legacyBlade(List<HitboxSample> samples, float tick) {
        HitboxSample before = samples.getFirst();
        HitboxSample after = samples.getLast();
        for (HitboxSample sample : samples) {
            if (sample.actionTick() <= tick) {
                before = sample;
            }
            if (sample.actionTick() >= tick) {
                after = sample;
                break;
            }
        }
        if (before == after || after.actionTick() == before.actionTick()) {
            return before;
        }
        float progress = Math.max(0.0F, Math.min(1.0F,
                (tick - before.actionTick()) / (float) (after.actionTick() - before.actionTick())));
        double[] start = legacyPolarLerp(
                before.startX(), before.startY(), before.startZ(),
                after.startX(), after.startY(), after.startZ(), progress);
        double[] end = legacyPolarLerp(
                before.endX(), before.endY(), before.endZ(),
                after.endX(), after.endY(), after.endZ(), progress);
        return new HitboxSample(
                before.actionTick(),
                start[0], start[1], start[2],
                end[0], end[1], end[2],
                before.horizontalRadius(),
                before.verticalRadius());
    }

    private static double[] legacyPolarLerp(
            double firstX, double firstY, double firstZ,
            double secondX, double secondY, double secondZ,
            float progress) {
        double firstAngle = Math.atan2(firstX, firstZ);
        double secondAngle = Math.atan2(secondX, secondZ);
        double angle = firstAngle + (secondAngle - firstAngle) * progress;
        double firstRadius = Math.hypot(firstX, firstZ);
        double radius = firstRadius + (Math.hypot(secondX, secondZ) - firstRadius) * progress;
        return new double[] {
                Math.sin(angle) * radius,
                firstY + (secondY - firstY) * progress,
                Math.cos(angle) * radius
        };
    }
}
