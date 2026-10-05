package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.data.RealmTable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Realms that exist both in the sim and in the player registry agree on lifespan (design §3.2). */
class RealmLifespanAgreementTest {
    private static final Path PLAYER_REALMS = Path.of("src/main/resources/data/myvillage/myvillage/realm");

    private static int playerLifespan(String id) throws IOException {
        JsonObject json = JsonParser.parseString(
                Files.readString(PLAYER_REALMS.resolve(id + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        return json.get("maximum_lifespan_years").getAsInt();
    }

    @Test
    void sharedRealmsHaveEqualLifespans() throws IOException {
        int shared = 0;
        for (RealmTable.Realm realm : SimFixtures.data().realms().realms()) {
            if (Files.isRegularFile(PLAYER_REALMS.resolve(realm.id() + ".json"))) {
                assertEquals(playerLifespan(realm.id()), realm.lifespanYears(), "lifespan of " + realm.id());
                shared++;
            }
        }
        assertTrue(shared >= 2, "qi_refining and foundation_establishment must be shared, found " + shared);
        assertEquals(120, SimFixtures.data().realms().byId("qi_refining").lifespanYears());
        assertEquals(240, SimFixtures.data().realms().byId("foundation_establishment").lifespanYears());
    }

    @Test
    void mortalLifespanIsNotContradicted() throws IOException {
        assertEquals(80, playerLifespan("mortal"));
        int mortal = SimFixtures.data().realms().indexOf("mortal");
        if (mortal >= 0) {
            assertEquals(80, SimFixtures.data().realms().get(mortal).lifespanYears());
        }
        assertTrue(SimFixtures.data().realms().first().lifespanYears() > 80,
                "cultivation must lengthen life beyond the mortal 80");
    }
}
