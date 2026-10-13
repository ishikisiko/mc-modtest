package com.example.myvillage.cultivation.qi;

import java.util.Objects;
import java.util.function.Supplier;

/** Loads and validates a complete candidate before replacing the running recovery configuration. */
final class QiRulesStore {
    private QiRules current;

    void load(Supplier<QiRules> loader) {
        QiRules candidate = Objects.requireNonNull(loader.get(), "Spiritual-power rules");
        current = candidate;
    }

    QiRules get() {
        if (current == null) throw new IllegalStateException("Spiritual-power runtime has not started");
        return current;
    }

    void clear() {
        current = null;
    }
}
