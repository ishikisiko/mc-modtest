package com.example.myvillage.combat.session;

public enum CombatStopReason {
    COMPLETED,
    REJECTED,
    MODE_CHANGED,
    WEAPON_CHANGED,
    DEATH,
    LOGOUT,
    DIMENSION_CHANGED,
    MOUNTED,
    CULTIVATION_STARTED,
    DISALLOWED,
    SERVER_STOPPING,
    /** The player dodged (身法) out of the action's recovery. Appended last: the wire codec sends ordinals. */
    DODGED
}
