package com.example.myvillage.forging;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class WorkshopToolSpecialtiesTest {
    @Test void eachToolCanChooseItsOwnSpecialtyWithTheRecipeMaterial() {
        var rules = ForgingRules.get();
        var workshop = WorkshopRules.get();
        Map<String, String> specialties = Map.of("pickaxe", ForgeMark.SEEK_VEIN, "axe", ForgeMark.REPLANT_TREE,
                "shovel", ForgeMark.GRADE_GROUND, "hoe", ForgeMark.HARVEST_HERBS);
        for (String material : List.of("forged", "zhuji_baiqing_lingyun", "jindan_jinwu")) {
            for (var choice : specialties.entrySet()) {
                var recipe = rules.recipe(material + "_" + choice.getKey()).orElseThrow();
                assertEquals(List.of("tough", choice.getValue()), recipe.traits());
                assertEquals(Map.of(recipe.infusionMaterial(), 1, workshop.spirit_ingredient(), 2),
                        workshop.infusionCosts(recipe, choice.getValue()));
            }
        }
    }

    @Test void soundingSearchStaysWithinTheWallAndPicksTheClosestEligibleBlock() {
        var origin = BlockPos.ZERO;
        var blocks = Set.of(origin, origin.west(), origin.east().above(2), origin.east(4), origin.east(3).above(3));
        assertEquals(origin.east().above(2), WorkshopToolSpecialties.findOre(origin, Direction.EAST, 4, blocks::contains));
        assertEquals(origin.west(), WorkshopToolSpecialties.findOre(origin, Direction.WEST, 4, blocks::contains));
        assertNull(WorkshopToolSpecialties.findOre(origin, Direction.EAST, 2, blocks::contains));
        assertEquals(origin.east(4), WorkshopToolSpecialties.findOre(origin, Direction.EAST, 4,
                p -> blocks.contains(p) && !p.equals(origin.east().above(2))));
    }

    @Test void authoredEffectsHaveBoundedQualityValuesAndSeedlingsForBothOakLogSpecies() throws Exception {
        var traits = ForgingRules.get();
        assertEquals(8, traits.trait(ForgeMark.SEEK_VEIN).value(4));
        assertEquals(1, traits.trait(ForgeMark.GRADE_GROUND).value(2));
        assertEquals(2, traits.trait(ForgeMark.GRADE_GROUND).value(3));
        assertEquals(.5, traits.trait(ForgeMark.HARVEST_HERBS).value(4));
        var tools = WorkshopSpecialtyRules.get();
        assertEquals(Set.of(ResourceLocation.parse("minecraft:azalea"), ResourceLocation.parse("minecraft:oak_sapling")),
                tools.saplings().stream().filter(s -> s.log().equals(ResourceLocation.parse("minecraft:oak_log")))
                        .map(WorkshopSpecialtyRules.Sapling::plant).collect(java.util.stream.Collectors.toSet()));
        assertTrue(tools.saplings().stream().anyMatch(s -> s.plant().equals(ResourceLocation.parse("yuanxian:ningyuan_sapling"))));
        assertEquals(1,tools.herbsRetained());
        assertEquals(2,tools.herbsBonus());
        String source;
        try (var in = ForgeMark.class.getResourceAsStream(ForgingRules.RESOURCE)) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String outOfBounds = source.replace("\"values\": [\n        0.15,", "\"values\": [\n        -0.01,");
        assertNotEquals(source, outOfBounds);
        assertThrows(IllegalArgumentException.class, () -> ForgingRules.parse(new StringReader(outOfBounds)));
    }
}
