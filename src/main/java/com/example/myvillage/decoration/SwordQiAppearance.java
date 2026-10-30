package com.example.myvillage.decoration;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Packaged authored proportions: shared by the pedestal collision and the client effect. */
public final class SwordQiAppearance {
    public float[] base_box;
    public int light_level, view_distance, breath_ticks, ribbon_ticks, ribbon_samples;
    public int ring_samples, motes, mote_ticks;
    public float bob_height, guard_width, guard_y, guard_sweep, guard_thickness, halo_width;
    public float ribbon_radius, ribbon_bottom, ribbon_height, ribbon_turns, ribbon_width;
    public float ring_radius, ring_y, ring_width, mote_size;
    public float[][] blade, grip;
    public java.util.Map<String, String> colors;

    private static final class Holder {
        private static final SwordQiAppearance VALUE = load();
    }
    public static SwordQiAppearance get() { return Holder.VALUE; }
    public int color(String key) { return Integer.parseInt(colors.get(key), 16); }
    private static SwordQiAppearance load() {
        var stream = SwordQiAppearance.class.getResourceAsStream("/assets/myvillage/sword_qi/appearance.json");
        if (stream == null) throw new IllegalStateException("Missing sword-qi decoration appearance");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, SwordQiAppearance.class);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read sword-qi decoration appearance", exception);
        }
    }
    private SwordQiAppearance() { }
}
