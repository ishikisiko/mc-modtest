package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.model.Person;
import java.util.List;

/**
 * One sim day (design §4). Yearly business first on the first day of a year (sect economy and
 * ranks, sect politics, fortune regrowth, new blood), then every living person in id order
 * (cultivate, break through, age, travel, beasts, fortunes, revenge, meetings), then the players
 * whose master is gone, then the successions the day's deaths left to settle. People who arrive
 * today act from tomorrow; people who die today stop acting at once.
 */
public final class Engine {
    private Engine() {
    }

    /** Populates a fresh world on day 0 (the caller then runs the prehistory with {@link #step}). */
    public static void genesis(SimContext ctx) {
        Genesis.populate(ctx);
        ctx.chronicle.endDay();
    }

    public static List<SimEvent> step(SimContext ctx) {
        ctx.newDay();
        if (ctx.newYear()) {
            SectAffairs.yearly(ctx);
            PlayerAffairs.yearly(ctx);
            SectPolitics.yearly(ctx);
            Fortunes.yearly(ctx);
            Entrants.yearly(ctx);
            ctx.newDay();
        }
        int[] ids = livingIds(ctx);
        for (int id : ids) {
            Person p = ctx.state.persons.get(id);
            if (p == null) {
                continue;
            }
            Cultivation.daily(ctx, p);
            Breakthrough.daily(ctx, p);
            if (ctx.alive(p)) {
                Lifespan.daily(ctx, p);
            }
            if (ctx.alive(p)) {
                Travel.daily(ctx, p);
            }
            if (ctx.alive(p)) {
                Danger.daily(ctx, p);
            }
            if (ctx.alive(p)) {
                Fortunes.daily(ctx, p);
            }
            if (ctx.alive(p)) {
                Revenge.daily(ctx, p);
            }
            if (ctx.alive(p)) {
                Meetings.daily(ctx, p);
            }
        }
        PlayerAffairs.daily(ctx);
        Succession.settle(ctx);
        List<SimEvent> today = ctx.chronicle.endDay();
        ctx.state.day++;
        return today;
    }

    static int[] livingIds(SimContext ctx) {
        int[] ids = new int[ctx.state.persons.size()];
        int i = 0;
        for (Integer id : ctx.state.persons.keySet()) {
            ids[i++] = id;
        }
        return ids;
    }
}
