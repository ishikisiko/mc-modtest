package com.example.myvillage.combat.session;

import com.example.myvillage.combat.definition.CombatSkillDefinition;
import com.example.myvillage.combat.definition.CombatTestData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Cast ownership, final payment and one accepted-hit receipt, independent of victim count. */
class OpenMeridiansSkillRefundTest {
    private final CombatSkillDefinition skill = CombatTestData.styles()
            .skill(ResourceLocation.parse("myvillage:jianqi_jue")).orElseThrow();
    private CombatSkillSession charge(long revision) {
        return new CombatSkillSession(skill, skill.id(), 8, 100, revision);
    }

    @Test void firstSuccessfulHitRefundsActualFinalPaymentOnceAcrossTargetsAndTicks() {
        var cast = charge(1);
        assertEquals(8, cast.costAt(100));
        assertTrue(cast.release(100, Vec3.ZERO, new Vec3(0, 0, 1), 7, .25));
        assertEquals(7, cast.paidPower());
        assertEquals(1, cast.claimHitRefund(), "post-forging paid 7 refunds floor(7/4), not floor(8/4)");
        for (int target = 0; target < 50; target++) assertEquals(0, cast.claimHitRefund());
        cast.advance(cast.nextPosition(), false);
        assertEquals(0, cast.claimHitRefund(), "another wave tick cannot reopen the receipt");
    }

    @Test void chargingOrMissedCastCannotCreateRefundAndEachNewPaidCastOwnsItsReceipt() {
        var first = charge(1);
        assertEquals(0, first.claimHitRefund());
        assertEquals(0, first.paidPower());
        first.release(100, Vec3.ZERO, new Vec3(0, 0, 1), 8, .25);
        // Misses/rejected hurt calls do not call claimHitRefund in CombatSkillActions.
        first.advance(new Vec3(0, 0, .25), true);
        var second = charge(2);
        second.release(120, Vec3.ZERO, new Vec3(0, 0, 1), 12, .25);
        assertEquals(3, second.claimHitRefund());
        assertEquals(0, second.claimHitRefund());
        assertEquals(8, first.paidPower());
    }

    @Test void duplicateReleaseCannotReplacePaymentOrRearmAlreadyClaimedRefund() {
        var cast = charge(1);
        cast.release(100, Vec3.ZERO, new Vec3(0, 0, 1), 8, .25);
        assertEquals(2, cast.claimHitRefund());
        assertFalse(cast.release(120, Vec3.ZERO, new Vec3(1, 0, 0), 100, 1));
        assertEquals(8, cast.paidPower());
        assertEquals(0, cast.claimHitRefund());
        assertEquals(new Vec3(0, 0, 1), cast.direction());
    }

    @Test void zeroPaymentAbsentTraitAndFractionalRemainderGiveNoRefund() {
        var free = charge(1); free.release(100, Vec3.ZERO, new Vec3(0, 0, 1), 0, .25);
        assertEquals(0, free.claimHitRefund());
        var noTrait = charge(2); noTrait.release(100, Vec3.ZERO, new Vec3(0, 0, 1), 12, 0);
        assertEquals(0, noTrait.claimHitRefund());
        var tinyCost = charge(3); tinyCost.release(100, Vec3.ZERO, new Vec3(0, 0, 1), 3, .25);
        assertEquals(0, tinyCost.claimHitRefund());
    }

    @Test void invalidPaymentMetadataCannotPartiallyReleaseTheSession() {
        var cast = charge(1);
        assertThrows(IllegalArgumentException.class,
                () -> cast.release(100, Vec3.ZERO, new Vec3(0, 0, 1), -1, .25));
        assertTrue(cast.charging());
        assertEquals(0, cast.paidPower());
        assertEquals(0, cast.claimHitRefund());
    }
}
