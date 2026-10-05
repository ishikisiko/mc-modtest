package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;
import java.util.List;

/**
 * Fills an emptied master's seat at the end of the day (design §4.7): the strongest elder succeeds,
 * failing that the strongest member; a sect with no one left is extinct. The cause is the death that
 * emptied the seat.
 */
public final class Succession {
    private Succession() {
    }

    public static void settle(SimContext ctx) {
        for (Sect sect : ctx.state.sects.values()) {
            if (!sect.active() || sect.masterId >= 0) {
                continue;
            }
            long cause = sect.vacancyCauseEventId;
            String predecessor = predecessorName(ctx, cause);
            Person heir = strongest(ctx.members(sect.id), true);
            boolean elder = heir != null;
            if (heir == null) {
                heir = strongest(ctx.members(sect.id), false);
            }
            if (heir == null) {
                destroy(ctx, sect, cause, predecessor);
                continue;
            }
            heir.rank = "sect_master";
            sect.masterId = heir.id;
            sect.masterSinceDay = ctx.day();
            sect.vacancyCauseEventId = -1;
            ctx.chronicle.event("succession", 3).actors(heir.id).sects(sect.id).region(sect.homeRegionId)
                    .cause(cause)
                    .text(elder ? TextKeys.SUCCESSION : TextKeys.SUCCESSION_JUNIOR, heir.name(), sect.name, predecessor);
        }
    }

    static Person strongest(List<Person> members, boolean eldersOnly) {
        Person best = null;
        for (Person p : members) {
            if (eldersOnly && !p.rank.equals("elder")) {
                continue;
            }
            if (best == null || SimContext.standing(p) > SimContext.standing(best)) {
                best = p;
            }
        }
        return best;
    }

    private static String predecessorName(SimContext ctx, long deathEventId) {
        for (int i = ctx.state.chronicle.size() - 1; i >= 0; i--) {
            var e = ctx.state.chronicle.get(i);
            if (e.id() == deathEventId) {
                return ctx.nameOf(e.subject());
            }
            if (e.id() < deathEventId) {
                break;
            }
        }
        return "";
    }

    static void destroy(SimContext ctx, Sect sect, long cause, String predecessor) {
        sect.state = Sect.DESTROYED;
        sect.destroyedDay = ctx.day();
        sect.vacancyCauseEventId = -1;
        ctx.chronicle.event("sect_extinct", 3).sects(sect.id).region(sect.homeRegionId).cause(cause)
                .text(TextKeys.SECT_EXTINCT, sect.name, predecessor);
    }
}
