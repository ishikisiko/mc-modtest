package com.example.myvillage.sim.runtime.net;

import java.util.List;
import java.util.Objects;

/**
 * The server's answer to one {@link WorldSimQuery}: a bounded, read-only slice of the ledger with
 * every name already resolved, so the client renders it without a ledger of its own. Which
 * sections are filled depends on the query kind (the table below); the others are null or empty.
 * Prose stays as language keys with params ({@link EventLine}, realm ids, ranks, statuses) so each
 * client reads it in its own language; person, sect, technique and region names are literals.
 *
 * <pre>
 * kind           overview sects                 sect  persons                 person events                  causes region
 * OVERVIEW       yes      -                     -     the five foremost       -      -                       -      -
 * SECTS          -        all, active first     -     -                       -      -                       -      -
 * SECT           -        -                     yes   members at the sect     -      the sect's recent       yes    -
 * PERSON_SEARCH  -        -                     -     matches, living first   -      -                       -      -
 * PERSON         -        -                     -     -                       yes    the person's recent     yes    -
 * CHRONICLE      -        -                     -     -                       -      latest notable + major  yes    -
 * HERE           -        seated here           -     strongest present       -      recent here             yes    yes
 * </pre>
 *
 * <p>{@code mine} (the asking player's own sect record, 0.41.0) is filled for {@code OVERVIEW} while the
 * player belongs to a sect, and for {@code SECT} only when the sect asked about is the player's own;
 * it is null otherwise. A {@link SectSummary#bearing()} is filled only in {@code HERE}.
 *
 * @param query          the query answered; the client caches by it
 * @param active         false while the ledger is inactive; then only {@code inactiveReason} is meaningful
 * @param inactiveReason the server's reason (English, for {@code commands.myvillage.world.inactive}), "" when active
 * @param day            the ledger's current sim day (ages are {@code day - birthDay})
 * @param prehistoryDays sim days before 启元一年 (dates: {@code SimDate.of(day, prehistoryDays, daysPerYear)})
 * @param daysPerYear    the calendar's current days per year
 * @param events         chronicle lines, oldest first
 * @param causes         the events that {@code events} point at through {@code causeId}, when still kept
 * @param mine           the asking player's sect membership (see above), or null
 */
public record WorldSimSnapshot(
        WorldSimQuery query,
        boolean active,
        String inactiveReason,
        long day,
        long prehistoryDays,
        int daysPerYear,
        Overview overview,
        List<SectSummary> sects,
        SectDetail sect,
        List<PersonSummary> persons,
        PersonDetail person,
        List<EventLine> events,
        List<EventLine> causes,
        Region region,
        MySect mine) {

    /** Most sects in a {@link WorldSimQuery.Kind#SECTS} answer. */
    public static final int MAX_SECTS = 96;
    /** Most members listed for one sect, strongest first. */
    public static final int MAX_MEMBERS = 24;
    /** Most matches for a person search. */
    public static final int MAX_SEARCH = 10;
    /** Most people listed as present in a region or as the foremost of the world. */
    public static final int MAX_PRESENT = 10;
    /** Most chronicle lines in a {@link WorldSimQuery.Kind#CHRONICLE} answer. */
    public static final int MAX_CHRONICLE = 40;
    /** Most recent events attached to one sect, person or region. */
    public static final int MAX_RELATED = 10;

    public WorldSimSnapshot {
        Objects.requireNonNull(query, "query");
        inactiveReason = inactiveReason == null ? "" : inactiveReason;
        sects = sects == null ? List.of() : List.copyOf(sects);
        persons = persons == null ? List.of() : List.copyOf(persons);
        events = events == null ? List.of() : List.copyOf(events);
        causes = causes == null ? List.of() : List.copyOf(causes);
    }

    /** The answer while the ledger is inactive. */
    public static WorldSimSnapshot inactive(WorldSimQuery query, String reason) {
        return new WorldSimSnapshot(query, false, reason, 0, 0, 1, null, List.of(), null, List.of(), null,
                List.of(), List.of(), null, null);
    }

    /** The cause line for an event, or null when it has none or it is no longer kept. */
    public EventLine causeOf(EventLine event) {
        if (event.causeId() < 0) {
            return null;
        }
        for (EventLine c : causes) {
            if (c.id() == event.causeId()) {
                return c;
            }
        }
        return null;
    }

    /**
     * World summary.
     *
     * @param livingByRealm living count per realm id, in realm order
     * @param paused        settlement paused by command
     * @param pendingDays   sim days waiting to be settled
     * @param calendarDay   the cultivation calendar's day index
     */
    public record Overview(
            String tierId,
            int population,
            int targetPopulation,
            List<RealmCount> livingByRealm,
            int activeSects,
            int destroyedSects,
            int deadCount,
            long eventCount,
            boolean paused,
            int pendingDays,
            long calendarDay) {
        public Overview {
            livingByRealm = livingByRealm == null ? List.of() : List.copyOf(livingByRealm);
        }
    }

    public record RealmCount(String realmId, int count) {
    }

    /**
     * One sect in a list.
     *
     * @param topRealmId "" when the sect has no living member
     * @param prestige   rounded
     * @param distance   blocks from the asking player to the gate ({@link WorldSimQuery.Kind#HERE}); -1 otherwise
     * @param bearing    the gate's direction from the asking player, one of {@code n ne e se s sw w nw}
     *                   ({@link WorldSimQuery.Kind#HERE}); "" otherwise
     */
    public record SectSummary(
            int id,
            String name,
            String regionName,
            String masterName,
            int memberCount,
            String topRealmId,
            int prestige,
            int gateX,
            int gateZ,
            boolean gateRealized,
            boolean active,
            int distance,
            String bearing) {
        public SectSummary {
            bearing = bearing == null ? "" : bearing;
        }
    }

    /**
     * One sect in detail; the people and events of the sect travel in the snapshot's
     * {@code persons} and {@code events}.
     *
     * @param parentSectId   -1 when the sect did not split from another
     * @param destroyedDay   -1 while active
     * @param resources      rounded
     * @param heritageName   the Chinese name of the heritage (传承) the sect holds or, once destroyed,
     *                       held; null for none
     */
    public record SectDetail(
            SectSummary summary,
            long foundedDay,
            int founderId,
            String founderName,
            int masterId,
            int parentSectId,
            String parentSectName,
            long destroyedDay,
            int resources,
            String signatureTechniqueName,
            String heritageName,
            List<SectRelation> relations) {
        public SectDetail {
            relations = relations == null ? List.of() : List.copyOf(relations);
        }
    }

    /** Standing towards another sect: value -100..100 and state none/feud/war. */
    public record SectRelation(int otherSectId, String otherSectName, int value, String state) {
    }

    /**
     * One person in a list.
     *
     * @param title    the Daoist title ("" when none)
     * @param stage    0-based stage within the realm
     * @param sectId   -1 for a rogue
     * @param rank     sect_master, elder, inner, outer or rogue
     */
    public record PersonSummary(
            int id,
            String name,
            String title,
            boolean alive,
            String realmId,
            int stage,
            int sectId,
            String sectName,
            String rank) {
    }

    /**
     * One person in detail; the person's recent events travel in the snapshot's {@code events}.
     * Fields only the living have are neutral for the dead ("" / -1 / 0), and the death fields
     * are neutral for the living.
     *
     * @param root     five-element basis points over metal, wood, water, fire, earth (sum 10000); empty for the dead
     * @param status   at_sect, travelling or secluded; "dead" for the dead
     * @param injury   0 when unhurt
     */
    public record PersonDetail(
            PersonSummary summary,
            String gender,
            long birthDay,
            long deathDay,
            String deathCause,
            int killerId,
            String killerName,
            String rootGrade,
            List<Integer> root,
            double progress,
            int masterId,
            String masterName,
            String regionName,
            String status,
            String techniqueName,
            int injury,
            List<PersonRelation> relations) {
        public PersonDetail {
            root = root == null ? List.of() : List.copyOf(root);
            relations = relations == null ? List.of() : List.copyOf(relations);
        }
    }

    /** A relation to another person: kind friend/enemy/disciple/master..., strength as the ledger keeps it. */
    public record PersonRelation(int otherId, String otherName, String kind, int strength) {
    }

    /**
     * One chronicle line: {@code Component.translatable(textKey, params)} where a param starting
     * with {@code @} is itself a language key (see {@code WorldSimText.event}).
     *
     * @param importance 1 minor, 2 notable, 3 major
     * @param subjectId  the first actor (a person id), or -1
     * @param causeId    the event this one answers, or -1
     */
    public record EventLine(
            long id,
            long day,
            int importance,
            int subjectId,
            String textKey,
            List<String> params,
            long causeId) {
        public EventLine {
            params = params == null ? List.of() : List.copyOf(params);
        }
    }

    /**
     * The asking player's record in their sect (拜入宗门).
     *
     * @param rank          outer, inner or elder
     * @param joinedDay     sim day of joining
     * @param masterName    "" without a master
     * @param standing      the player's standing (交情, -100..100) with this sect
     * @param borrowedCount techniques borrowed from the sect's scripture hall
     * @param sectActive    false once the sect is destroyed
     */
    public record MySect(
            int sectId,
            String sectName,
            String rank,
            long joinedDay,
            String masterName,
            int contribution,
            int standing,
            int borrowedCount,
            boolean sectActive) {
        public MySect {
            sectName = sectName == null ? "" : sectName;
            rank = rank == null ? "" : rank;
            masterName = masterName == null ? "" : masterName;
        }
    }

    /** The asking player's region. */
    public record Region(
            String id,
            String displayName,
            int tier,
            int qiLo,
            int qiHi,
            int dangerLo,
            int dangerHi,
            boolean admitsSects,
            int livingCount) {
    }
}
