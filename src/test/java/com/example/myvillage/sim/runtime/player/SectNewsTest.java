package com.example.myvillage.sim.runtime.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.SimEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Which settled-day events reach a player as sect news ({@link SectNews#relevant}). */
class SectNewsTest {
    private static final int MINE = 4;
    private static final int OTHER = 9;

    private static PlayerMemberView member(int sectId, int leftSectId) {
        return new PlayerMemberView("uuid", "Dev", sectId, sectId < 0 ? "" : "青云宗", "outer", 12, -1, "", 0,
                List.of(), Map.of(), leftSectId, leftSectId < 0 ? -1 : 30, "mortal", 1, true, 4000, "", 0, -1, -1);
    }

    private static SimEvent event(String type, int importance, Integer... sects) {
        return new SimEvent(1, 30, type, importance, List.of(), List.of(sects), "r", -1, "world_sim.event.x.1",
                List.of());
    }

    @Test
    void notableEventsOfTheMembersSectAreNews() {
        PlayerMemberView me = member(MINE, -1);
        for (String type : List.of("war", "war_end", "succession", "sect_split", "heritage_lost", "sect_destroyed")) {
            assertTrue(SectNews.relevant(event(type, 2, MINE), me), type);
            assertTrue(SectNews.relevant(event(type, 3, OTHER, MINE), me), type + " naming the sect second");
        }
    }

    @Test
    void minorEventsOtherSectsAndPlayerEventsAreNot() {
        PlayerMemberView me = member(MINE, -1);
        assertFalse(SectNews.relevant(event("desertion", 1, MINE), me), "minor");
        assertFalse(SectNews.relevant(event("war", 3, OTHER), me), "another sect's");
        assertFalse(SectNews.relevant(event("war", 3), me), "no sect");
        assertFalse(SectNews.relevant(event("player_promotion", 2, MINE), me), "player_* are sent on their own");
        assertFalse(SectNews.relevant(event("player_leave", 2, MINE), me));
    }

    @Test
    void aRogueHearsOnlyOfTheSectTheyLastLeft() {
        PlayerMemberView rogue = member(-1, MINE);
        assertTrue(SectNews.relevant(event("sect_destroyed", 3, MINE, OTHER), rogue));
        assertFalse(SectNews.relevant(event("war_end", 3, MINE, OTHER), rogue), "only sect* events");
        assertFalse(SectNews.relevant(event("heritage_lost", 2, MINE), rogue));
        assertFalse(SectNews.relevant(event("sect_destroyed", 3, OTHER), rogue), "not their old sect");
        assertFalse(SectNews.relevant(event("player_leave", 2, MINE), rogue));
        assertFalse(SectNews.relevant(event("sect_destroyed", 3, MINE), member(-1, -1)), "never in a sect");
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void theNewsLineTakesTheEventLine(String langId) throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/myvillage/lang/" + langId + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertTrue(lang.has(SectNews.NEWS_KEY), langId);
        String template = lang.get(SectNews.NEWS_KEY).getAsString();
        assertTrue(template.contains("%1$s") && !template.contains("%2$s"), template);
    }
}
