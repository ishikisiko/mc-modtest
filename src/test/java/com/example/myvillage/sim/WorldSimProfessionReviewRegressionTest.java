package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.profession.*;
import org.junit.jupiter.api.Test;

class WorldSimProfessionReviewRegressionTest {
    private final WorldSimProfessionTest fixtures = new WorldSimProfessionTest();

    @Test void unassignmentAndReemploymentKeepBelongingsAcrossAnUnassignedSave() {
        var ctx = fixtures.context();
        var r = fixtures.assign(ctx, 1, "herb_child", "gather_leaf");
        r.herbs = 5;
        r.herbInventory.put(WorldSimProfessionTest.LEAF, 2);
        r.coins = 17;
        r.fatigue = 42;
        ctx.state.life.professions.get(1).gatheringExperience = 40;
        var sim = fixtures.facade(ctx);
        assertTrue(sim.unassignLife(1));
        sim = fixtures.reload(sim);
        assertEquals(2, sim.profession(1).orElseThrow().herbInventory().get(WorldSimProfessionTest.LEAF));
        assertEquals(3, sim.profession(1).orElseThrow().legacyHerbs());
        for (int turn = 0; turn < 2; turn++) {
            assertTrue(sim.staffProfession(1, WorldSimProfessionTest.SITE, "alchemist", "home", "work"));
            var hired = sim.life(1).orElseThrow();
            assertEquals(5, hired.herbs());
            assertEquals(17, hired.coins());
            assertEquals(42, hired.fatigue());
            assertEquals(40, sim.profession(1).orElseThrow().gatheringExperience());
            assertEquals(0, sim.profession(1).orElseThrow().alchemyExperience());
            sim = fixtures.reload(sim);
            if (turn == 0) assertTrue(sim.unassignLife(1));
        }
    }

    @Test void failedEmbodiedGardenYieldsToAnotherProductivePlotAcrossColdMode() {
        var ctx = fixtures.context();
        fixtures.assign(ctx, 1, "spirit_grower", "garden");
        fixtures.prepareGarden(ctx);
        var other = LifePlace.free("garden_b", "garden", 6, 64, 0);
        LifeEngine.addPlace(ctx, WorldSimProfessionTest.SITE, other);
        LifeEngine.availability(ctx, WorldSimProfessionTest.SITE, other.id(), "available");
        ProfessionEngine.configureGarden(ctx, WorldSimProfessionTest.SITE, other.id(), WorldSimProfessionTest.LEAF);
        fixtures.stock(ctx, WorldSimProfessionTest.LEAF, 2);
        var sim = fixtures.facade(ctx);
        long epoch = sim.activateLife(1);
        sim.advanceLife(20, WorldSimProfessionTest.DAY);
        var first = sim.life(1).orElseThrow();
        assertEquals("garden", first.targetId());
        assertTrue(sim.failLife(1, epoch, first.revision(), "path_unavailable"));
        sim.advanceLife(20 + sim.lifeRules().retryTicks() + 20, WorldSimProfessionTest.DAY);
        assertEquals("garden_b", sim.life(1).orElseThrow().targetId());
        assertEquals(0, sim.profession(1).orElseThrow().plantingExperience());
        assertTrue(sim.releaseLife(1, epoch, 0, 64, 0));
        sim = fixtures.reload(sim);
        sim.advanceLife(sim.lifeClockTicks() + 40, WorldSimProfessionTest.DAY);
        assertEquals("garden_b", sim.life(1).orElseThrow().targetId());
        assertEquals(0, sim.profession(1).orElseThrow().plantingExperience());
        assertTrue(sim.professionSupply(WorldSimProfessionTest.SITE).orElseThrow().gardens().stream()
                .allMatch(g -> g.plantedAt() == -1));
    }

    @Test void changingDayLengthTranslatesQuotaDaysWithoutGivingAnotherCurrentDayQuota() {
        var ctx = fixtures.context();
        var child = fixtures.assign(ctx, 1, "herb_child", "gather_leaf");
        ProfessionEngine.registerGathering(ctx, WorldSimProfessionTest.SITE, "gather_leaf", WorldSimProfessionTest.LEAF, 2);
        LifeEngine.clock(ctx, 240_000, 24_000, true);
        var quota = ctx.state.life.gathering.get(WorldSimProfessionTest.SITE + "|gather_leaf");
        quota.quotaDay = 10;
        quota.gatheredToday = 2;
        var supply = ctx.state.life.supplies.get(WorldSimProfessionTest.SITE);
        supply.quotaDay = 10;
        supply.gatheredToday = 2;
        LifeEngine.clock(ctx, 240_000, 48_000, true);
        assertEquals(5, quota.quotaDay);
        assertEquals(5, supply.quotaDay);
        assertEquals(2, quota.gatheredToday);
        var place = ctx.state.life.sites.get(WorldSimProfessionTest.SITE).place("gather_leaf").orElseThrow();
        assertFalse(ProfessionEngine.canGather(ctx, child, place, 240_000));
        assertTrue(ProfessionEngine.canGather(ctx, child, place, 288_000));
        LifeEngine.clock(ctx, 240_000, 24_000, true);
        assertEquals(10, quota.quotaDay);
        assertFalse(ProfessionEngine.canGather(ctx, child, place, 240_000));
        LifeEngine.clock(ctx, 264_000, 24_000, true);
        quota.quotaDay = supply.quotaDay = 11;
        LifeEngine.clock(ctx, 264_000, 48_000, true);
        assertEquals(5, quota.quotaDay);
        LifeEngine.clock(ctx, 264_000, 24_000, true);
        assertEquals(11, quota.quotaDay, "rounding a partial new day cannot grant another quota");
        assertFalse(ProfessionEngine.canGather(ctx, child, place, 264_000));
    }

    @Test void facadeRejectsTheSameInvalidProfessionWorkplacesAsTheCommand() {
        var sim = fixtures.facade(fixtures.context());
        assertThrows(IllegalArgumentException.class,
                () -> sim.staffProfession(1, WorldSimProfessionTest.SITE, "herb_child", "home", "work"));
        assertThrows(IllegalArgumentException.class,
                () -> sim.staffProfession(1, WorldSimProfessionTest.SITE, "spirit_grower", "home", "work"));
        assertThrows(IllegalArgumentException.class,
                () -> sim.staffProfession(1, WorldSimProfessionTest.SITE, "alchemist", "home", "garden"));
        assertTrue(sim.lifeResidents().isEmpty());
        assertTrue(sim.staffProfession(1, WorldSimProfessionTest.SITE, "herb_child", "home", "garden"));
    }

    @Test void plantedBatchSurvivesReloadAndPaysOnlyOnceWhenItMatures() {
        var ctx = fixtures.context();
        fixtures.assign(ctx, 1, "herb_child", "garden");
        fixtures.prepareGarden(ctx);
        fixtures.stock(ctx, WorldSimProfessionTest.LEAF, 1);
        var sim = fixtures.facade(ctx);
        long epoch = sim.activateLife(1);
        sim.advanceLife(20, WorldSimProfessionTest.DAY);
        var moving = sim.life(1).orElseThrow();
        assertEquals("plant", moving.activity());
        sim.checkpointLife(1, epoch, 4, 64, 0);
        assertTrue(sim.arriveLife(1, epoch, moving.revision()));
        sim.advanceLife(20 + sim.professionRules().plantTicks(), WorldSimProfessionTest.DAY);
        var growing = sim.professionSupply(WorldSimProfessionTest.SITE).orElseThrow().gardens().getFirst();
        assertTrue(growing.plantedAt() >= 0);
        assertEquals(0, sim.lifeSupply(WorldSimProfessionTest.SITE).orElseThrow().stock());
        var body = sim.life(1).orElseThrow();
        assertTrue(sim.releaseLife(1, epoch, body.x(), body.y(), body.z()));
        sim = fixtures.reload(sim);
        assertEquals(growing, sim.professionSupply(WorldSimProfessionTest.SITE).orElseThrow().gardens().getFirst());
        long harvestedAt = -1;
        for (long tick = sim.lifeClockTicks() + 20; tick <= growing.matureAt() + 2000; tick += 20) {
            sim.advanceLife(tick, WorldSimProfessionTest.DAY);
            if (sim.professionSupply(WorldSimProfessionTest.SITE).orElseThrow().gardens().getFirst().plantedAt() == -1) {
                harvestedAt = tick;
                break;
            }
        }
        assertTrue(harvestedAt >= growing.matureAt());
        assertEquals(sim.professionRules().harvestUnits(), sim.lifeSupply(WorldSimProfessionTest.SITE).orElseThrow().stock());
        int earned = sim.professionRules().plantingExperience() + sim.professionRules().harvestExperience();
        assertEquals(earned, sim.profession(1).orElseThrow().plantingExperience());
        sim = fixtures.reload(sim);
        assertFalse(sim.advanceLife(harvestedAt, WorldSimProfessionTest.DAY));
        assertEquals(earned, sim.profession(1).orElseThrow().plantingExperience());
        assertEquals(sim.professionRules().harvestUnits(), sim.lifeSupply(WorldSimProfessionTest.SITE).orElseThrow().stock());
    }
}
