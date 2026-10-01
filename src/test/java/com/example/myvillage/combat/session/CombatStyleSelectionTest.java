package com.example.myvillage.combat.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.definition.CombatTestData;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The server runs the held weapon's style, and a weapon change stops an action. */
final class CombatStyleSelectionTest {
    private static final ResourceLocation WORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final CombatStyles STYLES = CombatTestData.styles();
    private static final CombatStyleDefinition BASIC = CombatTestData.basicSword();

    @Test
    void registeredWeaponStartsMoveOneOfItsStyle() {
        Optional<CombatStyleDefinition> held = STYLES.styleForItem(CombatTestData.QINGFENG_SWORD);
        assertNull(CombatSessionManager.weaponFailure(held, null));
        CombatSession session = new CombatSession(held.orElseThrow());
        CombatSession.StartEvent start = session.acceptIntent(0, CombatTestData.QINGFENG_SWORD, WORLD, 0.0F)
                .start().orElseThrow();
        assertEquals(ResourceLocation.fromNamespaceAndPath("myvillage", "basic_sword_01_thrust"), start.move().id());
        assertEquals(CombatTestData.QINGFENG_SWORD, start.weaponId());
    }

    @Test
    void unregisteredItemIsNotAWeapon() {
        Optional<CombatStyleDefinition> held = STYLES.styleForItem(ResourceLocation.withDefaultNamespace("stick"));
        assertTrue(held.isEmpty());
        assertEquals(CombatStopReason.WEAPON_CHANGED, CombatSessionManager.weaponFailure(held, null));
        CombatSession idle = new CombatSession(BASIC);
        assertEquals(CombatStopReason.WEAPON_CHANGED, CombatSessionManager.weaponFailure(held, idle));
    }

    @Test
    void changingWeaponMidActionStopsAsWeaponChanged() {
        CombatSession session = new CombatSession(BASIC);
        session.acceptIntent(0, CombatTestData.QINGFENG_SWORD, WORLD, 0.0F);
        // Same style keeps the action; an unregistered item or another style stops it.
        assertNull(CombatSessionManager.weaponFailure(Optional.of(BASIC), session));
        assertEquals(CombatStopReason.WEAPON_CHANGED, CombatSessionManager.weaponFailure(Optional.empty(), session));
        CombatStyleDefinition other = otherStyle();
        assertEquals(CombatStopReason.WEAPON_CHANGED, CombatSessionManager.weaponFailure(Optional.of(other), session));
        assertThrows(IllegalStateException.class, () -> session.useStyle(other));

        CombatSession.StopEvent stop = session.interrupt(CombatStopReason.WEAPON_CHANGED).orElseThrow();
        assertEquals(CombatStopReason.WEAPON_CHANGED, stop.reason());
        // Between actions another style is allowed and the next intent runs it from its first move.
        assertNull(CombatSessionManager.weaponFailure(Optional.of(other), session));
    }

    @Test
    void switchingStyleBetweenActionsRestartsTheComboAndKeepsTheRevision() {
        CombatSession session = new CombatSession(BASIC);
        CombatSession.StartEvent first = session.acceptIntent(0, CombatTestData.QINGFENG_SWORD, WORLD, 0.0F)
                .start().orElseThrow();
        session.tick(first.move().totalTicks());
        assertEquals(1, session.nextMoveIndex());

        session.useStyle(BASIC);
        assertEquals(1, session.nextMoveIndex(), "the same style keeps the combo");

        CombatStyleDefinition other = otherStyle();
        session.useStyle(other);
        assertEquals(other, session.style());
        assertEquals(0, session.nextMoveIndex());
        CombatSession.StartEvent next = session.acceptIntent(first.move().totalTicks() + 1L,
                ResourceLocation.fromNamespaceAndPath("myvillage", "other_sword"), WORLD, 0.0F).start().orElseThrow();
        assertEquals(other.move(0).id(), next.move().id());
        assertTrue(next.revision() > first.revision(), "clients never see the revision go backwards");
    }

    /** A second style with the basic moves under new ids. */
    private static CombatStyleDefinition otherStyle() {
        List<AttackMoveDefinition> moves = BASIC.moves().stream().map(move -> {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("myvillage", "other_" + move.id().getPath());
            return new AttackMoveDefinition(
                    id, move.displayKey(), move.kind(), move.totalTicks(), move.activeStartTick(),
                    move.activeEndTick(), move.damageMultiplier(), move.maximumTargets(), move.range(),
                    move.bufferStartTick(), move.chainTick(), move.reaction(),
                    new com.example.myvillage.combat.definition.AnimationDefinition(id, move.totalTicks()),
                    move.hitbox(), move.step(), move.feedback(), move.camera());
        }).toList();
        return new CombatStyleDefinition(
                ResourceLocation.fromNamespaceAndPath("myvillage", "other_sword"),
                BASIC.readyIdleAnimation(), BASIC.modeEnterAnimation(),
                BASIC.comboTimeoutTicks(), BASIC.minimumIntentIntervalTicks(), moves);
    }
}
