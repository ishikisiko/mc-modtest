package com.example.myvillage.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A landed hit only changes the held stack's data (durability); that must not replay the equip
 * animation. Drawing the weapon from another slot or switching to another item still does. Empty
 * hands never reach the item (NeoForge's ClientHooks answers those itself).
 */
final class CombatWeaponItemTest {
    @Test
    void aChangeOfDataAloneDoesNotReequip() {
        assertFalse(CombatWeaponItem.reequips(true, false));
    }

    @Test
    void aSlotChangeOrAnotherItemStillReequips() {
        assertTrue(CombatWeaponItem.reequips(true, true), "same item drawn from another slot");
        assertTrue(CombatWeaponItem.reequips(false, false), "another item in the same slot");
        assertTrue(CombatWeaponItem.reequips(false, true), "another item from another slot");
    }
}
