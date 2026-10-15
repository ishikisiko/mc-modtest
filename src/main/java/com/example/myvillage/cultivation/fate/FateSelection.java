package com.example.myvillage.cultivation.fate;

import java.util.*;
import java.util.random.RandomGenerator;

/** Pure draft/confirmation rules. A lock preserves a candidate, never forces its selection. */
public final class FateSelection {
    private FateSelection() { }
    /** The stele resumes existing choices; opening it never rerolls an existing draft. */
    public static FateState initializeDraft(FateState current, Map<String, FateDefinition> pool,
            int budget, FateRules rules, RandomGenerator random) {
        if (current.confirmed() || !current.candidates().isEmpty()) return current;
        return transition(current, 0, current.revision(), 0, pool, budget, rules, random);
    }
    public static FateState transition(FateState current, int action, long revision, int value,
            Map<String, FateDefinition> pool, int budget, FateRules rules, RandomGenerator random) {
        if (current.confirmed()) throw new IllegalArgumentException("confirmed");
        if (current.revision() != revision) throw new IllegalArgumentException("stale");
        if (action == 0 && value == 0) return current.choice(roll(pool, current.locked(), budget, rules, random),
                current.locked(), List.of(), false, Math.addExact(current.rolls(), 1));
        if (action == 1 && (value == 255 || (value >= 0 && value < current.candidates().size()))) {
            String id = value == 255 ? "" : current.candidates().get(value);
            if (!id.isEmpty() && !pool.containsKey(id)) throw new IllegalArgumentException("unavailable");
            if (!id.isEmpty() && !current.locked().isEmpty() && !current.locked().equals(id))
                throw new IllegalArgumentException("locked");
            return current.choice(current.candidates(), id, List.of(), false, current.rolls());
        }
        if (action == 2) {
            List<String> selected;
            try { selected = mask(current.candidates(), value); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("count"); }
            String error = validate(selected, pool, budget, true);
            if (!error.isEmpty()) throw new IllegalArgumentException(error);
            return current.choice(current.candidates(), "", selected, true, current.rolls());
        }
        throw new IllegalArgumentException("intent");
    }
    public static String validate(List<String> ids, Map<String, FateDefinition> available, int budget, boolean complete) {
        if (budget < 3 || budget > 18) return "budget";
        if (ids.size() > 3 || (complete && ids.size() != 3) || new HashSet<>(ids).size() != ids.size()) return "count";
        if (ids.stream().anyMatch(id -> !available.containsKey(id))) return "unavailable";
        var definitions = ids.stream().map(available::get).toList();
        if (definitions.stream().mapToInt(FateDefinition::cost).sum() > budget) return "budget";
        if (definitions.stream().filter(t -> t.tier() >= 3).count() > 1) return "high";
        if (definitions.stream().map(FateDefinition::group).distinct().count() != definitions.size()) return "exclusive";
        return "";
    }
    public static List<String> mask(List<String> candidates, int mask) {
        if ((mask & ~63) != 0 || Integer.bitCount(mask) != 3 || candidates.size() != 6)
            throw new IllegalArgumentException("Invalid selection mask");
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < 6; i++) if ((mask & (1 << i)) != 0) selected.add(candidates.get(i));
        return List.copyOf(selected);
    }
    public static boolean hasTriple(List<String> pool, Map<String, FateDefinition> defs, int budget) {
        for (int a = 0; a < pool.size(); a++) for (int b = a + 1; b < pool.size(); b++)
            for (int c = b + 1; c < pool.size(); c++)
                if (validate(List.of(pool.get(a), pool.get(b), pool.get(c)), defs, budget, true).isEmpty()) return true;
        return false;
    }
    public static List<String> roll(Map<String, FateDefinition> defs, String lock, int budget,
            FateRules rules, RandomGenerator random) {
        if (defs.size() < 6 || budget < 3 || budget > 18 || (!lock.isEmpty() && !defs.containsKey(lock)))
            throw new IllegalArgumentException("Unavailable pool or lock");
        List<String> ids = new ArrayList<>(); if (!lock.isEmpty()) ids.add(lock);
        List<String> all = defs.keySet().stream().sorted().toList();
        while (ids.stream().filter(id -> defs.get(id).tier() == 0).count() < 2) {
            var pool = all.stream().filter(id -> defs.get(id).tier() == 0 && !ids.contains(id))
                    .filter(id -> ids.stream().noneMatch(v -> defs.get(v).group().equals(defs.get(id).group()))).toList();
            if (pool.isEmpty()) throw new IllegalArgumentException("Insufficient compatible ordinary fates");
            ids.add(pool.get(random.nextInt(pool.size())));
        }
        while (ids.size() < 6) {
            var pool = all.stream().filter(id -> !ids.contains(id)).toList();
            double[] counts = new double[6]; for (String id : pool) counts[defs.get(id).tier()]++;
            double total = 0; for (int i = 0; i < 6; i++) if (counts[i] > 0) total += rules.tierWeights().get(i);
            if (total <= 0) throw new IllegalArgumentException("No weighted candidates");
            double value = random.nextDouble() * total; String chosen = pool.getLast();
            for (String id : pool) {
                int tier = defs.get(id).tier(); value -= rules.tierWeights().get(tier) / counts[tier];
                if (value < 0) { chosen = id; break; }
            }
            ids.add(chosen);
        }
        while (!hasTriple(ids, defs, budget)) {
            var pool = all.stream().filter(id -> defs.get(id).tier() == 0 && !ids.contains(id))
                    .filter(id -> ids.stream().filter(v -> defs.get(v).tier() == 0)
                            .noneMatch(v -> defs.get(v).group().equals(defs.get(id).group()))).toList();
            int replace = -1;
            for (int i = ids.size() - 1; i >= 0; i--) if (!ids.get(i).equals(lock) && defs.get(ids.get(i)).tier() != 0) { replace = i; break; }
            if (replace < 0 || pool.isEmpty()) throw new IllegalArgumentException("No legal triple");
            ids.set(replace, pool.get(random.nextInt(pool.size())));
        }
        return List.copyOf(ids);
    }
}
