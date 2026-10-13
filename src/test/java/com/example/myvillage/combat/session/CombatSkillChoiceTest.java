package com.example.myvillage.combat.session;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.cultivation.data.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class CombatSkillChoiceTest {
    private final CombatStyles styles = CombatTestData.styles();
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("myvillage", name); }
    private static TechniqueDefinition technique(int grade, String skill, String school) {
        return new TechniqueDefinition("cultivation.technique.test", TechniqueCategory.ACTIVE, grade, List.of(),
                TechniqueRequirements.none(), Optional.of(id(school)), Optional.empty(),
                Optional.of(new TechniqueEffects(Optional.empty(),
                        Optional.of(new TechniqueEffects.Active(id(skill), 8, 1)), Optional.empty(), Optional.empty())));
    }
    private Optional<CombatSkillActions.Choice> choose(List<ResourceLocation> learned,
                                                     Map<ResourceLocation, TechniqueDefinition> definitions, String family) {
        return CombatSkillActions.choose(learned, key -> Optional.ofNullable(definitions.get(key)),
                school -> school.equals(id("sword")) ? Optional.of("sword") : Optional.empty(),
                Optional.of(family), styles);
    }
    @Test void learnedSkillAndMatchingSchoolFamilyAreAllRequired() {
        Map<ResourceLocation, TechniqueDefinition> definitions = Map.of(id("jianqi_jue"), technique(1, "jianqi_jue", "sword"));
        assertTrue(choose(List.of(), definitions, "sword").isEmpty());
        assertTrue(choose(List.of(id("jianqi_jue")), definitions, "fist").isEmpty());
        assertTrue(choose(List.of(id("unknown")), definitions, "sword").isEmpty());
        assertEquals(id("jianqi_jue"), choose(List.of(id("jianqi_jue")), definitions, "sword").orElseThrow().technique());
        assertTrue(choose(List.of(id("bad")), Map.of(id("bad"), technique(1, "missing_skill", "sword")), "sword").isEmpty());
        assertTrue(choose(List.of(id("bad")), Map.of(id("bad"), technique(1, "jianqi_jue", "spear")), "sword").isEmpty());
    }
    @Test void highestGradeThenLexicalIdMakesSelectionDeterministic() {
        var definitions = Map.of(id("z"), technique(2, "jianqi_jue", "sword"),
                id("a"), technique(2, "jianqi_jue", "sword"), id("first"), technique(1, "jianqi_jue", "sword"));
        assertEquals(id("a"), choose(List.of(id("z"), id("first"), id("a")), definitions, "sword").orElseThrow().technique());
    }
}
