package com.example.myvillage.combat.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The bundled {@code myvillage:basic_sword} style (the Qingfeng sword's moves). The accepted
 * Qingfeng numbers are pinned here once, as literals: they are the values the removed
 * {@code BasicSwordStyle} constants and {@code CombatCameraFx} arrays held, proven equal value for
 * value before those were deleted (D5).
 */
class BasicSwordStyleTest {
    private static final CombatStyleDefinition STYLE = CombatTestData.basicSword();

    /** Every hitbox sample of the five moves, as the removed Java generators produced them. */
    private static final List<List<HitboxSample>> SAMPLES = List.of(
            // basic_sword_01_thrust
            List.of(
                    new HitboxSample(3, 0.0, 1.05, 0.55, 0.0, 1.2, 2.55, 0.16, 0.25),
                    new HitboxSample(4, 0.0, 1.05, 0.55, 0.0, 1.2, 2.95, 0.16, 0.25)),
            // basic_sword_02_horizontal_cut
            List.of(
                    new HitboxSample(4, 0.3686184199300463, 1.15, 0.25810939635797076,
                            2.293625724009177, 1.15, 1.606014021782929, 0.18, 0.34),
                    new HitboxSample(5, 0.0, 1.15, 0.45, 0.0, 1.15, 2.8, 0.18, 0.34),
                    new HitboxSample(6, -0.3686184199300463, 1.15, 0.25810939635797076,
                            -2.293625724009177, 1.15, 1.606014021782929, 0.18, 0.34)),
            // basic_sword_03_rising_cut
            List.of(
                    new HitboxSample(5, -0.75, 0.45, 0.55, 0.95, 1.9, 2.55, 0.2, 0.2),
                    new HitboxSample(6, 0.0, 0.6, 0.55, 0.0, 1.825, 2.55, 0.2, 0.2),
                    new HitboxSample(7, 0.75, 0.75, 0.55, -0.95, 1.75, 2.55, 0.2, 0.2)),
            // basic_sword_04_diagonal_cut
            List.of(
                    new HitboxSample(6, 0.75, 1.9, 0.55, -0.95, 0.45, 2.75, 0.28, 0.22),
                    new HitboxSample(7, -0.0, 1.825, 0.55, -0.0, 0.6, 2.75, 0.28, 0.22),
                    new HitboxSample(8, -0.75, 1.75, 0.55, 0.95, 0.75, 2.75, 0.28, 0.22)),
            // basic_sword_05_lunge_thrust
            List.of(
                    new HitboxSample(7, 0.0, 1.05, 0.55, 0.0, 1.2, 2.8, 0.19, 0.25),
                    new HitboxSample(8, 0.0, 1.05, 0.55, 0.0, 1.2, 3.15, 0.19, 0.25),
                    new HitboxSample(9, 0.0, 1.05, 0.55, 0.0, 1.2, 3.5, 0.19, 0.25)));

    @Test
    void pinnedQingfengBaselineIdsShapesSamplesFeedbackAndCamera() {
        assertEquals(id("basic_sword"), STYLE.id());
        assertEquals(id("sword_ready_idle"), STYLE.readyIdleAnimation());
        assertEquals(id("sword_mode_enter"), STYLE.modeEnterAnimation());
        WeaponDefinition qingfeng = CombatTestData.qingfeng();
        assertEquals(new WeaponDefinition(
                id("qingfeng_sword"), id("basic_sword"),
                id("combat/qingfeng_first_person.json"), id("combat/qingfeng_sword_geometry.json"),
                Optional.of("sword")), qingfeng);

        List<String> paths = List.of(
                "basic_sword_01_thrust", "basic_sword_02_horizontal_cut", "basic_sword_03_rising_cut",
                "basic_sword_04_diagonal_cut", "basic_sword_05_lunge_thrust");
        List<String> shapes = List.of(
                "center_thrust", "horizontal_arc_110", "rising_diagonal", "descending_diagonal_thick",
                "long_lunge_thrust");
        for (int index = 0; index < 5; index++) {
            AttackMoveDefinition move = STYLE.move(index);
            assertEquals(id(paths.get(index)), move.id());
            assertEquals("combat.myvillage.move." + paths.get(index), move.displayKey());
            assertEquals(new AnimationDefinition(move.id(), move.totalTicks()), move.animation());
            assertEquals(shapes.get(index), move.hitbox().shapeFamily());
            assertEquals(0.20, move.hitbox().horizontalTolerance());
            assertEquals(0.12, move.hitbox().verticalTolerance());
            List<HitboxSample> expected = SAMPLES.get(index);
            List<HitboxSample> actual = move.hitbox().samples();
            assertEquals(expected.size(), actual.size(), move.id().toString());
            for (int sample = 0; sample < expected.size(); sample++) {
                // Exact: record equality compares every double bit for bit (Double.compare).
                assertEquals(expected.get(sample), actual.get(sample), move.id() + " sample " + sample);
            }
        }

        assertEquals(
                List.of(new ReactionDefinition(9, 0.30, 0.00, 0.0), new ReactionDefinition(9, 0.35, 0.00, 0.3),
                        new ReactionDefinition(10, 0.30, 0.20, 0.0), new ReactionDefinition(13, 0.60, 0.00, 0.0),
                        new ReactionDefinition(16, 2.00, 0.25, 0.0)),
                STYLE.moves().stream().map(AttackMoveDefinition::reaction).toList());

        ResourceLocation thrust = id("combat.sword.thrust");
        ResourceLocation cut = id("combat.sword.cut");
        ResourceLocation hit = id("combat.sword.hit");
        ResourceLocation hitHeavy = id("combat.sword.hit_heavy");
        Optional<ResourceLocation> layer = Optional.of(id("combat.sword.impact_heavy"));
        assertEquals(
                List.of(
                        new MoveFeedback(thrust, 1.25F, hit, Optional.empty(), false, 1.5F, 0.25F, 0.0F),
                        new MoveFeedback(cut, 1.10F, hit, Optional.empty(), false, 2.0F, 0.30F, 0.0F),
                        new MoveFeedback(cut, 1.20F, hit, Optional.empty(), false, 2.0F, 0.30F, -35.0F),
                        new MoveFeedback(cut, 0.95F, hitHeavy, layer, true, 3.0F, 0.50F, 40.0F),
                        new MoveFeedback(thrust, 0.90F, hitHeavy, layer, true, 4.0F, 0.80F, 0.0F)),
                STYLE.moves().stream().map(AttackMoveDefinition::feedback).toList());

        // Formerly CombatCameraFx's HIT_PITCH_KICK, HIT_ROLL_KICK, HIT_FOV_PUNCH, 0.3 x SWING_LEAN_SIGN,
        // and the 2.0 surge of the only step of at least one block.
        assertEquals(
                List.of(
                        new CameraCues(0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                        new CameraCues(0.0F, 0.5F, 0.0F, 0.3F, 0.0F),
                        new CameraCues(0.6F, 0.0F, 0.0F, -0.3F, 0.0F),
                        new CameraCues(-0.8F, 0.4F, 0.0F, 0.3F, 0.0F),
                        new CameraCues(-1.2F, 0.0F, -3.0F, 0.0F, 2.0F)),
                STYLE.moves().stream().map(AttackMoveDefinition::camera).toList());
    }


    @Test
    void centralDefinitionMatchesTheFiveMoveContract() {
        List<AttackMoveDefinition> moves = STYLE.moves();

        assertEquals(5, moves.size());
        assertEquals(
                List.of(11, 13, 15, 17, 20),
                moves.stream().map(AttackMoveDefinition::totalTicks).toList());
        assertEquals(
                List.of(0.90, 0.95, 1.00, 1.10, 1.25),
                moves.stream().map(AttackMoveDefinition::damageMultiplier).toList());
        assertEquals(
                List.of(1, 3, 2, 3, 2),
                moves.stream().map(AttackMoveDefinition::maximumTargets).toList());
        assertEquals(
                List.of(3.0, 2.8, 2.8, 3.0, 3.5),
                moves.stream().map(AttackMoveDefinition::range).toList());
        assertEquals(
                List.of(3, 4, 5, 6, 7),
                moves.stream().map(AttackMoveDefinition::activeStartTick).toList());
        assertEquals(
                List.of(4, 6, 7, 8, 9),
                moves.stream().map(AttackMoveDefinition::activeEndTick).toList());
        assertEquals(
                List.of(3, 4, 5, 6, 7),
                moves.stream().map(AttackMoveDefinition::bufferStartTick).toList());
        assertEquals(
                List.of(7, 8, 10, 13, 20),
                moves.stream().map(AttackMoveDefinition::chainTick).toList());
        assertEquals(
                List.of(9, 9, 10, 13, 16),
                moves.stream().map(move -> move.reaction().hitstunTicks()).toList());
        assertEquals(
                List.of(0.30, 0.35, 0.30, 0.60, 2.00),
                moves.stream().map(move -> move.reaction().slideDistance()).toList());
        assertEquals(
                List.of(0.00, 0.00, 0.20, 0.00, 0.25),
                moves.stream().map(move -> move.reaction().lift()).toList());
        assertEquals(
                List.of(0.0, 0.3, 0.0, 0.0, 0.0),
                moves.stream().map(move -> move.reaction().lateralBias()).toList());
        assertEquals(14, STYLE.comboTimeoutTicks());
        assertEquals(2, STYLE.minimumIntentIntervalTicks());
        assertEquals(5, moves.stream().map(move -> move.hitbox().shapeFamily()).distinct().count());
    }

    @Test
    void animationAndHitboxContractsAreBoundedAndDistinct() {
        List<AttackMoveDefinition> moves = STYLE.moves();
        for (AttackMoveDefinition move : moves) {
            assertEquals(move.id(), move.animation().animationId());
            assertEquals(move.totalTicks(), move.animation().lengthTicks());
            assertTrue(move.bufferStartTick() >= move.activeStartTick());
            assertTrue(move.bufferStartTick() < move.totalTicks());
            assertTrue(move.chainTick() > move.activeEndTick());
            assertTrue(move.chainTick() <= move.totalTicks());
            assertTrue(move.step().isPresent(), move.id().toString());
            assertTrue(move.step().orElseThrow().actionTick() < move.activeStartTick(), move.id().toString());
            assertTrue(move.hitbox().horizontalTolerance() <= 0.25);
            assertTrue(move.hitbox().verticalTolerance() <= 0.15);
            assertFalse(move.hitbox().samples().isEmpty());
        }
        assertNotEquals(
                moves.get(1).hitbox().samples(),
                moves.get(3).hitbox().samples());
        assertEquals(
                List.of(2, 3, 4, 5, 6),
                moves.stream().map(move -> move.step().orElseThrow().actionTick()).toList());
        assertEquals(
                List.of(0.30, 0.25, 0.30, 0.45, 1.40),
                moves.stream().map(move -> move.step().orElseThrow().maximumDistance()).toList());
        for (AttackMoveDefinition move : moves) {
            assertEquals(0.35, move.step().orElseThrow().supportDepth());
        }
        assertEquals(moves.get(4).totalTicks(), moves.get(4).chainTick());
    }

    @Test
    void everyMoveHasPresentationFeedback() {
        List<MoveFeedback> feedback = STYLE.moves().stream().map(AttackMoveDefinition::feedback).toList();
        ResourceLocation thrust = id("combat.sword.thrust");
        ResourceLocation cut = id("combat.sword.cut");
        assertEquals(List.of(thrust, cut, cut, cut, thrust), feedback.stream().map(MoveFeedback::swingSound).toList());
        assertEquals(
                List.of(MoveKind.THRUST, MoveKind.CUT, MoveKind.CUT, MoveKind.CUT, MoveKind.THRUST),
                STYLE.moves().stream().map(AttackMoveDefinition::kind).toList());
        assertTrue(feedback.get(4).heavyHit());
        assertTrue(feedback.get(3).heavyHit());
        assertFalse(feedback.get(0).heavyHit());
        assertEquals(
                List.of(1.5F, 2.0F, 2.0F, 3.0F, 4.0F),
                feedback.stream().map(MoveFeedback::hitStopTicks).toList());
        assertEquals(
                List.of(0.25F, 0.30F, 0.30F, 0.50F, 0.80F),
                feedback.stream().map(MoveFeedback::cameraTrauma).toList());
        assertEquals(
                List.of(0.0F, 0.0F, -35.0F, 40.0F, 0.0F),
                feedback.stream().map(MoveFeedback::cutRollDegrees).toList());
    }

    @Test
    void moveDefinitionRejectsBrokenBufferAndChainWindows() {
        AttackMoveDefinition thrust = STYLE.move(0);
        assertThrows(IllegalArgumentException.class, () -> copy(thrust, thrust.activeStartTick() - 1, thrust.chainTick()));
        assertThrows(IllegalArgumentException.class, () -> copy(thrust, thrust.bufferStartTick(), thrust.activeEndTick()));
        assertThrows(IllegalArgumentException.class, () -> copy(thrust, thrust.bufferStartTick(), thrust.totalTicks() + 1));
        assertEquals(thrust, copy(thrust, thrust.bufferStartTick(), thrust.chainTick()));
        assertThrows(IllegalArgumentException.class, () -> new StepDefinition(2, 1.7, 0.35));
        assertEquals(1.6, new StepDefinition(2, 1.6, 0.35).maximumDistance());
    }

    @Test
    void chainAndBufferPredicatesFollowTheDefinition() {
        AttackMoveDefinition thrust = STYLE.move(0);
        assertFalse(thrust.acceptsBuffer(thrust.activeStartTick() - 1));
        assertTrue(thrust.acceptsBuffer(thrust.activeStartTick()));
        assertTrue(thrust.acceptsBuffer(thrust.totalTicks() - 1));
        assertFalse(thrust.acceptsBuffer(thrust.totalTicks()));
        assertFalse(thrust.chainsAt(thrust.chainTick() - 1));
        assertTrue(thrust.chainsAt(thrust.chainTick()));
    }

    @Test
    void reactionAndFeedbackBoundsAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new ReactionDefinition(-1, 0.3, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new ReactionDefinition(9, -0.1, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new ReactionDefinition(9, 0.3, 0.0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new ReactionDefinition(9, 0.3, Double.NaN, 0.0));
        ResourceLocation cut = id("combat.sword.cut");
        ResourceLocation hit = id("combat.sword.hit");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MoveFeedback(cut, 1.0F, hit, Optional.empty(), false, 7.0F, 0.3F, 0.0F));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MoveFeedback(cut, 1.0F, hit, Optional.empty(), false, 2.0F, 1.5F, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> new CameraCues(0.0F, 0.0F, 0.0F, 0.0F, -1.0F));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }

    private static AttackMoveDefinition copy(AttackMoveDefinition move, int bufferStart, int chainTick) {
        return new AttackMoveDefinition(
                move.id(),
                move.displayKey(),
                move.kind(),
                move.totalTicks(),
                move.activeStartTick(),
                move.activeEndTick(),
                move.damageMultiplier(),
                move.maximumTargets(),
                move.range(),
                bufferStart,
                chainTick,
                move.reaction(),
                move.animation(),
                move.hitbox(),
                move.step(),
                move.feedback(),
                move.camera());
    }
}
