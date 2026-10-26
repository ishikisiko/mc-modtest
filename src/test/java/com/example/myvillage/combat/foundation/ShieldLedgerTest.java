package com.example.myvillage.combat.foundation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.cultivation.fate.PillAegisEffects;
import com.example.myvillage.cultivation.fate.PillAegisState;
import java.util.List;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ShieldLedgerTest {
    private static final ResourceLocation A = ResourceLocation.parse("myvillage:a");
    private static final ResourceLocation B = ResourceLocation.parse("myvillage:b");
    private static final ResourceLocation C = ResourceLocation.parse("other:a");
    private static ShieldLedger ledger() { return new ShieldLedger(new ShieldRules(2, 10, 15, 100, 8)); }

    @Test void paidGrantPlansCapacityBeforePaymentAndDoesNotReplaceOnPaymentFailure() {
        var ledger = new ShieldLedger(new ShieldRules(1, 20, 20, 100, 10));
        var a = net.minecraft.resources.ResourceLocation.parse("myvillage:a");
        var b = net.minecraft.resources.ResourceLocation.parse("myvillage:b");
        assertTrue(ledger.grant(a, 8, 80, 10));
        var before = ledger.entries(10);
        var paid = new java.util.concurrent.atomic.AtomicInteger();
        assertFalse(ledger.grantPaid(b, 10, 50, 11, () -> { paid.incrementAndGet(); return true; }));
        assertEquals(0, paid.get()); assertEquals(before, ledger.entries(11));
        assertFalse(ledger.grantPaid(a, 15, 50, 11, () -> false));
        assertEquals(before, ledger.entries(11));
        assertTrue(ledger.grantPaid(a, 15, 50, 11, () -> { paid.incrementAndGet(); return true; }));
        assertEquals(1, paid.get()); assertEquals(15, ledger.total(11)); assertEquals(61, ledger.entries(11).get(0).expiresAt());
    }

    @Test void sameSourceReplacesRemainingAndDeadlineInsteadOfAddingStacks() {
        var ledger = ledger();
        assertTrue(ledger.grant(A, 8, 20, 100));
        assertEquals(3, ledger.absorb(3, 101, false).blocked());
        assertTrue(ledger.grant(A, 4, 30, 102));
        assertEquals(4, ledger.total(102));
        assertEquals(List.of(new ShieldLedger.Entry(A, 4, 132)), ledger.entries(102));
        for (int i = 0; i < 100; i++) assertTrue(ledger.grant(A, 4, 30, 102));
        assertEquals(4, ledger.total(102));
        assertEquals(4, ledger.total(131));
        assertEquals(0, ledger.total(132));
    }

    @Test void capsBoundEachSourceTheirTotalTheirCountAndDuration() {
        var ledger = ledger();
        assertTrue(ledger.grant(A, Float.MAX_VALUE, Integer.MAX_VALUE, 100));
        assertEquals(new ShieldLedger.Entry(A, 10, 200), ledger.entries(100).getFirst());
        assertTrue(ledger.grant(B, 10, 50, 100));
        assertEquals(15, ledger.total(100));
        assertEquals(5, ledger.entries(100).getFirst().amount());
        assertFalse(ledger.grant(C, 1, 50, 100));
        assertEquals(2, ledger.entries(100).size());
        assertTrue(ledger.grant(A, 3, 60, 100));
        assertEquals(8, ledger.total(100));
    }

    @Test void saturatedTotalRefusesNewSourceWithoutEvictingTheExistingOne() {
        var ledger = new ShieldLedger(new ShieldRules(3, 10, 10, 100, 8));
        assertTrue(ledger.grant(A, 10, 20, 0));
        assertFalse(ledger.grant(B, 1, 20, 0));
        assertEquals(List.of(new ShieldLedger.Entry(A, 10, 20)), ledger.entries(0));
        assertTrue(ledger.grant(A, 10, 30, 1));
        assertEquals(10, ledger.total(1));
    }

    @Test void consumeByEarliestExpiryThenFullSourceIdRegardlessOfGrantOrder() {
        var ledger = new ShieldLedger(new ShieldRules(3, 10, 30, 100, 8));
        ledger.grant(C, 3, 20, 0);
        ledger.grant(B, 4, 10, 0);
        ledger.grant(A, 2, 20, 0);
        var hit = ledger.absorb(7, 1, false);
        assertEquals(0, hit.damage());
        assertEquals(7, hit.blocked());
        assertEquals(List.of(new ShieldLedger.Consumption(B, 4, 0, true),
                new ShieldLedger.Consumption(A, 2, 0, true),
                new ShieldLedger.Consumption(C, 1, 2, false)), hit.consumed());
        assertEquals(List.of(new ShieldLedger.Entry(C, 2, 20)), ledger.entries(1));
        var broken = ledger.absorb(5, 2, false);
        assertEquals(3, broken.damage());
        assertTrue(broken.consumed().getFirst().broken());
        assertTrue(ledger.isEmpty());
    }

    @Test void expiredSourcesCannotAbsorbOrOccupySlotsAndReadsDoNotRefreshTime() {
        var ledger = ledger();
        ledger.grant(A, 5, 10, 100);
        ledger.grant(B, 5, 20, 100);
        assertEquals(10, ledger.total(109));
        assertEquals(5, ledger.total(110));
        assertTrue(ledger.grant(C, 5, 5, 110));
        assertEquals(5, ledger.total(115));
        var hit = ledger.absorb(6, 120, false);
        assertEquals(6, hit.damage());
        assertEquals(0, hit.blocked());
        assertTrue(ledger.isEmpty());
    }

    @Test void bypassAndInvalidDamageNeverConsumeThePoolOrEmitBreakReceipts() {
        var ledger = ledger();
        ledger.grant(A, 5, 20, 100);
        for (float damage : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            var hit = ledger.absorb(damage, 101, false);
            assertEquals(0, hit.blocked());
            assertTrue(hit.consumed().isEmpty());
            assertEquals(5, ledger.total(101));
        }
        var bypass = ledger.absorb(1000, 101, true);
        assertEquals(1000, bypass.damage());
        assertEquals(0, bypass.blocked());
        assertEquals(5, ledger.total(101));
        assertEquals(0, ledger.absorb(1000, 120, true).blocked());
        assertTrue(ledger.isEmpty());
    }

    @Test void malformedGrantAndDeadlineOverflowCannotReplaceAnActiveSource() {
        var ledger = ledger();
        ledger.grant(A, 5, 20, 100);
        var previous = ledger.entries(100);
        for (float invalid : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY})
            assertFalse(ledger.grant(A, invalid, 10, 101));
        assertFalse(ledger.grant(null, 5, 10, 101));
        assertFalse(ledger.grant(A, 5, 0, 101));
        assertFalse(ledger.grant(A, 5, -1, 101));
        assertFalse(ledger.grant(A, 5, 10, -1));
        assertFalse(ledger.grant(A, 5, 10, Long.MAX_VALUE));
        assertEquals(previous, ledger.entries(101));
    }

    @Test void removalAndClearAffectOnlyTheirOwnedSourcesAndSnapshotsAreImmutable() {
        var ledger = ledger();
        ledger.grant(A, 5, 20, 100);
        ledger.grant(B, 4, 30, 100);
        var snapshot = ledger.entries(100);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertTrue(ledger.removeSource(A, 101));
        assertFalse(ledger.removeSource(A, 101));
        assertEquals(4, ledger.total(101));
        assertEquals(2, snapshot.size());
        ledger.clear();
        assertEquals(0, ledger.total(101));
        assertTrue(ledger.isEmpty());
    }

    @Test void oldPillPoolThenTemporaryPoolThenVanillaHeartsConsumeResidualOnly() {
        var pill = new PillAegisState(2, 200, 700);
        var ledger = ledger();
        ledger.grant(A, 3, 30, 100);
        // 盔甲、魔法和类型抗性已把原始 20 点伤害结算为 9；此处不再次减伤。
        var first = PillAegisEffects.absorb(pill, 101, 9, false);
        var second = ledger.absorb(first.damage(), 101, false);
        assertEquals(2, first.blocked());
        assertEquals(3, second.blocked());
        assertEquals(4, second.damage());
        float vanillaHearts = 6;
        float vanillaSpent = Math.min(vanillaHearts, second.damage());
        assertEquals(2, vanillaHearts - vanillaSpent);
        assertEquals(0, second.damage() - vanillaSpent);
        assertEquals(700, first.next().cooldownReadyAt());
        assertEquals(0, first.next().shield());
        ledger.clear();
        assertEquals(2, vanillaHearts - vanillaSpent);
    }

    @Test void regrantAndRandomHitsConserveIntegerDamageAndBoundEveryPool() {
        Random random = new Random(4639);
        var ledger = ledger();
        ResourceLocation[] ids = {A, B, C};
        for (int tick = 0; tick < 1000; tick++) {
            if (random.nextBoolean()) ledger.grant(ids[random.nextInt(ids.length)], random.nextInt(20) + 1,
                    random.nextInt(200) + 1, tick);
            float before = ledger.total(tick);
            float damage = random.nextInt(25);
            var hit = ledger.absorb(damage, tick, false);
            assertEquals(damage, hit.damage() + hit.blocked());
            assertEquals(before, ledger.total(tick) + hit.blocked());
            assertTrue(ledger.entries(tick).size() <= 2);
            assertTrue(ledger.total(tick) <= 15);
            assertTrue(ledger.entries(tick).stream().allMatch(entry -> entry.amount() <= 10));
        }
    }
}
