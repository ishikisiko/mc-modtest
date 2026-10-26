package com.example.myvillage.entity.flight;

import java.util.function.Supplier;

/** Remove the saved receipt before granting inventory/drop output; repeated lifecycle callbacks are no-ops. */
public final class FlyingSwordEscrow {
    private FlyingSwordEscrow() { }
    public static <T> T claim(Supplier<T> pending, Runnable remove) {
        T value = pending.get();
        if (value != null) remove.run();
        return value;
    }
}
