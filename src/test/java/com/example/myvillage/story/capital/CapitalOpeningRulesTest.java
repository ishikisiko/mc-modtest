package com.example.myvillage.story.capital;

import com.example.myvillage.sim.life.LifeRules;
import com.example.myvillage.worldplan.WorldPlanRules;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapitalOpeningRulesTest {
    private CapitalOpeningRules rules() throws Exception {
        try (var in = Files.newBufferedReader(Path.of("src/main/resources/data/myvillage/story/capital_opening.json"))) { return CapitalOpeningRules.read(in); }
    }
    @Test void everyCurrentCityHasRealBedsWorkPointsAndBoundedHomeWorkDistances() throws Exception {
        var opening = rules();
        try (var input = getClass().getResourceAsStream(WorldPlanRules.V11_RESOURCE)) {
            assertNotNull(input);
            var world = new WorldPlanRules(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            int maxDistance = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/world_sim/life_rules.json")))
                    .getAsJsonObject().get("max_path_distance").getAsInt();
            for (var city : world.capitals.bundles) {
                var places = city.life().getAsJsonArray("places");
                for (var resident : opening.residents(city.regionId)) {
                    com.google.gson.JsonObject home = null, work = null;
                    for (var p : places) {
                        var point = p.getAsJsonObject(); var id = point.get("id").getAsString();
                        if (id.equals(resident.home())) home = point;
                        if (id.equals(resident.work())) work = point;
                    }
                    assertNotNull(home,city.cityId+":"+resident.home()); assertNotNull(work,city.cityId+":"+resident.work());
                    assertEquals("rest",home.get("kind").getAsString()); assertTrue(home.get("block").getAsString().endsWith("_bed"));
                    assertEquals(resident.role().equals("guide")?"entry":"work",work.get("kind").getAsString());
                    long distance = 0;
                    for (int axis=0;axis<3;axis++) { long delta=home.getAsJsonArray("pos").get(axis).getAsInt()-work.getAsJsonArray("pos").get(axis).getAsInt();distance+=delta*delta; }
                    assertTrue(distance<=(long)maxDistance*maxDistance,city.cityId+":"+resident.role());
                }
            }
        }
    }
    @Test void fractionalCountsAndDuplicateRolesCannotLoad() throws Exception {
        var path = Path.of("src/main/resources/data/myvillage/story/capital_opening.json");
        var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject(); json.getAsJsonObject("work").addProperty("count",1.5);
        assertThrows(ArithmeticException.class,()->CapitalOpeningRules.read(new StringReader(json.toString())));
        json.getAsJsonObject("work").addProperty("count",3);
        json.getAsJsonArray("residents").get(3).getAsJsonObject().addProperty("role","herbalist");
        assertThrows(IllegalArgumentException.class,()->CapitalOpeningRules.read(new StringReader(json.toString())));
    }
}
