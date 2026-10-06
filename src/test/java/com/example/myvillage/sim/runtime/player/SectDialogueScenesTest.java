package com.example.myvillage.sim.runtime.player;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.Admission;
import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.TaskView;
import com.example.myvillage.sim.runtime.player.SectDialogueScenes.Affairs;
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

    private static Optional<PlayerMemberView> member(String rank, int masterId, long taskYear) {
        return Optional.of(new PlayerMemberView("uuid", "Dev", SECT, "青云宗", rank, 12, masterId,
                masterId < 0 ? "" : "韩立", 0, List.of(), Map.of(SECT, 20), -1, -1, "qi_refining", 1, true, 4000,
                "", 0, -1, taskYear));
    }

    private static TaskView task(String kind, int progress, int count) {
        return new TaskView(kind + "_task", kind, count, 10, progress, -1, "", 3, false);
    }

    private static Scene steward(Optional<PlayerMemberView> me, Affairs affairs) {
        return SectDialogueScenes.decide(STEWARD, me, Admission.refused(Admission.ALREADY_MEMBER), SECT, affairs);
    }

    @Test
    void theStewardOffersThisYearsTaskToAMember() {
        Affairs offer = new Affairs(Optional.empty(), Optional.of(task("patrol", 0, 3)), false, false);
        Scene scene = steward(member("outer", -1, -1), offer);
        assertEquals(List.of("steward.member", "steward.task.offer", "steward.leave_ask"), scene.lines());
        assertEquals(List.of(Option.TASK_ACCEPT, Option.LEAVE, Option.FAREWELL), scene.options());
        assertEquals(List.of("steward.greet", "steward.intro", "steward.member", "steward.task.offer",
                "steward.leave_ask"), SectDialogueScenes.openingLines(STEWARD, scene));
    }

    @Test
    void anOpenTaskNotYetDoneShowsItsProgress() {
        for (TaskView open : List.of(task("patrol", 2, 3), task("courier", 0, 1), task("tribute", 0, 5))) {
            Scene scene = steward(member("outer", -1, 3), new Affairs(Optional.of(open), Optional.empty(), false, false));
            assertEquals(List.of("steward.member", "steward.task.progress", "steward.leave_ask"), scene.lines(),
                    open.kind());
            assertEquals(List.of(Option.LEAVE, Option.FAREWELL), scene.options(), open.kind());
        }
    }

    @Test
    void aDoneTaskMayBeTurnedIn() {
        for (Affairs done : List.of(
                new Affairs(Optional.of(task("patrol", 3, 3)), Optional.empty(), false, false),
                new Affairs(Optional.of(task("courier", 1, 1)), Optional.empty(), false, false),
                new Affairs(Optional.of(task("tribute", 0, 5)), Optional.empty(), true, false))) {
            Scene scene = steward(member("outer", -1, 3), done);
            assertEquals(List.of("steward.member", "steward.task.ready", "steward.leave_ask"), scene.lines());
            assertEquals(List.of(Option.TASK_TURN_IN, Option.LEAVE, Option.FAREWELL), scene.options());
        }
    }

    @Test
    void aMemberWhoTookThisYearsTaskIsToldToWait() {
        Scene scene = steward(member("outer", -1, 3), Affairs.NONE);
        assertEquals(List.of("steward.member", "steward.task.none_this_year", "steward.leave_ask"), scene.lines());
        assertEquals(List.of(Option.LEAVE, Option.FAREWELL), scene.options());
        // never took one and nothing to offer: the plain member scene
        assertEquals(List.of("steward.member", "steward.leave_ask"), steward(member("outer", -1, -1), Affairs.NONE).lines());
    }

    @Test
    void tasksAreOnlyForMembersOfThisSect() {
        Affairs offer = new Affairs(Optional.empty(), Optional.of(task("patrol", 0, 3)), false, false);
        assertEquals("steward.invite",
                SectDialogueScenes.decide(STEWARD, Optional.empty(), Admission.admitted(), SECT, offer).key());
        assertEquals("steward.refuse.member_elsewhere",
                SectDialogueScenes.decide(STEWARD, memberOf(3), Admission.admitted(), SECT, offer).key());
    }

    @Test
    void anElderOffersApprenticeshipWhenItMayBe() {
        Affairs can = new Affairs(Optional.empty(), Optional.empty(), false, true);
        Scene scene = SectDialogueScenes.decide(ELDER, member("inner", -1, -1), Admission.admitted(), SECT, can);
        assertEquals(List.of("elder.apprentice.offer"), scene.lines());
        assertEquals(List.of(Option.APPRENTICE, Option.FAREWELL), scene.options());
        assertEquals(List.of("elder.apprentice.offer"), SectDialogueScenes.openingLines(ELDER, scene));
        // a member who already has a master (canApprentice false) is greeted as before
        Scene greeted = SectDialogueScenes.decide(ELDER, member("inner", 5, -1), Admission.admitted(), SECT,
                Affairs.NONE);
        assertEquals(List.of("elder.member"), greeted.lines());
        assertEquals(List.of(Option.FAREWELL), greeted.options());
        // a non-member is greeted, whatever the flag says
        assertEquals("elder.greet",
                SectDialogueScenes.decide(ELDER, Optional.empty(), Admission.admitted(), SECT, can).key());
    }

    @Test
    void whoMayBeTakenAsDisciple() {
        assertTrue(SectDialogueScenes.canApprentice(member("inner", -1, -1), SECT, "elder", "at_sect"));
        assertTrue(SectDialogueScenes.canApprentice(member("elder", -1, -1), SECT, "sect_master", "at_sect"));
        assertFalse(SectDialogueScenes.canApprentice(member("outer", -1, -1), SECT, "elder", "at_sect"));
        assertFalse(SectDialogueScenes.canApprentice(member("inner", 9, -1), SECT, "elder", "at_sect"));
        assertFalse(SectDialogueScenes.canApprentice(member("inner", -1, -1), SECT, "elder", "travelling"));
        assertFalse(SectDialogueScenes.canApprentice(member("inner", -1, -1), SECT, "inner", "at_sect"));
        assertFalse(SectDialogueScenes.canApprentice(member("inner", -1, -1), 3, "elder", "at_sect"));
        assertFalse(SectDialogueScenes.canApprentice(Optional.empty(), SECT, "elder", "at_sect"));
    }

    @Test
    void outcomesOfTaskAndApprenticeChoices() {
        assertEquals(List.of("steward.task.accepted"), SectDialogueScenes.taskAccepted().lines());
        assertEquals(List.of("steward.task.done"), SectDialogueScenes.taskDone().lines());
        assertEquals(List.of("elder.apprentice.done"), SectDialogueScenes.apprenticed().lines());
        assertEquals("steward.task.refuse.tribute_short", SectDialogueScenes.taskRefused("tribute_short").key());
        assertEquals("steward.task.refuse.task_done_this_year",
                SectDialogueScenes.taskRefused("task_done_this_year").key());
        assertEquals("steward.task.refuse.inactive", SectDialogueScenes.taskRefused("something_new").key());
        assertEquals("elder.apprentice.refuse.has_master", SectDialogueScenes.apprenticeRefused("has_master").key());
        assertEquals("elder.apprentice.refuse.inactive", SectDialogueScenes.apprenticeRefused("nope").key());
        for (Scene s : List.of(SectDialogueScenes.taskAccepted(), SectDialogueScenes.taskDone(),
                SectDialogueScenes.apprenticed(), SectDialogueScenes.taskRefused("no_task"))) {
            assertEquals(List.of(Option.FAREWELL), s.options());
        }
    }

    @Test
    void taskLinesTakeTheTaskFacts() {
        SectDialogueScenes.Facts facts = new SectDialogueScenes.Facts("青云宗", 61, 14, "韩立", "inner-rank", "南宫婉",
                "patrol-name", "patrol-brief", 2, 3, 10);
        assertArrayEquals(new Object[] {"patrol-name", "patrol-brief"}, SectDialogueScenes.args("steward.task.offer", facts));
        assertArrayEquals(new Object[] {"patrol-name", "2", "3"}, SectDialogueScenes.args("steward.task.progress", facts));
        assertArrayEquals(new Object[] {"patrol-name", "10"}, SectDialogueScenes.args("steward.task.done", facts));
        assertArrayEquals(new Object[] {"南宫婉"}, SectDialogueScenes.args("elder.apprentice.offer", facts));
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
