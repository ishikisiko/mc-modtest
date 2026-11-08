package com.example.myvillage.sim.sect;

import com.example.myvillage.sim.data.SectProgressionRules;
import java.math.BigInteger;
import java.util.Map;
import java.util.TreeMap;

/** 每种品级分别记账，不自动兑换；余额和结算月份同属命簿。 */
public final class StipendAccount {
    public long throughMonth = -1;
    private final TreeMap<String, BigInteger> stones = new TreeMap<>();
    public Map<String, BigInteger> balance() { return Map.copyOf(stones); }
    public void replace(Map<String, BigInteger> value) {
        var copy = new TreeMap<String, BigInteger>();
        for (var e : value.entrySet()) {
            if (!SectProgressionRules.STONES.contains(e.getKey()) || e.getValue().signum() < 0)
                throw new IllegalArgumentException("供奉账目无效");
            if (e.getValue().signum() > 0) copy.put(e.getKey(), e.getValue());
        }
        stones.clear(); stones.putAll(copy);
    }
    public void credit(String stone, BigInteger amount) {
        if (!SectProgressionRules.STONES.contains(stone) || amount.signum() < 0) throw new IllegalArgumentException("供奉金额无效");
        if (amount.signum() > 0) stones.merge(stone, amount, BigInteger::add);
    }
    public void clear() { stones.clear(); }
}
