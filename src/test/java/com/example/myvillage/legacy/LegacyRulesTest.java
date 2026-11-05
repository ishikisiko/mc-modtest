package com.example.myvillage.legacy;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.cultivation.data.*;
import com.example.myvillage.entity.beast.BeastDefinitions;
import com.example.myvillage.client.entity.beast.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRulesTest {
    @Test void fourExecutorsHaveRealLearnableDefinitionsAndNoExtraMeleeStep() throws Exception {
        var arts=ArtDefinition.all().stream().filter(a->a.family().equals("legacy")).toList();
        assertEquals(4,arts.size());
        assertEquals(Set.of("sword","shadow","fire","ruler"),LegacyRules.get().arts().stream().map(LegacyRules.Art::mode).collect(java.util.stream.Collectors.toSet()));
        for(var art:arts){
            var path=Path.of("src/main/resources/data/myvillage/myvillage/technique/"+art.id().getPath()+".json");
            var technique=TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(Files.readString(path))).getOrThrow();
            assertEquals(art.qiCost(),technique.effects().orElseThrow().active().orElseThrow().qiCost());
            assertEquals(-1,art.slot());assertTrue(art.move(0,1).step().isEmpty());
        }
        var school=SchoolDefinition.CODEC.parse(JsonOps.INSTANCE,LegacyRules.read("/data/myvillage/myvillage/school/legacy.json")).getOrThrow();
        assertEquals(Optional.of(SchoolRuntime.SPELL),school.runtime());
    }
    @Test void everyBeastHasMatchingModelAnimationAndRecoverableAttacks() throws Exception {
        for(var beast:LegacyRules.get().beasts()){
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("myvillage",beast.id());
            var definition=BeastDefinitions.bundled().require(id);
            for(var move:definition.moves())assertTrue(move.totalTicks()-move.activeTicks().last()>=12);
            try(var m=Files.newBufferedReader(Path.of("src/main/resources/assets/myvillage/beast/"+beast.id()+"_model.json"));
                var a=Files.newBufferedReader(Path.of("src/main/resources/assets/myvillage/beast/"+beast.id()+"_animations.json"))){
                var model=BeastModelFile.parse(beast.id(),id,m);var clips=BeastAnimationFile.parse(beast.id(),id,a);
                clips.check(beast.id(),model,definition);
            }
        }
    }
    @Test void invalidDataCannotSilentlyDisableGameplayBounds(){
        var root=LegacyRules.read("/data/myvillage/legacy/encounters.json");
        root.getAsJsonArray("arts").get(0).getAsJsonObject().addProperty("range",500);
        assertThrows(IllegalArgumentException.class,()->LegacyRules.parse(root));
        var duplicate=LegacyRules.read("/data/myvillage/legacy/encounters.json");
        duplicate.getAsJsonArray("mobs").add(duplicate.getAsJsonArray("mobs").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class,()->LegacyRules.parse(duplicate));
    }
}
