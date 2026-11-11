package com.example.myvillage.estate;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.worldplan.capital.CapitalGameBundle;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EstateManifestTest {
    private JsonObject city() throws Exception {
        try(var input=getClass().getResourceAsStream("/data/myvillage/capitals/v4/capital_zhongzhou/city.json")) {
            assertNotNull(input);return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void actualExportSharesOnePriceAndKeepsStewardOutsideEntirePrivateColumn()throws Exception {
        var root=city();var definition=EstateDefinition.read(root.getAsJsonArray("player_estates").get(0).getAsJsonObject(),1200,320,1440);
        assertEquals(100,definition.price());assertEquals("myvillage:high_grade_spirit_stone",definition.currency().toString());
        assertEquals("open_after_purchase",root.getAsJsonArray("player_estates").get(0).getAsJsonObject().get("access").getAsString());
        assertEquals(new EstateBounds(349,713,460,811),definition.bounds());
        assertFalse(definition.bounds().overlaps(definition.steward().getX()+.5,definition.steward().getZ()+.5,.3));
        assertFalse(definition.bounds().overlaps(definition.exit().getX()+.5,definition.exit().getZ()+.5,.3));
    }
    @Test void priceBoundsAndStewardChangesInvalidateFrozenIdentityWhileDiagnosticsDoNot()throws Exception {
        var root=city();String identity=CapitalGameBundle.identityJson(root);
        var diagnostic=root.deepCopy();diagnostic.addProperty("player_estate_build","changed review note");
        assertEquals(identity,CapitalGameBundle.identityJson(diagnostic));
        for(String key:new String[]{"price","bounds","steward","exit","access"}) {
            var changed=root.deepCopy();var estate=changed.getAsJsonArray("player_estates").get(0).getAsJsonObject();
            switch(key) {
                case "price" -> estate.getAsJsonObject(key).addProperty("count",101);
                case "bounds" -> estate.getAsJsonArray(key).set(0,new JsonPrimitive(350));
                case "steward" -> estate.getAsJsonObject(key).getAsJsonArray("position").set(0,new JsonPrimitive(462));
                case "exit" -> estate.getAsJsonArray(key).set(2,new JsonPrimitive(763));
                case "access" -> estate.addProperty(key,"closed");
            }
            assertNotEquals(identity,CapitalGameBundle.identityJson(changed),key);
        }
    }
    @Test void invalidStewardAndPrivateBoundsAreRejectedBeforeWorldStartup()throws Exception {
        var root=city().getAsJsonArray("player_estates").get(0).getAsJsonObject();
        var inside=root.deepCopy();inside.getAsJsonObject("steward").getAsJsonArray("position").set(0,new JsonPrimitive(400));
        assertThrows(IllegalArgumentException.class,()->EstateDefinition.read(inside,1200,320,1440));
        var outside=root.deepCopy();outside.getAsJsonArray("bounds").set(2,new JsonPrimitive(1300));
        assertThrows(IllegalArgumentException.class,()->EstateDefinition.read(outside,1200,320,1440));
        var policy=root.deepCopy();policy.addProperty("access","owner_only");
        assertThrows(IllegalArgumentException.class,()->EstateDefinition.read(policy,1200,320,1440));
    }
}
