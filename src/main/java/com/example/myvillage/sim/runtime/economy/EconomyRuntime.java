package com.example.myvillage.sim.runtime.economy;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.sect.SectBlueprint;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.*;
import com.example.myvillage.sim.runtime.avatar.GateRealizations;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** 实体柜台与命簿的适配层，不在靠近或交互时营造建筑。 */
public final class EconomyRuntime {
    public record Context(boolean linlang, String site, int sect, String title) { }
    private EconomyRuntime() { }
    public static void register() { NeoForge.EVENT_BUS.addListener(EconomyRuntime::death); }
    public static long day(ServerPlayer player) { return WorldSimRuntime.calendarDay(player.getServer()); }
    public static void dirty(ServerPlayer player) { WorldSimSavedData.get(player.getServer().overworld()).setDirty(); }
    public static Optional<Context> resolve(ServerPlayer player, BlockPos pos) {
        var sim = WorldSimRuntime.sim().orElse(null); var level = player.serverLevel();
        if (sim == null || level.dimension() != Level.OVERWORLD || !level.isLoaded(pos)) return Optional.empty();
        var state = level.getBlockState(pos);
        if (state.is(ModBlocks.LINLANG_COUNTER.get())) {
            for (var gate : GateRealizations.get(level).all()) {
                var sect = sim.sect(gate.sectId()).orElse(null);
                if (sect == null || !sect.state().equals("active") || !sect.gateRealized() || sect.gateX() != gate.anchor().getX()
                        || sect.gateZ() != gate.anchor().getZ() || !gate.matchesCurrentBlueprint()) continue;
                var blueprint = SectBlueprint.get(gate.variant());
                if (blueprint.world(gate.anchor(), blueprint.linlangCounter).equals(pos))
                    return Optional.of(new Context(true, "sect_" + sect.id(), sect.id(), sect.name() + " · 琳琅阁"));
            }
        } else if (state.is(ModBlocks.JIANGHU_BOARD.get())) {
            for (var site : sim.lifeSites()) for (var place : site.places())
                if (site.dimension().equals(level.dimension().location().toString()) && place.id().equals("jianghu_board")
                        && place.block().equals("myvillage:jianghu_board") && new BlockPos(place.blockX(), place.blockY(), place.blockZ()).equals(pos))
                    return Optional.of(new Context(false, site.id(), -1, site.name() + " · 千机阁委托"));
        }
        return Optional.empty();
    }
    public static boolean valid(ServerPlayer player, BlockPos pos, boolean linlang, String site, int sect) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return false;
        var sim = WorldSimRuntime.sim().orElse(null); if (sim == null) return false;
        int range = sim.playerEconomyRules().interactionRange();
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > range * range) return false;
        var hit = player.level().clip(new ClipContext(player.getEyePosition(), Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (!hit.getBlockPos().equals(pos)) return false;
        return resolve(player, pos).filter(c -> c.linlang() == linlang && c.site().equals(site) && c.sect() == sect).isPresent();
    }
    public static void open(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().isLoaded(pos)) return;
        if (player.serverLevel().getBlockState(pos).is(ModBlocks.JIANGHU_BOARD.get())) SettlementNoticeBoard.bindAt(player, pos);
        var context = resolve(player, pos).orElse(null);
        if (context == null || !valid(player, pos, context.linlang(), context.site(), context.sect())) {
            player.displayClientMessage(Component.literal("此处尚未登记为宗门或城镇的事务设施。"), true); return;
        }
        var sim = WorldSimRuntime.sim().orElseThrow(); String owner = player.getUUID().toString(); long day = day(player);
        var rows = new ArrayList<EconomyMenu.Row>();
        if (context.linlang()) {
            if (sim.playerMember(owner).filter(m -> m.sectId() == context.sect()).isEmpty()) { player.displayClientMessage(Component.literal("琳琅阁只向本宗弟子开放。"), true); return; }
            for (var offer : sim.playerEconomyRules().linlang()) rows.add(new EconomyMenu.Row(offer.id(), offer.name(), offer.item(), offer.quantity(), offer.pillQuality(), offer.cost(),
                    "需" + switch (offer.rank()) { case "outer" -> "外门"; case "inner" -> "内门"; default -> "长老"; } + "身份；花费宗门贡献 " + offer.cost() + "。"));
        } else {
            var active = sim.townTask(owner);
            if (active.isPresent() && !active.get().siteId().equals(context.site())) {
                player.displayClientMessage(Component.literal("你还有其他城镇的事务，请先回" + sim.lifeSite(active.get().siteId()).map(s -> s.name()).orElse(active.get().siteId()) + "江湖榜交付。"), false); return;
            }
            for (var task : sim.playerEconomyRules().townTasks()) {
                var assignment = active.filter(a -> a.taskId().equals(task.id())).orElseGet(() -> sim.townTaskOffer(owner, context.site(), task.id(), day));
                rows.add(new EconomyMenu.Row(task.id(), task.name(), task.item(), task.count(), 0, 0,
                        task.brief() + " 奖励：" + EconomyInventory.describe(assignment.reward()) + "。"));
            }
        }
        long token = UUID.randomUUID().getLeastSignificantBits();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new EconomyMenu(id, inventory, pos, context.linlang(), context.site(), context.sect(), day, token, rows), Component.literal(context.title())),
                buf -> EconomyMenu.writeOpening(buf, pos, context.linlang(), context.site(), context.sect(), day, token, rows));
    }
    private static void death(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(event.getEntity() instanceof Enemy || event.getEntity() instanceof com.example.myvillage.entity.beast.BeastEntity)) return;
        var sim = WorldSimRuntime.sim().orElse(null);
        if (sim != null && sim.advanceTownHunt(player.getUUID().toString(), RegionRuntimeService.currentRegion(player).orElse(""))) {
            dirty(player); var task = sim.townTask(player.getUUID().toString()).orElseThrow();
            player.displayClientMessage(Component.literal("江湖事务进度：" + task.progress() + "/" + sim.playerEconomyRules().task(task.taskId()).count()), true);
        }
    }
}
