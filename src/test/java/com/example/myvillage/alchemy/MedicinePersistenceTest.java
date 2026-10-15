package com.example.myvillage.alchemy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicinePersistenceTest {
    @Test void saveLoadPreservesPartialIntervalWithoutOfflineAdvancement() {
        var state = new MedicineState(452, "yangyuan", 52, 8);
        CompoundTag saved = MedicineRuntime.write(state);
        assertEquals(state, MedicineRuntime.read(saved.copy(), AlchemyRules.get()));
        assertEquals(state, MedicineRuntime.read(saved.copy(), AlchemyRules.get()));
        var next = MedicineRuntime.read(saved, AlchemyRules.get()).tick(20);
        assertFalse(next.heal());
        assertEquals(new MedicineState(451, "yangyuan", 51, 9), next.next());
    }

    @Test void malformedSaveCannotCreateUnknownOrOverlongBenefits() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cooldown_ticks", Integer.MAX_VALUE);
        tag.putString("effect", "unknown");
        tag.putInt("effect_ticks", Integer.MAX_VALUE);
        var state = MedicineRuntime.read(tag, AlchemyRules.get());
        assertEquals(AlchemyRules.get().sharedCooldownTicks(), state.cooldownTicks());
        assertEquals("", state.effect());
        tag.putString("effect", "ningshen");
        tag.putInt("healing_elapsed", Integer.MAX_VALUE);
        state = MedicineRuntime.read(tag, AlchemyRules.get());
        assertEquals(AlchemyRules.get().effects().ningshen().durationTicks(), state.effectTicks());
        assertEquals(0, state.healingElapsed());
    }
}
