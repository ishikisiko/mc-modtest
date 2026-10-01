package com.example.myvillage.combat.definition;

/**
 * Bundled combat data that cannot be loaded. It always names the file and the field, and it is a
 * startup error: the mod never continues with partial combat data.
 */
public final class CombatDataException extends RuntimeException {
    private final String file;
    private final String field;

    public CombatDataException(String file, String field, String problem) {
        this(file, field, problem, null);
    }

    public CombatDataException(String file, String field, String problem, Throwable cause) {
        super("Invalid combat data " + file + " at " + field + ": " + problem, cause);
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
