package com.example.myvillage.combat.art;

import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AscendantArtsTest {
    private final List<ArtDefinition> arts = ArtDefinition.all().stream().filter(art -> art.ascendant().isPresent()).toList();

    @Test void twoArtsPerRouteAndRealmUseDistinctMechanics() {
        var counts = arts.stream().collect(Collectors.groupingBy(
                art -> art.ascendant().orElseThrow().tier(),
                Collectors.groupingBy(art -> art.elementType().map(e -> e.id()).orElse(art.family()), Collectors.counting())));
        var perRoute = Map.of("sword", 2L, "spear", 2L, "fist", 2L, "metal", 2L, "wood", 2L, "water", 2L, "fire", 2L, "earth", 2L);
        assertEquals(Map.of("nascent_soul", perRoute, "spirit_transformation", perRoute), counts);
        assertEquals(AscendantArt.Mechanic.values().length, arts.stream()
                .map(art -> art.ascendant().orElseThrow().mechanic()).distinct().count());
        assertEquals(Set.of("nanjiang", "moyu"), AscendantArt.shelfRegions());
        assertEquals("zhongzhou", AscendantArt.expeditionRegion());
    }

    @Test void studyDefinitionsAndAttackPhasesMatchTheServerRegistry() throws Exception {
        for (var art : arts) {
            var spec = art.ascendant().orElseThrow();
            var path = Path.of("src/main/resources/data/yuanxian/yuanxian/technique/" + art.id().getPath() + ".json");
            var row = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var technique = TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE, row).getOrThrow();
            assertEquals(TechniqueCategory.ACTIVE, technique.category());
            assertEquals(art.school(), technique.school().orElseThrow());
            assertEquals(-1, art.slot());
            assertEquals(spec.spiritTransformation() ? 4 : 3, technique.grade());
            assertEquals(spec.spiritTransformation() ? 16800 : 9000, technique.study().points());
            assertEquals("yuanxian:" + spec.tier(), row.getAsJsonObject("requirements").get("minimum_realm").getAsString());
            var active = technique.effects().orElseThrow().active().orElseThrow();
            assertEquals(art.id(), active.skill());
            assertEquals(art.qiCost(), active.qiCost());
            var move = art.move(0, 1);
            assertEquals(spec.geometry().range(), move.range());
            assertEquals(spec.timing().windup(), move.activeStartTick());
            int hold = spec.mechanic() == AscendantArt.Mechanic.DRAGON_STANCE ? spec.timing().duration() : 0;
            assertEquals(spec.timing().windup() + hold + 1 + spec.timing().recovery(), move.totalTicks());
        }
    }
}
