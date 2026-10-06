package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.sim.Admission;
import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.TaskView;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.engine.PlayerAffairs;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The pure decisions of the sect dialogue: which scene a steward or elder shows a player, which
 * options it offers, and which params each line takes. No world access, so it is unit-tested; the
 * server ({@link SectDialogue}) turns a scene into components and a payload.
 */
public final class SectDialogueScenes {
    /** Dialogue roles (the avatar's {@code NpcEntity#ledgerRole()}). */
    public static final String ROLE_STEWARD = "steward";
    public static final String ROLE_ELDER = "elder";

    /** A choice offered to the player; {@link #id()} is its fixed network id, shared with {@code SectIntentPayload.kind}. */
    public enum Option {
        JOIN(0), LEAVE(1), FAREWELL(2), APPRENTICE(3), TASK_ACCEPT(4), TASK_TURN_IN(5);

        private final int id;

        Option(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        /** The option of a network id; throws IllegalArgumentException for an unknown one. */
        public static Option of(int id) {
            for (Option o : values()) {
                if (o.id == id) {
                    return o;
                }
            }
            throw new IllegalArgumentException("unknown sect dialogue option " + id);
        }
    }

    /**
     * What the speaker says and offers. {@code lines} are scene bases of {@link SectDialogueKeys}
     * (the first is the scene itself), {@code options} the buttons in order.
     */
    public record Scene(List<String> lines, List<Option> options) {
        public Scene {
            lines = List.copyOf(lines);
            options = List.copyOf(options);
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("a scene says something");
            }
        }

        /** The scene base (the first line). */
        public String key() {
            return lines.get(0);
        }
    }

    /**
     * What the line params are made of; {@code master}, {@code rank}, {@code taskName} and
     * {@code taskBrief} are display objects (components). {@code speaker} is the avatar's name;
     * the task fields are those of the task the lines talk about (the open task, the offer, or the
     * one just turned in), empty and 0 when there is none.
     */
    public record Facts(String sectName, int prestige, int memberCount, Object master, Object rank,
                        String speaker, Object taskName, Object taskBrief, int taskProgress, int taskCount,
                        int taskContribution) {
        public Facts {
            Objects.requireNonNull(sectName, "sectName");
            Objects.requireNonNull(master, "master");
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(speaker, "speaker");
            Objects.requireNonNull(taskName, "taskName");
            Objects.requireNonNull(taskBrief, "taskBrief");
        }

        /** Facts without a speaker name or a task. */
        public Facts(String sectName, int prestige, int memberCount, Object master, Object rank) {
            this(sectName, prestige, memberCount, master, rank, "", "", "", 0, 0, 0);
        }
    }

    /**
     * What the dialogue knows about the player's sect task and master, for {@link #decide}.
     *
     * @param task          the player's open task
     * @param offer         the task the steward would hand out now ({@code WorldSim.offerTask})
     * @param tributeReady  the inventory holds the stones an open tribute task asks for
     * @param canApprentice the speaker may take the player as disciple ({@link #canApprentice})
     */
    public record Affairs(Optional<TaskView> task, Optional<TaskView> offer, boolean tributeReady,
                          boolean canApprentice) {
        public static final Affairs NONE = new Affairs(Optional.empty(), Optional.empty(), false, false);

        public Affairs {
            Objects.requireNonNull(task, "task");
            Objects.requireNonNull(offer, "offer");
        }
    }

    private SectDialogueScenes() {
    }

    /** {@link #decide(String, Optional, Admission, int, Affairs)} without tasks or apprenticeship. */
    public static Scene decide(String role, Optional<PlayerMemberView> me, Admission admission, int sectId) {
        return decide(role, me, admission, sectId, Affairs.NONE);
    }

    /**
     * The scene of an opened dialogue. Steward: a non-member who may join is invited (JOIN,
     * FAREWELL); one who may not hears the reason (FAREWELL); a member of another sect is turned
     * away. A member of this sect is greeted by rank, hears about their sect task, and may leave:
     * <ul>
     *   <li>no open task, an offer → {@code steward.task.offer} (TASK_ACCEPT, LEAVE, FAREWELL);</li>
     *   <li>an open task not yet done → {@code steward.task.progress} (LEAVE, FAREWELL);</li>
     *   <li>an open task done ({@link #taskReady}) → {@code steward.task.ready} (TASK_TURN_IN, LEAVE, FAREWELL);</li>
     *   <li>no open task, no offer, a task taken before ({@code taskYear >= 0}: this year's is
     *       spent) → {@code steward.task.none_this_year} (LEAVE, FAREWELL);</li>
     *   <li>otherwise the plain member scene (LEAVE, FAREWELL).</li>
     * </ul>
     * The task line comes between {@code steward.member} and {@code steward.leave_ask}. Elder: a
     * greeting (FAREWELL); a member of this sect whom the elder may take as disciple is offered
     * apprenticeship ({@code elder.apprentice.offer}: APPRENTICE, FAREWELL), any other member is
     * greeted by rank.
     */
    public static Scene decide(String role, Optional<PlayerMemberView> me, Admission admission, int sectId,
                               Affairs affairs) {
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(affairs, "affairs");
        boolean here = me.isPresent() && me.get().inSect() && me.get().sectId() == sectId;
        boolean elsewhere = me.isPresent() && me.get().inSect() && me.get().sectId() != sectId;
        if (ROLE_ELDER.equals(role)) {
            if (here && affairs.canApprentice()) {
                return new Scene(List.of(SectDialogueKeys.APPRENTICE_OFFER), List.of(Option.APPRENTICE, Option.FAREWELL));
            }
            return new Scene(List.of(here ? SectDialogueKeys.ELDER_MEMBER : SectDialogueKeys.ELDER_GREET),
                    List.of(Option.FAREWELL));
        }
        if (!ROLE_STEWARD.equals(role)) {
            throw new IllegalArgumentException("no dialogue for role " + role);
        }
        if (here) {
            return memberScene(me.get(), affairs);
        }
        if (elsewhere) {
            return refused(Admission.MEMBER_ELSEWHERE);
        }
        if (admission.ok()) {
            return new Scene(List.of(SectDialogueKeys.STEWARD_INVITE), List.of(Option.JOIN, Option.FAREWELL));
        }
        return refused(admission.reason());
    }

    private static Scene memberScene(PlayerMemberView me, Affairs affairs) {
        String taskLine;
        List<Option> options;
        if (affairs.task().isPresent()) {
            if (taskReady(affairs.task().get(), affairs.tributeReady())) {
                taskLine = SectDialogueKeys.TASK_READY;
                options = List.of(Option.TASK_TURN_IN, Option.LEAVE, Option.FAREWELL);
            } else {
                taskLine = SectDialogueKeys.TASK_PROGRESS;
                options = List.of(Option.LEAVE, Option.FAREWELL);
            }
        } else if (affairs.offer().isPresent()) {
            taskLine = SectDialogueKeys.TASK_OFFER;
            options = List.of(Option.TASK_ACCEPT, Option.LEAVE, Option.FAREWELL);
        } else if (me.taskYear() >= 0) {
            taskLine = SectDialogueKeys.TASK_NONE_THIS_YEAR;
            options = List.of(Option.LEAVE, Option.FAREWELL);
        } else {
            return new Scene(List.of(SectDialogueKeys.STEWARD_MEMBER, SectDialogueKeys.STEWARD_LEAVE_ASK),
                    List.of(Option.LEAVE, Option.FAREWELL));
        }
        return new Scene(List.of(SectDialogueKeys.STEWARD_MEMBER, taskLine, SectDialogueKeys.STEWARD_LEAVE_ASK),
                options);
    }

    /**
     * Whether an open task can be turned in: a tribute when the inventory holds the stones
     * ({@code tributeReady}), any other kind when its progress has reached its count.
     */
    public static boolean taskReady(TaskView task, boolean tributeReady) {
        if (ContentTables.TASK_TRIBUTE.equals(task.kind())) {
            return tributeReady;
        }
        return task.progress() >= task.count();
    }

    /**
     * Whether an elder may take the player as disciple: the player is of this sect above outer
     * rank and has no master, and the speaker is an elder or the sect master who is at the sect.
     */
    public static boolean canApprentice(Optional<PlayerMemberView> me, int sectId, String speakerRank,
                                        String speakerStatus) {
        if (me.isEmpty() || !me.get().inSect() || me.get().sectId() != sectId) {
            return false;
        }
        boolean aboveOuter = !me.get().rank().isEmpty() && !PlayerAffairs.OUTER.equals(me.get().rank());
        boolean elder = "elder".equals(speakerRank) || "sect_master".equals(speakerRank);
        return aboveOuter && me.get().masterId() < 0 && elder && "at_sect".equals(speakerStatus);
    }

    /** After a successful TASK_ACCEPT. */
    public static Scene taskAccepted() {
        return new Scene(List.of(SectDialogueKeys.TASK_ACCEPTED), List.of(Option.FAREWELL));
    }

    /** After a successful TASK_TURN_IN. */
    public static Scene taskDone() {
        return new Scene(List.of(SectDialogueKeys.TASK_DONE), List.of(Option.FAREWELL));
    }

    /** A TASK_ACCEPT or TASK_TURN_IN refused by reason (an unknown reason reads as {@code inactive}). */
    public static Scene taskRefused(String reason) {
        return new Scene(List.of(SectDialogueKeys.taskRefusal(reason)), List.of(Option.FAREWELL));
    }

    /** After a successful APPRENTICE. */
    public static Scene apprenticed() {
        return new Scene(List.of(SectDialogueKeys.APPRENTICE_DONE), List.of(Option.FAREWELL));
    }

    /** An APPRENTICE refused by reason (an unknown reason reads as {@code inactive}). */
    public static Scene apprenticeRefused(String reason) {
        return new Scene(List.of(SectDialogueKeys.apprenticeRefusal(reason)), List.of(Option.FAREWELL));
    }

    /** After a successful JOIN. */
    public static Scene welcome() {
        return new Scene(List.of(SectDialogueKeys.STEWARD_WELCOME), List.of(Option.FAREWELL));
    }

    /** After a successful LEAVE. */
    public static Scene farewellLeft() {
        return new Scene(List.of(SectDialogueKeys.STEWARD_FAREWELL_LEFT), List.of(Option.FAREWELL));
    }

    /** A refusal by reason (an unknown reason reads as {@code inactive}). */
    public static Scene refused(String reason) {
        return new Scene(List.of(SectDialogueKeys.refusal(reason)), List.of(Option.FAREWELL));
    }

    /**
     * The lines an opened dialogue shows: the steward greets and
     * introduces the sect before the scene; an elder introduces it to a non-member after a greeting.
     */
    public static List<String> openingLines(String role, Scene scene) {
        List<String> out = new ArrayList<>();
        if (ROLE_STEWARD.equals(role)) {
            out.add(SectDialogueKeys.STEWARD_GREET);
            out.add(SectDialogueKeys.STEWARD_INTRO);
            out.addAll(scene.lines());
        } else {
            out.addAll(scene.lines());
            if (scene.key().equals(SectDialogueKeys.ELDER_GREET)) {
                out.add(SectDialogueKeys.STEWARD_INTRO);
            }
        }
        return out;
    }

    /** The params of one scene line, as many as {@link SectDialogueKeys#PARAMS} says. */
    public static Object[] args(String base, Facts facts) {
        Object[] out = switch (base) {
            case SectDialogueKeys.STEWARD_GREET, SectDialogueKeys.STEWARD_INVITE, SectDialogueKeys.STEWARD_WELCOME,
                 SectDialogueKeys.STEWARD_FAREWELL_LEFT, SectDialogueKeys.ELDER_GREET ->
                    new Object[] {facts.sectName()};
            case SectDialogueKeys.STEWARD_INTRO -> new Object[] {facts.sectName(), String.valueOf(facts.prestige()),
                    String.valueOf(facts.memberCount()), facts.master()};
            case SectDialogueKeys.STEWARD_MEMBER, SectDialogueKeys.ELDER_MEMBER -> new Object[] {facts.rank()};
            case SectDialogueKeys.TASK_OFFER, SectDialogueKeys.TASK_ACCEPTED ->
                    new Object[] {facts.taskName(), facts.taskBrief()};
            case SectDialogueKeys.TASK_PROGRESS -> new Object[] {facts.taskName(),
                    String.valueOf(facts.taskProgress()), String.valueOf(facts.taskCount())};
            case SectDialogueKeys.TASK_READY -> new Object[] {facts.taskName()};
            case SectDialogueKeys.TASK_DONE -> new Object[] {facts.taskName(), String.valueOf(facts.taskContribution())};
            case SectDialogueKeys.APPRENTICE_OFFER, SectDialogueKeys.APPRENTICE_DONE -> new Object[] {facts.speaker()};
            default -> new Object[0];
        };
        Integer expected = SectDialogueKeys.PARAMS.get(base);
        if (expected == null || expected != out.length) {
            throw new IllegalArgumentException("scene " + base + " takes " + expected + " params, built " + out.length);
        }
        return out;
    }
}
