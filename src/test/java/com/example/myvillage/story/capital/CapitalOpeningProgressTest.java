package com.example.myvillage.story.capital;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapitalOpeningProgressTest {
    @Test void supplyAcceptanceCannotRepeatAndOnlyDistinctOwnReceiptsFinishTheWork() {
        var a = CapitalOpeningProgress.EMPTY.accept("capital_test");
        assertEquals("working", a.stage()); assertSame(a, a.accept("capital_test"));
        assertSame(CapitalOpeningProgress.EMPTY, CapitalOpeningProgress.EMPTY.plant(1,3));
        a = a.plant(10,3); assertSame(a, a.plant(10,3)); a = a.plant(11,3).plant(12,3);
        assertEquals("teaching", a.stage()); assertEquals(3,a.planted().size()); assertSame(a,a.plant(13,3));
        var learned = a.learned("capital_test", false); assertEquals("practice", learned.stage());
        var complete = learned.practiced(); assertEquals("complete", complete.stage());
        assertSame(complete, complete.practiced()); assertSame(complete, complete.accept("capital_test"));
    }
    @Test void playerProgressAndSitesAreIndependent() {
        var a = CapitalOpeningProgress.EMPTY.accept("capital_test").plant(10,3);
        var b = CapitalOpeningProgress.EMPTY.accept("capital_test");
        assertTrue(b.planted().isEmpty()); assertEquals(List.of(10L), a.planted());
        assertSame(a, a.learned("another_capital",true));
        assertEquals("complete", CapitalOpeningProgress.EMPTY.learned("capital_test",true).stage());
        assertEquals("practice", CapitalOpeningProgress.EMPTY.learned("capital_test",false).stage());
    }
    @Test void codecKeepsExactReceiptsAndRevisionAcrossSaveRestore() {
        var a = CapitalOpeningProgress.EMPTY.accept("capital_test").plant(10,3).plant(11,3);
        var json = CapitalOpeningProgress.CODEC.encodeStart(JsonOps.INSTANCE,a).getOrThrow();
        assertEquals(a, CapitalOpeningProgress.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow());
        assertThrows(UnsupportedOperationException.class,()->a.planted().add(12L));
    }
    @Test void malformedStageRevisionAndDuplicateReceiptsAreRejected() {
        for (String text : List.of(
                "{\"site\":\"capital_test\",\"stage\":\"working\",\"revision\":-1,\"planted\":[]}",
                "{\"site\":\"capital_test\",\"stage\":\"unknown\",\"revision\":0,\"planted\":[]}",
                "{\"site\":\"capital_test\",\"stage\":\"working\",\"revision\":1,\"planted\":[1,1]}"))
            assertTrue(CapitalOpeningProgress.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(text)).error().isPresent());
    }
}
