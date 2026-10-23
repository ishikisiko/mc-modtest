package com.example.myvillage.combat.foundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldPolicyTest {
    @Test void friendshipIncludesSelfTeamsAndRealSectButNotUnknownSect() {
        assertTrue(FieldPolicy.allied(true, false, -1, -1));
        assertTrue(FieldPolicy.allied(false, true, -1, -1));
        assertTrue(FieldPolicy.allied(false, false, 0, 0));
        assertTrue(FieldPolicy.allied(false, false, 3, 3));
        assertFalse(FieldPolicy.allied(false, false, -1, -1));
        assertFalse(FieldPolicy.allied(false, false, 3, 4));
        assertFalse(FieldPolicy.allied(false, false, -1, 3));
    }

    @Test void hostileFieldRequiresEveryPvpAndPermissionGate() {
        assertTrue(enemy(false, false, true, true, true, true, false, true, false));
        assertFalse(enemy(false, false, true, true, false, true, false, true, false));
        assertFalse(enemy(false, false, true, true, true, false, false, true, false));
        assertFalse(enemy(false, false, false, true, true, true, false, true, false));
        assertFalse(enemy(false, true, true, true, true, true, false, true, false));
        assertFalse(enemy(true, false, true, true, true, true, false, true, false));
        assertFalse(enemy(false, false, true, true, true, true, true, true, false));
        assertFalse(enemy(false, false, true, true, true, true, false, false, false));
        assertFalse(enemy(false, false, true, true, true, true, false, true, true));
        // 服务器关闭 PvP 不阻止合法非玩家目标。
        assertTrue(enemy(false, false, true, false, false, false, false, true, false));
    }

    @Test void healingDoesNotTreatProtectedOrNeutralStrangersAsFriends() {
        assertTrue(FieldPolicy.permits(true, true, true, false, true, false, false, false, true, false));
        assertTrue(FieldPolicy.permits(true, false, true, false, false, false, false, false, true, false));
        assertFalse(FieldPolicy.permits(true, false, false, false, true, false, false, false, true, false));
        assertFalse(FieldPolicy.permits(true, true, true, false, true, false, false, false, false, false));
        assertFalse(FieldPolicy.permits(true, true, true, false, true, false, false, false, true, true));
    }

    @Test void expiryDeathOfflineDimensionAndUnloadedCenterTerminateImmediately() {
        assertTrue(FieldPolicy.alive(59, 60, true, true, true, true));
        assertFalse(FieldPolicy.alive(60, 60, true, true, true, true));
        assertFalse(FieldPolicy.alive(0, 60, false, true, true, true));
        assertFalse(FieldPolicy.alive(0, 60, true, false, true, true));
        assertFalse(FieldPolicy.alive(0, 60, true, true, false, true));
        assertFalse(FieldPolicy.alive(0, 60, true, true, true, false));
    }

    private static boolean enemy(boolean self, boolean allied, boolean mayHarm, boolean player,
                                 boolean pvp, boolean mutual, boolean creative, boolean alive, boolean spectator) {
        return FieldPolicy.permits(false, self, allied, mayHarm, player, pvp, mutual, creative, alive, spectator);
    }
}
