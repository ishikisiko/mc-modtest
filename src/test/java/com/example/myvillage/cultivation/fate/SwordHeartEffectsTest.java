package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.session.CombatSession;
import com.example.myvillage.combat.session.CombatSkillSession;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SwordHeartEffectsTest {
    private static SwordHeartState dodge(SwordHeartState state, long persistent, long elapsed,
            boolean enemy, double amount) {
        return SwordHeartEffects.dodged(state, persistent, 100 + elapsed, 100, 4, 60, 160, enemy, amount);
    }

    @Test void onlyActualPositiveEnemyAttackInsideFirstFourTicksEarnsAnOpening() {
        for (int tick = 0; tick < 4; tick++)
            assertEquals(new SwordHeartState(260, 360), dodge(SwordHeartState.EMPTY, 200, tick, true, 4));
        for (long tick : new long[] {-1, 4, 5, 100})
            assertSame(SwordHeartState.EMPTY, dodge(SwordHeartState.EMPTY, 200, tick, true, 4));
        // The runtime supplies false for environmental, sustained, self, allied, derived and reflected damage.
        assertSame(SwordHeartState.EMPTY, dodge(SwordHeartState.EMPTY, 200, 0, false, 4));
        for (double amount : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertSame(SwordHeartState.EMPTY, dodge(SwordHeartState.EMPTY, 200, 0, true, amount));
    }

    @Test void dodgeDimensionClockNeverBecomesThePersistentCooldownClock() {
        var state = SwordHeartEffects.dodged(SwordHeartState.EMPTY, 10000, 103, 100,
                4, 60, 160, true, 1);
        assertEquals(new SwordHeartState(10060, 10160), state);
        assertSame(state, dodge(state, 10159, 0, true, 1));
        assertEquals(new SwordHeartState(10220, 10320), dodge(state, 10160, 0, true, 1));
    }

    @Test void repeatedContactsInSameDodgeCannotRefreshOpeningOrCooldown() {
        var state = dodge(SwordHeartState.EMPTY, 200, 0, true, 1);
        assertSame(state, dodge(state, 201, 1, true, 1));
        assertSame(state, dodge(state, 202, 2, true, 100));
        assertSame(state, dodge(state, 203, 3, true, 1));
    }

    @Test void threeSecondExclusiveExpiryBoundaryAndSwordOnlyCritical() {
        var state = dodge(SwordHeartState.EMPTY, 200, 0, true, 4);
        assertEquals(1.5, SwordHeartEffects.criticalMultiplier(state, 200, .5, true));
        assertEquals(1.5, SwordHeartEffects.criticalMultiplier(state, 259, .5, true));
        assertEquals(1, SwordHeartEffects.criticalMultiplier(state, 260, .5, true));
        assertEquals(1, SwordHeartEffects.criticalMultiplier(state, 200, .5, false));
        for (double bonus : new double[] {0, -.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(1, SwordHeartEffects.criticalMultiplier(state, 200, bonus, true));
        assertEquals(new SwordHeartState(0, 360), state.expire(260));
    }

    @Test void missesAndRejectedDamageKeepOpeningAndOnlyFirstAcceptedTargetClaimsIt() {
        var ready = new SwordHeartState(260, 360);
        assertSame(ready, SwordHeartEffects.consume(ready, 201, 260, false));
        assertSame(ready, SwordHeartEffects.consume(ready, 201, 259, true));
        var firstTarget = SwordHeartEffects.consume(ready, 201, 260, true);
        assertEquals(new SwordHeartState(0, 360), firstTarget);
        assertSame(firstTarget, SwordHeartEffects.consume(firstTarget, 201, 260, true));
        assertEquals(1, SwordHeartEffects.criticalMultiplier(firstTarget, 201, .5, true));
        assertSame(ready, SwordHeartEffects.consume(ready, 260, 260, true));
    }

    @Test void committedShieldOrAbsorptionDamageConsumesWithoutAHealthLossRequirement() {
        var ready = new SwordHeartState(260, 360);
        assertEquals(new SwordHeartState(0, 360), SwordHeartEffects.consume(ready, 201, 260,
                SwordHeartEffects.acceptedDamage(4, 0)));
        assertEquals(new SwordHeartState(0, 360), SwordHeartEffects.consume(ready, 201, 260,
                SwordHeartEffects.acceptedDamage(0, 4)));
        assertFalse(SwordHeartEffects.acceptedDamage(0, 0));
        assertFalse(SwordHeartEffects.acceptedDamage(-1, -1));
        assertFalse(SwordHeartEffects.acceptedDamage(Double.NaN, Double.POSITIVE_INFINITY));
    }

    @Test void logoutDeathExpiryAndResetDiscardOpeningButKeepCooldown() {
        var ready = new SwordHeartState(260, 360);
        var cleared = ready.clearOpening();
        assertEquals(new SwordHeartState(0, 360), cleared);
        assertSame(cleared, cleared.clearOpening());
        assertSame(cleared, dodge(cleared, 359, 0, true, 4));
        var state = FateState.empty().withSwordHeart(ready);
        assertEquals(cleared, state.afterDeath().swordHeart());
        assertEquals(cleared, state.resetChoices().swordHeart());
        assertEquals(ready, state.timers(10, 2, .5, 20).swordHeart());
        assertEquals(ready, state.withPillAegis(new PillAegisState(1, 400, 500)).swordHeart());
        assertEquals(ready, state.withTemperedBones(new TemperedBonesState(1, false)).swordHeart());
    }

    @Test void codecKeepsCooldownAcrossReloadAndAbsentLegacyStateGrantsNothing() {
        for (var state : List.of(SwordHeartState.EMPTY, new SwordHeartState(260, 360), new SwordHeartState(0, 360))) {
            var json = SwordHeartState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
            assertEquals(state, SwordHeartState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
        assertEquals(SwordHeartState.EMPTY,
                SwordHeartState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
        assertThrows(IllegalArgumentException.class, () -> new SwordHeartState(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SwordHeartState(0, -1));
        var full = FateState.CODEC.encodeStart(JsonOps.INSTANCE,
                FateState.empty().withSwordHeart(new SwordHeartState(260, 360))).getOrThrow().getAsJsonObject();
        assertEquals(new SwordHeartState(260, 360), FateState.CODEC.parse(JsonOps.INSTANCE, full).getOrThrow().swordHeart());
        full.remove("sword_heart");
        assertEquals(SwordHeartState.EMPTY, FateState.CODEC.parse(JsonOps.INSTANCE, full).getOrThrow().swordHeart());
    }

    @Test void deadlinesSaturateAndInvalidWindowConfigurationCannotGrant() {
        assertEquals(new SwordHeartState(Long.MAX_VALUE, Long.MAX_VALUE),
                dodge(SwordHeartState.EMPTY, Long.MAX_VALUE - 1, 0, true, 1));
        assertSame(SwordHeartState.EMPTY, SwordHeartEffects.dodged(SwordHeartState.EMPTY,
                200, 100, 100, 0, 60, 160, true, 1));
        assertSame(SwordHeartState.EMPTY, SwordHeartEffects.dodged(SwordHeartState.EMPTY,
                200, 100, 100, 4, 0, 160, true, 1));
        assertSame(SwordHeartState.EMPTY, SwordHeartEffects.dodged(SwordHeartState.EMPTY,
                200, 100, 100, 4, 60, 0, true, 1));
        assertSame(SwordHeartState.EMPTY, dodge(SwordHeartState.EMPTY, -1, 0, true, 1));
    }

    @Test void authoredFateAndPiercingWaveOwnTheirNumbersAndWaveHasNoQiRefund() throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/myvillage/myvillage/innate_fate/innate_sword_heart.json")) {
            assertNotNull(stream);
            var definition = FateDefinition.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getOrThrow();
            assertEquals(4, definition.effect("heart_window_ticks"));
            assertEquals(60, definition.effect("heart_opening_ticks"));
            assertEquals(160, definition.effect("heart_cooldown"));
            assertEquals(.5, definition.effect("heart_critical_bonus"));
            assertEquals("myvillage:innate_sword_heart_wave", definition.derivedSkill().orElseThrow().toString());
        }
        var styles = CombatTestData.styles();
        var skill = styles.skill(ResourceLocation.fromNamespaceAndPath("myvillage", "innate_sword_heart_wave")).orElseThrow();
        assertEquals(1.5, skill.damageMultiplier());
        assertTrue(skill.maximumTargets() > 1);
        var flight = new CombatSkillSession(skill, skill.id(), 0, 100, 42);
        assertTrue(flight.release(100, Vec3.ZERO, new Vec3(0, 0, 1)));
        assertEquals(0, flight.paidPower());
        assertEquals(0, flight.claimHitRefund());
        var derived = new CombatSession(CombatTestData.basicSword());
        derived.startExternal(skill.releaseMove(styles, 0), 100, CombatTestData.QINGFENG_SWORD,
                ResourceLocation.withDefaultNamespace("overworld"), 0);
        assertTrue(derived.isExternal());
        assertTrue(derived.markAttempted(10));
        assertFalse(derived.markAttempted(10));
        assertTrue(derived.markAttempted(11));
    }
}
