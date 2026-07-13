package com.example.myvillage.cultivation.root;

import com.example.myvillage.cultivation.SpiritualRoot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiritualRootGeneratorTest {
    private static final UUID PLAYER = UUID.fromString("12345678-1234-5678-9abc-def012345678");

    @Test
    void identicalInputsAndReorderedCandidatesProduceTheSameRoot() {
        List<SpiritualRootGenerator.ElementCandidate> candidates = candidates(5);
        SpiritualRoot first = generate(987654321L, PLAYER, candidates);
        List<SpiritualRootGenerator.ElementCandidate> reversed = new ArrayList<>(candidates);
        java.util.Collections.reverse(reversed);

        assertEquals(first, generate(987654321L, PLAYER, candidates));
        assertEquals(first, generate(987654321L, PLAYER, reversed));
    }

    @Test
    void goldenVectorsPinExactAffinityOutputs() {
        assertEquals(
                "{myvillage:element_0=2014, myvillage:element_1=1241, myvillage:element_2=2159, myvillage:element_3=1364, myvillage:element_4=3222}",
                generate(987654321L, PLAYER, candidates(5)).affinitiesBasisPoints().toString());
        assertEquals(
                "{myvillage:earth=2269, myvillage:fire=1121, myvillage:lightning=3364, myvillage:wood=3246}",
                generate(
                        -445566778899L,
                        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        weightedCandidates()).affinitiesBasisPoints().toString());
    }

    @Test
    void deterministicFixturesExerciseAllRootCounts() {
        Set<Integer> counts = new HashSet<>();
        for (long seed = 0; seed < 20_000 && counts.size() < 5; seed++) {
            counts.add(generate(seed, PLAYER, candidates(5)).affinitiesBasisPoints().size());
        }
        assertEquals(Set.of(1, 2, 3, 4, 5), counts);
    }

    @Test
    void zeroWeightIsExcludedAndCandidateCountClamps() {
        ResourceLocation excluded = id("excluded");
        List<SpiritualRootGenerator.ElementCandidate> values = List.of(
                new SpiritualRootGenerator.ElementCandidate(excluded, 0),
                new SpiritualRootGenerator.ElementCandidate(id("only"), 1));
        SpiritualRoot root = generate(1L, PLAYER, values);

        assertEquals(Map.of(id("only"), 10_000), root.affinitiesBasisPoints());
        assertFalse(root.affinitiesBasisPoints().containsKey(excluded));
    }

    @Test
    void weightedSelectionNeverRepeatsAndAffinitiesArePositiveAndExact() {
        for (long seed = 0; seed < 500; seed++) {
            SpiritualRoot root = generate(seed, new UUID(seed, ~seed), weightedCandidates());
            assertEquals(
                    root.affinitiesBasisPoints().size(),
                    new HashSet<>(root.affinitiesBasisPoints().keySet()).size());
            assertEquals(10_000, root.affinitiesBasisPoints().values().stream()
                    .mapToInt(Integer::intValue)
                    .sum());
            assertTrue(root.affinitiesBasisPoints().values().stream().allMatch(value -> value > 0));
        }
    }

    @Test
    void emptyCandidatesFailWithoutProducingARoot() {
        assertEquals(Optional.empty(), SpiritualRootGenerator.generate(0, PLAYER, List.of()));
    }

    @Test
    void duplicateIdsAreRejectedAndMaximumWeightsRemainSafe() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritualRootGenerator.generate(
                        0,
                        PLAYER,
                        List.of(
                                new SpiritualRootGenerator.ElementCandidate(id("same"), 1),
                                new SpiritualRootGenerator.ElementCandidate(id("same"), 2))));

        SpiritualRoot root = generate(
                Long.MAX_VALUE,
                PLAYER,
                List.of(
                        new SpiritualRootGenerator.ElementCandidate(id("one"), 1_000_000),
                        new SpiritualRootGenerator.ElementCandidate(id("two"), 1_000_000),
                        new SpiritualRootGenerator.ElementCandidate(id("three"), 1_000_000),
                        new SpiritualRootGenerator.ElementCandidate(id("four"), 1_000_000),
                        new SpiritualRootGenerator.ElementCandidate(id("five"), 1_000_000)));
        assertEquals(10_000, root.affinitiesBasisPoints().values().stream()
                .mapToInt(Integer::intValue)
                .sum());
    }

    @Test
    void largestRemainderTiesUseFullIdOrder() {
        LinkedHashMap<ResourceLocation, Long> weights = new LinkedHashMap<>();
        weights.put(id("charlie"), 1L);
        weights.put(id("alpha"), 1L);
        weights.put(id("bravo"), 1L);

        SpiritualRoot root = SpiritualRootGenerator.allocateAffinities(weights);

        assertEquals(3_334, root.affinitiesBasisPoints().get(id("alpha")));
        assertEquals(3_333, root.affinitiesBasisPoints().get(id("bravo")));
        assertEquals(3_333, root.affinitiesBasisPoints().get(id("charlie")));
    }

    private static SpiritualRoot generate(
            long seed,
            UUID uuid,
            List<SpiritualRootGenerator.ElementCandidate> candidates) {
        return SpiritualRootGenerator.generate(seed, uuid, candidates).orElseThrow();
    }

    private static List<SpiritualRootGenerator.ElementCandidate> candidates(int count) {
        List<SpiritualRootGenerator.ElementCandidate> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add(new SpiritualRootGenerator.ElementCandidate(id("element_" + index), 1));
        }
        return values;
    }

    private static List<SpiritualRootGenerator.ElementCandidate> weightedCandidates() {
        return List.of(
                new SpiritualRootGenerator.ElementCandidate(id("metal"), 1),
                new SpiritualRootGenerator.ElementCandidate(id("wood"), 2),
                new SpiritualRootGenerator.ElementCandidate(id("water"), 3),
                new SpiritualRootGenerator.ElementCandidate(id("fire"), 5),
                new SpiritualRootGenerator.ElementCandidate(id("earth"), 8),
                new SpiritualRootGenerator.ElementCandidate(id("lightning"), 13));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
