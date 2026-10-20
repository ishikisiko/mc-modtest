package com.example.myvillage.forging;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopTest {
    @Test void shapingIsFiniteAndAllIntermediateStagesRoundTrip(){
        var rules=WorkshopRules.get();var piece=new Workpiece("qingfeng_sword",0,0,0);
        for(int i=1;i<=rules.forming_strikes();i++){
            piece=piece.strike(rules);assertEquals(i,piece.blows());assertEquals(i<rules.shape_at()?0:1,piece.stage());
            var json=Workpiece.CODEC.encodeStart(JsonOps.INSTANCE,piece).getOrThrow();assertEquals(piece,Workpiece.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow());
            var bytes=Unpooled.buffer();try{Workpiece.STREAM_CODEC.encode(bytes,piece);assertEquals(piece,Workpiece.STREAM_CODEC.decode(bytes));assertFalse(bytes.isReadable());}finally{bytes.release();}
        }
        var formed=piece;assertThrows(IllegalStateException.class,()->formed.strike(rules));
        for(int stage=2;stage<=4;stage++){piece=piece.next(2);assertEquals(stage,piece.stage());assertEquals(2,piece.treatment());}
        var ground=piece;assertThrows(IllegalStateException.class,()->ground.next(0));
    }
    @Test void everyPaidStrikeCostsItsCurrentBandAndAvailableCountMatchesExactly(){
        var rules=WorkshopRules.get();
        for(var craft:rules.crafts())for(int start=0;start<craft.max_strikes();start++)for(int xp:new int[]{0,1,5,6,15,30,100,10000}){
            var record=new TemperRecord(xp,start,0,0,0);int expected=rules.available(record,craft),count=0;
            while(record.strikes()<craft.max_strikes()&&record.experience()>=rules.experienceCost(record.strikes())){
                int before=record.experience(),cost=rules.experienceCost(record.strikes());record=record.strike(2,craft,rules);assertEquals(before-cost,record.experience());count++;
            }
            assertEquals(expected,count);var exhausted=record;assertThrows(IllegalArgumentException.class,()->exhausted.strike(2,craft,rules));
        }
    }
    @Test void directionsPreserveEarlierInvestmentsAndNeverResetTheLifetimeLimit(){
        var rules=WorkshopRules.get();var craft=rules.crafts().getFirst();var record=new TemperRecord(10000,0,0,0,2);
        record=record.strike(1,craft,rules).strike(2,craft,rules).strike(3,craft,rules);
        assertEquals(1,record.edge());assertEquals(1,record.endurance());assertEquals(1,record.channeling());assertEquals(3,record.strikes());assertEquals(2,record.treatment());
        var bytes=Unpooled.buffer();try{TemperRecord.STREAM_CODEC.encode(bytes,record);assertEquals(record,TemperRecord.STREAM_CODEC.decode(bytes));}finally{bytes.release();}
        assertEquals(record,TemperRecord.CODEC.parse(JsonOps.INSTANCE,TemperRecord.CODEC.encodeStart(JsonOps.INSTANCE,record).getOrThrow()).getOrThrow());
    }
    @Test void experienceSaturatesWithoutIntegerOverflowAndWireRejectsNegativeValues(){
        var rules=WorkshopRules.get();assertEquals(rules.experience_limit(),TemperRecord.EMPTY.earn(Integer.MAX_VALUE,rules).experience());
        assertThrows(IllegalArgumentException.class,()->TemperRecord.EMPTY.earn(-1,rules));
        for(String json:new String[]{"{\"experience\":-1,\"edge\":0,\"endurance\":0,\"channeling\":0}","{\"experience\":0,\"edge\":1001,\"endurance\":0,\"channeling\":0}"})
            assertTrue(TemperRecord.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).error().isPresent());
        var bytes=Unpooled.buffer();try{bytes.writeByte(-1);assertThrows(Exception.class,()->TemperRecord.STREAM_CODEC.decode(bytes));}finally{bytes.release();}
    }
    @Test void malformedTuningCannotMakeFreeOrUnboundedStrikes() throws Exception {
        String json;try(var in=WorkshopRules.class.getResourceAsStream(WorkshopRules.RESOURCE)){json=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        for(String invalid:new String[]{json.replace("\"experience\": 6","\"experience\": 0"),json.replace("\"contact_tick\": 4","\"contact_tick\": 99"),
                json.replace("\"max_strikes\": 60","\"max_strikes\": 9999"),json.replace("\"qi_discount_per_strike\": 0.002","\"qi_discount_per_strike\": 0.5")})
            assertThrows(RuntimeException.class,()->WorkshopRules.parse(new StringReader(invalid)));
    }
    @Test void blankAndSpiritTransactionsPartitionOriginalCostsWithoutDuplicatingOrLosingIngredients(){
        var rules=WorkshopRules.get();
        for(var craft:rules.crafts()){
            var recipe=ForgingRules.get().recipe(craft.recipe()).orElseThrow();
            for(var trait:recipe.traits()){
                var combined=new java.util.TreeMap<>(rules.blankCosts(recipe));rules.infusionCosts(recipe,trait).forEach((id,n)->combined.merge(id,n,Integer::sum));
                assertEquals(ForgingRules.get().costs(recipe,trait),combined);assertFalse(rules.blankCosts(recipe).containsKey(rules.spirit_ingredient()));
            }
        }
    }
}
