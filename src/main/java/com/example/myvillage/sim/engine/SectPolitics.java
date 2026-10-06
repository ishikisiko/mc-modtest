package com.example.myvillage.sim.engine;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.LostHeritage;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Relation;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.SectRelation;
import java.util.ArrayList;
import java.util.List;

/**
 * Sects among sects (design §4.7): standing drifts back toward neutral, drops sharply on wounds and
 * killings between members, and at thresholds becomes a feud and then a war (cause: the incident).
 * At war, members fight on sight and the strongest meet in set battles; a war ends in a truce, in
 * tribute, or with one side annexed or destroyed. Also here: decline and ruin, schisms of oversized
 * sects, rogues taking service, and founding new sects (by a strong rogue, a losing heir or a
 * schismatic elder).
 */
public final class SectPolitics {
    private SectPolitics() {
    }

    // ------------------------------------------------------------------ incidents

    /** A wound or kill between members of two different sects. */
    static void incident(SimContext ctx, Person doer, Person victim, long eventId, boolean kill) {
        if (doer.sectId < 0 || victim.sectId < 0 || doer.sectId == victim.sectId) {
            return;
        }
        Sect a = ctx.sect(doer.sectId);
        Sect b = ctx.sect(victim.sectId);
        if (!a.active() || !b.active()) {
            return;
        }
        Rules.SectRelations r = ctx.rules.sectRelations();
        int penalty = !kill ? r.woundPenalty()
                : Combat.notable(ctx, victim) ? r.notableKillPenalty() : r.killPenalty();
        SectRelation ab = a.relationTo(b.id);
        SectRelation ba = b.relationTo(a.id);
        ab.value = Math.max(-100, ab.value - penalty);
        ba.value = ab.value;
        if (ab.state.equals(SectRelation.NONE) && ab.value <= r.feudAt()) {
            long id = ctx.chronicle.event("feud", 2).actors(doer.id, victim.id).sects(b.id, a.id)
                    .region(victim.regionId).cause(eventId)
                    .say(kill ? TextKeys.FEUD_DEATH_NAMED : TextKeys.FEUD_INJURY_NAMED,
                            Anchor.of(ctx).who(doer).who(victim));
            for (SectRelation rel : new SectRelation[] {ab, ba}) {
                rel.state = SectRelation.FEUD;
                rel.causeEventId = id;
                rel.sinceDay = ctx.day();
            }
        }
    }

    // ------------------------------------------------------------------ yearly

    public static void yearly(SimContext ctx) {
        relationsAndWars(ctx);
        for (Sect sect : ctx.activeSects()) {
            decline(ctx, sect);
        }
        for (Sect sect : ctx.activeSects()) {
            schism(ctx, sect);
        }
        roguesTakeService(ctx);
        founding(ctx);
    }

    private static void relationsAndWars(SimContext ctx) {
        Rules.SectRelations r = ctx.rules.sectRelations();
        List<Sect> sects = ctx.activeSects();
        for (int i = 0; i < sects.size(); i++) {
            for (int j = i + 1; j < sects.size(); j++) {
                Sect a = sects.get(i);
                Sect b = sects.get(j);
                if (!a.active() || !b.active()) {
                    continue;
                }
                SectRelation ab = a.relationTo(b.id);
                SectRelation ba = b.relationTo(a.id);
                if (ab.tributeUntilDay >= ctx.day()) {
                    payTribute(ctx, a, b, r);
                } else if (ba.tributeUntilDay >= ctx.day()) {
                    payTribute(ctx, b, a, r);
                }
                switch (ab.state) {
                    case SectRelation.WAR -> war(ctx, a, b, ab, ba);
                    case SectRelation.FEUD -> {
                        drift(ab, ba, r.driftPerYear(), baseline(a, b, r));
                        if (ab.value > r.feudAt() + r.driftPerYear() * 5) {
                            ab.state = SectRelation.NONE;
                            ba.state = SectRelation.NONE;
                        } else if (ab.value <= r.warAt() && atPeaceLongEnough(ctx, ab, r) && ctx.day() >= ab.noWarUntilDay
                                && ctx.rng(a.id * 1000L + b.id, Purpose.SECT_WAR).chance(r.warChancePerYear())) {
                            declareWar(ctx, a, b, ab, ba);
                        }
                    }
                    default -> drift(ab, ba, r.driftPerYear(), baseline(a, b, r));
                }
            }
        }
    }

    /** Sects seated in the same region compete: their standing settles below neutral. */
    private static int baseline(Sect a, Sect b, Rules.SectRelations r) {
        return a.homeRegionId.equals(b.homeRegionId) ? r.neighbourBaseline() : 0;
    }

    private static void drift(SectRelation ab, SectRelation ba, int step, int target) {
        if (ab.value < target) {
            ab.value = Math.min(target, ab.value + step);
        } else if (ab.value > target) {
            ab.value = Math.max(target, ab.value - step);
        }
        ba.value = ab.value;
    }

    private static void payTribute(SimContext ctx, Sect payer, Sect lord, Rules.SectRelations r) {
        double amount = payer.resources * r.tributeShare();
        payer.resources -= amount;
        lord.resources += amount;
    }

    private static void declareWar(SimContext ctx, Sect a, Sect b, SectRelation ab, SectRelation ba) {
        Person ma = ctx.state.persons.get(a.masterId);
        Person mb = ctx.state.persons.get(b.masterId);
        boolean aDeclares = mb == null || (ma != null && ma.aggression >= mb.aggression);
        Sect declarer = aDeclares ? a : b;
        Sect target = aDeclares ? b : a;
        Person master = aDeclares ? ma : mb;
        Chronicle.Builder event = ctx.chronicle.event("war", 3).sects(declarer.id, target.id)
                .region(declarer.homeRegionId).cause(ab.causeEventId);
        Anchor params = Anchor.of(ctx);
        if (master != null) {
            event.actors(master.id);
            params.who(master);
        } else {
            params.add(declarer.name, TextKeys.rank("sect_master"), "");
        }
        params.add(target.name);
        long id = event.say(TextKeys.WAR_DECLARE, params);
        for (SectRelation rel : new SectRelation[] {ab, ba}) {
            rel.state = SectRelation.WAR;
            rel.causeEventId = id;
            rel.sinceDay = ctx.day();
            rel.score = 0;
        }
    }

    private static void war(SimContext ctx, Sect a, Sect b, SectRelation ab, SectRelation ba) {
        Rules.SectRelations r = ctx.rules.sectRelations();
        SimRng rng = ctx.rng(a.id * 1000L + b.id, Purpose.SECT_BATTLE);
        boolean rested = ab.lastBattleDay < 0
                || ctx.day() - ab.lastBattleDay >= (long) r.battleIntervalYears() * ctx.dpy;
        if (rested && rng.chance(Math.min(1.0, r.battlesPerYear()))) {
            Person ca = champion(ctx, a, r);
            Person cb = champion(ctx, b, r);
            if (ca != null && cb != null) {
                boolean again = (ca.id == ab.lastChampionA && cb.id == ab.lastChampionB)
                        || (ca.id == ab.lastChampionB && cb.id == ab.lastChampionA);
                Person[] order = Meetings.strongerFirst(ctx, ca, cb);
                Combat.Result res = Combat.fight(ctx, order[0], order[1], Combat.Kind.CHAMPION, ab.causeEventId,
                        again ? "again" : "");
                for (SectRelation rel : new SectRelation[] {ab, ba}) {
                    rel.lastBattleDay = ctx.day();
                    rel.lastChampionA = ca.id;
                    rel.lastChampionB = cb.id;
                }
                Sect winner = res.winner().sectId == a.id ? a : b;
                winner.victories++;
                winner.prestige += r.victoryPrestige();
                ab.score += winner == a ? 1 : -1;
                ba.score = -ab.score;
            }
        }
        int na = ctx.members(a.id).size();
        int nb = ctx.members(b.id).size();
        int floor = Math.max(r.annexBelow(), minViable(ctx));
        if (na < floor || nb < floor) {
            Sect loser = na <= nb ? a : b;
            Sect victor = loser == a ? b : a;
            endWar(ctx, ab, ba);
            conquer(ctx, victor, loser, ab.causeEventId);
            return;
        }
        long years = (ctx.day() - ab.sinceDay) / ctx.dpy;
        if (Math.abs(ab.score) >= r.tributeScore()) {
            Sect loser = ab.score > 0 ? b : a;
            Sect victor = loser == a ? b : a;
            long cause = ab.causeEventId;
            endWar(ctx, ab, ba);
            loser.relationTo(victor.id).tributeUntilDay = ctx.day() + (long) r.tributeYears() * ctx.dpy;
            ctx.chronicle.event("war_end", 3).sects(loser.id, victor.id).region(loser.homeRegionId).cause(cause)
                    .say(TextKeys.WAR_TRIBUTE, loser.name, victor.name, String.valueOf(r.tributeYears()));
            return;
        }
        double truce = r.trucePerYear() + r.trucePerYearOfWar() * years;
        if (ctx.rng(a.id * 1000L + b.id, Purpose.SECT_TRUCE).chance(Math.min(1.0, truce))) {
            long cause = ab.causeEventId;
            endWar(ctx, ab, ba);
            ctx.chronicle.event("war_end", 3).sects(a.id, b.id).region(a.homeRegionId).cause(cause)
                    .say(TextKeys.WAR_TRUCE, a.name, b.name, String.valueOf(Math.max(1, years)));
        }
    }

    /** A sect's champion: its strongest member not carrying a serious wound, else its strongest. */
    private static Person champion(SimContext ctx, Sect sect, Rules.SectRelations r) {
        Person best = null;
        for (Person p : ctx.members(sect.id)) {
            if (p.injury <= r.championMaxInjury()
                    && (best == null || SimContext.standing(p) > SimContext.standing(best))) {
                best = p;
            }
        }
        return best != null ? best : Succession.strongest(ctx.members(sect.id), false);
    }

    private static void endWar(SimContext ctx, SectRelation ab, SectRelation ba) {
        for (SectRelation rel : new SectRelation[] {ab, ba}) {
            rel.state = SectRelation.NONE;
            rel.score = 0;
            rel.value = Math.max(rel.value, -30);
            rel.sinceDay = ctx.day();
            rel.lastWarEndDay = ctx.day();
        }
    }

    /** After a war ends (state none since {@code sinceDay}) a new one waits for the peace years. */
    private static boolean atPeaceLongEnough(SimContext ctx, SectRelation rel, Rules.SectRelations r) {
        return rel.lastWarEndDay < 0 || ctx.day() - rel.lastWarEndDay >= (long) r.peaceYears() * ctx.dpy;
    }

    /** The loser of a war is annexed (its remaining members join the victor) or simply destroyed. */
    private static void conquer(SimContext ctx, Sect victor, Sect loser, long cause) {
        List<Person> members = new ArrayList<>(ctx.members(loser.id));
        if (members.isEmpty()) {
            long id = ctx.chronicle.event("sect_destroyed", 3).sects(loser.id, victor.id).region(loser.homeRegionId)
                    .cause(cause).say(TextKeys.WAR_DESTROY, victor.name, loser.name);
            dissolve(ctx, loser, false, id);
            return;
        }
        long id = ctx.chronicle.event("sect_destroyed", 3).sects(loser.id, victor.id).region(loser.homeRegionId)
                .cause(cause).say(TextKeys.WAR_ANNEX, victor.name, loser.name, String.valueOf(members.size()));
        for (Person p : members) {
            String rank = p.rank.equals("sect_master") ? "elder" : p.rank;
            People.join(ctx, p, victor, rank);
        }
        dissolve(ctx, loser, false, id);
    }

    /**
     * Ends a sect; remaining members become rogues unless {@code keepMembers}. A heritage it held
     * goes to the lost pool (the sect keeps the id for history), and the chronicle says so with
     * {@code cause}, the event that ended the sect.
     */
    static void dissolve(SimContext ctx, Sect sect, boolean keepMembers, long cause) {
        sect.state = Sect.DESTROYED;
        sect.destroyedDay = ctx.day();
        sect.masterId = -1;
        sect.vacancyCauseEventId = -1;
        if (!keepMembers) {
            for (Person p : new ArrayList<>(ctx.members(sect.id))) {
                makeRogue(ctx, p);
            }
        }
        for (Sect other : ctx.state.sects.values()) {
            SectRelation rel = other.relations.get(sect.id);
            if (rel != null) {
                rel.state = SectRelation.NONE;
                rel.tributeUntilDay = -1;
            }
        }
        ctx.membershipChanged();
        PlayerAffairs.sectDissolved(ctx, sect, cause);
        loseHeritage(ctx, sect, cause);
    }

    private static void loseHeritage(SimContext ctx, Sect sect, long cause) {
        ContentTables.Heritage heritage = ctx.data.heritage(sect.heritageId);
        if (heritage == null) {
            return;
        }
        ctx.state.lostHeritages.add(new LostHeritage(heritage.id(), sect.id, ctx.day()));
        ctx.chronicle.event("heritage_lost", 2).sects(sect.id).region(sect.homeRegionId).cause(cause)
                .say(TextKeys.SECT_HERITAGE_LOST, sect.name, heritage.name());
    }

    static void makeRogue(SimContext ctx, Person p) {
        p.sectId = -1;
        p.rank = "rogue";
        p.homeRegionId = p.regionId;
        if (p.status.equals("secluded")) {
            p.status = "at_sect";
        }
        ctx.membershipChanged();
    }

    // ------------------------------------------------------------------ decline

    private static void decline(SimContext ctx, Sect sect) {
        Rules.Decline d = ctx.rules.decline();
        Person master = ctx.state.persons.get(sect.masterId);
        boolean old = ctx.day() - sect.foundedDay >= (long) d.graceYears() * ctx.dpy;
        if (sect.declineSinceDay >= 0 && master != null
                && SectAffairs.reached(ctx, master, ctx.rules.succession().minMaster())
                && Succession.yearsLeft(ctx, master) >= ctx.rules.succession().minYearsLeft()
                && ctx.members(sect.id).size() >= minViable(ctx)) {
            sect.declineSinceDay = -1;
            ctx.chronicle.event("sect_revival", 2).actors(master.id).sects(sect.id).region(sect.homeRegionId)
                    .cause(sect.declineCauseEventId).say(TextKeys.SECT_REVIVAL, Anchor.of(ctx).who(master));
            sect.declineCauseEventId = -1;
            return;
        }
        if (sect.declineSinceDay < 0 && old && ctx.members(sect.id).size() < minViable(ctx) && master != null) {
            startDecline(ctx, sect, master, -1);
        } else if (sect.declineSinceDay < 0 && master != null && !hasFitHeir(ctx, sect, master)
                && Succession.yearsLeft(ctx, master) < ctx.rules.succession().minYearsLeft()) {
            // The master is near the end and no one could take over: the decline starts before the death.
            startDecline(ctx, sect, master, -1);
        }
        boolean declining = sect.declineSinceDay >= 0;
        double rate = declining ? d.desertRatePerYear() : d.desertionRatePerYear();
        for (Person p : new ArrayList<>(ctx.members(sect.id))) {
            if (p.rank.equals("sect_master")) {
                continue;
            }
            SimRng rng = ctx.rng(p.id, Purpose.DESERTION);
            if (rng.chance(rate * (1.5 - p.loyalty / 100.0))) {
                ctx.chronicle.event("desertion", 1).actors(p.id).sects(sect.id).region(p.regionId)
                        .say(TextKeys.SECT_DESERT, Anchor.of(ctx).who(p));
                makeRogue(ctx, p);
            }
        }
        if (declining && ctx.members(sect.id).size() < d.minMembers()
                || declining && ctx.day() - sect.declineSinceDay > (long) d.graceYears() * ctx.dpy
                        && ctx.members(sect.id).size() < minViable(ctx)) {
            Chronicle.Builder event = ctx.chronicle.event("sect_destroyed", 3).sects(sect.id)
                    .region(sect.homeRegionId).cause(sect.declineCauseEventId);
            if (master != null) {
                event.actors(master.id);
            }
            long id = event.say(TextKeys.SECT_RUIN, sect.name, ctx.regionName(sect.homeRegionId),
                    master == null ? "" : master.name());
            dissolve(ctx, sect, false, id);
        }
    }

    /** Whether anyone besides the master could lead: fit realm and years enough left. */
    static boolean hasFitHeir(SimContext ctx, Sect sect, Person master) {
        Rules.Succession r = ctx.rules.succession();
        for (Person p : ctx.members(sect.id)) {
            if (p != master && SectAffairs.reached(ctx, p, r.minMaster()) && Succession.yearsLeft(ctx, p) >= r.minYearsLeft()) {
                return true;
            }
        }
        return false;
    }

    /** A sect without a fit leader begins to decline; {@code cause} is what started it. */
    static void startDecline(SimContext ctx, Sect sect, Person master, long cause) {
        sect.declineSinceDay = ctx.day();
        sect.declineCauseEventId = cause;
        long id = ctx.chronicle.event("sect_decline", 2).actors(master.id).sects(sect.id).region(sect.homeRegionId)
                .cause(cause).say(TextKeys.SECT_DECLINE, Anchor.of(ctx).who(master));
        if (cause < 0) {
            sect.declineCauseEventId = id;
        }
    }

    // ------------------------------------------------------------------ schism, rogues, founding

    /** Fewest members a sect needs to stand on its own: an absolute floor or a share of the nominal size. */
    static int minViable(SimContext ctx) {
        Rules.Decline d = ctx.rules.decline();
        return Math.max(d.minMembers(), (int) Math.ceil(d.minShare() * nominalSize(ctx)));
    }

    private static double nominalSize(SimContext ctx) {
        Rules.Tier tier = ctx.rules.tier(ctx.state.tierId);
        return Math.max(1.0, tier.population() * (1.0 - tier.rogueShare()) / tier.sects());
    }

    private static void schism(SimContext ctx, Sect sect) {
        Rules.Decline d = ctx.rules.decline();
        List<Person> members = ctx.members(sect.id);
        if (members.size() < d.schismSizeRatio() * nominalSize(ctx)) {
            return;
        }
        for (Person p : members) {
            if (p.rank.equals("elder") && p.ambition >= d.schismAmbition() && p.loyalty < 50
                    && ctx.rng(p.id, Purpose.SECT_SCHISM).chance(d.schismRatePerYear())) {
                Sect created = foundSect(ctx, p, sect, followersOf(ctx, p, sect), -1, TextKeys.SECT_SCHISM);
                if (created != null) {
                    return;
                }
            }
        }
    }

    /** The leader's disciples plus disloyal members, up to the rules' share of the sect. */
    static List<Person> followersOf(SimContext ctx, Person leader, Sect sect) {
        double share = ctx.rules.succession().followerShare();
        List<Person> out = new ArrayList<>();
        List<Person> members = ctx.members(sect.id);
        int cap = (int) Math.floor(members.size() * share);
        for (Person p : members) {
            if (out.size() >= cap) {
                break;
            }
            if (p == leader || p.rank.equals("sect_master")) {
                continue;
            }
            boolean disciple = p.masterId == leader.id;
            boolean friend = p.hasRelation(leader.id, Relation.FRIEND);
            if (disciple || (friend && p.loyalty < 60)
                    || (p.loyalty < 30 && ctx.rng(p.id, Purpose.SECT_SPLIT, leader.id).chance(share))) {
                out.add(p);
            }
        }
        return out;
    }

    private static void roguesTakeService(SimContext ctx) {
        Rules.Decline d = ctx.rules.decline();
        List<Sect> sects = new ArrayList<>();
        for (Sect s : ctx.activeSects()) {
            if (s.declineSinceDay < 0) {
                sects.add(s);
            }
        }
        if (sects.isEmpty()) {
            return;
        }
        for (Person p : new ArrayList<>(ctx.state.persons.values())) {
            if (p.sectId >= 0 || p.ambition >= ctx.rules.founding().minAmbition() && SectAffairs.reached(ctx, p,
                    ctx.rules.founding().minRealm())) {
                continue;
            }
            SimRng rng = ctx.rng(p.id, Purpose.ROGUE_JOIN);
            if (!rng.chance(d.rogueJoinRatePerYear() * (0.5 + p.loyalty / 100.0))) {
                continue;
            }
            List<Sect> near = new ArrayList<>();
            for (Sect s : sects) {
                if (s.homeRegionId.equals(p.regionId)) {
                    near.add(s);
                }
            }
            List<Sect> pool = near.isEmpty() ? sects : near;
            Sect sect = pool.get(rng.nextInt(pool.size()));
            String rank = rankFor(ctx, p);
            People.join(ctx, p, sect, rank);
            p.techniqueId = SectAffairs.upgradeTechnique(ctx, p, People.sectTechnique(ctx, sect, rank));
            ctx.chronicle.event("rogue_join", 1).actors(p.id).sects(sect.id).region(sect.homeRegionId)
                    .say(TextKeys.ROGUE_JOIN, Anchor.of(ctx).who(p, "rogue").add(sect.name));
        }
    }

    static String rankFor(SimContext ctx, Person p) {
        Rules.Sects r = ctx.rules.sects();
        if (SectAffairs.reached(ctx, p, r.promoteElder())) {
            return "elder";
        }
        return SectAffairs.reached(ctx, p, r.promoteInner()) ? "inner" : "outer";
    }

    private static void founding(SimContext ctx) {
        Rules.Founding f = ctx.rules.founding();
        Rules.Tier tier = ctx.rules.tier(ctx.state.tierId);
        int active = ctx.activeSects().size();
        // Fewer sects than the tier's nominal count make founding likelier; more make it rarer.
        double rate = active < tier.sects()
                ? f.ratePerYear() * (1.0 + f.deficitBoost() * (tier.sects() - active) / (double) tier.sects())
                : f.ratePerYear() * StrictMath.pow(tier.sects() / (double) Math.max(1, active), f.surplusExponent());
        for (Person p : new ArrayList<>(ctx.state.persons.values())) {
            if (p.sectId >= 0 || p.ambition < f.minAmbition() || !SectAffairs.reached(ctx, p, f.minRealm())
                    || Succession.yearsLeft(ctx, p) < f.minYearsLeft()) {
                continue;
            }
            if (ctx.rng(p.id, Purpose.FOUNDING).chance(rate)) {
                foundSect(ctx, p, null, recruitsAround(ctx, p), strengthCause(p), TextKeys.SECT_FOUNDED);
            }
        }
    }

    /** What made a person strong enough to found a sect: a fortune's technique, root or pill. */
    static long strengthCause(Person p) {
        if (p.techniqueEventId >= 0) {
            return p.techniqueEventId;
        }
        if (p.rootEventId >= 0) {
            return p.rootEventId;
        }
        return -1;
    }

    private static List<Person> recruitsAround(SimContext ctx, Person founder) {
        Rules.Founding f = ctx.rules.founding();
        SimRng rng = ctx.rng(founder.id, Purpose.FOUNDING, 1);
        int wanted = rng.range(f.followers()[0], f.followers()[1]);
        List<Person> out = new ArrayList<>();
        for (Person q : ctx.peopleIn(founder.regionId)) {
            if (out.size() >= wanted) {
                break;
            }
            if (q != founder && ctx.alive(q) && q.sectId < 0 && q.realm <= founder.realm
                    && (q.hasRelation(founder.id, Relation.FRIEND) || q.ambition < 50)) {
                out.add(q);
            }
        }
        return out;
    }

    /**
     * Founds a sect led by {@code leader} near where they are, with followers. {@code parent} is the
     * sect left behind (split or schism) or null for a rogue's founding. Returns null when there is
     * no room for a gate.
     *
     * @param family {@link TextKeys#SECT_SPLIT}, {@link TextKeys#SECT_SCHISM} or {@link TextKeys#SECT_FOUNDED}
     */
    static Sect foundSect(SimContext ctx, Person leader, Sect parent, List<Person> followers, long cause, String family) {
        if (Succession.yearsLeft(ctx, leader) < ctx.rules.founding().minYearsLeft()) {
            return null;
        }
        if (parent != null && followers.size() < ctx.rules.succession().minSplitFollowers()) {
            return null;
        }
        SimRng rng = ctx.rng(leader.id, Purpose.SECT_FOUND_REGION);
        String region = null;
        int[] gate = null;
        for (String candidate : candidateRegions(ctx, leader, parent, rng)) {
            gate = GatePlacement.choose(ctx, ctx.rng(leader.id, Purpose.FOUNDING_GATE, candidate.hashCode()), candidate);
            if (gate != null) {
                region = candidate;
                break;
            }
        }
        if (region == null) {
            return null;
        }
        Sect sect = new Sect();
        sect.id = ctx.state.nextSectId++;
        sect.name = Naming.sectName(ctx, ctx.rng(sect.id, Purpose.SECT_NAME));
        sect.homeRegionId = region;
        sect.gateX = gate[0];
        sect.gateZ = gate[1];
        sect.founderId = leader.id;
        sect.foundedDay = ctx.day();
        sect.masterId = leader.id;
        sect.masterSinceDay = ctx.day();
        sect.parentSectId = parent == null ? -1 : parent.id;
        ContentTables.Technique own = ctx.technique(leader);
        List<ContentTables.Technique> basics = People.techniquesOfGrade(ctx, ContentTables.GRADE_ORDER.get(0));
        sect.basicTechniqueId = basics.get(rng.nextInt(basics.size())).id();
        sect.signatureTechniqueId = own != null ? own.id() : sect.basicTechniqueId;
        LostHeritage rekindled = rekindle(ctx, sect, leader);
        ctx.state.sects.put(sect.id, sect);

        // A splinter takes a stake of the parent's wealth and standing in proportion to the people it takes.
        double share = 0.0;
        if (parent != null) {
            share = (followers.size() + 1.0) / Math.max(1, ctx.members(parent.id).size());
        }
        Anchor params = Anchor.of(ctx).who(leader);
        People.join(ctx, leader, sect, "sect_master");
        for (Person f : followers) {
            if (ctx.alive(f)) {
                String rank = rankFor(ctx, f);
                People.join(ctx, f, sect, rank);
                f.techniqueId = SectAffairs.upgradeTechnique(ctx, f, People.sectTechnique(ctx, sect, rank));
            }
        }
        Chronicle.Builder event = ctx.chronicle.event(parent == null ? "sect_founded" : "sect_split", 3)
                .actors(leader.id).region(region).cause(cause);
        long id;
        if (parent != null) {
            event.sects(sect.id, parent.id);
            id = event.say(family, params.add(sect.name, ctx.regionName(region), String.valueOf(followers.size())));
            Rules.SectRelations r = ctx.rules.sectRelations();
            for (SectRelation rel : new SectRelation[] {sect.relationTo(parent.id), parent.relationTo(sect.id)}) {
                rel.value = r.splitValue();
                rel.noWarUntilDay = ctx.day() + (long) r.splitGraceYears() * ctx.dpy;
                if (rel.value <= r.feudAt()) {
                    rel.state = SectRelation.FEUD;
                    rel.causeEventId = id;
                    rel.sinceDay = ctx.day();
                }
            }
        } else {
            event.sects(sect.id);
            ContentTables.Technique cited = cause >= 0 && leader.techniqueEventId == cause ? own : null;
            if (cited != null) {
                id = event.say(TextKeys.SECT_FOUNDED_FORTUNE, params.add(sect.name, ctx.regionName(region), cited.name()));
            } else {
                id = event.cause(-1).say(TextKeys.SECT_FOUNDED, params.add(sect.name, ctx.regionName(region)));
            }
        }
        if (rekindled != null) {
            ctx.chronicle.event("heritage_rekindled", 2).actors(leader.id).sects(sect.id, rekindled.sectId())
                    .region(region).cause(id).say(TextKeys.SECT_HERITAGE_REKINDLED, sect.name, leader.name(),
                            own.name(), ctx.data.heritage(rekindled.heritageId()).name(),
                            ctx.sectName(rekindled.sectId()));
        }
        sect.resources = ctx.rules.sects().incomeBase();
        if (parent != null) {
            double stake = Math.min(1.0, share);
            sect.resources += parent.resources * stake;
            parent.resources -= parent.resources * stake;
            sect.prestige = parent.prestige * stake;
            parent.prestige -= sect.prestige;
        }
        SectAffairs.economy(ctx, sect);
        return sect;
    }

    /**
     * A founder who learned a technique of a lost heritage takes the heritage up: it leaves the lost
     * pool and the new sect teaches its chain. Returns the pool entry, or null.
     */
    private static LostHeritage rekindle(SimContext ctx, Sect sect, Person leader) {
        ContentTables.Heritage heritage = ctx.data.heritageOfTechnique(leader.techniqueId);
        if (heritage == null) {
            return null;
        }
        for (int i = 0; i < ctx.state.lostHeritages.size(); i++) {
            LostHeritage lost = ctx.state.lostHeritages.get(i);
            if (lost.heritageId().equals(heritage.id())) {
                ctx.state.lostHeritages.remove(i);
                sect.heritageId = heritage.id();
                sect.signatureTechniqueId = heritage.last();
                sect.basicTechniqueId = heritage.first();
                return lost;
            }
        }
        return null;
    }

    /** The leader's region first when it admits sects, then the others by qi and crowding. */
    private static List<String> candidateRegions(SimContext ctx, Person leader, Sect parent, SimRng rng) {
        List<String> out = new ArrayList<>();
        String avoid = parent == null ? null : parent.homeRegionId;
        if (SimContext.admitsSects(ctx.region(leader.regionId)) && !leader.regionId.equals(avoid)) {
            out.add(leader.regionId);
        }
        List<GenRegion> rest = new ArrayList<>();
        for (GenRegion r : ctx.graph.regions()) {
            if (SimContext.admitsSects(r) && !r.id().equals(leader.regionId) && !r.id().equals(avoid)) {
                rest.add(r);
            }
        }
        while (!rest.isEmpty()) {
            double[] w = new double[rest.size()];
            for (int i = 0; i < w.length; i++) {
                int crowd = 0;
                for (Sect s : ctx.activeSects()) {
                    if (s.homeRegionId.equals(rest.get(i).id())) {
                        crowd++;
                    }
                }
                w[i] = SimContext.qiMid(rest.get(i)) / (1.0 + crowd);
            }
            out.add(rest.remove(rng.weighted(w)).id());
        }
        // A splinter settles away from the parent when it can; the parent's region is the last resort.
        if (avoid != null && SimContext.admitsSects(ctx.region(avoid))) {
            out.add(avoid);
        }
        return out;
    }
}
