package com.example.myvillage.entity.beast;

/** Where a move tick falls in the move's timeline. */
public enum BeastMovePhase {
    /** Ticks before {@code windup_ticks}: the telegraph. */
    WINDUP,
    /** Ticks from {@code windup_ticks} up to the first active tick (empty when they coincide). */
    RELEASE,
    /** {@code active_ticks}: the hit volume is tested. */
    ACTIVE,
    /** Ticks after the last active tick up to {@code total_ticks}: the punish window. */
    RECOVERY,
    /** At or past {@code total_ticks}: the move is over. */
    FINISHED
}
