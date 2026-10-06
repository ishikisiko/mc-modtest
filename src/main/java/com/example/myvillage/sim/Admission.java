package com.example.myvillage.sim;

import java.util.Objects;

/**
 * Whether a player may join a sect, and why not. {@code reason} is one of the constants below
 * ({@link #OK} when {@code ok}); the runtime turns it into dialogue and chat text.
 */
public record Admission(boolean ok, String reason) {
    public static final String OK = "ok";
    public static final String SECT_INACTIVE = "sect_inactive";
    public static final String ALREADY_MEMBER = "already_member";
    public static final String MEMBER_ELSEWHERE = "member_elsewhere";
    public static final String NOT_AWAKENED = "not_awakened";
    public static final String REALM_TOO_LOW = "realm_too_low";
    public static final String SELECTIVE = "selective";
    public static final String REJOIN_COOLDOWN = "rejoin_cooldown";
    public static final String STANDING_TOO_LOW = "standing_too_low";

    public Admission {
        Objects.requireNonNull(reason, "reason");
    }

    /** An admission; named so because a static {@code ok()} would clash with the {@code ok} accessor. */
    public static Admission admitted() {
        return new Admission(true, OK);
    }

    public static Admission refused(String reason) {
        if (OK.equals(reason)) {
            throw new IllegalArgumentException("a refusal needs a reason other than \"ok\"");
        }
        return new Admission(false, reason);
    }
}
