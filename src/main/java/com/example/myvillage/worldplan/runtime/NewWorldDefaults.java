package com.example.myvillage.worldplan.runtime;

import java.util.Properties;

/** Defaults for new worlds only; explicit choices and saved dimension codecs remain authoritative. */
public final class NewWorldDefaults {
    public static final String PRESET = "myvillage:cultivation";

    /** Do not mutate the caller's properties or replace an explicit normal/flat/custom choice. */
    public static Properties server(Properties original) {
        String selected = original.getProperty("level-type");
        if (selected != null && !selected.isBlank()) return original;
        Properties defaults = (Properties) original.clone();
        defaults.setProperty("level-type", PRESET);
        return defaults;
    }
    private NewWorldDefaults() {}
}
