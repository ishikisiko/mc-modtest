package com.example.myvillage.cultivation.fate.ability;

import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/** 仅属于主动能力的同步准备／扣费事务；普通拒绝不会调用扣费。 */
final class FateAbilityCast {
    private FateAbilityCast() { }

    static boolean attempt(boolean allowed, long remaining, long power, long cost,
            Supplier<Optional<Runnable>> prepare, LongPredicate spend, LongConsumer refund, Runnable commit) {
        if (!allowed || remaining > 0 || cost < 1 || power < cost) return false;
        Optional<Runnable> action = prepare.get();
        if (action.isEmpty() || !spend.test(cost)) return false;
        try {
            action.get().run();
        } catch (RuntimeException exception) {
            refund.accept(cost);
            throw exception;
        }
        commit.run();
        return true;
    }
}
