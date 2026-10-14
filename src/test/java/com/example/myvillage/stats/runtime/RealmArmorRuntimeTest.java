package com.example.myvillage.stats.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.stats.StatsRules;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RealmArmorRuntimeTest {
    private static final String ASSIGNMENT = "myvillage:stats_realm";

    @AfterEach
    void clear() {
        RealmArmorRuntime.clear();
    }

    @Test
    void unassignedMortalAndValidatedGoldenAssignmentRemainDistinct() {
        assertEquals(new RealmArmorRuntime.Scale(1, "unassigned"),
                RealmArmorRuntime.resolveScale(new CompoundTag(), StatsRules.bundled()));
        assertEquals(new RealmArmorRuntime.Scale(1, "unassigned"),
                RealmArmorRuntime.resolveScale(null, StatsRules.bundled()));
        assertEquals(new RealmArmorRuntime.Scale(1, "valid"),
                RealmArmorRuntime.resolveScale(assigned("myvillage:mortal", 0), StatsRules.bundled()));
        var golden = RealmArmorRuntime.resolveScale(assigned("myvillage:golden_core", 2), StatsRules.bundled());
        assertEquals(6.6, golden.factor(), 1e-12);
        assertEquals("valid", golden.status());
    }

    @Test
    void malformedTypesMissingFieldsUnknownRealmAndInvalidStageUseBaseline() {
        CompoundTag wrongTopType = new CompoundTag();
        wrongTopType.putString(ASSIGNMENT, "not a compound");
        invalid(wrongTopType);
        invalid(assigned("myvillage:missing", 0));
        invalid(assigned("myvillage:golden_core", -1));
        invalid(assigned("myvillage:golden_core", 3));
        for (String missing : new String[]{"realm", "stage"}) {
            CompoundTag persistent = assigned("myvillage:golden_core", 2);
            persistent.getCompound(ASSIGNMENT).remove(missing);
            invalid(persistent);
        }
        CompoundTag stageByte = assigned("myvillage:golden_core", 2);
        stageByte.getCompound(ASSIGNMENT).putByte("stage", (byte) 2);
        invalid(stageByte);
        CompoundTag stageLong = assigned("myvillage:golden_core", 2);
        stageLong.getCompound(ASSIGNMENT).putLong("stage", 2);
        invalid(stageLong);
        CompoundTag stageText = assigned("myvillage:golden_core", 2);
        stageText.getCompound(ASSIGNMENT).putString("stage", "2");
        invalid(stageText);
        CompoundTag realmInt = assigned("myvillage:golden_core", 2);
        realmInt.getCompound(ASSIGNMENT).putInt("realm", 2);
        invalid(realmInt);
    }

    @Test
    void nestedContainersReusingOneSourceKeepTheirOwnArmorInputs() {
        DamageSource source = new DamageSource(Holder.direct(new DamageType("test", 0)));
        DamageContainer outer = new DamageContainer(source, 66);
        DamageContainer inner = new DamageContainer(source, 100);
        var outerTrace = new RealmArmorRuntime.ArmorTrace(66, 6.6, 10, 20, "valid", null);
        var innerTrace = new RealmArmorRuntime.ArmorTrace(100, 1, 100, 6, "unassigned", null);
        RealmArmorRuntime.record(outer, outerTrace);
        RealmArmorRuntime.record(inner, innerTrace);
        assertSame(innerTrace, RealmArmorRuntime.take(inner));
        assertSame(outerTrace, RealmArmorRuntime.take(outer));
        assertNull(RealmArmorRuntime.take(inner));
        assertNull(RealmArmorRuntime.take(outer));
    }

    @Test
    void completionDoesNotRewriteInputsAndSkippedPathsHaveNoInventedResult() {
        var input = new RealmArmorRuntime.ArmorTrace(66, 6.6, 10, 20, "valid", null);
        var completed = input.completed(52.8);
        assertNull(input.armorOutput());
        assertEquals(52.8, completed.armorOutput());
        assertEquals(input.damageAtArmor(), completed.damageAtArmor());
        assertEquals(input.normalizedEffectiveArmor(), completed.normalizedEffectiveArmor());
        assertNull(RealmArmorRuntime.take(new DamageContainer(null, 66)));
        assertNull(RealmArmorRuntime.take(null));
    }

    private static CompoundTag assigned(String realm, int stage) {
        CompoundTag assignment = new CompoundTag();
        assignment.putString("realm", realm);
        assignment.putInt("stage", stage);
        CompoundTag persistent = new CompoundTag();
        persistent.put(ASSIGNMENT, assignment);
        return persistent;
    }

    private static void invalid(CompoundTag persistent) {
        assertEquals(new RealmArmorRuntime.Scale(1, "invalid_assignment"),
                RealmArmorRuntime.resolveScale(persistent, StatsRules.bundled()));
    }
}
