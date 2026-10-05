package com.example.myvillage.sim.data;

import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The sim's own realm ladder ({@code realms.json}), weakest first. Separate from the player realm
 * registry: only the lifespans of realms that exist in both must agree (a test guards this).
 */
public record RealmTable(List<Realm> realms) {

    /** One stage of a great realm: the progress needed to leave it and its combat power. */
    public record Stage(double cap, double power) {
    }

    /** Parameters for breaking through <em>into</em> a realm (on its first stage). */
    public record Breakthrough(
            double baseChance,
            double deathChance,
            double progressLoss,
            int injury,
            int successImportance,
            int failureImportance) {
    }

    /**
     * @param titleSuffix suffix appended to a Daoist title on reaching this realm (真人, 真君), or null
     * @param breakthrough null for the first realm
     */
    public record Realm(
            String id,
            int index,
            int lifespanYears,
            double prestige,
            int deathImportance,
            String titleSuffix,
            List<Stage> stages,
            Breakthrough breakthrough) {

        public int lastStage() {
            return stages.size() - 1;
        }

        public Stage stage(int stage) {
            return stages.get(stage);
        }
    }

    public Realm get(int index) {
        return realms.get(index);
    }

    public int size() {
        return realms.size();
    }

    public Realm first() {
        return realms.get(0);
    }

    /** Realm by id; throws if absent. */
    public Realm byId(String id) {
        for (Realm r : realms) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        throw new IllegalArgumentException("unknown realm " + id);
    }

    public int indexOf(String id) {
        for (Realm r : realms) {
            if (r.id().equals(id)) {
                return r.index();
            }
        }
        return -1;
    }

    private static final Set<String> ROOT_FIELDS = Set.of("schema", "realms");
    private static final Set<String> REALM_FIELDS = Set.of(
            "id", "lifespan_years", "prestige", "death_importance", "title_suffix", "stages", "breakthrough");
    private static final Set<String> STAGE_FIELDS = Set.of("cap", "power");
    private static final Set<String> BREAKTHROUGH_FIELDS = Set.of(
            "base_chance", "death_chance", "progress_loss", "injury", "success_importance", "failure_importance");

    static RealmTable parse(String file, com.google.gson.JsonObject json) {
        SimJson.Fields root = SimJson.Fields.root(file, json, ROOT_FIELDS);
        root.schema();
        JsonArray array = root.nonEmptyArray("realms");
        List<Realm> realms = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int previousLifespan = 0;
        for (int i = 0; i < array.size(); i++) {
            SimJson.Fields r = root.element("realms", array, i, REALM_FIELDS);
            String id = r.nonEmptyString("id");
            if (!id.matches("[a-z0-9_]+") || !ids.add(id)) {
                throw r.error("id", "must be a unique [a-z0-9_]+ id, got \"" + id + "\"");
            }
            int lifespan = r.positiveInteger("lifespan_years");
            if (lifespan <= previousLifespan) {
                throw r.error("lifespan_years", "must grow with each realm (previous " + previousLifespan + ")");
            }
            previousLifespan = lifespan;
            JsonArray stageJson = r.nonEmptyArray("stages");
            List<Stage> stages = new ArrayList<>();
            for (int s = 0; s < stageJson.size(); s++) {
                SimJson.Fields st = r.element("stages", stageJson, s, STAGE_FIELDS);
                stages.add(new Stage(st.positiveNumber("cap"), st.positiveNumber("power")));
            }
            Breakthrough breakthrough = null;
            if (i == 0) {
                if (r.has("breakthrough")) {
                    throw r.error("breakthrough", "must be null for the first realm");
                }
            } else {
                SimJson.Fields b = r.object("breakthrough", BREAKTHROUGH_FIELDS);
                breakthrough = new Breakthrough(
                        b.fraction("base_chance"),
                        b.fraction("death_chance"),
                        b.fraction("progress_loss"),
                        b.integer("injury", 0, 100),
                        b.integer("success_importance", 1, 3),
                        b.integer("failure_importance", 1, 3));
            }
            String suffix = r.nullableString("title_suffix");
            realms.add(new Realm(id, i, lifespan, r.nonNegativeNumber("prestige"),
                    r.integer("death_importance", 1, 3), suffix, List.copyOf(stages), breakthrough));
        }
        return new RealmTable(List.copyOf(realms));
    }
}
