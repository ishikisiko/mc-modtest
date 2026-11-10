package com.example.myvillage.alchemy;

import com.example.myvillage.cultivation.crafting.CraftingQualityRules;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyBatchTest {
    private static final UUID BREWER=UUID.fromString("619a2fa3-76e3-45c1-b1fa-5136ef930224");
    private static AlchemyBatch mixed(){return new AlchemyBatch(BREWER,"huiling",60,"myvillage:qi_refining",0,
        List.of(new CraftingQualityRules.Roll(10,0),new CraftingQualityRules.Roll(100,4)));}
    @Test void startRequiresEveryAuthorityAndMaterialGateBeforeDrawing(){
        assertNull(AlchemyBatch.rejection(true,false,true,true,true,true));
        assertEquals(AlchemyBatch.Rejection.INVALID,AlchemyBatch.rejection(false,false,true,true,true,true));
        assertEquals(AlchemyBatch.Rejection.BUSY,AlchemyBatch.rejection(true,true,false,true,true,true));
        assertEquals(AlchemyBatch.Rejection.UNAWAKENED,AlchemyBatch.rejection(true,false,false,true,true,true));
        assertEquals(AlchemyBatch.Rejection.INGREDIENTS,AlchemyBatch.rejection(true,false,true,false,true,true));
        assertEquals(AlchemyBatch.Rejection.FUEL,AlchemyBatch.rejection(true,false,true,true,false,true));
        assertEquals(AlchemyBatch.Rejection.OUTPUT,AlchemyBatch.rejection(true,false,true,true,true,false));
        assertEquals("message.myvillage.alchemy.start_output",AlchemyBatch.rejection(true,false,true,true,true,false).messageKey());
    }
    @Test void everyPillDrawsIndependentlyAndFailuresAreRetained(){
        var calls=new AtomicInteger();var r=AlchemyRules.get().recipe("huiling").orElseThrow();
        var b=AlchemyBatch.create(BREWER,r,60,()->calls.getAndIncrement()==0?-5:3);
        assertEquals(2,calls.get());assertEquals(1,b.failures());assertEquals(1,b.successfulCount());assertEquals(4,b.bestQuality());
        assertEquals(List.of(0,4),b.outcomes().stream().map(CraftingQualityRules.Roll::quality).toList());
        var failed=AlchemyBatch.create(BREWER,r,0,()->-2);
        assertEquals(2,failed.failures());assertEquals(0,failed.successfulCount());
    }
    @Test void saveLoadAndBlockedProgressPreserveEveryFrozenOutcome(){
        var original=mixed();var current=original;var p=new BrewingProgress();p.restore("huiling",2,5);
        for(int i=0;i<100;i++){assertFalse(p.advance("huiling",5,false,false));current=AlchemyBatch.read(current.save()).orElseThrow();assertEquals(original,current);}
        assertEquals(2,p.ticks());assertEquals(BREWER,current.brewer());
        assertFalse(p.advance("huiling",5,false,true));assertFalse(p.advance("huiling",5,false,true));assertTrue(p.advance("huiling",5,false,true));
    }
    @Test void malformedMissingAndInconsistentFrozenResultsAreRejected(){
        assertTrue(AlchemyBatch.read(new CompoundTag()).isEmpty());var good=mixed().save();
        for(String key:List.of("Version","Brewer","Recipe","Score","Tier","Difficulty","Outcomes")){
            var bad=good.copy();bad.remove(key);assertTrue(AlchemyBatch.read(bad).isEmpty(),key);
        }
        var bad=good.copy();bad.getList("Outcomes",Tag.TAG_COMPOUND).getCompound(0).putInt("Quality",1);
        assertTrue(AlchemyBatch.read(bad).isEmpty(),"cannot turn failure into a successful pill");
        bad=good.copy();bad.putInt("Difficulty",4);assertTrue(AlchemyBatch.read(bad).isEmpty());
        bad=good.copy();bad.putInt("Version",1);assertTrue(AlchemyBatch.read(bad).isEmpty());
        bad=good.copy();bad.putInt("Score",-1);assertTrue(AlchemyBatch.read(bad).isEmpty());
        bad=good.copy();bad.putFloat("Score",60);assertTrue(AlchemyBatch.read(bad).isEmpty());
        bad=good.copy();bad.putIntArray("Brewer",new int[]{1,2});assertTrue(AlchemyBatch.read(bad).isEmpty());
        bad=good.copy();bad.put("Outcomes",new ListTag());assertTrue(AlchemyBatch.read(bad).isEmpty());
    }
}
