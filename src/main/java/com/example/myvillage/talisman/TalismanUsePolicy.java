package com.example.myvillage.talisman;

/** 所有符共用的服务器激活前置；真正支付仍由 CultivationService 拥有。 */
public final class TalismanUsePolicy {
    private TalismanUsePolicy() { }
    public static boolean ready(boolean prepared, boolean cooldown, long power, int cost) {
        return prepared && !cooldown && cost > 0 && power >= cost;
    }
}
