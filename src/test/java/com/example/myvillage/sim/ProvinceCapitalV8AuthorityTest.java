package com.example.myvillage.sim;

import com.example.myvillage.worldplan.*;
import com.example.myvillage.worldplan.capital.*;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.model.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Focused logic fixtures consume the exact R2 jar's frozen rules and production facade. */
class ProvinceCapitalV8AuthorityTest {
 static final long SEED=20261029;
 static final String PLAYER="00000000-0000-0000-0000-00000000000a";
 static WorldPlanRules rules() throws Exception {try(var in=WorldPlanRules.class.getResourceAsStream(WorldPlanRules.V8_RESOURCE)){return new WorldPlanRules(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8));}}
 static SimData.ResourceOpener opener(){return p->ProvinceCapitalV8AuthorityTest.class.getResourceAsStream("/"+p);}
 static SimData data(WorldPlanRules r){return WorldSim.loadData(r.capitals.lifeOpener(opener()));}
 static WorldSim empty(WorldPlan p,SimData d){var s=new WorldState();s.seed=SEED;s.tierId="small";s.genesisDaysPerYear=24;for(var x:p.graph().regions())s.regions.put(x.id(),new RegionState(x.id()));return WorldSim.fromBytes(StateCodec.toBytes(s,d.realms()),p.graph(),d);}
 static LifeSite sized(String region,int n){var a=new ArrayList<LifePlace>();for(int i=0;i<n;i++)a.add(LifePlace.free("p"+i,"rest",i,64,0));return new LifeSite("limit","fixture","minecraft:overworld",region,0,64,0,a);}
 static LifeSite city(CapitalCity c){var a=new ArrayList<LifePlace>();for(var el:c.bundle().life().getAsJsonArray("places")){var v=el.getAsJsonObject();var pos=v.getAsJsonArray("pos");var fp=v.getAsJsonArray("block_pos");String block=v.get("block").getAsString();a.add(new LifePlace(v.get("id").getAsString(),v.get("kind").getAsString(),c.x()+pos.get(0).getAsInt(),c.originY()+pos.get(1).getAsInt(),c.z()+pos.get(2).getAsInt(),v.get("capacity").getAsInt(),block,block.isEmpty()?0:c.x()+fp.get(0).getAsInt(),block.isEmpty()?0:c.originY()+fp.get(1).getAsInt(),block.isEmpty()?0:c.z()+fp.get(2).getAsInt()));}return new LifeSite(c.cityId(),c.bundle().displayName,"minecraft:overworld",c.regionId(),c.x(),c.originY(),c.z(),a);}
 static WorldSim restore(WorldSim s,WorldPlan p,SimData d){return WorldSim.fromBytes(s.toBytes(),p.graph(),d);}
 @Test void v8AndLegacyBudgetsAreEnforcedAtRegistrationAppendAndRestore() throws Exception {
  var r=rules();var p=new WorldPlan(SEED,r);var v8=data(r);var old=WorldSim.loadData(opener());var oldRules=new WorldPlanRules(new String(Objects.requireNonNull(WorldPlanRules.class.getResourceAsStream(WorldPlanRules.V7_RESOURCE)).readAllBytes(),StandardCharsets.UTF_8));assertNull(oldRules.capitals);var oldPlan=new WorldPlan(SEED,oldRules);
  for(var d:List.of(v8,old)){int n=d==v8?192:128;var plan=d==v8?p:oldPlan;String region=plan.graph().regions().getFirst().id();var s=empty(plan,d);assertTrue(s.registerLifeSite(sized(region,n)));assertFalse(s.registerLifeSite(sized(region,n)));byte[] before=s.toBytes();
   assertEquals("place_limit",assertThrows(IllegalArgumentException.class,()->s.addLifePlace("limit",LifePlace.free("overflow","rest",0,64,0))).getMessage());assertArrayEquals(before,s.toBytes());
   assertEquals(n,restore(s,plan,d).lifeSite("limit").orElseThrow().places().size());var fresh=empty(plan,d);byte[] clean=fresh.toBytes();assertEquals("place_limit",assertThrows(IllegalArgumentException.class,()->fresh.registerLifeSite(sized(region,n+1))).getMessage());assertArrayEquals(clean,fresh.toBytes());
   var root=JsonParser.parseString(new String(before,StandardCharsets.UTF_8)).getAsJsonObject();var places=root.getAsJsonObject("life").getAsJsonArray("sites").get(0).getAsJsonObject().getAsJsonArray("places");var extra=places.get(0).deepCopy().getAsJsonObject();extra.addProperty("id","overflow");places.add(extra);assertTrue(assertThrows(SimFormatException.class,()->WorldSim.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),plan.graph(),d)).getMessage().contains("too many places at limit"));
  }
 }
 static Person person(int id,String region){var p=new Person();p.id=id;p.surname="林";p.given="测"+id;p.regionId=region;p.homeRegionId=region;p.birthDay=-480;p.root=new int[]{2000,2000,2000,2000,2000};return p;}
 static WorldSim fixture(WorldPlan plan,SimData d,Map<Integer,Person> people){var s=new WorldState();s.seed=SEED;s.tierId="small";s.genesisDaysPerYear=24;for(var x:plan.graph().regions())s.regions.put(x.id(),new RegionState(x.id()));s.persons.putAll(people);s.nextPersonId=people.keySet().stream().mapToInt(x->x).max().orElse(0)+1;return WorldSim.fromBytes(StateCodec.toBytes(s,d.realms()),plan.graph(),d);}
 static void observe(WorldSim s,LifeSite site){for(var q:site.places())s.setLifePlaceAvailability(site.id(),q.id(),"available");}
 @Test void finiteCommerceIsIsolatedBetweenRealV8CityLayouts() throws Exception {
  var r=rules();var p=new WorldPlan(SEED,r);var d=data(r);var ca=p.cities().stream().filter(c->c.regionId().equals("zhongzhou")).findFirst().orElseThrow();var cb=p.cities().stream().filter(c->c.regionId().equals("donghai")).findFirst().orElseThrow();
  Map<Integer,Person> people=new LinkedHashMap<>();for(int i=1;i<=4;i++)people.put(i,person(i,ca.regionId()));for(int i=5;i<=8;i++)people.put(i,person(i,cb.regionId()));var s=fixture(p,d,people);
  for(var c:p.cities()){var site=city(c);assertTrue(s.registerLifeSite(site));observe(s,site);assertTrue(s.initializeCommerce(site.id()));s.staffCommerce(site.id());}
  s.registerPlayerActor(PLAYER,"fixture");s.observeLifeDaylight(.25);s.advanceLife(0,24000);for(var x:s.lifeResidents())s.activateLife(x.personId());s.advanceLife(20,24000);
  for(var x:s.lifeResidents()){var work=s.lifeSite(x.siteId()).orElseThrow().place(x.workId()).orElseThrow();assertTrue(s.checkpointLife(x.personId(),x.epoch(),work.x(),work.y(),work.z()));assertTrue(s.arriveLife(x.personId(),x.epoch(),x.revision()));}
  var markets=new LinkedHashMap<String,com.example.myvillage.sim.commerce.CommerceView>();for(var c:p.cities())markets.put(c.cityId(),s.commerce(c.cityId()).orElseThrow());
  var worker=s.lifeResidents().stream().filter(x->x.siteId().equals(ca.cityId())&&x.role().equals("herbalist")).findFirst().orElseThrow();var q=s.quoteCommerce(worker.personId(),worker.epoch(),"buy_pill_huiling",PLAYER);var stale=s.quoteCommerce(worker.personId(),worker.epoch(),"buy_pill_huiling",PLAYER);assertTrue(q.ready(),q.result());assertTrue(s.commitCommerce(q,PLAYER));assertFalse(s.commitCommerce(q,PLAYER));assertFalse(s.commitCommerce(stale,PLAYER));var after=s.commerce(ca.cityId()).orElseThrow();var before=markets.get(ca.cityId());assertEquals(before.items().get("myvillage:pill_huiling")-1,after.items().get("myvillage:pill_huiling"));assertEquals(before.treasury()+q.price(),after.treasury());assertEquals(before.transactions()+1,after.transactions());
  for(var c:p.cities())if(!c.cityId().equals(ca.cityId()))assertEquals(markets.get(c.cityId()),s.commerce(c.cityId()).orElseThrow());var copy=restore(s,p,d);assertFalse(copy.commitCommerce(q,PLAYER));assertFalse(copy.commitCommerce(stale,PLAYER));assertEquals(after,copy.commerce(ca.cityId()).orElseThrow());for(var c:p.cities()){assertFalse(copy.initializeCommerce(c.cityId()));assertEquals(c.cityId().equals(ca.cityId())?after:markets.get(c.cityId()),copy.commerce(c.cityId()).orElseThrow());}
 }
 @Test void moyuExistingResidentsAreRecruitedWithoutInventingPeople() throws Exception {
  var r=rules();var p=new WorldPlan(SEED,r);var d=data(r);var capital=p.cities().stream().filter(c->c.regionId().equals("moyu")).findFirst().orElseThrow();
  var genesis=WorldSim.genesis(SEED,p.graph(),d,"small",24);assertTrue(genesis.overview(24).population()>0);var generated=JsonParser.parseString(new String(genesis.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("persons");assertTrue(generated.asList().stream().map(JsonElement::getAsJsonObject).anyMatch(x->x.get("region").getAsString().equals("moyu") && x.get("sect").getAsInt()<0),"V8 Genesis must include an existing Moyu rogue for this seed");
  Map<Integer,Person> people=new LinkedHashMap<>();people.put(1,person(1,"moyu"));people.put(2,person(2,"moyu"));var traveling=person(3,"moyu");traveling.status="travelling";traveling.statusUntil=2;people.put(3,traveling);people.put(4,person(4,"donghai"));people.put(5,person(5,"moyu"));
  var s=fixture(p,d,people);var other=new LifeSite("occupied","occupied","minecraft:overworld","moyu",0,64,0,List.of(LifePlace.free("home","rest",0,64,0),LifePlace.free("work","work",1,64,0)));assertTrue(s.registerLifeSite(other));assertTrue(s.assignLife(5,"occupied","rogue","home","work"));var site=city(capital);assertTrue(s.registerLifeSite(site));observe(s,site);assertTrue(s.initializeCommerce(site.id()));int population=s.overview(24).population();var hired=s.staffCommerce(site.id());assertEquals(Set.of(1,2),new HashSet<>(hired));assertEquals(population,s.overview(24).population());assertTrue(s.staffCommerce(site.id()).isEmpty());assertEquals("occupied",s.life(5).orElseThrow().siteId());assertTrue(s.life(3).isEmpty());assertTrue(s.life(4).isEmpty());assertEquals(-1,s.staffTownGuide(site.id(),"guest_west","inn_social"));
  var copy=restore(s,p,d);assertEquals(population,copy.overview(24).population());assertTrue(copy.staffCommerce(site.id()).isEmpty());assertEquals(-1,copy.staffTownGuide(site.id(),"guest_west","inn_social"));
 }
 @Test void moyuGuideDeathDoesNotCreateAReplacement() throws Exception {
  var r=rules();var p=new WorldPlan(SEED,r);var d=data(r);var c=p.cities().stream().filter(x->x.regionId().equals("moyu")).findFirst().orElseThrow();var s=fixture(p,d,Map.of(1,person(1,"moyu"),2,person(2,"moyu")));var site=city(c);assertTrue(s.registerLifeSite(site));observe(s,site);int guide=s.staffTownGuide(site.id(),"guest_west","inn_social");assertTrue(guide>0);assertEquals(guide,s.staffTownGuide(site.id(),"guest_west","inn_social"));
  var profile=new com.example.myvillage.sim.combat.NpcCombatProfile(20,20,0,"",0,0,"",0,null);long lease=s.claimCombat(guide,profile);assertTrue(lease>0);assertTrue(s.combatDeath(guide,lease,"environment:fall","","fall",0));int population=s.overview(24).population();var copy=restore(s,p,d);assertEquals(guide,copy.staffTownGuide(site.id(),"guest_west","inn_social"));assertTrue(copy.life(guide).isEmpty());assertEquals(population,copy.overview(24).population());assertTrue(copy.lifeResidents().isEmpty());
 }
 @Test void moyuMerchantDeathLeavesThePreviouslyStaffedRoleVacant() throws Exception {
  var r=rules();var p=new WorldPlan(SEED,r);var d=data(r);var c=p.cities().stream().filter(x->x.regionId().equals("moyu")).findFirst().orElseThrow();
  var s=fixture(p,d,Map.of(1,person(1,"moyu"),2,person(2,"moyu"),3,person(3,"moyu"),4,person(4,"moyu")));var site=city(c);assertTrue(s.registerLifeSite(site));observe(s,site);assertTrue(s.initializeCommerce(site.id()));assertEquals(3,s.staffCommerce(site.id()).size());var victim=s.lifeResidents().getFirst();String role=victim.role();var profile=new com.example.myvillage.sim.combat.NpcCombatProfile(20,20,0,"",0,0,"",0,null);long lease=s.claimCombat(victim.personId(),profile);assertTrue(lease>0);assertTrue(s.combatDeath(victim.personId(),lease,"environment:fall","","fall",0));int population=s.overview(24).population();assertTrue(s.staffCommerce(site.id()).isEmpty());assertTrue(s.lifeResidents().stream().noneMatch(x->x.role().equals(role)));assertEquals(population,s.overview(24).population());var copy=restore(s,p,d);assertTrue(copy.staffCommerce(site.id()).isEmpty());assertTrue(copy.lifeResidents().stream().noneMatch(x->x.role().equals(role)));assertEquals(population,copy.overview(24).population());
 }
 @Test void existingLegacyYunshanSiteAndWarehouseAreNotOverwrittenByV8Capitals() throws Exception {
  var r=rules();var p=new WorldPlan(SEED,r);var d=data(r);var s=empty(p,d);var region=p.cities().getFirst().regionId();var legacy=new LifeSite("yunshan","旧云山夹具","minecraft:overworld",region,11,64,11,List.of(LifePlace.free("legacy_home","rest",11,64,11)));assertTrue(s.registerLifeSite(legacy));assertTrue(s.initializeCommerce("yunshan"));var stock=s.commerce("yunshan").orElseThrow();
  for(var c:p.cities()){var site=city(c);assertTrue(s.registerLifeSite(site));assertTrue(s.initializeCommerce(site.id()));}assertEquals(legacy,s.lifeSite("yunshan").orElseThrow());assertEquals(stock,s.commerce("yunshan").orElseThrow());var copy=restore(s,p,d);assertEquals(legacy,copy.lifeSite("yunshan").orElseThrow());assertEquals(stock,copy.commerce("yunshan").orElseThrow());assertFalse(copy.initializeCommerce("yunshan"));
 }
}
