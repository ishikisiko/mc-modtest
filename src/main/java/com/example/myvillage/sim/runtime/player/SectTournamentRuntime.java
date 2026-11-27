package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.entity.npc.NpcCombatRuntime;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.npc.NpcDialogueOptions.Page;
import com.example.myvillage.npc.NpcDialogueText;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.WorldSimText;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import com.example.myvillage.sim.sect.SectTournament.*;
import com.example.myvillage.story.net.StoryDialoguePayload.Option;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** One monthly event per sect, projected at its actual court only while there are witnesses. */
public final class SectTournamentRuntime {
    public static final String SIGN_UP = "tournament_sign_up";
    public static final String NEXT_BOUT = "tournament_next_bout";
    private static final int OBSERVE_TICKS = 20;
    private static final Map<Integer, CourtDuty> DUTIES = new HashMap<>();
    private static final Map<Integer, Long> NEXT_CALL = new HashMap<>();

    private record CourtDuty(SectTournamentCourt court, NpcEntity referee, LinkedHashSet<UUID> ready) {
        CourtDuty(SectTournamentCourt court, NpcEntity referee) { this(court, referee, new LinkedHashSet<>()); }
    }

    private SectTournamentRuntime() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SectTournamentBouts::incoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SectTournamentBouts::beforeDamage);
        NeoForge.EVENT_BUS.addListener(SectTournamentRuntime::commands);
        NeoForge.EVENT_BUS.addListener(SectTournamentRuntime::join);
        NeoForge.EVENT_BUS.addListener(SectTournamentRuntime::login);
        NeoForge.EVENT_BUS.addListener(SectTournamentRuntime::logout);
        NeoForge.EVENT_BUS.addListener(SectTournamentRuntime::changeDimension);
        NeoForge.EVENT_BUS.addListener(SectTournamentRuntime::mount);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ServerStoppingEvent event) -> WorldSimRuntime.sim().ifPresent(SectTournamentRuntime::stop));
    }

    public static void tick(MinecraftServer server, WorldSim sim, long month) {
        if (sim.scheduler().paused()) {
            stop(sim);
            return;
        }
        if (month > sim.tournamentMonth()) {
            SectTournamentBouts.tick(sim); // Finish a result already won on the last tick of the month.
            stop(sim);
            if (sim.advanceTournamentMonth(month)) dirty(server);
        }
        SectTournamentBouts.tick(sim);
        if (server.getTickCount() % OBSERVE_TICKS != 0) return;
        var seen = new HashSet<Integer>();
        for (var sect : sim.sects(false)) {
            if (!sect.state().equals("active")) continue;
            int id = sect.id();
            seen.add(id);
            var court = SectTournamentCourt.resolve(server.overworld(), sect);
            boolean hasWitness = court != null && watched(court, sim);
            if (hasWitness) ensureDuty(sim, court);
            else releaseDuty(id);
            advance(server, sim, id, hasWitness, court);
        }
        new ArrayList<>(DUTIES.keySet()).stream().filter(id -> !seen.contains(id)).forEach(SectTournamentRuntime::releaseDuty);
        NEXT_CALL.keySet().retainAll(seen);
    }

    public static void stop(WorldSim sim) {
        SectTournamentBouts.interruptAll(sim);
        List.copyOf(DUTIES.keySet()).forEach(SectTournamentRuntime::releaseDuty);
        NEXT_CALL.clear();
    }

    private static void advance(MinecraftServer server, WorldSim sim, int sect, boolean observed, SectTournamentCourt court) {
        long now = server.overworld().getGameTime();
        if (SectTournamentBouts.occupied(sect) || now < NEXT_CALL.getOrDefault(sect, 0L)) return;
        var rules = sim.sectProgression().tournament();
        NEXT_CALL.put(sect, now + rules.intervalTicks());
        var fixtures = validFixtures(server, sim, sect);
        CourtDuty duty = DUTIES.get(sect);
        if (duty != null) {
            if (SectTournamentBouts.count() >= rules.maxLiveCourts()) return;
            for (UUID id : List.copyOf(duty.ready())) {
                var player = server.getPlayerList().getPlayer(id);
                var fixture = nextPlayerBout(fixtures, id.toString());
                if (player == null || fixture.isEmpty() || !atReferee(player, duty) || !SectTournamentBouts.available(player)) {
                    duty.ready().remove(id);
                    continue;
                }
                if (SectTournamentBouts.start(sim, duty.court(), fixture.get(), player)) duty.ready().remove(id);
                return; // Wait for this pair's loaded bodies rather than skip a waiting disciple.
            }
        }
        if (sim.arrangeNpcTournamentGroup(sect)) {
            dirty(server);
            fixtures = sim.tournamentFixtures(sect);
        }
        for (Fixture fixture : fixtures) {
            if (fixture.hasPlayer()) continue;
            if (observed) {
                if (duty != null && SectTournamentBouts.count() < rules.maxLiveCourts())
                    SectTournamentBouts.start(sim, court, fixture, null);
                return;
            }
            if (sim.simulateTournamentFixture(fixture)) {
                dirty(server);
                return;
            }
        }
    }

    private static List<Fixture> validFixtures(MinecraftServer server, WorldSim sim, int sect) {
        var valid = new ArrayList<Fixture>();
        for (Fixture fixture : sim.tournamentFixtures(sect)) {
            if (sim.validTournamentFixture(fixture)) valid.add(fixture);
            else if (sim.recordTournamentResult(fixture.ticket(), Outcome.VOID)) dirty(server);
        }
        return valid;
    }

    private static void ensureDuty(WorldSim sim, SectTournamentCourt court) {
        CourtDuty old = DUTIES.get(court.sect());
        if (old != null && old.court().equals(court) && old.referee().isAlive() && !old.referee().isRemoved()
                && NpcCombatRuntime.managed(old.referee()) && sim.person(old.referee().ledgerPersonId())
                        .filter(p -> p.alive() && p.sectId() == court.sect() && p.status().equals("at_sect")).isPresent()) return;
        releaseDuty(court.sect());
        if (!court.ready()) return;
        var officials = sim.membersAt(court.sect()).stream()
                .filter(p -> p.rank().equals("elder") || p.rank().equals("sect_master"))
                .sorted(Comparator.comparing((PersonView p) -> !p.rank().equals("elder")).thenComparingInt(PersonView::id)).toList();
        for (var official : officials) {
            var body = WorldSimAvatars.seatTournamentMember(court.level(), sim, court.sect(), official.id(), court.referee(), true);
            if (body.isPresent()) {
                DUTIES.put(court.sect(), new CourtDuty(court, body.get()));
                return;
            }
        }
    }

    private static void releaseDuty(int sect) {
        CourtDuty duty = DUTIES.remove(sect);
        if (duty != null) WorldSimAvatars.releaseTournamentMember(duty.referee(), duty.court().exit());
    }

    static boolean watched(SectTournamentCourt court, WorldSim sim) {
        int distance = sim.sectProgression().tournament().watchRadius();
        return court.level().players().stream().anyMatch(p -> court.watches(p, distance));
    }

    private static Optional<Fixture> nextPlayerBout(List<Fixture> matches, String player) {
        return matches.stream().filter(b -> b.first().player().equals(player) || b.second().player().equals(player)).findFirst();
    }

    private static boolean atReferee(ServerPlayer player, CourtDuty duty) {
        return player.level() == duty.court().level() && duty.referee().isAlive() && !duty.referee().isRemoved()
                && player.distanceToSqr(duty.referee()) <= 36 && player.hasLineOfSight(duty.referee());
    }

    private static CourtDuty duty(NpcEntity entity) {
        for (CourtDuty duty : DUTIES.values()) if (duty.referee() == entity) return duty;
        return null;
    }

    public static Optional<BlockPos> checkInPoint(ServerPlayer player, WorldSim sim, int sect) {
        return sim.sect(sect).map(view -> SectTournamentCourt.resolve(player.getServer().overworld(), view))
                .map(SectTournamentCourt::referee);
    }

    public static Optional<Page> interact(ServerPlayer player, NpcEntity referee, String action) {
        CourtDuty duty = duty(referee);
        WorldSim sim = WorldSimRuntime.sim().orElse(null);
        if (duty == null || sim == null || !atReferee(player, duty)) return Optional.empty();
        int sect = duty.court().sect();
        String id = player.getStringUUID();
        var lines = new ArrayList<String>();
        var options = new ArrayList<Option>();
        if (sim.scheduler().paused()) {
            return Optional.of(new Page(List.of("宗门大比暂停了。"), List.of(new Option("close", "告辞"))));
        }
        String notice = "";
        if (action.equals(SIGN_UP)) {
            if (!SectTournamentBouts.available(player)) notice = "请先结束当前活动，再来报到。";
            else {
                sim.updatePlayerQualification(id, player.getGameProfile().getName(), WorldSimPlayers.qualification(player));
                var status = sim.enterTournament(sect, id);
                if (status == Enrollment.ENTERED) dirty(player.getServer());
                notice = enrollmentText(status);
            }
        } else if (action.equals(NEXT_BOUT)) {
            var available = nextPlayerBout(sim.tournamentFixtures(sect), id);
            if (!SectTournamentBouts.available(player) || available.isEmpty()) tell(player, "眼下没有可以开始的对阵。");
            else {
                duty.ready().add(player.getUUID());
                tell(player, "已经记下。请在比试执事身边候场，轮到你时会叫号。");
            }
            return Optional.empty();
        }
        if (!notice.isEmpty()) lines.add(notice);
        var standing = sim.tournamentStanding(sect, id);
        if (standing.isPresent()) {
            var score = standing.get();
            lines.add(score.promoted() ? "你已通过本届大比，晋升内门。" : "本届已胜 " + score.wins() + " 场，晋升需 " + score.requiredWins() + " 场。");
            for (Score match : score.scores()) {
                var opponent = match.opponent();
                lines.add(opponent.name() + "（" + realmName(opponent.realm(), opponent.stage()) + "） " + result(match));
            }
            if (!score.finished()) options.add(new Option(NEXT_BOUT, "准备下一场"));
        } else {
            lines.add("本宗每月举办一次大比，外门弟子可以在这里报到。你可以到擂台边观战。");
            var qualification = sim.tournamentEligibility(sect, id);
            if (qualification == Enrollment.ENTERED) options.add(new Option(SIGN_UP, "报名本届大比"));
            else if (qualification == Enrollment.BELOW_THRESHOLD) {
                var bar = sim.tournamentThreshold(sect).stage();
                lines.add("进内门的比试要求修为达到" + realmName(bar.realm(), bar.stage()) + "。");
            }
        }
        options.add(new Option("close", "告辞"));
        return Optional.of(new Page(lines, options));
    }

    private static String result(Score match) {
        return switch (match.outcome()) {
            case PENDING -> "待比试";
            case DRAW -> "平局";
            case VOID -> "取消";
            case FIRST, SECOND -> match.playerWon() ? "胜" : "负";
        };
    }

    private static String realmName(String realm, int stage) {
        return NpcDialogueText.of(WorldSimText.realm(realm)) + NpcDialogueText.of(WorldSimText.stage(realm, stage));
    }

    private static String enrollmentText(Enrollment status) {
        return switch (status) {
            case ENTERED -> "报到成功。对阵已经排好，准备好了就请我叫号。";
            case ALREADY_ENTERED -> "你已报到，可以查看本届对阵。";
            case NOT_OUTER, NOT_MEMBER -> "本宗外门弟子可以报名。";
            case BELOW_THRESHOLD -> "你的修为还没有达到报考内门的门槛。";
            case NO_OPPONENTS -> "现在凑不齐两名境界相近的同门，请晚些时候再来。";
            case CLOSED -> "本届大比还没有开始。";
        };
    }

    public static Boolean combatPermission(LivingEntity actor, Entity victim) {
        if (!actor.level().isClientSide() && victim instanceof NpcEntity npc && duty(npc) != null) return false;
        return SectTournamentBouts.permission(actor, victim);
    }

    public static boolean opponents(Entity a, Entity b) { return SectTournamentBouts.opponents(a, b); }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("yuanxian").then(Commands.literal("sect_tournament")
                .then(Commands.literal("yield").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var sim = WorldSimRuntime.sim().orElse(null);
                    if (sim != null && SectTournamentBouts.concede(sim, player)) return 1;
                    tell(player, "你现在没有正在进行的比试。");
                    return 0;
                }))));
    }

    private static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Projectile projectile) SectTournamentBouts.track(projectile);
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SectTournamentBouts.afterLogin(player);
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WorldSimRuntime.sim().ifPresent(sim -> SectTournamentBouts.concede(sim, player));
    }
    private static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WorldSimRuntime.sim().ifPresent(sim -> SectTournamentBouts.concede(sim, player));
    }
    private static void mount(EntityMountEvent event) {
        if (!event.getLevel().isClientSide() && event.isMounting() && SectTournamentBouts.fighting(event.getEntityMounting())) event.setCanceled(true);
    }
    private static void tell(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal("比试执事：" + text)); }
    private static void dirty(MinecraftServer server) { WorldSimSavedData.get(server.overworld()).setDirty(); }
}
