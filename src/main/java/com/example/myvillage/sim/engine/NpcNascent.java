package com.example.myvillage.sim.engine;

import com.example.myvillage.cultivation.nascent.NascentRules;
import com.example.myvillage.sim.model.Person;

import java.util.OptionalLong;

/** 命簿保存元婴状态。碎婴不会换人物、清退席位或结算击杀。 */
public final class NpcNascent {
    private NpcNascent() { }

    public static boolean eligible(SimContext ctx, Person person) {
        int first = ctx.realms.indexOf("nascent_soul");
        return first >= 0 && person.realm >= first;
    }

    public static int injuryAfterShatter() {
        return NascentRules.get().value("npc_injury_after_shatter");
    }

    public static OptionalLong shatter(SimContext ctx, Person person, String cause, long causeEvent) {
        if (!eligible(ctx, person) || person.nascentShattered) return OptionalLong.empty();
        person.nascentShattered = true;
        if (Deaths.OLD_AGE.equals(cause)) {
            person.nascentGraceUntil = ctx.day() + (long) ctx.dpy * NascentRules.get().value("npc_old_age_grace_years");
        }
        long event = ctx.chronicle.event("nascent_shattered", Deaths.importance(ctx, person)).actors(person.id)
                .sects(Cultivation.sectIds(person)).region(person.regionId).cause(causeEvent)
                .say(TextKeys.P + "nascent_shattered", person.name());
        person.injury = injuryAfterShatter();
        person.injuryEventId = event;
        person.injurerId = -1;
        NpcCombatLedger.resetShatteredBody(ctx, person);
        return OptionalLong.of(event);
    }

    public static boolean give(SimContext ctx, int id, long epoch, String giver) {
        if (!NpcCombatLedger.owns(ctx, id, epoch) || !startReformation(ctx, ctx.state.persons.get(id), giver)) return false;
        ctx.chronicle.endDay();
        return true;
    }

    static boolean startReformation(SimContext ctx, Person person, String giver) {
        if (!eligible(ctx, person) || !person.nascentShattered || person.nascentReformsOn != Long.MAX_VALUE
                || ctx.state.combat.engaged.contains(person.id)) return false;
        person.nascentReformsOn = ctx.day() + NascentRules.get().value("npc_reformation_days");
        ctx.chronicle.event("nascent_reforming", 2).actors(person.id).region(person.regionId)
                .say(TextKeys.P + (giver.isEmpty() ? "nascent_lotus_found" : "nascent_lotus_given"),
                        giver.isEmpty() ? new String[]{person.name()} : new String[]{person.name(), giver});
        return true;
    }

    static void explore(SimContext ctx, Person person) {
        if (person.nascentShattered && person.nascentReformsOn == Long.MAX_VALUE
                && !ctx.state.combat.engaged.contains(person.id)
                && person.status.equals("travelling")
                && ctx.rng(person.id, Purpose.NASCENT_LOTUS).chance(NascentRules.get().npcLotusChance())) {
            startReformation(ctx, person, "");
        }
    }

    static void daily(SimContext ctx, Person person) {
        if (person.nascentShattered && person.nascentReformsOn <= ctx.day()
                && !ctx.state.combat.engaged.contains(person.id)) {
            person.nascentShattered = false;
            person.nascentReformsOn = Long.MAX_VALUE;
            ctx.chronicle.event("nascent_reformed", 2).actors(person.id).region(person.regionId)
                    .say(TextKeys.P + "nascent_reformed", person.name());
        }
    }
}
