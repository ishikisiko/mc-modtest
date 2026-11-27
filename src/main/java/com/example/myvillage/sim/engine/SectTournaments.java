package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.data.SectProgressionRules.Tournament;
import com.example.myvillage.sim.life.StoryActors;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.sect.SectTournament;
import com.example.myvillage.sim.sect.SectTournament.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Monthly pairing and promotion. Only submitted bout outcomes or the deterministic NPC roll award wins. */
public final class SectTournaments {
    private SectTournaments() { }

    public static boolean advanceMonth(SimContext ctx, long month) {
        var state = ctx.state.tournaments;
        if (month <= state.month) return false;
        state.sects.forEach((sect, groups) -> {
            for (Group group : groups) {
                for (int i = 0; i < group.size(); i++) group.resolve(i, Outcome.VOID);
                settle(ctx, sect, group);
            }
        });
        state.sects.clear();
        state.month = month;
        return true;
    }

    public static Enrollment eligibility(SimContext ctx, int sectId, String player) {
        if (ctx.state.tournaments.month < 0) return Enrollment.CLOSED;
        Sect sect = active(ctx, sectId);
        var member = ctx.state.playerMembers.get(player);
        if (sect == null || member == null || member.sectId != sectId) return Enrollment.NOT_MEMBER;
        if (standing(ctx, sectId, player).isPresent()) return Enrollment.ALREADY_ENTERED;
        if (!member.rank.equals(PlayerAffairs.OUTER)) return Enrollment.NOT_OUTER;
        var bar = threshold(ctx, sect);
        if (member.contribution < bar.contribution()
                || !PlayerAffairs.reached(ctx.realms, member.realmId, member.stageIndex, bar.stage())) return Enrollment.BELOW_THRESHOLD;
        return Enrollment.ENTERED;
    }

    public static Enrollment enroll(SimContext ctx, int sectId, String player) {
        Enrollment status = eligibility(ctx, sectId, player);
        if (status != Enrollment.ENTERED) return status;
        var member = ctx.state.playerMembers.get(player);
        var entry = new Entry(-1, player, member.playerName, member.realmId, member.stageIndex, member.joinedDay, true);
        return arrange(ctx, ctx.sect(sectId), entry) ? Enrollment.ENTERED : Enrollment.NO_OPPONENTS;
    }

    /** NPC-only groups wait for the previous NPC-only group to finish; a player's absence can't hold the monthly roster. */
    public static boolean arrangeNpc(SimContext ctx, int sectId) {
        Sect sect = active(ctx, sectId);
        return ctx.state.tournaments.month >= 0 && sect != null
                && groups(ctx, sectId).stream().noneMatch(g -> !g.hasPlayer() && !g.finished())
                && arrange(ctx, sect, null);
    }

    private static boolean arrange(SimContext ctx, Sect sect, Entry player) {
        List<Group> groups = groups(ctx, sect.id);
        if (groups.size() >= SectTournament.MAX_GROUPS_PER_SECT) return false;
        var registered = new HashSet<Integer>();
        var reserved = new HashSet<Integer>();
        for (Group group : groups) for (Entry entry : group.entries) {
            if (entry.isPlayer()) continue;
            if (entry.promotion()) registered.add(entry.person());
            if (!group.finished()) reserved.add(entry.person());
        }
        var candidates = new ArrayList<Entry>();
        for (Person p : ctx.members(sect.id)) {
            if (reserved.contains(p.id) || !atSect(ctx, p, sect) || ctx.state.combat.engaged.contains(p.id)) continue;
            if (!registered.contains(p.id) && qualifiedNpc(ctx, p, sect)) candidates.add(npc(ctx, p, true));
            else if (p.rank.equals(PlayerAffairs.INNER) || p.rank.equals(PlayerAffairs.OUTER)) candidates.add(npc(ctx, p, false));
        }
        // The monthly order rotates between equally placed disciples without depending on loaded entities.
        long month = ctx.state.tournaments.month;
        candidates.sort(Comparator.comparingLong(e -> SimRng.at(ctx.state.seed, month, sect.id,
                Purpose.SECT_TOURNAMENT, e.person()).nextLong()));
        if (player != null) return createGroup(ctx, sect.id, player, candidates);
        for (Entry candidate : candidates) {
            if (candidate.promotion() && createGroup(ctx, sect.id, candidate, candidates)) return true;
        }
        return false;
    }

    private static boolean createGroup(SimContext ctx, int sect, Entry anchor, List<Entry> candidates) {
        Tournament rules = ctx.rules.sectProgression().tournament();
        List<Entry> best = List.of();
        long bestPromotions = -1;
        int bestGap = Integer.MAX_VALUE;
        for (int min = Math.max(0, anchor.stage() - rules.stageGap()); min <= anchor.stage(); min++) {
            int low = min;
            var group = new ArrayList<Entry>();
            group.add(anchor);
            candidates.stream().filter(e -> !e.key().equals(anchor.key()) && e.realm().equals(anchor.realm())
                    && e.stage() >= low && e.stage() <= low + rules.stageGap())
                    .sorted(Comparator.comparing(Entry::promotion).reversed()
                            .thenComparingInt(e -> Math.abs(e.stage() - anchor.stage())))
                    .limit(rules.groupSize() - 1L).forEach(group::add);
            if (group.size() <= rules.requiredWins()) continue;
            long promos = group.stream().filter(Entry::promotion).count();
            int gap = group.stream().mapToInt(e -> Math.abs(e.stage() - anchor.stage())).sum();
            if (promos > bestPromotions || promos == bestPromotions && (group.size() > best.size()
                    || group.size() == best.size() && gap < bestGap)) {
                best = group;
                bestPromotions = promos;
                bestGap = gap;
            }
        }
        if (best.isEmpty()) return false;
        ctx.state.tournaments.sects.computeIfAbsent(sect, k -> new ArrayList<>()).add(new Group(best));
        return true;
    }

    public static List<Fixture> fixtures(SimContext ctx, int sect) {
        var fixtures = new ArrayList<Fixture>();
        List<Group> groups = groups(ctx, sect);
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (group.finished()) continue;
            for (int b = 0; b < group.size(); b++) {
                var bout = group.bout(b);
                if (bout.outcome() == Outcome.PENDING)
                    fixtures.add(new Fixture(new Ticket(ctx.state.tournaments.month, sect, i, b),
                            group.entries.get(bout.first()), group.entries.get(bout.second())));
            }
        }
        return List.copyOf(fixtures);
    }

    public static Optional<Standing> standing(SimContext ctx, int sect, String player) {
        for (Group group : groups(ctx, sect)) for (int i = 0; i < group.entries.size(); i++) {
            Entry e = group.entries.get(i);
            if (!e.isPlayer() || !e.player().equals(player)) continue;
            var scores = group.scores(i);
            int wins = (int) scores.stream().filter(Score::playerWon).count();
            return Optional.of(new Standing(wins, ctx.rules.sectProgression().tournament().requiredWins(),
                    group.settled(i), group.promoted(i), scores));
        }
        return Optional.empty();
    }

    public static boolean eligibleFixture(SimContext ctx, Fixture fixture) {
        Sect sect = active(ctx, fixture.ticket().sect());
        Entry first = fixture.first(), second = fixture.second();
        return sect != null && eligible(ctx, sect, first) && eligible(ctx, sect, second)
                && first.realm().equals(second.realm())
                && Math.abs(currentStage(ctx, first) - currentStage(ctx, second)) <= ctx.rules.sectProgression().tournament().stageGap();
    }

    /** Ignore stale tickets and outcomes for already resolved matches, including across server restart. */
    public static boolean record(SimContext ctx, Ticket ticket, Outcome outcome) {
        Group group = group(ctx, ticket);
        if (group == null || ticket.bout() < 0 || ticket.bout() >= group.size()) return false;
        var existing = group.bout(ticket.bout());
        if (existing.outcome() != Outcome.PENDING || outcome == Outcome.PENDING) return false;
        var fixture = new Fixture(ticket, group.entries.get(existing.first()), group.entries.get(existing.second()));
        Outcome result = eligibleFixture(ctx, fixture) ? outcome : Outcome.VOID;
        if (!group.resolve(ticket.bout(), result)) return false;
        settle(ctx, ticket.sect(), group);
        return true;
    }

    /** Far-field outcomes share combat strength calculations and treat a bout as non-lethal. */
    public static boolean simulate(SimContext ctx, Fixture fixture) {
        Ticket ticket = fixture.ticket();
        Group group = group(ctx, ticket);
        if (group == null || ticket.bout() < 0 || ticket.bout() >= group.size()) return false;
        var bout = group.bout(ticket.bout());
        if (bout.outcome() != Outcome.PENDING) return false;
        fixture = new Fixture(ticket, group.entries.get(bout.first()), group.entries.get(bout.second()));
        if (fixture.hasPlayer()) return false;
        if (!eligibleFixture(ctx, fixture)) return record(ctx, ticket, Outcome.VOID);
        Person a = ctx.state.persons.get(fixture.first().person());
        Person b = ctx.state.persons.get(fixture.second().person());
        if (ctx.state.combat.engaged.contains(a.id) || ctx.state.combat.engaged.contains(b.id)) return false;
        var rng = SimRng.at(ctx.state.seed, ticket.month(), ticket.sect(), Purpose.SECT_TOURNAMENT,
                ((long) ticket.group() << 32) | ticket.bout());
        return record(ctx, ticket, rng.chance(Combat.winChance(ctx, a, b)) ? Outcome.FIRST : Outcome.SECOND);
    }

    private static void settle(SimContext ctx, int sectId, Group group) {
        Sect sect = active(ctx, sectId);
        boolean changed = false;
        for (int i = 0; i < group.entries.size(); i++) {
            if (group.settled(i)) continue;
            var scores = group.scores(i);
            if (scores.stream().anyMatch(s -> s.outcome() == Outcome.PENDING)) continue;
            Entry entry = group.entries.get(i);
            boolean earns = sect != null && entry.promotion() && eligible(ctx, sect, entry)
                    && scores.stream().filter(Score::playerWon).count() >= ctx.rules.sectProgression().tournament().requiredWins();
            group.settle(i, earns);
            if (!earns) continue;
            if (entry.isPlayer()) PlayerAffairs.promoteThroughTournament(ctx, ctx.state.playerMembers.get(entry.player()), sect);
            else SectAffairs.promoteInner(ctx, sect, ctx.state.persons.get(entry.person()));
            changed = true;
        }
        if (changed) ctx.chronicle.endDay();
    }

    private static boolean eligible(SimContext ctx, Sect sect, Entry entry) {
        if (entry.isPlayer()) {
            var m = ctx.state.playerMembers.get(entry.player());
            var bar = threshold(ctx, sect);
            return m != null && m.sectId == sect.id && m.joinedDay == entry.joined() && m.rank.equals(PlayerAffairs.OUTER)
                    && m.realmId.equals(entry.realm()) && m.contribution >= bar.contribution()
                    && PlayerAffairs.reached(ctx.realms, m.realmId, m.stageIndex, bar.stage());
        }
        Person p = ctx.state.persons.get(entry.person());
        return p != null && atSect(ctx, p, sect) && ctx.realm(p).id().equals(entry.realm())
                && (entry.promotion() ? qualifiedNpc(ctx, p, sect)
                : p.rank.equals(PlayerAffairs.INNER) || p.rank.equals(PlayerAffairs.OUTER));
    }

    private static int currentStage(SimContext ctx, Entry entry) {
        return entry.isPlayer() ? ctx.state.playerMembers.get(entry.player()).stageIndex
                : ctx.state.persons.get(entry.person()).stage;
    }

    private static boolean qualifiedNpc(SimContext ctx, Person p, Sect sect) {
        var bar = threshold(ctx, sect).stage();
        return p.rank.equals(PlayerAffairs.OUTER) && SectAffairs.reached(ctx, p, new Rules.RealmStage(bar.realm(), bar.stage()));
    }

    private static boolean atSect(SimContext ctx, Person p, Sect sect) {
        var profile = ctx.state.combat.profiles.get(p.id);
        return p.sectId == sect.id && p.regionId.equals(sect.homeRegionId) && p.status.equals("at_sect")
                && !ctx.state.life.residents.containsKey(p.id) && !StoryActors.protectedPerson(ctx.state, p.id)
                && !com.example.myvillage.sim.fate.NpcFateRoll.suspended(p.fateRuntime)
                && (profile == null || profile.body() == null || !profile.body().unresolved());
    }

    private static Rules.PlayerThreshold threshold(SimContext ctx, Sect sect) {
        return ctx.rules.sectProgression().promotion(sect.tierId, ctx.rules.player().promotion()).inner();
    }

    private static List<Group> groups(SimContext ctx, int sect) {
        return ctx.state.tournaments.sects.getOrDefault(sect, List.of());
    }

    private static Group group(SimContext ctx, Ticket ticket) {
        if (ctx.state.tournaments.month != ticket.month()) return null;
        var groups = groups(ctx, ticket.sect());
        return ticket.group() < 0 || ticket.group() >= groups.size() ? null : groups.get(ticket.group());
    }

    private static Sect active(SimContext ctx, int id) {
        Sect sect = ctx.sect(id);
        return sect != null && sect.active() ? sect : null;
    }

    private static Entry npc(SimContext ctx, Person p, boolean promotion) {
        return new Entry(p.id, "", p.name(), ctx.realm(p).id(), p.stage, -1, promotion);
    }
}
