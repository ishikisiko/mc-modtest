package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import org.junit.jupiter.api.Test;

class SpiritBodyRulesTest {
    private JsonObject json() { return JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/data/myvillage/fates/spirit_bodies.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); }
    @Test void authoredRulesParseAndCategoriesAreExplicit() {
        var rules=SpiritBodyRules.parse(json());assertEquals(40,rules.adaptation().capacity());
        assertEquals("slash",rules.styleCategories().get("myvillage:basic_sword"));assertEquals(60,rules.phoenix().rebirthCost());
        assertEquals(100,rules.phoenix().emberTicks());assertEquals(10,AdaptationCategory.values().length);
    }
    @Test void missingFractionAndNaNFailRatherThanUsingHiddenDefaults() {
        var root=json();root.getAsJsonObject("phoenix").remove("natural_per_second");assertThrows(IllegalArgumentException.class,()->SpiritBodyRules.parse(root));
        var broken=json();broken.getAsJsonObject("adaptation").addProperty("capacity",Double.NaN);assertThrows(IllegalArgumentException.class,()->SpiritBodyRules.parse(broken));
    }
    @Test void excessiveDurabilityAndUnknownCategoriesFail() {
        var root=json();root.getAsJsonObject("phoenix").addProperty("extra_ember_health_fraction_per_seed",1);assertThrows(IllegalArgumentException.class,()->SpiritBodyRules.parse(root));
        var broken=json();broken.getAsJsonObject("style_categories").addProperty("myvillage:basic_sword","imaginary");assertThrows(IllegalArgumentException.class,()->SpiritBodyRules.parse(broken));
    }
    @Test void coreFateDefinitionsUseSeparateBodyBonusesAndEnableMechanisms() {
        for(String id:java.util.List.of("metal_spirit_body","phoenix_body")) {
            var document=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/data/myvillage/myvillage/innate_fate/"+id+".json"),java.nio.charset.StandardCharsets.UTF_8));
            var fate=FateDefinition.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,document).getOrThrow();
            assertTrue(fate.effect("body_health_bonus")>0);assertEquals(0,fate.effect("health"));
            assertEquals(1,fate.effect(id.equals("phoenix_body")?"phoenix":"jingang_adaptation"));
        }
    }
}
