package com.example.myvillage.ruins;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuinRulesTest {
    private JsonObject json() throws IOException {
        try (var in = getClass().getResourceAsStream("/data/myvillage/ruins/danxia.json")) {
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private RuinRules parse(JsonObject j) { return RuinRules.parse(new StringReader(j.toString())); }
    @Test void shippedRulesAndAllFourRotationsPreserveOffsets() {
        var r=RuinRules.get();
        assertEquals(12,r.garden().size());
        assertEquals("myvillage:qingmu_changchun_gong",r.manual());
        var d=r.vault().subtract(r.core());
        assertEquals(new RuinRules.Pos(9,0,5),d);
        assertEquals(new RuinRules.Pos(-5,0,9),d.rotate(1));
        assertEquals(d,d.rotate(1).rotate(3));
        for (var plant:r.garden()) for (int turn=0;turn<4;turn++)
            assertEquals(plant.pos().subtract(r.core()),plant.pos().subtract(r.core()).rotate(turn).rotate(4-turn));
    }
    @Test void invalidTargetsAndCountsAreRejected() throws IOException {
        var j=json(); j.add("vault",j.get("core"));
        assertThrows(IllegalArgumentException.class,()->parse(j));
        var k=json(); k.getAsJsonArray("garden").get(0).getAsJsonObject().getAsJsonArray("pos").set(1,new JsonPrimitive(0));
        assertThrows(IllegalArgumentException.class,()->parse(k));
        var l=json(); l.getAsJsonArray("vault_items").get(0).getAsJsonObject().addProperty("count",65);
        assertThrows(IllegalArgumentException.class,()->parse(l));
        var m=json(); m.addProperty("interaction_distance",100);
        assertThrows(IllegalArgumentException.class,()->parse(m));
    }
    @Test void malformedOrMissingSaveNeverRefillsCharge() {
        for (String s:new String[]{null,"","unknown","garden"}) assertFalse(new RuinChoice(s).charged());
        for (var c:RuinChoice.Choice.values()) assertEquals(c,new RuinChoice(c.name()).choice());
    }
    @Test void confirmationIsBoundToPlayerChoiceTimeAndLaterTick() {
        var c=new RuinChoice("CHARGED"); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var garden=RuinChoice.Choice.GARDEN; var vault=RuinChoice.Choice.VAULT;
        assertFalse(c.confirm(a,garden,10,100));
        assertFalse(c.confirm(b,garden,11,100));
        assertFalse(c.confirm(b,vault,12,100));
        assertFalse(c.confirm(b,vault,12,100));
        assertFalse(c.confirm(b,vault,113,100));
        assertTrue(c.confirm(b,vault,114,100));
        assertTrue(c.commit(vault));
        assertFalse(c.confirm(a,garden,115,100));
        assertFalse(c.commit(garden));
        assertEquals(vault,new RuinChoice(c.choice().name()).choice());
    }
    @Test void restartDropsPendingConfirmationAndSpentStateIsShared() {
        var a=UUID.randomUUID(); var c=new RuinChoice("CHARGED");
        c.confirm(a,RuinChoice.Choice.GARDEN,10,100);
        var restored=new RuinChoice(c.choice().name());
        assertFalse(restored.confirm(a,RuinChoice.Choice.GARDEN,11,100));
        assertTrue(restored.confirm(a,RuinChoice.Choice.GARDEN,12,100));
        assertFalse(restored.commit(RuinChoice.Choice.CHARGED));
        assertTrue(restored.commit(RuinChoice.Choice.GARDEN));
        assertFalse(restored.commit(RuinChoice.Choice.VAULT));
    }
}
