package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.sim.PersonView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;

/**
 * The pure decisions behind the avatars: who of a sect's members at the gate is shown, and where
 * each one stands. No world access, so it is unit-tested.
 */
final class AvatarPlanner {
    /** Least Chebyshev distance between two avatars on the same terrace. */
    static final int MIN_SPACING = 2;

    private AvatarPlanner() {
    }

    /**
     * At most {@code max} of {@code atSect}: the master first, then the elders, then everyone else;
     * within each group the higher realm and stage first ({@code realmOrder} lists realms weakest
     * first), then the lower id.
     */
    static List<PersonView> select(List<PersonView> atSect, List<String> realmOrder, int max) {
        List<PersonView> sorted = new ArrayList<>(atSect);
        sorted.sort(Comparator.<PersonView>comparingInt(p -> rankOrder(p.rank()))
                .thenComparing(Comparator.<PersonView>comparingInt(p -> realmOrder.indexOf(p.realmId())).reversed())
                .thenComparing(Comparator.comparingInt(PersonView::stage).reversed())
                .thenComparingInt(PersonView::id));
        return List.copyOf(sorted.subList(0, Math.max(0, Math.min(max, sorted.size()))));
    }

    private static int rankOrder(String rank) {
        return switch (rank) {
            case "sect_master" -> 0;
            case "elder" -> 1;
            default -> 2;
        };
    }

    /**
     * A cell for {@code personId}: the lowest terrace (smallest y) that has a cell at least
     * {@link #MIN_SPACING} from every occupied cell of that terrace; within the terrace the probe
     * starts at a cell chosen by hashing the person id, so a person tends to stand at the same
     * place each time. Returns null when every terrace is full.
     */
    static BlockPos pickCell(int personId, List<BlockPos> cells, Collection<BlockPos> occupied) {
        Map<Integer, List<BlockPos>> byTerrace = new TreeMap<>();
        for (BlockPos c : cells) {
            byTerrace.computeIfAbsent(c.getY(), y -> new ArrayList<>()).add(c);
        }
        long h = mix(personId);
        for (List<BlockPos> terrace : byTerrace.values()) {
            int n = terrace.size();
            int start = (int) Math.floorMod(h, (long) n);
            int step = coprimeStep(n, h);
            for (int k = 0; k < n; k++) {
                BlockPos candidate = terrace.get((int) ((start + (long) k * step) % n));
                if (spaced(candidate, occupied)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** A body yaw for the avatar, by hash so it is stable per person. */
    static float yaw(int personId) {
        return (float) Math.floorMod(mix(personId ^ 0x5bd1e995), 360L);
    }

    private static boolean spaced(BlockPos c, Collection<BlockPos> occupied) {
        for (BlockPos o : occupied) {
            if (o.getY() == c.getY()
                    && Math.max(Math.abs(o.getX() - c.getX()), Math.abs(o.getZ() - c.getZ())) < MIN_SPACING) {
                return false;
            }
        }
        return true;
    }

    /** A probe stride coprime with n that scatters consecutive probes over the terrace. */
    private static int coprimeStep(int n, long h) {
        if (n <= 2) {
            return 1;
        }
        int step = (int) (Math.floorMod(h >>> 17, (long) n - 1) + 1);
        while (gcd(step, n) != 1) {
            step = step % (n - 1) + 1;
        }
        return step;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
