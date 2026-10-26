package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.sim.life.LifePlace;
import com.example.myvillage.sim.profession.PhysicalGardenWork;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Pure receipt tests; the runtime callback owns actual block checks and mutation. */
class WorldSimPhysicalGardenTest {
    private static final String SITE=WorldSimProfessionTest.SITE, LEAF=WorldSimProfessionTest.LEAF;
    private final WorldSimProfessionTest fixture=new WorldSimProfessionTest();

    private WorldSim newPlot(int seedStock) {
        var ctx=fixture.context();
        fixture.assign(ctx,1,"herb_child","garden");
        fixture.stock(ctx,LEAF,seedStock);
        var sim=fixture.facade(ctx);
        assertTrue(sim.bindPhysicalGarden(SITE,"garden",LEAF,5,64,0));
        assertFalse(sim.bindPhysicalGarden(SITE,"garden",LEAF,5,64,0));
        assertTrue(sim.professionSupply(SITE).orElseThrow().gardens().getFirst().physical());
        return sim;
    }

    private PhysicalGardenWork ready(WorldSim sim,long epoch,int age,long start) {
        sim.observePhysicalGarden(SITE,"garden",age);
        sim.advanceLife(start,WorldSimProfessionTest.DAY);
        var moving=sim.life(1).orElseThrow();
        assertEquals("moving",moving.phase());
        assertTrue(sim.checkpointLife(1,epoch,4,64,0) || moving.x()==4);
        assertTrue(sim.arriveLife(1,epoch,moving.revision()));
        sim.advanceLife(start+200,WorldSimProfessionTest.DAY);
        return sim.physicalGardenWork(1,epoch).orElseThrow();
    }

    @Test void completedClockNeedsReceiptAndFalseCallbackHasNoLedgerEffects() {
        var sim=newPlot(1);long epoch=sim.activateLife(1);sim.setLifeObserved(1,true);
        var plant=ready(sim,epoch,-1,20);
        assertEquals("plant",plant.activity());assertEquals(-1,plant.expectedAge());
        assertEquals(1,sim.professionSupply(SITE).orElseThrow().herbs().get(LEAF));
        assertEquals(0,sim.profession(1).orElseThrow().plantingExperience());
        sim.advanceLife(2000,WorldSimProfessionTest.DAY);
        assertEquals(1,sim.professionSupply(SITE).orElseThrow().herbs().get(LEAF),"time cannot plant a block");
        AtomicInteger calls=new AtomicInteger();
        assertFalse(sim.commitPhysicalGardenWork(plant,0,()->{calls.incrementAndGet();return false;}));
        assertEquals(1,calls.get());assertEquals(0,sim.profession(1).orElseThrow().plantingExperience());
        assertTrue(sim.commitPhysicalGardenWork(plant,0,()->{calls.incrementAndGet();return true;}));
        assertEquals(2,calls.get());assertEquals(0,sim.professionSupply(SITE).orElseThrow().herbs().getOrDefault(LEAF,0));
        assertEquals(sim.professionRules().plantingExperience(),sim.profession(1).orElseThrow().plantingExperience());
        assertFalse(sim.commitPhysicalGardenWork(plant,0,()->{calls.incrementAndGet();return true;}));
        assertEquals(2,calls.get(),"replayed receipt cannot touch world");
        assertEquals(-2,sim.professionSupply(SITE).orElseThrow().gardens().getFirst().observedAge());
    }

    @Test void realMaturityAndLootUnitsOwnHarvestAndSerialSurvivesReload() {
        var sim=newPlot(1);long epoch=sim.activateLife(1);sim.setLifeObserved(1,true);
        var plant=ready(sim,epoch,-1,20);assertTrue(sim.commitPhysicalGardenWork(plant,0,()->true));
        var harvest=ready(sim,epoch,2,240);assertEquals("harvest",harvest.activity());
        assertEquals(0,harvest.harvestSerial());
        AtomicInteger calls=new AtomicInteger();
        assertFalse(sim.commitPhysicalGardenWork(harvest,0,()->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get(),"invalid loot quantity must not invoke the world callback");
        assertTrue(sim.commitPhysicalGardenWork(harvest,3,()->{calls.incrementAndGet();return true;}));
        assertEquals(1,calls.get());assertEquals(3,sim.professionSupply(SITE).orElseThrow().herbs().get(LEAF));
        assertEquals(1,sim.professionSupply(SITE).orElseThrow().gardens().getFirst().harvestSerial());
        assertFalse(sim.commitPhysicalGardenWork(harvest,3,()->{calls.incrementAndGet();return true;}));
        assertEquals(1,calls.get());
        var loaded=fixture.reload(sim);var garden=loaded.professionSupply(SITE).orElseThrow().gardens().getFirst();
        assertEquals(1,garden.harvestSerial());assertTrue(garden.physical());assertEquals(-2,garden.observedAge());
        assertTrue(loaded.physicalGardenWork(1,epoch).isEmpty());
    }

    @Test void externalCropChangeReplansInsteadOfStallingAndStaleReceiptIsRejected() {
        var sim=newPlot(1);long epoch=sim.activateLife(1);sim.setLifeObserved(1,true);
        var plant=ready(sim,epoch,-1,20);
        assertTrue(sim.observePhysicalGarden(SITE,"garden",1));
        assertEquals("waiting",sim.life(1).orElseThrow().phase());
        assertTrue(sim.life(1).orElseThrow().revision()>plant.revision());
        assertTrue(sim.physicalGardenWork(1,epoch).isEmpty());
        AtomicInteger calls=new AtomicInteger();
        assertFalse(sim.commitPhysicalGardenWork(plant,0,()->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get());assertEquals(1,sim.professionSupply(SITE).orElseThrow().herbs().get(LEAF));
        sim.observePhysicalGarden(SITE,"garden",-2);
        assertTrue(sim.physicalGardenWork(1,epoch).isEmpty());
    }

    @Test void externalHarvestRevokesReadyReceiptWithoutGrantingLoot() {
        var sim=newPlot(1);long epoch=sim.activateLife(1);sim.setLifeObserved(1,true);
        var harvest=ready(sim,epoch,2,20);
        assertEquals("harvest",harvest.activity());
        assertTrue(sim.observePhysicalGarden(SITE,"garden",-1));
        AtomicInteger calls=new AtomicInteger();
        assertFalse(sim.commitPhysicalGardenWork(harvest,3,()->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get());
        assertEquals(1,sim.professionSupply(SITE).orElseThrow().herbs().get(LEAF));
        assertEquals(0,sim.profession(1).orElseThrow().plantingExperience());
        assertEquals(0,sim.professionSupply(SITE).orElseThrow().gardens().getFirst().harvestSerial());
    }

    @Test void disabledBodyAndDuplicateCropBindingFailClosed() {
        var sim=newPlot(1);long epoch=sim.activateLife(1);sim.setLifeObserved(1,true);
        var plant=ready(sim,epoch,-1,20);
        assertTrue(sim.addLifePlace(SITE,LifePlace.free("garden2","garden",6,64,0)));
        assertThrows(IllegalArgumentException.class,()->sim.bindPhysicalGarden(SITE,"garden2",LEAF,5,64,0));
        assertTrue(sim.releaseLife(1,epoch,4,64,0));
        AtomicInteger calls=new AtomicInteger();
        assertFalse(sim.commitPhysicalGardenWork(plant,0,()->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get());
        long replacement=sim.activateLife(1);assertTrue(replacement>epoch);
        assertFalse(sim.commitPhysicalGardenWork(plant,0,()->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get(),"old epoch cannot mutate the crop after reactivation");
    }

    @Test void activeAbstractBatchCannotBeReboundAsExistingWorldCrop() {
        var ctx=fixture.context();fixture.prepareGarden(ctx);
        var garden=ctx.state.life.gardens.get(SITE+"|garden");
        garden.plantedAt=10;garden.matureAt=2410;
        var sim=fixture.facade(ctx);
        assertThrows(IllegalArgumentException.class,()->sim.bindPhysicalGarden(SITE,"garden",LEAF,5,64,0));
        garden.plantedAt=-1;garden.matureAt=-1;
        assertTrue(fixture.facade(ctx).bindPhysicalGarden(SITE,"garden",LEAF,5,64,0));
    }

    @Test void twoReadyWorkersShareOneObservedPlotAndOnlyOneCanConsumeIt() {
        var ctx=fixture.context();
        for(int id=1;id<=2;id++) {
            var r=fixture.assign(ctx,id,"herb_child","garden");
            r.phase="working";r.activity="harvest";r.targetId="garden";
            r.revision=1;r.progressTicks=200;r.requiredTicks=200;
        }
        var sim=fixture.facade(ctx);
        sim.bindPhysicalGarden(SITE,"garden",LEAF,5,64,0);
        long firstEpoch=sim.activateLife(1),secondEpoch=sim.activateLife(2);
        assertTrue(sim.physicalGardenWork(1,firstEpoch).isEmpty(),"no body observation yet");
        sim.setLifeObserved(1,true);sim.setLifeObserved(2,true);
        sim.observePhysicalGarden(SITE,"garden",2);
        var first=sim.physicalGardenWork(1,firstEpoch).orElseThrow();
        var second=sim.physicalGardenWork(2,secondEpoch).orElseThrow();
        AtomicInteger calls=new AtomicInteger();
        assertTrue(sim.commitPhysicalGardenWork(first,2,()->{calls.incrementAndGet();return true;}));
        assertFalse(sim.commitPhysicalGardenWork(second,2,()->{calls.incrementAndGet();return true;}));
        assertEquals(1,calls.get());
        assertEquals(2,sim.professionSupply(SITE).orElseThrow().herbs().get(LEAF));
        assertEquals(1,sim.professionSupply(SITE).orElseThrow().gardens().getFirst().harvestSerial());
    }

    @Test void insufficientWarehouseSpaceRejectsLootBeforeWorldMutation() {
        var ctx=fixture.context();var r=fixture.assign(ctx,1,"herb_child","garden");
        r.phase="working";r.activity="harvest";r.targetId="garden";
        r.revision=1;r.progressTicks=200;r.requiredTicks=200;
        ctx.state.life.supplies.get(SITE).stock=ctx.data.lifeRules().maxInventory()-1;
        var sim=fixture.facade(ctx);sim.bindPhysicalGarden(SITE,"garden",LEAF,5,64,0);
        long epoch=sim.activateLife(1);sim.setLifeObserved(1,true);sim.observePhysicalGarden(SITE,"garden",2);
        var work=sim.physicalGardenWork(1,epoch).orElseThrow();AtomicInteger calls=new AtomicInteger();
        assertFalse(sim.commitPhysicalGardenWork(work,2,()->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get());assertEquals(0,sim.profession(1).orElseThrow().plantingExperience());
        assertEquals(0,sim.professionSupply(SITE).orElseThrow().gardens().getFirst().harvestSerial());
    }

    @Test void missingSeedCannotReachWorldMutationOrExperience() {
        var sim=newPlot(0);long epoch=sim.activateLife(1);sim.setLifeObserved(1,true);
        sim.observePhysicalGarden(SITE,"garden",-1);
        sim.advanceLife(20,WorldSimProfessionTest.DAY);
        assertTrue(sim.physicalGardenWork(1,epoch).isEmpty());
        AtomicInteger calls=new AtomicInteger();
        var invented=new PhysicalGardenWork(1,epoch,sim.life(1).orElseThrow().revision(),SITE,"garden",LEAF,
                5,64,0,"plant",-1,0);
        assertFalse(sim.commitPhysicalGardenWork(invented,0,()->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get());assertEquals(0,sim.profession(1).orElseThrow().plantingExperience());
    }

    @Test void ordinaryCombatBodyLeaseAllowsFarmingButActualCombatOrMissingObserverDoesNot() {
        var sim=newPlot(1);long epoch=sim.activateLife(1);sim.setLifeObserved(1,true);
        long combat=sim.claimCombat(1,new com.example.myvillage.sim.combat.NpcCombatProfile(20,20,0,"{}",0,0,"",0,null));
        var plant=ready(sim,epoch,-1,20);
        assertTrue(sim.physicalGardenWork(1,epoch).isPresent(), "managed living avatars always also own a combat lease");
        AtomicInteger calls=new AtomicInteger();
        sim.setLifeObserved(1,false);
        assertFalse(sim.commitPhysicalGardenWork(plant,0,()->{calls.incrementAndGet();return true;}));
        sim.setLifeObserved(1,true);
        assertTrue(sim.engageCombat(1,combat,true));
        assertFalse(sim.commitPhysicalGardenWork(plant,0,()->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get());
    }
}
