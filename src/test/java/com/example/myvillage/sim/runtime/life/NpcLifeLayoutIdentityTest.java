package com.example.myvillage.sim.runtime.life;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcLifeLayoutIdentityTest {
    @Test void facilitiesRequireTheExactCurrentLayoutAndIgnoreInjectedHistoricalAliases() {
        String current="a".repeat(64),other="b".repeat(64);
        var layout=new JsonObject();layout.addProperty("worldplan_layout_hash",current);
        assertTrue(NpcLifeSites.matchesLayout(layout,current));
        assertFalse(NpcLifeSites.matchesLayout(layout,other));
        var aliases=new JsonArray();aliases.add(other);layout.add("compatible_worldplan_layout_hashes",aliases);
        assertFalse(NpcLifeSites.matchesLayout(layout,other));
        assertTrue(NpcLifeSites.matchesLayout(layout,current));
    }
}
