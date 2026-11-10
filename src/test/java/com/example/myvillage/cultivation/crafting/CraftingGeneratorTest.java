package com.example.myvillage.cultivation.crafting;

import java.io.StringReader;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingGeneratorTest {
    private static final UUID PLAYER=UUID.fromString("12345678-1234-5678-9abc-def012345678");
    private static final CraftingOpeningRules.Selection CHOICE=new CraftingOpeningRules.Selection(
            List.of("myvillage:robust","myvillage:lightfoot","myvillage:bright"),-1,-1);
    @Test void selectedFatesAndIdentityDetermineOpeningWhileTalismanKeepsItsDomain(){
        var expected=new CraftingAptitudes(1,true,35,40,83);
        assertEquals(expected,CraftingGenerator.ensure(CraftingAptitudes.EMPTY,true,987654321L,PLAYER,CraftingRules.get(),CHOICE));
        var anotherWorld=CraftingGenerator.ensure(CraftingAptitudes.EMPTY,true,987654322L,PLAYER,CraftingRules.get(),CHOICE);
        assertEquals(expected.alchemy(),anotherWorld.alchemy());assertEquals(expected.forging(),anotherWorld.forging());
        for(var order:List.of(CHOICE.ids(),List.of(CHOICE.ids().get(2),CHOICE.ids().get(0),CHOICE.ids().get(1))))
            assertEquals(expected,CraftingGenerator.ensure(CraftingAptitudes.EMPTY,true,987654321L,PLAYER,CraftingRules.get(),new CraftingOpeningRules.Selection(order,-1,-1)));
    }
    @Test void awakeningAndFateConfirmationAreBothRequiredAndSavedGrowthWins(){
        assertSame(CraftingAptitudes.EMPTY,CraftingGenerator.ensure(CraftingAptitudes.EMPTY,false,0,null,null,null));
        assertSame(CraftingAptitudes.EMPTY,CraftingGenerator.ensure(CraftingAptitudes.EMPTY,true,0,PLAYER,CraftingRules.get(),CraftingOpeningRules.Selection.PENDING));
        var saved=new CraftingAptitudes(1,true,111,205,73);
        assertSame(saved,CraftingGenerator.ensure(saved,true,0,null,null,null));
        assertSame(saved,CraftingGenerator.ensure(saved,true,123,PLAYER,CraftingRules.get(),new CraftingOpeningRules.Selection(CHOICE.ids(),60,60)));
    }
    @Test void contributionsUseConfiguredBucketsAndRemainIndependentBetweenProfessions(){
        var opening=CraftingOpeningRules.get();var counts=new HashMap<Integer,Integer>();
        for(int bucket=0;bucket<opening.totalWeight();bucket++)counts.merge(opening.deltaForBucket(bucket),1,Integer::sum);
        assertEquals(Map.of(-5,15,5,15,-10,5,10,5,0,60),counts);
        assertEquals(30,opening.base());assertEquals(0,opening.initialMinimum());assertEquals(60,opening.initialMaximum());
        int different=0;
        for(int i=0;i<1000;i++){
            var state=CraftingGenerator.ensure(CraftingAptitudes.EMPTY,true,321,new UUID(1,i),CraftingRules.get(),CHOICE);
            assertTrue(state.alchemy()>=0&&state.alchemy()<=60&&state.forging()>=0&&state.forging()<=60);
            assertEquals(0,state.alchemy()%5);if(state.alchemy()!=state.forging())different++;
        }
        assertTrue(different>600);
    }
    @Test void fillingPendingTalismanDoesNotResetSavedAlchemyOrForging(){
        var current=new CraftingAptitudes(1,true,7,91);
        var next=CraftingGenerator.ensure(current,true,987654321L,PLAYER,CraftingRules.get(),CHOICE);
        assertEquals(7,next.alchemy());assertEquals(91,next.forging());assertEquals(83,next.talisman());
        assertSame(next,CraftingGenerator.ensure(next,true,0,null,null,null));
    }
    @Test void talismanRangeRemainsSeparateAndGeniusOverridesOnlyItsOwnOpeningScore(){
        var rules=CraftingRules.parse(new StringReader("""
                {"version":1,"initial_roll":{"min":17,"max":17},"quality_thresholds":[
                 {"quality":1,"minimum":0},{"quality":2,"minimum":40},
                 {"quality":3,"minimum":70},{"quality":4,"minimum":90}]}
                """));
        var actual=CraftingGenerator.ensure(CraftingAptitudes.EMPTY,true,1,PLAYER,rules,new CraftingOpeningRules.Selection(CHOICE.ids(),60,-1));
        assertEquals(60,actual.alchemy());assertEquals(40,actual.forging());assertEquals(17,actual.talisman());
    }
}
