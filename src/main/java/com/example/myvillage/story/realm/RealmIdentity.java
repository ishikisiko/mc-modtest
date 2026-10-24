package com.example.myvillage.story.realm;

import java.util.Optional;
import java.util.UUID;

/** Canonical save identities; no world, registry or client dependency. */
public final class RealmIdentity {
    public static final String TEMPLATE = "myvillage:qianzhen_template";
    private static final String PREFIX = "myvillage:qianzhen/";
    private RealmIdentity() {}
    public static String dimension(UUID owner) { return PREFIX + owner; }
    public static String cast(UUID owner) { return "qianzhen_ch1_" + owner; }
    public static Optional<UUID> owner(String dimension) {
        if (!dimension.startsWith(PREFIX)) return Optional.empty();
        try {
            UUID id = UUID.fromString(dimension.substring(PREFIX.length()));
            return dimension(id).equals(dimension) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    public static boolean reserved(String dimension) { return dimension.equals(TEMPLATE) || dimension.startsWith(PREFIX); }
    public static boolean permits(UUID player, String dimension) {
        return !reserved(dimension) || owner(dimension).filter(player::equals).isPresent();
    }
}
