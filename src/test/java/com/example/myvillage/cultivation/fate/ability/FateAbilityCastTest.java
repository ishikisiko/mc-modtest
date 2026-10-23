package com.example.myvillage.cultivation.fate.ability;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FateAbilityCastTest {
    @Test void missingQualificationCooldownAndInsufficientPowerNeverPrepareOrSpend() {
        for (var gate : List.of(new long[]{0, 0, 100}, new long[]{1, 1, 100}, new long[]{1, 0, 9})) {
            var calls = new ArrayList<String>();
            assertFalse(FateAbilityCast.attempt(gate[0] == 1, gate[1], gate[2], 10,
                    () -> { calls.add("准备"); return Optional.of(() -> calls.add("执行")); },
                    amount -> { calls.add("扣费"); return true; }, amount -> calls.add("退款"), () -> calls.add("冷却")));
            assertTrue(calls.isEmpty());
        }
    }

    @Test void executorRejectionNeverSpendsOrStartsCooldown() {
        var calls = new ArrayList<String>();
        assertFalse(FateAbilityCast.attempt(true, 0, 100, 10,
                () -> { calls.add("准备拒绝"); return Optional.empty(); },
                amount -> { calls.add("扣费"); return true; }, amount -> calls.add("退款"), () -> calls.add("冷却")));
        assertEquals(List.of("准备拒绝"), calls);
    }

    @Test void failedServiceDebitCannotExecuteOrStartCooldown() {
        var calls = new ArrayList<String>();
        assertFalse(FateAbilityCast.attempt(true, 0, 100, 10,
                () -> { calls.add("准备"); return Optional.of(() -> calls.add("执行")); },
                amount -> { calls.add("扣费拒绝"); return false; }, amount -> calls.add("退款"), () -> calls.add("冷却")));
        assertEquals(List.of("准备", "扣费拒绝"), calls);
    }

    @Test void successChargesExactlyOnceAfterPreparationAndThenCommitsCooldown() {
        var calls = new ArrayList<String>();
        var power = new AtomicLong(100);
        assertTrue(FateAbilityCast.attempt(true, 0, power.get(), 10,
                () -> { calls.add("准备"); assertEquals(100, power.get()); return Optional.of(() -> calls.add("执行")); },
                amount -> { calls.add("扣费"); power.addAndGet(-amount); return true; },
                amount -> { calls.add("退款"); power.addAndGet(amount); }, () -> calls.add("冷却")));
        assertEquals(90, power.get());
        assertEquals(List.of("准备", "扣费", "执行", "冷却"), calls);
    }

    @Test void preparationExceptionDoesNotTouchPowerOrCooldown() {
        var power = new AtomicLong(100);
        assertThrows(IllegalStateException.class, () -> FateAbilityCast.attempt(true, 0, power.get(), 10,
                () -> { throw new IllegalStateException("准备失败"); },
                amount -> { power.addAndGet(-amount); return true; }, amount -> power.addAndGet(amount),
                () -> fail("不应记冷却")));
        assertEquals(100, power.get());
    }

    @Test void unexpectedExecutionFailureRefundsAndDoesNotCommitCooldown() {
        var power = new AtomicLong(100);
        assertThrows(IllegalStateException.class, () -> FateAbilityCast.attempt(true, 0, power.get(), 10,
                () -> Optional.of(() -> { throw new IllegalStateException("执行失败"); }),
                amount -> { power.addAndGet(-amount); return true; }, amount -> power.addAndGet(amount),
                () -> fail("不应记冷却")));
        assertEquals(100, power.get());
    }
}
