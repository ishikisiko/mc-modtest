package com.example.myvillage.orchard;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrchardRulesTest {
    @Test void bundledRulesMatchTheAuthoredTuning() throws Exception {
        var root=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/orchard/rules.json"))).getAsJsonObject();
        assertEquals(OrchardRules.get(),OrchardRules.parse(root));
        assertTrue(OrchardRules.get().fertileSaplingChance()<OrchardRules.get().saplingChance());
    }
    @Test void malformedGrowthBoundsFailInsteadOfBecomingInfiniteGrowth() throws Exception {
        var root=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/orchard/rules.json"))).getAsJsonObject();
        root.getAsJsonObject("growth").addProperty("fruit_tick_chance",0);
        assertThrows(IllegalArgumentException.class,()->OrchardRules.parse(root));
        root.getAsJsonObject("growth").addProperty("fruit_tick_chance",2.5);
        assertThrows(IllegalArgumentException.class,()->OrchardRules.parse(root));
    }
}
