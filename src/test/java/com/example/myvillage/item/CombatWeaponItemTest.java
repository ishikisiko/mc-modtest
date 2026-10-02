package com.example.myvillage.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The combat weapon's re-equip rule is the vanilla one minus the durability replay. Component
 * patches are modelled as maps keyed by component type name; "damage" stands for
 * {@code minecraft:damage}.
 */
final class CombatWeaponItemTest {
    private static final String DAMAGE = "damage";

    @Test
    void aDurabilityChangeAloneDoesNotReplayTheEquipAnimation() {
        assertFalse(CombatWeaponItem.reequips(true, Map.of(), Map.of(DAMAGE, Optional.of(1)), DAMAGE));
        assertFalse(CombatWeaponItem.reequips(true,
                Map.of(DAMAGE, Optional.of(3)), Map.of(DAMAGE, Optional.of(4)), DAMAGE));
    }

    @Test
    void identicalStacksDoNotReplayTheEquipAnimation() {
        Map<String, Optional<?>> named = Map.of("custom_name", Optional.of("Qingfeng"), DAMAGE, Optional.of(2));
        assertFalse(CombatWeaponItem.reequips(true, named, named, DAMAGE));
        assertFalse(CombatWeaponItem.reequips(true, Map.of(), Map.of(), DAMAGE));
    }

    @Test
    void anotherItemOrCountStillReplaysTheEquipAnimation() {
        assertTrue(CombatWeaponItem.reequips(false, Map.of(), Map.of(), DAMAGE));
    }

    @Test
    void enchantingOrRenamingStillReplaysTheEquipAnimation() {
        assertTrue(CombatWeaponItem.reequips(true,
                Map.of(DAMAGE, Optional.of(3)),
                Map.of(DAMAGE, Optional.of(3), "enchantments", Optional.of("sharpness 1")), DAMAGE));
        assertTrue(CombatWeaponItem.reequips(true,
                Map.of("custom_name", Optional.of("Qingfeng")),
                Map.of("custom_name", Optional.of("Old Blade")), DAMAGE));
        assertTrue(CombatWeaponItem.reequips(true,
                Map.of("custom_name", Optional.of("Qingfeng")), Map.of(), DAMAGE), "a name removed");
    }
}
