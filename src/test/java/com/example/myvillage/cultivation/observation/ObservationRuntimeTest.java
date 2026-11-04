package com.example.myvillage.cultivation.observation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.stats.StatsRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ObservationRuntimeTest {
    private static final String REALM = "myvillage:stats_realm";
    @Test void unassignedEntitiesAreMortalButMalformedAssignmentsAreUnknown() {
        assertEquals(StatsRules.bundled().realmKey("mortal", 0),
                ObservationRuntime.storedRealm(new CompoundTag()).orElseThrow());
        var data = new CompoundTag(); data.putString(REALM, "qi_refining");
        assertTrue(ObservationRuntime.storedRealm(data).isEmpty());
        var realm = new CompoundTag(); data.put(REALM, realm);
        assertTrue(ObservationRuntime.storedRealm(data).isEmpty());
        realm.putString("realm", "myvillage:qi_refining"); realm.putInt("stage", 3);
        assertEquals(StatsRules.bundled().realmKey("qi_refining", 3),
                ObservationRuntime.storedRealm(data).orElseThrow());
        realm.putString("stage", "3");
        assertTrue(ObservationRuntime.storedRealm(data).isEmpty());
        realm.putInt("stage", -1);
        assertTrue(ObservationRuntime.storedRealm(data).isEmpty());
        realm.putInt("stage", 9);
        assertTrue(ObservationRuntime.storedRealm(data).isEmpty());
        realm.putString("realm", "myvillage:unknown"); realm.putInt("stage", 0);
        assertTrue(ObservationRuntime.storedRealm(data).isEmpty());
    }
    @Test void coarseChatContainsTheSameComponentForEveryLayerInItsInterval() {
        var stats = StatsRules.bundled(); var rules = ObservationRules.bundled();
        var observer = stats.realmKey("qi_refining", 0); var name = Component.literal("张青");
        var first = ObservationRuntime.message(name, rules.read(observer, stats.realmKey("qi_refining", 3)));
        assertEquals("张青", name.getString(), "Rendering must not modify the entity name");
        for (int stage = 4; stage <= 5; stage++) assertEquals(first,
                ObservationRuntime.message(name, rules.read(observer, stats.realmKey("qi_refining", stage))));
        assertNotEquals(first, ObservationRuntime.message(name, rules.read(observer, stats.realmKey("qi_refining", 1))));
        var mortal = ObservationRuntime.message(name, rules.read(observer, stats.realmKey("mortal", 0)));
        assertTrue(mortal.getString().contains("未感知到修为"));
    }
}
