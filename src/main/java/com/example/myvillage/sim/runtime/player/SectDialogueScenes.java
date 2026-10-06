package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.sim.Admission;
import com.example.myvillage.sim.PlayerMemberView;
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

    /** What the line params are made of; {@code master} and {@code rank} are display objects (components). */
    public record Facts(String sectName, int prestige, int memberCount, Object master, Object rank) {
        public Facts {
            Objects.requireNonNull(sectName, "sectName");
            Objects.requireNonNull(master, "master");
            Objects.requireNonNull(rank, "rank");
        }
    }

    private SectDialogueScenes() {
    }

    /**
     * The scene of an opened dialogue. Steward: a non-member who may join is invited (JOIN,
     * FAREWELL); one who may not hears the reason (FAREWELL); a member of this sect is greeted by
     * rank and may leave (LEAVE, FAREWELL); a member of another sect is turned away. Elder: a
     * greeting, by rank for a member of this sect (FAREWELL only).
     */
    public static Scene decide(String role, Optional<PlayerMemberView> me, Admission admission, int sectId) {
        Objects.requireNonNull(admission, "admission");
        boolean here = me.isPresent() && me.get().inSect() && me.get().sectId() == sectId;
        boolean elsewhere = me.isPresent() && me.get().inSect() && me.get().sectId() != sectId;
        if (ROLE_ELDER.equals(role)) {
            return new Scene(List.of(here ? SectDialogueKeys.ELDER_MEMBER : SectDialogueKeys.ELDER_GREET),
                    List.of(Option.FAREWELL));
        }
        if (!ROLE_STEWARD.equals(role)) {
            throw new IllegalArgumentException("no dialogue for role " + role);
        }
        if (here) {
            return new Scene(List.of(SectDialogueKeys.STEWARD_MEMBER, SectDialogueKeys.STEWARD_LEAVE_ASK),
                    List.of(Option.LEAVE, Option.FAREWELL));
        }
        if (elsewhere) {
            return refused(Admission.MEMBER_ELSEWHERE);
        }
        if (admission.ok()) {
            return new Scene(List.of(SectDialogueKeys.STEWARD_INVITE), List.of(Option.JOIN, Option.FAREWELL));
        }
        return refused(admission.reason());
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
            default -> new Object[0];
        };
        Integer expected = SectDialogueKeys.PARAMS.get(base);
        if (expected == null || expected != out.length) {
            throw new IllegalArgumentException("scene " + base + " takes " + expected + " params, built " + out.length);
        }
        return out;
    }
}
