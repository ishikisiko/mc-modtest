package com.example.myvillage.client.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClientCombatHotbarTest {
    @Test void presentationCountsDownOnlyForwardAndNeverBelowZero() {
        assertEquals(40,ClientCombatHotbar.remaining(40,100,99));
        assertEquals(25,ClientCombatHotbar.remaining(40,100,115));
        assertEquals(0,ClientCombatHotbar.remaining(40,100,140));
        assertEquals(0,ClientCombatHotbar.remaining(40,100,200));
    }
}
