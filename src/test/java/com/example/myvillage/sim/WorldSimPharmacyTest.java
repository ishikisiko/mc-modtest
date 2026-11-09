package com.example.myvillage.sim;

import com.example.myvillage.sim.commerce.*;
import com.example.myvillage.sim.life.LifeSite;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.myvillage.sim.WorldSimCommerceTest.*;

class WorldSimPharmacyTest {
    private final WorldSimCommerceTest fixture=new WorldSimCommerceTest();
    private static final String HERB="myvillage:spirit_leaf";
    private static final long WEEK=7L*DAY;
    /** 品质库存用例先满足真实声望门槛；低声望拒绝由玩家经济测试单独覆盖。 */
    private WorldSim unlockedPharmacy() {
        var sim=fixture.fresh();
        int required=sim.playerEconomyRules().pillReputation().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for(String player:List.of(A,B))for(int day=0;sim.jianghuReputation(player)<required;day++) {
            var task=sim.acceptTownTask(player,player,SITE,"supply_bread",day);
            assertTrue(sim.completeTownTask(player,SITE,task,()->true));
        }
        return sim;
    }
    private static JsonObject json(WorldSim sim) {return JsonParser.parseString(new String(sim.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    private static JsonObject savedMarket(JsonObject root,String site) {return root.getAsJsonObject("commerce").getAsJsonObject("markets").getAsJsonObject(site);}
    private static WorldSim read(JsonObject root) {return WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(SEED),SimFixtures.data());}
    private WorldSim stock(WorldSim sim,String key,int amount) {
        var root=json(sim);savedMarket(root,SITE).getAsJsonObject("items").addProperty(key,amount);return fixture.reload(read(root));
    }
    private long due(WorldSim sim) {return savedMarket(json(sim),SITE).get("next_restock_at").getAsLong();}

    @Test void threeBasicPillsHaveConfiguredQualityPricesAndSeparateResaleStock() {
        var sim=unlockedPharmacy();
        for(String pill:List.of("pill_huiling","pill_yangyuan","pill_ningshen"))for(int quality=1;quality<=4;quality++) {
            String suffix=quality==1?"":"_q"+quality;
            var sell=sim.commerceRules().offer("sell_"+pill+suffix);
            var buy=sim.commerceRules().offer("buy_"+pill+suffix);
            assertEquals(List.of(6,9,12,18).get(quality-1),sell.price());
            assertEquals(quality,sell.pillQuality());assertEquals(quality,buy.pillQuality());
            assertTrue(buy.price()>sell.price());assertEquals(buy.stockKey(),sell.stockKey());
            assertEquals(quality==1?4:0,fixture.market(sim).items().get(sell.stockKey()));
            var before=fixture.market(sim);
            assertTrue(sim.commitCommerce(fixture.quote(sim,sell.id(),A),A));
            assertEquals(before.treasury(),fixture.market(sim).treasury());
            assertEquals(before.items().get(sell.stockKey())+1,fixture.market(sim).items().get(sell.stockKey()));
            assertTrue(sim.commitCommerce(fixture.quote(sim,buy.id(),B),B));
            assertEquals(before.items(),fixture.market(sim).items());
        }
        assertEquals(fixture.market(sim),fixture.market(fixture.reload(sim)));
    }
    @Test void highQualityLastItemAndPurchaseQuotaAreSharedAcrossTwoPlayers() {
        var sim=unlockedPharmacy();String sell="sell_pill_huiling_q4",buy="buy_pill_huiling_q4";
        assertTrue(sim.commitCommerce(fixture.quote(sim,sell,A),A));
        var alice=fixture.quote(sim,buy,A);var bob=fixture.quote(sim,buy,B);
        assertTrue(sim.commitCommerce(alice,A));assertFalse(sim.commitCommerce(bob,B));
        assertFalse(sim.commitCommerce(alice,A));
        assertEquals(4,fixture.market(sim).items().get(PILL));
        assertEquals(0,fixture.market(sim).items().get(PILL+"#4"));
    }
    @Test void forgedReceiptPriceAndCountCannotCommit() {
        var sim=fixture.fresh();var q=fixture.quote(sim,"sell_pill_huiling_q3",A);var before=fixture.market(sim);
        var forged=new CommerceEngine.Quote(q.result(),q.siteId(),q.personId(),q.epoch(),q.offerId(),q.revision(),q.available(),q.price()+100,A,q.quantity());
        assertFalse(sim.commitCommerce(forged,A));assertEquals(before,fixture.market(sim));
    }
    @Test void sixShopHerbsStartFiniteAndDoNotWithdrawProfessionRawMaterials() {
        var sim=fixture.fresh();
        for(String herb:sim.professionRules().herbs()) {
            var offer=sim.commerceRules().offer("buy_"+herb.substring(herb.indexOf(':')+1));
            assertEquals(2,offer.price());assertEquals(16,fixture.market(sim).items().get(herb));
            assertTrue(sim.commitCommerce(fixture.quote(sim,offer.id(),A),A));
            assertEquals(15,fixture.market(sim).items().get(herb));
        }
        assertEquals(0,sim.lifeSupply(SITE).orElseThrow().stock());
    }
    @Test void restockWaitsSevenDaysSurvivesReloadAndDoesNotAccumulateMissedWeeks() {
        var sim=fixture.fresh();assertTrue(sim.commitCommerce(fixture.bulk(sim,"buy_spirit_leaf",A,16),A));
        assertEquals(0,fixture.market(sim).items().get(HERB));assertEquals(WEEK,due(sim));
        sim.advanceLife(WEEK-1,DAY);assertEquals(0,fixture.market(sim).items().get(HERB));
        sim=fixture.reload(sim);assertEquals(WEEK,due(sim));
        sim.advanceLife(WEEK,DAY);assertEquals(16,fixture.market(sim).items().get(HERB));
        assertEquals(2*WEEK,due(sim));
        sim=stock(sim,HERB,0);sim.advanceLife(WEEK,DAY);
        assertEquals(0,fixture.market(sim).items().get(HERB));
        sim=fixture.reload(sim);assertEquals(0,fixture.market(sim).items().get(HERB));
        sim.advanceLife(30*WEEK+100,DAY);
        assertEquals(16,fixture.market(sim).items().get(HERB));assertEquals(31*WEEK,due(sim));
        assertEquals(4,fixture.market(sim).items().get(PILL));
    }
    @Test void restockOnlyTopsUpAndDoesNotClearSurplusOrReplenishPillsOrTreasury() {
        var sim=stock(fixture.fresh(),HERB,25);sim=stock(sim,PILL,0);
        // Observe the delivery alone: ordinary resident wages legitimately spend the treasury.
        for(var resident:sim.lifeResidents())assertTrue(sim.unassignLife(resident.personId()));
        assertTrue(sim.lifeResidents().isEmpty());
        var before=fixture.market(sim);sim.advanceLife(WEEK,DAY);
        assertEquals(25,fixture.market(sim).items().get(HERB));assertEquals(0,fixture.market(sim).items().get(PILL));
        assertEquals(before.treasury(),fixture.market(sim).treasury());assertEquals(before.transactions(),fixture.market(sim).transactions());
    }
    @Test void clockRollbackPauseAndDayScaleChangesCannotRepeatDelivery() {
        var sim=stock(fixture.fresh(),HERB,0);sim.advanceLife(WEEK,DAY);
        sim=stock(sim,HERB,0);sim.advanceLife(WEEK-100,DAY);sim.advanceLife(WEEK,DAY);
        assertEquals(0,fixture.market(sim).items().get(HERB));assertEquals(2*WEEK,due(sim));
        sim.scheduler().setPaused(true);sim.advanceLife(3*WEEK,DAY);
        assertEquals(0,fixture.market(sim).items().get(HERB));assertEquals(4*WEEK,due(sim));
        sim.scheduler().setPaused(false);sim.advanceLife(4*WEEK,DAY);
        assertEquals(16,fixture.market(sim).items().get(HERB));
        var scaled=stock(fixture.fresh(),HERB,0);scaled.advanceLife(3L*DAY,DAY);
        scaled.advanceLife(3L*DAY,DAY/2);assertEquals(5L*DAY,due(scaled));
        assertEquals(0,fixture.market(scaled).items().get(HERB));
        scaled.advanceLife(5L*DAY,DAY/2);assertEquals(16,fixture.market(scaled).items().get(HERB));
    }
    @Test void marketsKeepTheirOwnRegistrationAndDeliveryTimes() {
        var sim=stock(fixture.fresh(),HERB,0);sim.advanceLife(DAY,DAY);
        var site=sim.lifeSite(SITE).orElseThrow();String other="other_town";
        sim.registerLifeSite(new LifeSite(other,"另城",site.dimension(),site.regionId(),site.anchorX(),site.anchorY(),site.anchorZ(),site.places()));
        assertTrue(sim.initializeCommerce(other));
        var root=json(sim);savedMarket(root,other).getAsJsonObject("items").addProperty(HERB,0);sim=read(root);
        sim.advanceLife(WEEK,DAY);assertEquals(16,fixture.market(sim).items().get(HERB));
        assertEquals(0,sim.commerce(other).orElseThrow().items().get(HERB));
        sim.advanceLife(WEEK+DAY,DAY);assertEquals(16,sim.commerce(other).orElseThrow().items().get(HERB));
    }
    @Test void malformedDeliveryTimesAndQualityStockFailClosed() {
        for(String invalid:List.of("myvillage:pill_huiling#0","myvillage:pill_huiling#1","myvillage:pill_huiling#5")) {
            var root=json(fixture.fresh());savedMarket(root,SITE).getAsJsonObject("items").addProperty(invalid,1);
            assertThrows(SimFormatException.class,()->read(root));
        }
        var root=json(fixture.fresh());savedMarket(root,SITE).addProperty("next_restock_at",-1);
        assertThrows(SimFormatException.class,()->read(root));
    }
    @Test void malformedQualityPricesAndRestockTargetsAreRejected() throws Exception {
        String source=Files.readString(Path.of("src/main/resources/data/myvillage/world_sim/commerce_rules.json"));
        for(String multipliers:List.of("[1,1,2,3]","[1,1.5,2]","[1,\"1.5\",2,3]","[1,1.3,2,3]")) {
            var root=JsonParser.parseString(source).getAsJsonObject();
            root.getAsJsonObject("pill_trading").add("quality_multipliers",JsonParser.parseString(multipliers));
            assertThrows(SimDataException.class,()->CommerceRules.parse("test",root));
        }
        var root=JsonParser.parseString(source).getAsJsonObject();
        root.getAsJsonObject("herb_restock").getAsJsonObject("stock").addProperty(HERB,65);
        assertThrows(SimDataException.class,()->CommerceRules.parse("test",root));
    }
}
