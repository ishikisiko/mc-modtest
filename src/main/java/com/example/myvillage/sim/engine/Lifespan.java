package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.model.Person;

/** Injuries heal once a year; at the end of lifespan (realm plus bonuses) a person dies of old age (坐化). */
public final class Lifespan {
    private Lifespan() {
    }

    public static void daily(SimContext ctx, Person p) {
        if (ctx.newYear() && p.injury > 0) {
            p.injury = Math.max(0, p.injury - (int) Math.round(ctx.rules.injury().healPerYear()));
            if (p.injury == 0) {
                p.injuryEventId = -1;
            }
        }
        if (ctx.ageYears(p) < ctx.lifespanYears(p)) {
            return;
        }
        String stage = TextKeys.stage(ctx.realm(p).id(), p.stage);
        long id = ctx.chronicle.event("death", Deaths.importance(ctx, p)).actors(p.id)
                .sects(Cultivation.sectIds(p)).region(p.regionId)
                .say(TextKeys.DEATH_OLD_AGE, Anchor.of(ctx).who(p).age(p).add(stage));
        Deaths.bury(ctx, p, Deaths.OLD_AGE, -1, id);
    }
}
