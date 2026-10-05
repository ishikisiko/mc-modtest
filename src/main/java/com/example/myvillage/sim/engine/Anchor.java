package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Tombstone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the params of a chronicle line. A person is anchored on first mention by a who block of
 * {@link TextKeys#WHO} params (sect name or "", rank key, name), so templates read
 * 青云宫内门弟子谢湄 or 散修柴恒. Ages are plain digits; the reader's language decides how to show them.
 */
public final class Anchor {
    private final SimContext ctx;
    private final List<String> params = new ArrayList<>();

    private Anchor(SimContext ctx) {
        this.ctx = ctx;
    }

    public static Anchor of(SimContext ctx) {
        return new Anchor(ctx);
    }

    /** A living person as they stand now. */
    public Anchor who(Person p) {
        return who(p, p.rank);
    }

    /** A living person shown with a given rank (e.g. the rank held before a promotion). */
    public Anchor who(Person p, String rank) {
        boolean member = p.sectId >= 0 && !rank.equals("rogue");
        params.add(member ? ctx.sectName(p.sectId) : "");
        params.add(TextKeys.rank(member ? rank : "rogue"));
        params.add(p.name());
        return this;
    }

    /** A dead person as they stood at death. */
    public Anchor who(Tombstone t) {
        boolean member = t.sectId >= 0 && !t.rank.equals("rogue");
        params.add(member ? ctx.sectName(t.sectId) : "");
        params.add(TextKeys.rank(member ? t.rank : "rogue"));
        params.add(t.name);
        return this;
    }

    /** Living or dead by id. */
    public Anchor who(int personId) {
        Person p = ctx.state.persons.get(personId);
        if (p != null) {
            return who(p);
        }
        Tombstone t = ctx.state.tombstones.get(personId);
        if (t != null) {
            return who(t);
        }
        params.add("");
        params.add(TextKeys.rank("rogue"));
        params.add("");
        return this;
    }

    public Anchor age(Person p) {
        params.add(String.valueOf((long) Math.floor(ctx.ageYears(p))));
        return this;
    }

    public Anchor add(String... values) {
        Collections.addAll(params, values);
        return this;
    }

    public Anchor region(String regionId) {
        params.add(ctx.regionName(regionId));
        return this;
    }

    public String[] build() {
        return params.toArray(new String[0]);
    }
}
