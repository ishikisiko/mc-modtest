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
    @Test void deployedRulerRoomContainsEveryAuthoredCornerAtEveryHeading() throws Exception {
        var model=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/myvillage/models/item/zhenyue_ruler.json"))).getAsJsonObject();
        double scale=LegacyRules.get().visual("ruler_model_scale");
        var bounds=LegacyEffects.rulerBounds(net.minecraft.world.phys.Vec3.ZERO);
        assertTrue(bounds.getYsize()>3.5);
        for(var element:model.getAsJsonArray("elements")) {
            var box=element.getAsJsonObject();var low=box.getAsJsonArray("from");var high=box.getAsJsonArray("to");
            for(int corner=0;corner<8;corner++)for(int degrees=0;degrees<360;degrees+=15) {
                double x=((corner&1)==0?low:high).get(0).getAsDouble()/16-.5;
                double y=((corner&2)==0?low:high).get(1).getAsDouble()/16;
                double z=((corner&4)==0?low:high).get(2).getAsDouble()/16-.5;
                double a=Math.toRadians(degrees),rx=(x*Math.cos(a)-z*Math.sin(a))*scale,rz=(x*Math.sin(a)+z*Math.cos(a))*scale;
                assertTrue(rx>=bounds.minX&&rx<=bounds.maxX&&y*scale>=bounds.minY&&y*scale<=bounds.maxY&&rz>=bounds.minZ&&rz<=bounds.maxZ);
            }
        }
    }
    @Test void visualDataRejectsUnboundedMeshesAndInvalidColors() {
        var root=LegacyRules.read("/data/myvillage/legacy/encounters.json");
        root.getAsJsonObject("visual").getAsJsonObject("parameters").addProperty("segments",1_000_000);
        assertThrows(IllegalArgumentException.class,()->LegacyRules.parse(root));
        var color=LegacyRules.read("/data/myvillage/legacy/encounters.json");
        color.getAsJsonObject("visual").getAsJsonObject("palette").getAsJsonArray("fire").set(0,new JsonPrimitive(999));
        assertThrows(IllegalArgumentException.class,()->LegacyRules.parse(color));
    }
}
