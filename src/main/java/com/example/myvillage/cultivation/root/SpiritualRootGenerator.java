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
    public static final int ALGORITHM_VERSION = 1;
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
        int selectedCount = Math.min(rollElementCount(random.nextLong(100)), eligible.size());
        List<ElementCandidate> selected = selectWithoutReplacement(eligible, selectedCount, random);
        selected.sort(Comparator.comparing(candidate -> candidate.id().toString()));

        LinkedHashMap<ResourceLocation, Long> apportionmentWeights = new LinkedHashMap<>();
        for (ElementCandidate candidate : selected) {
            apportionmentWeights.put(candidate.id(), random.nextLong(1_000_000) + 1);
        }
        return Optional.of(allocateAffinities(apportionmentWeights));
    }

    static SpiritualRoot allocateAffinities(Map<ResourceLocation, Long> rawWeights) {
        Objects.requireNonNull(rawWeights, "rawWeights");
        if (rawWeights.isEmpty() || rawWeights.size() > 5) {
            throw new IllegalArgumentException("Affinity allocation requires one through five selected elements");
        }

        List<Map.Entry<ResourceLocation, Long>> weights = rawWeights.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(ID_ORDER))
                .toList();
        long totalWeight = 0;
        for (Map.Entry<ResourceLocation, Long> entry : weights) {
            Objects.requireNonNull(entry.getKey(), "selected element id");
            long weight = Objects.requireNonNull(entry.getValue(), "apportionment weight");
            if (weight <= 0) {
                throw new IllegalArgumentException("Apportionment weights must be positive");
            }
            totalWeight = Math.addExact(totalWeight, weight);
        }

        if (weights.size() == 1) {
            return new SpiritualRoot(Map.of(weights.getFirst().getKey(), SpiritualRoot.TOTAL_BASIS_POINTS));
        }

        int baseTotal = Math.multiplyExact(weights.size(), 1_000);
        int remaining = SpiritualRoot.TOTAL_BASIS_POINTS - baseTotal;
        LinkedHashMap<ResourceLocation, Integer> affinities = new LinkedHashMap<>();
        List<Remainder> remainders = new ArrayList<>();
        int assigned = baseTotal;
        for (Map.Entry<ResourceLocation, Long> entry : weights) {
            long product = Math.multiplyExact((long) remaining, entry.getValue());
            int floorShare = Math.toIntExact(product / totalWeight);
            affinities.put(entry.getKey(), 1_000 + floorShare);
            assigned = Math.addExact(assigned, floorShare);
            remainders.add(new Remainder(entry.getKey(), product % totalWeight));
        }

        int residue = SpiritualRoot.TOTAL_BASIS_POINTS - assigned;
        remainders.sort(Comparator.comparingLong(Remainder::value)
                .reversed()
                .thenComparing(remainder -> remainder.id().toString()));
        if (residue < 0 || residue > remainders.size()) {
            throw new IllegalStateException("Invalid largest-remainder residue " + residue);
        }
        for (int index = 0; index < residue; index++) {
            ResourceLocation id = remainders.get(index).id();
            affinities.put(id, affinities.get(id) + 1);
        }

        SpiritualRoot root = new SpiritualRoot(affinities);
        if (root.affinitiesBasisPoints().values().stream().anyMatch(value -> value <= 0)) {
            throw new IllegalStateException("Generated affinities must all be positive");
        }
        return root;
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
        if (roll < 10) {
            return 1;
        }
        if (roll < 35) {
            return 2;
        }
        if (roll < 70) {
            return 3;
        }
        if (roll < 90) {
            return 4;
        }
        return 5;
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
