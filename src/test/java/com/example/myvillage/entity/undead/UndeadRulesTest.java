package com.example.myvillage.entity.undead;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class UndeadRulesTest {
    private static JsonObject source() throws IOException { return JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/wilderness/undead.json"))).getAsJsonObject(); }
    private static UndeadRules load(JsonObject json) { return UndeadRules.parse(new StringReader(json.toString())); }
    @Test void exactCoverageAndRegionalEligibility() throws Exception {
        var rules=load(source());assertEquals(18,rules.variants().size());
        for(var v:rules.variants())for(String region:UndeadRules.REGIONS)assertEquals(v.region().equals("common")||v.region().equals(region),v.acceptsRegion(region),v.id()+" "+region);
    }
    @Test void everyMoveHasWindupLockAndRecovery() throws Exception {
        for(var v:load(source()).variants())for(var a:v.actions()) {
            int windup=a.data().get("windup_ticks").getAsInt();
            assertTrue(a.data().get("turn_lock_tick").getAsInt()<windup);
            assertFalse(a.strikesAt(windup-1));
            assertTrue(a.totalTicks()-a.strikes().getLast()>load(source()).ticks("minimum_recovery_ticks"));
            for(int t=0;t<a.totalTicks();t++)assertEquals(a.strikes().contains(t),a.strikesAt(t));
        }
    }
    @Test void pairedClawsAndRegionalBurstHaveSeparateReceipts() throws Exception {
        var r=load(source());assertEquals(List.of(14,29),r.require("wandering_corpse").action("claw").strikes());
        assertEquals(List.of(30,44),r.require("beiyuan_bone_archer").action("shot").strikes());
        assertEquals(List.of(30,37,44),r.require("zhongzhou_bone_archer").action("shot").strikes());
        assertEquals("piercing",r.require("lingyue_bone_archer").action("shot").projectile());
    }
    @Test void malformedSchedulesAndUnboundedRuntimeAreRejected() throws Exception {
        reject(j->j.getAsJsonArray("variants").remove(0));
        reject(j->j.getAsJsonArray("variants").add(j.getAsJsonArray("variants").get(0).deepCopy()));
        reject(j->action(j).getAsJsonArray("strike_ticks").add(14));
        reject(j->action(j).getAsJsonArray("strike_ticks").set(0,new JsonPrimitive(13)));
        reject(j->action(j).addProperty("total_ticks",30));
        reject(j->action(j).addProperty("turn_lock_tick",15));
        reject(j->action(j).addProperty("projectile","tracking_through_walls"));
        reject(j->j.getAsJsonObject("runtime").addProperty("field_interval",0));
        reject(j->j.getAsJsonObject("runtime").addProperty("maximum_targets",1.5));
        reject(j->j.getAsJsonObject("runtime").addProperty("maximum_projectiles_per_shooter",10000));
    }
    private static JsonObject action(JsonObject j){return j.getAsJsonArray("variants").get(0).getAsJsonObject().getAsJsonArray("moves").get(0).getAsJsonObject();}
    private static void reject(Consumer<JsonObject> mutate) throws Exception {var json=source();mutate.accept(json);assertThrows(RuntimeException.class,()->load(json));}
}
