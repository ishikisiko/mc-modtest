package com.example.myvillage.stats;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyDefenseTest {
    private final CultivationStatsRules rules=CultivationStatsRules.bundled();
    private double damage(double attack,double defense){return BodyDefense.calculate(attack,defense,0,rules).afterDefense();}
    @Test void lowDefenseSubtractsBeforeMoveAndCritical() {
        assertEquals(15,damage(20,5));
        assertEquals(67.5,damage(20,5)*3*1.5);
        assertEquals(20,damage(20,0));
        assertEquals(0,damage(0,100));
    }
    @Test void transitionIsContinuousAndHasMatchingSlope() {
        double epsilon=1e-5;
        assertEquals(20,damage(40,20));
        assertEquals(-1,(damage(40,20)-damage(40,20-epsilon))/epsilon,1e-5);
        assertEquals(-1,(damage(40,20+epsilon)-damage(40,20))/epsilon,1e-5);
    }
    @Test void defenseNeverIncreasesDamageAndAsymptoteStaysPositive() {
        for(double a:new double[]{.01,1,20,100,2048}){
            double previous=a;
            for(int d=0;d<=1024;d++){
                double next=damage(a,d);
                assertTrue(next<=previous+1e-10);assertTrue(next>=a*.05);assertTrue(next<=a);
                previous=next;
            }
            assertEquals(a*.05,damage(a,1e14),1e-6);
        }
    }
    @Test void penetrationOnlyChangesBodyDefenseAndInputsAreBounded() {
        assertEquals(20,BodyDefense.calculate(20,100,1,rules).afterDefense());
        assertEquals(damage(20,50),BodyDefense.calculate(20,100,.5,rules).afterDefense());
        for(double bad:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}){
            assertThrows(IllegalArgumentException.class,()->BodyDefense.calculate(bad,0,0,rules));
            assertThrows(IllegalArgumentException.class,()->BodyDefense.calculate(1,bad,0,rules));
        }
        assertThrows(IllegalArgumentException.class,()->BodyDefense.calculate(1,0,1.01,rules));
    }
}
