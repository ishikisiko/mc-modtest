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

    /**
     * Returns {surname, given}; registers the full name as used. Surnames are listed common-first
     * and drawn with weight {@code 1 / (1 + index / surname_rank_scale)}; a given name whose first
     * character repeats the surname's last (云云起) and the rules' banned prefixes (朱德…) are skipped.
     */
    public static String[] personName(SimContext ctx, SimRng rng, String gender) {
        ContentTables.Names names = ctx.data.names();
        List<String> own = gender.equals("f") ? names.givenFemale() : names.givenMale();
        List<String> neutral = names.givenNeutral();
        double[] surnameWeights = surnameWeights(ctx);
        String surname = "";
        String given = "";
        for (int attempt = 0; attempt < ATTEMPTS * 2; attempt++) {
            surname = names.surnames().get(rng.weighted(surnameWeights));
            int pick = rng.nextInt(own.size() + neutral.size());
            given = pick < own.size() ? own.get(pick) : neutral.get(pick - own.size());
            if (acceptable(ctx, surname, given) && (!ctx.usedNames().contains(surname + given) || attempt >= ATTEMPTS)) {
                break;
            }
        }
        ctx.usedNames().add(surname + given);
        return new String[] {surname, given};
    }

    static boolean acceptable(SimContext ctx, String surname, String given) {
        if (given.isEmpty() || surname.isEmpty()
                || given.codePointAt(0) == surname.codePointBefore(surname.length())) {
            return false;
        }
        String full = surname + given;
        for (String banned : ctx.rules.naming().bannedNamePrefixes()) {
            if (full.startsWith(banned)) {
                return false;
            }
        }
        return true;
    }

    private static double[] surnameWeights(SimContext ctx) {
        List<String> surnames = ctx.data.names().surnames();
        double scale = ctx.rules.naming().surnameRankScale();
        double[] w = new double[surnames.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = 1.0 / (1.0 + i / scale);
        }
        return w;
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
