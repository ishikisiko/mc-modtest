package com.example.myvillage.combat.art;

import com.example.myvillage.combat.SkillBindingRules;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LockedSwordTest {
    @Test void threeExistingTechniquesAreExecutableWithoutReplacingAutomaticSlots() throws Exception {
        Set<String> ids=Set.of("gengjin_jianjue","tiangang_jiandian","taibai_jianjing");
        var arts=ArtDefinition.all().stream().filter(a->a.lock().isPresent()).toList();
        assertEquals(ids,arts.stream().map(a->a.id().getPath()).collect(java.util.stream.Collectors.toSet()));
        for(var art:arts) {
            assertEquals(-1,art.slot());assertEquals("sword",art.family());
            assertTrue(ArtDefinition.forSlot("sword",-1).isEmpty());
            var raw=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/myvillage/technique/"+art.id().getPath()+".json")));
            var technique=TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE,raw).getOrThrow();
            assertEquals(SkillBindingRules.Kind.ART,SkillBindingRules.kind(art.id(),technique));
            assertFalse(SkillBindingRules.assignable(art.id(),Set.of(),technique));
            assertTrue(SkillBindingRules.assignable(art.id(),Set.of(art.id()),technique));
            assertTrue(technique.lineage().isPresent());
            assertFalse(technique.elements().isEmpty());
            assertEquals(art.qiCost(),technique.effects().orElseThrow().active().orElseThrow().qiCost());
        }
        assertEquals("qinglian_jiange",ArtDefinition.forSlot("sword",0).orElseThrow().id().getPath());
        assertEquals("huifeng_jianzhen",ArtDefinition.forSlot("sword",1).orElseThrow().id().getPath());
    }
    @Test void eachStrikeIsConsumedEvenWhenItsDefenderEvades() {
        for(var art:ArtDefinition.all())art.lock().ifPresent(lock->{
            int next=0;List<Integer> ticks=new ArrayList<>();
            for(int age=0;age<lock.totalTicks();age++)if(lock.strikeDue(age,next)){ticks.add(age);next++;}
            assertEquals(lock.strikes(),ticks.size());
            assertEquals(lock.firstStrike(),ticks.getFirst());assertEquals(lock.lastStrike(),ticks.getLast());
            assertFalse(lock.strikeDue(lock.totalTicks(),next));
            var move=art.move(0,1);
            assertEquals(lock.totalTicks(),move.animation().lengthTicks());
            assertEquals(lock.lastStrike(),move.activeEndTick());
            assertTrue(move.step().isEmpty());assertEquals(0,move.reaction().hitstunTicks());
            assertEquals(0,move.feedback().hitStopTicks());
        });
    }
    @Test void dodgeWindowsAreDeterministicAndExclusiveAtBothBoundaries() {
        var d=LockedSwordDefinition.defence();long end=100;
        assertEquals(0,d.damageFraction(end-1,end));
        assertEquals(d.tailDamageFraction(),d.damageFraction(end,end));
        assertEquals(d.tailDamageFraction(),d.damageFraction(end+d.tailTicks()-1,end));
        assertEquals(1,d.damageFraction(end+d.tailTicks(),end));
        assertEquals(1,d.damageFraction(end+100,end));
    }
    @Test void broadAcquisitionStillRejectsTargetsBehindCaster() {
        for(var art:ArtDefinition.all())art.lock().ifPresent(l->{
            assertTrue(l.inCone(1));assertTrue(l.inCone(Math.cos(Math.toRadians(l.acquireArc()/2-1))));
            assertFalse(l.inCone(-1));assertFalse(l.inCone(Double.NaN));
        });
    }
    @Test void judgmentKeepsFullPrimaryDamageAndAuthoredSecondaryScale() {
        var art=ArtDefinition.find(ResourceLocation.parse("myvillage:taibai_jianjing")).orElseThrow();
        var lock=art.lock().orElseThrow();
        assertTrue(lock.hasSplash());assertTrue(lock.radius()>1);assertTrue(art.phases().getFirst().targets()>1);
        assertEquals(1,lock.targetMultiplier(true));
        assertEquals(lock.splashMultiplier(),lock.targetMultiplier(false));
        assertTrue(lock.targetMultiplier(false)>0 && lock.targetMultiplier(false)<1);
        assertEquals(art.move(0,1).damageMultiplier()*lock.splashMultiplier(),
                art.move(0,lock.targetMultiplier(false)).damageMultiplier(),1e-9);
        for(var other:ArtDefinition.all())other.lock().filter(l->!l.mode().equals("judgment")).ifPresent(l->{
            assertFalse(l.hasSplash());assertEquals(1,l.targetMultiplier(false));
        });
    }
    @Test void visualAndSplashBoundsRejectUnsafeAuthoredValues() {
        var row=LockedSwordDefinition.document().getAsJsonArray("arts").get(2).getAsJsonObject().getAsJsonObject("lock");
        for(double badValue:new double[]{-1,1.01,Double.NaN}) {
            var bad=row.deepCopy();bad.addProperty("splash_multiplier",badValue);
            assertThrows(IllegalArgumentException.class,()->LockedSwordDefinition.parse(bad));
        }
        var bad=row.deepCopy();bad.getAsJsonObject("visual").addProperty("sword_length",100);
        assertThrows(IllegalArgumentException.class,()->LockedSwordDefinition.parse(bad));
    }
    @Test void invalidAuthoredTimingsAndDefenceCannotLoad() {
        var r=LockedSwordDefinition.document().getAsJsonArray("arts").get(0).getAsJsonObject().getAsJsonObject("lock");
        for(String key:List.of("strikes","strike_interval_ticks","first_strike_ticks","recovery_ticks","range")) {
            var bad=r.deepCopy();bad.addProperty(key,0);assertThrows(IllegalArgumentException.class,()->LockedSwordDefinition.parse(bad));
        }
        var fractional=r.deepCopy();fractional.addProperty("strikes",1.5);
        assertThrows(IllegalArgumentException.class,()->LockedSwordDefinition.parse(fractional));
        assertThrows(IllegalArgumentException.class,()->new LockedSwordDefinition.Defence(6,Double.NaN));
    }
}
