package com.example.myvillage.sim.runtime.avatar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.myvillage.portrait.NpcColours;
import com.example.myvillage.portrait.PortraitAssign;
import com.example.myvillage.portrait.PortraitSpec.EyeColour;
import com.example.myvillage.portrait.PortraitSpec.HairColour;
import com.example.myvillage.sim.PersonView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** An avatar wears its portrait's hair and eye colours, dated by the sim day and the calendar. */
class AvatarColoursTest {
    /** qi_refining lives 120 years: greying from 0.55 (66 years), silver from 0.8 (96 years). */
    private static final int DAYS_PER_YEAR = 24;
    private static final long BIRTH = -100;
    private static final List<Integer> WATER_ROOT = List.of(1000, 1000, 6000, 1000, 1000);

    private static PersonView person(int id, String realm, boolean alive, long deathDay, List<Integer> root) {
        return new PersonView(id, "李" + id, "", "male", alive, BIRTH, deathDay, alive ? "" : "old_age", -1, root,
                "single", realm, 0, 0.0, 3, "青云宗", "inner", -1, "r", alive ? "at_sect" : "dead", "t", "青木长春功", 0,
                alive ? List.of(50, 50, 50, 50, 50) : List.of(), List.of());
    }

    private static long dayAtAge(int years) {
        return BIRTH + (long) years * DAYS_PER_YEAR;
    }

    @Test
    void theColoursAreThePortraitsOnThatDay() {
        for (int id = 1; id <= 40; id++) {
            PersonView p = person(id, "qi_refining", true, -1, WATER_ROOT);
            for (int years : new int[] {16, 70, 100}) {
                long day = dayAtAge(years);
                assertEquals(NpcColours.of(PortraitAssign.of(p, day, DAYS_PER_YEAR)),
                        AvatarColours.of(p, day, DAYS_PER_YEAR), "person " + id + " at " + years);
            }
        }
    }

    @Test
    void hairGreysAndTurnsSilverWithAge() {
        PersonView p = person(7, "qi_refining", true, -1, WATER_ROOT);
        NpcColours young = AvatarColours.of(p, dayAtAge(20), DAYS_PER_YEAR);
        NpcColours greying = AvatarColours.of(p, dayAtAge(70), DAYS_PER_YEAR);
        NpcColours old = AvatarColours.of(p, dayAtAge(100), DAYS_PER_YEAR);
        assertEquals(young.hair().greying(), greying.hair());
        assertEquals(HairColour.SILVER, old.hair());
        assertEquals(EyeColour.WATER, young.eye(), "the strongest root element");
        assertEquals(EyeColour.WATER, old.eye());
        // the calendar dates the age: the same day under a longer year is a younger person
        assertEquals(young.hair(), AvatarColours.of(p, dayAtAge(70), DAYS_PER_YEAR * 4).hair());
    }

    @Test
    void theDeadKeepTheColoursOfTheirDeathDay() {
        PersonView dead = person(7, "qi_refining", false, dayAtAge(20), List.of());
        PersonView living = person(7, "qi_refining", true, -1, List.of());
        assertEquals(AvatarColours.of(living, dayAtAge(20), DAYS_PER_YEAR).hair(),
                AvatarColours.of(dead, dayAtAge(200), DAYS_PER_YEAR).hair());
    }

    @Test
    void nascentSoulEyesAreSpirit() {
        assertEquals(EyeColour.SPIRIT, AvatarColours.of(person(3, "nascent_soul", true, -1, WATER_ROOT), 0,
                DAYS_PER_YEAR).eye());
    }

    @Test
    void aMalformedRecordOrCalendarThrowsForTheCallerToCatch() {
        PersonView p = person(7, "qi_refining", true, -1, WATER_ROOT);
        assertThrows(IllegalArgumentException.class, () -> AvatarColours.of(p, 0, 0));
        assertThrows(RuntimeException.class, () -> AvatarColours.of(person(8, null, true, -1, WATER_ROOT), 0,
                DAYS_PER_YEAR));
    }

    @Test
    void changeIsNullOnlyWhenTheEntityAlreadyWearsTheColours() {
        NpcColours wanted = new NpcColours(HairColour.GREY_INK_BLUE, EyeColour.WOOD);
        assertEquals(wanted, AvatarColours.change(null, wanted), "an avatar without colours");
        assertEquals(wanted, AvatarColours.change(new NpcColours(HairColour.INK_BLUE, EyeColour.WOOD), wanted));
        assertNull(AvatarColours.change(new NpcColours(HairColour.GREY_INK_BLUE, EyeColour.WOOD), wanted));
    }
}
