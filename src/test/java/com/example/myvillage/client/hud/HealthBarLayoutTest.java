package com.example.myvillage.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthBarLayoutTest {
    @Test void heightIsFixedAndLeavesArmorAndFoodBelow() {
        for(int width:new int[]{320,427,480,854})for(int base:new int[]{39,49,69}) {
            var box=HealthBarLayout.at(width,240,base);
            assertTrue(box.x()>=0 && box.x()+box.width()<=width);
            assertTrue(box.absorptionY()+2<240-base);
            assertTrue(240-box.reservedHeight()+9<box.headerY());
            assertEquals(182,box.width());
        }
    }
    @Test void healthRatioDoesNotDependOnMagnitudeOrAbsorption() {
        for(double max:new double[]{20,600,4800,120000}) {
            assertEquals(.7,HealthBarLayout.ratio(max*.7,max),1e-9);
            assertEquals(126,HealthBarLayout.fill(max*.7,max,180));
        }
        assertEquals(180,HealthBarLayout.fill(200,100,180));
        assertEquals(1,HealthBarLayout.fill(.01,100000,180));
    }
    @Test void invalidValuesCannotEscapeTheBar() {
        for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            assertEquals(0,HealthBarLayout.fill(value,20,180));
            assertEquals(0,HealthBarLayout.fill(20,value,180));
        }
        assertEquals(0,HealthBarLayout.fill(20,0,180));
    }
    @Test void numbersRetainLargeValuesAndDoNotReportLivingPlayersAsZero() {
        assertEquals("84,000 / 120,000",HealthBarLayout.balance(84000,120000));
        assertEquals("3.5 / 20",HealthBarLayout.balance(3.5,20));
        assertEquals("<0.1",HealthBarLayout.number(.001));
        assertEquals("0",HealthBarLayout.number(0));
    }
}
