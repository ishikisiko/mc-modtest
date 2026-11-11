package com.example.myvillage.estate;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

class EstateTest {
    private static final String ID="capital_zhongzhou/west_gray_courtyard";
    private static final EstateBounds B=new EstateBounds(349,713,460,811);
    @Test void publicPerimeterIsOutsideWhileEveryPrivateEdgeIsGuarded() {
        assertFalse(B.overlaps(461.5,760.5,.3));
        assertFalse(B.overlaps(348.5,760.5,.3));
        assertTrue(B.overlaps(349.1,760.5,.3));
        assertTrue(B.overlaps(459.9,760.5,.3));
        assertTrue(B.overlaps(400,713,.3));
        assertTrue(B.overlaps(400,811,.3));
        assertFalse(B.overlaps(460.3,760,0.3));
    }
    @Test void movementCannotSkipAcrossAWholeEstateOrEnterFromAnySide() {
        assertTrue(B.crosses(300,760,500,760,.3));
        assertTrue(B.crosses(400,680,400,850,.3));
        assertTrue(B.crosses(300,680,500,850,.3));
        assertTrue(B.crosses(461,760,459,760,.3));
        assertTrue(B.crosses(400,760,400,760,.3));
        assertFalse(B.crosses(461,760,461,800,.3));
        assertFalse(B.crosses(200,600,300,700,.3));
        assertFalse(B.crosses(Double.NaN,760,400,760,.3));
    }
    @Test void exactHalfOpenColumnsAndSegmentTangentsDoNotCloseThePublicAlley() {
        assertFalse(B.overlaps(460,760,0));
        assertFalse(B.crosses(460,700,460,820,0));
        assertFalse(B.crosses(300,713,500,713,0));
        assertTrue(B.crosses(460,760,459,760,0));
        assertThrows(IllegalArgumentException.class,()->new EstateBounds(5,0,4,9));
    }
    @Test void insufficientFundsNeverInstallsATitle() {
        var data=new EstateTitles();var p=UUID.randomUUID();
        assertEquals(EstateTitles.Result.PAYMENT_FAILED,data.purchase(ID,p,"甲",()->false));
        assertNull(data.get(ID));
        assertEquals(EstateTitles.Result.PURCHASED,data.purchase(ID,p,"甲",()->true));
    }
    @Test void repeatedPurchaseAndCompetingBuyerNeverCallPaymentAgain() {
        var data=new EstateTitles();var a=UUID.randomUUID();var b=UUID.randomUUID();var charges=new AtomicInteger();
        assertEquals(EstateTitles.Result.PURCHASED,data.purchase(ID,a,"甲",()->{charges.incrementAndGet();return true;}));
        assertEquals(EstateTitles.Result.ALREADY_OWNER,data.purchase(ID,a,"甲",()->{charges.incrementAndGet();return true;}));
        assertEquals(EstateTitles.Result.SOLD,data.purchase(ID,b,"乙",()->{charges.incrementAndGet();return true;}));
        assertEquals(1,charges.get());assertEquals(a,data.get(ID).owner());
    }
    @Test void titlesRoundTripNBTAndRetainSingleOwnerAfterRename() {
        var data=new EstateSavedData();var p=UUID.randomUUID();data.titles.purchase(ID,p,"原名",()->true);
        var reloaded=EstateSavedData.load(data.save(new CompoundTag(),null),null);
        assertEquals(new EstateTitles.Title(p,"原名"),reloaded.titles.get(ID));
        assertEquals(EstateTitles.Result.ALREADY_OWNER,reloaded.titles.purchase(ID,p,"改名",()->fail("不能再次付款")));
        assertEquals(EstateTitles.Result.SOLD,reloaded.titles.purchase(ID,UUID.randomUUID(),"原名",()->fail("不能冒名付款")));
    }
    @Test void malformedOrDuplicateTitlesFailRatherThanResettingOwnership() {
        var data=new EstateTitles();var title=new EstateTitles.Title(UUID.randomUUID(),"甲");data.restore(ID,title);
        assertThrows(IllegalArgumentException.class,()->data.restore(ID,title));
        assertThrows(IllegalArgumentException.class,()->data.restore("../bad",title));
        assertThrows(IllegalArgumentException.class,()->EstateSavedData.load(new CompoundTag(),null));
    }
}
