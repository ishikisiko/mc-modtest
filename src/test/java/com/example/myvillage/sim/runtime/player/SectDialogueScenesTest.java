package com.example.myvillage.sim.runtime.player;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.myvillage.sim.Admission;
import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.runtime.player.SectDialogueScenes.Option;
import com.example.myvillage.sim.runtime.player.SectDialogueScenes.Scene;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Which scene and options the steward or an elder shows, from the player's record and admission. */
class SectDialogueScenesTest {
    private static final int SECT = 7;
    private static final String STEWARD = SectDialogueScenes.ROLE_STEWARD;
    private static final String ELDER = SectDialogueScenes.ROLE_ELDER;

    private static Optional<PlayerMemberView> memberOf(int sectId) {
        return Optional.of(new PlayerMemberView("uuid", "Dev", sectId, sectId < 0 ? "" : "青云宗", "outer", 12, -1, "",
                0, List.of(), Map.of(SECT, 20), -1, -1, "mortal", 1, true, 4000, "", 0, -1, -1));
    }

    @Test
    void aNewcomerWhoMayJoinIsInvited() {
        Scene scene = SectDialogueScenes.decide(STEWARD, Optional.empty(), Admission.admitted(), SECT);
        assertEquals("steward.invite", scene.key());
        assertEquals(List.of(Option.JOIN, Option.FAREWELL), scene.options());
        // a former member (no sect now) who may join again is invited too
        assertEquals("steward.invite",
                SectDialogueScenes.decide(STEWARD, memberOf(-1), Admission.admitted(), SECT).key());
    }

    @Test
    void aRefusalNamesItsReasonAndOffersOnlyFarewell() {
        for (String reason : List.of(Admission.NOT_AWAKENED, Admission.REALM_TOO_LOW, Admission.SELECTIVE,
                Admission.REJOIN_COOLDOWN, Admission.STANDING_TOO_LOW, Admission.SECT_INACTIVE)) {
            Scene scene = SectDialogueScenes.decide(STEWARD, memberOf(-1), Admission.refused(reason), SECT);
            assertEquals("steward.refuse." + reason, scene.key());
            assertEquals(List.of(Option.FAREWELL), scene.options());
        }
    }

    @Test
    void aMemberOfThisSectIsGreetedByRankAndMayLeave() {
        Scene scene = SectDialogueScenes.decide(STEWARD, memberOf(SECT), Admission.refused(Admission.ALREADY_MEMBER),
                SECT);
        assertEquals(List.of("steward.member", "steward.leave_ask"), scene.lines());
        assertEquals(List.of(Option.LEAVE, Option.FAREWELL), scene.options());
    }

    @Test
    void aMemberOfAnotherSectIsTurnedAwayWhateverTheAdmissionSays() {
        Scene scene = SectDialogueScenes.decide(STEWARD, memberOf(3), Admission.admitted(), SECT);
        assertEquals("steward.refuse.member_elsewhere", scene.key());
        assertEquals(List.of(Option.FAREWELL), scene.options());
    }

    @Test
    void anElderOnlyGreets() {
        assertEquals("elder.greet", SectDialogueScenes.decide(ELDER, Optional.empty(), Admission.admitted(), SECT).key());
        assertEquals("elder.greet", SectDialogueScenes.decide(ELDER, memberOf(3), Admission.admitted(), SECT).key());
        Scene member = SectDialogueScenes.decide(ELDER, memberOf(SECT), Admission.refused(Admission.ALREADY_MEMBER),
                SECT);
        assertEquals("elder.member", member.key());
        assertEquals(List.of(Option.FAREWELL), member.options());
        assertThrows(IllegalArgumentException.class,
                () -> SectDialogueScenes.decide("none", Optional.empty(), Admission.admitted(), SECT));
    }

    @Test
    void outcomesOfAChoice() {
        assertEquals(List.of("steward.welcome"), SectDialogueScenes.welcome().lines());
        assertEquals(List.of("steward.farewell_left"), SectDialogueScenes.farewellLeft().lines());
        assertEquals("steward.refuse.rejoin_cooldown", SectDialogueScenes.refused(Admission.REJOIN_COOLDOWN).key());
        assertEquals("steward.refuse.inactive", SectDialogueScenes.refused("something_new").key());
        assertEquals("steward.refuse.not_member", SectDialogueScenes.refused("not_member").key());
        assertEquals(List.of(Option.FAREWELL), SectDialogueScenes.welcome().options());
    }

    @Test
    void theStewardGreetsAndIntroducesBeforeTheScene() {
        Scene invite = SectDialogueScenes.decide(STEWARD, Optional.empty(), Admission.admitted(), SECT);
        assertEquals(List.of("steward.greet", "steward.intro", "steward.invite"),
                SectDialogueScenes.openingLines(STEWARD, invite));
        Scene greet = SectDialogueScenes.decide(ELDER, Optional.empty(), Admission.admitted(), SECT);
        assertEquals(List.of("elder.greet", "steward.intro"), SectDialogueScenes.openingLines(ELDER, greet));
        Scene member = SectDialogueScenes.decide(ELDER, memberOf(SECT), Admission.admitted(), SECT);
        assertEquals(List.of("elder.member"), SectDialogueScenes.openingLines(ELDER, member));
    }

    @Test
    void everySceneLineGetsAsManyParamsAsItsKeyTakes() {
        SectDialogueScenes.Facts facts = new SectDialogueScenes.Facts("青云宗", 61, 14, "韩立", "outer-rank");
        for (Map.Entry<String, Integer> e : SectDialogueKeys.PARAMS.entrySet()) {
            assertEquals(e.getValue(), SectDialogueScenes.args(e.getKey(), facts).length, e.getKey());
        }
        assertArrayEquals(new Object[] {"青云宗", "61", "14", "韩立"},
                SectDialogueScenes.args("steward.intro", facts));
        assertArrayEquals(new Object[] {"outer-rank"}, SectDialogueScenes.args("steward.member", facts));
        assertThrows(IllegalArgumentException.class, () -> SectDialogueScenes.args("steward.unknown", facts));
    }

    @Test
    void optionIdsAreFixed() {
        assertEquals(0, Option.JOIN.id());
        assertEquals(1, Option.LEAVE.id());
        assertEquals(2, Option.FAREWELL.id());
        assertEquals(3, Option.APPRENTICE.id());
        assertEquals(4, Option.TASK_ACCEPT.id());
        assertEquals(5, Option.TASK_TURN_IN.id());
        for (Option o : Option.values()) {
            assertEquals(o, Option.of(o.id()));
        }
        assertThrows(IllegalArgumentException.class, () -> Option.of(6));
    }

    @Test
    void variantsArePickedBySaltAndStayInRange() {
        assertEquals("world_sim.dialogue.steward.greet.1", SectDialogueKeys.pick("steward.greet", 0));
        assertEquals("world_sim.dialogue.steward.greet.2", SectDialogueKeys.pick("steward.greet", 1));
        assertEquals("world_sim.dialogue.steward.greet.2", SectDialogueKeys.pick("steward.greet", -1));
        assertEquals("world_sim.dialogue.steward.intro.1", SectDialogueKeys.pick("steward.intro", 12345));
        assertThrows(IllegalArgumentException.class, () -> SectDialogueKeys.key("steward.greet", 3));
        assertThrows(IllegalArgumentException.class, () -> SectDialogueKeys.pick("nobody.says", 0));
    }
}
