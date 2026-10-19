package com.example.myvillage.entity.beast;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Strict, bundled boss-only tuning. Ordinary beasts keep their schema and behavior. */
public record ThunderTigerRules(ResourceLocation encounterRealm, int encounterStage, double phaseHealthFraction, PhaseTwo phaseTwo, Bounds bounds,
        Horn horn, Thunder thunder, Presentation presentation, Feedback feedback) {
    public static final String PATH = "data/myvillage/beast/thunder_tiger_boss.json";
    private static final class Holder { static final ThunderTigerRules INSTANCE = load(); }
    public static ThunderTigerRules bundled() { return Holder.INSTANCE; }

    private static ThunderTigerRules load() {
        try (InputStream stream = ThunderTigerRules.class.getClassLoader().getResourceAsStream(PATH)) {
            if (stream == null) throw new BeastDataException(PATH, "<file>", "is missing");
            return parse(BeastJson.parseStrict(PATH, new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new BeastDataException(PATH, "<file>", "cannot read", exception);
        }
    }

    public static ThunderTigerRules parse(com.google.gson.JsonObject json) {
        var r = new BeastJson.Fields(PATH, "", json, Set.of("schema", "encounter_realm", "encounter_stage", "phase_health_fraction", "phase_two", "bounds", "horn", "thunder", "presentation", "feedback"));
        r.schema();
        ResourceLocation realm = r.id("encounter_realm");
        int stage = r.nonNegativeInteger("encounter_stage");
        com.example.myvillage.stats.StatsRules.bundled().realmKey(realm.toString(), stage);
        double fraction = r.fraction("phase_health_fraction");
        if (fraction <= 0 || fraction >= 1) throw r.error("phase_health_fraction", "must be strictly inside 0..1");
        var p = r.object("phase_two", Set.of("move_gap_ticks", "damage_multiplier", "aftershock_delay_ticks", "aftershock_radius"));
        var phase = new PhaseTwo(p.nonNegativeInteger("move_gap_ticks"), p.positiveNumber("damage_multiplier"), p.positiveInteger("aftershock_delay_ticks"), p.positiveNumber("aftershock_radius"));
        var b = r.object("bounds", Set.of("home_radius", "target_radius", "vertical_range", "target_rescan_ticks", "unseen_timeout_ticks", "repath_ticks", "return_speed_modifier", "load_grace_ticks"));
        var bounds = new Bounds(b.positiveNumber("home_radius"), b.positiveNumber("target_radius"), b.positiveNumber("vertical_range"), b.positiveInteger("target_rescan_ticks"), b.positiveInteger("unseen_timeout_ticks"), b.positiveInteger("repath_ticks"), b.positiveNumber("return_speed_modifier"), b.positiveInteger("load_grace_ticks"));
        if (bounds.targetRadius() > bounds.homeRadius()) throw b.error("target_radius", "must not exceed home_radius");
        var h = r.object("horn", Set.of("exposure_ticks", "damage_to_break", "pillar_half_width", "pillar_height", "maximum_pillars", "allowed_blocks", "head_forward", "head_center_y", "exposed_head_center_y", "head_width", "head_height", "body_width", "body_height", "body_front", "body_back"));
        var horn = new Horn(h.positiveInteger("exposure_ticks"), h.positiveNumber("damage_to_break"), h.nonNegativeInteger("pillar_half_width"), h.positiveInteger("pillar_height"), h.positiveInteger("maximum_pillars"), h.idList("allowed_blocks"), h.positiveNumber("head_forward"), h.positiveNumber("head_center_y"), h.positiveNumber("exposed_head_center_y"), h.positiveNumber("head_width"), h.positiveNumber("head_height"), h.positiveNumber("body_width"), h.positiveNumber("body_height"), h.number("body_front"), h.number("body_back"));
        if (horn.pillarHalfWidth() > 2 || horn.pillarHeight() > 8 || horn.maximumPillars() > 16) throw h.error("maximum_pillars", "pillar scan budget exceeds 16 columns of at most 5x5x8");
        if (horn.bodyBack() >= horn.bodyFront()) throw h.error("body_back", "must precede body_front");
        var t = r.object("thunder", Set.of("mark_radius", "mark_height", "stomp_ring_radius", "roar_mark_radius", "telegraph_interval_ticks", "particle_points"));
        var thunder = new Thunder(t.positiveNumber("mark_radius"), t.positiveNumber("mark_height"), t.positiveNumber("stomp_ring_radius"), t.positiveNumber("roar_mark_radius"), t.positiveInteger("telegraph_interval_ticks"), t.positiveInteger("particle_points"));
        if (thunder.particlePoints() > 64 || thunder.stompRingRadius() > bounds.targetRadius()) throw t.error("particle_points", "feedback/query budget exceeds configured bounds");
        var v = r.object("presentation", Set.of("horn_break_ticks", "phase_change_ticks", "death_ticks", "horn_exposed_ticks"));
        var presentation = new Presentation(v.positiveInteger("horn_break_ticks"), v.positiveInteger("phase_change_ticks"), v.positiveInteger("death_ticks"), v.positiveInteger("horn_exposed_ticks"));
        if (presentation.deathTicks() != 20) throw v.error("death_ticks", "must match vanilla Monster removal at 20 ticks");
        if (presentation.hornExposedTicks() != horn.exposureTicks()) throw v.error("horn_exposed_ticks", "must equal horn.exposure_ticks");
        var f = r.object("feedback", Set.of("ambient_pitch", "ambient_volume", "telegraph_volume", "strike_volume"));
        var feedback = new Feedback(f.positiveNumber("ambient_pitch"), f.positiveNumber("ambient_volume"), f.positiveNumber("telegraph_volume"), f.positiveNumber("strike_volume"));
        return new ThunderTigerRules(realm, stage, fraction, phase, bounds, horn, thunder, presentation, feedback);
    }

    /** Phase never rolls back after healing or loading older health values. */
    public int phaseAfter(int previous, float health, float maximum) {
        return previous >= 2 || health <= maximum * phaseHealthFraction ? 2 : 1;
    }
    public record PhaseTwo(int moveGapTicks, double damageMultiplier, int aftershockDelayTicks, double aftershockRadius) {}
    public record Bounds(double homeRadius, double targetRadius, double verticalRange, int targetRescanTicks, int unseenTimeoutTicks, int repathTicks, double returnSpeedModifier, int loadGraceTicks) {}
    public record Horn(int exposureTicks, double damageToBreak, int pillarHalfWidth, int pillarHeight, int maximumPillars, List<ResourceLocation> allowedBlocks, double headForward, double headCenterY, double exposedHeadCenterY, double headWidth, double headHeight, double bodyWidth, double bodyHeight, double bodyFront, double bodyBack) {}
    public record Thunder(double markRadius, double markHeight, double stompRingRadius, double roarMarkRadius, int telegraphIntervalTicks, int particlePoints) {}
    public record Presentation(int hornBreakTicks, int phaseChangeTicks, int deathTicks, int hornExposedTicks) {}
    public record Feedback(double ambientPitch, double ambientVolume, double telegraphVolume, double strikeVolume) {}
}
