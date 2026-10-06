package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.runtime.net.ScriptureHallPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The scripture hall's decisions without a server (pure, tested): which rows a member sees
 * ({@link #entries}) and why a player gets no list at a shelf ({@link #refusal}). {@link ScriptureHall}
 * feeds them from the ledger, the technique registry and the rules.
 */
public final class ScriptureHallList {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptureHallList.class);

    public static final String OK = "ok";
    public static final String NOT_MEMBER = "not_member";
    public static final String MEMBER_ELSEWHERE = "member_elsewhere";
    public static final String NOT_BORROWABLE = "not_borrowable";
    public static final String ALREADY_BORROWED = "already_borrowed";
    public static final String NO_MANUAL = "no_manual";
    public static final String INACTIVE = "inactive";
    public static final String TOO_FAR = "too_far";
    public static final String UNOWNED = "unowned";
    /** Every reason a borrow can be refused with (each has a {@code message.myvillage.world.scripture.refused.*} line). */
    public static final List<String> REFUSALS = List.of(NOT_MEMBER, MEMBER_ELSEWHERE, NOT_BORROWABLE,
            ALREADY_BORROWED, NO_MANUAL, INACTIVE, TOO_FAR, UNOWNED);

    private ScriptureHallList() {
    }

    /** What the registry says about one technique: its name, grade and category word. */
    public record Def(Component name, int grade, String category) {
        public Def {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(category, "category");
        }
    }

    /**
     * The hall's rows for {@code borrowable} (ledger ids, in order): each id the {@code lookup}
     * knows becomes an entry with whether it is {@code borrowed} and its cost by grade
     * ({@code cost} keyed "1".."4"; missing or negative is 0). Ids the registry does not know, or
     * with a grade outside 0..{@value ScriptureHallPayload#MAX_GRADE}, are left out with a warning;
     * at most {@value ScriptureHallPayload#MAX_ENTRIES} rows.
     */
    public static List<ScriptureHallPayload.Entry> entries(List<String> borrowable,
                                                           Function<String, Optional<Def>> lookup,
                                                           Predicate<String> borrowed,
                                                           Map<String, Integer> cost) {
        List<ScriptureHallPayload.Entry> out = new ArrayList<>();
        for (String id : borrowable) {
            if (out.size() == ScriptureHallPayload.MAX_ENTRIES) {
                LOGGER.warn("ScriptureHall: more than {} borrowable techniques; the rest are not listed",
                        ScriptureHallPayload.MAX_ENTRIES);
                break;
            }
            if (id == null || id.isEmpty() || id.length() > ScriptureHallPayload.MAX_TECHNIQUE_ID) {
                LOGGER.warn("ScriptureHall: borrowable id \"{}\" cannot be listed", id);
                continue;
            }
            Optional<Def> def = lookup.apply(id);
            if (def.isEmpty()) {
                LOGGER.warn("ScriptureHall: technique {} is borrowable in the ledger but not registered; skipped", id);
                continue;
            }
            int grade = def.get().grade();
            if (grade < 0 || grade > ScriptureHallPayload.MAX_GRADE) {
                LOGGER.warn("ScriptureHall: technique {} has grade {}; skipped", id, grade);
                continue;
            }
            Integer price = cost.get(String.valueOf(grade));
            out.add(new ScriptureHallPayload.Entry(id, def.get().name(), grade,
                    ScriptureHallPayload.clip(def.get().category(), ScriptureHallPayload.MAX_WORD),
                    borrowed.test(id), price == null ? 0 : Math.max(0, price)));
        }
        return out;
    }

    /**
     * Why the player gets no list at a shelf of sect {@code shelfSectId}: {@link #INACTIVE} when
     * the sect is not active, {@link #NOT_MEMBER} without a record or a sect,
     * {@link #MEMBER_ELSEWHERE} for a member of another sect; empty for a member of this sect.
     */
    public static Optional<String> refusal(Optional<PlayerMemberView> me, int shelfSectId, boolean sectActive) {
        if (!sectActive) {
            return Optional.of(INACTIVE);
        }
        if (me.isEmpty() || !me.get().inSect()) {
            return Optional.of(NOT_MEMBER);
        }
        if (me.get().sectId() != shelfSectId) {
            return Optional.of(MEMBER_ELSEWHERE);
        }
        return Optional.empty();
    }
}
