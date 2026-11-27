package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.combat.runtime.CombatDodgeService;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.cultivation.fate.PhoenixRuntime;
import com.example.myvillage.entity.npc.NpcCombatRuntime;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import com.example.myvillage.sim.sect.SectTournament.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Server-body lease for one live fixture. The ledger alone owns the results and promotion. */
final class SectTournamentBouts {
    private static final String RECOVERY = "yuanxian_sect_tournament_return";
    private static final Map<Integer, Bout> COURTS = new HashMap<>();
    private static final Map<UUID, Bout> FIGHTERS = new HashMap<>();

    private record Fighter(LivingEntity entity, float health, float absorption) {
        Fighter(LivingEntity entity) { this(entity, entity.getHealth(), entity.getAbsorptionAmount()); }
    }

    private static final class Bout {
        final SectTournamentCourt court;
        final Fixture fixture;
        final Fighter first;
        final Fighter second;
        final long start;
        final long end;
        final int watchRadius;
        final Vec3 playerReturn;
        final List<Projectile> projectiles = new ArrayList<>();
        boolean fighting;
        Outcome finish;
        String reason;

        Bout(SectTournamentCourt court, Fixture fixture, Fighter first, Fighter second, Vec3 playerReturn,
             long start, long end, int watchRadius) {
            this.court = court;
            this.fixture = fixture;
            this.first = first;
            this.second = second;
            this.playerReturn = playerReturn;
            this.start = start;
            this.end = end;
            this.watchRadius = watchRadius;
        }
        boolean pairs(Entity a, Entity b) {
            return first.entity() == a && second.entity() == b || first.entity() == b && second.entity() == a;
        }
        boolean canHit(Entity a, Entity b) {
            return fighting && finish == null && pairs(a, b) && court.onRing(a) && court.onRing(b);
        }
        void lose(Entity loser, String why) {
            finish = first.entity() == loser ? Outcome.SECOND : Outcome.FIRST;
            reason = why;
        }
    }

    private SectTournamentBouts() { }

    static boolean occupied(int sect) { return COURTS.containsKey(sect); }
    static int count() { return COURTS.size(); }
    static boolean fighting(Entity entity) { return FIGHTERS.containsKey(entity.getUUID()); }

    static Boolean permission(LivingEntity actor, Entity target) {
        if (actor.level().isClientSide()) return null;
        Bout from = FIGHTERS.get(actor.getUUID());
        Bout into = FIGHTERS.get(target.getUUID());
        if (from == null && into == null) return null;
        return from != null && from == into && from.canHit(actor, target);
    }

    static boolean opponents(Entity a, Entity b) {
        Bout bout = FIGHTERS.get(a.getUUID());
        return bout != null && bout.pairs(a, b);
    }

    static boolean start(WorldSim sim, SectTournamentCourt court, Fixture fixture, ServerPlayer player) {
        if (occupied(court.sect()) || !court.ready() || !sim.validTournamentFixture(fixture)) return false;
        if (fixture.hasPlayer() != (player != null)) return false;
        Vec3 playerReturn = player == null ? null : court.exitFor(player);
        if (player != null && (!available(player) || playerReturn == null || player.level() != court.level())) return false;
        LivingEntity first = seat(sim, court, fixture.first(), court.first(), player);
        if (first == null) return false;
        LivingEntity second = seat(sim, court, fixture.second(), court.second(), player);
        if (second == null) {
            if (first instanceof NpcEntity npc) WorldSimAvatars.releaseTournamentMember(npc, court.exit());
            return false;
        }
        var rules = sim.sectProgression().tournament();
        long start = court.level().getGameTime() + rules.readyTicks();
        var bout = new Bout(court, fixture, new Fighter(first), new Fighter(second), playerReturn,
                start, start + rules.boutTicks(), rules.watchRadius());
        COURTS.put(court.sect(), bout);
        FIGHTERS.put(first.getUUID(), bout);
        FIGHTERS.put(second.getUUID(), bout);
        prepare(bout, bout.first, court.first(), bout.second.entity());
        prepare(bout, bout.second, court.second(), bout.first.entity());
        announce(bout, "请上台。" + names(fixture) + "，" + (rules.readyTicks() + 19) / 20 + " 秒后开打。");
        return true;
    }

    static boolean available(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isCreative() && !player.isSpectator()
                && !player.isPassenger() && !player.isFallFlying() && !PhoenixRuntime.active(player)
                && !com.example.myvillage.cultivation.nascent.NascentTrial.active(player)
                && !com.example.myvillage.cultivation.tribulation.TribulationManager.isActive(player)
                && !com.example.myvillage.cultivation.meditation.MeditationManager.active(player)
                && !fighting(player);
    }

    private static LivingEntity seat(WorldSim sim, SectTournamentCourt court, Entry entry, BlockPos cell, ServerPlayer player) {
        if (entry.isPlayer()) {
            if (player == null || !player.getStringUUID().equals(entry.player())) return null;
            var box = player.getBoundingBox().move(Vec3.atBottomCenterOf(cell).subtract(player.position()));
            return WorldSimAvatars.loadedArea(court.level(), box) && court.level().noCollision(player, box) ? player : null;
        }
        return WorldSimAvatars.seatTournamentMember(court.level(), sim, court.sect(), entry.person(), cell, false).orElse(null);
    }

    private static void prepare(Bout bout, Fighter fighter, BlockPos cell, LivingEntity other) {
        var actor = fighter.entity();
        CombatSessionManager.endTournamentCombat(actor);
        actor.stopUsingItem();
        Vec3 toward = other.position().subtract(Vec3.atBottomCenterOf(cell));
        float direction = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
        if (actor instanceof ServerPlayer player) {
            storeReturn(player, fighter, bout.playerReturn);
            move(player, Vec3.atBottomCenterOf(cell), direction);
        } else if (actor instanceof NpcEntity npc) {
            npc.getNavigation().stop();
            npc.pauseConversation((int) Math.max(0, bout.start - bout.court.level().getGameTime()));
            face(npc, direction);
        }
    }

    static void tick(WorldSim sim) {
        for (Bout bout : List.copyOf(COURTS.values())) tick(sim, bout);
    }

    private static void tick(WorldSim sim, Bout bout) {
        if (bout.finish != null) { close(sim, bout, true); return; }
        if (bout.fixture.ticket().month() != sim.tournamentMonth() || !sim.validTournamentFixture(bout.fixture)) {
            bout.finish = Outcome.VOID;
            bout.reason = "参赛资格有变，本场取消。";
            close(sim, bout, true);
            return;
        }
        if (!bout.fixture.hasPlayer() && !SectTournamentRuntime.watched(bout.court, sim)) {
            close(sim, bout, false); // A paused live bout can be settled by the far-field scheduler.
            return;
        }
        var a = bout.first.entity();
        var b = bout.second.entity();
        if (!present(a) || !present(b)) {
            close(sim, bout, false); // A vanished projection is not a match result.
            return;
        }
        boolean aInside = bout.court.onRing(a), bInside = bout.court.onRing(b);
        if (!aInside || !bInside) {
            bout.finish = bout.fighting ? (aInside == bInside ? Outcome.DRAW : aInside ? Outcome.FIRST : Outcome.SECOND) : Outcome.VOID;
            bout.reason = bout.fighting ? "出界" : "开场前离开擂台";
            close(sim, bout, true);
            return;
        }
        long now = bout.court.level().getGameTime();
        if (now >= bout.end) {
            bout.finish = Outcome.DRAW;
            bout.reason = "时限已到";
            close(sim, bout, true);
            return;
        }
        if (now < bout.start) return;
        if (!bout.fighting) {
            bout.fighting = true;
            announce(bout, "比试开始。" + names(bout.fixture) + "，点到即止。");
            for (LivingEntity actor : List.of(a, b)) if (actor instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("本场可主动认输。 ").append(Component.literal("[认输]")
                        .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GOLD)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/yuanxian sect_tournament yield")))));
            }
        }
        chooseTarget(a, b);
        chooseTarget(b, a);
    }

    private static void chooseTarget(LivingEntity actor, LivingEntity opponent) {
        if (actor instanceof NpcEntity npc && npc.getTarget() != opponent) NpcCombatRuntime.selectTournamentOpponent(npc, opponent);
    }

    private static boolean present(LivingEntity actor) {
        return actor.isAlive() && !actor.isRemoved() && !actor.isSpectator()
                && (!(actor instanceof NpcEntity npc) || NpcCombatRuntime.managed(npc));
    }

    static boolean concede(WorldSim sim, ServerPlayer player) {
        Bout bout = FIGHTERS.get(player.getUUID());
        if (bout == null) return false;
        if (bout.finish == null) {
            if (bout.fighting) bout.lose(player, "认输");
            else { bout.finish = Outcome.VOID; bout.reason = "开场前退出"; }
        }
        close(sim, bout, true);
        return true;
    }

    static void interruptAll(WorldSim sim) {
        for (Bout bout : List.copyOf(COURTS.values())) close(sim, bout, false);
    }

    private static void close(WorldSim sim, Bout bout, boolean saveResult) {
        if (!COURTS.remove(bout.court.sect(), bout)) return;
        FIGHTERS.remove(bout.first.entity().getUUID(), bout);
        FIGHTERS.remove(bout.second.entity().getUUID(), bout);
        bout.projectiles.forEach(Entity::discard);
        recover(bout, bout.first);
        recover(bout, bout.second);
        if (!saveResult || bout.finish == null) return;
        if (!sim.validTournamentFixture(bout.fixture)) {
            bout.finish = Outcome.VOID;
            bout.reason = "参赛资格有变";
        }
        if (!sim.recordTournamentResult(bout.fixture.ticket(), bout.finish)) return;
        WorldSimSavedData.get(bout.court.level()).setDirty();
        String result = switch (bout.finish) {
            case FIRST -> bout.fixture.first().name() + "取胜";
            case SECOND -> bout.fixture.second().name() + "取胜";
            case DRAW -> "本场打平";
            default -> "本场未计成绩";
        };
        announce(bout, result + "（" + bout.reason + "）。");
        if (bout.first.entity() instanceof ServerPlayer p) report(sim, bout.court.sect(), p);
        if (bout.second.entity() instanceof ServerPlayer p) report(sim, bout.court.sect(), p);
    }

    private static void report(WorldSim sim, int sect, ServerPlayer player) {
        sim.tournamentStanding(sect, player.getStringUUID()).ifPresent(score -> {
            String message = "本届大比已胜 " + score.wins() + " 场，晋升需 " + score.requiredWins() + " 场。";
            if (score.promoted()) message += "你的比试已经结束，执事准你晋升内门。";
            else if (score.finished()) message += "本届对阵已经打完。";
            else message += "可再向比试执事报到，准备下一场。";
            player.sendSystemMessage(Component.literal(message));
        });
    }

    private static void recover(Bout bout, Fighter fighter) {
        var entity = fighter.entity();
        CombatSessionManager.endTournamentCombat(entity);
        CombatReactionService.clear(entity);
        CombatDodgeService.clear(entity.getUUID());
        if (entity.isAlive()) {
            entity.setHealth(Math.min(fighter.health(), entity.getMaxHealth()));
            entity.setAbsorptionAmount(fighter.absorption());
        }
        if (entity instanceof NpcEntity npc) {
            NpcCombatRuntime.disengage(npc);
            WorldSimAvatars.releaseTournamentMember(npc, bout.court.exit());
        } else if (entity instanceof ServerPlayer player) {
            var safe = player.level() == bout.court.level() && player.isAlive() ? bout.court.exitFor(player) : null;
            if (safe != null) {
                move(player, safe, player.getYRot());
                player.getPersistentData().remove(RECOVERY);
            } else if (player.level() != bout.court.level() || !player.isAlive()) player.getPersistentData().remove(RECOVERY);
        }
    }

    private static void face(LivingEntity actor, float yaw) {
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
    }

    private static void move(ServerPlayer player, Vec3 to, float yaw) {
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.connection.teleport(to.x, to.y, to.z, yaw, 0);
    }

    private static void storeReturn(ServerPlayer player, Fighter before, Vec3 end) {
        var marker = new CompoundTag();
        marker.putString("dimension", player.level().dimension().location().toString());
        marker.putDouble("x", end.x); marker.putDouble("y", end.y); marker.putDouble("z", end.z);
        marker.putFloat("health", before.health()); marker.putFloat("absorption", before.absorption());
        player.getPersistentData().put(RECOVERY, marker);
    }

    static void afterLogin(ServerPlayer player) {
        if (!player.getPersistentData().contains(RECOVERY)) return;
        var marker = player.getPersistentData().getCompound(RECOVERY);
        if (player.level().dimension().location().toString().equals(marker.getString("dimension")) && player.isAlive()) {
            Vec3 end = new Vec3(marker.getDouble("x"), marker.getDouble("y"), marker.getDouble("z"));
            var box = player.getBoundingBox().move(end.subtract(player.position()));
            if (WorldSimAvatars.loadedArea(player.serverLevel(), box) && player.serverLevel().getWorldBorder().isWithinBounds(box)
                    && player.serverLevel().noCollision(player, box)) move(player, end, player.getYRot());
            if (marker.getFloat("health") > 0) player.setHealth(Math.min(marker.getFloat("health"), player.getMaxHealth()));
            player.setAbsorptionAmount(marker.getFloat("absorption"));
        }
        player.getPersistentData().remove(RECOVERY);
    }

    static void incoming(LivingIncomingDamageEvent event) {
        var target = event.getEntity();
        var attacker = attacker(event.getSource());
        Bout into = FIGHTERS.get(target.getUUID());
        Bout from = attacker == null ? null : FIGHTERS.get(attacker.getUUID());
        if (from == null && into == null) return;
        if (from == null || from != into || !from.canHit(attacker, target)) event.setCanceled(true);
    }

    /** This event runs after armor and the mod's damage modifiers, before absorption and health loss. */
    static void beforeDamage(LivingDamageEvent.Pre event) {
        var target = event.getEntity();
        var attacker = attacker(event.getSource());
        Bout bout = FIGHTERS.get(target.getUUID());
        if (bout == null) return;
        if (attacker == null || !bout.canHit(attacker, target)) event.setNewDamage(0);
        else if (event.getNewDamage() >= target.getHealth() + target.getAbsorptionAmount()) {
            event.setNewDamage(0);
            bout.lose(target, "裁判叫停");
        }
    }

    static void track(Projectile projectile) {
        if (projectile.getOwner() == null) return;
        Bout bout = FIGHTERS.get(projectile.getOwner().getUUID());
        if (bout != null) bout.projectiles.add(projectile);
    }

    private static Entity attacker(DamageSource source) {
        if (source.getEntity() != null) return source.getEntity();
        return source.getDirectEntity() instanceof Projectile projectile ? projectile.getOwner() : null;
    }

    private static String names(Fixture fixture) { return fixture.first().name() + "对阵" + fixture.second().name(); }

    private static void announce(Bout bout, String text) {
        for (ServerPlayer player : bout.court.level().players()) if (bout.court.watches(player, bout.watchRadius))
            player.sendSystemMessage(Component.literal("比试执事：" + text));
    }
}
