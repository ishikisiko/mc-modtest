package com.example.myvillage.combat.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.session.CombatSession;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The bundled {@code myvillage:basic_fist} style (the Xuantie gauntlet's moves). The accepted
 * values are pinned here as literals, as {@link BasicSwordStyleTest} pins Qingfeng's; a deliberate
 * retune updates this test and {@code tools/tests/test_combat_style_baseline.py} together.
 */
class BasicFistStyleTest {
    private static final CombatStyles STYLES = CombatTestData.styles();
    private static final CombatStyleDefinition STYLE = CombatTestData.basicFist();
    private static final List<String> MOVE_PATHS = List.of(
            "basic_fist_01_straight_punch", "basic_fist_02_horizontal_palm", "basic_fist_03_uppercut",
            "basic_fist_04_chop", "basic_fist_05_step_double_strike");

    @Test
    void loadsFromTheBundledDataWithFiveMovesInComboOrder() {
        assertEquals(id("basic_fist"), STYLE.id());
        assertEquals(id("basic_fist_ready_idle"), STYLE.readyIdleAnimation());
        assertEquals(id("basic_fist_mode_enter"), STYLE.modeEnterAnimation());
        assertEquals(MOVE_PATHS.size(), STYLE.moves().size());
        for (int index = 0; index < MOVE_PATHS.size(); index++) {
            AttackMoveDefinition move = STYLE.move(index);
            assertEquals(id(MOVE_PATHS.get(index)), move.id());
            assertEquals("combat.myvillage.move." + MOVE_PATHS.get(index), move.displayKey());
            assertEquals(new AnimationDefinition(move.id(), move.totalTicks()), move.animation());
            CombatStyles.MoveRef ref = STYLES.move(move.id()).orElseThrow();
            assertSame(STYLE, ref.style());
            assertEquals(index, ref.index());
        }
        assertTrue(STYLES.isReadyIdle(id("basic_fist_ready_idle")));
    }

    @Test
    void resolvesByTheGauntletItemIdWithTheFistFamilyAsAPair() {
        assertEquals(new WeaponDefinition(
                id("xuantie_gauntlet"), id("basic_fist"),
                id("combat/xuantie_gauntlet_first_person.json"), id("combat/xuantie_gauntlet_geometry.json"),
                Optional.of("fist"), true), CombatTestData.xuantie());
        assertSame(STYLE, STYLES.styleForItem(CombatTestData.XUANTIE_GAUNTLET).orElseThrow());
        assertSame(CombatTestData.basicSword(), STYLES.styleForItem(CombatTestData.QINGFENG_SWORD).orElseThrow());
    }

    @Test
    void pinnedTimingDamageAndReaction() {
        List<AttackMoveDefinition> moves = STYLE.moves();
        assertEquals(List.of(9, 10, 10, 11, 11), moves.stream().map(AttackMoveDefinition::totalTicks).toList());
        assertEquals(List.of(3, 4, 4, 5, 6), moves.stream().map(AttackMoveDefinition::activeStartTick).toList());
        assertEquals(List.of(4, 5, 5, 6, 7), moves.stream().map(AttackMoveDefinition::activeEndTick).toList());
        assertEquals(List.of(3, 4, 4, 5, 6), moves.stream().map(AttackMoveDefinition::bufferStartTick).toList());
        assertEquals(List.of(6, 7, 7, 8, 11), moves.stream().map(AttackMoveDefinition::chainTick).toList());
        assertEquals(List.of(0.80, 0.85, 0.90, 1.00, 1.25),
                moves.stream().map(AttackMoveDefinition::damageMultiplier).toList());
        assertEquals(List.of(1, 2, 1, 2, 2), moves.stream().map(AttackMoveDefinition::maximumTargets).toList());
        assertEquals(List.of(1.9, 1.9, 1.8, 2.0, 2.2), moves.stream().map(AttackMoveDefinition::range).toList());
        assertEquals(
                List.of(new ReactionDefinition(7, 0.20, 0.00, 0.0), new ReactionDefinition(8, 0.25, 0.00, -0.35),
                        new ReactionDefinition(9, 0.15, 0.30, 0.0), new ReactionDefinition(10, 0.30, 0.00, -0.1),
                        new ReactionDefinition(13, 1.10, 0.10, 0.0)),
                moves.stream().map(AttackMoveDefinition::reaction).toList());
        assertEquals(List.of(2, 3, 3, 4, 5),
                moves.stream().map(move -> move.step().orElseThrow().actionTick()).toList());
        assertEquals(List.of(0.25, 0.2, 0.2, 0.3, 0.9),
                moves.stream().map(move -> move.step().orElseThrow().maximumDistance()).toList());
        assertEquals(List.of(2, 6, 4, 4, 4), moves.stream().map(move -> move.hitbox().samples().size()).toList());
        assertEquals(10, STYLE.comboTimeoutTicks());
        assertEquals(1, STYLE.minimumIntentIntervalTicks());
        assertEquals(
                List.of(MoveKind.THRUST, MoveKind.CUT, MoveKind.CUT, MoveKind.CUT, MoveKind.THRUST),
                moves.stream().map(AttackMoveDefinition::kind).toList());
    }

    @Test
    void identityIsShortFastAndOnlyTheFinisherIsHeavy() {
        for (AttackMoveDefinition move : STYLE.moves()) {
            assertTrue(move.totalTicks() >= 8 && move.totalTicks() <= 11, move.id().toString());
            assertTrue(move.activeEndTick() - move.activeStartTick() + 1 <= 2, move.id().toString());
            assertTrue(move.range() >= 1.8 && move.range() <= 2.2, move.id().toString());
            assertTrue(move.maximumTargets() <= 2, move.id().toString());
        }
        List<MoveFeedback> feedback = STYLE.moves().stream().map(AttackMoveDefinition::feedback).toList();
        assertEquals(List.of(false, false, false, false, true), feedback.stream().map(MoveFeedback::heavyHit).toList());
        // Every stop leaves the first-person swing room to catch up before the server total
        // (SwingClock: start + stop + 2 ticks); longer stops on the 11-tick finisher were dropped in capture.
        assertEquals(List.of(1.0F, 1.5F, 1.5F, 2.0F, 1.5F), feedback.stream().map(MoveFeedback::hitStopTicks).toList());
        assertEquals(List.of(0.18F, 0.22F, 0.25F, 0.32F, 0.6F), feedback.stream().map(MoveFeedback::cameraTrauma).toList());
        // Each fist move is shorter than the sword move at its combo position.
        for (int index = 0; index < 5; index++) {
            assertTrue(STYLE.move(index).totalTicks() < CombatTestData.basicSword().move(index).totalTicks());
        }
    }

    @Test
    void comboChainsFourMovesAndTheFinisherPlaysOut() {
        AttackMoveDefinition finisher = STYLE.moves().getLast();
        assertEquals(finisher.totalTicks(), finisher.chainTick());
        assertFalse(finisher.chainsAt(finisher.totalTicks() - 1));
        ResourceLocation world = ResourceLocation.withDefaultNamespace("overworld");
        CombatSession session = new CombatSession(STYLE);
        long tick = 100;
        CombatSession.StartEvent current = session.acceptIntent(tick, CombatTestData.XUANTIE_GAUNTLET, world, 0.0F)
                .start().orElseThrow();
        for (int index = 0; index < STYLE.moves().size(); index++) {
            AttackMoveDefinition move = STYLE.move(index);
            assertEquals(move.id(), current.move().id());
            assertEquals(CombatSession.IntentDecision.BUFFERED, session.acceptIntent(
                    tick + move.bufferStartTick(), CombatTestData.XUANTIE_GAUNTLET, world, 0.0F).decision());
            if (move.chainTick() - 1 >= move.bufferStartTick()) {
                assertTrue(session.tick(tick + move.chainTick() - 1L).start().isEmpty());
            }
            current = session.tick(tick + move.chainTick()).start().orElseThrow();
            tick = current.startTick();
        }
        assertEquals(STYLE.move(0).id(), current.move().id());
    }

    @Test
    void weaponFamilyIsOptionalAndLowerSnakeCase() {
        JsonObject weapon = JsonParser.parseString("""
                {"schema": 1, "item": "myvillage:probe", "style": "myvillage:basic_fist",
                 "first_person_rig": "myvillage:combat/r.json", "geometry": "myvillage:combat/g.json"}
                """).getAsJsonObject();
        assertEquals(Optional.empty(), CombatDataLoader.parseWeapon("probe.json", weapon).family());
        weapon.addProperty("family", "fist");
        assertEquals(Optional.of("fist"), CombatDataLoader.parseWeapon("probe.json", weapon).family());
        weapon.addProperty("family", "Fist Style");
        CombatDataException error = assertThrows(CombatDataException.class,
                () -> CombatDataLoader.parseWeapon("probe.json", weapon));
        assertTrue(error.getMessage().contains("family"), error.getMessage());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
