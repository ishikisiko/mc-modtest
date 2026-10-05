package com.example.myvillage.sim;

/** A saved world that cannot be restored: malformed, inconsistent, or written by a newer format. */
public final class SimFormatException extends RuntimeException {
    public SimFormatException(String problem) {
        super("World-sim save: " + problem);
    }

    public SimFormatException(String problem, Throwable cause) {
        super("World-sim save: " + problem, cause);
    }
}
