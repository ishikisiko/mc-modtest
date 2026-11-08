package com.example.myvillage.stats;

import java.io.StringReader;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CultivationStatsRulesTest {
    private final CultivationStatsRules rules = CultivationStatsRules.bundled();
    @Test void exactApprovedQiTableAndNoExtraFifthLayerReward() {
        double[] health={30,32,34,36,38,40,42,44,46}, attack={2,2,3,3,4,4,5,5,6}, defense={1,1,1.5,1.5,2,2,2.5,2.5,3};
        for(int i=0;i<9;i++) assertEquals(new CultivationStatsRules.Body(health[i],attack[i],defense[i],30+10*i),
                rules.at(new RealmKey("qi_refining",i),0,0));
        assertEquals(new CultivationStatsRules.Body(20,1,0,0),rules.at(new RealmKey("mortal",0),0,0));
    }
    @Test void allFoundationOriginsHaveExactStartingPointsAndLinearStages() {
        double[][] expected={{62,10,4,160},{66,11,4.5,180},{70,12,5,200},{78,14,6,240}};
        for(int q=1;q<=4;q++) for(int stage=0;stage<3;stage++) {
            var e=expected[q-1];
            assertEquals(new CultivationStatsRules.Body(e[0]+6*stage,e[1]+stage,e[2]+.5*stage,(long)e[3]+20*stage),
                    rules.at(new RealmKey("foundation_establishment",stage),q,0));
        }
    }
    @Test void goldenTableKeepsBothEarlierOriginAndOwnGradeExactlyOnce() {
        double[][] early={{120,28,10,400},{130,32,11,440},{140,36,12,480}};
        for(int grade=1;grade<=3;grade++) for(int stage=0;stage<3;stage++) {
            var e=early[grade-1];
            assertEquals(new CultivationStatsRules.Body(e[0]+10*stage,e[1]+2*stage,e[2]+stage,(long)e[3]+40*stage),
                    rules.at(new RealmKey("golden_core",stage),4,grade));
            // 根基补全始终只补差额，不随金丹品质/小阶段翻倍。
            var low=rules.at(new RealmKey("golden_core",stage),1,grade);
            var high=rules.at(new RealmKey("golden_core",stage),3,grade);
            assertEquals(8,high.health()-low.health());assertEquals(2,high.attack()-low.attack());
            assertEquals(1,high.defense()-low.defense());assertEquals(40,high.power()-low.power());
        }
    }
    @Test void creatureBaseDifferencesAreAddedRatherThanMultiplied() {
        var key=new RealmKey("golden_core",2);
        var a=RealmStats.derived(StatsRules.bundled(),key,20,1);
        var b=RealmStats.derived(StatsRules.bundled(),key,30,6);
        assertEquals(10,b.health()-a.health());assertEquals(5,b.attack()-a.attack());
        assertEquals(a.defense(),b.defense());assertEquals(1,a.factor());
    }
    @Test void nativeBossBaseSurvivesAdditiveGrowthWithoutRaisingEngineCaps() {
        var body = RealmStats.vanillaDerived(StatsRules.bundled(),new RealmKey("foundation_establishment",0),320,7);
        assertEquals(362,body.health());assertEquals(16,body.attack());assertEquals(4,body.defense());
        var saturated=RealmStats.vanillaDerived(StatsRules.bundled(),new RealmKey("golden_core",2),1024,2048);
        assertEquals(1024,saturated.health());assertEquals(2048,saturated.attack());
        assertThrows(IllegalArgumentException.class,()->RealmStats.vanillaDerived(StatsRules.bundled(),new RealmKey("qi_refining",0),1025,1));
    }
    @Test void spiritTransformationUsesTheActualAdditiveEnvelopeForEveryQuality() throws Exception {
        var limits = StatsRules.bundled().limits();
        for (int f=1; f<=4; f++) for(int g=1; g<=3; g++) {
            var body = RealmStats.derived(StatsRules.bundled(),new RealmKey("spirit_transformation",2),limits.maxBaseHealth(),limits.maxBaseAttack(),f,g);
            assertTrue(body.health()<=1024); assertTrue(body.attack()<=2048);
            assertEquals(1,body.factor());
        }
        try(var input=getClass().getResourceAsStream(CultivationStatsRules.RESOURCE)) {
            var root=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            root.getAsJsonObject("spirit_transformation").getAsJsonArray("breakthrough").set(0,new JsonPrimitive(2000));
            assertThrows(IllegalArgumentException.class,()->CultivationStatsRules.parse(new StringReader(root.toString())));
        }
    }
    @Test void invalidDataCannotChangeApprovedStatBoundaries() throws Exception {
        assertThrows(IllegalArgumentException.class,()->rules.at(new RealmKey("qi_refining",9),0,0));
        assertThrows(IllegalArgumentException.class,()->rules.foundationBonus(5));
        assertThrows(IllegalArgumentException.class,()->rules.goldenBonus(4));
        try(var input=getClass().getResourceAsStream(CultivationStatsRules.RESOURCE)) {
            var root=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            root.getAsJsonObject("defense").addProperty("floor",.5);
            assertThrows(IllegalArgumentException.class,()->CultivationStatsRules.parse(new StringReader(root.toString())));
        }
    }
}
