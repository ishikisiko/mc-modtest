package com.example.myvillage.gathering;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.meditation.*;
import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GatheringTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    private BasicBreathingSettlement.Plan base(long progress, long cap, MeditationMode mode, boolean paid) {
        var profile = CultivationProfile.defaultProfile().learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withSpiritualAffinity(20).withCultivationProgress(progress);
        return BasicBreathingSettlement.plan(profile,cap,mode,
                new BasicBreathingSettlement.Accrual(0,BasicBreathingSettlement.Remainders.ZERO),paid);
    }
    @Test void normalAndSpiritReceiveOneProgressOnlyBonus() {
        var normal=base(100,1000,MeditationMode.NORMAL,false);
        var spirit=base(100,1000,MeditationMode.SPIRIT,true);
        assertEquals(25,GatheringMath.boost(normal,1000,2500).progressApplied());
        assertEquals(62,GatheringMath.boost(spirit,1000,2500).progressApplied());
        assertTrue(GatheringMath.boost(spirit,1000,2500).consumeSpiritStones());
        assertEquals(normal.replacement().stability(),GatheringMath.boost(normal,1000,2500).replacement().stability());
        assertEquals(normal.replacement().learnedTechniques(),GatheringMath.boost(normal,1000,2500).replacement().learnedTechniques());
    }
    @Test void fullStageAndAlreadyClippedBatchHaveNoPaidBonus() {
        var clipped=base(990,1000,MeditationMode.NORMAL,false);
        assertEquals(clipped,GatheringMath.boost(clipped,1000,2500));
        var capped=base(1000,1000,MeditationMode.SPIRIT,true);
        var next=GatheringMath.boost(capped,1000,2500);
        assertEquals(capped,next);assertEquals(20,next.stabilityApplied());assertFalse(next.consumeSpiritStones());
    }
    @Test void nearCapPaysOnlyWhenAtLeastOneExtraPointFits() {
        var almost=base(977,1000,MeditationMode.NORMAL,false);
        assertEquals(1000,GatheringMath.boost(almost,1000,2500).replacement().cultivationProgress());
        assertEquals(23,GatheringMath.boost(almost,1000,2500).progressApplied());
    }
    @Test void downgradePreservesOriginalSpiritPaymentDecision() {
        var original=base(100,1000,MeditationMode.SPIRIT,false);
        var boosted=GatheringMath.boost(original,1000,2500);
        assertTrue(boosted.downgradeToNormal());assertFalse(boosted.consumeSpiritStones());assertEquals(25,boosted.progressApplied());
    }
    @Test void bonusRoundsDownAndCannotOverflow() {
        assertEquals(0,GatheringMath.bonus(3,1000,2500));
        assertEquals(2,GatheringMath.bonus(10,1000,2500));
        assertEquals(Long.MAX_VALUE,GatheringMath.bonus(Long.MAX_VALUE,Long.MAX_VALUE,10000));
        assertThrows(IllegalArgumentException.class,()->GatheringMath.bonus(1,1,10001));
    }
    @Test void oneStoneFundsExactlyTwentyPlayerBatchesAndThenRefuses() {
        var state=new GatheringMath.Debit(1,0);
        for(int i=0;i<20;i++){
            state=GatheringMath.debit(state.stones(),state.charges(),20);
            assertNotNull(state);assertEquals(0,state.stones());assertEquals(19-i,state.charges());
        }
        assertNull(GatheringMath.debit(state.stones(),state.charges(),20));
    }
    @Test void concurrentPlayersConsumeDistinctChargesWithoutNegativeBalance() {
        var first=GatheringMath.debit(0,2,20);
        var second=GatheringMath.debit(first.stones(),first.charges(),20);
        assertNotNull(second);assertEquals(0,second.charges());assertNull(GatheringMath.debit(0,second.charges(),20));
    }
    @Test void savedCreditIsNotRevaluedWhenFutureFuelRateChanges() {
        assertEquals(new GatheringMath.Debit(0,18),GatheringMath.debit(0,19,10));
    }
    @Test void originalEnergySnapshotIsUnchangedByPlanning() {
        var before=new GatheringMath.Debit(64,0);
        var planned=GatheringMath.debit(before.stones(),before.charges(),20);
        assertEquals(new GatheringMath.Debit(63,19),planned);assertEquals(new GatheringMath.Debit(64,0),before);
    }
    private static final class World implements GatheringPattern.Access {
        final Set<BlockPos> cores=new HashSet<>(Set.of(BlockPos.ZERO));
        final Set<BlockPos> nodes=new HashSet<>();
        World() { this(GatheringTier.LOW); }
        World(GatheringTier tier) { nodes.addAll(GatheringPattern.nodes(tier)); }
        final Set<BlockPos> unloaded=new HashSet<>(),holes=new HashSet<>(),blocked=new HashSet<>();
        public boolean loaded(BlockPos pos){return !unloaded.contains(pos);}
        private void assertLoaded(BlockPos pos){assertFalse(unloaded.contains(pos),"unloaded position read: "+pos);}
        public boolean core(BlockPos pos){assertLoaded(pos);return cores.contains(pos);}
        public boolean node(BlockPos pos){assertLoaded(pos);return nodes.contains(pos);}
        public boolean supported(BlockPos pos){assertLoaded(pos);return !holes.contains(pos);}
        public boolean clear(BlockPos pos){assertLoaded(pos);return !blocked.contains(pos);}
    }
    @Test void fourNodesAndCompleteFloorFormArray() {assertTrue(GatheringPattern.check(new World(),BlockPos.ZERO).formed());}
    @Test void eachMissingDirectionReportsItsRealCoordinate() {
        for(int i=0;i<4;i++){
            var world=new World();var pos=GatheringPattern.nodes().get(i);world.nodes.remove(pos);
            var result=GatheringPattern.check(world,BlockPos.ZERO);
            assertEquals(i+1,result.issue());assertEquals(pos,result.offset());
        }
    }
    @Test void brokenFoundationAndBlockedMeditationAreaRefuse() {
        var world=new World();world.holes.add(new BlockPos(-2,0,1));
        assertEquals(5,GatheringPattern.check(world,BlockPos.ZERO).issue());
        world.holes.clear();world.blocked.add(new BlockPos(1,2,0));
        assertEquals(6,GatheringPattern.check(world,BlockPos.ZERO).issue());
    }
    @Test void unknownChunkNeverGetsReadOrSilentlyAccepted() {
        var world=new World();world.unloaded.add(GatheringPattern.nodes().getFirst());
        assertEquals(7,GatheringPattern.check(world,BlockPos.ZERO).issue());
        world.unloaded.clear();world.unloaded.add(new BlockPos(0,0,-6));
        assertEquals(7,GatheringPattern.check(world,BlockPos.ZERO).issue());
    }
    @Test void sharedNodeIsRejectedEvenIfCompetingArrayIsIncomplete() {
        var world=new World();world.cores.add(new BlockPos(0,0,6));
        assertEquals(8,GatheringPattern.check(world,BlockPos.ZERO).issue());
    }
    @Test void displacedOrMissingCoreIsNotAValidStructure() {
        var world=new World();world.cores.clear();assertEquals(9,GatheringPattern.check(world,BlockPos.ZERO).issue());
    }
    @Test void largerTiersValidateTheirOwnOuterFloorAndInnerClearance() {
        for (var tier : List.of(GatheringTier.MEDIUM, GatheringTier.HIGH)) {
            var world = new World(tier); var geometry = GatheringRules.get().tier(tier);
            assertTrue(GatheringPattern.check(world, BlockPos.ZERO, tier).formed());
            var edge = new BlockPos(geometry.radius(), 0, geometry.radius()); world.holes.add(edge);
            assertEquals(5, GatheringPattern.check(world, BlockPos.ZERO, tier).issue()); world.holes.clear();
            var inner = new BlockPos(geometry.innerRadius(), 2, geometry.innerRadius()); world.blocked.add(inner);
            assertEquals(6, GatheringPattern.check(world, BlockPos.ZERO, tier).issue()); world.blocked.clear();
            world.unloaded.add(inner);
            assertEquals(7, GatheringPattern.check(world, BlockPos.ZERO, tier).issue());
        }
    }
    @Test void lowGradeNodePositionsCannotFormHigherTiers() {
        assertFalse(GatheringPattern.check(new World(), BlockPos.ZERO, GatheringTier.MEDIUM).formed());
        assertFalse(GatheringPattern.check(new World(), BlockPos.ZERO, GatheringTier.HIGH).formed());
    }
    @Test void configRejectsFreeOrUnboundedEnergyAndUnexpectedFields() throws Exception {
        try(var stream=getClass().getResourceAsStream("/data/myvillage/gathering/rules.json")) {
            var root=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(20,GatheringRules.parse(root).chargesPerStone());
            var zero=root.deepCopy();zero.getAsJsonObject("fuel_charges").addProperty("low_grade",0);
            assertThrows(IllegalArgumentException.class,()->GatheringRules.parse(zero));
            var wide=root.deepCopy();wide.getAsJsonObject("tiers").getAsJsonObject("high").addProperty("radius",100);
            assertThrows(IllegalArgumentException.class,()->GatheringRules.parse(wide));
            var unknown=root.deepCopy();unknown.addProperty("free_energy",true);
            assertThrows(IllegalArgumentException.class,()->GatheringRules.parse(unknown));
        }
    }
}
