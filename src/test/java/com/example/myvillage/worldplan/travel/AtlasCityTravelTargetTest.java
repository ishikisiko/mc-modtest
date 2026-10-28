package com.example.myvillage.worldplan.travel;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlanRules;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasCityTravelTargetTest {
    private WorldPlan plan(int version) throws Exception {
        String path="/data/myvillage/worldplan/frozen_v"+version+".json";
        try (var in=getClass().getResourceAsStream(path)) {
            assertNotNull(in,"必须先经正常资源生成提供 "+path);
            return new WorldPlan(20261029,new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8)));
        }
    }
    @Test void bothFrozenCapitalVersionsResolveEveryServerOwnedEntry() throws Exception {
        for (int version : new int[]{8,9}) {
            var plan=plan(version);String before=plan.manifest().toString();
            assertEquals(8,plan.cities().size());
            for (var city : plan.cities()) {
                var selected=AtlasCityTravelTarget.resolve(plan,city.cityId(),plan.fingerprint()).orElseThrow();
                assertSame(city,selected);
                assertEquals(city.entrance(),selected.entrance());
            }
            assertEquals(before,plan.manifest().toString());
        }
    }
    @Test void staleMapsRegionAliasesAndUnknownCitiesAreRejected() throws Exception {
        var plan=plan(8);
        var city=plan.cities().getFirst();
        String stale=plan.fingerprint().charAt(0)=='a' ? "b".repeat(64) : "a".repeat(64);
        assertTrue(AtlasCityTravelTarget.resolve(plan,city.cityId(),stale).isEmpty());
        assertTrue(AtlasCityTravelTarget.resolve(plan,city.regionId(),plan.fingerprint()).isEmpty());
        assertTrue(AtlasCityTravelTarget.resolve(plan,"capital_unknown",plan.fingerprint()).isEmpty());
    }
    @Test void legacyPlansKeepTheirEmptyCityOverlay() throws Exception {
        var plan=plan(7);
        assertTrue(plan.cities().isEmpty());
        assertTrue(AtlasCityTravelTarget.resolve(plan,"capital_zhongzhou",plan.fingerprint()).isEmpty());
    }
}
