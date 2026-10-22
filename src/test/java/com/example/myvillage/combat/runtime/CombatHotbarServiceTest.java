package com.example.myvillage.combat.runtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.myvillage.combat.network.CombatHotbarPayload.*;
class CombatHotbarServiceTest {
    @Test void unlearnedAndCooldownCannotBeOverriddenByLocalReadiness() {
        assertEquals(LOCKED,CombatHotbarService.state(false,80,false));
        assertEquals(COOLDOWN,CombatHotbarService.state(true,80,true));
        assertEquals(BLOCKED,CombatHotbarService.state(true,0,true));
        assertEquals(READY,CombatHotbarService.state(true,0,false));
    }
}
