package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import java.util.List;

/**
 * Picks person, sect and Daoist names from {@code names.json}, unique across history where possible
 * (sects also avoid sharing a prefix while there are fresh ones to try).
 */
public final class Naming {
    private static final int ATTEMPTS = 24;

    private Naming() {
    }

    /** Returns {surname, given}; registers the full name as used. */
    public static String[] personName(SimContext ctx, SimRng rng, String gender) {
        ContentTables.Names names = ctx.data.names();
        List<String> own = gender.equals("f") ? names.givenFemale() : names.givenMale();
        List<String> neutral = names.givenNeutral();
        String surname = "";
        String given = "";
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            surname = names.surnames().get(rng.nextInt(names.surnames().size()));
            int pick = rng.nextInt(own.size() + neutral.size());
            given = pick < own.size() ? own.get(pick) : neutral.get(pick - own.size());
            if (!ctx.usedNames().contains(surname + given)) {
                break;
            }
        }
        ctx.usedNames().add(surname + given);
        return new String[] {surname, given};
    }

    public static String sectName(SimContext ctx, SimRng rng) {
        ContentTables.Names names = ctx.data.names();
        double[] weights = new double[names.sectSuffixes().size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = names.sectSuffixes().get(i).weight();
        }
        String name = "";
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String prefix = names.sectPrefixes().get(rng.nextInt(names.sectPrefixes().size()));
            name = prefix + names.sectSuffixes().get(rng.weighted(weights)).text();
            boolean prefixTaken = false;
            for (var sect : ctx.state.sects.values()) {
                prefixTaken |= sect.name.startsWith(prefix);
            }
            if (!ctx.usedSectNames().contains(name) && (!prefixTaken || attempt >= ATTEMPTS / 2)) {
                break;
            }
        }
        ctx.usedSectNames().add(name);
        return name;
    }

    public static String daoName(SimContext ctx, SimRng rng) {
        List<String> titles = ctx.data.names().daoTitles();
        String name = "";
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            name = titles.get(rng.nextInt(titles.size()));
            if (!ctx.usedDaoNames().contains(name)) {
                break;
            }
        }
        ctx.usedDaoNames().add(name);
        return name;
    }
}
