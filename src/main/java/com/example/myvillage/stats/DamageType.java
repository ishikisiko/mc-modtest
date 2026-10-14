package com.example.myvillage.stats;

import java.util.Locale;

/** Damage channels, independent of Minecraft damage source registrations. */
public enum DamageType {
    PHYSICAL, METAL, WOOD, WATER, FIRE, EARTH;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static DamageType fromId(String id) {
        for (DamageType type : values()) if (type.id().equals(id)) return type;
        throw new IllegalArgumentException("Unknown damage_types id: " + id);
    }
}
