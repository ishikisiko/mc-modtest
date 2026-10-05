package com.example.myvillage.sim.engine;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.RegionState;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.Tombstone;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the world on day 0 (design §5): sects in regions that admit them (the anchor gets the
 * strongest), each with a gate, a dead founder, a master of believable age and realm, elders and
 * disciples of mixed ages; then a few rogues. The prehistory that follows is run by the caller with
 * the normal step.
 */
public final class Genesis {
    private Genesis() {
    }

    public static void populate(SimContext ctx) {
        Rules.Tier tier = ctx.rules.tier(ctx.state.tierId);
        for (GenRegion r : ctx.graph.regions()) {
            ctx.state.regions.put(r.id(), new RegionState(r.id()));
        }
        List<String> sectRegions = chooseSectRegions(ctx, tier.sects());
        int sectPopulation = (int) Math.round(tier.population() * (1.0 - tier.rogueShare()));
        int[] sizes = sectSizes(ctx, sectPopulation, tier.sects());
        Set<String> signatureUsed = new HashSet<>();
        for (int rank = 0; rank < tier.sects(); rank++) {
            Sect sect = foundSect(ctx, rank, sectRegions.get(rank), signatureUsed);
            staff(ctx, sect, rank, sizes[rank]);
        }
        int rogues = Math.max(0, tier.population() - ctx.state.persons.size());
        for (int i = 0; i < rogues; i++) {
            rogue(ctx, ctx.rng(i, Purpose.GENESIS_ROGUE));
        }
        for (Sect sect : ctx.activeSects()) {
            SectAffairs.economy(ctx, sect);
            sect.prestige = 0.0;
            for (Person p : ctx.members(sect.id)) {
                sect.prestige += ctx.realm(p).prestige();
            }
            Person master = ctx.state.persons.get(sect.masterId);
            long age = -sect.foundedDay / ctx.dpy;
            ctx.chronicle.event("genesis", 3).actors(master.id).sects(sect.id).region(sect.homeRegionId)
                    .text(TextKeys.GENESIS_SECT, sect.name, ctx.regionName(sect.homeRegionId), master.name(),
                            TextKeys.stage(ctx.realm(master).id(), master.stage), ctx.nameOf(sect.founderId),
                            String.valueOf(age));
        }
    }

    /** Region per strength rank: rank 0 in the anchor, the rest weighted by qi and spread out. */
    static List<String> chooseSectRegions(SimContext ctx, int count) {
        Rules.Genesis g = ctx.rules.genesis();
        List<GenRegion> eligible = new ArrayList<>();
        GenRegion anchor = null;
        for (GenRegion r : ctx.graph.regions()) {
            if (SimContext.admitsSects(r)) {
                eligible.add(r);
                if (r.role().equals("anchor")) {
                    anchor = r;
                }
            }
        }
        if (eligible.isEmpty()) {
            throw new IllegalStateException("no region admits sects in the graph for seed " + ctx.state.seed);
        }
        if (anchor == null) {
            anchor = eligible.get(0);
            for (GenRegion r : eligible) {
                if (SimContext.qiMid(r) > SimContext.qiMid(anchor)) {
                    anchor = r;
                }
            }
        }
        int[] placed = new int[eligible.size()];
        List<String> out = new ArrayList<>();
        out.add(anchor.id());
        placed[eligible.indexOf(anchor)]++;
        SimRng rng = ctx.rng(0, Purpose.GENESIS_SECT_REGION);
        for (int k = 1; k < count; k++) {
            double[] w = new double[eligible.size()];
            for (int i = 0; i < w.length; i++) {
                w[i] = StrictMath.pow(SimContext.qiMid(eligible.get(i)), g.regionQiExponent())
                        / (1.0 + g.regionCrowding() * placed[i]);
            }
            int pick = rng.weighted(w);
            placed[pick]++;
            out.add(eligible.get(pick).id());
        }
        return out;
    }

    static int[] sectSizes(SimContext ctx, int total, int count) {
        double[] weights = ctx.rules.genesis().sizeWeights();
        double sum = 0.0;
        for (int k = 0; k < count; k++) {
            sum += weights[Math.min(k, weights.length - 1)];
        }
        int[] sizes = new int[count];
        int given = 0;
        for (int k = 0; k < count; k++) {
            sizes[k] = Math.max(2, (int) Math.floor(total * weights[Math.min(k, weights.length - 1)] / sum));
            given += sizes[k];
        }
        for (int k = 0; given < total; k = (k + 1) % count) {
            sizes[k]++;
            given++;
        }
        return sizes;
    }

    private static Sect foundSect(SimContext ctx, int rank, String regionId, Set<String> signatureUsed) {
        Rules.Genesis g = ctx.rules.genesis();
        Sect sect = new Sect();
        sect.id = ctx.state.nextSectId++;
        sect.name = Naming.sectName(ctx, ctx.rng(sect.id, Purpose.GENESIS_SECT_NAME));
        sect.homeRegionId = regionId;
        int[] gate = GatePlacement.choose(ctx, ctx.rng(sect.id, Purpose.GENESIS_GATE), regionId);
        if (gate == null) {
            throw new IllegalStateException("no gate site for sect " + sect.id + " in " + regionId
                    + " within " + ctx.rules.gates().retries() + " tries");
        }
        sect.gateX = gate[0];
        sect.gateZ = gate[1];
        SimRng rng = ctx.rng(sect.id, Purpose.GENESIS_TECHNIQUE);
        String grade = g.signatureGrades().get(Math.min(rank, g.signatureGrades().size() - 1));
        sect.signatureTechniqueId = pickTechnique(ctx, rng, grade, signatureUsed);
        sect.basicTechniqueId = pickTechnique(ctx, rng, ContentTables.GRADE_ORDER.get(0), signatureUsed);
        int ageYears = ctx.rng(sect.id, Purpose.GENESIS_FOUNDER).range(g.sectAgeYears()[0], g.sectAgeYears()[1]);
        sect.foundedDay = -(long) ageYears * ctx.dpy;
        ctx.state.sects.put(sect.id, sect);
        return sect;
    }

    private static String pickTechnique(SimContext ctx, SimRng rng, String grade, Set<String> used) {
        List<ContentTables.Technique> options = new ArrayList<>();
        for (ContentTables.Technique t : People.techniquesOfGrade(ctx, grade)) {
            if (!used.contains(t.id())) {
                options.add(t);
            }
        }
        if (options.isEmpty()) {
            options = People.techniquesOfGrade(ctx, grade);
        }
        String id = options.get(rng.nextInt(options.size())).id();
        used.add(id);
        return id;
    }

    private static void staff(SimContext ctx, Sect sect, int rank, int size) {
        Rules.Genesis g = ctx.rules.genesis();
        Rules.Sects r = ctx.rules.sects();
        Rules.RealmStage masterRealm = g.masters().get(Math.min(rank, g.masters().size() - 1));
        int master = ctx.realms.indexOf(masterRealm.realm());
        int salt = 0;
        Person head = member(ctx, sect, salt++, master, masterRealm.stage(), "sect_master", 3);
        sect.masterId = head.id;
        sect.masterSinceDay = -(long) ctx.rng(head.id, Purpose.GENESIS_PERSON, 1)
                .range(1, Math.max(1, (int) (ctx.ageYears(head) / 3))) * ctx.dpy;
        founder(ctx, sect, master);

        int rest = size - 1;
        int elders = rest >= 3 ? Math.max(1, (int) Math.round(rest * g.elderShare())) : 0;
        int inner = (int) Math.round(rest * g.innerShare());
        int elderRealm = ctx.realms.indexOf(r.promoteElder().realm());
        int innerStage = r.promoteInner().stage();
        int qi = ctx.realms.indexOf(r.promoteInner().realm());
        List<Person> seniors = new ArrayList<>();
        seniors.add(head);
        for (int i = 0; i < rest; i++) {
            SimRng rng = ctx.rng(sect.id * 1000L + salt, Purpose.GENESIS_PERSON);
            Person p;
            if (i < elders) {
                int realm = Math.min(elderRealm, master);
                int lo = realm == elderRealm ? r.promoteElder().stage() : 0;
                p = member(ctx, sect, salt++, realm, rng.range(lo, ctx.realms.get(realm).lastStage()), "elder", 2);
                seniors.add(p);
            } else if (i < elders + inner) {
                p = member(ctx, sect, salt++, qi, rng.range(innerStage, ctx.realms.get(qi).lastStage()), "inner", 1);
            } else {
                p = member(ctx, sect, salt++, qi, rng.range(0, Math.max(0, innerStage - 1)), "outer", 1);
            }
        }
        for (Person p : ctx.members(sect.id)) {
            if (p.rank.equals("inner") || p.rank.equals("outer")) {
                SimRng rng = ctx.rng(p.id, Purpose.GENESIS_MENTOR);
                if (!rng.chance(r.mentorChancePerYear())) {
                    continue;
                }
                List<Person> open = new ArrayList<>();
                for (Person s : seniors) {
                    if (s.realm > p.realm && People.discipleCount(ctx, s) < r.disciplesPerMaster()) {
                        open.add(s);
                    }
                }
                if (!open.isEmpty()) {
                    People.bindMentor(open.get(rng.nextInt(open.size())), p, -1);
                }
            }
        }
    }

    /**
     * Creates a genesis member with an age that fits the realm and stage.
     *
     * @param rootDraws more draws for seniors: their roots carried them this far
     */
    private static Person member(SimContext ctx, Sect sect, int salt, int realm, int stage, String rank, int rootDraws) {
        SimRng rng = ctx.rng(sect.id * 1000L + salt, Purpose.GENESIS_PERSON, 7);
        int[] root = Roots.drawBest(ctx.rules.roots(), rng, rootDraws);
        Person p = People.create(ctx, rng, root, realm, stage, ageDays(ctx, rng, realm, stage), sect.homeRegionId);
        People.join(ctx, p, sect, rank);
        p.joinedDay = -(long) rng.range(0, (int) Math.max(0, ctx.ageYears(p) - ctx.rules.entrants().age()[1])) * ctx.dpy;
        p.progress = rng.nextDouble() * ctx.realms.get(realm).stage(stage).cap();
        p.techniqueId = People.sectTechnique(sect, rank);
        if (ctx.realms.get(realm).titleSuffix() != null || titledBelow(ctx, realm)) {
            p.daoName = Naming.daoName(ctx, rng);
        }
        return p;
    }

    private static boolean titledBelow(SimContext ctx, int realm) {
        for (int r = 0; r < realm; r++) {
            if (ctx.realms.get(r).titleSuffix() != null) {
                return true;
            }
        }
        return false;
    }

    private static long ageDays(SimContext ctx, SimRng rng, int realm, int stage) {
        RealmTable.Realm def = ctx.realms.get(realm);
        int[] range = ctx.rules.genesis().ages().get(def.id());
        double t = (stage + rng.nextDouble()) / def.stages().size();
        double years = range[0] + (range[1] - range[0]) * t;
        return (long) (years * ctx.dpy) + rng.nextInt(ctx.dpy);
    }

    /** The founder is long dead: a tombstone one or more realms above the current master. */
    private static void founder(SimContext ctx, Sect sect, int masterRealm) {
        Rules.Genesis g = ctx.rules.genesis();
        SimRng rng = ctx.rng(sect.id, Purpose.GENESIS_FOUNDER, 1);
        int realm = Math.min(ctx.realms.size() - 1, masterRealm + g.founderRealmBonus());
        RealmTable.Realm def = ctx.realms.get(realm);
        Tombstone t = new Tombstone();
        t.id = ctx.state.nextPersonId++;
        t.gender = rng.chance(0.5) ? "m" : "f";
        String[] name = Naming.personName(ctx, rng, t.gender);
        t.name = name[0] + name[1];
        if (def.titleSuffix() != null || titledBelow(ctx, realm)) {
            t.daoName = Naming.daoName(ctx, rng);
            String suffix = "";
            for (int r = 0; r <= realm; r++) {
                if (ctx.realms.get(r).titleSuffix() != null) {
                    suffix = ctx.realms.get(r).titleSuffix();
                }
            }
            t.title = t.daoName + suffix;
        }
        t.sectId = sect.id;
        t.rank = "sect_master";
        t.realm = realm;
        t.rootGrade = Roots.grade(ctx.rules.roots(), Roots.drawBest(ctx.rules.roots(), rng, 3)).id();
        t.stage = rng.range(0, def.lastStage());
        int[] ages = g.ages().get(def.id());
        long foundingAge = (long) rng.range(ages[0], ages[1]) * ctx.dpy;
        t.birthDay = sect.foundedDay - foundingAge;
        long latestDeath = Math.min(-1L, t.birthDay + (long) def.lifespanYears() * ctx.dpy);
        t.deathDay = Math.max(sect.foundedDay + 1, latestDeath);
        t.cause = Deaths.OLD_AGE;
        sect.founderId = t.id;
        ctx.state.tombstones.put(t.id, t);
    }

    private static void rogue(SimContext ctx, SimRng rng) {
        Rules.Genesis g = ctx.rules.genesis();
        Rules.Sects r = ctx.rules.sects();
        int realm = 0;
        int stage = rng.range(0, ctx.realms.get(0).lastStage());
        if (rng.chance(g.rogueFoundationChance())) {
            realm = ctx.realms.indexOf(r.promoteElder().realm());
            stage = rng.range(0, ctx.realms.get(realm).lastStage());
        }
        int[] root = Roots.draw(ctx.rules.roots(), rng);
        Person p = People.create(ctx, rng, root, realm, stage, ageDays(ctx, rng, realm, stage),
                Entrants.rogueHaunt(ctx, rng));
        p.joinedDay = 0;
        p.progress = rng.nextDouble() * ctx.realms.get(realm).stage(stage).cap();
        if (rng.chance(ctx.rules.entrants().rogueTechniqueChance())) {
            List<ContentTables.Technique> options = People.techniquesOfGrade(ctx, ContentTables.GRADE_ORDER.get(0));
            p.techniqueId = options.get(rng.nextInt(options.size())).id();
        }
    }
}
