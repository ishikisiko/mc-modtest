package com.example.myvillage.combat.art;

import com.example.myvillage.combat.SkillBindingRules;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoldenCoreArtsTest {
    @Test void everyRouteHasTwoLearnableBindableArts() throws Exception {
        var found = EnumSet.noneOf(GoldenCoreArt.Mechanic.class);
        var byRoute = new HashMap<String, Integer>();
        for (ArtDefinition art : ArtDefinition.all()) {
            if (art.goldenCore().isEmpty()) continue;
            var spec = art.goldenCore().orElseThrow();
            assertTrue(found.add(spec.mechanic()));
            byRoute.merge(art.school().getPath(), 1, Integer::sum);
            var path = Path.of("src/main/resources/data/yuanxian/yuanxian/technique", art.id().getPath() + ".json");
            var definition = TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(Files.readString(path))).getOrThrow();
            assertEquals(3, definition.grade());
            assertEquals("golden_core", definition.requirements().minimumRealm().orElseThrow().getPath());
            assertTrue(definition.previous().isPresent());
            assertEquals(4800, definition.study().points());
            assertTrue(SkillBindingRules.assignable(art.id(), Set.of(art.id()), definition));
            assertEquals(-1, art.slot());
            assertEquals(spec.element().isPresent(), art.family().equals("elemental"));
            assertEquals(spec.windup(), art.move(0, 1).activeStartTick());
            assertEquals(spec.lastStrike(), art.move(0, 1).activeEndTick());
        }
        assertEquals(EnumSet.allOf(GoldenCoreArt.Mechanic.class), found);
        assertEquals(Map.of("sword", 2, "spear", 2, "fist", 2, "metal_arts", 2,
                "wood_arts", 2, "water_arts", 2, "fire_arts", 2, "earth_arts", 2), byRoute);
    }

    @Test void rangesAndWidthsMatchTheRevisedDesign() {
        var sword = spec("chuanyun_yijian");
        assertEquals(14, sword.range());
        assertEquals(2, sword.width());
        assertEquals(3.3, spec("kaibei_cunjin").range());
        assertEquals(40, spec("rongxin_jian").range());
        var stamp = spec("luopan_yin");
        assertEquals(36, stamp.range());
        assertEquals(6.125, stamp.radius());
        assertEquals(2.5, stamp.inner());
        assertEquals(6.3, GoldenCoreArt.rules().spearTipMax());
        assertEquals(48, GoldenCoreArt.rules().aimArc());
        assertEquals(Set.of("donghai", "ximo"), GoldenCoreArt.rules().shelfRegions());
    }

    private static GoldenCoreArt spec(String path) {
        return ArtDefinition.find(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("yuanxian", path))
                .orElseThrow().goldenCore().orElseThrow();
    }
}
