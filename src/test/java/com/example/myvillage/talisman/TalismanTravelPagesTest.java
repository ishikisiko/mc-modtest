package com.example.myvillage.talisman;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TalismanTravelPagesTest {
    @Test void everyActivatedDestinationRemainsReachableAcrossPages() {
        var all = IntStream.range(0, 29).boxed().toList();
        var shown = IntStream.range(0, TalismanTravelMenu.pageCount(all.size()))
                .mapToObj(i -> TalismanTravelMenu.page(all, i)).flatMap(java.util.List::stream).toList();
        assertEquals(all, shown);
        assertThrows(IllegalArgumentException.class, () -> TalismanTravelMenu.page(all, -1));
        assertThrows(IllegalArgumentException.class, () -> TalismanTravelMenu.page(all, 5));
    }
}
