package com.example.myvillage.wilderness.runtime;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ShulkerBirthInheritanceTest {
    private static final String REALM = "myvillage:stats_realm";
    private static CompoundTag owned() {
        var parent = new CompoundTag(); var assignment = new CompoundTag();
        assignment.putString("realm", "myvillage:golden_core"); assignment.putInt("stage", 1);
        parent.put(REALM, assignment.copy());
        var birth = assignment.copy(); birth.putString("location", "end_city");
        birth.putString("dimension", "minecraft:the_end");
        parent.put(VanillaMonsterRealms.BIRTH, birth); return parent;
    }
    @Test void onlyCompletedMatchingOwnedBirthIsInherited() {
        var parent = owned(); var child = new CompoundTag();
        assertTrue(VanillaMonsterRealms.canInheritShulkerBirth(parent, child));
        assertEquals("end_city", parent.getCompound(VanillaMonsterRealms.BIRTH).getString("location"));
        assertTrue(child.isEmpty());
        parent.getCompound(VanillaMonsterRealms.BIRTH).putBoolean("pending", true);
        assertFalse(VanillaMonsterRealms.canInheritShulkerBirth(parent, child));
    }
    @Test void legacyUnassignedAndForeignAssignmentsStayUnassigned() {
        assertFalse(VanillaMonsterRealms.canInheritShulkerBirth(new CompoundTag(), new CompoundTag()));
        var foreign = owned(); foreign.remove(VanillaMonsterRealms.BIRTH);
        assertFalse(VanillaMonsterRealms.canInheritShulkerBirth(foreign, new CompoundTag()));
        var noAssignment = owned(); noAssignment.remove(REALM);
        assertFalse(VanillaMonsterRealms.canInheritShulkerBirth(noAssignment, new CompoundTag()));
        var mismatch = owned(); mismatch.getCompound(REALM).putInt("stage", 2);
        assertFalse(VanillaMonsterRealms.canInheritShulkerBirth(mismatch, new CompoundTag()));
        var child = new CompoundTag(); child.putString(REALM, "foreign");
        assertFalse(VanillaMonsterRealms.canInheritShulkerBirth(owned(), child));
        child = new CompoundTag(); child.put(VanillaMonsterRealms.BIRTH, new CompoundTag());
        assertFalse(VanillaMonsterRealms.canInheritShulkerBirth(owned(), child));
    }
    @Test void malformedOrUnknownRealmDoesNotBecomeAValidBirth() {
        var malformed = owned(); malformed.getCompound(VanillaMonsterRealms.BIRTH).putDouble("stage", 1);
        assertFalse(VanillaMonsterRealms.canInheritShulkerBirth(malformed, new CompoundTag()));
        var unknown = owned();
        unknown.getCompound(VanillaMonsterRealms.BIRTH).putString("realm", "foreign:realm");
        unknown.getCompound(REALM).putString("realm", "foreign:realm");
        assertFalse(VanillaMonsterRealms.canInheritShulkerBirth(unknown, new CompoundTag()));
        var malformedPending = owned(); malformedPending.getCompound(VanillaMonsterRealms.BIRTH).putString("pending", "false");
        assertFalse(VanillaMonsterRealms.canInheritShulkerBirth(malformedPending, new CompoundTag()));
    }
}
