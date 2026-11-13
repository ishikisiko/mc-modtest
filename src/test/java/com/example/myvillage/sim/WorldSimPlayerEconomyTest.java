package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.myvillage.sim.WorldSimCommerceTest.*;
import com.example.myvillage.sim.economy.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 实际配置下的贡献消费、任务冻结、声望成交检查和正常保存恢复。 */
class WorldSimPlayerEconomyTest {
    private final WorldSimCommerceTest commerce = new WorldSimCommerceTest();
    private static WorldSim restore(WorldSim sim) { return WorldSim.fromBytes(sim.toBytes(), SimFixtures.graph(SEED), SimFixtures.data()); }
    private static WorldSim balance(WorldSim sim, String player, int merit, int rep) {
        var root = JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        boolean found = false;
        for (var element : root.getAsJsonArray("player_members")) {
            var p = element.getAsJsonObject(); if (!p.get("player").getAsString().equals(player)) continue;
            p.addProperty("contribution", merit); p.addProperty("jianghu_reputation", rep); found = true;
        }
        assertTrue(found); var loaded = WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED), SimFixtures.data());
        for (var resident : loaded.lifeResidents()) loaded.activateLife(resident.personId());
        return loaded;
    }
    private static WorldSim member() {
        var sim = SimFixtures.genesis(SEED, "small", 6); var sect = sim.sects(false).getFirst();
        sim.joinSect(A, "Alice", sect.id(), new PlayerQualification("qi_refining", 0, true, 3000), true); return sim;
    }
    @Test void sectRewardsAreFrozenAndInventoryFailureCannotCompleteTheTask() {
        var sim = member(); var offer = sim.offerTask(A).orElseThrow(); var reward = sim.sectTaskOfferReward(A);
        assertEquals(offer.contribution(), reward.contribution()); assertEquals(reward, sim.sectTaskOfferReward(A));
        sim.acceptTask(A, "Alice"); sim.advanceTask(A, offer.kind(), offer.count());
        assertEquals(reward, sim.sectTaskReward(A)); sim = restore(sim); assertEquals(reward, sim.sectTaskReward(A));
        var before = sim.toBytes(); assertFalse(sim.completeSectTask(A, "Alice", sim.task(A).orElseThrow(), () -> false)); assertArrayEquals(before, sim.toBytes());
        var task = sim.task(A).orElseThrow(); assertTrue(sim.completeSectTask(A, "Alice", task, () -> true));
        assertEquals(reward.contribution(), sim.playerMember(A).orElseThrow().contribution());
        assertFalse(sim.completeSectTask(A, "Alice", task, () -> true)); assertTrue(sim.task(A).isEmpty());
    }
    @Test void expeditionRewardUsesTheLedgerWithoutConsumingDailyTasks(){
        var sim=commerce.fresh();var task=sim.acceptTownTask(A,"Alice",SITE,"supply_bread",3);
        var reward=new TaskReward(0,15,java.util.Map.of("low",12));
        var before=sim.toBytes();assertFalse(sim.rewardExpedition(B,"Bob",reward,()->false));assertArrayEquals(before,sim.toBytes());
        assertFalse(sim.rewardExpedition(A,"Alice",reward,()->false));assertEquals(0,sim.jianghuReputation(A));
        assertTrue(sim.rewardExpedition(A,"Alice",reward,()->true));assertEquals(15,sim.jianghuReputation(A));assertEquals(task,sim.townTask(A).orElseThrow());
        assertEquals(15,restore(sim).jianghuReputation(A));
    }
    @Test void linlangChecksMembershipRankAndFundsBeforeCallingInventoryAndDebitsOnce() {
        var sim = balance(member(), A, 20, 0); int sect = sim.playerMember(A).orElseThrow().sectId(); var called = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> sim.buyLinlang(B, sect, "huiling", () -> { called.incrementAndGet(); return true; }));
        assertThrows(IllegalArgumentException.class, () -> sim.buyLinlang(A, sect, "armor_pattern", () -> { called.incrementAndGet(); return true; })); assertEquals(0, called.get());
        assertFalse(sim.buyLinlang(A, sect, "huiling", () -> false)); assertEquals(20, sim.playerMember(A).orElseThrow().contribution());
        assertTrue(sim.buyLinlang(A, sect, "huiling", () -> true)); assertEquals(16, sim.playerMember(A).orElseThrow().contribution());
        assertEquals(16, restore(sim).playerMember(A).orElseThrow().contribution());
        var poor = balance(sim, A, 0, 0); assertEquals("insufficient_contribution", assertThrows(IllegalArgumentException.class, () -> poor.buyLinlang(A, sect, "huiling", () -> true)).getMessage());
    }
    @Test void manualExchangeSupportsFreeAndPaidRowsAndAnInventoryFailureKeepsBothRecords() {
        var sim = member(); int sect = sim.playerMember(A).orElseThrow().sectId(); sim.promotePlayer(A, "Alice", "elder");
        String paid = sim.borrowable(A).stream().filter(id -> sim.manualContributionCost(id) > 0).findFirst().orElseThrow();
        assertEquals(0, sim.manualContributionCost("jianqi_jue")); assertTrue(sim.exchangeManual(A, "Alice", "jianqi_jue", () -> true));
        assertEquals("insufficient_contribution", assertThrows(IllegalArgumentException.class, () -> sim.exchangeManual(A, "Alice", paid, () -> true)).getMessage());
        var funded = balance(sim, A, 100, 0); int cost = funded.manualContributionCost(paid);
        assertFalse(funded.exchangeManual(A, "Alice", paid, () -> false)); assertFalse(funded.hasBorrowed(A, paid)); assertEquals(100, funded.playerMember(A).orElseThrow().contribution());
        assertTrue(funded.exchangeManual(A, "Alice", paid, () -> true)); assertEquals(100 - cost, funded.playerMember(A).orElseThrow().contribution());
        assertThrows(IllegalArgumentException.class, () -> funded.exchangeManual(A, "Alice", paid, () -> true));
        assertEquals(100 - cost, funded.playerMember(A).orElseThrow().contribution()); assertEquals(sect, funded.playerMember(A).orElseThrow().sectId());
    }
    @Test void townTaskDoesNotRequireSectAndCannotPayTwiceOrRerollOnReload() {
        var sim = commerce.fresh(); var offer = sim.townTaskOffer(A, SITE, "supply_bread", 3);
        assertEquals(offer, sim.townTaskOffer(A, SITE, "supply_bread", 3)); assertEquals(offer, sim.acceptTownTask(A, "Alice", SITE, "supply_bread", 3));
        assertFalse(sim.playerMember(A).orElseThrow().inSect()); sim = restore(sim); assertEquals(offer, sim.townTask(A).orElseThrow());
        assertFalse(sim.completeTownTask(A, SITE, offer, () -> false)); assertEquals(0, sim.jianghuReputation(A));
        assertTrue(sim.completeTownTask(A, SITE, offer, () -> true)); assertEquals(offer.reward().reputation(), sim.jianghuReputation(A));
        var finished = sim; assertThrows(IllegalArgumentException.class, () -> finished.completeTownTask(A, SITE, offer, () -> true));
        assertFalse(sim.townTaskAvailable(A, SITE, 3)); assertFalse(sim.townTaskAvailable(A, SITE, 2)); assertTrue(sim.townTaskAvailable(A, SITE, 4));
        assertEquals(offer.reward().reputation(), restore(sim).jianghuReputation(A));
    }
    @Test void huntingCountsOnlyTheIssuedRegionAndTheOriginalBoardOwnsTurnIn() {
        var sim = commerce.fresh(); var a = sim.acceptTownTask(A, "Alice", SITE, "clear_beasts", 0); var calls = new AtomicInteger();
        assertFalse(sim.advanceTownHunt(A, "wrong_region")); assertFalse(sim.advanceTownHunt(B, a.regionId()));
        assertThrows(IllegalArgumentException.class, () -> sim.completeTownTask(A, SITE, a, () -> { calls.incrementAndGet(); return true; })); assertEquals(0, calls.get());
        for (int i = 0; i < 3; i++) assertTrue(sim.advanceTownHunt(A, a.regionId())); assertFalse(sim.advanceTownHunt(A, a.regionId()));
        var ready = sim.townTask(A).orElseThrow(); assertThrows(IllegalArgumentException.class, () -> sim.completeTownTask(A, "other_town", ready, () -> true));
        assertTrue(sim.completeTownTask(A, SITE, ready, () -> true)); assertTrue(sim.jianghuReputation(A) >= 8);
    }
    @Test void allFourPharmacyTiersAreCheckedAgainByTheLedgerAndSellingRemainsAvailable() {
        for (int quality = 1; quality <= 4; quality++) {
            var sim = commerce.fresh(); var task = sim.acceptTownTask(A, "Alice", SITE, "supply_bread", 0); sim.completeTownTask(A, SITE, task, () -> true);
            sim = balance(sim, A, 0, 0); String suffix = quality == 1 ? "" : "_q" + quality;
            String buy = "buy_pill_huiling" + suffix, sell = "sell_pill_huiling" + suffix;
            assertTrue(sim.commitCommerce(commerce.quote(sim, sell, A), A)); int required = sim.commerceReputationRequired(buy);
            if (required > 0) {
                assertEquals("reputation_low", commerce.quote(sim, buy, A).result()); var before = commerce.market(sim);
                assertFalse(sim.commitCommerce(commerce.quote(sim, buy, A), A)); assertEquals(before, commerce.market(sim));
                sim = balance(sim, A, 0, required - 1); assertEquals("reputation_low", commerce.quote(sim, buy, A).result());
                sim = balance(sim, A, 0, required);
            }
            var quote = commerce.quote(sim, buy, A); assertTrue(quote.ready(), "quality " + quality + " " + quote);
            assertTrue(sim.commitCommerce(quote, A)); assertFalse(sim.commitCommerce(quote, A)); assertEquals(required, sim.jianghuReputation(A));
        }
    }
    @Test void advancedPillsUseTheirOwnReputationThresholdAndStillChargeSpiritStones() {
        var sim = commerce.fresh(); var task = sim.acceptTownTask(A, "Alice", SITE, "supply_bread", 0); sim.completeTownTask(A, SITE, task, () -> true);
        sim = balance(sim, A, 0, 79); String offer = "buy_pill_zhuji";
        assertEquals(80, sim.commerceReputationRequired(offer)); assertEquals("reputation_low", commerce.quote(sim, offer, A).result());
        sim = balance(sim, A, 0, 80); var quote = commerce.quote(sim, offer, A); assertTrue(quote.ready());
        assertEquals(64, quote.price()); int stock = commerce.market(sim).items().get("myvillage:pill_zhuji");
        assertTrue(sim.commitCommerce(quote, A)); assertEquals(stock - 1, commerce.market(sim).items().get("myvillage:pill_zhuji"));
        assertEquals(80, sim.jianghuReputation(A));
    }
    @Test void corruptedNegativeReputationAndReversedRewardRangesAreRejected() throws Exception {
        var sim = commerce.fresh(); var a = sim.acceptTownTask(A, "Alice", SITE, "supply_bread", 0); sim.completeTownTask(A, SITE, a, () -> true);
        var root = JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject(); root.getAsJsonArray("player_members").get(0).getAsJsonObject().addProperty("jianghu_reputation", -1);
        assertThrows(SimFormatException.class, () -> WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED), SimFixtures.data()));
        var data = JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/data/myvillage/world_sim/player_economy.json"))).getAsJsonObject();
        data.getAsJsonObject("sect_task_rewards").getAsJsonObject("patrol_beasts").add("contribution_bonus", JsonParser.parseString("[6,0]"));
        assertThrows(SimDataException.class, () -> PlayerEconomyRules.parse("fixture", data));
    }
}
