package com.example.myvillage.stats.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class RealmHealthPolicyTest {
    @Test
    void woundedGrowthAndRepeatedRefreshPreserveCurrentHealth() {
        float health = RealmHealthPolicy.refreshed(11, 20, 30);
        assertEquals(11, health);
        assertEquals(health, RealmHealthPolicy.refreshed(health, 30, 30));
    }

    @Test
    void downgradeClampsOnlyTheOverflow() {
        assertEquals(20, RealmHealthPolicy.refreshed(28, 30, 20));
        assertEquals(11, RealmHealthPolicy.refreshed(11, 30, 20));
    }

    @Test
    void deathRespawnStartsFullAndEndReturnPreservesWounds() {
        assertEquals(30, RealmHealthPolicy.respawned(20, 20, 30, false));
        assertEquals(11, RealmHealthPolicy.respawned(11, 30, 30, true));
        assertEquals(20, RealmHealthPolicy.respawned(28, 30, 20, true));
    }

    @Test
    void endCloneUsesOriginalHealthRatherThanTheNewBodysVanillaClampedHealth() {
        // Vanilla has already clamped the new body's copied Health to 20 at Clone.
        // Passing the original body retains its wound without refilling the realm maximum.
        assertEquals(53, RealmHealthPolicy.respawned(53, 60, 60, true));
        assertEquals(30, RealmHealthPolicy.respawned(53, 60, 30, true));
    }
}
