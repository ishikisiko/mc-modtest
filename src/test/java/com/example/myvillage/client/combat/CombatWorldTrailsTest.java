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
        // The spear sweep has three lines per active tick: a third of a tick apart, in list order,
        // the middle one on the tick, so the steps across a tick boundary match the steps inside it.
        List<HitboxSample> sweep = spearSweep().hitbox().samples();
        assertEquals(9, sweep.size());
        for (int index = 0; index < sweep.size(); index++) {
            float expected = sweep.getFirst().actionTick() - 1.0F / 3.0F + index / 3.0F;
            assertEquals(expected, CombatWorldTrails.sampleTime(sweep, index), 1.0E-5F);
            HitboxSample at = CombatWorldTrails.blade(sweep, CombatWorldTrails.sampleTime(sweep, index));
            assertSame(sweep.get(index), at, "sample " + index);
        }
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
        SwordGeometry sword = FirstPersonSwingTest.geometry();
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
        SwordGeometry sword = FirstPersonSwingTest.geometry();
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
        SwordGeometry spear = FirstPersonOffHandTest.spearGeometry();
        CombatWorldTrails.TrailSize size = CombatWorldTrails.trailSize(Optional.of(spear), scale);
        CombatWorldTrails.TrailSize sword = CombatWorldTrails.trailSize(
                Optional.of(FirstPersonSwingTest.geometry()), thirdPersonScale(CombatTestData.qingfeng()));
        // Grip y -2 to tip y 32 at scale 0.9 plus the arm: about 2.6 blocks; the span y 16..32.
        assertEquals(0.9F, scale, 0.0F);
        assertEquals(CombatWorldTrails.ARM_REACH + 34.0 * 0.9 / 16.0, size.tipRadius(), 1.0E-6);
        assertEquals(16.0 * 0.9 / 16.0, size.trailLength(), 1.0E-6);
        assertTrue(size.tipRadius() > sword.tipRadius() + 0.8, "spear reaches " + size.tipRadius());
        assertTrue(size.trailLength() > sword.trailLength(), "spear span " + size.trailLength());

        // The trail head is never beyond the spear's own tip radius: it is that radius wherever the
        // move's far end reaches it, and the far end itself where the move is shorter (a cut whose
        // far end is the posed spearhead up in the air). The span keeps its length inside the head.
        // Checked at every drawn frame, between samples too, and for streaks.
        int full = 0;
        int shorter = 0;
        for (AttackMoveDefinition move : CombatTestData.basicSpear().moves()) {
            List<HitboxSample> samples = move.hitbox().samples();
            float first = CombatWorldTrails.sampleTime(samples, 0) - 0.5F;
            float last = CombatWorldTrails.sampleTime(samples, samples.size() - 1) + 0.5F;
            for (float tick = first; tick <= last + 1.0E-4F; tick += CombatWorldTrails.TRAIL_TICKS / CombatWorldTrails.SEGMENTS) {
                HitboxSample far = CombatWorldTrails.blade(samples, tick);
                double reach = tipRadius(far);
                HitboxSample drawn = CombatWorldTrails.drawnBlade(far, 1.0, size);
                String where = move.id() + " tick " + tick + " far end " + reach;
                double head = Math.min(reach, size.tipRadius());
                assertEquals(head, tipRadius(drawn), 1.0E-6, where);
                assertEquals(Math.max(0.0, head - size.trailLength()), baseRadius(drawn), 1.0E-6, where);
                assertTrue(tipRadius(drawn) <= size.tipRadius() + 1.0E-9, where);
                HitboxSample streak = CombatWorldTrails.drawnBlade(far, STREAK_OVERSHOOT, size);
                assertEquals(head * STREAK_OVERSHOOT, tipRadius(streak), 1.0E-6, where);
                // Never the old sword-sized cap: past 1.7 blocks the spear's head follows its reach.
                if (reach > sword.tipRadius() + 0.01) {
                    assertTrue(tipRadius(drawn) > sword.tipRadius() + 0.01 - 1.0E-9, where);
                }
                if (reach >= size.tipRadius()) {
                    full++;
                } else {
                    shorter++;
                }
            }
        }
        assertTrue(full > 0 && shorter > 0, "both cases drawn: full " + full + ", shorter " + shorter);
        // The sweep and both thrusts reach past the drawn spear everywhere: full radius throughout.
        for (int index : new int[] {0, 1, 4}) {
            for (HitboxSample sample : CombatTestData.basicSpear().move(index).hitbox().samples()) {
                HitboxSample drawn = CombatWorldTrails.drawnBlade(sample, 1.0, size);
                assertEquals(size.tipRadius(), tipRadius(drawn), 1.0E-6, CombatTestData.basicSpear().move(index).id().toString());
                assertEquals(size.tipRadius() - size.trailLength(), baseRadius(drawn), 1.0E-6);
            }
        }
    }

    /**
     * The spear sweep's nine lines (three per tick) draw one steady arc: at the render's own step
     * the tip only ever turns one way, and no step turns more than the average step (no jump at
     * a tick boundary). The old per-tick lookup turned 35 degrees at once at each boundary.
     */
    @Test
    void multiSampleSweepTracesASmoothPathInSampleOrder() throws IOException {
        AttackMoveDefinition move = spearSweep();
        List<HitboxSample> samples = move.hitbox().samples();
        CombatWorldTrails.TrailSize size = CombatWorldTrails.trailSize(
                Optional.of(FirstPersonOffHandTest.spearGeometry()), thirdPersonScale(CombatTestData.lingxiao()));
        float step = CombatWorldTrails.TRAIL_TICKS / CombatWorldTrails.SEGMENTS;
        float start = CombatWorldTrails.sampleTime(samples, 0);
        float end = CombatWorldTrails.sampleTime(samples, samples.size() - 1);
        List<Double> turns = new ArrayList<>();
        List<Double> legacyTurns = new ArrayList<>();
        double previous = Double.NaN;
        double legacyPrevious = Double.NaN;
        for (float tick = start; tick <= end + 1.0E-4F; tick += step) {
            double angle = heading(CombatWorldTrails.drawnBlade(CombatWorldTrails.blade(samples, tick), 1.0, size));
            double legacy = heading(legacyBlade(samples, tick));
            if (!Double.isNaN(previous)) {
                turns.add(angle - previous);
                legacyTurns.add(legacy - legacyPrevious);
            }
            previous = angle;
            legacyPrevious = legacy;
        }
        double total = heading(samples.getLast()) - heading(samples.getFirst());
        assertEquals(-140.0, Math.toDegrees(total), 0.1, "the sweep runs 140 degrees right to left");
        double mean = Math.abs(total) / ((end - start) / step);
        for (int index = 0; index < turns.size(); index++) {
            double turn = turns.get(index);
            assertTrue(turn < 0.0, "step " + index + " turns back by " + Math.toDegrees(turn));
            assertTrue(-turn <= mean * 1.01, "step " + index + " turns " + Math.toDegrees(-turn)
                    + " degrees, mean " + Math.toDegrees(mean));
        }
        // The jump this replaces: the largest old step was many average steps at once.
        double legacyLargest = legacyTurns.stream().mapToDouble(Math::abs).max().orElseThrow();
        assertTrue(legacyLargest > 5.0 * mean, "legacy largest step " + Math.toDegrees(legacyLargest));
    }

    /** The spear's level sweep (move 2): nine lines, three per active tick, all at full reach. */
    private static AttackMoveDefinition spearSweep() {
        AttackMoveDefinition sweep = CombatTestData.basicSpear().move(1);
        List<HitboxSample> samples = sweep.hitbox().samples();
        for (int tick = sweep.activeStartTick(); tick <= sweep.activeEndTick(); tick++) {
            assertEquals(3, sweep.hitbox().samplesAt(tick).size(), "sweep samples at tick " + tick);
        }
        assertEquals(9, samples.size());
        return sweep;
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

    private static double legacyBladeLength(SwordGeometry sword, float thirdPersonScale) {
        double length = sword.bladeLengthPixels() / 16.0 * thirdPersonScale;
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
