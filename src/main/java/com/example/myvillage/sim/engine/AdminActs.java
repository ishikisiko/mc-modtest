package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.SectRelation;

/**
 * Admin acts on the ledger (sect entry slice 4, for evidence and testing): a war declared and a
 * sect destroyed now, through the same paths as the yearly politics ({@link SectPolitics#declare},
 * {@link SectPolitics#dissolve}). Called by the {@code WorldSim} facade between settled days, like
 * the player actions of {@link PlayerAffairs}: the events go into the chronicle at once and the
 * open day is closed, so the next {@link Engine#step} neither returns them again nor loses them.
 */
public final class AdminActs {
    public static final String NO_SECT = "no_sect";
    public static final String SAME_SECT = "same_sect";
    public static final String SECT_INACTIVE = "sect_inactive";
    public static final String ALREADY_AT_WAR = "already_at_war";

    private AdminActs() {
    }

    /**
     * {@code sectA} declares war on {@code sectB}: both relations at war from today, the
     * {@code war} event with no cause. Throws IllegalArgumentException with the reason
     * {@link #NO_SECT}, {@link #SAME_SECT}, {@link #SECT_INACTIVE} or {@link #ALREADY_AT_WAR}.
     */
    public static SimEvent declareWar(SimContext ctx, int sectA, int sectB) {
        Sect a = require(ctx, sectA);
        Sect b = require(ctx, sectB);
        if (a.id == b.id) {
            throw new IllegalArgumentException(SAME_SECT);
        }
        if (!a.active() || !b.active()) {
            throw new IllegalArgumentException(SECT_INACTIVE);
        }
        SectRelation ab = a.relations.get(b.id);
        if (ab != null && SectRelation.WAR.equals(ab.state)) {
            throw new IllegalArgumentException(ALREADY_AT_WAR);
        }
        return closeDay(ctx, SectPolitics.declare(ctx, a, b, -1));
    }

    /**
     * The sect is destroyed as by ruin: the {@code sect_destroyed} event (the ruin line, its master
     * as subject) and then {@link SectPolitics#dissolve}, which makes its members rogues, releases
     * its players ({@code player_leave}) and loses a held heritage, all caused by that event. Returns
     * the {@code sect_destroyed} event. Throws IllegalArgumentException with the reason
     * {@link #NO_SECT} or {@link #SECT_INACTIVE}.
     */
    public static SimEvent destroySect(SimContext ctx, int sectId) {
        Sect sect = require(ctx, sectId);
        if (!sect.active()) {
            throw new IllegalArgumentException(SECT_INACTIVE);
        }
        Person master = ctx.state.persons.get(sect.masterId);
        Chronicle.Builder event = ctx.chronicle.event("sect_destroyed", 3).sects(sect.id).region(sect.homeRegionId);
        if (master != null) {
            event.actors(master.id);
        }
        long id = event.say(TextKeys.SECT_RUIN, sect.name, ctx.regionName(sect.homeRegionId),
                master == null ? "" : master.name());
        SectPolitics.dissolve(ctx, sect, false, id);
        return closeDay(ctx, id);
    }

    private static Sect require(SimContext ctx, int sectId) {
        Sect s = ctx.state.sects.get(sectId);
        if (s == null) {
            throw new IllegalArgumentException(NO_SECT);
        }
        return s;
    }

    private static SimEvent closeDay(SimContext ctx, long eventId) {
        ctx.chronicle.endDay();
        SimEvent e = ctx.findEvent(eventId);
        if (e == null) {
            throw new IllegalStateException("event " + eventId + " was not kept");
        }
        return e;
    }
}
