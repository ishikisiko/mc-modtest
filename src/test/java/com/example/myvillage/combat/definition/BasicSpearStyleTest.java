package com.example.myvillage.combat.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.session.CombatSession;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The bundled {@code myvillage:basic_spear} style (the Lingxiao Spear's moves). Only the fixed ids
 * and structure from the add-lingxiao-spear design are checked here; the spear's numbers are still
 * initial values and are deliberately not pinned.
 */
class BasicSpearStyleTest {
    private static final CombatStyles STYLES = CombatTestData.styles();
    private static final CombatStyleDefinition STYLE = CombatTestData.basicSpear();
    private static final List<String> MOVE_PATHS = List.of(
            "basic_spear_01_mid_thrust", "basic_spear_02_sweep", "basic_spear_03_rising_flick",
            "basic_spear_04_overhead_smash", "basic_spear_05_dragon_lunge");

    @Test
    void loadsFromTheBundledDataWithFiveMovesInComboOrder() {
        assertEquals(id("basic_spear"), STYLE.id());
        assertEquals(id("spear_ready_idle"), STYLE.readyIdleAnimation());
        assertEquals(id("spear_mode_enter"), STYLE.modeEnterAnimation());
        assertEquals(MOVE_PATHS.size(), STYLE.moves().size());
        for (int index = 0; index < MOVE_PATHS.size(); index++) {
            AttackMoveDefinition move = STYLE.move(index);
            assertEquals(id(MOVE_PATHS.get(index)), move.id());
            assertEquals("combat.myvillage.move." + MOVE_PATHS.get(index), move.displayKey());
            assertEquals(new AnimationDefinition(move.id(), move.totalTicks()), move.animation());
            // Move ids resolve to the spear style at their combo position.
            CombatStyles.MoveRef ref = STYLES.move(move.id()).orElseThrow();
            assertSame(STYLE, ref.style());
            assertEquals(index, ref.index());
            assertSame(move, ref.move());
        }
        assertTrue(STYLES.isReadyIdle(id("spear_ready_idle")));
        assertTrue(STYLES.isReadyIdle(id("sword_ready_idle")));
    }

    @Test
    void resolvesByTheSpearItemId() {
        assertEquals(new WeaponDefinition(
                id("lingxiao_spear"), id("basic_spear"),
                id("combat/lingxiao_spear_first_person.json"), id("combat/lingxiao_spear_geometry.json")),
                CombatTestData.lingxiao());
        assertSame(STYLE, STYLES.styleForItem(CombatTestData.LINGXIAO_SPEAR).orElseThrow());
        assertNotSame(STYLE, STYLES.styleForItem(CombatTestData.QINGFENG_SWORD).orElseThrow());
        assertSame(CombatTestData.basicSword(), STYLES.styleForItem(CombatTestData.QINGFENG_SWORD).orElseThrow());
    }

    @Test
    void finisherCannotChain() {
        AttackMoveDefinition finisher = STYLE.moves().getLast();
        assertEquals(id("basic_spear_05_dragon_lunge"), finisher.id());
        assertEquals(finisher.totalTicks(), finisher.chainTick(), "the finisher plays its full recovery");
        assertFalse(finisher.chainsAt(finisher.totalTicks() - 1));

        // Drive the whole combo with buffered intents: moves 1-4 chain early, the finisher does not.
        ResourceLocation world = ResourceLocation.withDefaultNamespace("overworld");
        CombatSession session = new CombatSession(STYLE);
        long tick = 100;
        CombatSession.StartEvent current = session.acceptIntent(tick, CombatTestData.LINGXIAO_SPEAR, world, 0.0F)
                .start().orElseThrow();
        for (int index = 0; index < STYLE.moves().size(); index++) {
            AttackMoveDefinition move = STYLE.move(index);
            assertEquals(move.id(), current.move().id());
            assertEquals(CombatSession.IntentDecision.BUFFERED, session.acceptIntent(
                    tick + move.bufferStartTick(), CombatTestData.LINGXIAO_SPEAR, world, 0.0F).decision());
            if (move.chainTick() - 1 >= move.bufferStartTick()) {
                assertTrue(session.tick(tick + move.chainTick() - 1L).start().isEmpty());
            }
            CombatSession.TickResult transition = session.tick(tick + move.chainTick());
            current = transition.start().orElseThrow();
            tick = current.startTick();
        }
        // After the finisher's full duration the buffered intent restarts the combo at move 1.
        assertEquals(STYLE.move(0).id(), current.move().id());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
