package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Relation;
import com.example.myvillage.sim.model.Sect;
import java.util.ArrayList;
import java.util.List;

/** Creating people and the small bookkeeping around membership and mentorship. */
public final class People {
    private People() {
    }

    /**
     * Creates a person and adds them to the ledger.
     *
     * @param ageDays age in days on the current day
     */
    public static Person create(SimContext ctx, SimRng rng, int[] root, int realm, int stage, long ageDays,
                                String regionId) {
        Person p = new Person();
        p.id = ctx.state.nextPersonId++;
        p.gender = rng.chance(0.5) ? "m" : "f";
        String[] name = Naming.personName(ctx, rng, p.gender);
        p.surname = name[0];
        p.given = name[1];
        p.birthDay = ctx.day() - ageDays;
        p.root = root;
        p.realm = realm;
        p.stage = stage;
        p.regionId = regionId;
        p.homeRegionId = regionId;
        p.joinedDay = ctx.day();
        p.ambition = trait(rng);
        p.aggression = trait(rng);
        p.caution = trait(rng);
        p.wanderlust = trait(rng);
        p.loyalty = trait(rng);
        ctx.state.persons.put(p.id, p);
        ctx.membershipChanged();
        return p;
    }

    /** A personality trait in 0..100, centred on 50. */
    private static int trait(SimRng rng) {
        return (rng.range(0, 100) + rng.range(0, 100)) / 2;
    }

    public static void join(SimContext ctx, Person p, Sect sect, String rank) {
        p.sectId = sect.id;
        p.rank = rank;
        p.regionId = sect.homeRegionId;
        p.homeRegionId = sect.homeRegionId;
        p.status = "at_sect";
        p.statusUntil = -1;
        p.joinedDay = ctx.day();
        ctx.membershipChanged();
    }

    public static void bindMentor(Person master, Person disciple, long causeEventId) {
        disciple.masterId = master.id;
        disciple.relate(master.id, Relation.MASTER, 80, causeEventId);
        master.relate(disciple.id, Relation.DISCIPLE, 80, causeEventId);
    }

    /** Living disciples of {@code master}, in id order. */
    public static int discipleCount(SimContext ctx, Person master) {
        int n = 0;
        for (Relation r : master.relations) {
            if (r.kind.equals(Relation.DISCIPLE) && ctx.state.persons.containsKey(r.other)) {
                n++;
            }
        }
        return n;
    }

    /** Techniques of a grade, in file order. */
    public static List<ContentTables.Technique> techniquesOfGrade(SimContext ctx, String grade) {
        List<ContentTables.Technique> out = new ArrayList<>();
        for (ContentTables.Technique t : ctx.data.techniques()) {
            if (t.grade().equals(grade)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Technique taught by a sect to a rank: the signature for inner and up, the basic one for outer. */
    public static String sectTechnique(Sect sect, String rank) {
        return rank.equals("outer") ? sect.basicTechniqueId : sect.signatureTechniqueId;
    }
}
