package com.example.myvillage.cultivation.qi;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure arithmetic; percentages come exclusively from the synchronized fate definition. */
public final class OpenMeridians {
    private OpenMeridians() { }

    public static long capacity(long baseCap, double bonus) {
        return com.example.myvillage.sim.cultivation.SpiritualPowerMath.capacity(baseCap,bonus);
    }

    public static long refund(long paid, double fraction) {
        if (paid < 0 || !Double.isFinite(fraction) || fraction < 0 || fraction > 1)
            throw new IllegalArgumentException("Invalid skill power refund");
        if (paid == 0 || fraction == 0) return 0;
        return BigDecimal.valueOf(paid).multiply(BigDecimal.valueOf(fraction))
                .setScale(0, RoundingMode.DOWN).longValueExact();
    }
}
