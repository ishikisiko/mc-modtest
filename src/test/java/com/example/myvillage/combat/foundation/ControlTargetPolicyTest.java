package com.example.myvillage.combat.foundation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlTargetPolicyTest {
    private static ControlRules.TargetPolicy allowed() {
        return new ControlRules.TargetPolicy(true, true, true, false, true, true, true, false, false, false);
    }

    @Test
    void validEnemyCanBeControlled() { assertTrue(allowed().allowed()); }

    @Test
    void selfAlliesAndSectPermissionCannotBeBypassedByFieldCallers() {
        assertFalse(new ControlRules.TargetPolicy(true, true, true, true, true, true, true, false, false, false).allowed());
        assertFalse(new ControlRules.TargetPolicy(true, true, true, false, false, true, true, false, false, false).allowed());
    }

    @Test
    void pvpOffAndWallsAlwaysReject() {
        assertFalse(new ControlRules.TargetPolicy(true, true, true, false, true, false, true, false, false, false).allowed());
        assertFalse(new ControlRules.TargetPolicy(true, true, true, false, true, true, false, false, false, false).allowed());
    }

    @Test
    void invalidActorsAndOtherDimensionsReject() {
        assertFalse(new ControlRules.TargetPolicy(false, true, true, false, true, true, true, false, false, false).allowed());
        assertFalse(new ControlRules.TargetPolicy(true, false, true, false, true, true, true, false, false, false).allowed());
        assertFalse(new ControlRules.TargetPolicy(true, true, false, false, true, true, true, false, false, false).allowed());
    }

    @Test
    void immuneBossPoiseAndMountedTargetsReject() {
        assertFalse(new ControlRules.TargetPolicy(true, true, true, false, true, true, true, true, false, false).allowed());
        assertFalse(new ControlRules.TargetPolicy(true, true, true, false, true, true, true, false, true, false).allowed());
        assertFalse(new ControlRules.TargetPolicy(true, true, true, false, true, true, true, false, false, true).allowed());
    }
}
