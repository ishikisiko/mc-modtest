package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class JingangAdaptationTest {
    private final SpiritBodyRules.Adaptation rules=SpiritBodyRules.get().adaptation();
    @Test void sharesCapacityAndRedistributesOtherCategoriesProportionally() {
        var state=JingangAdaptation.empty(0).grow(AdaptationCategory.SLASH,30,1,rules).grow(AdaptationCategory.FIRE,10,2,rules);
        var changed=state.grow(AdaptationCategory.WATER,8,3,rules);
        assertEquals(40,changed.total(),1e-9);assertEquals(24,changed.points(AdaptationCategory.SLASH),1e-9);
        assertEquals(8,changed.points(AdaptationCategory.FIRE),1e-9);assertEquals(8,changed.points(AdaptationCategory.WATER),1e-9);
        assertEquals(30,state.points(AdaptationCategory.SLASH));
    }
    @Test void concentrationStaysBoundedAndDoesNotMultiplyEquipment() {
        var state=JingangAdaptation.empty(0).grow(AdaptationCategory.SLASH,1000,1,rules);
        assertEquals(rules.capacity(),state.total(),1e-9);
        assertEquals(10,state.defense(AdaptationCategory.SLASH,10,rules),1e-9);
        assertEquals(0,state.defense(AdaptationCategory.FIRE,10,rules));
    }
    @Test void decaysOnlyAfterCombatTimeoutAndGraduallyReachesZero() {
        var state=JingangAdaptation.empty(0).grow(AdaptationCategory.SLASH,40,10,rules);
        assertEquals(40,state.tick(310,rules).total());
        assertEquals(20,state.tick(390,rules).total(),1e-9);assertEquals(0,state.tick(470,rules).total(),1e-9);
        assertEquals(0,state.tick(1000,rules).total());
    }
    @Test void newCombatPreservesRemainingAdaptationButRestartsGrace() {
        var state=JingangAdaptation.empty(0).grow(AdaptationCategory.FIRE,40,0,rules).tick(380,rules).touch(380);
        assertEquals(20,state.tick(680,rules).total(),1e-9);
        assertEquals(10,state.tick(720,rules).total(),1e-9);
    }
    @Test void rejectsInvalidValuesAndClockRollbackDoesNotGrantPoints() {
        assertThrows(IllegalArgumentException.class,()->JingangAdaptation.empty(0).grow(AdaptationCategory.SLASH,Double.NaN,1,rules));
        assertEquals(0,JingangAdaptation.empty(100).grow(AdaptationCategory.SLASH,20,100,rules).tick(1,rules).total());
    }
}
