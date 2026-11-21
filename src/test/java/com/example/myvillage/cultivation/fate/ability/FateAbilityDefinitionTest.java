package com.example.myvillage.cultivation.fate.ability;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.cultivation.fate.FateState;
import com.example.myvillage.cultivation.fate.FateDefinition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class FateAbilityDefinitionTest {
    private static ResourceLocation id(String value) { return ResourceLocation.parse("yuanxian:" + value); }

    private static JsonObject config() {
        return JsonParser.parseString("""
                {"schema":1,"abilities":[{"id":"yuanxian:wood_root","required_fate":"yuanxian:wood_spirit_body",
                "name":"乙木缠根","description":"生成缠根领域","cooldown_ticks":400,
                "cost_fraction":0.1,"minimum_cost":1,"radius":5}]}
                """).getAsJsonObject();
    }

    private static FateState confirmed(String first) {
        var ids = List.of("yuanxian:" + first, "yuanxian:robust", "yuanxian:bright");
        return new FateState(1, 1, ids, "", ids, true, 0, 0, 0, 0);
    }

    private static FateDefinition fate(boolean enabled) {
        return new FateDefinition("天命名称", "天命描述", 0, 1, "体质", enabled,
                Map.of(), Optional.empty(), 0, 5);
    }

    @Test void bundledAbilitiesStaySeparateFromTechniques() {
        var ability = FateAbilityDefinition.find(id("wood_root")).orElseThrow();
        assertEquals(id("wood_spirit_body"), ability.requiredFate());
        assertEquals(400, ability.cooldownTicks());
        assertEquals(5, ability.radius());
        assertEquals(10, ability.cost(100));
        var pursuit = FateAbilityDefinition.find(id("companion_sword_pursuit")).orElseThrow();
        assertEquals(id("primordial_sword_bone"), pursuit.requiredFate());
        var first = com.example.myvillage.cultivation.fate.FateReworkRules.get().stages().getFirst();
        assertEquals(first.power(), pursuit.cost(10000));
        assertEquals(List.of(ability, pursuit), FateAbilityDefinition.all());
        assertTrue(FateAbilityDefinition.find(id("metal_spirit_body")).isEmpty());
        assertFalse(ability.name().isBlank());
        assertFalse(ability.description().isBlank());
    }

    @Test void candidateOrDifferentConfirmedFateDoesNotGrantAbility() {
        var ability = FateAbilityDefinition.find(id("wood_root")).orElseThrow();
        assertFalse(ability.granted(FateState.empty(),fate(true)));
        var draft = FateState.empty().choice(List.of("yuanxian:wood_spirit_body"), "", List.of(), false, 1);
        assertFalse(ability.granted(draft,fate(true)));
        assertFalse(ability.granted(confirmed("water_spirit_body"),fate(true)));
        assertTrue(ability.granted(confirmed("wood_spirit_body"),fate(true)));
        assertTrue(ability.granted(confirmed("wood_spirit_body").afterDeath(),fate(true)));
        assertFalse(ability.granted(confirmed("wood_spirit_body").resetChoices(),fate(true)));
    }

    @Test void disabledOrRemovedDefinitionRevokesAbilityWithoutErasingSavedChoice() {
        var ability = FateAbilityDefinition.find(id("wood_root")).orElseThrow();
        var state = confirmed("wood_spirit_body");
        assertFalse(ability.granted(state,(FateDefinition)null));
        assertFalse(ability.granted(state,fate(false)));
        assertFalse(ability.granted(state,RegistryAccess.EMPTY));
        assertTrue(ability.granted(state,fate(true)));
        assertTrue(state.selected().contains(ability.requiredFate().toString()));
    }

    @Test void percentageCostRoundsUpAndNeverOverflowsLongCapacity() {
        var ability = FateAbilityDefinition.find(id("wood_root")).orElseThrow();
        assertEquals(1, ability.cost(0));
        assertEquals(1, ability.cost(1));
        assertEquals(1, ability.cost(10));
        assertEquals(2, ability.cost(11));
        assertEquals(11, ability.cost(101));
        assertEquals(922337203685477581L, ability.cost(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> ability.cost(-1));
    }

    @Test void malformedOrDuplicateConfigurationIsRejected() {
        var duplicate = config();
        duplicate.getAsJsonArray("abilities").add(duplicate.getAsJsonArray("abilities").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> FateAbilityDefinition.parse(duplicate));
        for (String field : List.of("cost_fraction", "radius", "cooldown_ticks", "minimum_cost")) {
            var invalid = config();
            invalid.getAsJsonArray("abilities").get(0).getAsJsonObject().addProperty(field, 0);
            assertThrows(IllegalArgumentException.class, () -> FateAbilityDefinition.parse(invalid));
        }
        var fractionalTicks = config();
        fractionalTicks.getAsJsonArray("abilities").get(0).getAsJsonObject().addProperty("cooldown_ticks", 1.5);
        assertThrows(IllegalArgumentException.class, () -> FateAbilityDefinition.parse(fractionalTicks));
        var textNumber = config();
        textNumber.getAsJsonArray("abilities").get(0).getAsJsonObject().addProperty("cost_fraction", "0.1");
        assertThrows(IllegalArgumentException.class, () -> FateAbilityDefinition.parse(textNumber));
        var reserved = config();
        reserved.getAsJsonArray("abilities").get(0).getAsJsonObject().addProperty("id", "yuanxian:unassigned");
        assertThrows(IllegalArgumentException.class, () -> FateAbilityDefinition.parse(reserved));
    }
}
