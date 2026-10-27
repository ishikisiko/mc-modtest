package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.sim.commerce.*;
import com.example.myvillage.sim.combat.NpcCombatProfile;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.model.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Ledger receipts and real life callbacks, not a claim of native inventory or client testing. */
class WorldSimCommerceTest {
    static final long SEED=42;
    static final int DAY=24_000;
    static final String A="00000000-0000-0000-0000-00000000000a",B="00000000-0000-0000-0000-00000000000b";
    static final String SITE="shop_town",PILL="myvillage:pill_huiling",BUY="buy_pill_huiling";
    WorldSim fresh() {
        var state=new WorldState();state.seed=SEED;state.tierId="small";state.genesisDaysPerYear=24;
        var graph=SimFixtures.graph(SEED);String region=graph.regions().getFirst().id();
        for(var r:graph.regions())state.regions.put(r.id(),new RegionState(r.id()));
        for(int id=1;id<=4;id++) {
            var p=new Person();p.id=id;p.surname="林";p.given="商"+id;p.regionId=region;p.homeRegionId=region;
            p.birthDay=-480;p.root=new int[]{2000,2000,2000,2000,2000};state.persons.put(id,p);
        }
        state.nextPersonId=5;
        var sim=WorldSim.fromBytes(StateCodec.toBytes(state,SimFixtures.data().realms()),graph,SimFixtures.data());
        List<LifePlace> places=new ArrayList<>();int x=0;
        for(var job:sim.commerceRules().staff()) {
            places.add(LifePlace.free(job.home(),"rest",x,64,0));
            places.add(LifePlace.free(job.work(),"work",x+2,64,0));x+=10;
        }
        places.add(LifePlace.free("social","social",5,64,5));
        sim.registerLifeSite(new LifeSite(SITE,"试镇","minecraft:overworld",region,0,64,0,places));
        for(var place:places)sim.setLifePlaceAvailability(SITE,place.id(),"available");
        sim.registerPlayerActor(A,"Alice");sim.registerPlayerActor(B,"Bob");
        assertTrue(sim.initializeCommerce(SITE));assertEquals(3,sim.staffCommerce(SITE).size());
        sim.advanceLife(0,DAY);
        for(var r:sim.lifeResidents())sim.activateLife(r.personId());
        sim.advanceLife(20,DAY);
        for(var r:sim.lifeResidents()) {
            assertEquals("work",r.activity());var work=sim.lifeSite(SITE).orElseThrow().place(r.workId()).orElseThrow();
            assertTrue(sim.checkpointLife(r.personId(),r.epoch(),work.x(),work.y(),work.z()));
            assertTrue(sim.arriveLife(r.personId(),r.epoch(),r.revision()));
        }
        return sim;
    }
    LifeResidentView worker(WorldSim sim,String role){return sim.lifeResidents().stream().filter(r->r.role().equals(role)).findFirst().orElseThrow();}
    CommerceEngine.Quote quote(WorldSim sim,String id,String player) {
        var o=sim.commerceRules().offer(id);var r=worker(sim,o.role());
        return sim.quoteCommerce(r.personId(),r.epoch(),id,player);
    }
    CommerceView market(WorldSim sim){return sim.commerce(SITE).orElseThrow();}
    CommerceEngine.Quote bulk(WorldSim sim,String id,String player,int count) {
        var r=worker(sim,sim.commerceRules().offer(id).role());
        return sim.quoteCommerce(r.personId(),r.epoch(),id,player,count);
    }
    @Test void batchIsOneReceiptAndCompetingBatchCannotPartiallyCommit() {
        var sim=fresh();var before=market(sim);
        var alice=bulk(sim,BUY,A,3);var bob=bulk(sim,BUY,B,2);
        assertTrue(alice.ready());assertEquals(24,alice.price());assertTrue(bob.ready());
        assertTrue(sim.commitCommerce(alice,A));var after=market(sim);
        assertEquals(1,after.items().get(PILL));assertEquals(before.treasury()+24,after.treasury());
        assertEquals(before.transactions()+1,after.transactions());
        assertFalse(sim.commitCommerce(bob,B));assertFalse(sim.commitCommerce(alice,A));assertEquals(after,market(sim));
        assertEquals("out_of_stock",bulk(sim,BUY,B,2).result());
        assertEquals(after,reload(sim).commerce(SITE).orElseThrow());
    }
    @Test void pillMerchantSellsAllArmorPatternsWithFinitePersistentStock() {
        var sim=fresh();var pills=market(sim).items().get(PILL);
        for(String slot:List.of("helmet","chestplate","leggings","boots")) {
            String id="buy_pattern_xuantie_"+slot;
            var offer=sim.commerceRules().offer(id);
            assertEquals("herbalist",offer.role());assertEquals("buy",offer.kind());
            assertEquals("myvillage:pattern_xuantie_"+slot,offer.item());
            assertEquals(1,offer.quantity());assertEquals(0,offer.medicineCost());
            assertTrue(offer.initialStock()>0);
            var before=market(sim);var q=bulk(sim,id,A,offer.initialStock());
            assertTrue(q.ready());assertEquals(offer.price()*offer.initialStock(),q.price());
            assertTrue(sim.commitCommerce(q,A));assertFalse(sim.commitCommerce(q,A));
            assertEquals(before.treasury()+q.price(),market(sim).treasury());
            assertEquals("out_of_stock",quote(sim,id,B).result());
            assertEquals(0,market(reload(sim)).items().get(offer.item()));
        }
        assertEquals(pills,market(sim).items().get(PILL));
    }
    @Test void oldMarketGetsNewPatternsOnceWithoutRefillingExistingGoods() {
        var original=fresh();
        assertTrue(original.commitCommerce(bulk(original,BUY,A,4),A));
        var before=market(original);
        var root=JsonParser.parseString(new String(original.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        var items=root.getAsJsonObject("commerce").getAsJsonObject("markets").getAsJsonObject(SITE).getAsJsonObject("items");
        for(String slot:List.of("helmet","chestplate","leggings","boots"))items.remove("myvillage:pattern_xuantie_"+slot);
        var sim=WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(SEED),SimFixtures.data());
        for(var r:sim.lifeResidents())sim.activateLife(r.personId());
        assertEquals(before.revision()+1,market(sim).revision());
        assertEquals(before.transactions(),market(sim).transactions());
        assertEquals(before.treasury(),market(sim).treasury());
        assertEquals(before.staffedRoles(),market(sim).staffedRoles());
        assertEquals(0,market(sim).items().get(PILL));
        for(String slot:List.of("helmet","chestplate","leggings","boots")) {
            String id="buy_pattern_xuantie_"+slot;var offer=sim.commerceRules().offer(id);
            assertEquals(offer.initialStock(),market(sim).items().get(offer.item()));
            assertTrue(sim.commitCommerce(bulk(sim,id,A,offer.initialStock()),A));
        }
        var after=market(sim);sim=reload(sim);
        assertFalse(sim.initializeCommerce(SITE));assertEquals(after,market(sim));
        assertEquals(after,market(reload(sim)));
    }
    @Test void batchSalesRespectTreasuryAndSharedHerbCapacity() {
        var sim=fresh();var initial=market(sim);int available=quote(sim,"sell_spirit_leaf",A).available();
        assertTrue(available>=3);assertTrue(sim.commitCommerce(bulk(sim,"sell_spirit_leaf",A,3),A));
        assertEquals(initial.treasury()-6,market(sim).treasury());assertEquals(3,sim.lifeSupply(SITE).orElseThrow().stock());
        var before=market(sim);var supplies=sim.lifeSupply(SITE).orElseThrow();
        var q=bulk(sim,"sell_dew_blossom",A,available);
        assertEquals("cannot_buy",q.result());assertFalse(sim.commitCommerce(q,A));
        assertEquals(before,market(sim));assertEquals(supplies,sim.lifeSupply(SITE).orElseThrow());
    }
    @Test void eachPlayerHerbSalePreservesItsKindAcrossSaveAndCannotConsumeLegacyCapacity() {
        var sim=fresh();
        for(String herb:sim.professionRules().herbs()) {
            String offer="sell_"+herb.substring(herb.indexOf(':')+1);
            assertTrue(sim.commitCommerce(bulk(sim,offer,A,2),A));
        }
        var kinds=sim.professionSupply(SITE).orElseThrow();assertEquals(6,kinds.herbs().size());
        for(String herb:sim.professionRules().herbs())assertEquals(2,kinds.herbs().get(herb));
        assertEquals(0,kinds.legacyStock());assertEquals(12,sim.lifeSupply(SITE).orElseThrow().stock());
        assertEquals(kinds,reload(sim).professionSupply(SITE).orElseThrow());
        var root=JsonParser.parseString(new String(sim.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        root.getAsJsonObject("life").getAsJsonObject("supplies").getAsJsonObject(SITE).addProperty("stock",31);
        var loaded=WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(SEED),SimFixtures.data());
        for(var resident:loaded.lifeResidents())loaded.activateLife(resident.personId());
        assertEquals(19,loaded.professionSupply(SITE).orElseThrow().legacyStock());
        assertEquals("cannot_buy",bulk(loaded,"sell_dew_blossom",A,2).result());
        assertTrue(loaded.commitCommerce(quote(loaded,"sell_dew_blossom",A),A));
        assertEquals(32,loaded.lifeSupply(SITE).orElseThrow().stock());
        assertEquals(3,loaded.professionSupply(SITE).orElseThrow().herbs().get("myvillage:dew_blossom"));
        assertEquals(19,loaded.professionSupply(SITE).orElseThrow().legacyStock());
        assertEquals("cannot_buy",quote(loaded,"sell_spirit_leaf",A).result());
    }
    @Test void malformedOrBulkServiceRequestsNeverChangeLedger() {
        var sim=fresh();var before=market(sim);
        for(int count:new int[]{0,-1,sim.commerceRules().stockLimit()+1,Integer.MAX_VALUE}) {
            var q=bulk(sim,BUY,A,count);assertEquals("invalid_quantity",q.result());assertFalse(sim.commitCommerce(q,A));
        }
        var q=bulk(sim,"inn_rest",A,2);assertEquals("invalid_quantity",q.result());assertFalse(sim.commitCommerce(q,A));
        assertEquals(before,market(sim));assertTrue(quote(sim,"inn_rest",A).ready());
    }
    @Test void treasuryRoomIsCheckedAgainstWholeBatch() {
        var original=fresh();var root=JsonParser.parseString(new String(original.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        root.getAsJsonObject("life").getAsJsonObject("supplies").getAsJsonObject(SITE)
                .addProperty("treasury",original.lifeRules().maxInventory()-10);
        var sim=WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(SEED),SimFixtures.data());
        for(var r:sim.lifeResidents())sim.activateLife(r.personId());
        var before=market(sim);assertTrue(quote(sim,BUY,A).ready());assertEquals(1,quote(sim,BUY,A).available());
        var q=bulk(sim,BUY,A,2);assertEquals("treasury_full",q.result());
        assertFalse(sim.commitCommerce(q,A));assertEquals(before,market(sim));
    }
    WorldSim reload(WorldSim sim) {
        var loaded=WorldSim.fromBytes(sim.toBytes(),SimFixtures.graph(SEED),SimFixtures.data());
        for(var r:loaded.lifeResidents())loaded.activateLife(r.personId());
        return loaded;
    }
    @Test void seedAndRecruitmentAreOneTimeAndNeverCreatePeople() {
        var sim=fresh();assertEquals(4,sim.overview(24).population());var before=market(sim);
        assertFalse(sim.initializeCommerce(SITE));assertTrue(sim.staffCommerce(SITE).isEmpty());assertEquals(before,market(sim));
        var loaded=reload(sim);assertFalse(loaded.initializeCommerce(SITE));assertTrue(loaded.staffCommerce(SITE).isEmpty());
        assertEquals(before,market(loaded));
    }
    @Test void lastItemCanOnlyBeCommittedOnceAcrossTwoPlayers() {
        var sim=fresh();for(int i=0;i<3;i++)assertTrue(sim.commitCommerce(quote(sim,BUY,A),A));
        var alice=quote(sim,BUY,A);var bob=quote(sim,BUY,B);assertEquals(1,alice.available());
        assertFalse(sim.commitCommerce(alice,B),"a quote also binds its registered player");
        assertTrue(sim.commitCommerce(alice,A));assertFalse(sim.commitCommerce(bob,B));assertFalse(sim.commitCommerce(alice,A));
        assertEquals(0,market(sim).items().get(PILL));assertEquals(4,market(sim).transactions());
        assertEquals("out_of_stock",quote(sim,BUY,A).result());
    }
    @Test void saleUsesExistingTreasuryAndFiniteHerbStorage() {
        var sim=fresh();int before=sim.lifeSupply(SITE).orElseThrow().treasury();
        assertTrue(sim.commitCommerce(quote(sim,"sell_spirit_leaf",A),A));
        assertEquals(before-2,sim.lifeSupply(SITE).orElseThrow().treasury());
        assertEquals(1,sim.lifeSupply(SITE).orElseThrow().stock());
        assertTrue(sim.commitCommerce(quote(sim,BUY,A),A));
        assertEquals(before-2+8,sim.lifeSupply(SITE).orElseThrow().treasury());
    }
    @Test void shopClerkNeverManufacturesPillsEvenAfterPaidHerbDelivery() {
        var empty=fresh();var initial=market(empty).items();empty.advanceLife(420,DAY);
        assertEquals(0,empty.lifeSupply(SITE).orElseThrow().stock());assertEquals(0,empty.lifeSupply(SITE).orElseThrow().medicine());
        assertEquals(initial,market(empty).items(),"opening hours must not manufacture pills");
        var sim=fresh();assertTrue(sim.commitCommerce(quote(sim,"sell_spirit_leaf",A),A));
        sim.advanceLife(420,DAY);
        assertEquals(1,sim.lifeSupply(SITE).orElseThrow().stock());
        assertEquals(0,sim.lifeSupply(SITE).orElseThrow().medicine());
        assertEquals(initial,market(sim).items(),"herbalist wages never authorize production");
        assertEquals(1,sim.professionSupply(SITE).orElseThrow().herbs().get("myvillage:spirit_leaf"));
        var before=market(sim);sim.advanceLife(420,DAY);assertEquals(before,market(sim));
    }
    @Test void legacyMedicineAndClassifiedHerbsCannotMakeClerkAProducer() {
        var original=fresh();var root=JsonParser.parseString(new String(original.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        root.getAsJsonObject("life").getAsJsonObject("supplies").getAsJsonObject(SITE).addProperty("medicine",20);
        var sim=WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(SEED),SimFixtures.data());
        for(var r:sim.lifeResidents())sim.activateLife(r.personId());
        assertTrue(sim.commitCommerce(quote(sim,"sell_spirit_leaf",A),A));
        assertTrue(sim.commitCommerce(quote(sim,"sell_dew_blossom",A),A));
        var items=market(sim).items();sim.advanceLife(1220,DAY);
        assertEquals(items,market(sim).items());assertEquals(20,sim.lifeSupply(SITE).orElseThrow().medicine());
        assertEquals(2,sim.lifeSupply(SITE).orElseThrow().stock());
        assertEquals(0,sim.profession(worker(sim,"herbalist").personId()).orElseThrow().alchemyExperience());
    }
    @Test void removalOrCombatRejectsThePreviousLiveBody() {
        var sim=fresh();var r=worker(sim,"herbalist");var old=quote(sim,BUY,A);
        assertTrue(sim.releaseLife(r.personId(),r.epoch(),r.x(),r.y(),r.z()));assertFalse(sim.commitCommerce(old,A));
        long newEpoch=sim.activateLife(r.personId());assertTrue(newEpoch>r.epoch());assertFalse(sim.commitCommerce(old,A));
        var profile=new NpcCombatProfile(20,20,0,"{}",0,0,"",0,null);long combat=sim.claimCombat(r.personId(),profile);
        assertTrue(sim.engageCombat(r.personId(),combat,true));assertEquals("unavailable",quote(sim,BUY,A).result());
    }
    @Test void realProprietorDeathDoesNotRecruitAnotherPersonOrRefreshGoods() {
        var sim=fresh();var r=worker(sim,"smith");var before=market(sim);
        long epoch=sim.claimCombat(r.personId(),new NpcCombatProfile(20,20,0,"{}",0,0,"",0,null));
        assertTrue(sim.combatDeath(r.personId(),epoch,"environment:fall","","fall",40));
        assertTrue(sim.staffCommerce(SITE).isEmpty());assertEquals(before,market(sim));
        var restored=reload(sim);assertTrue(restored.staffCommerce(SITE).isEmpty());
        assertTrue(restored.lifeResidents().stream().noneMatch(p->p.role().equals("smith")));assertEquals(before,market(restored));
    }
    @Test void facilityDamageAndOffDutyFailClosedWithoutAReceipt() {
        var sim=fresh();var r=worker(sim,"herbalist");var old=quote(sim,BUY,A);var before=market(sim);
        sim.setLifePlaceAvailability(SITE,r.workId(),"blocked");assertEquals("off_duty",quote(sim,BUY,A).result());
        assertFalse(sim.commitCommerce(old,A));assertEquals(before,market(sim));
    }
    @Test void innCooldownSurvivesNormalSaveAndIsSharedPerPlayer() {
        var sim=fresh();String rest=sim.commerceRules().offers("innkeeper").getFirst().id();
        assertTrue(sim.commitCommerce(quote(sim,rest,A),A));assertEquals("rest_cooldown",quote(sim,rest,A).result());
        var loaded=reload(sim);assertEquals("rest_cooldown",quote(loaded,rest,A).result());
        assertTrue(quote(loaded,rest,B).ready());
        assertTrue(loaded.commitCommerce(quote(loaded,rest,B),B));assertEquals(2,market(loaded).transactions());
    }
    @Test void oldPayloadsStartEmptyButCurrentMissingStateIsRejected() {
        var sim=fresh();var root=JsonParser.parseString(new String(sim.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        root.remove("commerce");
        assertThrows(SimFormatException.class,()->WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(SEED),SimFixtures.data()));
        root.addProperty("version",5);
        var old=WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(SEED),SimFixtures.data());
        assertTrue(old.commerce(SITE).isEmpty());assertEquals(3,old.lifeResidents().size());
    }
    @Test void malformedBalancesAndFutureComponentFailWithoutReplenishment() {
        var sim=fresh();var root=JsonParser.parseString(new String(sim.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        var commerce=root.getAsJsonObject("commerce");commerce.getAsJsonObject("markets").getAsJsonObject(SITE).getAsJsonObject("items").addProperty(PILL,-1);
        assertThrows(SimFormatException.class,()->WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(SEED),SimFixtures.data()));
        commerce.getAsJsonObject("markets").getAsJsonObject(SITE).getAsJsonObject("items").addProperty(PILL,4);commerce.addProperty("schema",2);
        assertThrows(SimFormatException.class,()->WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(SEED),SimFixtures.data()));
    }
    @Test void pausedWorldDoesNotTrade() {
        var sim=fresh();sim.scheduler().setPaused(true);var before=market(sim);var q=quote(sim,BUY,A);
        assertEquals("paused",q.result());assertFalse(sim.commitCommerce(q,A));assertEquals(before,market(sim));
    }
    @Test void configurationRejectsNegativePricesAndUnknownFields() throws Exception {
        var source=Files.readString(Path.of("src/main/resources/data/myvillage/world_sim/commerce_rules.json"));
        var root=JsonParser.parseString(source).getAsJsonObject();root.getAsJsonArray("offers").get(0).getAsJsonObject().addProperty("price",-1);
        assertThrows(SimDataException.class,()->CommerceRules.parse("test",root));
        var extra=JsonParser.parseString(source).getAsJsonObject();extra.addProperty("typo",true);
        assertThrows(SimDataException.class,()->CommerceRules.parse("test",extra));
    }
    @Test void commodityUnitsMustMatchPhysicalQuantityIncludingHerbs() throws Exception {
        var source=Files.readString(Path.of("src/main/resources/data/myvillage/world_sim/commerce_rules.json"));
        var offers=JsonParser.parseString(source).getAsJsonObject().getAsJsonArray("offers");
        int checked=0;
        for(int i=0;i<offers.size();i++) {
            var original=offers.get(i).getAsJsonObject();
            if(original.get("kind").getAsString().equals("rest"))continue;
            var changed=JsonParser.parseString(source).getAsJsonObject();
            changed.getAsJsonArray("offers").get(i).getAsJsonObject()
                    .addProperty("units",original.get("quantity").getAsInt()+1);
            assertThrows(SimDataException.class,()->CommerceRules.parse("test",changed),original.get("id").getAsString());
            checked++;
        }
        assertEquals(offers.size()-1,checked);
    }
    @Test void herbOffersMustAgreeOnSharedCapacity() throws Exception {
        var source=Files.readString(Path.of("src/main/resources/data/myvillage/world_sim/commerce_rules.json"));
        var root=JsonParser.parseString(source).getAsJsonObject();
        int herbSeen=0;
        for(var value:root.getAsJsonArray("offers")) {
            var offer=value.getAsJsonObject();
            if(offer.get("pool").getAsString().equals("herbs")&&++herbSeen==2)
                offer.addProperty("capacity",offer.get("capacity").getAsInt()-1);
        }
        assertTrue(herbSeen>=2);
        assertThrows(SimDataException.class,()->CommerceRules.parse("test",root));
    }
}
