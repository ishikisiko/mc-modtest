package com.example.myvillage.combat.art;

import com.example.myvillage.cultivation.technique.TechniqueDefinition;
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

class EarlyRealmArtsTest {
    private final List<ArtDefinition> arts = ArtDefinition.all().stream().filter(art -> art.earlyRealm().isPresent()).toList();

    @Test void rosterUsesEightRoutesAtEachRealm() {
        var counts = arts.stream().collect(Collectors.groupingBy(
                art -> art.earlyRealm().orElseThrow().tier(),
                Collectors.groupingBy(art -> art.elementType().map(e -> e.id()).orElse(art.family()), Collectors.counting())));
        assertEquals(Map.of("sword", 1L, "spear", 1L, "fist", 1L, "metal", 1L, "wood", 1L, "water", 1L, "fire", 1L, "earth", 1L),
                counts.get("qi_refining"));
        assertEquals(Map.of("sword", 1L, "spear", 2L, "fist", 2L, "metal", 1L, "wood", 1L, "water", 1L, "fire", 1L, "earth", 1L),
                counts.get("foundation_establishment"));
        assertEquals(Set.of("lingyue", "beiyuan"), EarlyRealmArt.shelfRegions());
    }

    @Test void manualAndTimingMatchRegisteredArts() throws Exception {
        for (var art : arts) {
            var spec = art.earlyRealm().orElseThrow();
            var path = Path.of("src/main/resources/data/yuanxian/yuanxian/technique/" + art.id().getPath() + ".json");
            var row = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var definition = TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE, row).getOrThrow();
            assertEquals(-1, art.slot());
            assertEquals(spec.foundation() ? 2 : 1, definition.grade());
            assertEquals(spec.foundation() ? 1800 : 600, definition.study().points());
            assertEquals("yuanxian:" + spec.tier(), row.getAsJsonObject("requirements").get("minimum_realm").getAsString());
            assertEquals(art.id(), definition.effects().orElseThrow().active().orElseThrow().skill());
            assertEquals(art.qiCost(), definition.effects().orElseThrow().active().orElseThrow().qiCost());
            var move = art.move(0, 1);
            assertEquals(spec.range(), move.range());
            assertEquals(spec.windup(), move.activeStartTick());
            assertEquals(spec.lastStrike() + 1 + spec.recovery(), move.totalTicks());
        }
    }
}
