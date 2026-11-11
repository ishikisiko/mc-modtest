package com.example.myvillage.sim.engine;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.sim.*;
import com.example.myvillage.sim.cli.SimCli;
import com.example.myvillage.sim.data.SectProgressionRules;
import com.example.myvillage.sim.model.*;
import com.example.myvillage.sim.sect.ElderAssessment;
import com.example.myvillage.stats.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SectProgressionTest {
    private static SimData data;
    private static com.example.myvillage.region.runtime.RegionGraph graph;
    private static final String PLAYER = "00000000-0000-0000-0000-000000000001";
    private static final PlayerQualification FOUNDATION = new PlayerQualification("foundation_establishment", 0, true, 60);
    @BeforeAll static void load() throws Exception {
        data = WorldSim.loadData(name -> Files.newInputStream(Path.of("src/main/resources").resolve(name)));
        // 宗门等级面向当前八州世界；旧CLI拓扑可只抽取五州，不用于此处的两处总舵断言。
        try (var input = getResource()) {
            var rules = new com.example.myvillage.worldplan.WorldPlanRules(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            graph = new com.example.myvillage.worldplan.WorldPlan(20261109L, rules).graph();
        }
    }
    private static java.io.InputStream getResource() {
        var input = SectProgressionTest.class.getResourceAsStream(com.example.myvillage.worldplan.WorldPlanRules.RESOURCE);
        assertNotNull(input, "正式八州资源必须存在"); return input;
    }
    private static SimContext fresh() {
        var state = new WorldState(); state.seed = 20261109; state.tierId = "small"; state.genesisDaysPerYear = 24;
        var ctx = new SimContext(state, data, graph, 24); Engine.genesis(ctx); return ctx;
    }
    private static Sect branch(SimContext ctx) {
        return ctx.activeSects().stream().filter(s -> s.tierId.equals("branch")).findFirst().orElseThrow();
    }
    private static PlayerMember member(SimContext ctx, Sect sect) {
        PlayerAffairs.join(ctx, PLAYER, "考核弟子", sect.id, FOUNDATION, true);
        return ctx.state.playerMembers.get(PLAYER);
    }
    private static BigInteger amount(long n) { return BigInteger.valueOf(n); }
    private static ElderAssessment.Trial trial() {
        return new ElderAssessment.Trial(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "minecraft:overworld", 0, 64, 0, 1000);
    }
    @Test void invalidGradeMoneyAndAssessmentConfigurationIsRejected() throws Exception {
        var path = Path.of("src/main/resources/data/myvillage/world_sim/rules.json");
        for (String kind : List.of("money", "realm", "trial", "hierarchy")) {
            var doc = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var rule = doc.getAsJsonObject("sect_progression");
            if (kind.equals("money")) rule.getAsJsonObject("levels").getAsJsonObject("branch").getAsJsonObject("monthly").addProperty("outer", -1);
            if (kind.equals("realm")) rule.getAsJsonObject("levels").getAsJsonObject("main").getAsJsonArray("masters").get(0).getAsJsonObject().addProperty("realm", "unknown");
            if (kind.equals("trial")) rule.getAsJsonObject("elder_trial").addProperty("stage", 7);
            if (kind.equals("hierarchy")) rule.getAsJsonObject("levels").getAsJsonObject("headquarters").getAsJsonObject("promotion").getAsJsonObject("elder").addProperty("realm", "qi_refining");
            byte[] changed = doc.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThrows(SimDataException.class, () -> WorldSim.loadData(name -> name.endsWith("world_sim/rules.json")
                    ? new java.io.ByteArrayInputStream(changed) : Files.newInputStream(Path.of("src/main/resources").resolve(name))), kind);
        }
    }
    @Test void regionalLevelsAndGenesisUseTheConfiguredRealmLadders() {
        var ctx = fresh();
        assertEquals("main", data.rules().sectProgression().forRegion(graph, "zhongzhou"));
        long hubs = graph.regions().stream().filter(r -> data.rules().sectProgression().forRegion(graph, r.id()).equals("headquarters")).count();
        assertEquals(2, hubs);
        assertTrue(ctx.activeSects().stream().anyMatch(s -> s.tierId.equals("main")));
        assertTrue(ctx.activeSects().stream().anyMatch(s -> s.tierId.equals("headquarters")));
        assertTrue(ctx.activeSects().stream().anyMatch(s -> s.tierId.equals("branch")));
        for (var sect : ctx.activeSects()) {
            var tier = data.rules().sectProgression().tier(sect.tierId);
            var head = ctx.state.persons.get(sect.masterId);
            assertTrue(tier.masters().stream().anyMatch(m -> ctx.realms.indexOf(m.realm()) == head.realm
                    && head.stage >= m.minimumStage() && head.stage <= m.maximumStage()));
            for (var p : ctx.members(sect.id)) {
                assertTrue(p.realm < head.realm || p.realm == head.realm && p.stage <= head.stage, p.name());
                if (p.rank.equals("outer")) assertEquals(tier.outer().realm(), ctx.realm(p).id());
            }
        }
    }
    @Test void npcTransferRechecksTheReceivingSectsRankRequirements() {
        var ctx = fresh();
        var main = ctx.activeSects().stream().filter(s -> s.tierId.equals("main")).findFirst().orElseThrow();
        var low = ctx.members(branch(ctx).id).stream().filter(p -> p.rank.equals("outer")).findFirst().orElseThrow();
        People.join(ctx, low, main, "elder");
        assertEquals("outer", low.rank, "分宗称号不能代替主宗的任职境界");
        assertEquals(main.id, low.sectId);
    }
    @Test void openingMasterProfilesRemainValidAfterTheActualHundredYearPrehistory() {
        var sim = WorldSim.genesis(20261109, graph, data, "small", 24);
        assertEquals(2400, sim.day());
        for (var sect : sim.sects(false)) {
            var head = sim.person(sect.masterId()).orElseThrow();
            var tier = data.rules().sectProgression().tier(sect.tierId());
            assertTrue(tier.masters().stream().anyMatch(m -> m.realm().equals(head.realmId())
                    && head.stage() >= m.minimumStage() && head.stage() <= m.maximumStage()), sect.name()+": "+head);
        }
        var ctx = fresh(); var sect = branch(ctx); var head = ctx.state.persons.get(sect.masterId);
        ctx.state.prehistoryDays = 2400;
        var opening = ctx.initialSectMaster(sect); head.realm = ctx.realms.indexOf(opening.realm()); head.stage = opening.stage();
        head.progress = 1_000_000;
        Cultivation.daily(ctx, head); Breakthrough.daily(ctx, head);
        assertEquals(opening.realm(), ctx.realm(head).id()); assertEquals(opening.stage(), head.stage);
        ctx.state.day = ctx.state.prehistoryDays;
        assertNull(ctx.prehistoryCeiling(head), "开局以后不限制人物成长");
    }
    @Test void prehistoryFoundersUseTheirConfiguredMasterRangeAndOpeningRestoresFreeFounding() {
        for (boolean beforeOpening : List.of(true, false)) {
            var ctx = fresh(); ctx.state.prehistoryDays = 2400;
            ctx.state.day = beforeOpening ? 0 : 2400;
            var leader = People.create(ctx, SimRng.at(ctx.state.seed, ctx.day(), 9999, Purpose.FOUNDING),
                    new int[]{50, 20, 10, 10, 10}, ctx.realms.indexOf("nascent_soul"), 0, 100L*ctx.dpy, "beiyuan");
            var created = SectPolitics.foundSect(ctx, leader, null, List.of(), -1, TextKeys.SECT_FOUNDED);
            assertNotNull(created);
            assertEquals("nascent_soul",ctx.realm(leader).id()); assertEquals(0,leader.stage);
            if (beforeOpening) {
                var tier = data.rules().sectProgression().tier(created.tierId);
                assertTrue(tier.masters().stream().anyMatch(m -> m.realm().equals("nascent_soul")
                        && m.minimumStage() <= leader.stage && m.maximumStage() >= leader.stage));
            } else {
                assertEquals("beiyuan",created.homeRegionId);
                assertEquals("branch",created.tierId);
            }
        }
    }
    @Test void branchMasterRarityAndHigherRealmBodiesAreActualData() {
        var rules = data.rules().sectProgression();
        assertEquals(99, rules.tier("branch").masters().getFirst().weight());
        assertEquals(1, rules.tier("branch").masters().getLast().weight());
        int rare = 0;
        for (int i = 0; i < 2000; i++) {
            var m = rules.tier("branch").master(SimRng.at(123, 0, i, Purpose.SECT_TIER_MASTER));
            if (m.realm().equals("golden_core")) { rare++; assertEquals(1, m.minimumStage()); }
            else { assertEquals("foundation_establishment", m.realm()); assertEquals(2, m.minimumStage()); }
        }
        assertTrue(rare > 0 && rare < 80, "固定种子稀有抽取必须低于4%");
        assertEquals(2000, data.realms().byId("spirit_transformation").lifespanYears());
        var body = CultivationStatsRules.bundled();
        var before = body.at(new RealmKey("nascent_soul", 2), 1, 1);
        var after = body.at(new RealmKey("spirit_transformation", 0), 1, 1);
        assertTrue(after.health() > before.health()); assertTrue(after.attack() > before.attack());
        assertTrue(after.defense() > before.defense()); assertTrue(after.power() > before.power());
        StatsRules.bundled().realmKey("spirit_transformation", 2);
    }
    @Test void everyRankReceivesItsExactMonthlyDenominationIncludingNpcMasters() {
        String[] tiers = {"branch", "headquarters", "main"};
        String[] stones = {"low", "middle", "high"};
        int[][] amounts = {{5,10,40,100}, {1,2,10,40}, {1,2,10,50}};
        String[] ranks = {"outer", "inner", "elder", "sect_master"};
        for (int level = 0; level < tiers.length; level++) for (int rank = 0; rank < ranks.length; rank++) {
            var ctx = fresh(); var sect = branch(ctx); sect.tierId = tiers[level];
            var npc = ctx.state.persons.get(sect.masterId); npc.rank = ranks[rank];
            SectBenefits.observeMonth(ctx, 100);
            assertTrue(npc.stipend.balance().isEmpty());
            SectBenefits.observeMonth(ctx, 101);
            assertEquals(Map.of(stones[level], amount(amounts[level][rank])), npc.stipend.balance());
            assertFalse(SectBenefits.observeMonth(ctx, 101)); assertFalse(SectBenefits.observeMonth(ctx, 99));
            assertEquals(Map.of(stones[level], amount(amounts[level][rank])), npc.stipend.balance());
        }
    }
    @Test void joiningPromotionLeavingAndRejoiningCannotReplayMonths() {
        var ctx = fresh(); var sect = branch(ctx);
        SectBenefits.observeMonth(ctx, 10); var m = member(ctx, sect);
        assertTrue(m.stipend.balance().isEmpty());
        SectBenefits.observeMonth(ctx, 11); assertEquals(amount(5), m.stipend.balance().get("low"));
        m.rank = "inner"; SectBenefits.observeMonth(ctx, 12); assertEquals(amount(15), m.stipend.balance().get("low"));
        PlayerAffairs.leave(ctx, PLAYER, "考核弟子"); SectBenefits.observeMonth(ctx, 15);
        assertEquals(amount(15), m.stipend.balance().get("low"));
        PlayerAffairs.join(ctx, PLAYER, "考核弟子", sect.id, FOUNDATION, true);
        SectBenefits.observeMonth(ctx, 16); assertEquals(amount(20), m.stipend.balance().get("low"));
        sect.state = Sect.DESTROYED; SectBenefits.observeMonth(ctx, 17);
        assertEquals(amount(20), m.stipend.balance().get("low"));
    }
    @Test void partialClaimsOnlyCommitWithTheExpectedBalanceAndInventory() {
        var ctx = fresh(); var sect = branch(ctx); SectBenefits.observeMonth(ctx, 0); var m = member(ctx, sect);
        SectBenefits.observeMonth(ctx, 10); var before = m.stipend.balance();
        assertEquals(Map.of("low", amount(50)), before);
        assertFalse(SectBenefits.claim(ctx, PLAYER, sect.id, before, Map.of("low", amount(20)), () -> false));
        assertEquals(before, m.stipend.balance());
        assertFalse(SectBenefits.claim(ctx, PLAYER, sect.id, before, Map.of("low", amount(51)), () -> fail("不得提交超额领取")));
        assertFalse(SectBenefits.claim(ctx, PLAYER, sect.id + 100, before, before, () -> fail("不得跨宗领取")));
        assertTrue(SectBenefits.claim(ctx, PLAYER, sect.id, before, Map.of("low", amount(20)), () -> true));
        assertEquals(Map.of("low", amount(30)), m.stipend.balance());
        assertFalse(SectBenefits.claim(ctx, PLAYER, sect.id, before, before, () -> fail("旧请求不得重放")));
        var remaining = m.stipend.balance(); assertTrue(SectBenefits.claim(ctx, PLAYER, sect.id, remaining, remaining, () -> true));
        assertTrue(m.stipend.balance().isEmpty());
    }
    @Test void balancesAndMonthWatermarksSurviveCodecWithNoSecondPayment() {
        var ctx = fresh(); var sect = branch(ctx); SectBenefits.observeMonth(ctx, 0); member(ctx, sect);
        SectBenefits.observeMonth(ctx, 2);
        byte[] saved = StateCodec.toBytes(ctx.state, data.realms());
        var restored = new SimContext(StateCodec.fromBytes(saved, data.realms()), data, graph, 24);
        assertArrayEquals(saved, StateCodec.toBytes(restored.state, data.realms()));
        assertFalse(SectBenefits.observeMonth(restored, 2));
        assertEquals(Map.of("low", amount(10)), SectBenefits.pending(restored, PLAYER));
        SectBenefits.observeMonth(restored, 3); assertEquals(Map.of("low", amount(15)), SectBenefits.pending(restored, PLAYER));
        var json = StateCodec.toJson(restored.state, data.realms());
        json.getAsJsonArray("player_members").get(0).getAsJsonObject().getAsJsonObject("stipend").addProperty("low", "-1");
        assertThrows(SimFormatException.class, () -> StateCodec.fromBytes(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), data.realms()));
    }
    @Test void yearlyReviewCannotPromoteAnUntestedBranchDiscipleToElder() {
        var ctx = fresh(); var m = member(ctx, branch(ctx)); m.rank = "inner";
        PlayerAffairs.yearly(ctx); assertEquals("inner", m.rank); assertFalse(m.elderTrialPassed);
        var t = trial(); PlayerAffairs.startElderTrial(ctx, PLAYER, t);
        assertFalse(PlayerAffairs.completeElderTrial(ctx, PLAYER, UUID.randomUUID().toString(), t.target(), 900));
        assertFalse(PlayerAffairs.completeElderTrial(ctx, PLAYER, t.attempt(), UUID.randomUUID().toString(), 900));
        assertFalse(PlayerAffairs.completeElderTrial(ctx, PLAYER, t.attempt(), t.target(), 1000));
        assertEquals("inner", m.rank);
        assertTrue(PlayerAffairs.completeElderTrial(ctx, PLAYER, t.attempt(), t.target(), 999));
        assertEquals("elder", m.rank); assertTrue(m.elderTrialPassed); assertNull(m.elderTrial);
        assertFalse(PlayerAffairs.completeElderTrial(ctx, PLAYER, t.attempt(), t.target(), 999));
    }
    @Test void trialAuthorityAndStateArePerMemberAndResetOnLeaving() {
        var ctx = fresh(); var sect = branch(ctx); var m = member(ctx, sect);
        assertFalse(PlayerAffairs.elderTrialRefusal(ctx, PLAYER).isEmpty());
        m.rank = "inner"; m.realmId = "qi_refining"; m.stageIndex = 8;
        assertFalse(PlayerAffairs.elderTrialRefusal(ctx, PLAYER).isEmpty());
        m.realmId = "foundation_establishment"; m.stageIndex = 0; var t = trial();
        PlayerAffairs.startElderTrial(ctx, PLAYER, t);
        assertThrows(IllegalArgumentException.class, () -> PlayerAffairs.startElderTrial(ctx, PLAYER, trial()));
        var restored = StateCodec.fromBytes(StateCodec.toBytes(ctx.state, data.realms()), data.realms());
        assertEquals(t, restored.playerMembers.get(PLAYER).elderTrial);
        assertFalse(PlayerAffairs.completeElderTrial(ctx, "00000000-0000-0000-0000-000000000002", t.attempt(), t.target(), 500));
        PlayerAffairs.leave(ctx, PLAYER, "考核弟子"); assertNull(m.elderTrial); assertFalse(m.elderTrialPassed);
        assertFalse(PlayerAffairs.completeElderTrial(ctx, PLAYER, t.attempt(), t.target(), 500));
        assertFalse(t.identity(UUID.randomUUID().toString(), t.target()));
        assertFalse(t.identity(t.attempt(), UUID.randomUUID().toString()));
        assertTrue(t.identity(t.attempt(), t.target()));
        assertFalse(t.within("minecraft:the_nether", 0,64,0,48)); assertFalse(t.within("minecraft:overworld",49,64,0,48));
        assertTrue(t.within("minecraft:overworld",0,64,0,48));
    }
    @Test void higherSectsUseDifferentPromotionBarsAndKeepCurrentAdmission() {
        for (String id : List.of("headquarters", "main")) {
            var ctx = fresh(); var sect = branch(ctx); sect.tierId = id; sect.prestige = 0;
            assertTrue(PlayerAffairs.admission(ctx, PLAYER, sect.id, new PlayerQualification("mortal",1,true,60)).ok());
            var m = member(ctx, sect); var bars = data.rules().sectProgression().promotion(id, data.rules().player().promotion());
            PlayerAffairs.yearly(ctx); assertEquals("outer", m.rank);
            m.realmId = bars.inner().stage().realm(); m.stageIndex = bars.inner().stage().stage();
            PlayerAffairs.yearly(ctx); assertEquals("inner", m.rank);
            m.realmId = bars.elder().stage().realm(); m.stageIndex = bars.elder().stage().stage();
            PlayerAffairs.yearly(ctx); assertEquals("elder", m.rank); assertFalse(m.elderTrialPassed);
        }
    }
}
