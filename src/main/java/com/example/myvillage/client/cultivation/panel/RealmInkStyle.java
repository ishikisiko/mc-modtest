package com.example.myvillage.client.cultivation.panel;

/** Exact cosmetic bindings only. This never resolves a realm definition or unlocks progression. */
public enum RealmInkStyle {
    MORTAL("mortal", "qi"),
    QI("qi", "qi"),
    FOUNDATION("foundation", "foundation"),
    CORE("core", "core"),
    SOUL("soul", "soul"),
    UNKNOWN("unknown", "qi");

    private final String key;
    private final String texture;

    RealmInkStyle(String key, String texture) {
        this.key = key;
        this.texture = texture;
    }

    public String key() { return key; }
    public String texture() { return texture; }

    public static RealmInkStyle forRealm(String id) {
        if (id == null) return UNKNOWN;
        return switch (id) {
            case "yuanxian:mortal" -> MORTAL;
            case "yuanxian:qi_refining" -> QI;
            case "yuanxian:foundation_establishment" -> FOUNDATION;
            case "yuanxian:golden_core" -> CORE;
            // Presentation is ready for a datapack-defined realm; no realm is registered here.
            case "yuanxian:nascent_soul" -> SOUL;
            default -> UNKNOWN;
        };
    }
}
