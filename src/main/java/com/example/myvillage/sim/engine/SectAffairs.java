package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;
import java.util.ArrayList;
import java.util.List;

/**
 * A sect's yearly business (design §4.7): resources from region qi and membership, prestige from
 * the realms of its members, promotion by realm (outer → inner → elder), and masters taking
 * disciples. Recruitment lives in {@link Entrants}.
 */
public final class SectAffairs {
    private SectAffairs() {
    }

    public static void yearly(SimContext ctx) {
        for (Sect sect : ctx.activeSects()) {
            economy(ctx, sect);
            promote(ctx, sect);
            mentor(ctx, sect);
        }
    }

    static void economy(SimContext ctx, Sect sect) {
        Rules.Sects r = ctx.rules.sects();
        Rules.Cultivation c = ctx.rules.cultivation();
        List<Person> members = ctx.members(sect.id);
        int n = members.size();
        double qi = c.qiBase() + c.qiPerPoint() * SimContext.qiMid(ctx.region(sect.homeRegionId));
        double income = r.incomeBase() * qi + (r.incomePerMember() - r.upkeepPerMember()) * n;
        sect.resources = Rates.clamp(sect.resources + income, 0.0, r.resourceCapPerMember() * Math.max(1, n));
        double target = 0.0;
        for (Person p : members) {
            target += ctx.realm(p).prestige();
        }
        target += sect.victories;
        sect.prestige += (target - sect.prestige) * r.prestigeSmoothing();
    }

    static boolean reached(SimContext ctx, Person p, Rules.RealmStage threshold) {
        int realm = ctx.realms.indexOf(threshold.realm());
        return p.realm > realm || (p.realm == realm && p.stage >= threshold.stage());
    }

    static void promote(SimContext ctx, Sect sect) {
        Rules.Sects r = ctx.rules.sects();
        for (Person p : ctx.members(sect.id)) {
            if (p.rank.equals("outer") && reached(ctx, p, r.promoteInner())) {
                p.rank = "inner";
                p.techniqueId = upgradeTechnique(ctx, p, sect.signatureTechniqueId);
                ctx.chronicle.event("promotion", 1).actors(p.id).sects(sect.id).region(p.regionId)
                        .text(TextKeys.PROMOTE_INNER, p.name(), sect.name);
            } else if (p.rank.equals("inner") && reached(ctx, p, r.promoteElder())) {
                p.rank = "elder";
                ctx.chronicle.event("promotion", 1).actors(p.id).sects(sect.id).region(p.regionId)
                        .text(TextKeys.PROMOTE_ELDER, p.name(), sect.name);
            }
        }
    }

    /** Keeps a technique the person already has if it is at least as good as the sect's. */
    static String upgradeTechnique(SimContext ctx, Person p, String offered) {
        var current = ctx.technique(p);
        var next = ctx.data.technique(offered);
        if (next == null) {
            return p.techniqueId;
        }
        if (current == null || next.gradeRank() > current.gradeRank()) {
            p.techniqueEventId = -1;
            return offered;
        }
        return p.techniqueId;
    }

    static void mentor(SimContext ctx, Sect sect) {
        Rules.Sects r = ctx.rules.sects();
        List<Person> members = ctx.members(sect.id);
        for (Person disciple : members) {
            if (disciple.masterId >= 0 || !(disciple.rank.equals("inner") || disciple.rank.equals("outer"))) {
                continue;
            }
            SimRng rng = ctx.rng(disciple.id, Purpose.SECT_MENTOR);
            if (!rng.chance(r.mentorChancePerYear())) {
                continue;
            }
            List<Person> mentors = new ArrayList<>();
            for (Person m : members) {
                boolean senior = m.rank.equals("elder") || m.rank.equals("sect_master");
                if (senior && m.realm > disciple.realm && People.discipleCount(ctx, m) < r.disciplesPerMaster()) {
                    mentors.add(m);
                }
            }
            if (mentors.isEmpty()) {
                continue;
            }
            double[] weights = new double[mentors.size()];
            for (int i = 0; i < weights.length; i++) {
                weights[i] = 1.0 + mentors.get(i).realm;
            }
            Person master = mentors.get(rng.weighted(weights));
            long id = ctx.chronicle.event("disciple", 1).actors(disciple.id, master.id).sects(sect.id)
                    .region(sect.homeRegionId).text(TextKeys.DISCIPLE, disciple.name(), master.name());
            People.bindMentor(master, disciple, id);
        }
    }
}
