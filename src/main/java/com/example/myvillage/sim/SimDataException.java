package com.example.myvillage.sim;

/**
 * World-sim data that cannot be loaded. It always names the file and the field, and it is a
 * startup error: the simulation never runs on partial data.
 */
public final class SimDataException extends RuntimeException {
    private final String file;
    private final String field;

    public SimDataException(String file, String field, String problem) {
        this(file, field, problem, null);
    }

    public SimDataException(String file, String field, String problem, Throwable cause) {
        super("Invalid world-sim data " + file + " at " + field + ": " + problem, cause);
        this.file = file;
        this.field = field;
    }

    public String file() {
        return file;
    }

    public String field() {
        return field;
    }
}
