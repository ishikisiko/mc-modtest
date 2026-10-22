package com.example.myvillage.combat.runtime;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatDisciplinesTest {
    @Test void aMultiTargetMoveCannotRechargeAfterSpendOrExpiry() {
        var state = new DisciplinePool(); var rules = DisciplineRules.get().sword();
        assertTrue(state.gain(10,0,rules));
        assertFalse(state.gain(10,1,rules));
        assertEquals(1+rules.bonus(),state.consume(1,rules),1e-9);
        assertFalse(state.gain(10,2,rules));
        assertFalse(state.gain(9,3,rules));
        assertFalse(state.gain(10,10000,rules));
        assertTrue(state.gain(11,10001,rules));
    }
    @Test void cappedAndExpiresAtBoundary() {
        var state = new DisciplinePool(); var rules = DisciplineRules.get().sword();
        for (int i=1;i<=20;i++) state.gain(i,i,rules);
        assertEquals(rules.maximum(),state.stacks(20+rules.timeoutTicks()-1));
        assertEquals(0,state.stacks(20+rules.timeoutTicks()));
        assertEquals(1,state.consume(2000,rules));
    }
    @Test void bodyChargeIsBoundedAndSingleUse() {
        var state = new DisciplinePool(); var rules = DisciplineRules.get().body();
        state.gain(1,0,rules); state.gain(2,1,rules);
        assertEquals(rules.maximum(),state.stacks(1));
        assertEquals(1+rules.maximum()*rules.bonus(),state.consume(1,rules),1e-9);
        assertEquals(1,state.consume(1,rules));
    }
    @Test void spearUsesWideFiniteBandAndNoCloseBonus() {
        var r = DisciplineRules.get();
        assertFalse(r.spearTip(r.spearMin()-.01)); assertFalse(r.spearTip(r.spearMax()+.01));
        assertTrue(r.spearTip(r.spearMin())); assertTrue(r.spearTip(r.spearMax()));
        assertFalse(r.spearTip(Double.NaN)); assertFalse(r.spearTip(Double.POSITIVE_INFINITY));
    }
    @Test void invalidTuningCannotStartRuntime() throws Exception {
        try(var in=getClass().getResourceAsStream("/data/myvillage/combat/disciplines.json")) {
            String text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var root=JsonParser.parseString(text).getAsJsonObject();
            root.getAsJsonObject("sword").addProperty("maximum",1.5);
            assertThrows(IllegalArgumentException.class,()->DisciplineRules.parse(root));
            root.getAsJsonObject("sword").addProperty("maximum",3);
            root.getAsJsonObject("spear").addProperty("minimum_distance",8);
            assertThrows(IllegalArgumentException.class,()->DisciplineRules.parse(root));
        }
    }
}
