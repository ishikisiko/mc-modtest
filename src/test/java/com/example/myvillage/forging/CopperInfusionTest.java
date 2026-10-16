package com.example.myvillage.forging;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CopperInfusionTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap(){
        if(net.neoforged.fml.loading.LoadingModList.get()==null)
            net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void paidProgressPersistsAndSyncsAndRejectsNegativeOrExcessiveValues(){
        for(int paid:new int[]{0,1,6,10000}){
            var progress=new CopperInfusion(paid);
            assertEquals(progress,CopperInfusion.CODEC.parse(JsonOps.INSTANCE,CopperInfusion.CODEC.encodeStart(JsonOps.INSTANCE,progress).getOrThrow()).getOrThrow());
            var buf=Unpooled.buffer();try{CopperInfusion.STREAM_CODEC.encode(buf,progress);assertEquals(progress,CopperInfusion.STREAM_CODEC.decode(buf));assertFalse(buf.isReadable());}finally{buf.release();}
        }
        for(int invalid:new int[]{-1,10001}){
            assertTrue(CopperInfusion.CODEC.parse(JsonOps.INSTANCE,new com.google.gson.JsonPrimitive(invalid)).error().isPresent());
            var buf=Unpooled.buffer();try{ByteBufCodecs.VAR_INT.encode(buf,invalid);assertThrows(IllegalArgumentException.class,()->CopperInfusion.STREAM_CODEC.decode(buf));}finally{buf.release();}
        }
    }
    @Test void unfinishedBatchChargesOnlyRemainingInvestment(){
        var rules=InfusionRules.get();int count=4,paid=0,cost=0;
        while(paid<rules.qiPerIngot()){
            var step=rules.plan(count,paid,Long.MAX_VALUE).orElseThrow();
            assertTrue(step.paidQi()>paid);assertEquals(step.paidQi()==rules.qiPerIngot(),step.complete());
            paid=step.paidQi();cost+=step.cost();
        }
        assertEquals(count*rules.qiPerIngot(),cost);
    }
    @Test void splittingPaidCopperCannotMultiplyQiInvestment(){
        var rules=new InfusionRules(64,11,3,20);
        int remainingWhole=rules.plan(10,4,100).orElseThrow().cost();
        int remainingSplit=rules.plan(3,4,100).orElseThrow().cost()+rules.plan(7,4,100).orElseThrow().cost();
        assertEquals(remainingWhole,remainingSplit);
        assertEquals(10*4,3*4+7*4);
        assertEquals(20,rules.plan(10,9,20).orElseThrow().cost());
        assertTrue(rules.plan(10,9,20).orElseThrow().complete());
    }
    @Test void insufficientQiCannotPartiallyChargeOnePulse(){
        var rules=new InfusionRules(16,6,1,20);
        assertTrue(rules.plan(4,2,3).isEmpty());
        var paid=rules.plan(4,2,4).orElseThrow();assertEquals(4,paid.cost());assertEquals(3,paid.paidQi());
        for(int count:new int[]{-1,0,17,10000})assertTrue(rules.plan(count,0,Long.MAX_VALUE).isEmpty());
        assertTrue(rules.plan(4,-1,100).isEmpty());assertTrue(rules.plan(4,0,-1).isEmpty());
    }
    @Test void malformedTuningCannotCreateFreeProcessing(){
        for(String key:new String[]{"max_batch","qi_per_ingot","qi_per_pulse","pulse_ticks"}){
            var json=JsonParser.parseString("{\"max_batch\":16,\"qi_per_ingot\":6,\"qi_per_pulse\":1,\"pulse_ticks\":20}").getAsJsonObject();
            json.addProperty(key,0);assertTrue(InfusionRules.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
        }
        assertThrows(IllegalArgumentException.class,()->new InfusionRules(16,6,7,20));
    }
    @Test void sandPatchOffsetsAreBoundedUniqueAndBeginAtCenter(){
        var positions=EdgeSandFeature.offsets(3);assertEquals(29,positions.size());
        assertEquals(positions.size(),new java.util.HashSet<>(positions).size());
        assertEquals(net.minecraft.core.BlockPos.ZERO,positions.getFirst());
        for(var p:positions)assertTrue(p.getX()*p.getX()+p.getZ()*p.getZ()<=9&&p.getY()==0);
    }
}
