package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.sim.combat.NpcCombatProfile;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.model.RegionState;
import com.example.myvillage.sim.model.StateCodec;
import com.example.myvillage.sim.model.WorldState;
import com.example.myvillage.story.npc.QianzhenActorDefinitions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** Offline ledger assertions only: no claim of native entity/pathfinding or multiplayer proof. */
class WorldSimStoryActorsTest {
    private static final long SEED = 42;
    private static final int DAY = 24_000;

    private QianzhenActorDefinitions defs() throws Exception {
        return QianzhenActorDefinitions.read(JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/myvillage/story/qianzhen_actors.json"))).getAsJsonObject());
    }
    private WorldSim fresh() {
        WorldState state = new WorldState();
        state.seed = SEED; state.tierId = "small"; state.genesisDaysPerYear = 24; state.day = 1;
        for (var r : SimFixtures.graph(SEED).regions()) state.regions.put(r.id(), new RegionState(r.id()));
        return WorldSim.fromBytes(StateCodec.toBytes(state, SimFixtures.data().realms()), SimFixtures.graph(SEED), SimFixtures.data());
    }
    private StoryCastDefinition cast() throws Exception {
        var defs = defs();
        var places = new ArrayList<LifePlace>();
        int x = 0;
        for (var actor : defs.actors()) {
            places.add(LifePlace.free(actor.homeId(), "rest", x, 64, 0));
            places.add(LifePlace.free(actor.initialPlaceId(), "social", x, 64, 0));
            if (!actor.returnPlaceId().isEmpty()) places.add(LifePlace.free(actor.returnPlaceId(), "social", 20, 64, 0));
            x += 2;
        }
        var site = new LifeSite(defs.siteId(), defs.siteName(), "minecraft:overworld",
                SimFixtures.graph(SEED).regions().getFirst().id(), 0, 64, 0, places);
        return new StoryCastDefinition(defs.storyId(), site, defs.actors());
    }
    @Test void privateRealmCastsKeepIdentityAndRescueSeparateAcrossRestart() throws Exception {
        var sim=fresh();var base=cast();var site=base.site();
        var a=java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        var b=java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
        var casts=new java.util.ArrayList<StoryCastDefinition>();
        for(var owner:java.util.List.of(a,b)) {
            String id=com.example.myvillage.story.realm.RealmIdentity.cast(owner);
            var ownSite=new LifeSite(id,site.name(),com.example.myvillage.story.realm.RealmIdentity.dimension(owner),
                    site.regionId(),site.anchorX(),site.anchorY(),site.anchorZ(),site.places());
            var own=new StoryCastDefinition(id,ownSite,base.actors());casts.add(own);assertTrue(sim.ensureStoryCast(own));
        }
        var first=sim.storyActor(casts.get(0).storyId(),"zhou_he").orElseThrow();
        var second=sim.storyActor(casts.get(1).storyId(),"zhou_he").orElseThrow();
        assertNotEquals(first.personId(),second.personId());
        assertTrue(sim.returnStoryActor(first.storyId(),"zhou_he",0));
        assertFalse(sim.storyActor(second.storyId(),"zhou_he").orElseThrow().returned());
        var restored=restore(sim);
        for(var own:casts)assertFalse(restored.ensureStoryCast(own));
        assertEquals(first.personId(),restored.storyActor(first.storyId(),"zhou_he").orElseThrow().personId());
        assertTrue(restored.storyActor(first.storyId(),"zhou_he").orElseThrow().returned());
        assertEquals(second,restored.storyActor(second.storyId(),"zhou_he").orElseThrow());
        assertEquals(8,restored.lifeResidents().size());
    }
    @Test void ordinaryWorldQueriesHideForeignRealmPeopleBeforeTheSearchCap() throws Exception {
        var sim=fresh();var base=cast();var site=base.site();
        var owners=new java.util.ArrayList<java.util.UUID>();
        for(int n=1;n<=12;n++) {
            var owner=new java.util.UUID(0,n);owners.add(owner);
            String id=com.example.myvillage.story.realm.RealmIdentity.cast(owner);
            var ownSite=new LifeSite(id,site.name(),com.example.myvillage.story.realm.RealmIdentity.dimension(owner),
                    site.regionId(),site.anchorX(),site.anchorY(),site.anchorZ(),site.places());
            assertTrue(sim.ensureStoryCast(new StoryCastDefinition(id,ownSite,base.actors())));
        }
        var viewer=owners.getLast();String ownCast=com.example.myvillage.story.realm.RealmIdentity.cast(viewer);
        int ownGuide=sim.storyActor(ownCast,"guide").orElseThrow().personId();
        int foreignGuide=sim.storyActor(com.example.myvillage.story.realm.RealmIdentity.cast(owners.getFirst()),"guide").orElseThrow().personId();
        java.util.function.Function<com.example.myvillage.sim.runtime.net.WorldSimQuery,com.example.myvillage.sim.runtime.net.WorldSimSnapshot> query=
                q->com.example.myvillage.sim.runtime.net.WorldSimSnapshots.build(sim,24,1,false,0,id->id,
                        java.util.Optional.of(site.regionId()),0,0,viewer.toString(),q);
        var search=query.apply(com.example.myvillage.sim.runtime.net.WorldSimQuery.personSearch("程砚"));
        assertEquals(java.util.List.of(ownGuide),search.persons().stream().map(p->p.id()).toList(),
                "Earlier foreign names must not exhaust the search cap before the owner's own actor");
        assertNull(query.apply(com.example.myvillage.sim.runtime.net.WorldSimQuery.person(foreignGuide)).person());
        assertNotNull(query.apply(com.example.myvillage.sim.runtime.net.WorldSimQuery.person(ownGuide)).person());
        var here=query.apply(new com.example.myvillage.sim.runtime.net.WorldSimQuery(com.example.myvillage.sim.runtime.net.WorldSimQuery.Kind.HERE,-1,""));
        assertEquals(4,here.persons().size());
        assertTrue(here.persons().stream().allMatch(p->sim.storyActorForPerson(p.id()).orElseThrow().storyId().equals(ownCast)));
        long epoch=sim.claimCombat(foreignGuide,profile(null));assertTrue(sim.combatDeath(foreignGuide,epoch,"","","generic",0));
        var loaded=restore(sim);
        assertNull(com.example.myvillage.sim.runtime.net.WorldSimSnapshots.build(loaded,24,1,false,0,id->id,
                java.util.Optional.empty(),0,0,viewer.toString(),com.example.myvillage.sim.runtime.net.WorldSimQuery.person(foreignGuide)).person());
        var outsider=com.example.myvillage.sim.runtime.net.WorldSimSnapshots.build(loaded,24,1,false,0,id->id,
                java.util.Optional.empty(),0,0,new java.util.UUID(0,99).toString(),com.example.myvillage.sim.runtime.net.WorldSimQuery.personSearch("程砚"));
        assertTrue(outsider.persons().isEmpty());
        var chronicle=com.example.myvillage.sim.runtime.net.WorldSimSnapshots.build(loaded,24,1,false,0,id->id,
                java.util.Optional.empty(),0,0,new java.util.UUID(0,99).toString(),
                new com.example.myvillage.sim.runtime.net.WorldSimQuery(com.example.myvillage.sim.runtime.net.WorldSimQuery.Kind.CHRONICLE,-1,""));
        assertTrue(chronicle.events().isEmpty());assertTrue(chronicle.causes().isEmpty());
    }
    private WorldSim actors() throws Exception {
        var sim = fresh(); assertTrue(sim.ensureStoryCast(cast())); return sim;
    }
    private JsonObject json(WorldSim sim) {
        return JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private WorldSim restore(JsonObject json) {
        return WorldSim.fromBytes(json.toString().getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED), SimFixtures.data());
    }
    private WorldSim restore(WorldSim sim) { return restore(json(sim)); }
    private int zhou(WorldSim sim) throws Exception { return sim.storyActor(defs().storyId(), "zhou_he").orElseThrow().personId(); }
    private NpcCombatProfile profile(NpcCombatProfile.Body body) {
        return new NpcCombatProfile(15, 20, 80, "", 0, 1, "", 0, body);
    }

    @Test void v7MigratesWithEmptyBindingsAndV8RequiresExplicitBindings() throws Exception {
        var legacy = json(fresh());
        legacy.addProperty("version", 7); legacy.getAsJsonObject("life").remove("story_actors");
        var migrated = restore(legacy);
        assertEquals(0, migrated.lifeResidents().size());
        assertEquals(StateCodec.VERSION, json(migrated).get("version").getAsInt());
        assertTrue(json(migrated).getAsJsonObject("life").getAsJsonArray("story_actors").isEmpty());
        var malformed = json(fresh()); malformed.getAsJsonObject("life").remove("story_actors");
        assertThrows(SimFormatException.class, () -> restore(malformed));
        malformed.getAsJsonObject("life").addProperty("story_actors", false);
        assertThrows(SimFormatException.class, () -> restore(malformed));
        assertTrue(migrated.ensureStoryCast(cast()));
        assertFalse(migrated.ensureStoryCast(cast()));
    }

    @Test void bindingIsIdempotentAndByteStableIncludingAfterRestore() throws Exception {
        var sim = actors();
        byte[] bytes = sim.toBytes();
        assertFalse(sim.ensureStoryCast(cast())); assertArrayEquals(bytes, sim.toBytes());
        var loaded = restore(sim); assertArrayEquals(bytes, loaded.toBytes());
        assertFalse(loaded.ensureStoryCast(cast())); assertArrayEquals(bytes, loaded.toBytes());
        assertEquals(4, sim.lifeResidents().size());
        for (var actor : defs().actors()) {
            var b = sim.storyActor(defs().storyId(), actor.key()).orElseThrow();
            var p = sim.person(b.personId()).orElseThrow();
            assertEquals(actor.surname() + actor.given(), p.name());
            assertEquals(actor.gender(), p.gender()); assertEquals("qi_refining", p.realmId());
            assertEquals(actor.stage(), p.stage()); assertTrue(sim.storyDamageProtected(p.id()));
            var place = cast().site().place(actor.initialPlaceId()).orElseThrow();
            assertEquals(place.x(), sim.life(p.id()).orElseThrow().x());
            assertThrows(IllegalArgumentException.class, () -> sim.unassignLife(p.id()));
        }
    }

    @Test void scriptedOnlySkipsBreakthroughOldAgeTravelAndAllReceptionEarnings() throws Exception {
        var sim = actors(); var altered = json(sim);
        for (var p : altered.getAsJsonArray("persons")) {
            p.getAsJsonObject().addProperty("birth", -1_000_000);
            p.getAsJsonObject().addProperty("progress", 1_000_000);
        }
        sim = restore(altered);
        for (var place : cast().site().places()) sim.setLifePlaceAvailability(cast().site().id(), place.id(), "available");
        sim.observeLifeDaylight(.9); sim.advanceLife(0, DAY);
        var supply = sim.lifeSupply(cast().site().id()).orElseThrow();
        for (int t = 20; t <= DAY * 2; t += 20) sim.advanceLife(t, DAY);
        for (int day = 0; day < 80; day++) sim.step(24);
        assertEquals(supply, sim.lifeSupply(cast().site().id()).orElseThrow());
        for (var actor : defs().actors()) {
            var b = sim.storyActor(defs().storyId(), actor.key()).orElseThrow();
            var p = sim.person(b.personId()).orElseThrow(); var life = sim.life(p.id()).orElseThrow();
            assertTrue(p.alive()); assertEquals(actor.stage(), p.stage());
            assertEquals("qi_refining", p.realmId()); assertEquals(1_000_000, p.progress());
            assertEquals("social", life.activity()); assertEquals(actor.initialPlaceId(), life.targetId());
            assertEquals("working", life.phase()); assertEquals(0, life.herbs()); assertEquals(0, life.coins());
            assertEquals(0, life.fatigue()); assertEquals(0, life.completedActivities());
        }
    }

    @Test void tombstoneBindingNeverCreatesReplacementEvenOnRepeatedStartup() throws Exception {
        var sim = actors(); int id = zhou(sim);
        long epoch = sim.claimCombat(id, profile(null));
        assertTrue(sim.combatDeath(id, epoch, "", "", "generic", 0));
        assertFalse(sim.person(id).orElseThrow().alive());
        byte[] before = sim.toBytes();
        assertFalse(sim.ensureStoryCast(cast())); assertArrayEquals(before, sim.toBytes());
        var loaded = restore(sim); assertFalse(loaded.ensureStoryCast(cast()));
        assertEquals(id, zhou(loaded)); assertEquals(3, loaded.lifeResidents().size());
        assertFalse(loaded.returnStoryActor(defs().storyId(), "zhou_he", 0));
    }

    @Test void rescueKeepsPersonAndHealthAndRejectsOldLifeAndCombatEpochs() throws Exception {
        var sim = actors(); int id = zhou(sim);
        long lifeEpoch = sim.activateLife(id), combatEpoch = sim.claimCombat(id, profile(null));
        assertFalse(sim.returnStoryActor(defs().storyId(), "zhou_he", 0));
        var life = sim.life(id).orElseThrow();
        assertTrue(sim.releaseLife(id, lifeEpoch, life.x(), life.y(), life.z()));
        assertFalse(sim.returnStoryActor(defs().storyId(), "zhou_he", 0), "combat lease still owns body");
        assertTrue(sim.releaseCombat(id, combatEpoch));
        assertFalse(sim.returnStoryActor(defs().storyId(), "zhou_he", 99), "stale actor revision");
        assertTrue(sim.returnStoryActor(defs().storyId(), "zhou_he", 0));
        assertEquals(id, zhou(sim)); assertTrue(sim.storyActor(defs().storyId(), "zhou_he").orElseThrow().returned());
        assertEquals(20, sim.life(id).orElseThrow().x()); assertEquals(15, sim.combatProfile(id).orElseThrow().health());
        assertFalse(sim.checkpointLife(id, lifeEpoch, -90, 64, 0));
        long newLife = sim.activateLife(id), newCombat = sim.claimCombat(id, profile(null));
        assertTrue(newLife > lifeEpoch); assertTrue(newCombat > combatEpoch);
        assertFalse(sim.checkpointLife(id, lifeEpoch, -90, 64, 0));
        assertFalse(sim.checkpointCombat(id, combatEpoch, new NpcCombatProfile(1,20,80,"",0,9,"",0,null)));
        assertFalse(sim.combatDeath(id, combatEpoch, "", "", "generic", 0));
        assertFalse(sim.releaseLife(id, lifeEpoch, -90,64,0)); assertFalse(sim.releaseCombat(id, combatEpoch));
        assertTrue(sim.releaseLife(id,newLife,20,64,0)); assertTrue(sim.releaseCombat(id,newCombat));
        byte[] bytes = sim.toBytes();
        assertTrue(sim.returnStoryActor(defs().storyId(), "zhou_he", 0));
        assertFalse(sim.ensureStoryCast(cast())); assertArrayEquals(bytes,sim.toBytes());
        var loaded = restore(sim); assertEquals(id,zhou(loaded));
        assertTrue(loaded.storyActor(defs().storyId(), "zhou_he").orElseThrow().returned());
        assertEquals(20,loaded.life(id).orElseThrow().x());
    }

    @Test void pendingFallOrFireCannotBeSilentlyClearedByRescue() throws Exception {
        for (var body : new NpcCombatProfile.Body[] {
                new NpcCombatProfile.Body("minecraft:overworld", cast().site().regionId(),4,68,0,0,-.5,0,4,0,false),
                new NpcCombatProfile.Body("minecraft:overworld", cast().site().regionId(),4,64,0,0,0,0,0,20,true) }) {
            var sim = actors(); int id = zhou(sim);
            long epoch = sim.claimCombat(id,profile(body)); assertTrue(sim.releaseCombat(id,epoch));
            byte[] bytes = sim.toBytes();
            assertFalse(sim.returnStoryActor(defs().storyId(),"zhou_he",0));
            assertArrayEquals(bytes,sim.toBytes());
        }
    }

    @Test void corruptedBindingMissingIdentityAndDuplicatePersonAreRefused() throws Exception {
        var sim = actors(); var missing = json(sim);
        missing.getAsJsonObject("life").getAsJsonArray("story_actors").get(0).getAsJsonObject().addProperty("person_id",999999);
        assertThrows(SimFormatException.class, () -> restore(missing));
        var duplicate = json(sim); var list = duplicate.getAsJsonObject("life").getAsJsonArray("story_actors");
        list.get(1).getAsJsonObject().addProperty("person_id",list.get(0).getAsJsonObject().get("person_id").getAsInt());
        assertThrows(SimFormatException.class, () -> restore(duplicate));
    }

    @Test void changedSiteIsRefusedWithoutWritingAndMissingLiveResidentIsNotRecreated() throws Exception {
        var sim = actors(); var site = cast().site();
        var moved = new LifeSite(site.id(),site.name(),site.dimension(),site.regionId(),1,64,0,site.places());
        byte[] before = sim.toBytes();
        assertThrows(IllegalArgumentException.class, () -> sim.ensureStoryCast(new StoryCastDefinition(cast().storyId(),moved,cast().actors())));
        assertArrayEquals(before,sim.toBytes());
        var broken = json(sim); broken.getAsJsonObject("life").getAsJsonArray("residents").remove(0);
        assertThrows(SimFormatException.class, () -> restore(broken));
    }

    @Test void authoredDefinitionRejectsUnknownPolicyIllegalRealmAndNonIntegralAge() throws Exception {
        var path = Path.of("src/main/resources/data/myvillage/story/qianzhen_actors.json");
        for (String key : new String[]{"realm","policy","age_years"}) {
            var input = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var actor = input.getAsJsonArray("actors").get(0).getAsJsonObject();
            if (key.equals("age_years")) actor.addProperty(key,18.5); else actor.addProperty(key,"mortal");
            assertThrows(IllegalArgumentException.class, () -> QianzhenActorDefinitions.read(input));
        }
    }
    @Test void duplicateJsonKeysUnknownFieldsAndInvalidReferencesAreRejectedAtLoad() throws Exception {
        assertThrows(SimDataException.class, () -> QianzhenActorDefinitions.read(
                new java.io.StringReader("{\"schema\":1,\"schema\":1}")));
        var input = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/myvillage/story/qianzhen_actors.json"))).getAsJsonObject();
        input.addProperty("unexpected", 1);
        assertThrows(IllegalArgumentException.class, () -> QianzhenActorDefinitions.read(input));
        input.remove("unexpected");
        input.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("initial_place_id", "invented");
        assertThrows(IllegalArgumentException.class, () -> QianzhenActorDefinitions.read(input));
    }

    @Test void realV7ResidentIdentitySurvivesAddingTheNewStoryCast() throws Exception {
        var state = StateCodec.fromBytes(fresh().toBytes(), SimFixtures.data().realms());
        var person = new com.example.myvillage.sim.model.Person();
        person.id=17; person.surname="旧"; person.given="居民"; person.birthDay=-480;
        person.regionId=SimFixtures.graph(SEED).regions().getFirst().id(); person.homeRegionId=person.regionId;
        person.root=new int[]{2000,2000,2000,2000,2000}; state.persons.put(person.id,person); state.nextPersonId=18;
        var oldSite = new LifeSite("old_town","旧镇","minecraft:overworld",person.regionId,0,64,0,
                java.util.List.of(LifePlace.free("home","rest",0,64,0),LifePlace.free("desk","social",1,64,0)));
        state.life.sites.put(oldSite.id(),oldSite); state.life.supplies.put(oldSite.id(),new LifeState.Supply());
        var resident = new LifeResident(); resident.personId=person.id; resident.siteId=oldSite.id();
        resident.role="visitor"; resident.homeId="home"; resident.workId="desk"; resident.y=64; resident.coins=13;
        state.life.residents.put(person.id,resident);
        var legacy = StateCodec.toJson(state,SimFixtures.data().realms());
        legacy.addProperty("version",7); legacy.getAsJsonObject("life").remove("story_actors");
        var migrated = restore(legacy); var oldPerson = migrated.person(17).orElseThrow(); var oldLife=migrated.life(17).orElseThrow();
        assertFalse(migrated.storyDamageProtected(17)); assertTrue(migrated.ensureStoryCast(cast()));
        assertEquals(oldPerson,migrated.person(17).orElseThrow()); assertEquals(oldLife,migrated.life(17).orElseThrow());
        assertEquals(5,migrated.lifeResidents().size()); assertTrue(zhou(migrated)>17);
        var again=restore(migrated); assertEquals(oldPerson,again.person(17).orElseThrow());
        assertFalse(again.ensureStoryCast(cast())); assertEquals(5,again.lifeResidents().size());
    }

    @Test void preflightFailureDoesNotPartiallyRegisterSiteOrAllocateIdentities() throws Exception {
        var sim=fresh();var cast=cast();var site=cast.site();var places=new ArrayList<>(site.places());
        for(int i=0;i<130;i++)places.add(LifePlace.free("extra_"+i,"social",i,64,2));
        var overfull=new LifeSite(site.id(),site.name(),site.dimension(),site.regionId(),0,64,0,places);
        byte[] before=sim.toBytes();
        assertThrows(IllegalArgumentException.class,()->sim.validateStoryCast(new StoryCastDefinition(cast.storyId(),overfull,cast.actors())));
        assertArrayEquals(before,sim.toBytes());
        assertThrows(IllegalArgumentException.class,()->sim.ensureStoryCast(new StoryCastDefinition(cast.storyId(),overfull,cast.actors())));
        assertArrayEquals(before,sim.toBytes()); assertTrue(sim.lifeSites().isEmpty());
        assertEquals(0,sim.overview(24).population());
    }

    @Test void validationOfFreshBoundReturnedAndDeadCastsNeverWrites() throws Exception {
        var sim = fresh(); byte[] before = sim.toBytes();
        sim.validateStoryCast(cast()); sim.validateStoryCast(cast());
        assertArrayEquals(before, sim.toBytes()); assertTrue(sim.lifeSites().isEmpty());
        assertTrue(sim.ensureStoryCast(cast()));
        before = sim.toBytes(); sim.validateStoryCast(cast()); assertArrayEquals(before, sim.toBytes());
        assertFalse(sim.ensureStoryCast(cast())); assertArrayEquals(before, sim.toBytes());
        assertTrue(sim.returnStoryActor(defs().storyId(), "zhou_he", 0));
        before = sim.toBytes(); sim.validateStoryCast(cast()); assertArrayEquals(before, sim.toBytes());
        assertFalse(sim.ensureStoryCast(cast())); assertArrayEquals(before, sim.toBytes());
        int id = zhou(sim); long epoch = sim.claimCombat(id, profile(null));
        assertTrue(sim.combatDeath(id, epoch, "", "", "generic", 0));
        before = sim.toBytes(); sim.validateStoryCast(cast()); assertArrayEquals(before, sim.toBytes());
        assertFalse(sim.ensureStoryCast(cast())); assertArrayEquals(before, sim.toBytes());
    }

    @Test void validationRefusesUnknownRegionAndSiteCapacityWithoutWriting() throws Exception {
        var sim = fresh(); var cast = cast(); var site = cast.site();
        var unknown = new LifeSite(site.id(), site.name(), site.dimension(), "missing_region",
                site.anchorX(), site.anchorY(), site.anchorZ(), site.places());
        byte[] before = sim.toBytes();
        var bad = new StoryCastDefinition(cast.storyId(), unknown, cast.actors());
        assertEquals("unknown_story_region", assertThrows(IllegalArgumentException.class,
                () -> sim.validateStoryCast(bad)).getMessage());
        assertArrayEquals(before, sim.toBytes());
        for (int n = 0; n < sim.lifeRules().maxSites(); n++)
            sim.registerLifeSite(new LifeSite("occupied_" + n, "旧地点", site.dimension(), site.regionId(),
                    0, 64, 0, java.util.List.of()));
        before = sim.toBytes();
        assertEquals("story_site_limit", assertThrows(IllegalArgumentException.class,
                () -> sim.validateStoryCast(cast)).getMessage());
        assertArrayEquals(before, sim.toBytes());
        assertThrows(IllegalArgumentException.class, () -> sim.ensureStoryCast(cast));
        assertArrayEquals(before, sim.toBytes());
    }

    private com.example.myvillage.sim.engine.SimContext context(WorldSim sim) {
        return new com.example.myvillage.sim.engine.SimContext(
                StateCodec.fromBytes(sim.toBytes(), SimFixtures.data().realms()),
                SimFixtures.data(), SimFixtures.graph(SEED), 24);
    }
    private void rejectedWithoutWrites(com.example.myvillage.sim.engine.SimContext ctx,
                                       StoryCastDefinition cast, String reason) {
        byte[] before = StateCodec.toBytes(ctx.state, ctx.realms);
        var names = java.util.Set.copyOf(ctx.usedNames());
        assertEquals(reason, assertThrows(RuntimeException.class, () -> StoryActors.preflight(ctx, cast)).getMessage());
        assertArrayEquals(before, StateCodec.toBytes(ctx.state, ctx.realms)); assertEquals(names, ctx.usedNames());
        assertEquals(reason, assertThrows(RuntimeException.class, () -> StoryActors.ensure(ctx, cast)).getMessage());
        assertArrayEquals(before, StateCodec.toBytes(ctx.state, ctx.realms)); assertEquals(names, ctx.usedNames());
    }

    @Test void preflightAndEnsureRejectTheSameDamagedBindingsWithoutRepair() throws Exception {
        var cast = cast(); var ctx = context(actors());
        var binding = ctx.state.life.storyActors.firstEntry().getValue();
        ctx.state.persons.remove(binding.personId());
        rejectedWithoutWrites(ctx, cast, "story_identity_missing");
        ctx = context(actors()); ctx.state.life.storyActors.pollFirstEntry();
        rejectedWithoutWrites(ctx, cast, "partial_story_cast");
        ctx = context(actors()); binding = ctx.state.life.storyActors.firstEntry().getValue();
        ctx.state.persons.get(binding.personId()).surname = "变";
        rejectedWithoutWrites(ctx, cast, "story_identity_conflict:" + binding.actorKey());
        var site = cast.site();
        var moved = new LifeSite(site.id(), site.name(), site.dimension(), site.regionId(), 1, 64, 0, site.places());
        rejectedWithoutWrites(context(actors()), new StoryCastDefinition(cast.storyId(), moved, cast.actors()),
                "story_site_already_bound");
    }

    @Test void preflightIncludesResidentHomeGuideAndAllocationLimits() throws Exception {
        var cast = cast(); var site = cast.site();
        var ctx = context(fresh()); var places = new ArrayList<>(site.places());
        int count = ctx.data.lifeRules().maxResidentsPerSite() - cast.actors().size() + 1;
        for (int n = 0; n < count; n++) {
            String home = "old_home_" + n; places.add(LifePlace.free(home, "rest", n, 64, 1));
            var p = new com.example.myvillage.sim.model.Person(); p.id = ctx.state.nextPersonId++;
            p.surname = "旧"; p.given = "居民" + n; p.regionId = site.regionId(); p.homeRegionId = site.regionId();
            ctx.state.persons.put(p.id, p);
            var r = new LifeResident(); r.personId = p.id; r.siteId = site.id(); r.role = "visitor";
            r.homeId = home; r.workId = cast.actors().getFirst().initialPlaceId(); r.y = 64;
            ctx.state.life.residents.put(p.id, r);
        }
        var expanded = new LifeSite(site.id(), site.name(), site.dimension(), site.regionId(), 0, 64, 0, places);
        LifeEngine.registerSite(ctx, expanded);
        rejectedWithoutWrites(ctx, new StoryCastDefinition(cast.storyId(), expanded, cast.actors()), "story_resident_limit");
        // Existing residents can conflict even below the site's overall limit.
        ctx = context(actors()); ctx.state.life.storyActors.clear();
        rejectedWithoutWrites(ctx, cast, "story_home_full");
        ctx = context(fresh()); ctx.state.life.guideAssignments.put(site.id(), 99);
        rejectedWithoutWrites(ctx, cast, "story_guide_conflict");
        ctx = context(fresh()); ctx.state.nextPersonId = 0;
        rejectedWithoutWrites(ctx, cast, "invalid_next_person_id");
        ctx = context(fresh()); ctx.state.persons.put(ctx.state.nextPersonId, new com.example.myvillage.sim.model.Person());
        rejectedWithoutWrites(ctx, cast, "story_person_id_collision");
    }

}
