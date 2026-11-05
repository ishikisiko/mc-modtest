package com.example.myvillage.talisman;

import java.util.function.BooleanSupplier;

/** 同一服务器调用内预留费用；传送取消或抛错都只退回一次。 */
public final class TravelPayment {
    private TravelPayment() { }
    public static boolean execute(BooleanSupplier reserve, BooleanSupplier travel, Runnable refund, Runnable complete) {
        if (!reserve.getAsBoolean()) return false;
        boolean moved = false;
        try {
            moved = travel.getAsBoolean();
            if (moved) complete.run();
            return moved;
        } finally {
            if (!moved) refund.run();
        }
    }
}
