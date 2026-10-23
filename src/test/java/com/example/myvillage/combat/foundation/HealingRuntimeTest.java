package com.example.myvillage.combat.foundation;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class HealingRuntimeTest {
    @Test void absentEntitiesCannotCreateHealingOrModifiersOrBreakLifecycleCleanup() {
        var source = ResourceLocation.parse("myvillage:fixture");
        assertEquals(0, HealingRuntime.heal(null, 10, source));
        assertFalse(HealingRuntime.modifier(null, source, .5, 20));
        assertEquals(HealingRuntime.UNATTRIBUTED, HealingRuntime.currentSource(null));
        assertDoesNotThrow(() -> HealingRuntime.primaryHit(null, null, 10));
        assertDoesNotThrow(() -> HealingRuntime.clear(null, source));
        assertDoesNotThrow(() -> HealingRuntime.clear(null));
        assertDoesNotThrow(() -> HealingRuntime.clear());
    }
}
