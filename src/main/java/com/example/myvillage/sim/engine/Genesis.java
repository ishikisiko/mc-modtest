package com.example.myvillage.sim.engine;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.region.runtime.SectSite;
import com.example.myvillage.sim.data.SectProgressionRules;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.RegionState;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.Tombstone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
        var geometry = ctx.graph.geometry();
        List<SectSite> planned = geometry == null ? List.of() : geometry.sectSites().stream()
                .filter(site -> site.openingRank() >= 0)
                .sorted(Comparator.comparingInt(SectSite::openingRank)).toList();
        int sectPopulation = (int) Math.round(tier.population() * (1.0 - tier.rogueShare()));
        if (planned.isEmpty()) {
            List<String> sectRegions = chooseSectRegions(ctx, tier.sects());
            int[] sizes = sectSizes(ctx, sectPopulation, tier.sects());
            Set<String> signatureUsed = new HashSet<>();
            Set<String> heritageUsed = new HashSet<>();
            for (int rank = 0; rank < tier.sects(); rank++) {
                Sect sect = foundSect(ctx, rank, sectRegions.get(rank), null, null, signatureUsed, heritageUsed);
                staff(ctx, sect, rank, sizes[rank]);
            }
        } else {
            populatePlannedSects(ctx, sectPopulation, planned);
        }
        int rogues = Math.max(0, tier.population() - ctx.state.persons.size());
        for (int i = 0; i < rogues; i++) {
            rogue(ctx, ctx.rng(i, Purpose.GENESIS_ROGUE), i);
        }
        for (Sect sect : ctx.activeSects()) {
            SectAffairs.economy(ctx, sect);
            sect.prestige = 0.0;
            for (Person p : ctx.members(sect.id)) {
                sect.prestige += ctx.realm(p).prestige();
            }
            Person master = ctx.state.persons.get(sect.masterId);
            long age = -sect.foundedDay / ctx.dpy;
            ContentTables.Heritage heritage = ctx.data.heritage(ctx.heritageSource(sect).heritageId);
            Chronicle.Builder event = ctx.chronicle.event("genesis", 3).actors(master.id).sects(sect.id)
                    .region(sect.homeRegionId);
            String[] params = {sect.name, ctx.regionName(sect.homeRegionId), master.name(),
                    TextKeys.stage(ctx.realm(master).id(), master.stage), ctx.nameOf(sect.founderId),
                    String.valueOf(age)};
            if (heritage == null) {
                event.text(TextKeys.GENESIS_SECT, params);
            } else {
                String[] withHeritage = Arrays.copyOf(params, params.length + 1);
                withHeritage[params.length] = heritage.name();
                event.text(TextKeys.GENESIS_SECT_HERITAGE, withHeritage);
            }
        }
    }

    private static void populatePlannedSects(SimContext ctx, int population, List<SectSite> planned) {
        Set<String> signatureUsed = new HashSet<>();
        Set<String> heritageUsed = new HashSet<>();
        Map<String, ContentTables.Heritage> rootHeritages = new LinkedHashMap<>();
        for (SectSite site : planned) {
            if (!site.tier().equals(SectProgressionRules.MAIN)
                    || planned.stream().noneMatch(child -> child.parentSiteId().equals(site.id()))) continue;
            var available = ctx.data.heritages().stream().filter(heritage -> !heritageUsed.contains(heritage.id())).toList();
            if (available.isEmpty()) throw new IllegalStateException("开档宗门缺少可分配的独立传承");
            var heritage = available.get(ctx.rng(site.openingRank(), Purpose.GENESIS_HERITAGE).nextInt(available.size()));
            rootHeritages.put(site.id(), heritage);
            heritageUsed.add(heritage.id());
            signatureUsed.addAll(heritage.techniques());
        }
        Map<String, Sect> bySite = new LinkedHashMap<>();
        int[] sizes = sectSizes(ctx, population, planned.size(), SectPolitics.minViable(ctx));
        for (int rank = 0; rank < planned.size(); rank++) {
            SectSite site = planned.get(rank);
            if (site.openingRank() != rank || site.foundingOnly())
                throw new IllegalStateException("开档宗门名单名次无效："+site.id());
            bySite.put(site.id(), foundSect(ctx, rank, site.regionId(), site, rootHeritages.get(site.id()),
                    signatureUsed, heritageUsed));
        }
        // 先登记总舵，再登记分宗，分宗通过总舵取得同一主宗来源。
        for (String tier : List.of(SectProgressionRules.HEADQUARTERS, SectProgressionRules.BRANCH)) {
            for (SectSite site : planned) {
                if (!site.tier().equals(tier) || site.parentSiteId().isEmpty()) continue;
                Sect sect = bySite.get(site.id());
                Sect parent = bySite.get(site.parentSiteId());
                if (parent == null) throw new IllegalStateException("缺少上级宗门："+site.parentSiteId());
                Sect root = ctx.heritageSource(parent);
                boolean main = tier.equals(SectProgressionRules.HEADQUARTERS);
                if (!root.tierId.equals(SectProgressionRules.MAIN) || root.heritageId.isEmpty()
                        || !(main ? parent == root : parent.tierId.equals(SectProgressionRules.HEADQUARTERS)))
                    throw new IllegalStateException("宗门隶属等级无效："+site.id());
                sect.parentSectId = parent.id;
                sect.heritageRootSectId = root.id;
                sect.foundedDay = Math.max(sect.foundedDay, parent.foundedDay + ctx.dpy);
                sect.name = root.name + "·" + ctx.regionName(sect.homeRegionId) + (main ? "总舵" : "分宗");
                ctx.usedSectNames().add(sect.name);
            }
        }
        for (SectSite site : planned) staff(ctx, bySite.get(site.id()), site.openingRank(), sizes[site.openingRank()]);
    }

    /** Region per strength rank: rank 0 in the anchor, the rest weighted by qi and spread out. */
    static List<String> chooseSectRegions(SimContext ctx, int count) {
        Rules.Genesis g = ctx.rules.genesis();
        return chooseSectRegions(ctx.graph, count, ctx.rules.sectProgression().entryStates(),
                region -> ctx.rules.sectProgression().forRegion(ctx.graph, region), g.regionQiExponent(),
                g.regionCrowding(), ctx.rng(0, Purpose.GENESIS_SECT_REGION));
    }

    public static List<String> chooseSectRegions(RegionGraph graph, int count, int entryStates,
                                                Function<String, String> tier,
                                                double qiExponent, double crowding, SimRng rng) {
        List<GenRegion> eligible = new ArrayList<>();
        GenRegion anchor = null;
        for (GenRegion r : graph.regions()) {
            if (SimContext.admitsSects(r)) {
                eligible.add(r);
                if (r.role().equals("anchor")) {
                    anchor = r;
                }
            }
        }
        if (eligible.isEmpty()) {
            throw new IllegalStateException("no region admits sects in the graph for seed " + graph.seed());
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
        List<GenRegion> peripheral = eligible.stream().filter(r -> !r.role().equals("anchor"))
                .sorted(java.util.Comparator.comparingInt(GenRegion::tier).thenComparing(GenRegion::id)).toList();
        // Seven eligible core states: the two weakest include the initial state even when 东海 is excluded.
        for (var region : entryRegions(graph, entryStates)) {
            if (out.size() >= count) break;
            out.add(region.id()); placed[eligible.indexOf(region)]++;
        }
        if (out.size() < count && !peripheral.isEmpty()) {
            var region = peripheral.stream().filter(r -> tier.apply(r.id()).equals(SectProgressionRules.HEADQUARTERS))
                    .findFirst().orElse(peripheral.getLast());
            if (!out.contains(region.id())) { out.add(region.id()); placed[eligible.indexOf(region)]++; }
        }
        for (int k = out.size(); k < count; k++) {
            double[] w = new double[eligible.size()];
            for (int i = 0; i < w.length; i++) {
                w[i] = StrictMath.pow(SimContext.qiMid(eligible.get(i)), qiExponent)
                        / (1.0 + crowding * placed[i]);
            }
            int pick = rng.weighted(w);
            placed[pick]++;
            out.add(eligible.get(pick).id());
        }
        return out;
    }

    static int[] sectSizes(SimContext ctx, int total, int count) {
        return sectSizes(ctx, total, count, 0);
    }

    private static int[] sectSizes(SimContext ctx, int total, int count, int minimum) {
        double[] weights = ctx.rules.genesis().sizeWeights();
        int distributable = Math.max(0, total - minimum * count);
        double sum = 0.0;
        for (int k = 0; k < count; k++) {
            sum += weights[Math.min(k, weights.length - 1)];
        }
        int[] sizes = new int[count];
        int given = 0;
        for (int k = 0; k < count; k++) {
            sizes[k] = Math.max(2, minimum + (int) Math.floor(distributable * weights[Math.min(k, weights.length - 1)] / sum));
            given += sizes[k];
        }
        for (int k = 0; given < total; k = (k + 1) % count) {
            sizes[k]++;
            given++;
        }
        return sizes;
    }

    static List<GenRegion> entryRegions(SimContext ctx) {
        return entryRegions(ctx.graph, ctx.rules.sectProgression().entryStates());
    }

    private static List<GenRegion> entryRegions(RegionGraph graph, int entryStates) {
        return graph.regions().stream().filter(SimContext::admitsSects)
                .filter(region -> !region.role().equals("anchor"))
                .sorted(java.util.Comparator.comparingInt(GenRegion::tier).thenComparing(GenRegion::id))
                .limit(entryStates).toList();
    }

    private static Sect foundSect(SimContext ctx, int rank, String regionId, SectSite site,
                                  ContentTables.Heritage reservedHeritage, Set<String> signatureUsed,
                                  Set<String> heritageUsed) {
        Rules.Genesis g = ctx.rules.genesis();
        Sect sect = new Sect();
        sect.id = ctx.state.nextSectId++;
        boolean dependent = site != null && !site.parentSiteId().isEmpty();
        sect.name = dependent ? "" : Naming.sectName(ctx, ctx.rng(sect.id, Purpose.GENESIS_SECT_NAME));
        sect.homeRegionId = regionId;
        sect.tierId = ctx.rules.sectProgression().forRegion(ctx.graph, regionId);
        int[] gate = site == null ? GatePlacement.choose(ctx, ctx.rng(sect.id, Purpose.GENESIS_GATE), regionId)
                : new int[]{site.gateX(), site.gateZ()};
        if (gate == null) {
            throw new IllegalStateException("no gate site for sect " + sect.id + " in " + regionId
                    + " within " + ctx.rules.gates().retries() + " tries");
        }
        if (site != null && (!sect.tierId.equals(site.tier()) || !GatePlacement.valid(ctx, regionId, gate[0], gate[1], -1)))
            throw new IllegalStateException("开档宗门山门不合法："+site.id());
        sect.gateX = gate[0];
        sect.gateZ = gate[1];
        if (!dependent) {
            ContentTables.Heritage heritage = reservedHeritage == null ? pickHeritage(ctx, sect, heritageUsed) : reservedHeritage;
            if (heritage != null) {
                sect.heritageId = heritage.id();
                sect.signatureTechniqueId = heritage.last();
                sect.basicTechniqueId = heritage.first();
                signatureUsed.addAll(heritage.techniques());
            } else {
                SimRng rng = ctx.rng(sect.id, Purpose.GENESIS_TECHNIQUE);
                String grade = g.signatureGrades().get(Math.min(rank, g.signatureGrades().size() - 1));
                sect.signatureTechniqueId = pickTechnique(ctx, rng, grade, signatureUsed);
                sect.basicTechniqueId = pickTechnique(ctx, rng, ContentTables.GRADE_ORDER.get(0), signatureUsed);
            }
        }
        int ageYears = ctx.rng(sect.id, Purpose.GENESIS_FOUNDER).range(g.sectAgeYears()[0], g.sectAgeYears()[1]);
        sect.foundedDay = -(long) ageYears * ctx.dpy;
        ctx.state.sects.put(sect.id, sect);
        return sect;
    }

    /**
     * With {@code genesis.heritage_chance}, one heritage no other genesis sect holds (each goes to at
     * most one sect), else null.
     */
    private static ContentTables.Heritage pickHeritage(SimContext ctx, Sect sect, Set<String> used) {
        SimRng rng = ctx.rng(sect.id, Purpose.GENESIS_HERITAGE);
        if (!rng.chance(ctx.rules.genesis().heritageChance())) {
            return null;
        }
        List<ContentTables.Heritage> options = new ArrayList<>();
        for (ContentTables.Heritage h : ctx.data.heritages()) {
            if (!used.contains(h.id())) {
                options.add(h);
            }
        }
        if (options.isEmpty()) {
            return null;
        }
        ContentTables.Heritage h = options.get(rng.nextInt(options.size()));
        used.add(h.id());
        return h;
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
        var level = ctx.rules.sectProgression().tier(sect.tierId);
        var masterRealm = ctx.initialSectMaster(sect);
        int master = ctx.realms.indexOf(masterRealm.realm());
        int masterStage = masterRealm.stage();
        var promotion = ctx.rules.sectProgression().promotion(sect.tierId, ctx.rules.player().promotion());
        int salt = 0;
        Person head = member(ctx, sect, salt++, master, masterStage, "sect_master", 3);
        sect.masterId = head.id;
        sect.masterSinceDay = -(long) ctx.rng(head.id, Purpose.GENESIS_PERSON, 1)
                .range(1, Math.max(1, (int) (ctx.ageYears(head) / 3))) * ctx.dpy;
        founder(ctx, sect, master);

        int rest = size - 1;
        int elders = rest >= 3 ? Math.max(1, (int) Math.round(rest * g.elderShare())) : 0;
        int inner = (int) Math.round(rest * g.innerShare());
        int elderRealm = ctx.realms.indexOf(promotion.elder().stage().realm());
        int innerStage = promotion.inner().stage().stage();
        int qi = ctx.realms.indexOf(promotion.inner().stage().realm());
        List<Person> seniors = new ArrayList<>();
        seniors.add(head);
        for (int i = 0; i < rest; i++) {
            SimRng rng = ctx.rng(sect.id * 1000L + salt, Purpose.GENESIS_PERSON);
            Person p;
            if (i < elders) {
                int realm = Math.min(elderRealm, master);
                int lo = realm == elderRealm ? promotion.elder().stage().stage() : 0;
                p = member(ctx, sect, salt++, realm, rng.range(lo, Math.min(ctx.realms.get(realm).lastStage(), realm == master ? masterStage : ctx.realms.get(realm).lastStage())), "elder", 2);
                seniors.add(p);
            } else if (i < elders + inner) {
                p = member(ctx, sect, salt++, qi, rng.range(innerStage, ctx.realms.get(qi).lastStage()), "inner", 1);
            } else {
                p = member(ctx, sect, salt++, ctx.realms.indexOf(level.outer().realm()), rng.range(level.outer().stage(), Math.max(level.outer().stage(), innerStage - 1)), "outer", 1);
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
        p.techniqueId = People.sectTechnique(ctx, sect, rank);
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
        int[] founderRoot = Roots.drawBest(ctx.rules.roots(), rng, 3);
        t.rootGrade = Roots.grade(ctx.rules.roots(), founderRoot).id();
        t.prodigy = ctx.rng(t.id, Purpose.NPC_PRODIGY).chance(com.example.myvillage.stats.CultivationStatsRules.bundled().npcProdigyChance());
        t.fates = ctx.data.npcFates().roll(founderRoot, t.prodigy, ctx.rng(t.id, Purpose.NPC_FATES));
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

    private static void rogue(SimContext ctx, SimRng rng, int index) {
        Rules.Genesis g = ctx.rules.genesis();
        Rules.Sects r = ctx.rules.sects();
        int realm = 0;
        int stage = rng.range(0, ctx.realms.get(0).lastStage());
        if (rng.chance(g.rogueFoundationChance())) {
            realm = ctx.realms.indexOf(r.promoteElder().realm());
            stage = rng.range(0, ctx.realms.get(realm).lastStage());
        }
        int[] root = Roots.draw(ctx.rules.roots(), rng);
        // New V8/V9 genesis distributes its ordinary rogue pool across all eight core regions.
        // Walled/sect admission remains unchanged; this does not create another population.
        Person p = People.create(ctx, rng, root, realm, stage, ageDays(ctx, rng, realm, stage),
                (ctx.graph.rulesetId().equals("yuanxian:worldplan_v8") || ctx.graph.rulesetId().equals("yuanxian:worldplan_v9") || ctx.graph.rulesetId().equals("yuanxian:worldplan_v10") || ctx.graph.rulesetId().equals("yuanxian:worldplan_v11"))
                    ? ctx.graph.regions().get(index % ctx.graph.regions().size()).id() : Entrants.rogueHaunt(ctx, rng));
        p.joinedDay = 0;
        p.progress = rng.nextDouble() * ctx.realms.get(realm).stage(stage).cap();
        if (rng.chance(ctx.rules.entrants().rogueTechniqueChance())) {
            List<ContentTables.Technique> options = People.techniquesOfGrade(ctx, ContentTables.GRADE_ORDER.get(0));
            p.techniqueId = options.get(rng.nextInt(options.size())).id();
        }
    }
}
