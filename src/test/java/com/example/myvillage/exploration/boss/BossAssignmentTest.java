package com.example.myvillage.exploration.boss;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossAssignmentTest {
    private final BossRules rules = BossRules.get();
    private final BossRules.Profile dragon = rules.forEntity("minecraft:ender_dragon").orElseThrow();
    private CompoundTag assigned() {
        var data = new CompoundTag();
        data.put(BossAssignment.BIRTH, BossAssignment.birth(dragon));
        data.put(BossAssignment.REALM, BossAssignment.realm(dragon));
        return data;
    }
    @Test void nativeChargeHealingPreservesItsFractionOfTheHealthEnvelope() {
        float scaled = BossAssignment.chargeHealing(10, 960, 300);
        assertEquals(32, scaled);
        assertEquals(10.0 / 300, scaled / 960.0, 1e-8);
        // Twenty-two original charge heals still reach the cap from the original one-third start.
        assertEquals(960, Math.min(960, 320 + 22 * scaled));
        assertEquals(10, BossAssignment.chargeHealing(10, 300, 300));
        assertEquals(0, BossAssignment.chargeHealing(0, 960, 300));
    }
    @Test void malformedHealthEnvelopesCannotCreateChargeHealing() {
        assertEquals(10, BossAssignment.chargeHealing(10, 960, 0));
        assertEquals(10, BossAssignment.chargeHealing(10, Double.NaN, 300));
        assertEquals(10, BossAssignment.chargeHealing(10, 960, Double.POSITIVE_INFINITY));
        assertEquals(-1, BossAssignment.chargeHealing(-1, 960, 300));
    }
    @Test void legacyWoundsAndChargingWitherFractionsSurviveAdoption() {
        assertEquals(400, BossAssignment.adoptedHealth(100, 200, 800));
        assertEquals(320, BossAssignment.adoptedHealth(100, 300, 960));
        assertEquals(0, BossAssignment.adoptedHealth(0, 200, 800));
        assertEquals(800, BossAssignment.adoptedHealth(200, 200, 800));
        assertThrows(IllegalArgumentException.class, () -> BossAssignment.adoptedHealth(2, 0, 800));
    }
    @Test void ownershipPreventsReassignmentEvenForMalformedOrForeignTags() {
        assertTrue(BossAssignment.mayAssign(new CompoundTag()));
        for (String key : new String[]{BossAssignment.BIRTH, BossAssignment.REALM, "myvillage:vanilla_monster_birth"}) {
            var data = new CompoundTag(); data.putString(key, "foreign-or-malformed");
            assertFalse(BossAssignment.mayAssign(data));
        }
        var saved = assigned();
        assertFalse(BossAssignment.mayAssign(saved));
        assertEquals(dragon, BossAssignment.resolve(saved.copy(), dragon.entity(), rules).orElseThrow());
    }
    @Test void persistedAssignmentCannotCrossEntityOrRealmAndRequiresExactNbtTypes() {
        assertTrue(BossAssignment.resolve(assigned(), "minecraft:wither", rules).isEmpty());
        var data = assigned();
        data.getCompound(BossAssignment.REALM).putInt("stage", 2);
        assertTrue(BossAssignment.resolve(data, dragon.entity(), rules).isEmpty());
        data = assigned();
        data.getCompound(BossAssignment.BIRTH).putDouble("stage", 0);
        assertTrue(BossAssignment.resolve(data, dragon.entity(), rules).isEmpty());
        data = assigned();
        data.getCompound(BossAssignment.BIRTH).putString("profile", "future_unknown_profile");
        assertTrue(BossAssignment.resolve(data, dragon.entity(), rules).isEmpty());
        assertFalse(BossAssignment.mayAssign(data));
    }
    @Test void dimensionAndPositionChangesDoNotChangeTheSavedProfile() {
        var data = assigned();
        data.getCompound(BossAssignment.BIRTH).putString("dimension", "minecraft:the_end");
        data.getCompound(BossAssignment.BIRTH).putInt("x", 300000);
        assertEquals(dragon, BossAssignment.resolve(data, dragon.entity(), rules).orElseThrow());
    }
}
