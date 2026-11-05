package com.example.myvillage.talisman;

import java.util.function.IntPredicate;

/** 浮空到达点只有在下方通道连续畅通并找到支撑时才可用。 */
public final class DescentSafety {
    private DescentSafety() { }
    public static boolean supportedWithin(int halfSteps, IntPredicate clear, IntPredicate supported) {
        if (halfSteps < 0 || halfSteps > 4) throw new IllegalArgumentException("落点探测范围无效");
        for (int step = 0; step <= halfSteps; step++) {
            if (!clear.test(step)) return false;
            if (supported.test(step)) return true;
        }
        return false;
    }
}
