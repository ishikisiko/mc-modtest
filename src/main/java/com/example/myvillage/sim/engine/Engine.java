package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.life.LifeEngine;
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
            if (com.example.myvillage.sim.life.StoryActors.protectedPerson(ctx.state, id)) continue;
            boolean townResident = People.lifeResident(ctx, p);
            double cultivationShare = LifeEngine.takeCultivationShare(ctx, id);
            if (ctx.state.combat.engaged.contains(id)) cultivationShare = 0;
            Cultivation.daily(ctx, p, cultivationShare);
            if (cultivationShare > 0) Breakthrough.daily(ctx, p);
            if (ctx.alive(p)) {
                Lifespan.daily(ctx, p);
            }
            // Town ownership applies both near and far. Lifespan and earned cultivation outcomes
            // remain authoritative; invisible travel/combat waits for an embodied outcome bridge.
            if (townResident) continue;
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
        ctx.state.life.residents.entrySet().removeIf(e -> !ctx.state.persons.containsKey(e.getKey()));
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
