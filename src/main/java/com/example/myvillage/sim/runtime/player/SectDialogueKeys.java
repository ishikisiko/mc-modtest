package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.sim.Admission;
import com.example.myvillage.sim.engine.PlayerAffairs;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sect dialogue's text as data: each scene line is the language key
 * {@code world_sim.dialogue.<base>.<n>} ({@code n} = 1..{@link #VARIANTS}), and takes
 * {@link #PARAMS} positional params. There is no dialogue.json; {@code SectDialogueKeysTest}
 * pins both language files to these two tables.
 */
public final class SectDialogueKeys {
    public static final String PREFIX = "world_sim.dialogue.";

    public static final String STEWARD_GREET = "steward.greet";
    public static final String STEWARD_INTRO = "steward.intro";
    public static final String STEWARD_INVITE = "steward.invite";
    public static final String STEWARD_REFUSE = "steward.refuse.";
    public static final String STEWARD_WELCOME = "steward.welcome";
    public static final String STEWARD_MEMBER = "steward.member";
    public static final String STEWARD_LEAVE_ASK = "steward.leave_ask";
    public static final String STEWARD_FAREWELL_LEFT = "steward.farewell_left";
    public static final String ELDER_GREET = "elder.greet";
    public static final String ELDER_MEMBER = "elder.member";

    // sect tasks (宗门事务) and apprenticeship (拜师), slice 3
    public static final String TASK_OFFER = "steward.task.offer";
    public static final String TASK_PROGRESS = "steward.task.progress";
    public static final String TASK_READY = "steward.task.ready";
    public static final String TASK_NONE_THIS_YEAR = "steward.task.none_this_year";
    public static final String TASK_ACCEPTED = "steward.task.accepted";
    public static final String TASK_DONE = "steward.task.done";
    public static final String TASK_REFUSE = "steward.task.refuse.";
    public static final String APPRENTICE_OFFER = "elder.apprentice.offer";
    public static final String APPRENTICE_DONE = "elder.apprentice.done";
    public static final String APPRENTICE_REFUSE = "elder.apprentice.refuse.";

    /** A join or leave refused for want of an active ledger (not an {@link Admission} reason). */
    public static final String INACTIVE = "inactive";
    /** A leave from someone who is not in a sect (not an {@link Admission} reason). */
    public static final String NOT_MEMBER = "not_member";

    /** Every refusal reason with a line: the {@link Admission} reasons plus {@link #INACTIVE} and {@link #NOT_MEMBER}. */
    public static final List<String> REFUSALS = List.of(
            Admission.SECT_INACTIVE, Admission.ALREADY_MEMBER, Admission.MEMBER_ELSEWHERE, Admission.NOT_AWAKENED,
            Admission.REALM_TOO_LOW, Admission.SELECTIVE, Admission.REJOIN_COOLDOWN, Admission.STANDING_TOO_LOW,
            INACTIVE, NOT_MEMBER);

    /** Turn-in of a tribute task without the stones in the inventory (not a ledger reason). */
    public static final String TRIBUTE_SHORT = "tribute_short";
    /**
     * Every task refusal with a line: the reasons of {@code WorldSim.acceptTask} and
     * {@code completeTask}, {@link #TRIBUTE_SHORT}, and {@link #INACTIVE} (anything else reads as it).
     */
    public static final List<String> TASK_REFUSALS = List.of(
            NOT_MEMBER, PlayerAffairs.TASK_ACTIVE, PlayerAffairs.TASK_DONE_THIS_YEAR, PlayerAffairs.NO_TASK,
            PlayerAffairs.NOT_READY, TRIBUTE_SHORT, INACTIVE);
    /** Every apprenticeship refusal with a line: the reasons of {@code WorldSim.apprentice} and {@link #INACTIVE}. */
    public static final List<String> APPRENTICE_REFUSALS = List.of(
            NOT_MEMBER, PlayerAffairs.RANK_TOO_LOW, PlayerAffairs.HAS_MASTER, PlayerAffairs.MASTER_NOT_HERE,
            INACTIVE);

    /** Scene base → number of variants. */
    public static final Map<String, Integer> VARIANTS;
    /** Scene base → number of params. */
    public static final Map<String, Integer> PARAMS;

    static {
        Map<String, Integer> variants = new LinkedHashMap<>();
        Map<String, Integer> params = new LinkedHashMap<>();
        put(variants, params, STEWARD_GREET, 2, 1);          // sect name
        put(variants, params, STEWARD_INTRO, 1, 4);          // sect name, prestige, people at the gate, master
        put(variants, params, STEWARD_INVITE, 2, 1);         // sect name
        for (String reason : REFUSALS) {
            put(variants, params, STEWARD_REFUSE + reason, 1, 0);
        }
        put(variants, params, STEWARD_WELCOME, 1, 1);        // sect name
        put(variants, params, STEWARD_MEMBER, 2, 1);         // the player's rank
        put(variants, params, STEWARD_LEAVE_ASK, 1, 0);
        put(variants, params, STEWARD_FAREWELL_LEFT, 1, 1);  // sect name
        put(variants, params, ELDER_GREET, 2, 1);            // sect name
        put(variants, params, ELDER_MEMBER, 1, 1);           // the player's rank
        put(variants, params, TASK_OFFER, 2, 2);             // task name, brief
        put(variants, params, TASK_PROGRESS, 2, 3);          // task name, progress, count
        put(variants, params, TASK_READY, 1, 1);             // task name
        put(variants, params, TASK_NONE_THIS_YEAR, 1, 0);
        put(variants, params, TASK_ACCEPTED, 1, 2);          // task name, brief
        put(variants, params, TASK_DONE, 1, 2);              // task name, contribution gained
        for (String reason : TASK_REFUSALS) {
            put(variants, params, TASK_REFUSE + reason, 1, 0);
        }
        put(variants, params, APPRENTICE_OFFER, 1, 1);       // the elder's name
        put(variants, params, APPRENTICE_DONE, 1, 1);        // the elder's (now master's) name
        for (String reason : APPRENTICE_REFUSALS) {
            put(variants, params, APPRENTICE_REFUSE + reason, 1, 0);
        }
        VARIANTS = Collections.unmodifiableMap(variants);
        PARAMS = Collections.unmodifiableMap(params);
    }

    private SectDialogueKeys() {
    }

    private static void put(Map<String, Integer> variants, Map<String, Integer> params, String base, int n, int p) {
        variants.put(base, n);
        params.put(base, p);
    }

    /** The language key of variant {@code n} (1-based) of a scene base. */
    public static String key(String base, int n) {
        Integer variants = VARIANTS.get(base);
        if (variants == null) {
            throw new IllegalArgumentException("unknown dialogue scene " + base);
        }
        if (n < 1 || n > variants) {
            throw new IllegalArgumentException("scene " + base + " has variants 1.." + variants + ", got " + n);
        }
        return PREFIX + base + "." + n;
    }

    /** A variant of the scene chosen by {@code salt} (e.g. player hash + day), stable for equal salts. */
    public static String pick(String base, long salt) {
        Integer variants = VARIANTS.get(base);
        if (variants == null) {
            throw new IllegalArgumentException("unknown dialogue scene " + base);
        }
        return key(base, (int) Math.floorMod(salt, (long) variants) + 1);
    }

    /** The refusal scene for a reason; a reason without its own line reads as {@link #INACTIVE}. */
    public static String refusal(String reason) {
        return STEWARD_REFUSE + (REFUSALS.contains(reason) ? reason : INACTIVE);
    }

    /** The task refusal scene for a reason; a reason without its own line reads as {@link #INACTIVE}. */
    public static String taskRefusal(String reason) {
        return TASK_REFUSE + (TASK_REFUSALS.contains(reason) ? reason : INACTIVE);
    }

    /** The apprenticeship refusal scene for a reason; a reason without its own line reads as {@link #INACTIVE}. */
    public static String apprenticeRefusal(String reason) {
        return APPRENTICE_REFUSE + (APPRENTICE_REFUSALS.contains(reason) ? reason : INACTIVE);
    }
}
