package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.Admission;
import com.example.myvillage.sim.PlayerQualification;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.PlayerMember;
import com.example.myvillage.sim.model.Sect;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Players' business in the ledger (player sect entry, slice 1). A player is never a
 * {@link Person}: their record is a {@link PlayerMember} in {@code WorldState.playerMembers}.
 *
 * <p>Two kinds of entry point. The player actions ({@link #admission}, {@link #join}, {@link #leave},
 * {@link #promote}) are called by the {@code WorldSim} facade between settled days; their event goes
 * into the chronicle at once and the open day is closed ({@code Chronicle.endDay}) so the next
 * {@link Engine#step} neither returns it again nor loses it. The yearly review ({@link #yearly}, run
 * by {@link Engine#step} right after {@link SectAffairs#yearly}) and {@link #sectDissolved} run inside
 * a step, so their events are that day's events like any other.
 */
public final class PlayerAffairs {
    public static final String OUTER = "outer";
    public static final String INNER = "inner";
    public static final String ELDER = "elder";
    /** Player ranks, lowest first. Players never become sect master by review. */
    public static final List<String> RANKS = List.of(OUTER, INNER, ELDER);

    public static final String NOT_MEMBER = "not_member";

    private static final int STANDING_MIN = -100;
    private static final int STANDING_MAX = 100;

    private PlayerAffairs() {
    }

    // ------------------------------------------------------------------ player actions

    /** Whether the player may join the sect now; does not change the ledger. */
    public static Admission admission(SimContext ctx, String playerId, int sectId, PlayerQualification q) {
        Rules.Player rules = ctx.rules.player();
        Sect sect = ctx.sect(sectId);
        if (sect == null || !sect.active()) {
            return Admission.refused(Admission.SECT_INACTIVE);
        }
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        if (m != null && m.inSect()) {
            return Admission.refused(m.sectId == sectId ? Admission.ALREADY_MEMBER : Admission.MEMBER_ELSEWHERE);
        }
        if (m != null && m.leftSectId == sectId
                && ctx.day() - m.leftDay < (long) rules.leave().rejoinYears() * ctx.dpy) {
            return Admission.refused(Admission.REJOIN_COOLDOWN);
        }
        int standing = m == null ? 0 : m.standings.getOrDefault(sectId, 0);
        if (standing < rules.leave().rejoinStandingAtLeast()) {
            return Admission.refused(Admission.STANDING_TOO_LOW);
        }
        Rules.PlayerAdmission ad = rules.admission();
        if (ad.requireAwakenedRoot() && !q.awakened()) {
            return Admission.refused(Admission.NOT_AWAKENED);
        }
        if (!reached(ctx.realms, q, ad.min())) {
            return Admission.refused(Admission.REALM_TOO_LOW);
        }
        Rules.PlayerSelective sel = ad.selective();
        if (sect.prestige >= sel.prestigeAtLeast() && !(q.rootPeakBp() >= sel.rootPeakBpAtLeast()
                || reached(ctx.realms, q, new Rules.PlayerRealmStage(sel.orMinRealm(), 0)))) {
            return Admission.refused(Admission.SELECTIVE);
        }
        return Admission.admitted();
    }

    /**
     * Joins the player to the sect as an outer disciple (creating the record on first join) and
     * records {@code player_join}. Throws IllegalArgumentException with the admission reason when
     * the player may not join.
     */
    public static SimEvent join(SimContext ctx, String playerId, String playerName, int sectId,
                                PlayerQualification q) {
        Admission a = admission(ctx, playerId, sectId, q);
        if (!a.ok()) {
            throw new IllegalArgumentException(a.reason());
        }
        Sect sect = ctx.sect(sectId);
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        if (m == null) {
            m = new PlayerMember();
            m.playerId = playerId;
            ctx.state.playerMembers.put(playerId, m);
        }
        m.playerName = playerName;
        m.sectId = sectId;
        m.rank = OUTER;
        m.joinedDay = ctx.day();
        m.masterId = -1;
        snapshot(m, q);
        addStanding(m, sectId, ctx.rules.player().admission().joinStanding());
        long id = ctx.chronicle.event("player_join", 2).sects(sectId).region(sect.homeRegionId)
                .say(TextKeys.PLAYER_JOIN, playerName, sect.name);
        return closeDay(ctx, id);
    }

    /**
     * The player leaves their sect: the standing penalty, the rejoin cooldown from today, rank back
     * to outer, no master; contribution and borrowed manuals stay on the record. Records
     * {@code player_leave}. Throws IllegalArgumentException("not_member") when not in a sect.
     */
    public static SimEvent leave(SimContext ctx, String playerId, String playerName) {
        PlayerMember m = requireMember(ctx, playerId);
        int old = m.sectId;
        Sect sect = ctx.sect(old);
        m.playerName = playerName;
        m.leftSectId = old;
        m.leftDay = ctx.day();
        m.sectId = -1;
        m.rank = OUTER;
        m.masterId = -1;
        addStanding(m, old, ctx.rules.player().leave().standingPenalty());
        long id = ctx.chronicle.event("player_leave", 2).sects(old).region(sect == null ? "" : sect.homeRegionId)
                .say(TextKeys.PLAYER_LEAVE, playerName, sect == null ? "" : sect.name);
        return closeDay(ctx, id);
    }

    /**
     * Admin: sets a member's rank (outer, inner or elder). A rise to inner or elder records
     * {@code player_promotion}; an unchanged rank or a demotion records nothing and returns empty.
     */
    public static Optional<SimEvent> promote(SimContext ctx, String playerId, String playerName, String rank) {
        if (!RANKS.contains(rank)) {
            throw new IllegalArgumentException("unknown player rank \"" + rank + "\" (expected " + RANKS + ")");
        }
        PlayerMember m = requireMember(ctx, playerId);
        m.playerName = playerName;
        if (m.rank.equals(rank)) {
            return Optional.empty();
        }
        boolean rise = RANKS.indexOf(rank) > RANKS.indexOf(m.rank);
        m.rank = rank;
        if (!rise) {
            return Optional.empty();
        }
        return Optional.of(closeDay(ctx, promotionEvent(ctx, m, ctx.sect(m.sectId))));
    }

    // ------------------------------------------------------------------ inside a step

    /** Called once at the start of every sim year. */
    public static void yearly(SimContext ctx) {
        Rules.Player rules = ctx.rules.player();
        for (PlayerMember m : ctx.state.playerMembers.values()) {
            if (!m.inSect()) {
                continue;
            }
            Sect sect = ctx.sect(m.sectId);
            if (sect == null || !sect.active()) {
                if (sect == null) {
                    releaseQuietly(ctx, m);
                } else {
                    release(ctx, m, sect, -1);
                }
                continue;
            }
            String next = m.rank.equals(OUTER) ? INNER : m.rank.equals(INNER) ? ELDER : null;
            Rules.PlayerThreshold bar = next == null ? null
                    : next.equals(INNER) ? rules.promotion().inner() : rules.promotion().elder();
            if (bar != null && reached(ctx.realms, m.realmId, m.stageIndex, bar.stage())
                    && m.contribution >= bar.contribution()) {
                m.rank = next;
                promotionEvent(ctx, m, sect);
            }
            if (m.masterId >= 0) {
                Person master = ctx.state.persons.get(m.masterId);
                if (master == null || master.sectId != m.sectId) {
                    m.masterId = -1;
                }
            }
        }
        int recovery = rules.leave().standingRecoveryPerYear();
        for (PlayerMember m : ctx.state.playerMembers.values()) {
            for (Map.Entry<Integer, Integer> e : m.standings.entrySet()) {
                if (e.getValue() < 0) {
                    e.setValue(Math.min(0, e.getValue() + recovery));
                }
            }
        }
    }

    /** The sect is gone: its players become rogues (no standing penalty) with a {@code player_leave} line. */
    public static void sectDissolved(SimContext ctx, Sect sect) {
        sectDissolved(ctx, sect, -1);
    }

    /** As {@link #sectDissolved(SimContext, Sect)}, with the event that ended the sect as the cause. */
    public static void sectDissolved(SimContext ctx, Sect sect, long cause) {
        for (PlayerMember m : ctx.state.playerMembers.values()) {
            if (m.sectId == sect.id) {
                release(ctx, m, sect, cause);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Whether a qualification is at or past a point on the player's realm ladder: {@code mortal}
     * ranks below every ledger realm, an unknown realm id ranks as mortal, then the stage decides.
     */
    static boolean reached(RealmTable realms, PlayerQualification q, Rules.PlayerRealmStage t) {
        return reached(realms, q.realmId(), q.stageIndex(), t);
    }

    static boolean reached(RealmTable realms, String realmId, int stageIndex, Rules.PlayerRealmStage t) {
        int have = realmRank(realms, realmId);
        int need = realmRank(realms, t.realm());
        return have > need || (have == need && stageIndex >= t.stage());
    }

    private static int realmRank(RealmTable realms, String realmId) {
        return Rules.PlayerRealmStage.MORTAL.equals(realmId) ? -1 : Math.max(-1, realms.indexOf(realmId));
    }

    private static void release(SimContext ctx, PlayerMember m, Sect sect, long cause) {
        releaseQuietly(ctx, m);
        ctx.chronicle.event("player_leave", 2).sects(sect.id).region(sect.homeRegionId).cause(cause)
                .say(TextKeys.PLAYER_LEAVE_SECT_GONE, m.playerName, sect.name);
    }

    private static void releaseQuietly(SimContext ctx, PlayerMember m) {
        m.leftSectId = m.sectId;
        m.leftDay = ctx.day();
        m.sectId = -1;
        m.rank = OUTER;
        m.masterId = -1;
    }

    private static long promotionEvent(SimContext ctx, PlayerMember m, Sect sect) {
        String key = m.rank.equals(ELDER) ? TextKeys.PLAYER_PROMOTE_ELDER : TextKeys.PLAYER_PROMOTE_INNER;
        return ctx.chronicle.event("player_promotion", 2).sects(m.sectId)
                .region(sect == null ? "" : sect.homeRegionId)
                .say(key, m.playerName, sect == null ? "" : sect.name);
    }

    private static PlayerMember requireMember(SimContext ctx, String playerId) {
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        if (m == null || !m.inSect()) {
            throw new IllegalArgumentException(NOT_MEMBER);
        }
        return m;
    }

    private static void snapshot(PlayerMember m, PlayerQualification q) {
        m.realmId = q.realmId();
        m.stageIndex = q.stageIndex();
        m.awakened = q.awakened();
        m.rootPeakBp = q.rootPeakBp();
    }

    private static void addStanding(PlayerMember m, int sectId, int delta) {
        int v = m.standings.getOrDefault(sectId, 0) + delta;
        m.standings.put(sectId, Math.max(STANDING_MIN, Math.min(STANDING_MAX, v)));
    }

    /**
     * A player action happens between settled days: its event is already in the chronicle; closing
     * the open day keeps the next step from returning it again with that day's events.
     */
    private static SimEvent closeDay(SimContext ctx, long eventId) {
        ctx.chronicle.endDay();
        SimEvent e = ctx.findEvent(eventId);
        if (e == null) {
            throw new IllegalStateException("event " + eventId + " was not kept");
        }
        return e;
    }
}
