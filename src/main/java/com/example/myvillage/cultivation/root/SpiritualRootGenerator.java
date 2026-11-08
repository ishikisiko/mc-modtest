package com.example.myvillage.cultivation.root;

import com.example.myvillage.cultivation.SpiritualRoot;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SpiritualRootGenerator {
    public static final int ALGORITHM_VERSION = 2;
    public static final long ROOT_AWAKENING_SALT = 0x4D5956494C4C4147L;

    private static final long SPLITMIX_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;
    private static final Comparator<ResourceLocation> ID_ORDER =
            Comparator.comparing(ResourceLocation::toString);

    private SpiritualRootGenerator() {
    }

    public static Optional<SpiritualRoot> generate(
            long overworldSeed,
            UUID playerUuid,
            List<ElementCandidate> candidates) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        List<ElementCandidate> eligible = canonicalEligibleCandidates(candidates);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        StableRandom random = new StableRandom(initialState(overworldSeed, playerUuid, eligible));
        int selectedCount = Math.min(rollElementCount(random.nextLong(java.util.Arrays.stream(com.example.myvillage.aptitude.AptitudeRules.get().rootCountWeights()).asLongStream().sum())), eligible.size());
        List<ElementCandidate> selected = selectWithoutReplacement(eligible, selectedCount, random);
        selected.sort(Comparator.comparing(candidate -> candidate.id().toString()));

        int[] range = com.example.myvillage.aptitude.AptitudeRules.get().rootRange(selectedCount);
        LinkedHashMap<ResourceLocation, Integer> points = new LinkedHashMap<>();
        for (ElementCandidate candidate : selected) {
            points.put(candidate.id(), range[0] + (int) random.nextLong((long) range[1] - range[0] + 1));
        }
        return Optional.of(new SpiritualRoot(points));
    }

    private static List<ElementCandidate> canonicalEligibleCandidates(List<ElementCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        List<ElementCandidate> sorted = candidates.stream()
                .map(candidate -> Objects.requireNonNull(candidate, "candidate"))
                .filter(candidate -> candidate.awakeningWeight() > 0)
                .sorted(Comparator.comparing(candidate -> candidate.id().toString()))
                .toList();
        Set<ResourceLocation> ids = new HashSet<>();
        for (ElementCandidate candidate : sorted) {
            if (!ids.add(candidate.id())) {
                throw new IllegalArgumentException("Duplicate awakening candidate " + candidate.id());
            }
        }
        return new ArrayList<>(sorted);
    }

    private static List<ElementCandidate> selectWithoutReplacement(
            List<ElementCandidate> eligible,
            int count,
            StableRandom random) {
        List<ElementCandidate> remaining = new ArrayList<>(eligible);
        List<ElementCandidate> selected = new ArrayList<>(count);
        for (int selection = 0; selection < count; selection++) {
            long totalWeight = 0;
            for (ElementCandidate candidate : remaining) {
                totalWeight = Math.addExact(totalWeight, candidate.awakeningWeight());
            }
            long roll = random.nextLong(totalWeight);
            long cumulative = 0;
            int selectedIndex = -1;
            for (int index = 0; index < remaining.size(); index++) {
                cumulative = Math.addExact(cumulative, remaining.get(index).awakeningWeight());
                if (roll < cumulative) {
                    selectedIndex = index;
                    break;
                }
            }
            if (selectedIndex < 0) {
                throw new IllegalStateException("Weighted selection did not resolve a candidate");
            }
            selected.add(remaining.remove(selectedIndex));
        }
        return selected;
    }

    private static int rollElementCount(long roll) {
        int[] weights = com.example.myvillage.aptitude.AptitudeRules.get().rootCountWeights();
        long cumulative = 0;
        for (int i=0;i<weights.length;i++) {
            cumulative += weights[i];
            if (roll < cumulative) return i+1;
        }
        throw new IllegalArgumentException("Invalid root-count roll");
    }

    private static long initialState(
            long overworldSeed,
            UUID playerUuid,
            List<ElementCandidate> eligible) {
        long state = ROOT_AWAKENING_SALT;
        state ^= mix64(overworldSeed);
        state ^= Long.rotateLeft(mix64(playerUuid.getMostSignificantBits()), 17);
        state ^= Long.rotateLeft(mix64(playerUuid.getLeastSignificantBits()), 39);
        state ^= mix64(ALGORITHM_VERSION);
        state ^= mix64(candidateFingerprint(eligible));
        return mix64(state);
    }

    private static long candidateFingerprint(List<ElementCandidate> eligible) {
        long hash = FNV_OFFSET_BASIS;
        for (ElementCandidate candidate : eligible) {
            for (byte value : candidate.id().toString().getBytes(StandardCharsets.UTF_8)) {
                hash ^= Byte.toUnsignedInt(value);
                hash *= FNV_PRIME;
            }
            hash ^= 0xFF;
            hash *= FNV_PRIME;
            long weight = candidate.awakeningWeight();
            for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
                hash ^= (weight >>> shift) & 0xFFL;
                hash *= FNV_PRIME;
            }
        }
        return hash;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record ElementCandidate(ResourceLocation id, int awakeningWeight) {
        public ElementCandidate {
            Objects.requireNonNull(id, "id");
            if (awakeningWeight < 0 || awakeningWeight > 1_000_000) {
                throw new IllegalArgumentException(
                        "Awakening weight must be in 0..1000000, got " + awakeningWeight);
            }
        }
    }

    private record Remainder(ResourceLocation id, long value) {
    }

    private static final class StableRandom {
        private long state;

        private StableRandom(long state) {
            this.state = state;
        }

        private long nextLong() {
            state += SPLITMIX_GAMMA;
            return mix64(state);
        }

        private long nextLong(long bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("Bound must be positive, got " + bound);
            }
            long value = nextLong() >>> 1;
            long result = value % bound;
            while (value - result + (bound - 1) < 0) {
                value = nextLong() >>> 1;
                result = value % bound;
            }
            return result;
        }
    }
}
