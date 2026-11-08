package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElementAffinityTest {
    private static final Map<String,FateDefinition> DEFS=new TreeMap<>();
    @BeforeAll static void load() throws Exception {
        for(String element:List.of("metal","wood","water","earth","fire")) {
            try(var in=ElementAffinityTest.class.getResourceAsStream("/data/myvillage/myvillage/innate_fate/"+element+"_affinity.json")) {
                assertNotNull(in);
                DEFS.put(element,FateDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8))).getOrThrow());
            }
        }
    }
    @Test void bundledTraitsGrantOnlyTheirMatchingElementAndRemainRare() {
        for(var entry:DEFS.entrySet()) {
            var definition=entry.getValue();assertEquals(1,definition.tier());assertEquals(2,definition.cost());
            assertFalse(definition.effects().isEmpty());
            assertEquals(.15,FateEffects.elementBonus(entry.getKey(),entry.getKey().equals("fire"),false,definition::effect));
            assertEquals(0,FateEffects.elementBonus("physical",false,false,definition::effect));
            if(!entry.getKey().equals("fire"))assertEquals(0,FateEffects.elementBonus("fire",true,false,definition::effect));
        }
    }
    @Test void frozenSpellDamageAndHarvestReceiveAffinityExactlyOnce() {
        var wood=DEFS.get("wood");
        double frozen=100*(1+FateEffects.elementBonus("wood",false,false,wood::effect));
        double hit=frozen*FateEffects.damageFactor(0,FateEffects.elementBonus("wood",false,true,wood::effect),0);
        assertEquals(115,hit,1e-10);
        double storedDot=frozen*.18;
        double harvest=(frozen+storedDot)*FateEffects.damageFactor(0,FateEffects.elementBonus("wood",false,true,wood::effect),0);
        assertEquals(135.7,harvest,1e-10);
        assertEquals(.15,FateEffects.elementBonus("physical",true,false,DEFS.get("fire")::effect));
    }
    @Test void ordinaryDamageBonusesComposeOnceWithHunterAndGeneralEffects() {
        double bonus=FateEffects.elementBonus("metal",false,false,DEFS.get("metal")::effect);
        assertEquals(1.6,FateEffects.damageFactor(.3,bonus,.15),1e-10);
    }
    @Test void actualDefinitionsCarryAcceptedProcValuesAndHealingBonus() {
        assertEquals(.1,DEFS.get("metal").effect("metal_armor_break_fraction"));
        assertEquals(80,DEFS.get("metal").effect("metal_armor_break_ticks"));
        assertEquals(.2,DEFS.get("wood").effect("healing_bonus"));
        assertEquals(.2,DEFS.get("water").effect("water_slow_fraction"));
        assertEquals(60,DEFS.get("water").effect("water_slow_ticks"));
        assertEquals(160,DEFS.get("water").effect("water_slow_cooldown"));
        assertEquals(.5,FateEffects.groundKnockbackFactor(true,DEFS.get("earth").effect("ground_knockback")));
        assertEquals(1,FateEffects.groundKnockbackFactor(false,DEFS.get("earth").effect("ground_knockback")));
    }
    @Test void missingProcTimeAndCooldownAreRejectedByActualCodec() {
        for(String element:List.of("metal","water")) {
            var data=FateDefinition.CODEC.encodeStart(JsonOps.INSTANCE,DEFS.get(element)).getOrThrow().getAsJsonObject();
            data.getAsJsonObject("effects").remove(element.equals("metal")?"metal_armor_break_ticks":"water_slow_cooldown");
            assertThrows(IllegalArgumentException.class,()->FateDefinition.CODEC.parse(JsonOps.INSTANCE,data).getOrThrow());
        }
    }
    @Test void affinityDefinitionsRoundTripInSyncedRegistryCodec() {
        for(var definition:DEFS.values()) assertEquals(definition,FateDefinition.CODEC.parse(JsonOps.INSTANCE,
                FateDefinition.CODEC.encodeStart(JsonOps.INSTANCE,definition).getOrThrow()).getOrThrow());
    }
}
