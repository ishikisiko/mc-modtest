package com.example.myvillage.sim.engine;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;

/**
 * Daily cultivation (design §4.1): progress per year = base × root × technique (+ element match) ×
 * rank × sect resources × master guidance × region qi × status × injury. Minor stage-ups happen at
 * the cap; at the last stage's cap progress waits for a great-realm breakthrough.
 */
public final class Cultivation {
    private Cultivation() {
    }

    public static void daily(SimContext ctx, Person p) {
        p.progress += Rates.linear(yearlyRate(ctx, p), ctx.dpy);
        RealmTable.Realm realm = ctx.realm(p);
        while (p.stage < realm.lastStage() && p.progress >= realm.stage(p.stage).cap()) {
            p.progress -= realm.stage(p.stage).cap();
            p.stage++;
            boolean insight = p.insightDay >= 0 && ctx.day() - p.insightDay <= ctx.dpy;
            p.insightDay = -1;
            ctx.chronicle.event("stage_up", 1).actors(p.id).sects(sectIds(p)).region(p.regionId)
                    .say(insight ? TextKeys.STAGE_UP_INSIGHT : TextKeys.STAGE_UP,
                            Anchor.of(ctx).who(p).add(TextKeys.stage(realm.id(), p.stage)));
        }
        double lastCap = realm.stage(realm.lastStage()).cap();
        if (p.stage == realm.lastStage() && p.progress > lastCap) {
            p.progress = lastCap;
        }
    }

    /** Whether the person sits at the cap of their realm's last stage. */
    public static boolean atRealmCap(SimContext ctx, Person p) {
        RealmTable.Realm realm = ctx.realm(p);
        return p.stage == realm.lastStage() && p.progress >= realm.stage(p.stage).cap();
    }

    public static double yearlyRate(SimContext ctx, Person p) {
        Rules.Cultivation c = ctx.rules.cultivation();
        double rate = c.basePerYear();
        rate *= ctx.rootGrade(p).cultivation();
        rate *= techniqueFactor(ctx, p, true);
        rate *= c.rank().getOrDefault(p.rank, 1.0);
        Sect sect = p.sectId >= 0 ? ctx.sect(p.sectId) : null;
        if (sect != null) {
            int members = Math.max(1, ctx.members(sect.id).size());
            double supply = Math.min(1.0, sect.resources / (members * c.resourceReferencePerMember()));
            rate *= 1.0 + c.resourceBonus() * supply;
        }
        Person master = p.masterId >= 0 ? ctx.state.persons.get(p.masterId) : null;
        if (master != null && SimContext.standing(master) > SimContext.standing(p)) {
            rate *= 1.0 + c.masterGuidance();
        }
        GenRegion region = ctx.region(p.regionId);
        rate *= c.qiBase() + c.qiPerPoint() * SimContext.qiMid(region);
        rate *= c.status().getOrDefault(p.status, 1.0);
        rate *= Math.max(c.injuryFloor(), 1.0 - p.injury * c.injuryPerPoint());
        return rate;
    }

    /**
     * Technique factor for cultivation ({@code forCultivation}) or breakthrough: the grade's factor,
     * plus the element-match bonus when the person's root is strong in the technique's element.
     */
    public static double techniqueFactor(SimContext ctx, Person p, boolean forCultivation) {
        Rules.Techniques rules = ctx.rules.techniques();
        ContentTables.Technique t = ctx.technique(p);
        if (t == null) {
            return forCultivation ? rules.none().cultivation() : rules.none().breakthrough();
        }
        Rules.GradeFactors g = rules.grades().get(t.grade());
        double f = forCultivation ? g.cultivation() : g.breakthrough();
        if (Roots.strongIn(ctx.rules.roots(), p.root, t.element())) {
            f += rules.elementMatchBonus();
        }
        return f;
    }

    static Integer[] sectIds(Person p) {
        return p.sectId >= 0 ? new Integer[] {p.sectId} : new Integer[0];
    }
}
