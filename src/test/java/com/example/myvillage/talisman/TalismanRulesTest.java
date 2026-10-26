package com.example.myvillage.talisman;

import com.example.myvillage.combat.foundation.FieldPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TalismanRulesTest {
    @Test void sameFormulaActuallyImprovesEveryQualityAndDrawingLeaseIsBounded() {
        var r = TalismanRules.get();
        for (int q = 1; q < 4; q++) {
            assertTrue(r.damage(q + 1) > r.damage(q)); assertTrue(r.rootTicks(q + 1) > r.rootTicks(q));
            assertTrue(r.shield(q + 1) > r.shield(q));
        }
        assertTrue(r.draw("heartbeat_ticks") < r.draw("lease_ticks"));
        assertTrue(r.use("activation_power") > 0); assertTrue(r.use("cooldown_ticks") > 0);
        assertThrows(IllegalArgumentException.class, () -> r.damage(5));
    }
    @Test void sharedHostilePolicyRejectsSelfAlliesDisabledPvpAndCreative() {
        assertFalse(FieldPolicy.permits(false, true, false, true, false, true, true, false, true, false));
        assertFalse(FieldPolicy.permits(false, false, true, true, false, true, true, false, true, false));
        assertFalse(FieldPolicy.permits(false, false, false, true, true, false, true, false, true, false));
        assertFalse(FieldPolicy.permits(false, false, false, true, true, true, true, true, true, false));
        assertTrue(FieldPolicy.permits(false, false, false, true, false, false, false, false, true, false));
    }
    @Test void insufficientPowerCooldownOrFailedPreparationRejectActivation() {
        assertFalse(TalismanUsePolicy.ready(true, false, 1, 2));
        assertFalse(TalismanUsePolicy.ready(true, true, 100, 2));
        assertFalse(TalismanUsePolicy.ready(false, false, 100, 2));
        assertTrue(TalismanUsePolicy.ready(true, false, 2, 2));
    }
}
