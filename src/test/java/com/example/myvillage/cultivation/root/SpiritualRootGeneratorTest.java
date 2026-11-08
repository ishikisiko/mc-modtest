package com.example.myvillage.cultivation.root;

import com.example.myvillage.aptitude.AptitudeRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritualRootGeneratorTest {
    private List<SpiritualRootGenerator.ElementCandidate> candidates() {
        return AptitudeRules.get().elements().stream().map(id -> new SpiritualRootGenerator.ElementCandidate(ResourceLocation.parse("myvillage:" + id), 1)).toList();
    }
    @Test void generationIsStableAndCandidateOrderIndependent() {
        var identity = new UUID(17, 29);
        var first = SpiritualRootGenerator.generate(123, identity, candidates());
        assertEquals(first, SpiritualRootGenerator.generate(123, identity, candidates().reversed()));
    }
    @Test void emptyAndZeroWeightCataloguesHaveNoRoot() {
        assertTrue(SpiritualRootGenerator.generate(1, new UUID(0,1), List.of()).isEmpty());
        assertTrue(SpiritualRootGenerator.generate(1, new UUID(0,1), List.of(new SpiritualRootGenerator.ElementCandidate(ResourceLocation.parse("myvillage:fire"), 0))).isEmpty());
    }
    @Test void generatedPointsRespectAuthoredRangesAndDoNotHaveAFixedSum() {
        var sums = new java.util.HashSet<Long>();
        for (int i=0;i<100;i++) {
            var root = SpiritualRootGenerator.generate(42, new UUID(0,i), candidates()).orElseThrow();
            int[] range = AptitudeRules.get().rootRange(root.points().size());
            for (int value : root.points().values()) assertTrue(value >= range[0] && value <= range[1]);
            sums.add(root.points().values().stream().mapToLong(Integer::longValue).sum());
        }
        assertTrue(sums.size() > 10);
    }
    @Test void duplicateEligibleElementsAreRejected() {
        var entry=candidates().getFirst();
        assertThrows(IllegalArgumentException.class,()->SpiritualRootGenerator.generate(1,new UUID(0,1),List.of(entry,entry)));
    }
}
