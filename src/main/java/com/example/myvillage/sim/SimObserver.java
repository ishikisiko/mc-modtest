package com.example.myvillage.sim;

/**
 * Optional listener for tools (the CLI report): sees every event as it is emitted, before the
 * chronicle prunes minor ones, and the world after genesis setup (day 0) and after every settled day,
 * prehistory included.
 */
public interface SimObserver {
    void onEvent(SimEvent event);

    default void onDayEnd(WorldSim sim) {
    }
}
