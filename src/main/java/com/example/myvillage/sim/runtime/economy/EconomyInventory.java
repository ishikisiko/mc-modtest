package com.example.myvillage.sim.runtime.economy;

import com.example.myvillage.sim.economy.TaskReward;
import com.example.myvillage.spiritstone.*;
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
        var access = SpiritStoneSources.open(player); var copies = access.copies();
        if (tribute) {
            if (!SpiritStoneStorage.remove(copies, s -> s.is(SpiritStoneTags.LOW_GRADE_SPIRIT_STONE), count)) return new Prepared(access, copies, "missing_items");
        } else if (count > 0 && !remove(copies.subList(0, 36), required, count)) return new Prepared(access, copies, "missing_items");
        for (var e : reward.stones().entrySet()) {
            String id = switch (e.getKey()) { case "low" -> "low_grade"; case "middle" -> "medium_grade"; case "high" -> "high_grade"; default -> throw new IllegalArgumentException("未知灵石"); };
            if (!insert(copies, new ItemStack(CommerceInventory.item("myvillage:" + id + "_spirit_stone")), e.getValue(), true))
                return new Prepared(access, copies, "inventory_full");
        }
        return new Prepared(access, copies, "ok");
    }
    public static Prepared goods(ServerPlayer player, ItemStack stack, int count) {
        var access = SpiritStoneSources.open(player); var copies = access.copies();
        return new Prepared(access, copies, insert(copies, stack, count, false) ? "ok" : "inventory_full");
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
