package com.example.myvillage.entity.beast;

/**
 * Bundled beast data that cannot be loaded. It always names the file and the field, and it is a
 * startup error: a beast never runs on partial data.
 */
public final class BeastDataException extends RuntimeException {
    private final String file;
    private final String field;

    public BeastDataException(String file, String field, String problem) {
        this(file, field, problem, null);
    }

    public BeastDataException(String file, String field, String problem, Throwable cause) {
        super("Invalid beast data " + file + " at " + field + ": " + problem, cause);
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
