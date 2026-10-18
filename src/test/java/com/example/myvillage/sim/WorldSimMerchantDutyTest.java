package com.example.myvillage.sim;

import com.example.myvillage.sim.combat.NpcCombatProfile;
import com.example.myvillage.sim.life.LifeResidentView;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldSimMerchantDutyTest {
    private final WorldSimCommerceTest fixtures = new WorldSimCommerceTest();

    @Test void allMerchantsStayWorkingAcrossCyclesAndFatigueWithoutFreeGoods() {
        var sim = fixtures.fresh();
        sim.observeLifeDaylight(.25);
        var stock = fixtures.market(sim).items();
        long money = sim.lifeSupply(WorldSimCommerceTest.SITE).orElseThrow().treasury()
                + sim.lifeResidents().stream().mapToLong(LifeResidentView::coins).sum();
        for (int tick = 40; tick <= 24_020; tick += 20) {
            sim.advanceLife(tick, WorldSimCommerceTest.DAY);
            for (var r : sim.lifeResidents()) {
                assertEquals("work", r.activity(), r.role());
                assertEquals("working", r.phase(), r.role());
                assertEquals(r.workId(), r.targetId());
            }
        }
        assertEquals(stock, fixtures.market(sim).items(), "standing at an empty pharmacy does not manufacture pills");
        assertEquals(money, sim.lifeSupply(WorldSimCommerceTest.SITE).orElseThrow().treasury()
                + sim.lifeResidents().stream().mapToLong(LifeResidentView::coins).sum());
        assertTrue(sim.lifeResidents().stream().allMatch(r -> r.fatigue() == 100));
    }

    @Test void sunsetClosesQuotesAndInterruptsEvenConversationThenDawnRequiresWalking() {
        var sim = fixtures.fresh();sim.observeLifeDaylight(.25);
        for (var r : sim.lifeResidents()) sim.pauseLife(r.personId(), sim.lifeRules().conversationPauseTicks());
        var before = fixtures.market(sim);
        sim.observeLifeDaylight(.75);
        assertEquals("off_duty", fixtures.quote(sim, WorldSimCommerceTest.BUY, WorldSimCommerceTest.A).result());
        sim.advanceLife(40, WorldSimCommerceTest.DAY);
        for (var r : sim.lifeResidents()) {
            assertEquals("rest", r.activity());assertEquals("moving", r.phase());assertEquals(r.homeId(), r.targetId());
        }
        assertEquals(before, fixtures.market(sim), "sunset pays no unfinished work");
        sim.observeLifeDaylight(.25);sim.advanceLife(60, WorldSimCommerceTest.DAY);
        for (var r : sim.lifeResidents()) {
            assertEquals("work", r.activity());assertEquals("moving", r.phase());
            assertEquals(r.workId(), r.targetId());assertEquals(0, r.progressTicks());
        }
        assertEquals("off_duty", fixtures.quote(sim, WorldSimCommerceTest.BUY, WorldSimCommerceTest.A).result());
    }

    @Test void solarCommandsCannotAdvanceMoneyOrRestCooldown() {
        var sim = fixtures.fresh();sim.observeLifeDaylight(.25);
        String offer = sim.commerceRules().offers("innkeeper").getFirst().id();
        assertTrue(sim.commitCommerce(fixtures.quote(sim,offer,WorldSimCommerceTest.A),WorldSimCommerceTest.A));
        byte[] saved = sim.toBytes();long clock = sim.lifeClockTicks();
        sim.observeLifeDaylight(.75);sim.observeLifeDaylight(.25);
        assertArrayEquals(saved, sim.toBytes());assertEquals(clock, sim.lifeClockTicks());
        assertEquals("rest_cooldown", fixtures.quote(sim,offer,WorldSimCommerceTest.A).result());
        var loaded = fixtures.reload(sim);loaded.observeLifeDaylight(.75);
        assertEquals("off_duty",fixtures.quote(loaded,offer,WorldSimCommerceTest.A).result());
        loaded.observeLifeDaylight(.25);
        assertEquals("rest_cooldown",fixtures.quote(loaded,offer,WorldSimCommerceTest.A).result());
    }

    @Test void daytimeShiftStillRequiresUsableWorkplaceAndCombatRelease() {
        var sim = fixtures.fresh();sim.observeLifeDaylight(.25);
        var r = fixtures.worker(sim,"herbalist");
        sim.setLifePlaceAvailability(r.siteId(),r.workId(),"blocked");
        long completed=r.completedActivities();sim.advanceLife(1000,WorldSimCommerceTest.DAY);
        assertEquals(completed,sim.life(r.personId()).orElseThrow().completedActivities());
        assertEquals("waiting",sim.life(r.personId()).orElseThrow().phase());
        sim.setLifePlaceAvailability(r.siteId(),r.workId(),"available");
        sim.advanceLife(1200,WorldSimCommerceTest.DAY);
        assertEquals("moving",sim.life(r.personId()).orElseThrow().phase());
        long combat=sim.claimCombat(r.personId(),new NpcCombatProfile(20,20,0,"{}",0,0,"",0,null));
        assertTrue(sim.engageCombat(r.personId(),combat,true));
        sim.advanceLife(2200,WorldSimCommerceTest.DAY);
        assertEquals(completed,sim.life(r.personId()).orElseThrow().completedActivities());
        assertEquals("unavailable",fixtures.quote(sim,WorldSimCommerceTest.BUY,WorldSimCommerceTest.A).result());
    }
}
