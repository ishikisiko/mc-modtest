package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.Admission;
import com.example.myvillage.sim.PlayerQualification;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.TaskView;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.PlayerMember;
import com.example.myvillage.sim.model.Sect;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * Players' business in the ledger (player sect entry, slice 1). A player is never a
 * {@link Person}: their record is a {@link PlayerMember} in {@code WorldState.playerMembers}.
 *
 * <p>Two kinds of entry point. The player actions ({@link #admission}, {@link #join}, {@link #leave},
 * {@link #promote}, {@link #borrow}, the sect tasks and {@link #apprentice}) are called by the
 * {@code WorldSim} facade between settled days; their event goes into the chronicle at once and the
 * open day is closed ({@code Chronicle.endDay}) so the next {@link Engine#step} neither returns it
 * again nor loses it. The yearly review ({@link #yearly}, run by {@link Engine#step} right after
 * {@link SectAffairs#yearly}), {@link #daily} and {@link #sectDissolved} run inside a step, so their
 * events are that day's events like any other.
 */
public final class PlayerAffairs {
    public static final String OUTER = "outer";
    public static final String INNER = "inner";
    public static final String ELDER = "elder";
    /** Player ranks, lowest first. Players never become sect master by review. */
    public static final List<String> RANKS = List.of(OUTER, INNER, ELDER);

    public static final String NOT_MEMBER = "not_member";
    public static final String NOT_BORROWABLE = "not_borrowable";
    public static final String ALREADY_BORROWED = "already_borrowed";
    /** The mortal-grade breathing method: no manual exists, so the scripture hall never lends it. */
    public static final String BASIC_BREATHING = "basic_breathing";

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
        return join(ctx, playerId, playerName, sectId, q, false);
    }

    /**
     * As {@link #join(SimContext, String, String, int, PlayerQualification)}; {@code force} (admin
     * commands) skips every admission rule but a living sect the player is not already in. A forced
     * player in another sect first leaves it without penalty or event ({@code leftSectId/leftDay}
     * still recorded). The join standing, the snapshot and the {@code player_join} event are as usual.
     */
    public static SimEvent join(SimContext ctx, String playerId, String playerName, int sectId,
                                PlayerQualification q, boolean force) {
        Sect sect = ctx.sect(sectId);
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        if (force) {
            if (sect == null || !sect.active()) {
                throw new IllegalArgumentException(Admission.SECT_INACTIVE);
            }
            if (m != null && m.sectId == sectId) {
                throw new IllegalArgumentException(Admission.ALREADY_MEMBER);
            }
            if (m != null && m.inSect()) {
                releaseQuietly(ctx, m);
            }
        } else {
            Admission a = admission(ctx, playerId, sectId, q);
            if (!a.ok()) {
                throw new IllegalArgumentException(a.reason());
            }
        }
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
     * to outer, no master, no open task ({@code taskYear} kept); contribution and borrowed manuals
     * stay on the record. Records
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
        clearTask(m);
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

    // ------------------------------------------------------------------ scripture hall (slice 2)

    /**
     * The techniques the player may borrow from their sect's scripture hall at their rank (ledger
     * ids, chain order, no duplicates). A heritage sect lends its chain: outer the first technique,
     * inner the first two, elder the whole chain. A sect without one lends its basic technique to
     * outer disciples and basic plus signature to inner disciples and elders. Empty ids and
     * {@link #BASIC_BREATHING} are left out. Empty when the player is in no living sect.
     */
    public static List<String> borrowable(SimContext ctx, String playerId) {
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        if (m == null || !m.inSect()) {
            return List.of();
        }
        Sect sect = ctx.sect(m.sectId);
        if (sect == null || !sect.active()) {
            return List.of();
        }
        List<String> raw = new ArrayList<>();
        ContentTables.Heritage heritage = ctx.data.heritage(sect.heritageId);
        if (heritage != null) {
            List<String> chain = heritage.techniques();
            int n = switch (m.rank) {
                case OUTER -> 1;
                case INNER -> 2;
                default -> chain.size();
            };
            raw.addAll(chain.subList(0, Math.min(n, chain.size())));
        } else {
            raw.add(sect.basicTechniqueId);
            if (!m.rank.equals(OUTER)) {
                raw.add(sect.signatureTechniqueId);
            }
        }
        Set<String> out = new LinkedHashSet<>();
        for (String id : raw) {
            if (id != null && !id.isEmpty() && !id.equals(BASIC_BREATHING)) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Records that the player borrowed this technique's manual and a {@code player_borrow} event
     * (timed like {@link #join}). Throws IllegalArgumentException with {@code not_member},
     * {@code not_borrowable} or {@code already_borrowed}, checked in that order.
     *
     * <p>Importance 2 like the other player events: the chronicle keeps importance-1 events per
     * person subject (a minor event without an actor is refused), and a player is never a person.
     */
    public static SimEvent borrow(SimContext ctx, String playerId, String playerName, String techniqueId) {
        PlayerMember m = requireMember(ctx, playerId);
        if (!borrowable(ctx, playerId).contains(techniqueId)) {
            throw new IllegalArgumentException(NOT_BORROWABLE);
        }
        if (m.borrowed.contains(techniqueId)) {
            throw new IllegalArgumentException(ALREADY_BORROWED);
        }
        Sect sect = ctx.sect(m.sectId);
        m.playerName = playerName;
        m.borrowed.add(techniqueId);
        ContentTables.Technique t = ctx.data.technique(techniqueId);
        String techniqueName = t == null ? techniqueId : t.name();
        long id = ctx.chronicle.event("player_borrow", 2).sects(sect.id).region(sect.homeRegionId)
                .say(TextKeys.PLAYER_BORROW, playerName, sect.name, techniqueName);
        return closeDay(ctx, id);
    }

    // ------------------------------------------------------------------ sect tasks and masters (slice 3)

    public static final String TASK_ACTIVE = "task_active";
    public static final String TASK_DONE_THIS_YEAR = "task_done_this_year";
    public static final String NO_TASK = "no_task";
    public static final String NOT_READY = "not_ready";
    public static final String RANK_TOO_LOW = "rank_too_low";
    public static final String HAS_MASTER = "has_master";
    public static final String MASTER_NOT_HERE = "master_not_here";

    /**
     * The sim year of the day the ledger stands at: {@code floorDiv(day, dpy)}, the same boundary as
     * {@link SimContext#newYear} (a year starts on a day with {@code day % dpy == 0}). Between
     * settled days this is the year of the next day to settle.
     */
    public static long year(SimContext ctx) {
        return Math.floorDiv(ctx.day(), (long) ctx.dpy);
    }

    /**
     * The task the steward would hand the player now; computed, never recorded. Empty for a player
     * in no sect, with an open task, who already took one this year, or when there is nothing to give.
     *
     * <p>The pick is fixed for the whole year: one rng {@code SimRng.at(seed, first day of the year,
     * playerId.hashCode(), Purpose.PLAYER_TASK, year)} draws a row of {@code sect_tasks.json}
     * uniformly in file order; a courier then draws its destination from the other active sects in
     * id order with the same rng, and with no other active sect the rng draws again among the
     * non-courier rows (none: empty).
     */
    public static Optional<TaskView> offerTask(SimContext ctx, String playerId) {
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        long year = year(ctx);
        if (m == null || !m.inSect() || !m.taskId.isEmpty() || m.taskYear == year) {
            return Optional.empty();
        }
        List<ContentTables.SectTask> tasks = ctx.data.sectTasks();
        if (tasks.isEmpty()) {
            return Optional.empty();
        }
        SimRng rng = SimRng.at(ctx.state.seed, year * ctx.dpy, playerId.hashCode(), Purpose.PLAYER_TASK, year);
        ContentTables.SectTask t = tasks.get(rng.nextInt(tasks.size()));
        int target = -1;
        if (ContentTables.TASK_COURIER.equals(t.kind())) {
            List<Integer> others = new ArrayList<>();
            for (Sect s : ctx.state.sects.values()) {
                if (s.active() && s.id != m.sectId) {
                    others.add(s.id);
                }
            }
            others.sort(Integer::compare);
            if (!others.isEmpty()) {
                target = others.get(rng.nextInt(others.size()));
            } else {
                List<ContentTables.SectTask> plain = tasks.stream()
                        .filter(x -> !ContentTables.TASK_COURIER.equals(x.kind())).toList();
                if (plain.isEmpty()) {
                    return Optional.empty();
                }
                t = plain.get(rng.nextInt(plain.size()));
            }
        }
        return Optional.of(new TaskView(t.id(), t.kind(), t.count(), t.contribution(), 0, target,
                target < 0 ? "" : ctx.sectName(target), year, false));
    }

    /**
     * Records the task {@link #offerTask} gives (progress 0, {@code taskYear} this year) and a
     * {@code player_task_accept} event (timed like {@link #join}). Throws IllegalArgumentException
     * with {@code not_member}, {@code task_active}, {@code task_done_this_year} or {@code no_task},
     * checked in that order.
     */
    public static SimEvent acceptTask(SimContext ctx, String playerId, String playerName) {
        PlayerMember m = requireMember(ctx, playerId);
        if (!m.taskId.isEmpty()) {
            throw new IllegalArgumentException(TASK_ACTIVE);
        }
        if (m.taskYear == year(ctx)) {
            throw new IllegalArgumentException(TASK_DONE_THIS_YEAR);
        }
        TaskView offer = offerTask(ctx, playerId).orElseThrow(() -> new IllegalArgumentException(NO_TASK));
        Sect sect = ctx.sect(m.sectId);
        m.playerName = playerName;
        m.taskId = offer.id();
        m.taskProgress = 0;
        m.taskTargetSectId = offer.targetSectId();
        m.taskYear = offer.year();
        long id = ctx.chronicle.event("player_task_accept", 2).sects(m.sectId)
                .region(sect == null ? "" : sect.homeRegionId)
                .say(TextKeys.PLAYER_TASK_ACCEPT, playerName, ctx.sectName(m.sectId), TextKeys.taskName(offer.id()));
        return closeDay(ctx, id);
    }

    /**
     * Adds {@code amount} to the open task's progress, capped at its count; no event. False (nothing
     * changes) for a player in no sect, without an open task, or whose task is not of {@code kind}.
     */
    public static boolean advanceTask(SimContext ctx, String playerId, String kind, int amount) {
        PlayerMember m = ctx.state.playerMembers.get(playerId);
        if (m == null || !m.inSect() || m.taskId.isEmpty()) {
            return false;
        }
        ContentTables.SectTask t = ctx.data.sectTask(m.taskId);
        if (t == null || !t.kind().equals(kind)) {
            return false;
        }
        m.taskProgress = (int) Math.max(0, Math.min((long) t.count(), (long) m.taskProgress + amount));
        return true;
    }

    /**
     * Turns in the open task: contribution += its contribution, the task cleared ({@code taskYear}
     * kept: one task a year) and a {@code player_task_done} event (timed like {@link #join}). Tribute
     * is judged by the caller, who has already checked and taken the stones; other kinds need
     * {@code progress >= count}, and a courier addressed to the player's own sect is never ready.
     * Throws IllegalArgumentException with {@code not_member},
     * {@code no_task} or {@code not_ready}, checked in that order.
     */
    public static SimEvent completeTask(SimContext ctx, String playerId, String playerName) {
        PlayerMember m = requireMember(ctx, playerId);
        ContentTables.SectTask t = m.taskId.isEmpty() ? null : ctx.data.sectTask(m.taskId);
        if (t == null) {
            throw new IllegalArgumentException(NO_TASK);
        }
        if (!ContentTables.TASK_TRIBUTE.equals(t.kind()) && m.taskProgress < t.count()) {
            throw new IllegalArgumentException(NOT_READY);
        }
        if (ContentTables.TASK_COURIER.equals(t.kind()) && m.taskTargetSectId == m.sectId) {
            // a letter to one's own sect (a task carried over from another sect) is never delivered
            throw new IllegalArgumentException(NOT_READY);
        }
        Sect sect = ctx.sect(m.sectId);
        m.playerName = playerName;
        m.contribution += t.contribution();
        clearTask(m);
        long id = ctx.chronicle.event("player_task_done", 2).sects(m.sectId)
                .region(sect == null ? "" : sect.homeRegionId)
                .say(TextKeys.PLAYER_TASK_DONE, playerName, ctx.sectName(m.sectId), TextKeys.taskName(t.id()));
        return closeDay(ctx, id);
    }

    /**
     * Takes {@code masterId} as the player's master and records {@code player_apprentice} (timed like
     * {@link #join}). The master must be a living elder or sect master of the player's sect who is at
     * the sect. Players do not take a {@code disciplesPerMaster} place. Throws
     * IllegalArgumentException with {@code not_member}, {@code rank_too_low} (outer disciple),
     * {@code has_master} or {@code master_not_here}, checked in that order.
     */
    public static SimEvent apprentice(SimContext ctx, String playerId, String playerName, int masterId) {
        PlayerMember m = requireMember(ctx, playerId);
        if (m.rank.equals(OUTER)) {
            throw new IllegalArgumentException(RANK_TOO_LOW);
        }
        if (m.masterId >= 0) {
            throw new IllegalArgumentException(HAS_MASTER);
        }
        Person master = ctx.state.persons.get(masterId);
        if (master == null || master.sectId != m.sectId || !master.status.equals("at_sect")
                || !(master.rank.equals("elder") || master.rank.equals("sect_master"))) {
            throw new IllegalArgumentException(MASTER_NOT_HERE);
        }
        Sect sect = ctx.sect(m.sectId);
        m.playerName = playerName;
        m.masterId = masterId;
        long id = ctx.chronicle.event("player_apprentice", 2).actors(masterId).sects(m.sectId)
                .region(sect == null ? "" : sect.homeRegionId)
                .say(TextKeys.PLAYER_APPRENTICE, playerName, ctx.sectName(m.sectId), master.name());
        return closeDay(ctx, id);
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

    /**
     * Called every settled day after the people have acted: a player whose master died or left the
     * player's sect loses the master, with a {@code player_master_lost} line. (The yearly review still
     * clears such a master silently, as a backstop.)
     */
    public static void daily(SimContext ctx) {
        for (PlayerMember m : ctx.state.playerMembers.values()) {
            if (!m.inSect() || m.masterId < 0) {
                continue;
            }
            Person master = ctx.state.persons.get(m.masterId);
            if (master != null && master.sectId == m.sectId) {
                continue;
            }
            int lost = m.masterId;
            m.masterId = -1;
            Sect sect = ctx.sect(m.sectId);
            Chronicle.Builder b = ctx.chronicle.event("player_master_lost", 2).sects(m.sectId)
                    .region(sect == null ? "" : sect.homeRegionId);
            String masterName = ctx.nameOf(lost);
            if (!masterName.isEmpty()) {
                b.actors(lost);
            }
            b.say(TextKeys.PLAYER_MASTER_LOST, m.playerName, masterName);
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

    /** Out of the sect without an event (a forced move, the sect gone): no rank, master or open task. */
    private static void releaseQuietly(SimContext ctx, PlayerMember m) {
        m.leftSectId = m.sectId;
        m.leftDay = ctx.day();
        m.sectId = -1;
        m.rank = OUTER;
        m.masterId = -1;
        clearTask(m);
    }

    /** No open task; {@code taskYear} is kept, so leaving does not buy a second task this year. */
    private static void clearTask(PlayerMember m) {
        m.taskId = "";
        m.taskProgress = 0;
        m.taskTargetSectId = -1;
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
