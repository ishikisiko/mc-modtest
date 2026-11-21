package com.example.myvillage.sim.runtime.economy;

import com.example.myvillage.sim.economy.TaskReward;
import com.example.myvillage.spiritstone.*;
import java.math.BigInteger;
import com.example.myvillage.item.SpiritStoneTags;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.example.myvillage.sim.runtime.commerce.CommerceInventory;

/** 所有扣料和产出先在副本中规划，失败保留原背包及命簿。 */
public final class EconomyInventory {
    public record Prepared(SpiritStoneSources.Access access, List<ItemStack> items, String result) {
        public boolean ready() { return result.equals("ok"); }
        public boolean commit() { return ready() && access.commit(items); }
    }
    private EconomyInventory() { }
    public static Prepared reward(ServerPlayer player, TaskReward reward, ItemStack required, int count, boolean tribute) {
        return reward(player, reward, required, count, tribute, ItemStack.EMPTY);
    }
    public static Prepared reward(ServerPlayer player, TaskReward reward, ItemStack required, int count, boolean tribute, ItemStack award) {
        var access = SpiritStoneSources.open(player); var copies = access.copies();
        if (tribute) {
            if (!SpiritStoneStorage.remove(copies, s -> s.is(SpiritStoneTags.LOW_GRADE_SPIRIT_STONE), count)) return new Prepared(access, copies, "missing_items");
        } else if (count > 0 && !remove(copies.subList(0, 36), required, count)) return new Prepared(access, copies, "missing_items");
        for (var e : reward.stones().entrySet()) {
            String id = switch (e.getKey()) { case "low" -> "low_grade"; case "middle" -> "medium_grade"; case "high" -> "high_grade"; default -> throw new IllegalArgumentException("未知灵石"); };
            if (!insert(copies, new ItemStack(CommerceInventory.item("yuanxian:" + id + "_spirit_stone")), e.getValue(), true))
                return new Prepared(access, copies, "inventory_full");
        }
        if (!award.isEmpty() && !insert(copies, award, award.getCount(), false))
            return new Prepared(access, copies, "inventory_full");
        return new Prepared(access, copies, "ok");
    }
    public static Prepared goods(ServerPlayer player, ItemStack stack, int count) {
        var access = SpiritStoneSources.open(player); var copies = access.copies();
        return new Prepared(access, copies, insert(copies, stack, count, false) ? "ok" : "inventory_full");
    }
    static Prepared tianhengPayment(ServerPlayer player, int cost, ItemStack goods) {
        var access = SpiritStoneSources.open(player);
        var copies = access.copies();
        String result = collectStones(copies, cost);
        if (result.equals("ok") && !goods.isEmpty() && !insert(copies, goods, goods.getCount(), false))
            result = "inventory_full";
        return new Prepared(access, copies, result);
    }

    static Prepared tianhengReceipt(ServerPlayer player, long refund, ItemStack award) {
        var access = SpiritStoneSources.open(player);
        var copies = access.copies();
        boolean ready = returnStones(copies, BigInteger.valueOf(refund));
        if (ready && !award.isEmpty()) ready = insert(copies, award, award.getCount(), false);
        return new Prepared(access, copies, ready ? "ok" : "inventory_full");
    }

    private static String collectStones(List<ItemStack> copies, int cost) {
        if (cost <= 0) throw new IllegalArgumentException("invalid_offer");
        var remaining = BigInteger.valueOf(cost);
        var tiers = SpiritStoneDenominations.get().tiers();
        for (var tier : tiers.reversed()) {
            var money = new ItemStack(CommerceInventory.item(tier.item().toString()));
            var accepts = SpiritStoneStorage.ordinary(money);
            var count = copies.stream().map(stack -> SpiritStoneStorage.count(stack, accepts))
                    .reduce(BigInteger.ZERO, BigInteger::add).min(remaining.divide(tier.units()));
            if (count.signum() == 0) continue;
            if (!SpiritStoneStorage.remove(copies, accepts, count)) throw new IllegalStateException("灵石支付副本改变");
            remaining = remaining.subtract(count.multiply(tier.units()));
        }
        if (remaining.signum() == 0) return "ok";
        for (var tier : tiers) {
            if (tier.units().compareTo(remaining) <= 0) continue;
            var money = new ItemStack(CommerceInventory.item(tier.item().toString()));
            if (SpiritStoneStorage.remove(copies, SpiritStoneStorage.ordinary(money), 1))
                return returnStones(copies, tier.units().subtract(remaining)) ? "ok" : "inventory_full";
        }
        return "missing_items";
    }

    private static boolean returnStones(List<ItemStack> copies, BigInteger value) {
        var denominations = SpiritStoneDenominations.get();
        for (var change : denominations.decompose(value).entrySet()) {
            var money = new ItemStack(CommerceInventory.item(denominations.tier(change.getKey()).item().toString()));
            var left = change.getValue();
            for (var stack : copies) {
                if (left.signum() == 0) break;
                left = SpiritStoneStorage.insert(stack, money, left, false);
            }
            if (left.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                    || SpiritStoneStorage.insertLoose(copies, money, left.intValueExact(), 36) > 0) return false;
        }
        return true;
    }

    static boolean insert(List<ItemStack> copies, ItemStack stack, int count, boolean bags) {
        int left = bags ? SpiritStoneStorage.insertBags(copies, stack, count, true) : count;
        return SpiritStoneStorage.insertLoose(copies, stack, left, 36) == 0;
    }
    static boolean remove(List<ItemStack> copies, ItemStack required, int count) {
        for (var stack : copies) if (count > 0 && ItemStack.isSameItemSameComponents(stack, required)) {
            int take = Math.min(count, stack.getCount()); stack.shrink(take); count -= take;
        }
        return count == 0;
    }
    public static String describe(TaskReward reward) {
        var parts = new ArrayList<String>();
        if (reward.contribution() > 0) parts.add("宗门贡献 " + reward.contribution());
        if (reward.reputation() > 0) parts.add("江湖声望 " + reward.reputation());
        reward.stones().forEach((kind, count) -> parts.add(switch (kind) { case "low" -> "下品灵石 "; case "middle" -> "中品灵石 "; default -> "上品灵石 "; } + count));
        return String.join("，", parts);
    }
}
