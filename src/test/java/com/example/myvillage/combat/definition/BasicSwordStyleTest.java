package com.example.myvillage.combat.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BasicSwordStyleTest {
    @Test
    void centralDefinitionMatchesTheFiveMoveContract() {
        List<AttackMoveDefinition> moves = BasicSwordStyle.DEFINITION.moves();

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
                List.of(8, 10, 12, 14, 17),
                moves.stream().map(AttackMoveDefinition::bufferStartTick).toList());
        assertEquals(14, BasicSwordStyle.DEFINITION.comboTimeoutTicks());
        assertEquals(2, BasicSwordStyle.DEFINITION.minimumIntentIntervalTicks());
        assertEquals(5, moves.stream().map(move -> move.hitbox().shapeFamily()).distinct().count());
    }

    @Test
    void animationAndHitboxContractsAreBoundedAndDistinct() {
        List<AttackMoveDefinition> moves = BasicSwordStyle.DEFINITION.moves();
        for (AttackMoveDefinition move : moves) {
            assertEquals(move.id(), move.animation().animationId());
            assertEquals(move.totalTicks(), move.animation().lengthTicks());
            assertTrue(move.bufferStartTick() > move.activeEndTick());
            assertTrue(move.hitbox().horizontalTolerance() <= 0.25);
            assertTrue(move.hitbox().verticalTolerance() <= 0.15);
            assertFalse(move.hitbox().samples().isEmpty());
        }
        assertNotEquals(
                moves.get(1).hitbox().samples(),
                moves.get(3).hitbox().samples());
        assertEquals(0.8, moves.get(4).step().orElseThrow().maximumDistance());
        assertEquals(6, moves.get(4).step().orElseThrow().actionTick());
        assertEquals(0.35, moves.get(4).step().orElseThrow().supportDepth());
    }
}
