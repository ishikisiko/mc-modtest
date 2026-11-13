package com.example.myvillage.sim.economy;

import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.engine.PlayerAffairs;
import com.example.myvillage.sim.model.PlayerMember;
import java.util.*;
import java.util.function.BooleanSupplier;

/** 玩家奖励、贡献消费与声望只由 WorldSim 门面调用。 */
public final class PlayerEconomy {
    private PlayerEconomy() { }
    public static int reputation(SimContext ctx, String player) {
        var m = ctx.state.playerMembers.get(player); return m == null ? 0 : m.jianghuReputation;
    }
    public static TaskReward sectReward(SimContext ctx, String player, String task, long year, int base) {
        var range = ctx.data.playerEconomy().sectRewards().get(task);
        if (range == null) return new TaskReward(base, 0, Map.of());
        var rolled = range.roll(seed(ctx, player, task, year));
        return new TaskReward(Math.addExact(base, rolled.contribution()), rolled.reputation(), rolled.stones());
    }
    private static long seed(SimContext ctx, String player, String key, long period) {
        // 全部字符参与稳定混合；不读取客户端随机数或系统时间。
        long hash = ctx.state.seed ^ period;
        for (char c : (player + ":" + key).toCharArray()) hash = (hash ^ c) * 0x100000001b3L;
        return hash;
    }
    public static TaskReward openSectReward(SimContext ctx, String player) {
        var m = ctx.state.playerMembers.get(player);
        if (m == null || m.taskId.isEmpty()) return TaskReward.NONE;
        return m.taskReward == null ? new TaskReward(ctx.data.sectTask(m.taskId).contribution(), 0, Map.of()) : m.taskReward;
    }
    public static int manualCost(SimContext ctx, String technique) {
        var override = ctx.data.playerEconomy().manualOverrides().get(technique);
        if (override != null) return override;
        var def = ctx.data.technique(technique);
        if (def == null) throw new IllegalArgumentException("not_borrowable");
        return ctx.rules.player().scriptureHall().borrowCostByGrade().getOrDefault(Integer.toString(def.gradeRank() + 1), 0);
    }
    public static boolean purchase(SimContext ctx, String player, int sectId, String offerId, BooleanSupplier inventoryCommit) {
        var m = ctx.state.playerMembers.get(player); var sect = ctx.sect(sectId);
        if (m == null || m.sectId != sectId || sect == null || !sect.active()) throw new IllegalArgumentException("not_member");
        var offer = ctx.data.playerEconomy().offer(offerId);
        if (PlayerAffairs.RANKS.indexOf(m.rank) < PlayerAffairs.RANKS.indexOf(offer.rank())) throw new IllegalArgumentException("rank_too_low");
        if (m.contribution < offer.cost()) throw new IllegalArgumentException("insufficient_contribution");
        int before = m.contribution;
        if (!inventoryCommit.getAsBoolean()) return false;
        if (m.contribution != before) throw new IllegalStateException("兑换提交期间贡献发生变化");
        m.contribution -= offer.cost(); return true;
    }
    private static PlayerMember ensure(SimContext ctx, String player, String name) {
        if (player == null || player.isBlank() || player.length() > 96) throw new IllegalArgumentException("invalid_player");
        var m = ctx.state.playerMembers.computeIfAbsent(player, key -> { var v = new PlayerMember(); v.playerId = key; return v; });
        m.playerName = name; return m;
    }
    public static TownAssignment offerTown(SimContext ctx, String player, String site, String taskId, long day) {
        var town = ctx.state.life.sites.get(site);
        if (town == null || day < 0) throw new IllegalArgumentException("unknown_site");
        var task = ctx.data.playerEconomy().task(taskId);
        return new TownAssignment(task.id(), site, town.regionId(), day, 0, task.reward().roll(seed(ctx, player, site + ":" + taskId, day)));
    }
    public static TownAssignment acceptTown(SimContext ctx, String player, String name, String site, String taskId, long day) {
        var offer = offerTown(ctx, player, site, taskId, day); var m = ensure(ctx, player, name);
        if (m.townTask != null) throw new IllegalArgumentException("task_active");
        if (m.townTaskDays.getOrDefault(site, -1L) >= day) throw new IllegalArgumentException("task_done_today");
        if (!m.townTaskDays.containsKey(site) && m.townTaskDays.size() >= 512) throw new IllegalArgumentException("town_limit");
        m.townTask = offer; m.townTaskDays.put(site, day); return offer;
    }
    public static boolean advanceTown(SimContext ctx, String player, String region) {
        var m = ctx.state.playerMembers.get(player);
        if (m == null || m.townTask == null || !m.townTask.regionId().equals(region)) return false;
        var a = m.townTask; var task = ctx.data.playerEconomy().task(a.taskId());
        if (!task.kind().equals("hunt") || a.progress() >= task.count()) return false;
        m.townTask = new TownAssignment(a.taskId(), a.siteId(), a.regionId(), a.day(), a.progress() + 1, a.reward()); return true;
    }
    public static boolean completeTown(SimContext ctx, String player, String site, TownAssignment expected, BooleanSupplier inventoryCommit) {
        var m = ctx.state.playerMembers.get(player);
        if (m == null || expected == null || !expected.equals(m.townTask) || !expected.siteId().equals(site)) throw new IllegalArgumentException("wrong_board");
        var task = ctx.data.playerEconomy().task(expected.taskId());
        if (task.kind().equals("hunt") && expected.progress() < task.count()) throw new IllegalArgumentException("not_ready");
        int next = Math.addExact(m.jianghuReputation, expected.reward().reputation());
        if (!inventoryCommit.getAsBoolean()) return false;
        m.jianghuReputation = next; m.townTask = null; return true;
    }
    public static boolean rewardExpedition(SimContext ctx, String player, String name, TaskReward reward, BooleanSupplier inventoryCommit) {
        if (reward.contribution() != 0) throw new IllegalArgumentException("远征报酬不包含宗门贡献");
        if (player == null || player.isBlank() || player.length() > 96) throw new IllegalArgumentException("invalid_player");
        int next = Math.addExact(reputation(ctx, player), reward.reputation());
        if (!inventoryCommit.getAsBoolean()) return false;
        ensure(ctx, player, name).jianghuReputation = next; return true;
    }
    public static void validate(SimContext ctx) {
        for (var m : ctx.state.playerMembers.values()) {
            if (m.contribution < 0 || m.jianghuReputation < 0 || m.townTaskDays.size() > 512
                    || m.townTaskDays.entrySet().stream().anyMatch(e -> !e.getKey().matches("[a-z0-9_]{1,96}") || e.getValue() < 0))
                throw new com.example.myvillage.sim.SimFormatException("玩家贡献、声望或任务日期无效");
            if (m.townTask != null) {
                var t = m.townTask; var site = ctx.state.life.sites.get(t.siteId());
                if (site == null || !site.regionId().equals(t.regionId()) || t.progress() > ctx.data.playerEconomy().task(t.taskId()).count()
                        || m.townTaskDays.getOrDefault(t.siteId(), -1L) < t.day())
                    throw new com.example.myvillage.sim.SimFormatException("江湖事务归属或进度无效");
            }
            if (m.taskReward != null && m.taskId.isEmpty()) throw new com.example.myvillage.sim.SimFormatException("空宗务保留了奖励");
        }
    }
}
