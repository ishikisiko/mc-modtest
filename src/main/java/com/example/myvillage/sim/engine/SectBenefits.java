package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.sect.StipendAccount;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** 用自然历法月份结算供奉；命簿预演年份和管理员推进模拟日不产生灵石。 */
public final class SectBenefits {
    private SectBenefits() { }
    public static boolean observeMonth(SimContext ctx, long month) {
        if (month < 0) throw new IllegalArgumentException("月份不得为负");
        if (month <= ctx.state.sectStipendMonth) return false;
        for (var p : ctx.state.persons.values()) settle(ctx, p.stipend, p.sectId, p.rank, month);
        for (var p : ctx.state.playerMembers.values()) settle(ctx, p.stipend, p.sectId, p.rank, month);
        ctx.state.sectStipendMonth = month;
        return true;
    }
    private static void settle(SimContext ctx, StipendAccount account, int sectId, String rank, long month) {
        Sect sect = ctx.sect(sectId);
        if (account.throughMonth >= 0 && month > account.throughMonth && sect != null && sect.active()) {
            var level = ctx.rules.sectProgression().tier(sect.tierId);
            account.credit(level.stone(), BigInteger.valueOf(month - account.throughMonth).multiply(BigInteger.valueOf(level.stipend(rank))));
        }
        account.throughMonth = Math.max(account.throughMonth, month);
    }
    public static Map<String, BigInteger> pending(SimContext ctx, String id) {
        var p = ctx.state.playerMembers.get(id);
        return p == null ? Map.of() : p.stipend.balance();
    }
    public static boolean claim(SimContext ctx, String id, int sectId, Map<String, BigInteger> expected, Map<String, BigInteger> paid, BooleanSupplier commit) {
        var p = ctx.state.playerMembers.get(id);
        Sect sect = ctx.sect(sectId);
        if (p == null || p.sectId != sectId || sect == null || !sect.active() || expected.isEmpty()
                || !p.stipend.balance().equals(expected)) return false;
        if (paid.isEmpty()) return false;
        var remainder = new java.util.TreeMap<>(expected);
        for (var e : paid.entrySet()) {
            if (e.getValue().signum() <= 0 || e.getValue().compareTo(expected.getOrDefault(e.getKey(), BigInteger.ZERO)) > 0) return false;
            remainder.put(e.getKey(), expected.get(e.getKey()).subtract(e.getValue()));
        }
        if (!commit.getAsBoolean()) return false;
        p.stipend.replace(remainder);
        return true;
    }
}
