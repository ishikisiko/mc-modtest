package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class EverlastingTest {
    private static final long YEAR = 576_000;
    private final EverlastingRules rules = EverlastingRules.get();
    @Test void approvedYearlyRatesAreIndependentHalfPointCredits() {
        assertEquals(0.5, rules.creditStep());
        assertEquals(new EverlastingRules.Annual(0,0,0), rules.at("myvillage:mortal"));
        assertEquals(new EverlastingRules.Annual(8,1,1), rules.at("myvillage:qi_refining"));
        assertEquals(new EverlastingRules.Annual(12,2,1), rules.at("myvillage:foundation_establishment"));
        assertEquals(new EverlastingRules.Annual(16,3,2), rules.at("myvillage:golden_core"));
        assertEquals(EverlastingRules.Annual.ZERO, rules.at("other:unreleased"));
    }
    @Test void mortalTimeCannotBeBankedOrRepricedAfterBreakthrough() {
        var afterMortal = EverlastingState.EMPTY.advance(rules.at("myvillage:mortal"), YEAR, YEAR * 80);
        assertEquals(EverlastingState.EMPTY, afterMortal);
        var firstQi = afterMortal.advance(rules.at("myvillage:qi_refining"), YEAR, YEAR / 8);
        assertEquals(1, firstQi.health().units());
        assertEquals(0, firstQi.defense().units());
        assertEquals(0, firstQi.attack().units());
    }
    @Test void qiHealthFirstCreditsExactlyAtOneHourAndOtherStatsAtEightHours() {
        var rate = rules.at("myvillage:qi_refining");
        var almost = EverlastingState.EMPTY.advance(rate,YEAR,YEAR/8-1);
        assertEquals(0,almost.health().units());
        assertEquals(1,almost.advance(rate,YEAR,1).health().units());
        var year = EverlastingState.EMPTY.advance(rate,YEAR,YEAR);
        assertEquals(4,year.health().points(rules.creditStep()));
        assertEquals(0.5,year.defense().points(rules.creditStep()));
        assertEquals(0.5,year.attack().points(rules.creditStep()));
    }
    @Test void everyRealmPaysExactlyItsAuthoredAnnualCredits() {
        for (var rate : rules.yearly().values()) {
            var state = EverlastingState.EMPTY.advance(rate,YEAR,YEAR);
            assertEquals(rate.health(),state.health().units());
            assertEquals(rate.defense(),state.defense().units());
            assertEquals(rate.attack(),state.attack().units());
        }
    }
    @Test void breakthroughKeepsPriorCreditsAndPartialProgressWithoutRepricing() {
        var qi = rules.at("myvillage:qi_refining");
        var partial = EverlastingState.EMPTY.advance(qi,YEAR,YEAR+YEAR/16);
        assertEquals(8,partial.health().units());
        assertEquals(YEAR/2,partial.health().remainder());
        var next = partial.advance(rules.at("myvillage:foundation_establishment"),YEAR,YEAR/24);
        assertEquals(9,next.health().units());
        assertEquals(0,next.health().remainder());
        assertEquals(1,next.defense().units());
        assertEquals(1,next.attack().units());
    }
    @Test void splittingOnlineTicksAcrossSavesDoesNotLoseOrDuplicateIncome() {
        var rate = rules.at("myvillage:golden_core");
        var split = EverlastingState.EMPTY;
        for(int i=0;i<2000;i++) split=split.advance(rate,YEAR,137);
        assertEquals(EverlastingState.EMPTY.advance(rate,YEAR,274000),split);
    }
    @Test void timeScaleReloadPreservesTheAlreadyEarnedFraction() {
        var rate=rules.at("myvillage:qi_refining");
        var partial=EverlastingState.EMPTY.advance(rate,YEAR,YEAR/16);
        var next=partial.advance(rate,YEAR/2,YEAR/32);
        assertEquals(1,next.health().units());
        assertEquals(0,next.health().remainder());
        assertEquals(YEAR/2,next.yearTicks());
    }
    @Test void savedPartialProgressRoundTripsWithoutReceivingOfflineTicks() {
        var state=EverlastingState.EMPTY.advance(rules.at("myvillage:foundation_establishment"),YEAR,123456);
        var encoded=EverlastingState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow();
        assertEquals(state,EverlastingState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow());
        assertEquals(state,state.advance(rules.at("myvillage:foundation_establishment"),YEAR,0));
    }
    @Test void malformedCountersAndProgressAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new EverlastingState.Counter(-1,0));
        assertThrows(IllegalArgumentException.class,()->new EverlastingState.Counter(0,-1));
        assertThrows(IllegalArgumentException.class,()->new EverlastingState(EverlastingState.Counter.ZERO,
                EverlastingState.Counter.ZERO,EverlastingState.Counter.ZERO,0));
        assertThrows(IllegalArgumentException.class,()->new EverlastingState(new EverlastingState.Counter(0,100),
                EverlastingState.Counter.ZERO,EverlastingState.Counter.ZERO,100));
        assertThrows(IllegalArgumentException.class,()->new EverlastingRules.Annual(-1,0,0));
        assertThrows(IllegalArgumentException.class,()->EverlastingState.EMPTY.advance(rules.at("myvillage:qi_refining"),YEAR,-1));
    }
    @Test void largeTickProductsCannotWrapCountersOrBecomeNegative() {
        var state=EverlastingState.EMPTY.advance(rules.at("myvillage:golden_core"),Long.MAX_VALUE/2,Long.MAX_VALUE);
        assertEquals(32,state.health().units());
        assertTrue(state.health().remainder()>=0 && state.health().remainder()<state.yearTicks());
        var reloaded=state.advance(rules.at("myvillage:qi_refining"),YEAR,1);
        assertTrue(reloaded.health().units()>=state.health().units());
    }
    @Test void lifespanBonusChangesTheCapWithoutRefundingConsumedAge() {
        int[] before={80,120,240,500},after={120,180,360,750};
        for(int i=0;i<before.length;i++) {
            assertEquals(before[i],rules.maximumYears(before[i],false));
            assertEquals(after[i],rules.maximumYears(before[i],true));
        }
        assertEquals(80,rules.maximumYears(120,true)-100);
    }
    @Test void ruleParserRejectsSubHalfPointGrowthAndMortalIncome() {
        String valid="{\"schema\":1,\"lifespan_bonus\":0.5,\"credit_step\":0.5,\"yearly\":{"
                +"\"myvillage:mortal\":[0,0,0],\"myvillage:qi_refining\":[4,0.5,0.5],"
                +"\"myvillage:foundation_establishment\":[6,1,0.5],\"myvillage:golden_core\":[8,1.5,1]}}";
        var json=JsonParser.parseString(valid).getAsJsonObject();
        assertEquals(rules,EverlastingRules.parse(json));
        json.getAsJsonObject("yearly").getAsJsonArray("myvillage:mortal").set(0,new com.google.gson.JsonPrimitive(0.5));
        assertThrows(IllegalArgumentException.class,()->EverlastingRules.parse(json));
        var sub=JsonParser.parseString(valid.replace("[4,0.5,0.5]","[4,0.25,0.5]")).getAsJsonObject();
        assertThrows(IllegalArgumentException.class,()->EverlastingRules.parse(sub));
    }
}
