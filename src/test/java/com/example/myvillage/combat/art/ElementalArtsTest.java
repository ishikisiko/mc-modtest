package com.example.myvillage.combat.art;

import com.example.myvillage.combat.elemental.*;
import com.example.myvillage.combat.SkillBindingRules;
import com.example.myvillage.cultivation.data.*;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.client.combat.ElementalShape;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ElementalArtsTest {
    private List<ArtDefinition> arts(){return ArtDefinition.all().stream().filter(a->a.elemental().isPresent()).toList();}
    @Test void everyElementHasCompleteIndependentKitWithNoWeaponSlots() throws Exception {
        assertEquals(20,arts().size());
        for(var element:DamageType.values()) {
            if(element==DamageType.PHYSICAL)continue;
            var kit=arts().stream().filter(a->a.elemental().orElseThrow().element()==element).toList();
            assertEquals(Set.of("basic","ward","burst","field"),kit.stream().map(a->a.elemental().get().role()).collect(java.util.stream.Collectors.toSet()));
            for(var art:kit) {
                assertEquals(-1,art.slot());assertTrue(art.lock().isEmpty());assertEquals("elemental",art.family());
                var path=Path.of("src/main/resources/data/myvillage/myvillage/technique/"+art.id().getPath()+".json");
                var technique=TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(Files.readString(path))).getOrThrow();
                assertTrue(technique.requirements().minimumElementAffinity().isEmpty(),"Random roots must not lock a basic school");
                assertEquals(SkillBindingRules.Kind.ART,SkillBindingRules.kind(art.id(),technique));
                assertEquals(art.qiCost(),technique.effects().orElseThrow().active().orElseThrow().qiCost());
                assertEquals(Optional.of(art.school()),technique.school());
                var school=SchoolDefinition.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/myvillage/school/"+art.school().getPath()+".json")))).getOrThrow();
                assertEquals(Optional.of(SchoolRuntime.SPELL),school.runtime());assertTrue(school.weaponFamily().isEmpty());
                assertEquals(List.of(ResourceLocation.parse("myvillage:"+element.id())),school.elementLean());
            }
        }
    }
    @Test void chargesCapExpireAndConsumeOnlyTheirOwnInstance() {
        var metal=new ElementalCharge();var water=new ElementalCharge();
        for(int i=0;i<10;i++)metal.gain(100+i,3,200);
        water.gain(100,3,200);
        assertEquals(3,metal.value(110));assertEquals(3,metal.consume(110));assertEquals(0,metal.consume(110));
        assertEquals(1,water.value(299));assertEquals(0,water.value(300));
        assertEquals(1.9,ElementalCharge.multiplier(3,.3),1e-9);
    }
    @Test void timelinesHaveExactlyAuthoredPulsesAndNoMeleeStepOrTrail() {
        for(var art:arts()) {
            var spell=art.elemental().orElseThrow();var move=art.move(0,1);
            int pulse=0;for(int age=0;age<move.totalTicks();age++)if(spell.due(age,pulse))pulse++;
            assertEquals(spell.pulses(),pulse);assertEquals(spell.windup(),move.activeStartTick());
            assertEquals(spell.lastStrike(),move.activeEndTick());assertTrue(move.step().isEmpty());assertTrue(move.trail().isEmpty());
            assertEquals(0,move.reaction().hitstunTicks());
        }
    }
    @Test void onlyBasicHitsGainAndOnlyWardOrBurstConsume() {
        for(var art:arts()) {
            var spell=art.elemental().orElseThrow();assertEquals(Set.of("ward","burst").contains(spell.role()),spell.consumes());
            if(spell.role().equals("ward"))assertTrue(spell.wardFraction()>0&&spell.wardFraction()<1);
        }
    }
    @Test void newElementShapesAreBoundedAndDistinct() {
        var signatures=new HashSet<Integer>();
        for(var art:arts()) {
            var spell=art.elemental().orElseThrow();var mesh=ElementalShape.mesh(spell,false,spell.windup(),art.particleCount());
            assertFalse(mesh.isEmpty());assertTrue(mesh.size()<256);
            for(var q:mesh)for(var p:List.of(q.a(),q.b(),q.c(),q.d())) {
                assertTrue(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()));
                assertTrue(Math.abs(p.x())<=spell.radius()+1 && Math.abs(p.z())<=spell.radius()+1);
            }
            if(spell.role().equals("field"))signatures.add(mesh.hashCode());
        }
        assertEquals(5,signatures.size());
    }
}
