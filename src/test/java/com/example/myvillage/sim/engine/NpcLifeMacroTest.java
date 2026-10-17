package com.example.myvillage.sim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.cli.SimCli;
import com.example.myvillage.sim.life.LifeResident;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.RegionState;
import com.example.myvillage.sim.model.Relation;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.SectRelation;
import com.example.myvillage.sim.model.WorldState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Town residents keep one executor, whether or not their physical avatar is present. */
class NpcLifeMacroTest {
    private static final long SEED = 21;
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static RegionGraph graph;
    private static SimData data;

    @BeforeAll
    static void world() throws IOException {
        graph = SimCli.buildGraph(Path.of("."), SEED);
        data = load(rules -> {});
    }

    /** Real data through the normal loader, with certain nonlethal encounters for focused tests. */
    private static SimData load(Consumer<JsonObject> patch) throws IOException {
        JsonObject rules = JsonParser.parseString(Files.readString(
                RESOURCES.resolve(SimData.DIRECTORY + "rules.json"))).getAsJsonObject();
        JsonObject combat = rules.getAsJsonObject("combat");
        for (String key : List.of("kill_base", "kill_enemy", "kill_war", "flee_base")) {
            combat.addProperty(key, 0);
        }
        rules.getAsJsonObject("succession").addProperty("contest_chance", 1);
        rules.getAsJsonObject("succession").addProperty("leave_chance", 0);
        rules.getAsJsonObject("fortune").addProperty("rate_per_year", 1);
        rules.getAsJsonObject("fortune").addProperty("contest_chance", 1);
        rules.getAsJsonObject("danger").addProperty("beast_rate_per_year", 1);
        rules.getAsJsonObject("danger").addProperty("danger_exponent", 0);
        rules.getAsJsonObject("danger").addProperty("death_on_loss", 0);
        for (String key : List.of("desert_rate_per_year", "desertion_rate_per_year",
                "schism_rate_per_year", "rogue_join_rate_per_year")) {
            rules.getAsJsonObject("decline").addProperty(key, 0);
        }
        rules.getAsJsonObject("founding").addProperty("rate_per_year", 0);
        patch.accept(rules);

        JsonObject encounters = JsonParser.parseString(Files.readString(
                RESOURCES.resolve(SimData.DIRECTORY + "encounters.json"))).getAsJsonObject();
        JsonObject herb = encounters.getAsJsonArray("encounters").get(0).getAsJsonObject();
        herb.addProperty("contested", true);
        JsonArray onlyHerb = new JsonArray();
        onlyHerb.add(herb);
        encounters.add("encounters", onlyHerb);
        return WorldSim.loadData(path -> {
            if (path.equals(SimData.DIRECTORY + "rules.json")) return stream(rules);
            if (path.equals(SimData.DIRECTORY + "encounters.json")) return stream(encounters);
            Path file = RESOURCES.resolve(path);
            return Files.isRegularFile(file) ? Files.newInputStream(file) : null;
        });
    }

    private static ByteArrayInputStream stream(JsonObject json) {
        return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static SimContext ctx() {
        return ctx(data);
    }

    private static SimContext ctx(SimData rules) {
        WorldState state = new WorldState();
        state.seed = SEED;
        state.tierId = "small";
        graph.regions().forEach(region -> state.regions.put(region.id(), new RegionState(region.id())));
        return new SimContext(state, rules, graph, 1);
    }

    private static String region() {
        return graph.regions().stream().filter(SimContext::admitsSects).findFirst().orElseThrow().id();
    }

    private static String otherRegion() {
        return graph.regions().stream().filter(r -> !r.id().equals(region())).findFirst().orElseThrow().id();
    }

    private static Person person(SimContext ctx, String name) {
        Person p = new Person();
        p.id = ctx.state.nextPersonId++;
        p.given = name;
        p.root = new int[] {10000, 0, 0, 0, 0};
        p.realm = ctx.realms.indexOf("qi_refining");
        p.birthDay = -25;
        p.regionId = region();
        p.homeRegionId = p.regionId;
        p.loyalty = 100;
        ctx.state.persons.put(p.id, p);
        ctx.membershipChanged();
        ctx.newDay();
        return p;
    }

    private static LifeResident resident(SimContext ctx, Person person) {
        LifeResident life = new LifeResident();
        life.personId = person.id;
        life.siteId = "town";
        life.homeId = "bed";
        life.workId = "counter";
        life.x = 20;
        life.y = 70;
        life.z = 30;
        ctx.state.life.residents.put(person.id, life);
        return life;
    }

    private static Sect sect(SimContext ctx, String name) {
        Sect sect = new Sect();
        sect.id = ctx.state.nextSectId++;
        sect.name = name;
        sect.homeRegionId = region();
        ctx.state.sects.put(sect.id, sect);
        return sect;
    }

    private static void war(Sect a, Sect b) {
        a.relationTo(b.id).state = SectRelation.WAR;
        b.relationTo(a.id).state = SectRelation.WAR;
        a.relationTo(b.id).sinceDay = 0;
        b.relationTo(a.id).sinceDay = 0;
    }

    @Test
    void affiliationAndDissolutionKeepTheResidentsHomeAndCurrentStatus() {
        SimContext ctx = ctx();
        Person shopkeeper = person(ctx, "shopkeeper");
        LifeResident life = resident(ctx, shopkeeper);
        shopkeeper.status = "secluded";
        shopkeeper.statusUntil = 120;
        Sect remote = sect(ctx, "remote");
        remote.homeRegionId = otherRegion();
        People.join(ctx, shopkeeper, remote, "elder");
        assertEquals(remote.id, shopkeeper.sectId);
        assertEquals("elder", shopkeeper.rank);
        assertEquals(region(), shopkeeper.regionId);
        assertEquals(region(), shopkeeper.homeRegionId);
        assertEquals("secluded", shopkeeper.status);
        assertEquals(120, shopkeeper.statusUntil);
        assertEquals(List.of(20, 70, 30), List.of(life.x, life.y, life.z));

        Person ordinary = person(ctx, "ordinary");
        People.join(ctx, ordinary, remote, "outer");
        assertEquals(remote.homeRegionId, ordinary.regionId, "regional members still move to their sect");
        SectPolitics.dissolve(ctx, remote, false, -1);
        assertEquals(-1, shopkeeper.sectId, "a destroyed sect still releases its residents' identity");
        assertEquals("rogue", shopkeeper.rank);
        assertEquals(region(), shopkeeper.homeRegionId);
        assertEquals("secluded", shopkeeper.status);
        assertEquals(120, shopkeeper.statusUntil);
    }

    @Test
    void travellingAndSeclusionCannotTakeOverEitherLoadedOrColdResidents() {
        for (boolean embodied : List.of(false, true)) {
            SimContext ctx = ctx();
            Person p = person(ctx, "resident");
            resident(ctx, p).embodied = embodied;
            Travel.depart(ctx, p, otherRegion(), 8);
            assertEquals("at_sect", p.status);
            assertEquals("", p.destRegionId);
            p.status = "travelling";
            p.statusUntil = 0;
            p.destRegionId = otherRegion();
            Travel.daily(ctx, p);
            assertEquals("travelling", p.status, "the life executor owns even a stale macro travel status");
            assertEquals(otherRegion(), p.destRegionId);
            assertEquals(region(), p.regionId);
            p.status = "secluded";
            Travel.daily(ctx, p);
            assertEquals("secluded", p.status);
            assertTrue(ctx.state.chronicle.isEmpty());
        }
    }

    @Test
    void warMeetingsExcludeResidentsAsBothInitiatorAndPartner() {
        for (boolean embodied : List.of(false, true)) {
            SimContext ctx = ctx();
            Sect a = sect(ctx, "a");
            Sect b = sect(ctx, "b");
            Person resident = person(ctx, "resident");
            Person other = person(ctx, "other");
            People.join(ctx, resident, a, "elder");
            People.join(ctx, other, b, "elder");
            resident(ctx, resident).embodied = embodied;
            war(a, b);
            Meetings.meet(ctx, resident, other, ctx.rng(resident.id, Purpose.MEETING_OUTCOME));
            Meetings.meet(ctx, other, resident, ctx.rng(other.id, Purpose.MEETING_OUTCOME));
            assertTrue(ctx.state.chronicle.isEmpty());
            assertEquals(0, resident.injury);
            assertEquals(0, other.injury);
            ctx.state.life.residents.remove(resident.id);
            Meetings.meet(ctx, other, resident, ctx.rng(other.id, Purpose.MEETING_OUTCOME));
            assertTrue(resident.injury + other.injury > 0, "the same ordinary meeting does fight");
        }
    }

    @Test
    void revengeDropsExecutionTargetsButKeepsTheirGrudges() {
        SimContext ctx = ctx();
        Person resident = person(ctx, "resident");
        Person avenger = person(ctx, "avenger");
        resident(ctx, resident);
        resident.relate(avenger.id, Relation.ENEMY, 100, 1, "master");
        avenger.relate(resident.id, Relation.ENEMY, 100, 1, "master");
        resident.quarryId = avenger.id;
        avenger.quarryId = resident.id;
        Revenge.daily(ctx, resident);
        Revenge.daily(ctx, avenger);
        assertEquals(-1, resident.quarryId);
        assertEquals(-1, avenger.quarryId);
        assertFalse(Revenge.dares(ctx, avenger, resident));
        assertFalse(Revenge.dares(ctx, resident, avenger));
        assertTrue(resident.hasRelation(avenger.id, Relation.ENEMY));
        assertTrue(avenger.hasRelation(resident.id, Relation.ENEMY));
        assertTrue(ctx.state.chronicle.isEmpty());
        ctx.state.life.residents.remove(resident.id);
        avenger.quarryId = resident.id;
        Revenge.daily(ctx, avenger);
        assertTrue(resident.injury + avenger.injury > 0);
    }

    @Test
    void fortuneCannotRewardAResidentOrDraftOneAsTheRival() {
        SimContext ctx = ctx();
        Person resident = person(ctx, "resident");
        Person finder = person(ctx, "finder");
        resident(ctx, resident);
        resident.status = "travelling";
        finder.status = "travelling";
        Fortunes.daily(ctx, resident);
        assertEquals(0, resident.fortunes);
        assertEquals(100, ctx.state.regions.get(region()).richness);
        Fortunes.daily(ctx, finder);
        assertEquals(1, finder.fortunes);
        assertTrue(finder.progress > 0);
        assertEquals(0, resident.progress);
        assertEquals(0, resident.injury + finder.injury);
        ctx.state.life.residents.remove(resident.id);
        ctx.state.regions.get(region()).richness = 100;
        Fortunes.daily(ctx, finder);
        assertTrue(resident.injury + finder.injury > 0, "the only ordinary rival contests the find");
    }

    @Test
    void beastsCannotSilentlyHarmOrRewardAResident() {
        SimContext ctx = ctx();
        Person resident = person(ctx, "resident");
        resident(ctx, resident);
        resident.status = "travelling";
        Danger.daily(ctx, resident);
        assertEquals(0, resident.injury);
        assertEquals(0, resident.progress);
        assertTrue(ctx.state.chronicle.isEmpty());
        ctx.state.life.residents.remove(resident.id);
        Danger.daily(ctx, resident);
        assertTrue(resident.injury > 0 || resident.progress > 0, "the ordinary traveller meets a beast");
    }

    @Test
    void championsAndTheirWoundedFallbackExcludeTheStrongestResidents() {
        for (int wound : List.of(0, 80)) {
            SimContext ctx = ctx();
            Sect a = sect(ctx, "a");
            Sect b = sect(ctx, "b");
            Person ma = null;
            Person mb = null;
            for (Sect sect : List.of(a, b)) {
                Person master = person(ctx, "resident-master-" + sect.id);
                master.realm = ctx.realms.indexOf("foundation_establishment");
                master.stage = 2;
                People.join(ctx, master, sect, "sect_master");
                sect.masterId = master.id;
                resident(ctx, master);
                if (sect == a) ma = master; else mb = master;
                for (int i = 0; i < 4; i++) {
                    Person fighter = person(ctx, "ordinary-" + sect.id + "-" + i);
                    People.join(ctx, fighter, sect, "outer");
                    fighter.injury = wound;
                }
            }
            war(a, b);
            SectPolitics.yearly(ctx);
            SectRelation result = a.relationTo(b.id);
            assertTrue(result.lastChampionA > 0 && result.lastChampionB > 0);
            assertNotEquals(ma.id, result.lastChampionA);
            assertNotEquals(mb.id, result.lastChampionB);
            assertEquals(0, ma.injury + mb.injury);
            assertTrue(ctx.alive(ma) && ctx.alive(mb));
        }
    }

    @Test
    void successionAllowsAResidentTitleButNoDuelAgainstEitherElder() {
        for (boolean residentIsHeir : List.of(false, true)) {
            SimContext ctx = ctx();
            Sect sect = sect(ctx, "vacant");
            Person heir = person(ctx, "heir");
            Person second = person(ctx, "second");
            for (Person p : List.of(heir, second)) {
                p.realm = ctx.realms.indexOf("foundation_establishment");
                p.ambition = 100;
                People.join(ctx, p, sect, "elder");
            }
            heir.stage = 1;
            resident(ctx, residentIsHeir ? heir : second);
            Succession.settle(ctx);
            assertEquals(heir.id, sect.masterId);
            assertEquals(0, heir.injury + second.injury, "otherwise eligible elders would certainly duel");
            assertTrue(ctx.state.chronicle.stream().anyMatch(e -> e.type().equals("succession")));
        }
    }

    @Test
    void automaticFoundingCannotTakeAResidentLeaderOrFollower() throws IOException {
        SimContext ctx = ctx(load(rules -> {
            rules.getAsJsonObject("founding").addProperty("rate_per_year", 1);
            rules.getAsJsonObject("founding").add("followers", JsonParser.parseString("[1,1]"));
        }));
        Person residentLeader = person(ctx, "resident-leader");
        Person residentFollower = person(ctx, "resident-follower");
        Person founder = person(ctx, "founder");
        Person ordinaryFollower = person(ctx, "ordinary-follower");
        for (Person leader : List.of(residentLeader, founder)) {
            leader.realm = ctx.realms.indexOf("foundation_establishment");
            leader.stage = 2;
            leader.ambition = 100;
        }
        resident(ctx, residentLeader);
        resident(ctx, residentFollower);
        assertNull(SectPolitics.foundSect(ctx, residentLeader, null,
                List.of(ordinaryFollower), -1, TextKeys.SECT_FOUNDED));
        SectPolitics.yearly(ctx);
        assertEquals(-1, residentLeader.sectId);
        assertEquals(-1, residentFollower.sectId);
        assertTrue(founder.sectId >= 0, "the eligible ordinary founder still establishes a sect");
        assertEquals(founder.sectId, ordinaryFollower.sectId);
        assertEquals(1, ctx.state.sects.size());
    }

    @Test
    void automaticRecruitmentLeavesTheResidentRogueAtHome() throws IOException {
        SimContext ctx = ctx(load(rules ->
                rules.getAsJsonObject("decline").addProperty("rogue_join_rate_per_year", 1)));
        Sect sect = sect(ctx, "recruiting");
        Person master = person(ctx, "master");
        master.realm = ctx.realms.indexOf("foundation_establishment");
        People.join(ctx, master, sect, "sect_master");
        sect.masterId = master.id;
        Person resident = person(ctx, "resident");
        Person ordinary = person(ctx, "ordinary");
        resident(ctx, resident);
        SectPolitics.yearly(ctx);
        assertEquals(-1, resident.sectId);
        assertEquals(sect.id, ordinary.sectId);
    }

    @Test
    void desertionAndSplinterFollowersLeaveResidentsInTheirAffiliation() throws IOException {
        SimContext ctx = ctx(load(rules ->
                rules.getAsJsonObject("decline").addProperty("desertion_rate_per_year", 1)));
        Sect sect = sect(ctx, "home");
        Person leader = person(ctx, "leader");
        Person resident = person(ctx, "resident-disciple");
        Person ordinary = person(ctx, "ordinary-disciple");
        Person master = person(ctx, "master");
        for (Person p : List.of(leader, resident, ordinary, master)) {
            People.join(ctx, p, sect, p == master ? "sect_master" : "elder");
        }
        master.realm = ctx.realms.indexOf("foundation_establishment");
        sect.masterId = master.id;
        resident(ctx, resident);
        resident.masterId = leader.id;
        ordinary.masterId = leader.id;
        assertEquals(List.of(ordinary), SectPolitics.followersOf(ctx, leader, sect));
        resident.loyalty = 0;
        ordinary.loyalty = 0;
        SectPolitics.yearly(ctx);
        assertEquals(sect.id, resident.sectId);
        assertEquals(-1, ordinary.sectId, "ordinary desertion still operates");
        assertFalse(ctx.state.chronicle.stream()
                .anyMatch(e -> e.type().equals("desertion") && e.actors().contains(resident.id)));
    }

    @Test
    void residentsStillAgeAndLeaveTheOrdinaryTombstone() {
        SimContext ctx = ctx();
        Person resident = person(ctx, "elderly-resident");
        resident(ctx, resident);
        resident.birthDay = -(long) ctx.lifespanYears(resident);
        Lifespan.daily(ctx, resident);
        assertFalse(ctx.alive(resident));
        assertEquals(Deaths.OLD_AGE, ctx.state.tombstones.get(resident.id).cause);
        assertTrue(ctx.state.chronicle.stream().anyMatch(e -> e.type().equals("death")));
    }
}
