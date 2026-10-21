package com.example.myvillage.orchard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FruitCycleTest {
    @Test void oneFertilizerBuysExactlyOneHighGradeHarvest() {
        var fruit=new FruitCycle(0,false);
        assertTrue(fruit.canEnrich());fruit=fruit.enrich();assertFalse(fruit.canEnrich());
        assertEquals(fruit,fruit.enrich());
        fruit=fruit.grow().grow();assertTrue(fruit.ripe());assertTrue(fruit.enriched());
        var next=fruit.afterHarvest();assertFalse(next.ripe());assertFalse(next.enriched());
        assertEquals(next,next.afterHarvest());
        assertFalse(next.grow().grow().enriched());
    }
    @Test void fertilizerCannotUpgradeAnAlreadyGrowingOrRipeCrop() {
        for(int age=1;age<=2;age++) {var fruit=new FruitCycle(age,false);assertFalse(fruit.canEnrich());assertEquals(fruit,fruit.enrich());}
    }
    @Test void growthNeverChangesQualityOrExceedsMaturity() {
        for(boolean enriched:new boolean[]{false,true}) {
            var fruit=new FruitCycle(0,enriched);
            for(int i=0;i<100;i++) {fruit=fruit.grow();assertEquals(enriched,fruit.enriched());assertTrue(fruit.age()<=2);}
        }
        assertThrows(IllegalArgumentException.class,()->new FruitCycle(-1,false));
        assertThrows(IllegalArgumentException.class,()->new FruitCycle(3,true));
    }
}
