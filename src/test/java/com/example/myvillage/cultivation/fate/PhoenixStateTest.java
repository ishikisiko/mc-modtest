package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhoenixStateTest {
    private final SpiritBodyRules.Phoenix rules=SpiritBodyRules.get().phoenix();
    @Test void initialGrantHappensOnceAndDeathDoesNotRefill() {
        var fresh=PhoenixState.EMPTY.initialize(rules);assertEquals(60,fresh.reserve());assertTrue(fresh.ready(rules));
        var empty=fresh.emptyReserve();assertEquals(0,empty.initialize(rules).reserve());assertFalse(empty.ready(rules));
    }
    @Test void healingNeverConsumesReservedSeedOrCreatesReserve() {
        var low=new PhoenixState(true,12);assertEquals(0,low.healingBudget(rules));assertEquals(12,low.spendHealing(0,rules).reserve());
        assertThrows(IllegalArgumentException.class,()->low.spendHealing(1,rules));
        var high=new PhoenixState(true,61.5);assertEquals(1.5,high.healingBudget(rules));
        assertEquals(60,high.spendHealing(1.5,rules).reserve());
        assertThrows(IllegalArgumentException.class,()->high.spendHealing(2,rules));
    }
    @Test void fireRecoveryIsBoundedAndCannotBeNegativeOrInfinite() {
        assertEquals(100,new PhoenixState(true,99).recover(10,rules).reserve());
        assertThrows(IllegalArgumentException.class,()->new PhoenixState(true,99).recover(-1,rules));
        assertThrows(IllegalArgumentException.class,()->new PhoenixState(true,99).recover(Double.POSITIVE_INFINITY,rules));
    }
    @Test void reserveBeyondThresholdStrengthensEmberWithoutIncreasingRebirthHealing() {
        assertEquals(40,PhoenixEmberClock.begin(100,new PhoenixState(true,60),rules).durability(),1e-9);
        assertEquals(60,PhoenixEmberClock.begin(100,new PhoenixState(true,100),rules).durability(),1e-9);
        assertEquals(.6,rules.rebirthHealthFraction());
        assertThrows(IllegalArgumentException.class,()->PhoenixEmberClock.begin(100,new PhoenixState(true,59.9),rules));
    }
    @Test void damageDoesNotRestartCountdownAndWaterWinsWhenItExtinguishesAtDeadline() {
        var clock=PhoenixEmberClock.begin(100,new PhoenixState(true,60),rules);
        for(int i=0;i<20;i++)clock=clock.tick(false,rules);
        assertEquals(80,clock.remaining());assertEquals(0,clock.protectionRemaining());
        assertEquals(80,clock.health(20).remaining());
        for(int i=0;i<80;i++)clock=clock.tick(true,rules);
        assertTrue(clock.failed());assertFalse(clock.complete());assertEquals(0,clock.remaining());
    }
    @Test void normalCondensationCompletesAndClockCanContinueFromSavedRemaining() {
        var clock=PhoenixEmberClock.begin(100,new PhoenixState(true,60),rules);
        for(int i=0;i<40;i++)clock=clock.tick(false,rules);
        var restored=new PhoenixEmberClock(clock.maximumHealth(),clock.maximumDurability(),clock.durability(),clock.duration(),clock.remaining(),clock.protectionRemaining(),clock.movementUsed());
        for(int i=0;i<60;i++)restored=restored.tick(false,rules);
        assertTrue(restored.complete());assertEquals(40,restored.durability());
    }
    @Test void everyFateReplacementPreservesFireAndDeathClearsItWithoutAnotherInitialGrant() {
        var ids=List.of("myvillage:phoenix_body","myvillage:bright","myvillage:lightfoot");
        var state=new FateState(1,1,ids,"",ids,true,0,0,0,0).withPhoenix(new PhoenixState(true,83.5));
        var replaced=state.choice(ids,"",ids,true,state.rolls()).timers(1,2,.1,3).withFireSpirit(state.fireSpirit()).withWindStep(state.windStep())
                .withSwordHeart(state.swordHeart()).withPillAegis(state.pillAegis()).withTemperedBones(state.temperedBones());
        var json=FateState.CODEC.encodeStart(JsonOps.INSTANCE,replaced).getOrThrow();
        assertEquals(83.5,FateState.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow().phoenix().reserve());
        assertEquals(0,state.afterDeath().phoenix().initialize(rules).reserve());
        assertEquals(0,state.resetChoices().phoenix().initialize(rules).reserve());
    }
}
