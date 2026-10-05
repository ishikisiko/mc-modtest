package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;

/**
 * Spiritual roots: five-element basis points summing to 10000 (same semantics as
 * {@code cultivation/SpiritualRoot}). The grade counts the elements at or above the threshold:
 * fewer, purer elements is better (天灵根 = one element).
 */
public final class Roots {
    public static final int TOTAL = 10_000;

    private Roots() {
    }

    public static int elementCount(Rules.Roots rules, int[] root) {
        int n = 0;
        for (int bp : root) {
            if (bp >= rules.elementThresholdBp()) {
                n++;
            }
        }
        return Math.max(1, n);
    }

    public static Rules.RootGrade grade(Rules.Roots rules, int[] root) {
        return rules.grades().get(elementCount(rules, root) - 1);
    }

    /** Index of the grade (0 best). */
    public static int gradeIndex(Rules.Roots rules, int[] root) {
        return elementCount(rules, root) - 1;
    }

    /** Draws a root: a grade by weight, then which elements are strong and how strong. */
    public static int[] draw(Rules.Roots rules, SimRng rng) {
        double[] weights = new double[rules.grades().size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = rules.grades().get(i).weight();
        }
        int k = rules.grades().get(rng.weighted(weights)).maxElements();
        return build(rules, rng, k);
    }

    /** Best of {@code n} draws (purer root first, then the strongest main element). */
    public static int[] drawBest(Rules.Roots rules, SimRng rng, int n) {
        int[] best = draw(rules, rng);
        for (int i = 1; i < n; i++) {
            int[] next = draw(rules, rng);
            if (better(rules, next, best)) {
                best = next;
            }
        }
        return best;
    }

    static boolean better(Rules.Roots rules, int[] a, int[] b) {
        int ga = elementCount(rules, a);
        int gb = elementCount(rules, b);
        if (ga != gb) {
            return ga < gb;
        }
        return max(a) > max(b);
    }

    private static int[] build(Rules.Roots rules, SimRng rng, int k) {
        int threshold = rules.elementThresholdBp();
        boolean[] main = new boolean[5];
        int chosen = 0;
        while (chosen < k) {
            int e = rng.nextInt(5);
            if (!main[e]) {
                main[e] = true;
                chosen++;
            }
        }
        int[] root = new int[5];
        int minor = 0;
        for (int e = 0; e < 5; e++) {
            if (!main[e]) {
                root[e] = rng.nextInt(Math.max(1, threshold / 3));
                minor += root[e];
            }
        }
        int rest = TOTAL - minor - k * threshold;
        int[] weight = new int[5];
        int weightSum = 0;
        for (int e = 0; e < 5; e++) {
            if (main[e]) {
                weight[e] = 1 + rng.nextInt(3);
                weightSum += weight[e];
            }
        }
        int given = 0;
        int last = -1;
        for (int e = 0; e < 5; e++) {
            if (main[e]) {
                int share = rest * weight[e] / weightSum;
                root[e] = threshold + share;
                given += share;
                last = e;
            }
        }
        root[last] += rest - given;
        return root;
    }

    public static int max(int[] root) {
        int m = 0;
        for (int bp : root) {
            m = Math.max(m, bp);
        }
        return m;
    }

    /** Whether the person's root is strong in {@code element} (an id from {@link Person#ELEMENTS}). */
    public static boolean strongIn(Rules.Roots rules, int[] root, String element) {
        int index = Person.ELEMENTS.indexOf(element);
        return index >= 0 && root[index] >= rules.elementThresholdBp();
    }
}
