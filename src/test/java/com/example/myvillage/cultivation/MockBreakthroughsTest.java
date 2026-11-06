package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MockBreakthroughsTest {
    private RealmStageDefinition stage(String realm,String stage) throws Exception {
        try(var in=getClass().getResourceAsStream("/data/myvillage/myvillage/realm/"+realm+".json")){
            var data=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8));
            return RealmDefinition.CODEC.parse(JsonOps.INSTANCE,data).getOrThrow().stages().stream()
                    .filter(s->s.id().getPath().equals(stage)).findFirst().orElseThrow();
        }
    }
    private CultivationProfile ready(String realm,String id) throws Exception {
        var s=stage(realm,id);
        return CultivationProfile.DEFAULT.withSpiritualRoot(new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:fire"),10000)))
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withRealmAndStage(ResourceLocation.parse("myvillage:"+realm),s.id())
                .withCultivationProgress(s.cultivationCap().orElseThrow())
                .withStability(s.advancement().orElseThrow().requiredStability()).withCurrentSpiritualPower(17);
    }
    @Test void heavenMockPreservesCountersAndDoesNotInventHealthOrPower() throws Exception {
        var old=ready("qi_refining","qi_refining_9").withLifespanConsumedTicks(77);
        var plan=MockBreakthroughs.plan(old,stage("qi_refining","qi_refining_9"),true,4);
        assertEquals(4,plan.replacement().foundationQuality());assertEquals(6,plan.plannedMainHits());
        assertEquals("foundation_early",plan.replacement().stageId().getPath());
        assertEquals(0,plan.replacement().cultivationProgress());assertEquals(old.stability()/2,plan.replacement().stability());
        assertEquals(17,plan.replacement().currentSpiritualPower());assertEquals(77,plan.replacement().lifespanConsumedTicks());
        assertThrows(IllegalArgumentException.class,()->MockBreakthroughs.plan(plan.replacement(),stage("qi_refining","qi_refining_9"),true,4));
    }
    @Test void allFoundationOriginsMayChooseEveryGoldenQuality() throws Exception {
        var stage=stage("foundation_establishment","foundation_late");
        for(int root=1;root<=4;root++)for(int grade=1;grade<=3;grade++){
            var old=ready("foundation_establishment","foundation_late").withFoundationQuality(root);
            var result=MockBreakthroughs.plan(old,stage,false,grade);
            assertEquals(root,result.replacement().foundationQuality());assertEquals(grade,result.replacement().goldenCoreQuality());
            assertEquals(3+3*grade,result.plannedMainHits());
        }
    }
    @Test void insufficientOrWrongSourceIntentsCannotGrantAQuality() throws Exception {
        var p=ready("qi_refining","qi_refining_9");var s=stage("qi_refining","qi_refining_9");
        assertThrows(IllegalArgumentException.class,()->MockBreakthroughs.plan(p.withCultivationProgress(0),s,true,4));
        assertThrows(IllegalArgumentException.class,()->MockBreakthroughs.plan(p.withStability(0),s,true,4));
        assertThrows(IllegalArgumentException.class,()->MockBreakthroughs.plan(p.withoutSpiritualRoot(),s,true,4));
        assertThrows(IllegalArgumentException.class,()->MockBreakthroughs.plan(p,s,false,3));
        assertThrows(IllegalArgumentException.class,()->MockBreakthroughs.plan(p,s,true,3));
    }
}
