package com.example.myvillage.entity.flight;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlyingSwordFlightTest {
    private FlyingSwordRules rules() throws Exception {
        return new Gson().fromJson(Files.readString(Path.of("src/main/resources/data/myvillage/flying_sword/rules.json")), FlyingSwordRules.class);
    }
    @Test void diagonalNeverExceedsCruiseOrBoost() throws Exception {
        var r=rules();
        for (var phase : new FlyingSwordFlight.Phase[]{FlyingSwordFlight.Phase.CRUISE,FlyingSwordFlight.Phase.BOOST}) {
            var v=new FlyingSwordFlight.Motion(0,0,0);
            double cap=phase==FlyingSwordFlight.Phase.BOOST?r.boost_speed():r.cruise_speed();
            for(int i=0;i<200;i++) {
                v=FlyingSwordFlight.step(v,(byte)5,43,phase,0,r);
                assertTrue(Math.hypot(v.x(),v.z())<=cap+1e-9);
            }
            assertEquals(cap,Math.hypot(v.x(),v.z()),1e-6);
        }
    }
    @Test void lowPowerDisablesSprintAndEmptyCannotRiseOrGlideForever() throws Exception {
        var r=rules();byte keys=(byte)(1|16|64);
        assertEquals(FlyingSwordFlight.Phase.CRUISE,FlyingSwordFlight.phase(false,r.low_qi(),false,keys,100,r));
        assertEquals(FlyingSwordFlight.Phase.EMPTY_DESCENT,FlyingSwordFlight.phase(false,0,false,keys,100,r));
        var v=FlyingSwordFlight.step(new FlyingSwordFlight.Motion(1,.3,1),keys,0,FlyingSwordFlight.Phase.EMPTY_DESCENT,1,r);
        assertTrue(v.y()<=-r.landing_speed());assertTrue(Math.hypot(v.x(),v.z())<=r.empty_horizontal_speed()+1e-9);
        v=FlyingSwordFlight.step(v,keys,0,FlyingSwordFlight.Phase.EMPTY_DESCENT,r.empty_steering_ticks()+1,r);
        assertEquals(0,Math.hypot(v.x(),v.z()));assertTrue(v.y()<0);
        assertEquals(0,FlyingSwordFlight.cost(FlyingSwordFlight.Phase.EMPTY_DESCENT,r));
        assertTrue(FlyingSwordFlight.cost(FlyingSwordFlight.Phase.BOOST,r)>FlyingSwordFlight.cost(FlyingSwordFlight.Phase.HOVER,r));
    }
    @Test void recallAlwaysDescendsAndInputExpires() throws Exception {
        var r=rules();
        var phase=FlyingSwordFlight.phase(true,100,false,(byte)16,100,r);
        assertEquals(FlyingSwordFlight.Phase.LANDING,phase);
        assertTrue(FlyingSwordFlight.step(new FlyingSwordFlight.Motion(0,.4,0),(byte)16,0,phase,0,r).y()<0);
        assertFalse(FlyingSwordFlight.fresh(20,Long.MIN_VALUE,r.input_timeout_ticks()));
        assertFalse(FlyingSwordFlight.fresh(20,21,r.input_timeout_ticks()));
        assertFalse(FlyingSwordFlight.fresh(20,20-r.input_timeout_ticks()-1,r.input_timeout_ticks()));
        assertTrue(FlyingSwordFlight.fresh(20,20,r.input_timeout_ticks()));
    }
    @Test void ownerPassengerAndLifecycleAreAllRequired() {
        UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
        assertTrue(FlyingSwordAuthority.controls(owner,owner,true,true,true));
        assertFalse(FlyingSwordAuthority.controls(owner,other,true,true,true));
        assertFalse(FlyingSwordAuthority.controls(owner,owner,false,true,true));
        assertFalse(FlyingSwordAuthority.controls(owner,owner,true,false,true));
        assertFalse(FlyingSwordAuthority.controls(owner,owner,true,true,false));
        assertFalse(FlyingSwordAuthority.controls(null,owner,true,true,true));
        assertFalse(FlyingSwordAuthority.currentBinding(other,owner));
        assertTrue(FlyingSwordAuthority.currentBinding(owner,owner));
    }
    @Test void deathLogoutAndLoginCanOnlyReturnOneSavedReceipt() {
        var saved = new java.util.concurrent.atomic.AtomicReference<>("original-stack-with-components");
        var granted = new java.util.ArrayList<String>();
        for (String event : new String[]{"death", "logout", "login", "duplicate-removal"}) {
            String receipt = FlyingSwordEscrow.claim(saved::get, () -> saved.set(null));
            if (receipt != null) granted.add(receipt);
        }
        assertEquals(java.util.List.of("original-stack-with-components"), granted);
        assertNull(saved.get());
    }
    @Test void recoveryHasNoHorizontalSteeringEvenWhenPlayerPressesAllKeys() throws Exception {
        var r=rules();
        var v=FlyingSwordFlight.step(new FlyingSwordFlight.Motion(1,.4,1),(byte)127,20,
                FlyingSwordFlight.Phase.EMPTY_DESCENT,r.empty_steering_ticks()+1,r);
        assertEquals(0,Math.hypot(v.x(),v.z()));assertTrue(v.y()<0);
    }
    @Test void noLaunchInTribulationWaterAirOrWithoutQualification() {
        assertTrue(FlyingSwordAuthority.mayLaunch(true,false,false,false,true,false,false,true));
        assertFalse(FlyingSwordAuthority.mayLaunch(true,false,false,false,true,false,true,true));
        assertFalse(FlyingSwordAuthority.mayLaunch(true,false,false,false,true,true,false,true));
        assertFalse(FlyingSwordAuthority.mayLaunch(true,false,false,false,false,false,false,true));
        assertFalse(FlyingSwordAuthority.mayLaunch(true,false,false,false,true,false,false,false));
    }
}
