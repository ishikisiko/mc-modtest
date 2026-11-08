package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.sect.ElderAssessment;
import com.example.myvillage.spiritstone.*;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** 守山执事提供领取和考核入口；所有账目与考核资格均由命簿复核。 */
public final class SectBenefitsRuntime {
    private static final String OWNER = "myvillage:elder_trial_owner", ATTEMPT = "myvillage:elder_trial_attempt";
    private SectBenefitsRuntime() { }
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SectBenefitsRuntime::damage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SectBenefitsRuntime::death);
        NeoForge.EVENT_BUS.addListener(SectBenefitsRuntime::tick);
        NeoForge.EVENT_BUS.addListener(SectBenefitsRuntime::logout);
        NeoForge.EVENT_BUS.addListener(SectBenefitsRuntime::drops);
        NeoForge.EVENT_BUS.addListener(SectBenefitsRuntime::experience);
    }
    public static String stoneName(String stone) {
        return switch (stone) { case "low" -> "下品灵石"; case "middle" -> "中品灵石"; case "high" -> "上品灵石";
            default -> throw new IllegalArgumentException("未知灵石"); };
    }
    private static ResourceLocation denomination(String stone) {
        return ResourceLocation.parse(switch (stone) { case "low" -> "myvillage:low_grade";
            case "middle" -> "myvillage:medium_grade"; case "high" -> "myvillage:high_grade";
            default -> throw new IllegalArgumentException("未知灵石"); });
    }
    public static List<Component> details(ServerPlayer p, WorldSim sim, int sectId) {
        var sect = sim.sect(sectId).orElseThrow();
        var member = sim.playerMember(p.getUUID().toString()).orElseThrow();
        var tier = sim.sectProgression().tier(sect.tierId());
        var lines = new ArrayList<Component>();
        lines.add(Component.literal("宗门等级：" + tier.name() + "；当前每月供奉 " + tier.stipend(member.rank()) + " " + stoneName(tier.stone()) + "。"));
        var pending = sim.pendingStipend(p.getUUID().toString());
        String money = pending.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getValue() + " " + stoneName(e.getKey()))
                .collect(java.util.stream.Collectors.joining("、"));
        lines.add(Component.literal(pending.isEmpty() ? "供奉在每月月初结算，当前没有待领取灵石。" : "待领取供奉：" + money + "。"));
        lines.add(Component.literal("未领取的供奉保存在命簿中，死亡和重登会保留余额。"));
        if (sect.tierId().equals("branch") && member.rank().equals("inner")) {
            String reason = sim.elderTrialRefusal(p.getUUID().toString());
            lines.add(Component.literal(reason.isEmpty() ? "长老考核：独自击败一只炼气巅峰妖兽，通过后晋升长老。" : reason));
        }
        return lines;
    }
    public static boolean canTrial(ServerPlayer p, WorldSim sim) {
        return !p.isCreative() && !p.isSpectator() && sim.elderTrialRefusal(p.getUUID().toString()).isEmpty();
    }
    public static void claim(ServerPlayer p, WorldSim sim, int sectId) {
        var pending = sim.pendingStipend(p.getUUID().toString());
        if (pending.isEmpty()) { message(p, "当前没有待领取的供奉。"); return; }
        var access = SpiritStoneSources.open(p); var copies = access.copies();
        Map<String, BigInteger> paid = new TreeMap<>();
        for (String stone : com.example.myvillage.sim.data.SectProgressionRules.STONES) {
            int wanted = pending.getOrDefault(stone, BigInteger.ZERO).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
            if (wanted == 0) continue;
            var tier = SpiritStoneDenominations.get().tier(denomination(stone));
            ItemStack type = new ItemStack(BuiltInRegistries.ITEM.get(tier.item()));
            int left = SpiritStoneStorage.insertBags(copies, type, wanted, true);
            left = SpiritStoneStorage.insertLoose(copies, type, left, 36);
            if (left < wanted) paid.put(stone, BigInteger.valueOf(wanted - left));
        }
        if (paid.isEmpty()) { message(p, "背包和灵石袋已满，供奉继续保存在宗门账上。"); return; }
        if (!sim.claimStipend(p.getUUID().toString(), sectId, pending, paid, () -> access.commit(copies))) {
            message(p, "本次领取未完成，请重新领取。"); return;
        }
        dirty(p); p.containerMenu.broadcastChanges();
        message(p, "已领取供奉：" + paid.entrySet().stream().map(e -> e.getValue() + " " + stoneName(e.getKey()))
                .collect(java.util.stream.Collectors.joining("、")) + "。");
    }
    public static void startTrial(ServerPlayer p, WorldSim sim) {
        if (!canTrial(p, sim)) { message(p, sim.elderTrialRefusal(p.getUUID().toString())); return; }
        if (p.serverLevel().getDifficulty() == Difficulty.PEACEFUL) { message(p, "长老考核需要在非和平难度进行。"); return; }
        if (!p.isAlive() || p.isPassenger() || p.isSleeping()) return;
        sim.updatePlayerQualification(p.getUUID().toString(), p.getGameProfile().getName(), WorldSimPlayers.qualification(p));
        if (!canTrial(p, sim)) return;
        var rules = sim.sectProgression().trial();
        var entity = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(rules.entity())).create(p.serverLevel());
        if (!(entity instanceof Mob mob)) { message(p, "考核对手暂时不可用。"); return; }
        BlockPos at = null;
        for (int i = 0; i < 16 && at == null; i++) {
            double angle = i * Math.PI / 8;
            BlockPos candidate = p.blockPosition().offset((int) Math.round(Math.cos(angle) * rules.spawnRadius()), 0,
                    (int) Math.round(Math.sin(angle) * rules.spawnRadius()));
            if (!p.serverLevel().hasChunkAt(candidate)) continue;
            for (int dy = 2; dy >= -3; dy--) {
                var site = candidate.above(dy);
                if (!p.serverLevel().isPositionEntityTicking(site)) continue;
                if (!p.serverLevel().getBlockState(site.below()).isFaceSturdy(p.serverLevel(), site.below(), net.minecraft.core.Direction.UP)) continue;
                mob.moveTo(site.getX()+.5, site.getY(), site.getZ()+.5, p.getYRot(), 0);
                if (!p.serverLevel().getWorldBorder().isWithinBounds(site) || !p.serverLevel().noCollision(mob)
                        || p.serverLevel().containsAnyLiquid(mob.getBoundingBox())) continue;
                if (!p.serverLevel().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), mob.getBoundingBox().getCenter(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p)).getType()
                        .equals(net.minecraft.world.phys.HitResult.Type.MISS)) continue;
                at = site; break;
            }
        }
        if (at == null) { mob.discard(); message(p, "附近没有足够的空地，请换一处开阔位置后申请。"); return; }
        String attempt = UUID.randomUUID().toString();
        var trial = new ElderAssessment.Trial(attempt, mob.getUUID().toString(), p.level().dimension().location().toString(),
                p.getBlockX(), p.getBlockY(), p.getBlockZ(), Math.addExact(p.serverLevel().getGameTime(), rules.durationTicks()));
        mob.getPersistentData().putUUID(OWNER, p.getUUID()); mob.getPersistentData().putString(ATTEMPT, attempt);
        mob.setPersistenceRequired(); mob.setCanPickUpLoot(false);
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) mob.setDropChance(slot, 0);
        StatsRuntime.applyRealm(mob, rules.realm(), rules.stage()); mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.literal("长老考核 · 炼气巅峰妖兽")); mob.setCustomNameVisible(true); mob.setTarget(p);
        sim.startElderTrial(p.getUUID().toString(), trial); dirty(p);
        if (!p.serverLevel().addFreshEntity(mob)) { sim.cancelElderTrial(p.getUUID().toString()); mob.discard(); dirty(p); return; }
        message(p, "长老考核开始。击败眼前的炼气巅峰妖兽；离开考核范围、死亡、退出或超时会结束本次考核，可重新申请。");
    }
    private static boolean valid(ServerPlayer p, WorldSim sim, Mob mob) {
        if (!p.isAlive() || p.isCreative() || p.isSpectator() || p.level() != mob.level()) return false;
        var t = sim.elderTrial(p.getUUID().toString()).orElse(null);
        return t != null && t.matches(mob.getPersistentData().getString(ATTEMPT), mob.getUUID().toString(), mob.level().getGameTime())
                && t.within(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ(), sim.sectProgression().trial().radius());
    }
    private static void damage(LivingIncomingDamageEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        if (e.getSource().getEntity() instanceof Mob attacker && attacker.getPersistentData().hasUUID(OWNER)
                && !attacker.getPersistentData().getUUID(OWNER).equals(e.getEntity().getUUID())) { e.setCanceled(true); return; }
        if (!e.getEntity().getPersistentData().hasUUID(OWNER)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !p.getUUID().equals(e.getEntity().getPersistentData().getUUID(OWNER))) {
            e.setCanceled(true); return;
        }
        var sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null || !(e.getEntity() instanceof Mob mob) || !valid(p, sim, mob)) e.setCanceled(true);
    }
    private static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { cancel(p, "长老考核因死亡结束，可重新申请。"); return; }
        if (!(e.getEntity() instanceof Mob mob) || !mob.getPersistentData().hasUUID(OWNER)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        var sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null || !valid(p, sim, mob) || !mob.getPersistentData().getUUID(OWNER).equals(p.getUUID())) return;
        var key = mob.getPersistentData().getCompound("myvillage:stats_realm");
        var rule = sim.sectProgression().trial();
        if (!new com.example.myvillage.stats.RealmKey(key.getString("realm"), key.getInt("stage"))
                .equals(new com.example.myvillage.stats.RealmKey(rule.realm(), rule.stage()))) return;
        sim.updatePlayerQualification(p.getUUID().toString(), p.getGameProfile().getName(), WorldSimPlayers.qualification(p));
        if (sim.completeElderTrial(p.getUUID().toString(), mob.getPersistentData().getString(ATTEMPT), mob.getUUID().toString(), mob.level().getGameTime())) {
            dirty(p); message(p, "长老考核通过，你已晋升本宗长老。");
        }
    }
    private static void tick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !mob.getPersistentData().hasUUID(OWNER)) return;
        var p = level.getServer().getPlayerList().getPlayer(mob.getPersistentData().getUUID(OWNER));
        var sim = WorldSimRuntime.sim().orElse(null);
        if (p == null || sim == null || !valid(p, sim, mob)) {
            String owner = mob.getPersistentData().getUUID(OWNER).toString();
            var active = sim == null ? null : sim.elderTrial(owner).orElse(null);
            if (active != null && active.identity(mob.getPersistentData().getString(ATTEMPT), mob.getUUID().toString())) {
                if (p != null) cancel(p, "本次长老考核已结束，可重新申请。");
                else if (sim.cancelElderTrial(owner)) WorldSimSavedData.get(level.getServer().overworld()).setDirty();
            }
            mob.discard(); return;
        }
        if (mob.isAlive()) mob.setTarget(p);
    }
    /** 无需加载对手区块也能结束超时、退宗或跨维度的考核。 */
    public static void checkTrials(net.minecraft.server.MinecraftServer server, WorldSim sim) {
        for (var p : server.getPlayerList().getPlayers()) {
            var trial = sim.elderTrial(p.getUUID().toString()).orElse(null);
            if (trial == null) continue;
            if (!p.isAlive() || p.isCreative() || p.isSpectator() || server.overworld().getGameTime() >= trial.deadline()
                    || !trial.within(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ(), sim.sectProgression().trial().radius()))
                cancel(p, "本次长老考核已结束，可重新申请。");
            else if (p.serverLevel().getEntity(UUID.fromString(trial.target())) == null
                    && p.serverLevel().isPositionEntityTicking(new BlockPos(trial.x(), trial.y(), trial.z())) && p.tickCount > 40)
                cancel(p, "考核对手已离场，可重新申请。");
        }
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) cancel(p, ""); }
    private static void cancel(ServerPlayer p, String reason) {
        var sim = WorldSimRuntime.sim().orElse(null);
        if (sim != null && sim.cancelElderTrial(p.getUUID().toString())) { dirty(p); if (!reason.isEmpty()) message(p, reason); }
    }
    private static void drops(LivingDropsEvent e) { if (e.getEntity().getPersistentData().hasUUID(OWNER)) e.getDrops().clear(); }
    private static void experience(LivingExperienceDropEvent e) { if (e.getEntity().getPersistentData().hasUUID(OWNER)) e.setDroppedExperience(0); }
    public static void dirty(ServerPlayer p) { WorldSimSavedData.get(p.getServer().overworld()).setDirty(); }
    private static void message(ServerPlayer p, String line) { if (!line.isEmpty()) p.sendSystemMessage(Component.literal(line)); }
}
