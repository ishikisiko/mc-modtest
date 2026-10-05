package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.Tombstone;

/**
 * Turning a living person into a tombstone after their death event was emitted: relations to them
 * are dropped, disciples lose their master, an emptied master's seat waits for succession at the end
 * of the day, and their minor chronicle entries go.
 */
public final class Deaths {
    public static final String OLD_AGE = "old_age";
    public static final String QI_DEVIATION = "qi_deviation";

    private Deaths() {
    }

    /** Importance of a person's death: the larger of the realm's and the rank's. */
    public static int importance(SimContext ctx, Person p) {
        int byRealm = ctx.realm(p).deathImportance();
        int byRank = ctx.rules.deathImportanceByRank().getOrDefault(p.rank, 1);
        return Math.max(byRealm, byRank);
    }

    public static void bury(SimContext ctx, Person p, String cause, int killerId, long deathEventId) {
        Tombstone t = new Tombstone();
        t.id = p.id;
        t.name = p.name();
        t.title = ctx.title(p);
        t.daoName = p.daoName;
        t.gender = p.gender;
        t.sectId = p.sectId;
        t.rank = p.rank;
        t.rootGrade = ctx.rootGrade(p).id();
        t.realm = p.realm;
        t.stage = p.stage;
        t.birthDay = p.birthDay;
        t.deathDay = ctx.day();
        t.cause = cause;
        t.killerId = killerId;
        t.deathEventId = deathEventId;
        t.masterId = p.masterId;
        ctx.state.tombstones.put(t.id, t);
        ctx.state.persons.remove(p.id);

        for (Person q : ctx.state.persons.values()) {
            if (q.masterId == p.id) {
                q.masterId = -1;
            }
            q.forget(p.id);
        }
        if (p.sectId >= 0 && p.rank.equals("sect_master")) {
            Sect sect = ctx.sect(p.sectId);
            if (sect != null && sect.masterId == p.id) {
                sect.masterId = -1;
                sect.vacancyCauseEventId = deathEventId;
            }
        }
        ctx.chronicle.dropMinor(p.id);
        ctx.membershipChanged();
    }
}
