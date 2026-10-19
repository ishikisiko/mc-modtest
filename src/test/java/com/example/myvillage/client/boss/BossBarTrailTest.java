package com.example.myvillage.client.boss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossBarTrailTest {
    @Test void injuryEchoHoldsThenSettlesWithoutExceedingOriginalHealth() {
        var echo = new BossBarTrail(.8f, 1000);
        assertEquals(.8f, echo.sample(.5f, 1100), .001);
        assertEquals(.8f, echo.sample(.5f, 1300), .001);
        assertEquals(.65f, echo.sample(.5f, 1600), .001);
        assertEquals(.5f, echo.sample(.5f, 1850), .001);
    }
    @Test void healingNewEncountersAndClockResetNeverLeaveFalseDamage() {
        var echo = new BossBarTrail(.8f, 1000);
        echo.sample(.2f, 1100);
        assertEquals(.6f, echo.sample(.6f, 1200), .001);
        assertEquals(.4f, new BossBarTrail(.4f, 0).sample(.4f, 0), .001);
        assertEquals(.3f, echo.sample(.3f, 0), .001);
    }
}
