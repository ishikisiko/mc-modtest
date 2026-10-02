package com.example.myvillage.combat.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class CombatStylesTest {
    private final CombatStyles styles = CombatTestData.styles();

    @Test
    void looksUpStylesWeaponsAndMoves() {
        CombatStyleDefinition basic = styles.style(CombatTestData.BASIC_SWORD).orElseThrow();
        // The default is the first style the index lists; the bundled index keeps the sword first so
        // the ready idle played without a weapon entry stays the sword's.
        assertSame(styles.styles().getFirst(), styles.defaultStyle());
        assertSame(basic, styles.defaultStyle(), "the bundled index must list basic_sword first");
        assertSame(basic, styles.styleForItem(CombatTestData.QINGFENG_SWORD).orElseThrow());
        assertEquals(CombatTestData.BASIC_SWORD, styles.weapon(CombatTestData.QINGFENG_SWORD).orElseThrow().style());
        assertFalse(styles.styleForItem(ResourceLocation.withDefaultNamespace("iron_sword")).isPresent());
        assertFalse(styles.style(id("spear")).isPresent());

        for (int index = 0; index < basic.moves().size(); index++) {
            AttackMoveDefinition move = basic.move(index);
            CombatStyles.MoveRef ref = styles.move(move.id()).orElseThrow();
            assertSame(basic, ref.style());
            assertEquals(index, ref.index());
            assertSame(move, ref.move());
            assertTrue(styles.isMove(move.id()));
        }
        assertFalse(styles.move(id("sword_ready_idle")).isPresent());
        assertTrue(styles.isReadyIdle(id("sword_ready_idle")));
        assertFalse(styles.isReadyIdle(id("sword_mode_enter")));
        assertEquals(id("sword_mode_enter"), basic.modeEnterAnimation());
    }

    @Test
    void registryRejectsInconsistentData() {
        CombatStyleDefinition basic = CombatTestData.basicSword();
        WeaponDefinition qingfeng = CombatTestData.qingfeng();
        assertThrows(IllegalArgumentException.class, () -> new CombatStyles(List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CombatStyles(List.of(basic, basic), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CombatStyles(List.of(basic), List.of(qingfeng, qingfeng)));
        assertThrows(IllegalArgumentException.class, () -> new CombatStyles(List.of(basic), List.of(
                new WeaponDefinition(id("spear"), id("spear_style"), qingfeng.firstPersonRig(), qingfeng.geometry()))));
        CombatStyleDefinition renamed = new CombatStyleDefinition(
                id("other_sword"), basic.readyIdleAnimation(), basic.modeEnterAnimation(),
                basic.comboTimeoutTicks(), basic.minimumIntentIntervalTicks(), basic.moves());
        assertThrows(IllegalArgumentException.class, () -> new CombatStyles(List.of(basic, renamed), List.of()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
