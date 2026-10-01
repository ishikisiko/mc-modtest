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

/** The bundled {@code myvillage:basic_sword} style (the Qingfeng sword's moves). */
class BasicSwordStyleTest {
    private static final CombatStyleDefinition STYLE = CombatTestData.basicSword();

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
